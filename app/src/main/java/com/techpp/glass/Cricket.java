package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.tv.TvContract;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cricket scores from ESPN's public score panel (site.web.api.espn.com, no key; like Football, it isn't a documented
 * API and may change without notice): international matches (men's and women's) and the domestic competitions of the
 * TV's country (Region), live or on today; the home nation's matches come first. Asks every minute while a match is
 * on (at least 30 s apart, so every minute tick of the home screen gets one; ESPN updates ball by ball) and every 30
 * minutes otherwise, only while the home screen is showing.
 * Watching: the apps' own TV channels are searched for a card naming both teams (that card's link opens the stream).
 * Otherwise, in India, the app that holds the rights there opens; elsewhere rights vary too much to guess, so the
 * card opens nothing and says so.
 */
final class Cricket {
    private static final String TAG = "GlassCricket";
    private static final String API = "https://site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates=";
    private static final long LIVE_MS = 30_000L, IDLE_MS = 30 * 60_000L, RETRY_MS = 5 * 60_000L;
    /** Statuses of a match that's on but not being played right now ("Stumps", "Lunch", "Rain delay"). */
    private static final java.util.regex.Pattern PAUSED = java.util.regex.Pattern.compile(
            "\\b(stumps|lunch|tea|dinner|rain|wet outfield|bad light|delay|delayed|play stopped|no play|suspended)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    /** "131/5 (21.2/50 ov, target 130)" → "131/5 (21.2)": the status line already says what's needed. */
    private static final java.util.regex.Pattern OVERS = java.util.regex.Pattern.compile("\\(([\\d.]+)(?:/\\d+)? ov[^)]*\\)");

    /** Each cricket country's domestic competitions (matched in the series name), by the TV's country code. */
    private static final java.util.Map<String, String[]> DOMESTIC = new java.util.HashMap<>();
    /** The TV's country code → its national team's name in the feed. */
    private static final java.util.Map<String, String> HOME_TEAM = new java.util.HashMap<>();
    static {
        DOMESTIC.put("IN", new String[]{"indian premier league", "ipl", "women's premier league", "ranji trophy",
                "syed mushtaq ali", "vijay hazare", "duleep trophy", "irani cup", "irani trophy", "deodhar trophy",
                "cooch behar", "cos nayudu", "ck nayudu"});
        DOMESTIC.put("AU", new String[]{"big bash", "sheffield shield", "marsh cup", "one-day cup", "domestic one-day"});
        DOMESTIC.put("GB", new String[]{"county championship", "the hundred", "vitality blast", "t20 blast", "one-day cup"});
        DOMESTIC.put("NZ", new String[]{"super smash", "plunket shield", "ford trophy"});
        DOMESTIC.put("ZA", new String[]{"sa20", "csa t20", "south africa domestic", "4-day series", "one-day cup"});
        DOMESTIC.put("PK", new String[]{"pakistan super league", "psl", "quaid-e-azam", "national t20"});
        DOMESTIC.put("LK", new String[]{"lanka premier league"});
        DOMESTIC.put("BD", new String[]{"bangladesh premier league", "bpl"});
        DOMESTIC.put("AE", new String[]{"ilt20", "international league t20"});
        DOMESTIC.put("US", new String[]{"major league cricket"});
        String[][] home = {{"IN", "india"}, {"AU", "australia"}, {"GB", "england"}, {"NZ", "new zealand"},
                {"ZA", "south africa"}, {"PK", "pakistan"}, {"LK", "sri lanka"}, {"BD", "bangladesh"},
                {"AF", "afghanistan"}, {"IE", "ireland"}, {"ZW", "zimbabwe"}, {"NL", "netherlands"}, {"NP", "nepal"},
                {"OM", "oman"}, {"AE", "united arab emirates"}, {"US", "united states of america"}, {"CA", "canada"},
                {"NA", "namibia"}, {"HK", "hong kong"}, {"KE", "kenya"}, {"UG", "uganda"}};
        for (String[] h : home) HOME_TEAM.put(h[0], h[1]);
        // West Indies plays for the Caribbean.
        for (String cc : new String[]{"JM", "TT", "BB", "GY", "AG", "LC", "VC", "GD", "KN", "DM"}) {
            HOME_TEAM.put(cc, "west indies");
            DOMESTIC.put(cc, new String[]{"caribbean premier league", "cpl", "super50"});
        }
    }
    /** India's cricket streamers: their TV channels may carry a live match card, and one has the rights. */
    static final String HOTSTAR = "in.startv.hotstar", FANCODE = "com.fancode.tv", SONYLIV = "com.sonyliv",
            PRIME = "com.amazon.amazonvideo.livingroom", XSTREAM = "tv.airtel.xstream.tvapp";
    private static final String[] STREAMERS = {HOTSTAR, FANCODE, SONYLIV, PRIME, XSTREAM};

    static final class Match {
        String id, name, status, start;          // start: "h:mm am" local, for upcoming
        boolean live, ended, home, domestic, intl, test;
        boolean paused;                           // on, but not playing now: stumps, lunch, tea, rain… (not "live")
        String kind;                              // "Test", "ODI", "T20I"; domestic "First-class", "One-day", "T20"
        String t1, t2, s1 = "", s2 = "";         // short names and score text ("245/4 (42.3)")
        String full1, full2;                      // full team names ("India", "New Zealand")
        String img1, img2;
        String watchPkg, watchUri;
        String search;                            // no match card found: typed into the app's search ("India vs New Zealand")
        int rank;                                 // lower shows first
        boolean football;                         // a Football match (same card): kind is the league, status the clock
        String short1, short2;                    // football: three-letter names for the score line ("ARS")
        String league;                            // football: the league's short name for the score line ("EPL")
        String vs = "v";                          // between the teams: "at" for the NBA and NFL, away team first
    }

    interface Listener { void onMatches(List<Match> matches); }

    private final Context ctx;
    private final File dir;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean running;

    Cricket(Context c) {
        ctx = c.getApplicationContext();
        dir = new File(c.getExternalFilesDir(null), "cricket");
        prefs = c.getSharedPreferences("cricket", Context.MODE_PRIVATE);
        // Up from CricketData (1.2.5 and before): its saved matches and timings mean nothing here; fetch afresh.
        if (!"espn".equals(prefs.getString("source", ""))) {
            prefs.edit().clear().putString("source", "espn").apply();
            new File(dir, "matches.json").delete();
            new File(dir, "innings.json").delete();
        }
    }

    /** The last matches fetched (still live, or today's), with watch links looked up again. Blocking (reads channels). */
    List<Match> cached() {
        List<Match> out = new ArrayList<>();
        try (InputStream in = new FileInputStream(new File(dir, "matches.json"))) {
            out = parse(new JSONArray(new String(readAll(in), StandardCharsets.UTF_8)), Region.country(ctx));
        } catch (Exception e) {
            return out;
        }
        for (Match m : out) findStream(m);
        return out;
    }

    /** The network is back: a fetch that failed while it was down may run again now, without waiting out the retry. */
    void networkBack() {
        if (prefs.getLong("try_at", 0) > prefs.getLong("ok_at", 0)) prefs.edit().remove("try_at").apply();
    }

    void refreshIfStale(Listener l) {
        if (running) return;
        long now = System.currentTimeMillis();
        long every = prefs.getBoolean("any_live", false) ? LIVE_MS : IDLE_MS;
        // A new day: today's fixtures, now.
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (!today.equals(prefs.getString("day", ""))) prefs.edit().putString("day", today).remove("ok_at").apply();
        if (now - prefs.getLong("ok_at", 0) < every || now - prefs.getLong("try_at", 0) < Math.min(every, RETRY_MS)) return;
        prefs.edit().putLong("try_at", now).apply();
        running = true;
        worker.execute(() -> {
            try {
                JSONArray events = fetch();
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
                File tmp = new File(dir, "matches.json.tmp");
                try (OutputStream out = new FileOutputStream(tmp)) { out.write(events.toString().getBytes(StandardCharsets.UTF_8)); }
                if (!tmp.renameTo(new File(dir, "matches.json"))) throw new IOException("Cannot save");
                List<Match> list = parse(events, Region.country(ctx));
                boolean anyLive = false;
                for (Match m : list) anyLive |= m.live || m.paused;  // a paused match is checked as often: play resumes
                prefs.edit().putLong("ok_at", System.currentTimeMillis()).putBoolean("any_live", anyLive).apply();
                for (Match m : list) {
                    findStream(m);
                    Log.i(TAG, m.t1 + " v " + m.t2 + (m.live ? " (live)" : "") + " → " + m.watchPkg + (m.watchUri != null ? " (match link)" : " (app)"));
                }
                Log.i(TAG, list.size() + " matches (" + (anyLive ? "live" : "none live") + ") of " + events.length());
                main.post(() -> l.onMatches(list));
            } catch (Exception e) {
                Log.w(TAG, "Refresh failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            } finally {
                main.post(() -> running = false);
            }
        });
    }

    /**
     * Every match ESPN lists for the days (in UTC, ESPN's day) that today's local hours fall on, as small records.
     * A match that's still on (a Test on day 3) is listed on each of its days.
     */
    private static JSONArray fetch() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        SimpleDateFormat utc = new SimpleDateFormat("yyyyMMdd", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        Set<String> days = new LinkedHashSet<>();
        days.add(utc.format(c.getTime()));
        days.add(utc.format(new Date(c.getTimeInMillis() + 24 * 3600_000L - 60_000L)));
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (String day : days) {
            JSONArray scores = get(API + day).optJSONArray("scores");
            for (int i = 0; scores != null && i < scores.length(); i++) {
                JSONObject s = scores.getJSONObject(i);
                JSONArray leagues = s.optJSONArray("leagues"), events = s.optJSONArray("events");
                String series = leagues != null && leagues.length() > 0 ? leagues.getJSONObject(0).optString("name") : "";
                for (int j = 0; events != null && j < events.length(); j++) {
                    JSONObject o = record(events.getJSONObject(j), series);
                    // The later day's copy is the newer one.
                    if (o == null) continue;
                    if (!seen.add(o.getString("id"))) {
                        for (int k = 0; k < out.length(); k++) if (out.getJSONObject(k).getString("id").equals(o.getString("id"))) out.put(k, o);
                    } else out.put(o);
                }
            }
        }
        return out;
    }

    /** One ESPN event as the small record saved, or null if it's unusable. */
    private static JSONObject record(JSONObject e, String series) throws Exception {
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        JSONArray teams = c.getJSONArray("competitors");
        if (teams.length() != 2) return null;
        JSONObject a = teams.getJSONObject(0), b = teams.getJSONObject(1);
        JSONObject st = e.getJSONObject("status"), type = st.getJSONObject("type"), cls = c.optJSONObject("class");
        JSONObject o = new JSONObject();
        o.put("id", e.getString("id"));
        o.put("series", series);
        o.put("date", e.optString("date"));
        o.put("state", type.optString("state"));          // pre, in, post
        o.put("what", type.optString("description"));     // "Live", "Stumps", "Result", "Abandoned"
        o.put("summary", st.optString("summary").replace("&amp;", "&"));   // "Afghanistan require 16 runs", "won by an inns & 81 runs"
        o.put("session", st.optString("session"));        // "Day 2"
        o.put("cls", cls == null ? 0 : cls.optInt("internationalClassId"));
        o.put("card", cls == null ? "" : cls.optString("generalClassCard"));
        for (int i = 0; i < 2; i++) {
            JSONObject t = teams.getJSONObject(i), team = t.getJSONObject("team");
            String n = String.valueOf(i + 1);
            o.put("full" + n, team.optString("displayName").replaceAll("\\s*\\(.*\\)$", "").trim());
            o.put("t" + n, team.optString("abbreviation"));
            o.put("img" + n, team.optString("logo"));
            o.put("s" + n, OVERS.matcher(t.optString("score")).replaceAll("($1)").trim());
        }
        return o;
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
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- which matches

    private static List<Match> parse(JSONArray data, String cc) throws Exception {
        String[] domesticNames = DOMESTIC.getOrDefault(cc, new String[0]);
        String homeTeam = HOME_TEAM.get(cc);
        List<Match> out = new ArrayList<>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.getJSONObject(i);
            String full1 = o.optString("full1"), full2 = o.optString("full2");
            if (full1.isEmpty() || full2.isEmpty()) continue;
            String series = o.optString("series");
            // ESPN's international classes: Test, ODI, T20I (1–3) and the women's (8–10); not A sides or under-19s.
            int cls = o.optInt("cls");
            boolean intl = (cls >= 1 && cls <= 3) || (cls >= 8 && cls <= 10);
            String lower = series.toLowerCase(Locale.ROOT);
            boolean domestic = false;
            for (String d : domesticNames) if (lower.contains(d)) { domestic = true; break; }
            if (!intl && !domestic) continue;
            String state = o.optString("state"), what = o.optString("what"), summary = o.optString("summary");
            if (summary.startsWith("Starts at")) summary = "";   // the venue's local time: the card says ours
            Match m = new Match();
            m.id = o.optString("id");
            m.full1 = full1;
            m.full2 = full2;
            m.name = full1 + " vs " + full2 + ", " + series;
            m.live = "in".equals(state);
            // A Test is "in" for all five days; at stumps, a meal break or a rain delay nobody is playing, so it isn't
            // live (a live football match wins the score line), only paused: "Day 2: Stumps - Queensland trail by 105".
            if (m.live && PAUSED.matcher(what + " " + summary).find()) {
                m.live = false;
                m.paused = true;
            }
            m.ended = "post".equals(state);
            m.status = m.paused ? (o.optString("session").isEmpty() ? "" : o.optString("session") + ": ") + what
                    + (summary.isEmpty() ? "" : " - " + summary)
                    : summary.isEmpty() && !"pre".equals(state) ? what : summary;
            m.domestic = domestic;
            m.home = homeTeam != null && (full1.toLowerCase(Locale.ROOT).startsWith(homeTeam)
                    || full2.toLowerCase(Locale.ROOT).startsWith(homeTeam));
            Date at = parseUtc(o.optString("date"));
            // Live, or starting or finished today (a Test that began days ago is still on).
            if (!m.live && !m.paused && (at == null || !today.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(at)))) continue;
            m.start = m.live || m.paused || m.ended || at == null ? null
                    : new SimpleDateFormat("h:mm a", Locale.US).format(at).toLowerCase(Locale.ROOT);
            m.t1 = o.optString("t1");
            m.t2 = o.optString("t2");
            m.s1 = o.optString("s1");
            m.s2 = o.optString("s2");
            m.img1 = o.optString("img1", null);
            m.img2 = o.optString("img2", null);
            m.intl = intl;
            String card = o.optString("card").toLowerCase(Locale.ROOT);
            m.test = cls == 1 || cls == 8 || card.equals("first-class");
            m.kind = intl ? (m.test ? "Test" : cls == 2 || cls == 9 ? "ODI" : "T20I")
                    : m.test ? "First-class" : card.contains("t20") || card.contains("twenty20") ? "T20"
                    : card.contains("list a") || card.contains("od") ? "One-day" : "";
            m.rank = (m.live ? 0 : m.paused ? 5 : m.ended ? 20 : 10) + (m.home ? 0 : 4) + (intl ? 0 : 2);
            out.add(m);
        }
        out.sort((x, y) -> Integer.compare(x.rank, y.rank));
        return out;
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

    // ---------------------------------------------------------------- where to watch

    /**
     * Looks through the streaming apps' TV channels for a card that names both teams (e.g. JioHotstar's
     * "IND vs AUS, 2nd ODI – LIVE"): its link opens the stream itself. Otherwise picks the app with the rights in India,
     * and for JioHotstar its search, with both teams typed in (see SleepService.searchHotstar).
     */
    private void findStream(Match m) {
        boolean india = "IN".equals(Region.country(ctx));
        String[] names1 = {m.t1.toLowerCase(Locale.ROOT).replace("-w", ""), m.full1.toLowerCase(Locale.ROOT)},
                names2 = {m.t2.toLowerCase(Locale.ROOT).replace("-w", ""), m.full2.toLowerCase(Locale.ROOT)};
        try (Cursor ch = ctx.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_TYPE},
                null, null, null)) {
            while (ch != null && ch.moveToNext()) {
                String pkg = ch.getString(1);
                // In India, only the cricket streamers' rows (news apps name teams too); elsewhere any app's.
                if (india && !Arrays.asList(STREAMERS).contains(pkg)) continue;
                if (!TvContract.Channels.TYPE_PREVIEW.equals(ch.getString(2))) continue;
                try (Cursor p = ctx.getContentResolver().query(TvContract.buildPreviewProgramsUriForChannel(ch.getLong(0)),
                        new String[]{TvContract.PreviewPrograms.COLUMN_TITLE, TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                                TvContract.PreviewPrograms.COLUMN_INTENT_URI}, null, null, null)) {
                    while (p != null && p.moveToNext()) {
                        String text = (p.getString(0) + " " + p.getString(1)).toLowerCase(Locale.ROOT);
                        if (mentions(text, names1) && mentions(text, names2) && p.getString(2) != null) {
                            m.watchPkg = pkg;
                            m.watchUri = p.getString(2);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Couldn't search channels: " + e);
        }
        // No card found: in India the rights holder opens (JioHotstar with its search typed in); elsewhere nothing.
        m.watchPkg = india ? rightsHolder(m) : null;
        m.search = india ? m.full1 + " vs " + m.full2 : null;
    }

    private static boolean mentions(String text, String[] names) {
        for (String n : names) {
            if (n.isEmpty()) continue;
            if (n.length() <= 4 ? text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(n) + "\\b.*") : text.contains(n)) return true;
        }
        return false;
    }

    /** Who streams it in India (2026): JioHotstar has BCCI (home internationals, domestic, IPL) and ICC events. */
    private static String rightsHolder(Match m) {
        String n = m.name.toLowerCase(Locale.ROOT);
        if (n.contains("lanka premier league")) return FANCODE;
        if (n.contains("asia cup")) return SONYLIV;
        return HOTSTAR;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
        return out.toByteArray();
    }
}
