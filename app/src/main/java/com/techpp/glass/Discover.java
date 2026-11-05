package com.techpp.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Three more tabs of picks from TMDB, with the same key as Trending but no filter by your apps: Popular (films and
 * series people are watching now, ranked), Upcoming (films coming out in your country, by release date) and Artists
 * (the most popular actors and filmmakers). Each is a list of picks like the others, so the row, the large card
 * and the remote work the same. Pictures and the lists are cached in files/discover/ and refreshed at most every
 * 6 hours, only when the home screen asks.
 */
final class Discover {
    private static final String TAG = "GlassDiscover";
    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/";
    static final String POPULAR = "popular", UPCOMING = "upcoming", ARTISTS = "artists";
    static final String PKG = "tmdb-list";   // not a real package: marks a pick as one of these lists
    private static final long REFRESH_MS = 6 * 3600_000L, RETRY_MS = 10 * 60_000L;
    private static final int MAX_POPULAR = 15, MAX_UPCOMING = 15, MAX_ARTISTS = 12;
    /** The large card's picture at twice its size (440 × 186 dp), and the small cards' poster width. */
    private static final int WIDE_W = 880, WIDE_H = 372, POSTER_W = 342;

    interface Listener { void onDiscover(); }

    private final Context ctx;
    private final File dir;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastTry, lastOk;
    private boolean busy;

    Discover(Context ctx) {
        this.ctx = ctx;
        dir = new File(ctx.getExternalFilesDir(null), "discover");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** The picks of POPULAR, UPCOMING or ARTISTS from the last download; empty until the first one finishes. */
    List<Trending.Pick> cached(String tab) {
        List<Trending.Pick> out = new ArrayList<>();
        try {
            File f = new File(dir, "lists.json");
            if (!f.isFile()) return out;
            JSONArray a = new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).optJSONArray(tab);
            long today = startOfToday();
            for (int i = 0; a != null && i < a.length(); i++) {
                Trending.Pick p = pick(a.getJSONObject(i), tab);
                if (p == null || !p.image.isFile() || !p.poster.isFile()) continue;
                if (UPCOMING.equals(tab) && p.at < today) continue;   // already out
                out.add(p);
            }
        } catch (Exception e) {
            Log.w(TAG, "No cached list: " + e);
        }
        return out;
    }

    private Trending.Pick pick(JSONObject o, String tab) {
        Trending.Pick p = new Trending.Pick();
        p.id = o.optInt("id");
        p.kind = o.optString("kind");
        p.tab = tab;
        p.pkg = PKG;
        p.title = o.optString("title");
        p.meta = o.optString("meta");
        p.at = o.optLong("at");
        p.intentUri = o.optString("url");
        p.image = new File(dir, p.kind + "_" + p.id + "_w2.jpg");
        p.poster = new File(dir, p.kind + "_" + p.id + "_p.jpg");
        return p.title.isEmpty() ? null : p;
    }

    private static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    void refreshIfStale(Listener l) {
        String key = Keys.read(ctx, Keys.TMDB);
        long now = System.currentTimeMillis();
        if (key == null || busy || now - lastTry < RETRY_MS || (lastOk != 0 && now - lastOk < REFRESH_MS)) return;
        busy = true;
        lastTry = now;
        final String region = Region.country(ctx);
        worker.execute(() -> {
            boolean ok = false;
            java.util.concurrent.ExecutorService net = Executors.newFixedThreadPool(3);
            try {
                Map<Integer, String> movieGenres = genres(key, "movie"), tvGenres = genres(key, "tv");
                JSONObject lists = new JSONObject();
                Set<String> used = new HashSet<>(Collections.singleton("lists.json"));
                // One list at a time, its pictures three at a time, and each list shown as soon as it is whole, so
                // the first cards appear within seconds of the first run instead of after all forty pictures.
                for (String tab : new String[]{POPULAR, UPCOMING, ARTISTS}) {
                    JSONArray items = POPULAR.equals(tab) ? popular(key, region, movieGenres, tvGenres)
                            : UPCOMING.equals(tab) ? upcoming(key, region, movieGenres) : artists(key);
                    lists.put(tab, withPictures(items, net, used));
                    // Keep the tabs not reached yet as they were, so a refresh never empties one.
                    write(new File(dir, "lists.json"), merged(lists).toString().getBytes(StandardCharsets.UTF_8));
                    main.post(l::onDiscover);
                }
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) if (!used.contains(f.getName())) f.delete();
                ok = true;
            } catch (Exception e) {
                Log.w(TAG, "Refresh failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            } finally {
                net.shutdownNow();
            }
            final boolean fine = ok;
            main.post(() -> {
                busy = false;
                if (fine) lastOk = System.currentTimeMillis();
            });
        });
    }

    /** The lists fetched so far, with the previous ones for the tabs still to come. */
    private JSONObject merged(JSONObject fresh) throws Exception {
        JSONObject out = new JSONObject();
        File f = new File(dir, "lists.json");
        JSONObject old = f.isFile() ? new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
        for (String tab : new String[]{POPULAR, UPCOMING, ARTISTS}) {
            if (fresh.has(tab)) out.put(tab, fresh.get(tab));
            else if (old.has(tab)) out.put(tab, old.get(tab));
        }
        return out;
    }

    /** The items whose pictures could be had, in order; the pictures are fetched in parallel on `net`. */
    private JSONArray withPictures(JSONArray items, java.util.concurrent.ExecutorService net, Set<String> used) throws Exception {
        List<java.util.concurrent.Future<Boolean>> jobs = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            final JSONObject o = items.getJSONObject(i);
            final String base = o.optString("kind") + "_" + o.optInt("id");
            jobs.add(net.submit(() -> fetchPictures(base, o.optString("path"), o.optString("size"))));
        }
        JSONArray kept = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            if (jobs.get(i).get()) {
                String base = o.optString("kind") + "_" + o.optInt("id");
                used.add(base + "_w2.jpg");
                used.add(base + "_p.jpg");
                kept.put(o);
            }
        }
        return kept;
    }

    private static Map<Integer, String> genres(String key, String kind) throws Exception {
        Map<Integer, String> out = new HashMap<>();
        JSONArray g = get(key, "/genre/" + kind + "/list?language=en").getJSONArray("genres");
        for (int i = 0; i < g.length(); i++) {
            out.put(g.getJSONObject(i).getInt("id"), g.getJSONObject(i).getString("name")
                    .replace("Action & Adventure", "Action").replace("Sci-Fi & Fantasy", "Sci-Fi").replace("War & Politics", "War"));
        }
        return out;
    }

    /** Films and series people are watching now, taken turn about, ranked in that order. */
    private JSONArray popular(String key, String region, Map<Integer, String> mg, Map<Integer, String> tg) throws Exception {
        JSONArray m = get(key, "/movie/popular?language=en-US&region=" + region + "&page=1").getJSONArray("results");
        JSONArray t = get(key, "/tv/popular?language=en-US&page=1").getJSONArray("results");
        JSONArray out = new JSONArray();
        for (int i = 0; i < Math.max(m.length(), t.length()) && out.length() < MAX_POPULAR; i++) {
            if (i < m.length()) add(out, m.getJSONObject(i), "movie", mg, true);
            if (i < t.length() && out.length() < MAX_POPULAR) add(out, t.getJSONObject(i), "tv", tg, true);
        }
        return out;
    }

    /** Films coming out in the country from today, soonest first. */
    private JSONArray upcoming(String key, String region, Map<Integer, String> mg) throws Exception {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        List<JSONObject> all = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            JSONArray r = get(key, "/movie/upcoming?language=en-US&region=" + region + "&page=" + page).getJSONArray("results");
            for (int i = 0; i < r.length(); i++) {
                JSONObject o = r.getJSONObject(i);
                if (o.optString("release_date").compareTo(today) >= 0 && !o.isNull("poster_path")) all.add(o);
            }
        }
        Collections.sort(all, (x, y) -> x.optString("release_date").compareTo(y.optString("release_date")));
        JSONArray out = new JSONArray();
        for (JSONObject o : all) if (out.length() < MAX_UPCOMING) add(out, o, "movie", mg, false);
        return out;
    }

    /** The most popular actors and filmmakers, with what they are known for. */
    private JSONArray artists(String key) throws Exception {
        JSONArray r = get(key, "/person/popular?language=en-US&page=1").getJSONArray("results");
        JSONArray out = new JSONArray();
        for (int i = 0; i < r.length() && out.length() < MAX_ARTISTS; i++) {
            JSONObject o = r.getJSONObject(i);
            if (o.isNull("profile_path") || o.optString("name").isEmpty()) continue;
            StringBuilder known = new StringBuilder();
            JSONArray k = o.optJSONArray("known_for");
            for (int j = 0; k != null && j < k.length() && j < 2; j++) {
                String n = k.getJSONObject(j).optString("title", k.getJSONObject(j).optString("name"));
                if (!n.isEmpty()) known.append(known.length() == 0 ? "" : ", ").append(n);
            }
            String dept = o.optString("known_for_department", "Acting");
            JSONObject item = new JSONObject();
            item.put("id", o.getInt("id")).put("kind", "person").put("title", o.getString("name"))
                    .put("meta", ("Acting".equals(dept) ? "Actor" : dept) + (known.length() > 0 ? "  ·  " + known : ""))
                    .put("path", o.getString("profile_path")).put("size", "h632")
                    .put("url", "https://www.themoviedb.org/person/" + o.getInt("id"));
            out.put(item);
        }
        return out;
    }

    /** One film or series as a list item: "Film · Action · ★ 7.4" (rated ones) or "Film · Action · Drama". */
    private static void add(JSONArray out, JSONObject o, String kind, Map<Integer, String> genres, boolean rated) throws Exception {
        String title = o.optString(kind.equals("tv") ? "name" : "title");
        if (title.isEmpty() || o.isNull("poster_path")) return;
        String date = o.optString(kind.equals("tv") ? "first_air_date" : "release_date");
        long at = 0;
        try { at = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).getTime(); } catch (Exception ignored) { }
        StringBuilder meta = new StringBuilder(kind.equals("tv") ? "Series" : "Film");
        JSONArray g = o.optJSONArray("genre_ids");
        for (int i = 0; g != null && i < g.length() && i < (rated ? 1 : 2); i++) {
            String name = genres.get(g.getInt(i));
            if (name != null) meta.append("  ·  ").append(name);
        }
        double vote = o.optDouble("vote_average", 0);
        if (rated && vote > 0) meta.append("  ·  ★ ").append(String.format(Locale.US, "%.1f", vote));
        JSONObject item = new JSONObject();
        item.put("id", o.getInt("id")).put("kind", kind).put("title", title).put("meta", meta.toString()).put("at", at)
                .put("path", o.getString("poster_path")).put("size", "w342")
                .put("url", "https://www.themoviedb.org/" + kind + "/" + o.getInt("id"));
        out.put(item);
    }

    // ---------------------------------------------------------------- TMDB

    /** TMDB accepts either a v3 API key (query parameter) or a v4 read access token (Bearer header). */
    private static JSONObject get(String key, String path) throws Exception {
        boolean bearer = key.length() > 40;
        String url = API + path + (bearer ? "" : (path.contains("?") ? "&" : "?") + "api_key=" + key);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer) c.setRequestProperty("Authorization", "Bearer " + key);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " for " + path.replaceAll("[?].*", ""));   // never the key
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
    }

    /** Downloads a picture once and makes its two sizes. False when it can't be had. */
    private boolean fetchPictures(String base, String path, String size) {
        if (path == null || path.isEmpty() || path.equals("null")) return false;
        File wide = new File(dir, base + "_w2.jpg"), poster = new File(dir, base + "_p.jpg");
        if (wide.isFile() && poster.isFile()) return true;
        try {
            byte[] raw = bytes(IMG + size + path);
            Bitmap src = decode(raw);
            if (src == null) return false;
            render(src, poster, wide);
            src.recycle();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Picture failed: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static byte[] bytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- pictures

    private static Bitmap decode(byte[] raw) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        int sample = 1;
        while (o.outWidth / sample > 1300 || o.outHeight / sample > 1300) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
    }

    /**
     * The small poster, and the large card's picture: the poster itself on the right, over a blurred copy of it that
     * fills the card, darker on the left where the title goes (the landscape card of the site's Discover).
     */
    private static void render(Bitmap src, File poster, File wide) throws Exception {
        int ph = Math.round(src.getHeight() * (POSTER_W / (float) src.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(src, POSTER_W, Math.max(1, Math.min(ph, 600)), true);
        save(small, poster, 88);

        Bitmap out = Bitmap.createBitmap(WIDE_W, WIDE_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        // A blur by shrinking to a few pixels and stretching back over the card.
        int tw = 24, th = Math.max(1, Math.round(24f * src.getHeight() / src.getWidth()));
        Bitmap tiny = Bitmap.createScaledBitmap(src, tw, th, true);
        float k = Math.max(WIDE_W / (float) tw, WIDE_H / (float) th) * 1.15f;
        Matrix m = new Matrix();
        m.postScale(k, k);
        m.postTranslate((WIDE_W - tw * k) / 2f, (WIDE_H - th * k) / 2f);
        Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(tiny, m, smooth);
        tiny.recycle();
        c.drawColor(0x730B0B0E);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, 0, WIDE_W, 0, new int[]{0xE60A0A0C, 0x990A0A0C, 0x000A0A0C}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, WIDE_W, WIDE_H, shade);

        float h = WIDE_H * 0.84f, w = Math.min(h * src.getWidth() / src.getHeight(), 330f);
        float x = WIDE_W - w - 44f, y = (WIDE_H - h) / 2f;
        RectF box = new RectF(x, y, x + w, y + h);
        Path clip = new Path();
        clip.addRoundRect(box, 26f, 26f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        float s = Math.max(w / src.getWidth(), h / src.getHeight());
        Matrix pm = new Matrix();
        pm.postScale(s, s);
        pm.postTranslate(x + (w - src.getWidth() * s) / 2f, y + (h - src.getHeight() * s) / 2f);
        c.drawBitmap(src, pm, smooth);
        c.restore();
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(2f);
        rim.setColor(0x33FFFFFF);
        c.drawRoundRect(box, 26f, 26f, rim);
        save(out, wide, 86);
        out.recycle();
        small.recycle();
    }

    private static void save(Bitmap b, File f, int quality) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            b.compress(Bitmap.CompressFormat.JPEG, quality, o);
        }
        if (!tmp.renameTo(f)) throw new java.io.IOException("rename");
    }

    private static void write(File f, byte[] data) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(data);
        }
        if (!tmp.renameTo(f)) throw new java.io.IOException("rename");
    }
}
