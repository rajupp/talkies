package com.techpp.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The "Recently watched" list: newest first, one entry per title, kept in files/recent/. */
final class RecentStore {
    static final String TAB = "recent";
    private static final String TAG = "GlassRecent";
    /** Kept in all and for each app, so a run of YouTube videos can't push another app's titles out. */
    private static final int MAX = 24, PER_APP = 8;
    /** An app's first report this soon after a Watch Next card opened it is taken to be that title. */
    private static final long OPENED_MS = 10 * 60_000L;
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    /** Closer to the start or end than this, a title has no place to resume from. */
    private static final long RESUME_MIN_MS = 5_000;
    private static final float FINISHED = 0.97f;

    private RecentStore() { }

    private static File dir(Context c) { return new File(c.getExternalFilesDir(null), "recent"); }

    /**
     * A Watch Next card was opened. Some apps report only "S1 E2 Allen" while they play, with no picture or link
     * (JioHotstar), and leave their Watch Next entry unchanged for weeks; this lets the new entry keep the show's.
     */
    static void noteOpened(Context c, Trending.Pick p) {
        c.getSharedPreferences("recent_opened", Context.MODE_PRIVATE).edit().clear()
                .putString("pkg", p.pkg).putString("title", p.title).putString("link", p.intentUri)
                .putString("image", p.image == null ? null : p.image.getPath())
                .putLong("at", System.currentTimeMillis()).apply();
    }

    /** Saves a title that's playing; returns the name it's listed under, or null if it couldn't be saved. */
    static synchronized String record(Context c, String pkg, String title, String subtitle, Bitmap art) {
        try {
            File dir = dir(c);
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            JSONArray old = read(c);
            JSONArray out = new JSONArray();
            String link = "";
            File fromCard = null;
            android.content.SharedPreferences opened = c.getSharedPreferences("recent_opened", Context.MODE_PRIVATE);
            String reported = opened.getString("reported", null);  // what the app called it: a pause and resume reports it again
            if (art == null && pkg.equals(opened.getString("pkg", null)) && (title.equals(reported)
                    || reported == null && System.currentTimeMillis() - opened.getLong("at", 0) < OPENED_MS)) {
                opened.edit().putString("reported", title).apply();
                String show = opened.getString("title", title), image = opened.getString("image", null);
                link = opened.getString("link", "");
                if (image != null) fromCard = new File(image);
                if (!title.equals(show)) subtitle = title;
                title = show;
            }
            String id = Integer.toHexString((pkg + "\n" + title).hashCode());
            String image = "";
            JSONObject before = null;
            for (int i = 0; i < old.length(); i++) if (old.getJSONObject(i).getString("id").equals(id)) before = old.getJSONObject(i);
            boolean hd = before != null && before.optBoolean("hd") && new File(dir, id + ".jpg").isFile();
            if (hd) {
                image = id + ".jpg";  // already has the sharp thumbnail: keep it
            } else if (art != null) {
                // Up to 1280 px: it can fill the large card (about 1040 px on screen).
                Bitmap small = art.getWidth() > 1280
                        ? Bitmap.createScaledBitmap(art, 1280, Math.max(1, art.getHeight() * 1280 / art.getWidth()), true) : art;
                image = id + ".jpg";
                try (OutputStream o = new FileOutputStream(new File(dir, image))) { small.compress(Bitmap.CompressFormat.JPEG, 85, o); }
            } else if (fromCard != null && fromCard.isFile()) {
                image = id + ".jpg";
                try (InputStream in = new FileInputStream(fromCard); OutputStream o = new FileOutputStream(new File(dir, image))) {
                    byte[] buf = new byte[8192];
                    for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
                }
            } else if (before != null && !before.optString("image").isEmpty() && new File(dir, before.optString("image")).isFile()) {
                image = before.optString("image");  // this time the app sent no picture: keep the one it had
            }
            if (link.isEmpty() && before != null) link = before.optString("link");
            JSONObject entry = new JSONObject().put("id", id).put("pkg", pkg).put("title", title).put("subtitle", subtitle)
                    .put("at", System.currentTimeMillis()).put("image", image);
            if (hd) entry.put("hd", true);
            if (!link.isEmpty()) entry.put("link", link);
            if (before != null && before.has("pos")) entry.put("pos", before.getLong("pos")).put("dur", before.optLong("dur"));
            out.put(entry);
            java.util.Map<String, Integer> perApp = new java.util.HashMap<>();
            perApp.put(pkg, 1);
            for (int i = 0; i < old.length() && out.length() < MAX; i++) {
                JSONObject o = old.getJSONObject(i);
                if (o.getString("id").equals(id) || perApp.merge(o.getString("pkg"), 1, Integer::sum) > PER_APP) continue;
                out.put(o);
            }
            File tmp = new File(dir, "recent.json.tmp");
            try (OutputStream o = new FileOutputStream(tmp)) { o.write(out.toString().getBytes(StandardCharsets.UTF_8)); }
            if (!tmp.renameTo(new File(dir, "recent.json"))) return null;
            Set<String> keep = new HashSet<>();
            for (int i = 0; i < out.length(); i++) keep.add(out.getJSONObject(i).optString("image"));
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) if (f.getName().endsWith(".jpg") && !keep.contains(f.getName())) f.delete();
            Log.i(TAG, "Saved " + pkg + " (" + (art != null ? "with" : "no") + " artwork), " + out.length() + " in list");
            return title;
        } catch (Exception e) {
            Log.w(TAG, "Could not save: " + e);
            return null;
        }
    }

    /** How far into a saved title you got. At the very start or end there's nothing to resume, so the mark goes. */
    static synchronized void notePosition(Context c, String pkg, String title, long position, long duration) {
        try {
            String id = Integer.toHexString((pkg + "\n" + title).hashCode());
            JSONArray a = read(c);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.getString("id").equals(id)) continue;
                boolean finished = duration > 0 && position >= duration * FINISHED;
                if (position < RESUME_MIN_MS || finished) {
                    o.remove("pos");
                    o.remove("dur");
                } else {
                    o.put("pos", position).put("dur", duration);
                }
                write(c, a);
                Log.i(TAG, "Position " + pkg + " " + position / 1000 + " of " + duration / 1000 + " s" + (finished ? " (finished)" : ""));
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not note position: " + e);
        }
    }

    private static boolean upgrading;

    /**
     * Swaps YouTube entries' soft 640 px pictures for 1280 px thumbnails (YouTubeArt), and notes the video's link so its
     * card opens that video. "hd" marks a picture done; "hdTried" an entry with no exact match, tried again after three
     * days; a network failure is simply tried next time.
     * Runs on its own thread; {@code done} runs on the main thread if anything changed.
     */
    static void upgradeArtwork(Context c, Runnable done) {
        synchronized (RecentStore.class) {
            if (upgrading) return;
            upgrading = true;
        }
        Context app = c.getApplicationContext();
        new Thread(() -> {
            boolean changed = false;
            try {
                JSONArray a;
                synchronized (RecentStore.class) { a = read(app); }
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    if (!YOUTUBE.equals(o.optString("pkg")) || o.optBoolean("hd") && o.has("link")) continue;
                    if (System.currentTimeMillis() - o.optLong("hdTried", 0) < 3 * 24 * 3600_000L) continue;
                    YouTubeArt.Found found;
                    try {
                        found = YouTubeArt.find(o.optString("title"), o.optString("subtitle", "").trim());
                    } catch (java.io.IOException e) {
                        Log.w(TAG, "YouTube unreachable, will retry: " + e);
                        continue;
                    }
                    Bitmap b = found == null ? null : found.picture;
                    String id = o.getString("id"), image = id + ".jpg";
                    synchronized (RecentStore.class) {
                        if (b != null) {
                            Bitmap s = b.getWidth() > 1280
                                    ? Bitmap.createScaledBitmap(b, 1280, Math.max(1, b.getHeight() * 1280 / b.getWidth()), true) : b;
                            try (OutputStream out = new FileOutputStream(new File(dir(app), image))) {
                                s.compress(Bitmap.CompressFormat.JPEG, 88, out);
                            }
                        }
                        // Mark it in the current file (it may have changed meanwhile).
                        JSONArray now = read(app);
                        for (int j = 0; j < now.length(); j++) {
                            JSONObject e = now.getJSONObject(j);
                            if (!e.getString("id").equals(id)) continue;
                            if (b != null) e.put("hd", true).put("image", image);
                            if (found != null) e.put("link", view(YOUTUBE, "https://www.youtube.com/watch?v=" + found.id));
                            if (b == null) e.put("hdTried", System.currentTimeMillis());
                        }
                        write(app, now);
                    }
                    changed |= b != null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Artwork upgrade failed: " + e);
            } finally {
                synchronized (RecentStore.class) { upgrading = false; }
            }
            if (changed && done != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(done);
        }, "youtube-art").start();
    }

    private static void write(Context c, JSONArray a) throws Exception {
        File tmp = new File(dir(c), "recent.json.tmp");
        try (OutputStream o = new FileOutputStream(tmp)) { o.write(a.toString().getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(new File(dir(c), "recent.json"))) throw new java.io.IOException("Cannot save");
    }

    /** Forgets one entry (hold OK → Remove from Continue). */
    static synchronized void remove(Context c, String pkg, String title) {
        try {
            String id = Integer.toHexString((pkg + "\n" + title).hashCode());
            JSONArray a = read(c), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (!a.getJSONObject(i).getString("id").equals(id)) out.put(a.getJSONObject(i));
            write(c, out);
            File img = new File(dir(c), id + ".jpg");
            if (img.isFile() && !img.delete()) Log.w(TAG, "Couldn't delete " + img.getName());
        } catch (Exception e) {
            Log.w(TAG, "Could not remove: " + e);
        }
    }

    /** Entries whose app is still installed, as picks for the launcher's row. */
    static synchronized List<Trending.Pick> load(Context c) {
        List<Trending.Pick> out = new ArrayList<>();
        try {
            JSONArray a = read(c);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String pkg = o.getString("pkg");
                if (c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg) == null) continue;
                Trending.Pick p = new Trending.Pick();
                p.id = (int) Long.parseLong(o.getString("id"), 16);
                p.kind = "recent";
                p.tab = TAB;
                p.pkg = pkg;
                p.title = o.getString("title");
                p.service = appName(c, pkg);
                p.at = o.optLong("at", 0);
                String sub = o.optString("subtitle", "").trim();
                p.meta = (sub.isEmpty() ? "" : sub + " · ") + p.service;
                String image = o.optString("image");
                p.image = image.isEmpty() ? null : new File(dir(c), image);
                long position = o.optLong("pos"), duration = o.optLong("dur");
                if (position > 0 && duration > 0) p.progress = Math.min(1f, position / (float) duration);
                String link = o.optString("link");
                if (position >= RESUME_MIN_MS && YOUTUBE.equals(pkg)) {
                    link = link.replaceFirst("(watch\\?v=[^#&]+)", "$1&t=" + position / 1000 + "s");
                }
                if (!link.isEmpty()) p.intentUri = link;
                // No exact match yet (or Sharp YouTube pictures is off): the search finds it, better than YouTube's front page.
                else if (YOUTUBE.equals(pkg)) p.intentUri = view(pkg, "https://www.youtube.com/results?search_query="
                        + android.net.Uri.encode(p.title + (sub.isEmpty() ? "" : " " + sub)));
                out.add(p);
            }
        } catch (Exception e) {
            // Nothing recorded yet.
        }
        return out;
    }

    private static String view(String pkg, String url) {
        return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage(pkg)
                .toUri(android.content.Intent.URI_INTENT_SCHEME);
    }

    private static String appName(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)).toString().trim();
        } catch (Exception e) {
            return pkg;
        }
    }

    static synchronized void clear(Context c) {
        File[] files = dir(c).listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    private static JSONArray read(Context c) throws Exception {
        File f = new File(dir(c), "recent.json");
        if (!f.isFile()) return new JSONArray();
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return new JSONArray(new String(b.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
