package com.techpp.glass;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Talkies settings → Keys and location → Send from your phone: the page a phone opens from the QR code, to paste the
 * TMDB key and the weather area instead of typing them with the remote. Its address is
 * http://(the TV's address on the home network):(a free port)/(a one-time random token). It takes one form (one
 * POST), has the TV check each key with its service, answers with the result and stops; after 5 minutes it stops
 * anyway. Plain HTTP, since a phone can't check a certificate for a LAN address; it listens only on that address, and
 * nothing sent is logged. Adapted from Free My TV's KeyPage (MIT).
 *
 * Its other job (Talkies settings → Wallpaper → From your phone): a page to pick a photo, which the phone itself crops
 * to the TV's 16:9 and re-encodes as a 1920 × 1080 JPEG (so it arrives small, without the photo's location or other
 * details), then sends as the one POST.
 */
final class PhonePage {
    private static final String TAG = "GlassPhonePage";

    /** Called on the page's own threads, never the UI's. */
    interface Listener {
        /** A phone sent the form: the TV checks it now. */
        void checking();
        /** Checks and saves what was filled in ("tmdb", "location"; empty ones left out): a line for each. */
        List<String> accept(Map<String, String> fields);
        /** The page stopped: after the form, after 5 minutes (expired) or close(). Called once. */
        void closed(boolean expired);
        /** The wallpaper page's photo, a JPEG: saves it and says how it went. */
        default List<String> acceptWallpaper(byte[] jpeg) { return null; }
    }

    static final long OPEN_FOR = 5 * 60_000L;
    private static final int MAX_HEAD = 8192, MAX_BODY = 4096, MAX_IMAGE = 8 << 20, MAX_REQUESTS = 100, MAX_ANSWERING = 4;
    /** A whole request (head and body), not each read: a client trickling bytes can't keep a thread for long. */
    private static final long READ_FOR_NS = 15_000_000_000L;

    final String url;
    /** The wallpaper page rather than the keys page. */
    private final boolean wallpaper;
    private final ServerSocket server;
    private final String path;
    private final Listener listener;
    private final AtomicBoolean taken = new AtomicBoolean();
    private final AtomicInteger answering = new AtomicInteger();
    private volatile boolean closed;

    PhonePage(InetAddress lan, Listener listener) throws IOException { this(lan, false, listener); }

    /** What the photo is cropped for, on the photo page: "the TV screen", or an app's tile. */
    private String fitFor = "the TV screen";

    PhonePage(InetAddress lan, boolean wallpaper, String fitFor, Listener listener) throws IOException {
        this(lan, wallpaper, listener);
        this.fitFor = fitFor;
    }

    PhonePage(InetAddress lan, boolean wallpaper, Listener listener) throws IOException {
        this.listener = listener;
        this.wallpaper = wallpaper;
        byte[] random = new byte[12];
        new SecureRandom().nextBytes(random);
        path = "/" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        server = new ServerSocket(0, 4, lan);   // port 0: a free one the system picks
        url = "http://" + lan.getHostAddress() + ":" + server.getLocalPort() + path;
        Thread t = new Thread(this::serve, "PhonePage");
        t.setDaemon(true);
        t.start();
    }

    /** Stops listening (the dialog closed); a request under way still gets its answer. */
    void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * The TV's private IPv4 address (10/8, 172.16/12, 192.168/16) on its Wi-Fi or Ethernet, the active network first;
     * never a VPN's (a phone on the Wi-Fi can't reach it) nor a public one (anyone could). Null: no page.
     */
    @SuppressWarnings("deprecation")   // getAllNetworks: still there
    static InetAddress lanAddress(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            if (cm == null) return null;
            Network active = cm.getActiveNetwork();
            List<Network> networks = new ArrayList<>();
            if (active != null) networks.add(active);
            for (Network n : cm.getAllNetworks()) if (!n.equals(active)) networks.add(n);
            for (Network n : networks) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        || !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    continue;
                }
                LinkProperties link = cm.getLinkProperties(n);
                if (link == null) continue;
                for (LinkAddress a : link.getLinkAddresses()) {
                    InetAddress ip = a.getAddress();
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) return ip;
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "No address: " + e);
        }
        return null;
    }

    private void serve() {
        long until = System.nanoTime() + OPEN_FOR * 1_000_000L;   // not the wall clock, which may move after a boot
        boolean expired = false;
        int requests = 0;
        try {
            while (!closed) {
                long left = (until - System.nanoTime()) / 1_000_000L;
                if (left <= 0 || requests >= MAX_REQUESTS) {   // a flood of requests ends it as time does
                    expired = true;
                    break;
                }
                server.setSoTimeout((int) Math.min(left, 30_000));
                Socket socket;
                try {
                    socket = server.accept();
                } catch (SocketTimeoutException e) {
                    continue;
                }
                requests++;
                // Each connection on its own thread, a few at most: a browser's idle preconnection doesn't hold up the page.
                if (answering.incrementAndGet() > MAX_ANSWERING) {
                    answering.decrementAndGet();
                    socket.close();
                    continue;
                }
                Thread t = new Thread(() -> answer(socket), "PhonePage answer");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (!closed) Log.w(TAG, e.getMessage() == null ? "Stopped" : e.getMessage());
        } finally {
            close();
            listener.closed(expired);
        }
    }

    private void answer(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(10_000);
            if (handle(s.getInputStream(), s.getOutputStream(), System.nanoTime() + READ_FOR_NS)) close();
        } catch (IOException | RuntimeException e) {
            // A phone that went away midway: the page stays open for the next request.
        } finally {
            answering.decrementAndGet();
        }
    }

    /** One request, read before deadline (System.nanoTime). True once the form came: the page has done its job. */
    private boolean handle(InputStream in, OutputStream out, long deadline) throws IOException {
        String head = readHead(in, deadline);
        if (head == null) return false;
        String[] line = head.substring(0, Math.max(0, head.indexOf("\r\n"))).split(" ");
        String method = line.length > 1 ? line[0] : "", target = line.length > 1 ? line[1] : "";
        int query = target.indexOf('?');
        if (query >= 0) target = target.substring(0, query);
        if (!target.equals(path)) {
            respond(out, "404 Not Found", "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        if (method.equals("GET") || method.equals("HEAD")) {
            respond(out, "200 OK", "text/html; charset=utf-8", method.equals("HEAD") ? new byte[0] : wallpaper ? photoForm() : form());
            return false;
        }
        if (!method.equals("POST")) {
            respond(out, "405 Method Not Allowed", "text/plain; charset=utf-8", new byte[0]);
            return false;
        }
        int length = contentLength(head);
        if (length < 0 || length > (wallpaper ? MAX_IMAGE : MAX_BODY)) {
            respond(out, "413 Payload Too Large", "text/plain; charset=utf-8", new byte[0]);
            return false;
        }
        byte[] body = new byte[length];
        for (int n = 0; n < length; ) {
            int got = in.read(body, n, length - n);
            if (got < 0 || System.nanoTime() > deadline) throw new IOException("cut short");
            n += got;
        }
        // The wallpaper page sends what the phone's own browser made: a JPEG, or nothing worth keeping.
        if (wallpaper && (length < 3 || (body[0] & 0xFF) != 0xFF || (body[1] & 0xFF) != 0xD8 || (body[2] & 0xFF) != 0xFF)) {
            respond(out, "415 Unsupported Media Type", "text/html; charset=utf-8",
                    result(java.util.Collections.singletonList("That didn't arrive as a picture."), false));
            return false;
        }
        // Stopped meanwhile (the dialog closed, 5 minutes), or two at once: only the first, while it shows, counts.
        if (closed || !taken.compareAndSet(false, true)) {
            respond(out, "409 Conflict", "text/html; charset=utf-8", result(null));
            return false;
        }
        if (wallpaper) {
            listener.checking();
            respond(out, "200 OK", "text/html; charset=utf-8", result(listener.acceptWallpaper(body)));
            return true;
        }
        String form = new String(body, StandardCharsets.US_ASCII);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String name : new String[]{"tmdb", "location"}) {
            String v = field(form, name).trim();
            if (!v.isEmpty()) fields.put(name, v);
        }
        listener.checking();
        respond(out, "200 OK", "text/html; charset=utf-8", result(fields.isEmpty()
                ? java.util.Collections.singletonList("Nothing was filled in, so nothing changed.") : listener.accept(fields)));
        return true;
    }

    /** The request line and headers, up to the blank line; null if there are none, too many, or they come too slowly. */
    private static String readHead(InputStream in, long deadline) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0, b;
        while (matched < 4 && (b = in.read()) >= 0) {
            if (head.size() >= MAX_HEAD || System.nanoTime() > deadline) return null;
            head.write(b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1
                    : b == '\r' ? 1 : 0;
        }
        return matched == 4 ? head.toString(StandardCharsets.ISO_8859_1.name()) : null;
    }

    private static int contentLength(String head) {
        for (String h : head.split("\r\n")) {
            int colon = h.indexOf(':');
            if (colon > 0 && h.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    return Integer.parseInt(h.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** One field of a form (application/x-www-form-urlencoded), or "". */
    private static String field(String form, String name) {
        for (String pair : form.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                try {
                    return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                } catch (IllegalArgumentException | java.io.UnsupportedEncodingException e) {
                    return "";
                }
            }
        }
        return "";
    }

    private static void respond(OutputStream out, String status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff"
                + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    // ---------------------------------------------------------------- the pages

    /** Two fields, each optional: fill in what you have. */
    private byte[] form() {
        StringBuilder b = top();
        b.append("<p>Fill in what you have; an empty box leaves that setting as it is.</p>")
                .append("<form method=post action=\"").append(path).append("\">")
                .append(box("tmdb", "TMDB key", "For the Trending and New picks. Free at "
                        + "<a href=\"https://www.themoviedb.org/settings/api\" target=_blank rel=noreferrer>themoviedb.org → Settings → API</a>: "
                        + "the API Key or the API Read Access Token.", true))
                .append(box("location", "Weather location", "A town or area, like “Camden, London”, or latitude,longitude.", false))
                .append("<button>Send to the TV</button></form><p class=s>")
                .append("It all stays on your TV. This page works once, for 5 minutes.</p>");
        return bottom(b);
    }

    /**
     * Pick a photo; the phone crops it to fill a 16:9 screen, shows it, and sends a 1920 × 1080 JPEG. Needs the
     * browser's JavaScript, which every phone has.
     */
    private byte[] photoForm() {
        StringBuilder b = top();
        b.append("<p>Choose a photo. It's cropped to fit ").append(esc(fitFor)).append(" here on your phone, then sent to the TV.</p>")
                .append("<input type=file id=f accept=\"image/*\">")
                .append("<img id=p alt=\"\" style=\"display:none;width:100%;margin-top:16px;border-radius:10px\">")
                .append("<button id=b disabled>Send to the TV</button>")
                .append("<p class=s id=m>Only the cropped picture goes to your TV, without its location or other details. "
                        + "This page works once, for 5 minutes.</p>")
                .append("<script>const f=document.getElementById('f'),p=document.getElementById('p'),b=document.getElementById('b'),")
                .append("m=document.getElementById('m');let blob=null;")
                .append("f.onchange=()=>{const file=f.files[0];if(!file)return;m.textContent='Preparing…';b.disabled=true;")
                .append("const i=new Image();i.onload=()=>{const W=1920,H=1080,s=Math.max(W/i.width,H/i.height),")
                .append("c=document.createElement('canvas');c.width=W;c.height=H;const w=i.width*s,h=i.height*s;")
                .append("c.getContext('2d').drawImage(i,(W-w)/2,(H-h)/2,w,h);c.toBlob(x=>{blob=x;p.src=URL.createObjectURL(x);")
                .append("p.style.display='block';b.disabled=false;m.textContent='This is how it fits the TV.'},'image/jpeg',0.88)};")
                .append("i.onerror=()=>{m.textContent='Your phone couldn\\'t open that picture. Try another one.'};")
                .append("i.src=URL.createObjectURL(file)};")
                .append("b.onclick=async()=>{if(!blob)return;b.disabled=true;m.textContent='Sending…';try{")
                .append("const r=await fetch(location.pathname,{method:'POST',headers:{'Content-Type':'image/jpeg'},body:blob});")
                .append("document.open();document.write(await r.text());document.close()}catch(e){")
                .append("m.textContent='Couldn\\'t reach the TV. Is your phone on the same Wi-Fi?';b.disabled=false}};</script>");
        return bottom(b);
    }

    private static String box(String name, String label, String help, boolean code) {
        return "<label for=" + name + ">" + label + "</label><p class=s>" + help + "</p><input id=" + name + " name=" + name
                + " autocomplete=off autocapitalize=off autocorrect=off spellcheck=false"
                + (code ? " class=code" : "") + ">";
    }

    /** The TV's answer, a line per field; null: the page had already stopped. */
    private byte[] result(List<String> lines) { return result(lines, true); }

    /** done false: the page is still open for another try. */
    private byte[] result(List<String> lines, boolean done) {
        StringBuilder b = top();
        if (lines == null) {
            b.append("<p class=no>This code has expired. On the TV, open Send from your phone again.</p>");
        } else {
            for (String l : lines) b.append("<p>").append(esc(l)).append("</p>");
            b.append(done ? "<p class=s>You can close this page.</p>"
                    : "<p class=s>Go back, choose another photo and send it again.</p>");
        }
        return bottom(b);
    }

    private static StringBuilder top() {
        return new StringBuilder(2048).append("<!doctype html><html lang=en><meta charset=utf-8>")
                .append("<meta name=viewport content=\"width=device-width,initial-scale=1\"><title>Talkies</title><style>")
                .append("body{margin:0 auto;max-width:34em;padding:24px;font:17px/1.5 system-ui,-apple-system,sans-serif;")
                .append("background:#1c1c1e;color:#f5f5f7}h1{font-size:24px}a{color:#9fd3ff}label{display:block;margin-top:20px;font-weight:600}")
                .append("input{box-sizing:border-box;width:100%;font:16px system-ui,sans-serif;padding:12px;border-radius:10px;border:0}")
                .append("input.code{font-family:monospace}p.s{color:#98989d;font-size:15px;margin:2px 0 8px}")
                .append("button{display:block;box-sizing:border-box;width:100%;margin:24px 0 14px;padding:14px;border:0;")
                .append("border-radius:12px;background:#f5f5f7;color:#1c1c1e;font:600 17px system-ui,sans-serif}")
                .append(".no{color:#ffc266;font-size:20px}button:disabled{opacity:.4}</style><h1>Talkies</h1>");
    }

    private static byte[] bottom(StringBuilder b) {
        return b.append("</html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&#39;"); break;
                default: b.append(ch);
            }
        }
        return b.toString();
    }

    @Override public String toString() {
        return "PhonePage(port " + server.getLocalPort() + ")";   // never the token
    }
}
