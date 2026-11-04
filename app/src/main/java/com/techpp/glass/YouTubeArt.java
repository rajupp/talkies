package com.techpp.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A sharp thumbnail for a YouTube video we saw play. YouTube's TV app shares only the title, the channel and a
 * 640 × 360 picture (no video ID), which looks soft on the large card. So: search YouTube for "title channel", confirm
 * a result through YouTube's public oEmbed endpoint (same title and channel), then fetch its 1280 × 720 thumbnail.
 * No key or account; nothing is sent but the title and channel. Blocking: call off the main thread.
 */
final class YouTubeArt {
    private static final String TAG = "GlassRecent";
    private static final Pattern VIDEO_ID = Pattern.compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"");

    private YouTubeArt() { }

    /** A video that matched exactly: its id, and its 1280 px thumbnail when it has one. */
    static final class Found {
        final String id;
        final Bitmap picture;
        Found(String id, Bitmap picture) { this.id = id; this.picture = picture; }
    }

    /** The video, or null when no result matches exactly; IOException when YouTube couldn't be reached. */
    static Found find(String title, String channel) throws IOException {
        try {
            String q = URLEncoder.encode(title + (channel.isEmpty() ? "" : " " + channel), "UTF-8");
            String page = new String(get("https://www.youtube.com/results?search_query=" + q, 3_000_000), "UTF-8");
            Set<String> ids = new LinkedHashSet<>();
            for (Matcher m = VIDEO_ID.matcher(page); m.find() && ids.size() < 6; ) ids.add(m.group(1));
            for (String id : ids) {
                JSONObject o;
                try {
                    o = new JSONObject(new String(get("https://www.youtube.com/oembed?format=json&url="
                            + URLEncoder.encode("https://www.youtube.com/watch?v=" + id, "UTF-8"), 100_000), "UTF-8"));
                } catch (IOException e) {
                    continue;  // some videos can't be embedded, so oEmbed refuses them
                }
                if (!squash(o.optString("title")).equals(squash(title))) continue;
                if (!channel.isEmpty() && !squash(o.optString("author_name")).equals(squash(channel))) continue;
                for (String name : new String[]{"maxresdefault.jpg", "hq720.jpg"}) {
                    try {
                        byte[] img = get("https://i.ytimg.com/vi/" + id + "/" + name, 2_000_000);
                        Bitmap b = BitmapFactory.decodeByteArray(img, 0, img.length);
                        if (b != null && b.getWidth() >= 1000) {
                            Log.i(TAG, "Sharp thumbnail for \"" + title + "\": " + name + " " + b.getWidth() + "x" + b.getHeight());
                            return new Found(id, b);
                        }
                    } catch (IOException ignored) {
                        // Not every video has every size.
                    }
                }
                return new Found(id, null);
            }
            Log.i(TAG, "No exact YouTube match for \"" + title + "\"");
        } catch (org.json.JSONException e) {
            Log.w(TAG, "YouTube thumbnail lookup: unexpected reply: " + e);
        }
        return null;
    }

    /** Letters and digits only, lower case: titles compare equal despite emoji, spacing or punctuation. */
    private static String squash(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Accept-Language", "en");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
            if (c.getResponseCode() / 100 != 2) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0 && out.size() < max; ) out.write(b, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
