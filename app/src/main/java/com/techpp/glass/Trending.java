package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two tabs of picks, kept only when TMDB says the title streams (subscription, not rent/buy) in the TV's country
 * (Region) on an app installed on this TV. Idea from Free My TV (MIT). The apps are the SERVICES below, plus any
 * installed app whose name is a streaming service TMDB lists for the country (Viaplay, Canal+, RTL+…).
 * With none of them installed, the same picks open their trailer in YouTube instead ("elsewhere").
 *   Trending      what's popular now on your apps here: titles from this country and English titles, mixed 2:1
 *                 (TMDB's own trending list is global and almost all English), minus anything in "New in";
 *                 in English-speaking countries simply what's popular here
 *   New in …      this country's films (last 6 months) and series (new episodes in the last 60 days)
 *
 * The TMDB key lives in a file on the TV (files/tmdb_key.txt), never in the APK. Picks and their backdrops are
 * cached in files/trending/. The list is refreshed at most every 6 hours, and only when the home screen asks.
 * In India without a key, the picks come from StreamRadar instead (fetchRadar).
 */
final class Trending {
    private static final String TAG = "GlassTrending";
    private static final String API = "https://api.themoviedb.org/3";
    /** Backdrops at 1280 px for the large card (about 1040 px on screen); posters at 342 px for the small ones. */
    private static final String BACKDROPS = "https://image.tmdb.org/t/p/w1280", POSTERS = "https://image.tmdb.org/t/p/w342";
    /** The TV's country, read at each refresh (Region). */
    private String region = "US";
    private static final long REFRESH_MS = 6 * 3600_000L, RETRY_MS = 30 * 60_000L;
    private static final int MAX_PICKS = 24, MAX_CHECKS = 72;
    static final int SHOWN = 4;
    static final String TRENDING = "trending", INDIA = "india", RADAR = "streamradar";
    /** Bump when picks.json changes shape; the next home screen refreshes right away. */
    private static final int CACHE_VERSION = 9;
    /** YouTube on Android TV, Fire TV, then the phone app, for the trailers when no streaming app is installed. */
    private static final String[] YOUTUBE = {"com.google.android.youtube.tv", "com.amazon.firetv.youtube", "com.google.android.youtube"};
    /** For one refresh: no streaming app matched, so picks are trailers in this YouTube app. */
    private String elsewhere;
    /** TMDB genre ids → names, fetched once per refresh. */
    private final Map<Integer, String> genres = new java.util.HashMap<>();

    /** TMDB provider name (lowercase substring) → TV app package, and the name we show. */
    private static final String[][] SERVICES = {
        {"netflix", "com.netflix.ninja", "Netflix"},
        {"prime video", "com.amazon.amazonvideo.livingroom", "Prime Video"},
        {"hotstar", "in.startv.hotstar", "JioHotstar"},
        {"zee5", "com.graymatrix.did", "ZEE5"},
        {"sony liv", "com.sonyliv", "SonyLIV"},
        {"sonyliv", "com.sonyliv", "SonyLIV"},
        {"apple tv", "com.apple.atve.androidtv.appletv", "Apple TV"},
        {"mx player", "com.mxtech.videoplayer.television", "MX Player"},
        {"airtel xstream", "tv.airtel.xstream.tvapp", "Xstream Play"},
        {"fancode", "com.fancode.tv", "FanCode"},
        {"disney plus", "com.disney.disneyplus", "Disney+"},
        {"hulu", "com.hulu.livingroomplus", "Hulu"},
        {"max", "com.wbd.stream", "Max"},
        {"peacock", "com.peacocktv.peacockandroid", "Peacock"},
        {"paramount", "com.cbs.ott", "Paramount+"},
        {"crunchyroll", "com.crunchyroll.crunchyroid", "Crunchyroll"},
        {"mubi", "com.mubi", "MUBI"},
    };

    static final class Pick {
        int id;
        String kind;     // "movie" or "tv"
        String title, pkg, service, tab, lang = "";
        File image;          // landscape picture (TMDB backdrop, or the app's artwork)
        File poster;         // TMDB poster, for the small portrait cards; null when there is none
        transient String backdropPath, posterPath;  // TMDB image paths, while a refresh downloads them
        long at;            // Recently watched: when it was last watched
        String intentUri;   // Recently watched (Watch Next): opens the right title or episode; a trailer pick: the trailer
        String meta = "";   // the line under a big card, e.g. "Film · Action · Telugu · Netflix"
        float progress = -1; // Continue: how far in (0–1), or -1 when unknown
        Object extra;        // Cricket: the Cricket.Match this card shows

        String key() { return "pick:" + tab + ":" + kind + ":" + id; }
    }

    interface Listener { void onPicks(List<Pick> all); }

    private final Context ctx;
    private final File dir;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** For one refresh: several TMDB requests and image downloads at a time (set in fetch, shut down after). */
    private ExecutorService net;
    private final Map<String, java.util.concurrent.Future<JSONObject>> providerLookups = new java.util.HashMap<>();
    private static final int PARALLEL = 6, LOOKAHEAD = 6;
    private static final long SETUP_MS = 7 * 24 * 3600_000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;

    Trending(Context c) {
        ctx = c.getApplicationContext();
        File base = c.getExternalFilesDir(null);
        dir = new File(base, "trending");
        prefs = c.getSharedPreferences("trending", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- languages (Talkies settings → Picks languages)

    /** The original languages picks may be in, as TMDB's codes (ta, en, fr…); empty for all of them. */
    Set<String> languages() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String c : prefs.getString("languages", "").split(",")) if (!c.isEmpty()) out.add(c);
        return out;
    }

    /** Saves the choice; the picks refetch at the next refresh check, not after six hours. */
    void setLanguages(Set<String> codes) {
        prefs.edit().putString("languages", String.join(",", codes)).apply();
    }

    /**
     * The languages this country's lists bring up, most common first, then English: what the Languages screen offers.
     * Before a refresh has counted them, the languages of the saved picks; empty with none.
     */
    List<String> seenLanguages() {
        List<String> out = new ArrayList<>();
        for (String c : prefs.getString("seen_languages", "").split(",")) if (!c.isEmpty()) out.add(c);
        if (!out.isEmpty()) return out;
        Map<String, Integer> count = new LinkedHashMap<>();
        for (Pick p : cached()) if (!p.lang.isEmpty() && !p.lang.equals("xx")) count.merge(p.lang, 1, Integer::sum);
        out.addAll(count.keySet());
        out.sort((x, y) -> count.get(y) - count.get(x));
        if (!out.isEmpty() && !out.contains("en")) out.add("en");
        return out;
    }

    /** "Tamil", "French"; TMDB's own codes Android doesn't know by their name. */
    static String languageName(String code) {
        if (code.equals("cn")) return "Cantonese";
        String name = new Locale(code).getDisplayLanguage(Locale.ENGLISH);
        return name.isEmpty() || name.equals(code) ? code : name;
    }

    /** Counts the original languages in candidate lists, and saves them most common first (English last if absent). */
    private void noteLanguages(boolean replace, List<JSONObject> lists) {
        Map<String, Integer> count = new LinkedHashMap<>();
        if (!replace) {
            List<String> old = seenLanguages();
            for (int i = 0; i < old.size(); i++) count.put(old.get(i), 1000 - i);  // kept, ahead of what's new
        }
        for (JSONObject o : lists) {
            String c = o.optString("original_language", "");
            if (!c.isEmpty() && !c.equals("xx")) count.merge(c, 1, Integer::sum);
        }
        List<String> order = new ArrayList<>(count.keySet());
        order.sort((a, b) -> count.get(b) - count.get(a));
        if (!order.contains("en")) order.add("en");
        prefs.edit().putString("seen_languages", String.join(",", order)).apply();
    }

    private File keyFile() { return new File(dir.getParentFile(), "tmdb_key.txt"); }

    /**
     * Where picks come from: the TMDB key, StreamRadar (RADAR), or null: none. Outside India, the key. In India,
     * Talkies settings → Picks from chooses; until then, TMDB with a key and StreamRadar without one.
     */
    String source() {
        String k = key();
        if (!inIndia()) return k;
        return RADAR.equals(prefs.getString("picks_from", k != null ? "tmdb" : RADAR)) ? RADAR : k;
    }

    /** StreamRadar only knows India's apps, so only a TV in India gets the choice. */
    boolean inIndia() { return "IN".equals(Region.country(ctx)); }

    /** Talkies settings → Picks from: "tmdb" or RADAR. The next refresh check fetches from it right away. */
    void setPicksFrom(String from) { prefs.edit().putString("picks_from", from).apply(); }

    String key() {
        try (InputStream in = new FileInputStream(keyFile())) {
            String k = new String(readAll(in), StandardCharsets.UTF_8).trim();
            return k.isEmpty() ? null : k;
        } catch (IOException e) {
            return null;
        }
    }

    /** Saved picks for one tab whose app is still installed and whose backdrop is on disk. */
    List<Pick> cached(String tab) {
        List<Pick> out = new ArrayList<>();
        for (Pick p : cached()) if (p.tab.equals(tab)) out.add(p);
        return out;
    }

    List<Pick> cached() {
        List<Pick> out = new ArrayList<>();
        if (source() == null) return out;
        try (InputStream in = new FileInputStream(new File(dir, "picks.json"))) {
            JSONArray a = new JSONArray(new String(readAll(in), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Pick p = new Pick();
                p.id = o.getInt("id");
                p.kind = o.getString("kind");
                p.title = o.getString("title");
                p.pkg = o.getString("pkg");
                p.service = o.getString("service");
                p.image = new File(dir, o.getString("image"));
                String poster = o.optString("poster", "");
                p.poster = poster.isEmpty() || !new File(dir, poster).isFile() ? null : new File(dir, poster);
                p.tab = o.optString("tab", TRENDING);
                p.lang = o.optString("lang", "");
                p.meta = o.optString("meta", "");
                String uri = o.optString("uri", "");
                p.intentUri = uri.isEmpty() ? null : uri;
                if (p.image.isFile() && installed(p.pkg)) out.add(p);
            }
        } catch (Exception e) {
            // Nothing saved yet.
        }
        return out;
    }

    /** Hours since the epoch in local time, so the picks change on the hour here (not at :30 in India). */
    static long localHour() {
        long now = System.currentTimeMillis();
        return (now + java.util.TimeZone.getDefault().getOffset(now)) / 3600_000L;
    }

    /** The picks for this hour: SHOWN of them, rotating through the saved list. */
    static List<Pick> forThisHour(List<Pick> all) {
        if (all.size() <= SHOWN) return all;
        long hour = localHour();
        int start = (int) ((hour * SHOWN) % all.size());
        List<Pick> out = new ArrayList<>();
        for (int i = 0; i < SHOWN; i++) out.add(all.get((start + i) % all.size()));
        return out;
    }

    /** Fetches a new list in the background when the saved one is old. Calls back on the main thread. */
    /** The network is back: a fetch that failed while it was down may run again now, without waiting out the retry. */
    void networkBack() {
        if (prefs.getLong("try_at", 0) > prefs.getLong("ok_at", 0)) prefs.edit().remove("try_at").apply();
    }

    void refreshIfStale(Listener l) {
        String key = source();
        if (key == null || running) return;
        long now = System.currentTimeMillis();
        // A new key fetches right away instead of waiting out the old key's timers.
        String cc = Region.country(ctx), was = prefs.getString("region", null);
        if (was == null) prefs.edit().putString("region", cc).apply();  // first run with countries: just remember it
        if (prefs.getInt("key_hash", 0) != key.hashCode() || prefs.getInt("version", 0) != CACHE_VERSION
                || (was != null && !cc.equals(was))
                || !prefs.getString("languages", "").equals(prefs.getString("languages_fetched", ""))) {
            // A new key, a new cache layout, the TV now in another country, or other languages: fetch right away.
            prefs.edit().putInt("key_hash", key.hashCode()).putInt("version", CACHE_VERSION).putString("region", cc)
                    .remove("ok_at").remove("try_at").apply();
        }
        if (now - prefs.getLong("ok_at", 0) < REFRESH_MS || now - prefs.getLong("try_at", 0) < RETRY_MS) return;
        prefs.edit().putLong("try_at", now).apply();
        running = true;
        String langs = prefs.getString("languages", "");
        worker.execute(() -> {
            try {
                fetch(key);
                // Fetched for these languages: if they changed meanwhile, the next check fetches again.
                prefs.edit().putLong("ok_at", System.currentTimeMillis()).putString("languages_fetched", langs).apply();
                List<Pick> all = cached();
                main.post(() -> l.onPicks(all));
            } catch (Exception e) {
                Log.w(TAG, "Refresh failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            } finally {
                main.post(() -> running = false);
            }
        });
    }

    // ---------------------------------------------------------------- fetching

    private void fetch(String key) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        long started = System.currentTimeMillis();
        region = Region.country(ctx);
        net = Executors.newFixedThreadPool(PARALLEL);
        providerLookups.clear();
        try {
            if (RADAR.equals(key)) fetchRadar();
            else fetchWith(key);
        } finally {
            net.shutdownNow();
            providerLookups.clear();
        }
        Log.i(TAG, "Refreshed for " + region + " in " + (System.currentTimeMillis() - started) + " ms");
    }

    /**
     * Genre names and India's list of streaming services hardly ever change: kept for a week (prefs "setup"), which
     * saves four requests per refresh.
     */
    private JSONObject setup(String key) throws Exception {
        try {
            JSONObject saved = new JSONObject(prefs.getString("setup", "{}"));
            if (System.currentTimeMillis() - saved.optLong("at") < SETUP_MS && saved.has("tv_providers")
                    && region.equals(saved.optString("region"))) return saved;
        } catch (Exception ignored) {
            // Fetch afresh.
        }
        JSONObject o = new JSONObject().put("at", System.currentTimeMillis()).put("region", region);
        for (String kind : new String[]{"movie", "tv"}) {
            o.put(kind + "_genres", get(key, "/genre/" + kind + "/list?language=en").getJSONArray("genres"));
            o.put(kind + "_providers", get(key, "/watch/providers/" + kind + "?watch_region=" + region).getJSONArray("results"));
        }
        prefs.edit().putString("setup", o.toString()).apply();
        return o;
    }

    private void fetchWith(String key) throws Exception {
        JSONObject setup = setup(key);
        genres.clear();
        for (String kind : new String[]{"movie", "tv"}) {
            JSONArray g = setup.getJSONArray(kind + "_genres");
            for (int i = 0; i < g.length(); i++) genres.put(g.getJSONObject(i).getInt("id"), g.getJSONObject(i).getString("name"));
        }
        // 1. Which of the country's streaming services TMDB knows are installed here: ours by package, others by name.
        Map<Integer, String[]> services = new LinkedHashMap<>();
        Map<String, String[]> apps = installedByName();
        for (String kind : new String[]{"movie", "tv"}) {
            JSONArray a = setup.getJSONArray(kind + "_providers");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String[] s = service(o.getString("provider_name"));
                if (s != null && !installed(s[1])) s = null;
                if (s == null) s = byName(apps, o.getString("provider_name"));
                if (s != null) services.put(o.getInt("provider_id"), s);
            }
        }
        Log.i(TAG, "Matched " + services.size() + " TMDB services to installed apps");
        prefs.edit().putInt("matched", services.size()).apply();
        elsewhere = null;
        if (services.isEmpty()) {
            for (String y : YOUTUBE) if (elsewhere == null && installed(y)) elsewhere = y;
            if (elsewhere == null) { save(new ArrayList<>()); return; }
            Log.i(TAG, "No streaming apps: trailers in " + elsewhere);
        }

        // 2. Candidates for each tab, then 3. keep those that stream on an installed app (and fetch backdrops).
        StringBuilder ids = new StringBuilder();
        for (int id : services.keySet()) ids.append(ids.length() == 0 ? "" : "|").append(id);
        String common = "&watch_region=" + region + "&with_watch_monetization_types=flatrate&include_adult=false"
                + "&sort_by=popularity.desc" + (elsewhere == null ? "&with_watch_providers=" + ids : "");

        // The six candidate lists, all at once. With languages chosen (Picks languages), "New in …" keeps to them, and
        // Trending's two sides become those languages (from anywhere, not only this country) and English, if chosen.
        Set<String> chosen = languages();
        List<String> others = new ArrayList<>(chosen);
        others.remove("en");
        boolean withEnglish = chosen.isEmpty() || chosen.contains("en");
        String only = chosen.isEmpty() ? "" : "&with_original_language=" + String.join("|", chosen);
        String localBy = chosen.isEmpty() ? "with_origin_country=" + region : "with_original_language=" + String.join("|", others);
        String popular = "&vote_count.gte=10" + common;
        java.util.concurrent.Future<JSONObject> newFilms = later(key, "/discover/movie?with_origin_country=" + region
                + "&primary_release_date.gte=" + day(-180) + "&primary_release_date.lte=" + day(0) + only + common);
        java.util.concurrent.Future<JSONObject> newShows = later(key, "/discover/tv?with_origin_country=" + region
                + "&air_date.gte=" + day(-60) + "&air_date.lte=" + day(0) + only + common);
        boolean anyLocal = chosen.isEmpty() || !others.isEmpty();
        java.util.concurrent.Future<JSONObject> localFilms = anyLocal ? later(key, "/discover/movie?" + localBy + popular) : null;
        java.util.concurrent.Future<JSONObject> localShows = anyLocal ? later(key, "/discover/tv?" + localBy + popular) : null;
        java.util.concurrent.Future<JSONObject> englishFilms = withEnglish ? later(key, "/discover/movie?with_original_language=en" + popular) : null;
        java.util.concurrent.Future<JSONObject> englishShows = withEnglish ? later(key, "/discover/tv?with_original_language=en" + popular) : null;

        // "New in …" first, so Trending can leave its titles out.
        List<JSONObject> fresh = merge(tag(results(newFilms), "movie"), tag(results(newShows), "tv"));
        List<Pick> india = keepMixed(key, services, INDIA, new HashSet<>(), new int[]{1}, fresh);
        Set<String> inIndia = new HashSet<>();
        for (Pick p : india) inIndia.add(p.kind + p.id);

        // Trending: popular titles from this country and popular English titles, two of the first for each one of the
        // second. Where the country's own titles are English anyway (US, UK…), just what's popular here.
        List<JSONObject> local = merge(tag(results(localFilms), "movie"), tag(results(localShows), "tv"));
        List<JSONObject> english = merge(tag(results(englishFilms), "movie"), tag(results(englishShows), "tv"));
        // What languages this country watches, for the Languages screen: all of them only when nothing is chosen.
        List<JSONObject> seen = new ArrayList<>(fresh);
        seen.addAll(local);
        noteLanguages(chosen.isEmpty(), seen);
        List<Pick> trend = Region.englishSpeaking(region)
                ? keepMixed(key, services, TRENDING, inIndia, new int[]{0, 1}, english, local)
                : keepMixed(key, services, TRENDING, inIndia, new int[]{0, 0, 1}, local, english);

        List<Pick> picks = new ArrayList<>(trend);
        picks.addAll(india);
        save(downloadImages(picks));
    }

    /**
     * India without a TMDB key: StreamRadar's lists. Trending is the Top 10s of the apps installed here, taken in turn,
     * films and series alternating; New in India is its "Just In", kept to titles on those apps. OK on a pick opens
     * the title's page in its app where StreamRadar has a link. No genres or languages: its lists don't say.
     */
    private void fetchRadar() throws Exception {
        List<String[]> apps = new ArrayList<>();
        for (String[] a : StreamRadar.APPS) if (installed(a[2])) apps.add(a);
        prefs.edit().putInt("matched", apps.size()).apply();
        Log.i(TAG, "StreamRadar for " + apps.size() + " installed apps");
        if (apps.isEmpty()) { save(new ArrayList<>()); return; }
        List<java.util.concurrent.Future<JSONArray>> tops = new ArrayList<>();
        for (String[] a : apps) {
            tops.add(net.submit(() -> StreamRadar.top(a[0], "movie")));
            tops.add(net.submit(() -> StreamRadar.top(a[0], "series")));
        }
        java.util.concurrent.Future<JSONArray> newFilms = net.submit(() -> StreamRadar.justIn("movie"));
        java.util.concurrent.Future<JSONArray> newShows = net.submit(() -> StreamRadar.justIn("series"));

        // Trending: each app's first title, then each app's second…; a list that fails is left out.
        List<List<JSONObject>> queues = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            List<JSONObject> q = alternate(radarList(tops.get(2 * i)), radarList(tops.get(2 * i + 1)));
            for (JSONObject m : q) m.put("_app", apps.get(i)[0]);
            queues.add(q);
        }
        List<Pick> trend = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (int j = 0; trend.size() < MAX_PICKS; j++) {
            boolean any = false;
            for (List<JSONObject> q : queues) {
                if (j >= q.size() || trend.size() >= MAX_PICKS) continue;
                any = true;
                JSONObject m = q.get(j);
                if (m.optString("background").isEmpty() || !taken.add(m.getString("id"))) continue;
                trend.add(radarPick(m, StreamRadar.app(m.getString("_app")), TRENDING));
            }
            if (!any) break;
        }

        // New in India: newest first, films and series alternating, on an app installed here (one lookup each).
        List<JSONObject> fresh = alternate(radarList(newFilms), radarList(newShows));
        fresh.removeIf(m -> m.optString("background").isEmpty() || taken.contains(m.optString("id")));
        if (fresh.size() > MAX_CHECKS) fresh = fresh.subList(0, MAX_CHECKS);
        List<java.util.concurrent.Future<String[]>> found = new ArrayList<>();
        for (JSONObject m : fresh) found.add(net.submit(() -> StreamRadar.where(m.getString("type"), m.getString("id"), this::installed)));
        List<Pick> india = new ArrayList<>();
        for (int i = 0; i < fresh.size() && india.size() < MAX_PICKS; i++) {
            String[] w = radarGet(found.get(i));
            if (w == null) continue;
            Pick p = radarPick(fresh.get(i), StreamRadar.app(w[0]), INDIA);
            p.intentUri = link(w[1], p.pkg);
            india.add(p);
        }

        // Trending's links, all at once.
        List<java.util.concurrent.Future<String[]>> links = new ArrayList<>();
        for (Pick p : trend) {
            String type = p.kind.equals("tv") ? "series" : "movie", id = p.intentUri;
            links.add(net.submit(() -> StreamRadar.where(type, id, pkg -> pkg.equals(p.pkg))));
        }
        for (int i = 0; i < trend.size(); i++) {
            String[] w = radarGet(links.get(i));
            trend.get(i).intentUri = w == null ? null : link(w[1], trend.get(i).pkg);
        }
        Log.i(TAG, "StreamRadar: " + trend.size() + " trending, " + india.size() + " new");
        List<Pick> picks = new ArrayList<>(trend);
        picks.addAll(india);
        save(downloadImages(picks));
    }

    /** Films and series taken in turn. */
    private static List<JSONObject> alternate(JSONArray films, JSONArray shows) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        for (int j = 0; j < Math.max(films.length(), shows.length()); j++) {
            if (j < films.length()) out.add(films.getJSONObject(j));
            if (j < shows.length()) out.add(shows.getJSONObject(j));
        }
        return out;
    }

    /** A StreamRadar title as a pick on this app. Until its link is looked up, intentUri holds StreamRadar's id. */
    private Pick radarPick(JSONObject m, String[] app, String tab) throws Exception {
        Pick p = new Pick();
        String id = m.getString("id");
        p.id = id.matches("tt\\d{1,9}") ? Integer.parseInt(id.substring(2)) : id.hashCode() & 0x7fffffff;
        p.kind = "series".equals(m.optString("type")) ? "tv" : "movie";
        p.tab = tab;
        p.title = m.optString("name");
        p.pkg = app[2];
        p.service = app[3];
        p.meta = (p.kind.equals("tv") ? "Series" : "Film") + " · " + p.service;
        p.intentUri = id;
        p.image = new File(dir, "sr_" + p.kind + "_" + p.id + "_w1280.jpg");
        p.backdropPath = m.getString("background");
        // Only TMDB's posters: the others come from many sites, some small or cropped. The backdrop does without.
        String poster = m.optString("poster");
        if (poster.startsWith("https://image.tmdb.org/")) {
            p.poster = new File(dir, "sr_" + p.kind + "_" + p.id + "_poster.jpg");
            p.posterPath = poster.replaceFirst("/t/p/[^/]+/", "/t/p/w342/");
        }
        return p;
    }

    /** The app's page for the title, opened in that app. */
    private static String link(String url, String pkg) {
        if (url == null || !url.startsWith("https://")) return null;
        return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage(pkg)
                .toUri(android.content.Intent.URI_INTENT_SCHEME);
    }

    /** A StreamRadar list, or none if it failed: the other apps' lists still make picks. */
    private static JSONArray radarList(java.util.concurrent.Future<JSONArray> f) {
        try {
            return f.get();
        } catch (Exception e) {
            Log.w(TAG, "StreamRadar list failed: " + e.getCause());
            return new JSONArray();
        }
    }

    private static String[] radarGet(java.util.concurrent.Future<String[]> f) {
        try {
            return f.get();
        } catch (Exception e) {
            return null;
        }
    }

    /** A candidate list's titles; none for a list that wasn't asked for. */
    private static JSONArray results(java.util.concurrent.Future<JSONObject> list) throws Exception {
        return list == null ? new JSONArray() : list.get().getJSONArray("results");
    }

    private java.util.concurrent.Future<JSONObject> later(String key, String path) {
        return net.submit(() -> get(key, path));
    }

    /** Starts the "where does it stream?" lookups for the next few titles in a queue, so they're ready when needed. */
    private void lookAhead(String key, List<JSONObject> queue, int from, Set<String> exclude) throws Exception {
        for (int i = from, n = 0; i < queue.size() && n < LOOKAHEAD; i++) {
            JSONObject o = queue.get(i);
            String id = o.getString("_kind") + o.getInt("id");
            if (exclude.contains(id)) continue;
            n++;
            if (!providerLookups.containsKey(id)) {
                providerLookups.put(id, later(key, lookupPath(o.getString("_kind"), o.getInt("id"), o.optString("original_language", ""))));
            }
        }
    }

    /** Backdrops and posters for the chosen picks, several at a time; a pick whose backdrop fails is dropped. */
    private List<Pick> downloadImages(List<Pick> picks) throws Exception {
        List<java.util.concurrent.Future<Boolean>> jobs = new ArrayList<>();
        for (Pick p : picks) {
            jobs.add(net.submit(() -> {
                // StreamRadar's pictures come as whole addresses.
                if (!p.image.isFile()) download(p.backdropPath.startsWith("http") ? p.backdropPath : BACKDROPS + p.backdropPath, p.image);
                if (p.poster != null && !p.poster.isFile()) {
                    try {
                        download(p.posterPath.startsWith("http") ? p.posterPath : POSTERS + p.posterPath, p.poster);
                    } catch (IOException e) {
                        p.poster = null;
                    }
                }
                return true;
            }));
        }
        List<Pick> out = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            try {
                jobs.get(i).get();
                out.add(picks.get(i));
            } catch (java.util.concurrent.ExecutionException e) {
                Log.w(TAG, "No backdrop for " + picks.get(i).title + ": " + e.getCause());
            }
        }
        return out;
    }

    /** Films and series alternating, without repeats or titles that have no backdrop. */
    private static List<JSONObject> merge(JSONArray movies, JSONArray shows) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < Math.max(movies.length(), shows.length()); i++) {
            for (JSONArray a : new JSONArray[]{movies, shows}) {
                if (i >= a.length()) continue;
                JSONObject o = a.getJSONObject(i);
                if (o.optString("backdrop_path", "").isEmpty()) continue;
                if (seen.add(o.getString("_kind") + o.getInt("id"))) out.add(o);
            }
        }
        return out;
    }

    /**
     * Up to MAX_PICKS titles that stream in India on an installed app, taken from the queues in the given pattern
     * ({0, 0, 1} = two from the first queue, then one from the second). When a queue runs dry the others fill in,
     * so the mix only drifts if TMDB really has too few. Titles in {@code exclude} are skipped.
     */
    @SafeVarargs
    private final List<Pick> keepMixed(String key, Map<Integer, String[]> services, String tab, Set<String> exclude,
                                 int[] pattern, List<JSONObject>... queues) throws Exception {
        int[] next = new int[queues.length];
        List<Pick> picks = new ArrayList<>();
        Map<String, Integer> langs = new LinkedHashMap<>();
        int checks = 0;
        for (int slot = 0; picks.size() < MAX_PICKS && checks < MAX_CHECKS; slot++) {
            Pick got = null;
            boolean anyLeft = false;
            // Try this slot's queue first, then the others.
            for (int t = 0; t < queues.length && got == null; t++) {
                int q = (pattern[slot % pattern.length] + t) % queues.length;
                List<JSONObject> queue = queues[q];
                while (got == null && next[q] < queue.size() && checks < MAX_CHECKS) {
                    lookAhead(key, queue, next[q], exclude);
                    JSONObject o = queue.get(next[q]++);
                    if (exclude.contains(o.getString("_kind") + o.getInt("id"))) continue;
                    checks++;
                    got = accept(key, services, tab, o);
                }
                if (next[q] < queue.size()) anyLeft = true;
            }
            if (got != null) {
                picks.add(got);
                exclude.add(got.kind + got.id);
                langs.merge(got.lang, 1, Integer::sum);
            } else if (!anyLeft) break;
        }
        Log.i(TAG, tab + ": " + picks.size() + " picks from " + checks + " checks, languages " + langs);
        return picks;
    }

    /** The title as a pick if it streams (subscription) in India on an installed app; downloads its backdrop. */
    private Pick accept(String key, Map<Integer, String[]> services, String tab, JSONObject o) throws Exception {
        String kind = o.getString("_kind");
        java.util.concurrent.Future<JSONObject> lookup = providerLookups.get(kind + o.getInt("id"));
        JSONObject providers;
        try {
            providers = lookup != null ? lookup.get() : get(key, lookupPath(kind, o.getInt("id"), o.optString("original_language", "")));
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
        }
        JSONObject details = providers;
        if (elsewhere != null) providers = details.getJSONObject("watch/providers");
        JSONObject in = providers.getJSONObject("results").optJSONObject(region);
        JSONArray flat = in == null ? null : in.optJSONArray("flatrate");
        String[] s = null;
        if (elsewhere != null) {
            // No streaming app here: the trailer in YouTube, saying where the title streams.
            String trailer = trailer(details, o.optString("original_language", ""));
            if (trailer == null) return null;
            String where = flat != null && flat.length() > 0 ? flat.getJSONObject(0).getString("provider_name") : null;
            s = new String[]{"", elsewhere, where == null ? "Trailer" : "Trailer · on " + shortName(where)};
            Pick t = new Pick();
            t.intentUri = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/watch?v=" + trailer)).setPackage(elsewhere)
                    .toUri(android.content.Intent.URI_INTENT_SCHEME);
            return fill(t, s, tab, kind, o);
        }
        if (flat == null) return null;
        for (int i = 0; i < flat.length() && s == null; i++) s = services.get(flat.getJSONObject(i).getInt("provider_id"));
        if (s == null) return null;
        return fill(new Pick(), s, tab, kind, o);
    }

    /** Where it streams; with no streaming app here, also its details and videos (for the trailer) in one request. */
    private String lookupPath(String kind, int id, String lang) {
        return "/" + kind + "/" + id + (elsewhere == null ? "/watch/providers"
                : "?append_to_response=videos,watch/providers&include_video_language=en,null" + (lang.matches("[a-z]{2}") ? "," + lang : ""));
    }

    /** The title's YouTube trailer (official ones first, in English or its own language), or null. */
    private static String trailer(JSONObject details, String lang) throws Exception {
        JSONObject v = details.optJSONObject("videos");
        JSONArray a = v == null ? null : v.optJSONArray("results");
        if (a == null) return null;
        String best = null;
        int bestScore = -1;
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.getJSONObject(i);
            if (!"YouTube".equals(x.optString("site")) || x.optString("key").isEmpty()) continue;
            String type = x.optString("type");
            int score = (type.equals("Trailer") ? 4 : type.equals("Teaser") ? 2 : 0) + (x.optBoolean("official") ? 1 : 0);
            if (type.equals("Trailer") || type.equals("Teaser")) {
                if (score > bestScore) { best = x.getString("key"); bestScore = score; }
            }
        }
        return best;
    }

    /** "Amazon Prime Video" → "Prime Video", "Netflix Standard with Ads" → "Netflix": our names where we have one. */
    private static String shortName(String provider) {
        String[] s = service(provider);
        return s != null ? s[2] : provider;
    }

    private Pick fill(Pick p, String[] s, String tab, String kind, JSONObject o) throws Exception {
        p.id = o.getInt("id");
        p.kind = kind;
        p.tab = tab;
        p.lang = o.optString("original_language", "");
        p.title = o.optString(kind.equals("tv") ? "name" : "title", "");
        p.pkg = s[1];
        p.service = s[2];
        // "Film · Action · Telugu · Netflix": kind, first genre, language when not English, the app.
        StringBuilder m = new StringBuilder(kind.equals("tv") ? "Series" : "Film");
        JSONArray g = o.optJSONArray("genre_ids");
        if (g != null && g.length() > 0 && genres.containsKey(g.getInt(0))) {
            m.append(" · ").append(genres.get(g.getInt(0)).replace("Action & Adventure", "Action").replace("Sci-Fi & Fantasy", "Sci-Fi"));
        }
        if (!p.lang.equals("en") && !Region.language(p.lang).isEmpty()) m.append(" · ").append(Region.language(p.lang));
        m.append(" · ").append(p.service);
        p.meta = m.toString();
        // Pictures are downloaded once all picks are chosen (downloadImages), several at a time.
        p.image = new File(dir, kind + "_" + p.id + "_w1280.jpg");
        p.backdropPath = o.getString("backdrop_path");
        String posterPath = o.optString("poster_path", "");
        if (!posterPath.isEmpty() && !posterPath.equals("null")) {
            p.poster = new File(dir, kind + "_" + p.id + "_poster.jpg");
            p.posterPath = posterPath;  // the backdrop alone is fine if this fails
        }
        return p;
    }

    /** Installed TV apps by simplified name ("viaplay", "canal+"), as {"", package, name}. */
    private Map<String, String[]> installedByName() {
        Map<String, String[]> out = new java.util.HashMap<>();
        PackageManager pm = ctx.getPackageManager();
        android.content.Intent q = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String label = String.valueOf(ri.loadLabel(pm)).trim();
            String n = simple(label);
            // YouTube shows the trailers; as a "streaming service" (YouTube Premium) it would only show its originals.
            if (n.length() >= 3 && !n.startsWith("youtube")) out.put(n, new String[]{"", ri.activityInfo.packageName, label});
        }
        return out;
    }

    /**
     * The installed app named like this TMDB service: the same name ("Viaplay", "RTL+"), or one is the other plus
     * more words ("Sky Go" for "Sky Go Italia"), when the shorter is at least four letters.
     */
    private static String[] byName(Map<String, String[]> apps, String provider) {
        String n = simple(provider);
        if (n.isEmpty()) return null;
        String[] exact = apps.get(n);
        if (exact != null) return exact;
        for (Map.Entry<String, String[]> e : apps.entrySet()) {
            String a = e.getKey();
            if ((a.length() >= 4 && n.startsWith(a + " ")) || (n.length() >= 4 && a.startsWith(n + " "))) return e.getValue();
        }
        return null;
    }

    /** Lower case, letters, digits, + and single spaces; "TV" and "Android TV" dropped ("Viaplay TV" → "viaplay"). */
    private static String simple(String name) {
        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+ ]", " ").replaceAll("\\s+", " ").trim();
        n = n.replaceAll("( android)? tv$", "").trim();
        return n;
    }

    /** How many streaming apps the last refresh found (-1: none yet), for the home screen's hint. */
    int matchedApps() { return prefs.getInt("matched", -1); }

    private static String[] service(String providerName) {
        String n = providerName.toLowerCase(Locale.ROOT);
        for (String[] s : SERVICES) if (n.contains(s[0])) return s;
        return null;
    }

    private static JSONArray tag(JSONArray a, String kind) throws Exception {
        for (int i = 0; i < a.length(); i++) a.getJSONObject(i).put("_kind", kind);
        return a;
    }

    private void save(List<Pick> picks) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        JSONArray a = new JSONArray();
        Set<String> keep = new HashSet<>();
        for (Pick p : picks) {
            a.put(new JSONObject().put("id", p.id).put("kind", p.kind).put("tab", p.tab).put("title", p.title)
                    .put("pkg", p.pkg).put("service", p.service).put("lang", p.lang).put("meta", p.meta).put("image", p.image.getName())
                    .put("poster", p.poster == null ? "" : p.poster.getName()).put("uri", p.intentUri == null ? "" : p.intentUri));
            keep.add(p.image.getName());
            if (p.poster != null) keep.add(p.poster.getName());
        }
        File tmp = new File(dir, "picks.json.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) { out.write(a.toString().getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(new File(dir, "picks.json"))) throw new IOException("Cannot save picks");
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.getName().endsWith(".jpg") && !keep.contains(f.getName())) f.delete();
    }

    private boolean installed(String pkg) {
        try {
            return ctx.getPackageManager().getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static String day(int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offset);
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Whether TMDB takes this key (Keys.OK, REJECTED or OFFLINE), for Control Center. Blocking. */
    static int check(String key) {
        try {
            get(key, "/configuration");
            return Keys.OK;
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage());
            return m.startsWith("HTTP 401") || m.startsWith("HTTP 404") ? Keys.REJECTED : Keys.OFFLINE;
        } catch (Exception e) {
            return Keys.REJECTED;  // a malformed key (spaces, odd characters) doesn't even make a URL
        }
    }

    /**
     * TMDB accepts either a v3 API key (query parameter) or a v4 read access token (Bearer header). A connection
     * reset is tried once more: some Indian networks drop the odd TMDB connection, and one lost request out of a
     * hundred would otherwise lose the whole refresh.
     */
    private static JSONObject get(String key, String path) throws Exception {
        try {
            return getOnce(key, path);
        } catch (java.net.SocketException e) {
            return getOnce(key, path);
        }
    }

    private static JSONObject getOnce(String key, String path) throws Exception {
        boolean bearer = key.length() > 40;
        String url = API + path + (bearer ? "" : (path.contains("?") ? "&" : "?") + "api_key=" + key);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer) c.setRequestProperty("Authorization", "Bearer " + key);
        try {
            int code = c.getResponseCode();
            // The path is logged without the key.
            if (code != 200) throw new IOException("HTTP " + code + " for " + path.replaceAll("[?].*", ""));
            try (InputStream in = c.getInputStream()) {
                return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
        // No disconnect() on success: reading the reply to the end returns the connection to the pool, so the next
        // request skips the TLS handshake (each new one cost about a second on this TV).
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        File tmp = new File(to.getPath() + ".part");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] b = new byte[16384];
            for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
        if (!tmp.renameTo(to)) throw new IOException("Cannot save " + to.getName());
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
        return out.toByteArray();
    }
}
