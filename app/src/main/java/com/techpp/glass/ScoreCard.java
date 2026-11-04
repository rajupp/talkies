package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A live match pinned over whatever is playing: hold OK on a match → Pin over TV. A small card in a corner, drawn by the
 * Accessibility service (SleepService), so it needs no other permission. It can't be focused or touched, so remote
 * buttons always reach the app underneath, and it stays out of the way on Talkies' own Home. An idea from Gulam.
 */
final class ScoreCard {
    private static final String TAG = "GlassScoreCard";
    private static final long TICK_MS = 30_000, ENDED_MS = 5 * 60_000L;
    /** Ticks with the match missing from the saved list before giving up on it: ten minutes. */
    private static final int MAX_MISSES = 20;
    private static final String[] CORNERS = {"Top right", "Top left", "Bottom right", "Bottom left"};
    private static final int[] GRAVITY = {Gravity.TOP | Gravity.END, Gravity.TOP | Gravity.START,
            Gravity.BOTTOM | Gravity.END, Gravity.BOTTOM | Gravity.START};

    /** Talkies' Home is on screen: it has the scores already, so the card waits until you leave for an app. */
    private static volatile boolean homeShowing;

    private final SleepService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable tick = this::tick;
    private Cricket cricket;
    private Football football;
    private LinearLayout card;
    private TextView team1, team2, status;
    private int misses;
    private long endedAt;

    ScoreCard(SleepService s) { service = s; }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("score_pin", Context.MODE_PRIVATE); }

    static void homeShowing(boolean showing) {
        homeShowing = showing;
        SleepService.scoreVisibility();
    }

    /** Over an app, not over Home. */
    void applyVisibility() {
        if (card != null) card.setVisibility(homeShowing ? View.GONE : View.VISIBLE);
    }

    static String pinnedId(Context c) { return prefs(c).getString("id", null); }

    /** "IND v NZ", for Control Center. */
    static String pinnedName(Context c) { return prefs(c).getString("name", ""); }

    static String cornerName(Context c) { return CORNERS[corner(c)]; }

    private static int corner(Context c) { return Math.floorMod(prefs(c).getInt("corner", 0), CORNERS.length); }

    /** False when the Accessibility service is off: it's what draws the card. */
    static boolean pin(Context c, Cricket.Match m) {
        prefs(c).edit().putString("id", m.id).putBoolean("football", m.football)
                .putString("name", name(m.short1, m.t1) + " " + m.vs + " " + name(m.short2, m.t2)).apply();
        if (SleepService.scoreChanged(m)) return true;
        unpin(c);
        return false;
    }

    static void unpin(Context c) {
        prefs(c).edit().remove("id").remove("football").remove("name").apply();
        SleepService.scoreChanged(null);
    }

    static void nextCorner(Context c) {
        prefs(c).edit().putInt("corner", (corner(c) + 1) % CORNERS.length).apply();
        SleepService.scoreChanged(null);
    }

    private static String name(String shortName, String full) { return shortName != null && !shortName.isEmpty() ? shortName : full; }

    /** Shows the pinned match, or takes the card away when nothing is pinned. {@code m}: what to show at once, if known. */
    void sync(Cricket.Match m) {
        if (pinnedId(service) == null) {
            hide();
            return;
        }
        if (card == null && !add()) return;
        place();
        applyVisibility();
        misses = 0;
        endedAt = 0;
        if (m != null) show(m);
        main.removeCallbacks(tick);
        tick();
    }

    void hide() {
        main.removeCallbacks(tick);
        if (card == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(card);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
        card = null;
    }

    private boolean add() {
        float dp = service.getResources().getDisplayMetrics().density;
        Typeface font = service.getResources().getFont(R.font.outfit);
        card = new LinearLayout(service);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Math.round(16 * dp), Math.round(10 * dp), Math.round(16 * dp), Math.round(10 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE61C1C1E);
        bg.setCornerRadius(14 * dp);
        bg.setStroke(Math.round(dp), 0x33FFFFFF);
        card.setBackground(bg);
        team1 = line(font, 17, 600, 0xFFF5F5F7);
        team2 = line(font, 17, 600, 0xFFF5F5F7);
        status = line(font, 12, 400, 0xFFA1A1A6);
        status.setMaxWidth(Math.round(300 * dp));
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(team1);
        card.addView(team2);
        card.addView(status);
        team1.setText("…");
        status.setVisibility(View.GONE);
        team2.setVisibility(View.GONE);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        try {
            service.getSystemService(WindowManager.class).addView(card, lp);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't show the score card: " + e);
            card = null;
            return false;
        }
    }

    private TextView line(Typeface font, int sp, int weight, int color) {
        TextView t = new TextView(service);
        t.setTypeface(font);
        t.setFontVariationSettings("'wght' " + weight);
        t.setFontFeatureSettings("tnum");
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }

    private void place() {
        float dp = service.getResources().getDisplayMetrics().density;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) card.getLayoutParams();
        lp.gravity = GRAVITY[corner(service)];
        lp.x = Math.round(32 * dp);
        lp.y = Math.round(28 * dp);
        try {
            service.getSystemService(WindowManager.class).updateViewLayout(card, lp);
        } catch (RuntimeException e) {
            Log.w(TAG, "Couldn't move the score card: " + e);
        }
    }

    /** Every 30 seconds, only with the screen on: fetches when the scores are due, and shows what's saved. */
    private void tick() {
        main.postDelayed(tick, TICK_MS);
        if (!service.getSystemService(PowerManager.class).isInteractive()) return;
        boolean isFootball = prefs(service).getBoolean("football", false);
        if (isFootball) {
            if (football == null) football = new Football(service);
            java.util.Set<String> on = Football.sportsOn(service);
            football.setSports(on.contains("football"), on.contains("nfl"), on.contains("nba"));
            football.refreshIfStale(list -> load());
        } else {
            if (cricket == null) cricket = new Cricket(service);
            cricket.refreshIfStale(list -> load());
        }
        load();
    }

    private void load() {
        boolean isFootball = prefs(service).getBoolean("football", false);
        String id = pinnedId(service);
        Cricket cr = cricket;
        Football fo = football;
        worker.execute(() -> {
            List<Cricket.Match> list = isFootball ? (fo == null ? null : fo.cached()) : (cr == null ? null : cr.cached());
            Cricket.Match found = null;
            if (list != null) for (Cricket.Match m : list) if (m.id.equals(id)) found = m;
            Cricket.Match m = found;
            main.post(() -> apply(id, m));
        });
    }

    private void apply(String id, Cricket.Match m) {
        if (card == null || id == null || !id.equals(pinnedId(service))) return;
        if (m == null) {
            if (++misses >= MAX_MISSES) ScoreCard.unpin(service);
            return;
        }
        misses = 0;
        show(m);
        if (!m.ended) {
            endedAt = 0;
        } else if (endedAt == 0) {
            endedAt = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - endedAt >= ENDED_MS) {
            ScoreCard.unpin(service);
        }
    }

    private void show(Cricket.Match m) {
        if (card == null) return;
        team1.setText(line(name(m.short1, m.t1), m.s1));
        team2.setText(line(name(m.short2, m.t2), m.s2));
        team2.setVisibility(View.VISIBLE);
        String text = m.live ? "LIVE  ·  " + m.status : !m.ended && m.start != null ? "Starts " + m.start : m.status;
        SpannableString s = new SpannableString(text == null ? "" : text);
        if (m.live) s.setSpan(new ForegroundColorSpan(0xFFFF453A), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        status.setText(s);
        status.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private static String line(String team, String score) {
        return score == null || score.isEmpty() ? team : team + "   " + score;
    }
}
