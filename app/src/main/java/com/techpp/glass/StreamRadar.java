package com.techpp.glass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * StreamRadar India (streamradar.bricksgenius.com), a Stremio add-on with open lists: each Indian app's Top 10 films
 * and series, and what's just come out. Trending uses it in India when there's no TMDB key, so picks work right after
 * install. An idea from Swakshan (Discussion #19). Its "streams" are links to the title's page in the app, not videos.
 */
final class StreamRadar {
    private static final String API = "https://streamradar.bricksgenius.com";

    /** StreamRadar's name for the app in its list ids, a word of the name in its links, the TV app, and our name. */
    static final String[][] APPS = {
        {"jiohotstar", "hotstar", "in.startv.hotstar", "JioHotstar"},
        {"netflix", "netflix", "com.netflix.ninja", "Netflix"},
        {"prime", "prime", "com.amazon.amazonvideo.livingroom", "Prime Video"},
        {"zee5", "zee5", "com.graymatrix.did", "ZEE5"},
        {"sony-liv", "sony", "com.sonyliv", "SonyLIV"},
        {"apple-tv-plus", "apple", "com.apple.atve.androidtv.appletv", "Apple TV"},
    };

    private StreamRadar() { }

    /** One app's Top 10 films ("movie") or series ("series"); a few more than ten, in StreamRadar's order. */
    static JSONArray top(String app, String type) throws Exception {
        return get("/catalog/" + type + "/streamradar-" + app + "-top10-" + (type.equals("movie") ? "movies" : "shows") + ".json")
                .getJSONArray("metas");
    }

    /** What's just come out on any of the apps, newest first. */
    static JSONArray justIn(String type) throws Exception {
        return get("/catalog/" + type + "/streamradar-justin-" + (type.equals("movie") ? "movies" : "shows") + ".json")
                .getJSONArray("metas");
    }

    /** The title's links, as {app row of APPS, its page}; null when no app here has it. */
    static String[] where(String type, String id, java.util.function.Predicate<String> installed) throws Exception {
        JSONArray s = get("/stream/" + type + "/" + id + ".json").optJSONArray("streams");
        if (s == null) return null;
        for (int i = 0; i < s.length(); i++) {
            String name = s.getJSONObject(i).optString("name").toLowerCase(Locale.ROOT);
            String url = s.getJSONObject(i).optString("externalUrl");
            for (String[] a : APPS) if (name.contains(a[1]) && installed.test(a[2])) return new String[]{a[0], url};
        }
        return null;
    }

    static String[] app(String id) {
        for (String[] a : APPS) if (a[0].equals(id)) return a;
        return null;
    }

    private static JSONObject get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + path);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
    }
}
