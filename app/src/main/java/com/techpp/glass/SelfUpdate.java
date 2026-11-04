package com.techpp.glass;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Talkies settings → Updates: a newer GitHub release, downloaded, checked and handed to Android's installer, which
 * asks before installing. Once a day while Talkies is in front, and when you press OK on the row, the latest release
 * is looked up. Only the APK from the releases (signed with the project's key) installs them: the download must come
 * from this repository's releases, match GitHub's size and SHA-256, be this app, be newer, and carry the project's
 * key. A build made on a computer has its own key, so it's only told that a new version is out. Adapted from Free My
 * TV's updater (MIT).
 */
final class SelfUpdate {
    private static final String TAG = "GlassSelfUpdate";
    private static final String LATEST = "https://api.github.com/repos/rajupp/talkies/releases/latest";
    /** The only place the APK may come from: this repository's releases. */
    private static final String RELEASES = "https://github.com/rajupp/talkies/releases/download/";
    /** The release key's certificate (SHA-256, as apksigner verify --print-certs shows it). */
    private static final String RELEASE_CERT = "8de7e80b42b39ac43893b7ab34294312136632f07dbf7a633ed1f67df57c4943";
    static final String ASSET = "talkies.apk";
    private static final long MAX_SIZE = 20L << 20;   // the APK is about 200 KB
    private static final long DAY = 24 * 3600_000L;
    static final String ACTION = "com.techpp.glass.UPDATE_STATUS";

    /** What the row shows; the latest version found is in "latest". */
    interface Listener { void changed(String status); }

    private static Boolean releaseKey;

    private SelfUpdate() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("updates", Context.MODE_PRIVATE); }

    /** This app is the APK from the releases (the project's key), so it can install the next one. */
    static boolean takesReleases(Context c) {
        if (releaseKey == null) {
            try {
                releaseKey = RELEASE_CERT.equals(cert(c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES)));
            } catch (PackageManager.NameNotFoundException e) {
                releaseKey = false;
            }
        }
        return releaseKey;
    }

    static String installed(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The newer version the last check found, or null. */
    static String available(Context c) {
        String v = prefs(c).getString("latest", null);
        return v != null && compare(v, installed(c)) > 0 ? v : null;
    }

    /** The row's status: "0.4.2 · Up to date", "0.4.3 is out · OK installs", … */
    static String summary(Context c) {
        String mine = installed(c), v = available(c);
        if (v == null) return mine + "  ·  " + (prefs(c).contains("checked") ? "Up to date" : "Check now");
        return takesReleases(c) ? v + " is out  ·  OK installs" : v + " is out  ·  build it again";
    }

    /** The daily check (no download): true when it found a newer version it hadn't mentioned yet. Background thread. */
    static boolean checkIfStale(Context c) {
        if (System.currentTimeMillis() - prefs(c).getLong("checked", 0) < DAY) return false;
        try {
            latest(c);
        } catch (Exception e) {
            Log.w(TAG, "Daily check: " + e);
            return false;
        }
        String v = available(c);
        if (v == null || v.equals(prefs(c).getString("told", null))) return false;
        prefs(c).edit().putString("told", v).apply();
        return true;
    }

    /** OK on the row: check now, and if there's a newer release (and this app takes them), download and install it. */
    static void checkAndInstall(Context c, Listener l) {
        new Thread(() -> {
            try {
                l.changed("Checking…");
                JSONObject release = latest(c);
                String v = available(c);
                if (v == null) { l.changed(summary(c)); return; }
                if (!takesReleases(c)) { l.changed(summary(c)); return; }
                l.changed("Downloading " + v + "…");
                File apk = download(c, release, v);
                String wrong = misfit(c, apk);
                if (wrong != null) {
                    apk.delete();
                    Log.w(TAG, v + " " + wrong);
                    l.changed("Couldn't update: the download " + wrong);
                    return;
                }
                l.changed("Installing " + v + "…");
                install(c, apk, v);
            } catch (Exception e) {
                Log.w(TAG, "Update: " + e);
                l.changed(offline(e) ? "No connection" : "Couldn't update");
            }
        }, "Updates").start();
    }

    /** The latest release from GitHub; remembers its version and when it looked. */
    private static JSONObject latest(Context c) throws Exception {
        JSONObject release = new JSONObject(get(LATEST));
        String v = release.optString("tag_name").replaceFirst("^[vV]", "");
        if (v.isEmpty()) throw new IOException("the latest release has no tag");
        prefs(c).edit().putString("latest", v).putLong("checked", System.currentTimeMillis()).apply();
        Log.i(TAG, "Latest release " + v + ", installed " + installed(c));
        return release;
    }

    /** The release's APK, checked against GitHub's size and SHA-256 as it downloads. */
    private static File download(Context c, JSONObject release, String version) throws Exception {
        JSONObject asset = null;
        JSONArray assets = release.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            if (ASSET.equals(assets.getJSONObject(i).optString("name"))) asset = assets.getJSONObject(i);
        }
        if (asset == null) throw new IOException(ASSET + " isn't in release " + version);
        String url = asset.optString("browser_download_url"), digest = asset.optString("digest");
        long size = asset.optLong("size");
        if (!url.startsWith(RELEASES) || size <= 0 || size > MAX_SIZE) throw new IOException("unexpected asset: " + url + ", " + size);
        File dir = new File(c.getNoBackupFilesDir(), "update");
        File[] old = dir.listFiles();
        for (int i = 0; old != null && i < old.length; i++) old[i].delete();
        dir.mkdirs();
        File to = new File(dir, "talkies-" + version + ".apk"), part = new File(to.getPath() + ".part");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        long total = 0;
        HttpURLConnection h = open(url);
        try {
            if (h.getResponseCode() != 200) throw new IOException("GitHub returned HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    total += n;
                    if (total > size) throw new IOException("the download is larger than GitHub says");
                    sha.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
            }
            if (total != size || digest.startsWith("sha256:") && !digest.substring(7).equalsIgnoreCase(hex(sha.digest()))) {
                throw new IOException("the download doesn't match GitHub's size or SHA-256");
            }
            if (!part.renameTo(to)) throw new IOException("couldn't save the update");
            return to;
        } catch (Exception e) {
            part.delete();
            throw e;
        } finally {
            h.disconnect();
        }
    }

    /** Why the APK can't update this app, or null if it can: this app, newer, and signed with the project's key. */
    @SuppressWarnings("deprecation")   // versionCode and GET_SIGNATURES: the newer ones are Android 9+
    private static String misfit(Context c, File apk) throws PackageManager.NameNotFoundException {
        PackageManager pm = c.getPackageManager();
        PackageInfo theirs = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        PackageInfo ours = pm.getPackageInfo(c.getPackageName(), 0);
        if (theirs == null) return "can't be read";
        if (!c.getPackageName().equals(theirs.packageName)) return "is another app";
        String name = theirs.versionName == null ? "" : theirs.versionName;
        if (theirs.versionCode <= ours.versionCode || compare(name, ours.versionName) <= 0) return "isn't newer than this one";
        String cert = cert(theirs);
        if (cert != null && !RELEASE_CERT.equals(cert)) return "isn't signed with Talkies' key";
        return null;   // a signature that can't be read here is left to Android's installer, which refuses another key
    }

    /** Hands the APK to Android's installer; its answer comes to Receiver. */
    private static void install(Context c, File apk, String version) throws IOException {
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        // One at a time: a session an earlier OK left waiting (its question closed unanswered) goes.
        for (PackageInstaller.SessionInfo s : installer.getMySessions()) installer.abandonSession(s.getSessionId());
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        params.setSize(apk.length());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite(ASSET, 0, apk.length())) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                session.fsync(out);
            }
            Intent done = new Intent(c, Receiver.class).setAction(ACTION).putExtra("version", version);
            // Mutable: the installer adds its status.
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getBroadcast(c, id, done, flags).getIntentSender());
        }
        Log.i(TAG, "Installing " + version);
    }

    /** Android's installer answers: it needs you to confirm (and, the first time, to allow Talkies to install apps). */
    public static class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                try {
                    if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    Log.w(TAG, "Android's installer didn't open: " + e);
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                Log.w(TAG, "Not installed (" + status + "): " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    }

    /** "0.10.0" vs "0.9.1": numbers compared one by one, missing ones as 0. */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? number(x[i]) : 0, q = i < y.length ? number(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The SHA-256 of a package's one signing certificate; null when there's none, or more than one. */
    @SuppressWarnings("deprecation")
    private static String cert(PackageInfo p) {
        Signature[] s = p == null ? null : p.signatures;
        if (s == null || s.length != 1) return null;
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s[0].toByteArray()));
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static boolean offline(Exception e) {
        return e instanceof java.net.UnknownHostException || e instanceof java.net.SocketException
                || e instanceof java.net.SocketTimeoutException || e instanceof javax.net.ssl.SSLException;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        h.setRequestProperty("User-Agent", "Talkies");
        return h;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection h = open(url);
        h.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (h.getResponseCode() != 200) throw new IOException("GitHub returned HTTP " + h.getResponseCode());
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            h.disconnect();
        }
    }
}
