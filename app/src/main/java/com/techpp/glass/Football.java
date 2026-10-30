package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Football scores from ESPN's public scoreboard (site.api.espn.com): no key, every major league. It isn't a documented
 * API, so it may change without notice; if it fails, the Football row just stays away. Leagues: the ones you chose in
 * Control Center → Football leagues, else a default set for the TV's country (Region). Shows matches live now,
 * starting in the next day, or finished in the last 12 hours. Asks every 15 minutes, and every minute for the
 * leagues with a match on (at least 30 s apart, so every minute tick of the home screen gets one), only while the home screen is showing. The matches reuse Cricket.Match, so the score line,
 * row and cards are drawn the same way.
 */
final class Football {
    private static final String TAG = "GlassFootball";
    private static final String API = "https://site.api.espn.com/apis/site/v2/sports/soccer/";
    private static final long FULL_MS = 15 * 60_000L, LIVE_MS = 30_000L, RETRY_MS = 5 * 60_000L;
    private static final long PAST_MS = 12 * 3600_000L, AHEAD_MS = 24 * 3600_000L;
    private static final int MAX = 12;

    /**
     * ESPN's league ids, the names shown in Control Center and on the cards, and the short ones fans use, for the
     * score line (10 characters at most), in menu order.
     */
    static final String[][] LEAGUES = {
            {"eng.1", "Premier League", "EPL"}, {"uefa.champions", "Champions League", "UCL"},
            {"esp.1", "LaLiga", "LaLiga"}, {"ger.1", "Bundesliga", "Bundesliga"}, {"ita.1", "Serie A", "Serie A"},
            {"fra.1", "Ligue 1", "Ligue 1"}, {"uefa.europa", "Europa League", "UEL"},
            {"ind.1", "Indian Super League", "ISL"}, {"usa.1", "MLS", "MLS"}, {"ned.1", "Eredivisie", "Eredivisie"},
            {"por.1", "Primeira Liga", "Primeira"}, {"sco.1", "Scottish Premiership", "SPFL"},
            {"tur.1", "S\u00fcper Lig", "S\u00fcper Lig"},
            {"bra.1", "Brasileirão", "Brazil"}, {"arg.1", "Liga Profesional", "Argentina"},
            {"mex.1", "Liga MX", "Liga MX"}, {"ksa.1", "Saudi Pro League", "Saudi Pro"}, {"aus.1", "A-League", "A-League"},
            {"jpn.1", "J1 League", "J1"}, {"eng.fa", "FA Cup", "FA Cup"}, {"fifa.world", "World Cup", "World Cup"},
            {"uefa.euro", "Euro", "Euro"}};
    /** Each country's default leagues (the World Cup and the Euros join everywhere: they only have matches when on). */
    private static final Map<String, String[]> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put("IN", new String[]{"eng.1", "uefa.champions", "ind.1"});
        DEFAULTS.put("TR", new String[]{"tur.1", "uefa.champions", "eng.1"});
        DEFAULTS.put("GB", new String[]{"eng.1", "uefa.champions", "sco.1"});
        DEFAULTS.put("IE", new String[]{"eng.1", "uefa.champions"});
        DEFAULTS.put("US", new String[]{"usa.1", "eng.1", "uefa.champions"});
        DEFAULTS.put("CA", new String[]{"usa.1", "eng.1", "uefa.champions"});
        DEFAULTS.put("ES", new String[]{"esp.1", "uefa.champions"});
        DEFAULTS.put("DE", new String[]{"ger.1", "uefa.champions"});
        DEFAULTS.put("AT", new String[]{"ger.1", "uefa.champions"});
        DEFAULTS.put("IT", new String[]{"ita.1", "uefa.champions"});
        DEFAULTS.put("FR", new String[]{"fra.1", "uefa.champions"});
        DEFAULTS.put("NL", new String[]{"ned.1", "uefa.champions"});
        DEFAULTS.put("PT", new String[]{"por.1", "uefa.champions"});
        DEFAULTS.put("BR", new String[]{"bra.1"});
        DEFAULTS.put("AR", new String[]{"arg.1"});
        DEFAULTS.put("MX", new String[]{"mex.1", "usa.1"});
        DEFAULTS.put("SA", new String[]{"ksa.1", "uefa.champions"});
        DEFAULTS.put("AU", new String[]{"aus.1", "eng.1"});
        DEFAULTS.put("JP", new String[]{"jpn.1", "eng.1"});
    }

    interface Listener { void onMatches(List<Cricket.Match> matches); }

    private final Context ctx;
    private final File dir;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;

    Football(Context c) {
        ctx = c.getApplicationContext();
        dir = new File(c.getExternalFilesDir(null), "football");
        prefs = c.getSharedPreferences("football", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- leagues

    static String leagueName(String id) {
        for (String[] l : LEAGUES) if (l[0].equals(id)) return l[1];
        return id;
    }

    static String leagueShort(String id) {
        for (String[] l : LEAGUES) if (l[0].equals(id)) return l[2];
        return id;
    }

    /** The leagues to show, in menu order: your choice, or the TV's country's defaults. */
    List<String> leagues() {
        String saved = prefs.getString("leagues", null);
        Set<String> on = new LinkedHashSet<>();
        if (saved != null) {
            if (!saved.isEmpty()) on.addAll(Arrays.asList(saved.split(",")));
        } else {
            String[] d = DEFAULTS.get(Region.country(ctx));
            on.addAll(Arrays.asList(d != null ? d : new String[]{"eng.1", "uefa.champions"}));
            on.add("fifa.world");
            on.add("uefa.euro");
        }
        List<String> out = new ArrayList<>();
        for (String[] l : LEAGUES) if (on.contains(l[0])) out.add(l[0]);
        return out;
    }

    boolean customLeagues() { return prefs.contains("leagues"); }

    /** Turns one league on or off; the defaults become your own list from then on. */
    void toggle(String id) {
        List<String> on = leagues();
        if (!on.remove(id)) on.add(id);
        prefs.edit().putString("leagues", String.join(",", on)).remove("ok_at").remove("try_at").apply();
    }

    void useDefaults() { prefs.edit().remove("leagues").remove("ok_at").remove("try_at").apply(); }

    // ---------------------------------------------------------------- fetching

    /** The last matches fetched, still within the window. Blocking (reads a small file). */
    List<Cricket.Match> cached() {
        try (InputStream in = new FileInputStream(new File(dir, "matches.json"))) {
            JSONArray a = new JSONArray(new String(readAll(in), StandardCharsets.UTF_8));
            List<String> on = leagues();
            List<Cricket.Match> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!on.contains(o.optString("league"))) continue;
                long at = o.optLong("at");
                if (at < now - PAST_MS || at > now + AHEAD_MS) continue;
                out.add(fromJson(o));
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** The network is back: a fetch that failed while it was down may run again now, without waiting out the retry. */
    void networkBack() {
        if (prefs.getLong("try_at", 0) > prefs.getLong("ok_at", 0)) prefs.edit().remove("try_at").apply();
    }

    void refreshIfStale(Listener l) {
        if (running) return;
        List<String> on = leagues();
        if (on.isEmpty()) return;
        // A new set of leagues (a country's new default after an update, say): fetch now, not when the last list goes stale.
        String sig = String.join(",", on);
        if (!sig.equals(prefs.getString("sig", ""))) prefs.edit().putString("sig", sig).remove("ok_at").remove("try_at").apply();
        long now = System.currentTimeMillis();
        boolean full = now - prefs.getLong("ok_at", 0) >= FULL_MS;
        String live = prefs.getString("live", "");  // "league@date,…" with a match on or about to start
        boolean liveDue = !live.isEmpty() && now - prefs.getLong("live_at", 0) >= LIVE_MS;
        if (!full && !liveDue) return;
        if (full && now - prefs.getLong("try_at", 0) < RETRY_MS && !liveDue) return;
        if (full) prefs.edit().putLong("try_at", now).apply();
        prefs.edit().putLong("live_at", now).apply();
        running = true;
        worker.execute(() -> {
            try {
                List<Cricket.Match> all = fetch(on, full ? null : live);
                main.post(() -> l.onMatches(all));
            } catch (Exception e) {
                Log.w(TAG, "Football failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            } finally {
                main.post(() -> running = false);
            }
        });
    }

    /**
     * Fetches every league for each date around now (ESPN takes one date per request), or with `only` set just the
     * league-dates that had a match on, merged into what's saved. Saves and returns the matches to show.
     */
    private List<Cricket.Match> fetch(List<String> on, String only) throws Exception {
        long now = System.currentTimeMillis();
        Map<String, JSONObject> byId = new HashMap<>();
        if (only != null) {
            // Keep the saved matches; the live ones get replaced below.
            try (InputStream in = new FileInputStream(new File(dir, "matches.json"))) {
                JSONArray a = new JSONArray(new String(readAll(in), StandardCharsets.UTF_8));
                for (int i = 0; i < a.length(); i++) byId.put(a.getJSONObject(i).getString("id"), a.getJSONObject(i));
            } catch (IOException ignored) {
                only = null;  // nothing saved: fetch everything
            }
        }
        Set<String> pairs = new LinkedHashSet<>();
        if (only != null) {
            pairs.addAll(Arrays.asList(only.split(",")));
        } else {
            for (String league : on) for (String d : dates(now)) pairs.add(league + "@" + d);
        }
        int fetched = 0;
        for (String pair : pairs) {
            String league = pair.substring(0, pair.indexOf('@')), date = pair.substring(pair.indexOf('@') + 1);
            if (!on.contains(league)) continue;
            JSONObject d;
            try {
                d = get(API + league + "/scoreboard?dates=" + date);
            } catch (IOException e) {
                Log.w(TAG, league + " " + date + ": " + e.getMessage());
                continue;
            }
            fetched++;
            JSONArray events = d.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject m = parse(events.getJSONObject(i), league, date);
                if (m != null) byId.put(m.getString("id"), m);
            }
        }
        if (fetched == 0) throw new IOException("no league answered");

        List<JSONObject> keep = new ArrayList<>();
        Set<String> live = new LinkedHashSet<>();
        for (JSONObject o : byId.values()) {
            long at = o.optLong("at");
            if (!on.contains(o.optString("league")) || at < now - PAST_MS || at > now + AHEAD_MS) continue;
            keep.add(o);
            boolean soon = !o.optBoolean("ended") && at - now < 15 * 60_000L;
            if (o.optBoolean("live") || soon) live.add(o.optString("league") + "@" + o.optString("date"));
        }
        // Live matches first (in your leagues' order), then the next to start, then the latest results.
        keep.sort((a, b) -> {
            int ga = group(a), gb = group(b);
            if (ga != gb) return ga - gb;
            if (ga == 0) {
                int la = on.indexOf(a.optString("league")), lb = on.indexOf(b.optString("league"));
                if (la != lb) return la - lb;
            }
            return ga == 2 ? Long.compare(b.optLong("at"), a.optLong("at")) : Long.compare(a.optLong("at"), b.optLong("at"));
        });
        JSONArray save = new JSONArray();
        List<Cricket.Match> out = new ArrayList<>();
        for (int i = 0; i < keep.size() && i < MAX * 2; i++) save.put(keep.get(i));
        for (int i = 0; i < keep.size() && i < MAX; i++) out.add(fromJson(keep.get(i)));
        write(save);
        SharedPreferences.Editor e = prefs.edit().putString("live", String.join(",", live));
        if (only == null) e.putLong("ok_at", now);
        e.apply();
        Log.i(TAG, out.size() + " matches, " + live.size() + " league-dates live" + (only == null ? "" : " (live update)"));
        return out;
    }

    private static int group(JSONObject o) { return o.optBoolean("live") ? 0 : !o.optBoolean("ended") ? 1 : 2; }

    /** The dates (yyyyMMdd) that the window around now touches, in UTC and in US Eastern (ESPN's own day). */
    private static Set<String> dates(long now) {
        Set<String> out = new LinkedHashSet<>();
        for (String zone : new String[]{"UTC", "America/New_York"}) {
            SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone(zone));
            for (long t : new long[]{now - PAST_MS, now, now + AHEAD_MS}) out.add(f.format(new Date(t)));
        }
        return out;
    }

    /** One ESPN event as the small record saved and shown, or null if it's unusable. */
    private static JSONObject parse(JSONObject e, String league, String date) throws Exception {
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject st = c.getJSONObject("status"), type = st.getJSONObject("type");
        JSONArray teams = c.getJSONArray("competitors");
        if (teams.length() != 2) return null;
        JSONObject home = teams.getJSONObject(0), away = teams.getJSONObject(1);
        if ("away".equals(home.optString("homeAway"))) { JSONObject t = home; home = away; away = t; }
        Date at = parseUtc(e.optString("date"));
        if (at == null) return null;
        String state = type.optString("state");  // pre, in, post
        String name = type.optString("name");
        JSONObject o = new JSONObject();
        o.put("id", e.getString("id"));
        o.put("league", league);
        o.put("date", date);
        o.put("at", at.getTime());
        o.put("live", "in".equals(state));
        o.put("ended", "post".equals(state));
        // "67'", "HT", "FT", "Postponed": what the card and the score line say.
        String status;
        if (name.contains("POSTPONED") || name.contains("CANCELED") || name.contains("ABANDONED")) status = type.optString("description");
        else if ("in".equals(state)) status = name.contains("HALFTIME") ? "Half-time" : st.optString("displayClock");
        else if ("post".equals(state)) status = type.optString("detail", "FT").replace("FT", "Full time");
        else status = "";
        o.put("status", status);
        o.put("t1", home.getJSONObject("team").optString("shortDisplayName"));
        o.put("t2", away.getJSONObject("team").optString("shortDisplayName"));
        o.put("a1", home.getJSONObject("team").optString("abbreviation"));
        o.put("a2", away.getJSONObject("team").optString("abbreviation"));
        o.put("full1", home.getJSONObject("team").optString("displayName"));
        o.put("full2", away.getJSONObject("team").optString("displayName"));
        o.put("img1", home.getJSONObject("team").optString("logo"));
        o.put("img2", away.getJSONObject("team").optString("logo"));
        o.put("s1", "pre".equals(state) ? "" : home.optString("score"));
        o.put("s2", "pre".equals(state) ? "" : away.optString("score"));
        return o;
    }

    private static Cricket.Match fromJson(JSONObject o) {
        Cricket.Match m = new Cricket.Match();
        m.football = true;
        m.id = "fb" + o.optString("id");
        m.live = o.optBoolean("live");
        m.ended = o.optBoolean("ended");
        m.kind = leagueName(o.optString("league"));
        m.league = leagueShort(o.optString("league"));
        m.t1 = o.optString("t1");
        m.t2 = o.optString("t2");
        m.short1 = o.optString("a1");
        m.short2 = o.optString("a2");
        m.full1 = o.optString("full1");
        m.full2 = o.optString("full2");
        m.s1 = o.optString("s1");
        m.s2 = o.optString("s2");
        m.img1 = o.optString("img1");
        m.img2 = o.optString("img2");
        m.status = o.optString("status");
        m.name = m.full1 + " v " + m.full2;
        m.start = m.live || m.ended ? null
                : new SimpleDateFormat("h:mm a", Locale.US).format(new Date(o.optLong("at"))).toLowerCase(Locale.ROOT);
        if (!m.live && !m.ended && m.status.isEmpty()) {
            // Not today: say which day ("Sat 4:30 pm").
            SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
            if (!day.format(new Date()).equals(day.format(new Date(o.optLong("at"))))) {
                m.start = new SimpleDateFormat("EEE", Locale.US).format(new Date(o.optLong("at"))) + " " + m.start;
            }
        }
        return m;
    }

    private static Date parseUtc(String s) {
        for (String p : new String[]{"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"}) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(s);
            } catch (Exception ignored) {
                // Try the next form.
            }
        }
        return null;
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Talkies/1.0 (Android TV launcher)");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
    }

    private void write(JSONArray a) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File tmp = new File(dir, "matches.json.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) { out.write(a.toString().getBytes(StandardCharsets.UTF_8)); }
        if (!tmp.renameTo(new File(dir, "matches.json"))) throw new IOException("Cannot save matches");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }
}
