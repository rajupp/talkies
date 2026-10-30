package com.techpp.glass;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talkies. One screen: "For You" with tabs (Continue, Trending, New in India) and a row of picks, round icon
 * buttons and a small time · date · weather line at the top right, a dock of seven apps in one band, and every other
 * app below the fold. Light by day, dark at night. The remote is handled entirely here, so navigation is exact.
 */
public class HomeActivity extends Activity {
    private static final String TAG = "GlassHome";

    // Where focus is.
    private static final int Z_TOP = 0, Z_TABS = 1, Z_PICKS = 2, Z_DOCK = 3, Z_GRID = 4, Z_MENU = 5, Z_CHAN = 6, Z_ALL = 7;
    private static final int P_CONTROL = 0, P_INPUTS = 1, P_WIFI = 2, P_SETTINGS = 3, P_BELL = 4;
    private static final String[] TAB_IDS = {RecentStore.TAB, Trending.TRENDING, Trending.INDIA};
    private static final int TAB_TRENDING = 1, MIN_CONTINUE = 10, MAX_CONTINUE = 16, MAX_YOUTUBE = 2;
    private static final int GRID_COLS = 6;
    /** How many apps the dock holds (Talkies settings → Dock size); the tiles shrink or grow to fit. */
    private static final int[] DOCK_SIZES = {5, 6, 7, 8, 9, 10};
    // Geometry (dp, on a 960 × 540 dp screen).
    private static final int DOCK_MAX_W = 146, DOCK_GAP = 12, ALL_BUTTON = 44, GRID_TILE_W = 133, GRID_TILE_H = 75, GRID_GAP_X = 16,
            GRID_GAP_Y = 18;
    // Channel rows, below the dock: header, a row of cards, the focused card's title and description.
    private static final int CHAN_TOP = 548, CHAN_ROW_H = 212, CH_W = 200, CH_H = 112, CH_GAP = 18, CRICKET_W = 280;
    private static final int MENU_PLAIN = 0, MENU_CHANNELS = 1;

    private float dp;
    private SharedPreferences prefs;
    private PackageManager pm;

    private View root;
    private Backdrop backdrop;
    private FrameLayout page, dockBand;
    private LinearLayout dock, grid, menu, channelsBox;
    private android.widget.ScrollView menuScroll;
    private TextView title, info, credit, allAppsLabel, moveHint;
    private TextView[] tabs;
    private ImageView[] icons;
    private PicksRow picksRow;
    private HighlightView ring;

    // Apps: the dock's seven first, then the rest in your order.
    private final List<AppEntry> apps = new ArrayList<>();
    private int dockCount;
    /** The tile after the dock: OK goes to All apps, rather than down past every channel row. */
    private View allTile;
    private final List<View> dockTiles = new ArrayList<>(), gridTiles = new ArrayList<>();

    // Channel rows.
    private List<Channels.Channel> chanRows = new ArrayList<>();
    private final List<android.widget.HorizontalScrollView> chanScrolls = new ArrayList<>();
    private final List<List<View>> chanCards = new ArrayList<>();
    private int[] chanCol = new int[0];
    private int chanIdx;
    private String chanSignature = "";
    private final List<Spring> chanSprings = new ArrayList<>();
    private boolean refocusChannels;
    /** When a remote key last moved focus, and whether a channel update is waiting for the remote to go still. */
    private long lastKeyAt;
    private boolean chanPending;
    /** The icon Talkies settings drops down from (Control Center, or the gear when you held OK on it). */
    private int settingsAt = P_CONTROL;
    private static final long CHAN_IDLE_MS = 4000;   // each channel row's scroll, in px
    // The Channels editor (Control Center → Channels).
    private int menuMode = MENU_PLAIN;
    private List<Channels.Channel> editList, beforeEdit;
    private boolean menuMoving, channelsEdited;
    /** The arrow being handled is a repeat from holding it down: menus then stop at their ends instead of wrapping. */
    private boolean heldKey;

    // Picks for the three tabs, and what the row shows now.
    private Trending trending;
    private Weather weather;
    private Cricket cricket;
    private Football football;
    private List<Cricket.Match> matches = new ArrayList<>(), footballMatches = new ArrayList<>();
    private List<Channels.Channel> appChannels = new ArrayList<>();  // the shown app channels, without Cricket
    private TextView score;
    private String weatherLine;
    private List<Trending.Pick> watchNextPicks = new ArrayList<>(), recentPicks = new ArrayList<>(),
            trendPicks = new ArrayList<>(), indiaPicks = new ArrayList<>(), rowPicks = new ArrayList<>();
    private int tabIdx = TAB_TRENDING;
    private long pickHour = -1;
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    // Focus.
    private int zone = Z_DOCK, iconIdx = P_CONTROL, dockIdx = 0, gridIdx = 0;  // Up starts on Control Center, then the icon used last
    private int menuIdx, menuAnchor = P_INPUTS;
    private View menuTile;          // a menu opened on an app or card sits by it (else under its top-bar icon)
    private Runnable menuClosed;    // and closing it puts focus back there
    // Back in a menu opened from another menu's row (Control Center → Talkies settings → Dock apps) goes up one
    // level: what reopens each menu above, with the row you came from.
    private Runnable menuOpener;
    private final java.util.ArrayDeque<Object[]> menuParents = new java.util.ArrayDeque<>();
    private boolean fromMenuRow;
    private int menuGen;
    private final List<View> menuItems = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();
    private final List<Boolean> menuKeepsOpen = new ArrayList<>();
    private View focusedView;

    // The ring and the page scroll.
    // Motion, all springs stepped on one frame callback (see doMotion): the page, the channel rows and each card's
    // scale. The ring is fixed to the focused card (it grows with it); a ring gliding on its own spring lagged behind
    // the cards when a row or the page moved. Springs keep their speed when a press changes the target.
    private final RectF ringNow = new RectF(), tmp = new RectF();
    private final Spring pageSpring = new Spring(0.34f, 1f, 0, 0.25f);
    private float ringRadius;
    private final Map<View, Spring> scaleSprings = new WeakHashMap<>();
    private boolean motionPosted;
    private long motionNanos, followUntil;

    // Theme.
    private boolean day;
    private Theme theme;
    private long wallpaperStamp = -1;
    private boolean hasWallpaper;
    private Bing bing;
    private Legibility wallFit;  // how much veil the wallpaper needs behind text (null: no wallpaper)
    // The accessibility service should be running and isn't (ServiceRepair.stopped): a note at the top says so.
    private TextView serviceNote;
    private boolean serviceStopped, rebindTried;
    // For You (title, tabs, picks) can be hidden in Control Center; a large clock, date and weather take its place.
    private boolean forYou;
    private Meters meters;
    /** Online at the last network change; null before the first. */
    private Boolean wasOnline;
    private View forYouHead, bigClock;
    private TextView picksHint;
    private TextView bigTime, bigAmPm, bigDate, bigWeather;
    private View bigRule;
    private boolean inFront;

    // Rearranging: hold OK on an app, arrows move it, OK places it, Back cancels.
    private static final long HOLD_MS = 550;
    private boolean moving, holdFired;
    private String movingPkg;
    private List<AppEntry> beforeMove;
    private final Runnable holdTimer = this::onHold;

    private ConnectivityManager.NetworkCallback netCallback;
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { onMinute(); }
    };
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reloadApps(); }
    };

    // ---------------------------------------------------------------- setup

    /**
     * The layout is drawn for a screen 960 × 540 dp, which is what 1080p and 4K TVs report at their usual density.
     * Some TVs and boxes report another density (1080p at 240 dpi is 1280 dp wide), which would leave the layout
     * small in a corner or too big. So the density is set from the screen's width: it's always 960 dp across.
     */
    @Override
    protected void attachBaseContext(Context base) {
        android.util.DisplayMetrics dm = base.getResources().getDisplayMetrics();
        android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
        int wide = Math.max(dm.widthPixels, dm.heightPixels), narrow = Math.min(dm.widthPixels, dm.heightPixels);
        int dpi = Math.round(wide * 160f / 960f);
        if (wide > 0 && Math.abs(dpi - c.densityDpi) > 2) {
            c.densityDpi = dpi;
            c.screenWidthDp = 960;
            c.screenHeightDp = Math.round(narrow * 160f / dpi);
            base = base.createConfigurationContext(c);
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // restore-settings.sh pushed a backup: apply it before anything reads the settings.
        if (Backup.restoreIfPresent(this)) Toast.makeText(this, "Settings restored from the backup", Toast.LENGTH_LONG).show();
        else if (getIntent().getBooleanExtra("talkies_backup", false)) Backup.save(this);  // asked before Talkies was running
        setContentView(R.layout.activity_home);
        weigh(this::findViewById, R.id.title, 600, R.id.info, 400, R.id.score, 500, R.id.big_weather, 500, R.id.big_time, 400,
                R.id.big_ampm, 500, R.id.big_date, 500, R.id.all_apps_label, 600, R.id.move_hint, 450);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        pm = getPackageManager();

        root = findViewById(R.id.root);
        backdrop = findViewById(R.id.backdrop);
        forYouHead = findViewById(R.id.for_you_head);
        bigClock = findViewById(R.id.big_clock);
        bigTime = findViewById(R.id.big_time);
        bigDate = findViewById(R.id.big_date);
        bigAmPm = findViewById(R.id.big_ampm);
        bigRule = findViewById(R.id.big_rule);
        bigWeather = findViewById(R.id.big_weather);
        forYou = prefs.getBoolean("for_you", true);
        meters = findViewById(R.id.meters);
        meters.setVisibility(prefs.getBoolean("meters", true) ? View.VISIBLE : View.GONE);
        getWindow().setBackgroundDrawable(null);   // the backdrop covers the screen: don't paint it black first
        page = findViewById(R.id.page);
        dockBand = findViewById(R.id.dock_band);
        dock = findViewById(R.id.dock);
        grid = findViewById(R.id.grid);
        menu = findViewById(R.id.menu);
        menuScroll = findViewById(R.id.menu_scroll);
        channelsBox = findViewById(R.id.channels);
        title = findViewById(R.id.title);
        info = findViewById(R.id.info);
        credit = findViewById(R.id.credit);
        allAppsLabel = findViewById(R.id.all_apps_label);
        moveHint = findViewById(R.id.move_hint);
        picksRow = findViewById(R.id.picks);
        picksHint = findViewById(R.id.picks_hint);
        ring = findViewById(R.id.ring);
        tabs = new TextView[]{findViewById(R.id.tab_recent), findViewById(R.id.tab_trending), findViewById(R.id.tab_india)};
        icons = new ImageView[]{findViewById(R.id.icon_cc), findViewById(R.id.icon_inputs), findViewById(R.id.icon_wifi),
                findViewById(R.id.icon_settings), findViewById(R.id.icon_bell)};
        Updates.setListener(this::onUpdates);
        onUpdates();
        picksRow.setOnFrame(this::snapRing);

        // Banners are prepared at the size of the largest focused tile: an All apps tile at 110%, or the dock's
        // largest (five apps) at 114%.
        AppEntry.setBannerSize(px(Math.max(GRID_TILE_W * 1.10f, DOCK_MAX_W * 1.14f)), px(Math.max(GRID_TILE_H * 1.10f, DOCK_MAX_W * 9 / 16f * 1.14f)));
        AppEntry.customDir = new File(getExternalFilesDir(null), "tiles");
        trending = new Trending(this);
        weather = new Weather(this);
        bing = new Bing(this, background);
        cricket = new Cricket(this);
        football = new Football(this);
        score = findViewById(R.id.score);
        weatherLine = weather.cached();
        day = isDayNow();
        theme = Theme.of(day);

        loadApps();
        loadPicks();
        buildAppViews();
        buildPicksRow(0);
        applyTheme();
        applyForYou();
        dockIdx = Math.min(prefs.getInt("last_dock_col", 0), Math.max(0, dockCount - 1));
        root.post(() -> {
            focusDock(dockIdx);
            String open = getIntent().getStringExtra(RemoteKeys.EXTRA_OPEN);
            if (RemoteKeys.INPUTS.equals(open)) openInputs();
            else if (RemoteKeys.RECENTS.equals(open)) openRecents();
        });
    }

    private int px(float v) { return Math.round(v * dp); }

    // ---------------------------------------------------------------- theme

    /** Colours for the light (day) and dark (night) looks. */
    private static final class Theme {
        int bgTop, bgBottom, text, text2, iconBg, iconStroke, iconFocusBg, iconFocusFg, band, bandStroke,
                scrimTop, scrimMid, scrimBottom, menuBg, menuStroke, menuFocusBg, menuFocusText, tileBg;
        float ambientAlpha;

        static Theme of(boolean day) {
            Theme t = new Theme();
            if (day) {
                t.bgTop = 0xFFF5F6F8; t.bgBottom = 0xFFE2E5EA; t.text = 0xFF1D1D1F; t.text2 = 0xFF55555A;
                t.iconBg = 0xC0FFFFFF; t.iconStroke = 0x10000000; t.iconFocusBg = 0xFF1D1D1F; t.iconFocusFg = 0xFFFFFFFF;
                t.band = 0x94FFFFFF; t.bandStroke = 0xF2FFFFFF;
                t.scrimTop = 0x8CF5F6F8; t.scrimMid = 0x33EBEEF2; t.scrimBottom = 0xA6E2E5EA;
                t.menuBg = 0xFFFFFFFF; t.menuStroke = 0x14000000; t.menuFocusBg = 0xFF1D1D1F; t.menuFocusText = 0xFFFFFFFF;
                t.tileBg = 0xFFD9DCE1; t.ambientAlpha = 0.22f;
            } else {
                t.bgTop = 0xFF121419; t.bgBottom = 0xFF08090C; t.text = 0xFFF5F5F7; t.text2 = 0xFFA1A1A6;
                t.iconBg = 0x1AFFFFFF; t.iconStroke = 0x1FFFFFFF; t.iconFocusBg = 0xFFF5F5F7; t.iconFocusFg = 0xFF111113;
                t.band = 0x12FFFFFF; t.bandStroke = 0x1FFFFFFF;
                t.scrimTop = 0x5908090C; t.scrimMid = 0x2608090C; t.scrimBottom = 0xB308090C;
                t.menuBg = 0xFF1C1C1F; t.menuStroke = 0x2EFFFFFF; t.menuFocusBg = 0xFFF5F5F7; t.menuFocusText = 0xFF111113;
                t.tileBg = 0xFF232326; t.ambientAlpha = 0.30f;
            }
            return t;
        }
    }

    private void applyTheme() {
        theme = Theme.of(day);
        backdrop.setTheme(day, theme.bgTop, theme.bgBottom, theme.scrimTop, theme.scrimMid, theme.scrimBottom, theme.ambientAlpha);
        backdrop.setScrimStops(wallpaperScrim());
        title.setTextColor(theme.text);
        bigTime.setTextColor(theme.text);
        bigAmPm.setTextColor(theme.text);
        bigDate.setTextColor(theme.text);
        bigWeather.setTextColor(theme.text2);
        meters.setColor(theme.text);
        bigRule.setBackgroundColor((theme.text2 & 0x00FFFFFF) | 0x66000000);
        allAppsLabel.setTextColor(theme.text);
        picksHint.setTextColor(theme.text2);
        credit.setTextColor((theme.text2 & 0x00FFFFFF) | 0x99000000);
        GradientDrawable band = new GradientDrawable();
        band.setCornerRadius(px(30));
        band.setColor(theme.band);
        band.setStroke(px(1), theme.bandStroke);
        dockBand.setBackground(band);
        moveHint.setTextColor(theme.text);
        moveHint.setBackground(rounded(theme.menuBg, theme.menuStroke, 14));
        ring.setDay(day);
        for (int i = 0; i < picksRow.getChildCount(); i++) stylePickText(picksRow.getChildAt(i));
        styleChannels();
        // Card backgrounds and match cards are drawn in the theme's colours: redraw the rows.
        if (cricket != null && zone != Z_CHAN && !chanRows.isEmpty()) {
            chanSignature = "";
            showChannels(appChannels);
        }
        for (View t : dockTiles) t.setBackground(rounded(theme.tileBg, 0, 11));
        for (View t : gridTiles) t.setBackground(rounded(theme.tileBg, 0, 11));
        styleTop();
        updateInfo();
    }

    /**
     * The veil over a wallpaper: at each stop the theme's usual scrim, or more where the photo needs it for the text
     * to stay readable (Legibility).
     */
    private int[] wallpaperScrim() {
        if (wallFit == null) return null;
        float[] need = day ? wallFit.forLight : wallFit.forDark;
        int n = Legibility.STOPS;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            float y = i / (float) (n - 1);
            // The theme's own three-stop scrim at this height.
            int base = y <= 0.5f ? lerpColor(theme.scrimTop, theme.scrimMid, y * 2) : lerpColor(theme.scrimMid, theme.scrimBottom, (y - 0.5f) * 2);
            int a = Math.max(base >>> 24, Math.round(need[i] * 255));
            out[i] = (a << 24) | (base & 0x00FFFFFF);
        }
        return out;
    }

    private static int lerpColor(int a, int b, float t) {
        int out = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int x = (a >>> sh) & 0xFF, y = (b >>> sh) & 0xFF;
            out |= Math.round(x + (y - x) * t) << sh;
        }
        return out;
    }

    private GradientDrawable rounded(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(radiusDp));
        g.setColor(fill);
        if (stroke != 0) g.setStroke(px(1), stroke);
        return g;
    }

    /** Icon buttons and tabs: their look depends on the theme and on whether they have focus. */
    private void styleTop() {
        for (int i = 0; i < icons.length; i++) {
            boolean f = zone == Z_TOP && iconIdx == i;
            GradientDrawable c = new GradientDrawable();
            c.setShape(GradientDrawable.OVAL);
            c.setColor(f ? theme.iconFocusBg : theme.iconBg);
            if (!f) c.setStroke(px(1), theme.iconStroke);
            icons[i].setBackground(c);
            icons[i].setImageTintList(ColorStateList.valueOf(f ? theme.iconFocusFg : theme.text));
            icons[i].animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(150).start();
        }
        if (allTile != null) {
            boolean f = zone == Z_ALL;
            GradientDrawable c = new GradientDrawable();
            c.setShape(GradientDrawable.OVAL);
            c.setColor(f ? theme.iconFocusBg : theme.iconBg);
            if (!f) c.setStroke(px(1), theme.iconStroke);
            allTile.setBackground(c);
            ((ImageView) allTile).setImageTintList(ColorStateList.valueOf(f ? theme.iconFocusFg : theme.text));
            allTile.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(150).start();
        }
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == tabIdx, f = zone == Z_TABS && selected;
            tabs[i].setVisibility(picksFor(i).isEmpty() ? View.GONE : View.VISIBLE);
            tabs[i].setTextColor(f ? theme.iconFocusFg : selected ? theme.text : theme.text2);
            tabs[i].setFontVariationSettings(selected ? "'wght' 600" : "'wght' 400");
            tabs[i].setBackground(f ? rounded(theme.iconFocusBg, 0, 15) : null);
        }
        credit.setVisibility(!forYou || tabIdx == 0 || rowPicks.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * The weather, time and date always sit top left (the small line under the icons isn't used). For You shown: its
     * tabs and picks follow below them; hidden: just the clock, 1.6 times the size, with the dock below.
     */
    private void applyForYou() {
        forYouHead.setVisibility(forYou ? View.VISIBLE : View.GONE);
        info.setVisibility(View.GONE);
        float k = forYou ? 1f : 1.6f;
        bigWeather.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12 * k);
        bigTime.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 42 * k);
        bigAmPm.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15 * k);
        bigDate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14 * k);
        ViewGroup.MarginLayoutParams am = (ViewGroup.MarginLayoutParams) bigAmPm.getLayoutParams();
        am.bottomMargin = px(5 * k);
        ViewGroup.MarginLayoutParams rule = (ViewGroup.MarginLayoutParams) bigRule.getLayoutParams();
        rule.height = px(28 * k);
        rule.setMarginStart(px(16 * k));
        rule.setMarginEnd(px(16 * k));
        ((ViewGroup.MarginLayoutParams) bigClock.getLayoutParams()).topMargin = px(forYou ? 26 : 34);
        bigClock.requestLayout();
        styleTop();
    }

    /** "1:05 PM · Wed 21 Oct · 27° Showers", the time a little stronger. */
    private void updateInfo() {
        Calendar now = Calendar.getInstance();
        String time = DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a", now).toString();
        SpannableStringBuilder b = new SpannableStringBuilder(time);
        b.setSpan(new ForegroundColorSpan(theme.text), 0, time.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new StyleSpan(Typeface.BOLD), 0, time.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append("  ·  ").append(DateFormat.format("EEE d MMM", now));
        if (weatherLine != null) b.append("  ·  ").append(weatherLine);
        info.setTextColor(theme.text2);
        info.setText(b);
        boolean h24 = DateFormat.is24HourFormat(this);
        bigTime.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", now));
        bigAmPm.setText(h24 ? "" : DateFormat.format("a", now));
        bigAmPm.setVisibility(h24 ? View.GONE : View.VISIBLE);
        bigDate.setText(DateFormat.format("EEEE  d MMMM", now));
        String city = cityName();
        bigWeather.setText(weatherLine == null ? "" : (city == null ? "" : city + "  \u00b7  ") + weatherLine);
        if (meters.getVisibility() == View.VISIBLE) meters.update();
        bigWeather.setVisibility(weatherLine == null ? View.GONE : View.VISIBLE);
        updateScore();
    }

    /** Control Center → Live scores: "both" (the default), "cricket", "football" or "off". */
    private String scores() { return prefs.getString("scores", "both"); }
    private List<Cricket.Match> cricketShown() { return scores().equals("both") || scores().equals("cricket") ? matches : new ArrayList<>(); }
    private List<Cricket.Match> footballShown() { return scores().equals("both") || scores().equals("football") ? footballMatches : new ArrayList<>(); }

    /**
     * "● LIVE  IND 245/4 (42.3) v AUS" or "● LIVE  67'  ·  ARS 2–1 CHE", or today's next match, or today's result;
     * hidden when there's none. A live match wins, cricket before football.
     */
    private void updateScore() {
        List<Cricket.Match> c = cricketShown(), f = footballShown();
        Cricket.Match m = !c.isEmpty() && c.get(0).live ? c.get(0) : !f.isEmpty() && f.get(0).live ? f.get(0)
                : !c.isEmpty() ? c.get(0) : !f.isEmpty() ? f.get(0) : null;
        if (m == null) {
            score.setVisibility(View.GONE);
            credit.setAlpha(1f);
            return;
        }
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (m.football) {
            // League first, like cricket's match type: "● LIVE  Serie A · 52' · SAS 2–4 LAZ".
            String result = m.short1 + " " + m.s1 + "–" + m.s2 + " " + m.short2;
            if (m.live) {
                b.append("● LIVE  ");
                b.setSpan(new ForegroundColorSpan(0xFFFF453A), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                b.append(m.league).append("  ·  ").append(m.status).append("  ·  ").append(result);
            } else if (!m.ended) {
                b.append(m.league).append("  ·  ").append(m.short1).append(" v ").append(m.short2)
                        .append(m.start == null ? "" : "  ·  " + m.start);
            } else {
                b.append(m.league).append("  ·  ").append(result).append("  ·  ").append(m.status);
            }
        } else if (m.paused) {
            // "Test · AUS 329 v RSA · Day 1: Stumps": the scores, and why nobody's playing (before the " - " detail).
            String why = m.status.contains(" - ") ? m.status.substring(0, m.status.indexOf(" - ")) : m.status;
            if (!m.kind.isEmpty()) b.append(m.kind).append("  ·  ");
            b.append(m.t1).append(m.s1.isEmpty() ? "" : " " + m.s1).append("  v  ").append(m.t2).append(m.s2.isEmpty() ? "" : " " + m.s2)
                    .append("  ·  ").append(why);
        } else if (m.live) {
            b.append("● LIVE  ");
            b.setSpan(new ForegroundColorSpan(0xFFFF453A), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!m.kind.isEmpty()) b.append(m.kind).append("  ·  ");
            b.append(m.t1).append(m.s1.isEmpty() ? "" : " " + m.s1).append("  v  ").append(m.t2).append(m.s2.isEmpty() ? "" : " " + m.s2);
        } else if (!m.ended) {
            b.append(m.t1).append(" v ").append(m.t2).append(m.start == null ? "" : "  ·  " + m.start).append(m.kind.isEmpty() ? "" : "  ·  " + m.kind);
        } else {
            b.append(m.t1).append(" v ").append(m.t2).append("  ·  ").append(m.status);
        }
        score.setText(b);
        score.setTextColor(theme.text);
        score.setVisibility(View.VISIBLE);
        credit.setAlpha(0f);  // the score sits where the TMDB credit goes; TMDB is credited in the README too
    }

    private void onMatches(List<Cricket.Match> list) {
        matches = list;
        updateScore();
        showChannels(appChannels);  // while you're moving through the rows it waits for the remote to go still
    }

    private void onFootball(List<Cricket.Match> list) {
        footballMatches = list;
        updateScore();
        showChannels(appChannels);  // while you're moving through the rows it waits for the remote to go still
    }

    private void onMinute() {
        updateInfo();
        refreshOnline();
        boolean nowDay = isDayNow();
        if (nowDay != day) { day = nowDay; applyTheme(); }
        // New picks on the hour, but never while you're looking through them.
        if (Trending.localHour() != pickHour && zone != Z_PICKS && zone != Z_TABS && tabIdx != 0) buildPicksRow(0);
    }

    /** Appearance from Control Center: Auto (by sunrise and sunset), Light or Dark. */
    private static final String[] APPEARANCE = {"Auto", "Light", "Dark"};
    /** How All apps is arranged (Talkies settings → All apps order). The dock always keeps your order. */
    private static final String[] APPS_ORDER = {"Your order", "Recently opened", "A to Z"};
    private static final int ORDER_YOURS = 0, ORDER_RECENT = 1, ORDER_AZ = 2;
    /** RecentApps as of the last loadApps, so coming home re-sorts only when something new was opened. */
    private String recentSig = "";

    private boolean isDayNow() {
        int mode = prefs.getInt("appearance", 0);
        // Auto with a wallpaper: the look the photo suits, so text stays readable on it.
        if (mode == 0 && wallFit != null) return wallFit.prefersLight();
        return mode == 1 || (mode == 0 && weather.isDay());
    }

    private void onWeather(String line) {
        weatherLine = line;
        boolean nowDay = isDayNow();
        if (nowDay != day) { day = nowDay; applyTheme(); } else updateInfo();
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<AppEntry> all = new ArrayList<>();
        Set<String> seen = new HashSet<>(), hidden = hiddenApps();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || hidden.contains(p)) continue;
            Intent main = pm.getLeanbackLaunchIntentForPackage(p);
            // Some apps expose several TV entries (e.g. input shortcuts); keep the main one.
            if (main != null && main.getComponent() != null
                    && !main.getComponent().getClassName().equals(ri.activityInfo.name)) continue;
            if (!seen.add(p)) continue;
            all.add(new AppEntry(ri.activityInfo, String.valueOf(ri.loadLabel(pm)).trim()));
        }
        // Your arrangement (hold OK on an app to move it), else dock.xml; the dock fills up to its size.
        int dockSize = dockSize();
        List<String> dockPkgs = new ArrayList<>();
        String savedDock = prefs.getString("order_dock", null);
        if (savedDock != null) dockPkgs.addAll(Arrays.asList(savedDock.split(",")));
        for (String d : getResources().getStringArray(R.array.dock_packages)) if (!dockPkgs.contains(d)) dockPkgs.add(d);
        List<AppEntry> docked = new ArrayList<>();
        for (String p : dockPkgs) {
            if (docked.size() >= dockSize) break;
            for (AppEntry a : all) if (a.pkg.equals(p)) { docked.add(a); break; }
        }
        all.removeAll(docked);
        List<String> savedApps = Arrays.asList(prefs.getString("order_apps", "").split(","));
        Collections.sort(all, (a, b) -> {
            // Apps placed by you keep their spot; new ones go to the end, A–Z.
            int ia = savedApps.indexOf(a.pkg), ib = savedApps.indexOf(b.pkg);
            if (ia < 0) ia = Integer.MAX_VALUE;
            if (ib < 0) ib = Integer.MAX_VALUE;
            return ia != ib ? Integer.compare(ia, ib) : a.label.compareToIgnoreCase(b.label);
        });
        while (docked.size() < dockSize && !all.isEmpty()) docked.add(all.remove(0));
        int order = appsOrder();
        List<String> recent = RecentApps.all(this);
        recentSig = String.join(",", recent);
        if (order == ORDER_RECENT) {
            // Apps you opened through Talkies, newest first; the ones you haven't opened yet follow, A–Z.
            Collections.sort(all, (a, b) -> {
                int ia = recent.indexOf(a.pkg), ib = recent.indexOf(b.pkg);
                if (ia < 0) ia = Integer.MAX_VALUE;
                if (ib < 0) ib = Integer.MAX_VALUE;
                return ia != ib ? Integer.compare(ia, ib) : a.label.compareToIgnoreCase(b.label);
            });
        } else if (order == ORDER_AZ) {
            Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
        }
        apps.clear();
        apps.addAll(docked);
        apps.addAll(all);
        dockCount = docked.size();
        Log.i(TAG, "apps=" + apps.size() + " dock=" + dockCount);
    }

    private int appsOrder() {
        int n = prefs.getInt("apps_order", ORDER_YOURS);
        return n < 0 || n >= APPS_ORDER.length ? ORDER_YOURS : n;
    }

    private int dockSize() {
        int n = prefs.getInt("dock_size", 7);
        return n < DOCK_SIZES[0] || n > DOCK_SIZES[DOCK_SIZES.length - 1] ? 7 : n;
    }

    private void reloadApps() {
        endMove(false);
        loadApps();
        buildAppViews();
        if (zone == Z_DOCK) focusDock(Math.min(dockIdx, dockCount - 1));
        else if (zone == Z_ALL) { if (allTile != null) focusAllTile(); else focusDock(dockCount - 1); }
        else if (zone == Z_GRID) focusGrid(Math.min(gridIdx, gridTiles.size() - 1));
    }

    /** One app tile: its banner (or icon) on a rounded card. */
    private View appTile(AppEntry app, float wDp, float hDp) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(theme.tileBg, 0, 11));
        final float r = px(11);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        Drawable banner = app.banner(pm);
        ImageView img = new ImageView(this);
        if (banner != null) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            img.setImageDrawable(app.icon(pm));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(hDp * 0.6f), px(hDp * 0.6f));
            lp.gravity = android.view.Gravity.CENTER;
            card.addView(img, lp);
        }
        card.setContentDescription(app.label);
        card.setLayoutParams(new LinearLayout.LayoutParams(px(wDp), px(hDp)));
        return card;
    }

    /** After the dock: a round button like the icons at the top, a little larger. Its look is set in styleTop. */
    private View allAppsButton() {
        ImageView b = new ImageView(this);
        b.setImageResource(R.drawable.ic_apps);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription("All apps");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(ALL_BUTTON), px(ALL_BUTTON));
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        b.setLayoutParams(lp);
        return b;
    }

    private void buildAppViews() {
        dock.removeAllViews();
        dockTiles.clear();
        // The dock's apps and, when any are left over, the round All apps button share the band's width: seven
        // apps are 106 dp wide, five 146, ten 73.
        boolean more = apps.size() > dockCount;
        int n = dockSize();
        float inner = getResources().getDisplayMetrics().widthPixels / dp - 40 - 36;
        float w = Math.min(DOCK_MAX_W, (inner - (more ? n * DOCK_GAP + ALL_BUTTON : (n - 1) * DOCK_GAP)) / n);
        float h = w * 9 / 16;
        for (int i = 0; i < dockCount; i++) {
            View t = appTile(apps.get(i), w, h);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
            if (i > 0) lp.leftMargin = px(DOCK_GAP);
            dock.addView(t, lp);
            dockTiles.add(t);
        }
        allTile = more ? allAppsButton() : null;
        if (allTile != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) allTile.getLayoutParams();
            lp.leftMargin = px(DOCK_GAP);
            dock.addView(allTile, lp);
        }
        grid.removeAllViews();
        gridTiles.clear();
        LinearLayout row = null;
        for (int i = dockCount; i < apps.size(); i++) {
            int g = i - dockCount;
            if (g % GRID_COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (g > 0) rl.topMargin = px(GRID_GAP_Y);
                grid.addView(row, rl);
            }
            View t = appTile(apps.get(i), GRID_TILE_W, GRID_TILE_H);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
            if (g % GRID_COLS > 0) lp.leftMargin = px(GRID_GAP_X);
            row.addView(t, lp);
            gridTiles.add(t);
        }
        allAppsLabel.setVisibility(gridTiles.isEmpty() ? View.GONE : View.VISIBLE);
        layoutPage();
        focusedView = null;
    }

    /** Where All apps starts: after the channel rows. */
    private int gridTopDp() { return CHAN_TOP + chanRows.size() * CHAN_ROW_H + 18 + 40; }

    /**
     * Places All apps under the channel rows and gives the page its full height (it is taller than the screen;
     * without this the parent caps it at the screen and everything below is clipped away).
     */
    private void layoutPage() {
        int gridTop = gridTopDp();
        ((ViewGroup.MarginLayoutParams) allAppsLabel.getLayoutParams()).topMargin = px(gridTop - 40);
        ((ViewGroup.MarginLayoutParams) grid.getLayoutParams()).topMargin = px(gridTop);
        int rows = (gridTiles.size() + GRID_COLS - 1) / GRID_COLS;
        ViewGroup.LayoutParams plp = page.getLayoutParams();
        plp.height = px(gridTop + rows * (GRID_TILE_H + GRID_GAP_Y) + 120);
        page.setLayoutParams(plp);
        allAppsLabel.requestLayout();
        grid.requestLayout();
    }

    // ---------------------------------------------------------------- channels

    /** Reloads the shown channels (and their artwork) in the background, then rebuilds the rows if anything changed. */
    private void refreshChannels() {
        List<Trending.Pick> watchNext = new ArrayList<>(watchNextPicks);
        background.execute(() -> {
            // The Watch Next row shows the same list as Continue: Watch Next plus anything played more recently.
            List<Channels.Channel> loaded = Channels.loadShown(this, continuePicks(RecentStore.load(this), watchNext));
            runOnUiThread(() -> {
                appChannels = loaded;
                showChannels(loaded);
            });
        });
    }

    /** The channel rows: Cricket and Football first while there are matches (Live scores), then your app channels. */
    private void showChannels(List<Channels.Channel> apps) {
        List<Channels.Channel> list = new ArrayList<>();
        List<Cricket.Match> cricketRow = cricketShown(), footballRow = footballShown();
        // The row with a match being played goes first: Football above Cricket while only football is live.
        boolean footballFirst = !footballRow.isEmpty() && footballRow.get(0).live
                && (cricketRow.isEmpty() || !cricketRow.get(0).live);
        if (footballFirst) list.add(scoresRow("football", "Football", footballRow));
        if (!cricketRow.isEmpty()) list.add(scoresRow("cricket", "Cricket", cricketRow));
        if (!footballRow.isEmpty() && !footballFirst) list.add(scoresRow("football", "Football", footballRow));
        list.addAll(apps);
        {
            {
                StringBuilder sig = new StringBuilder();
                for (Channels.Channel ch : list) {
                    sig.append(ch.key).append(':');
                    for (Trending.Pick p : ch.items) sig.append(p.id).append(',');
                }
                if (sig.toString().equals(chanSignature)) return;
                boolean inRows = zone == Z_CHAN;
                if (inRows && !refocusChannels && android.os.SystemClock.uptimeMillis() - lastKeyAt < CHAN_IDLE_MS) {
                    // You're moving through the rows: don't shift them under you. Try again once the remote is still.
                    if (!chanPending) {
                        chanPending = true;
                        root.postDelayed(() -> { chanPending = false; showChannels(appChannels); }, CHAN_IDLE_MS);
                    }
                    return;
                }
                // Focus is in the rows (resting there, or a Watch Next card was just removed): stay on the same row
                // and card after the rebuild, found by what they are, since a score update can reorder them.
                String atRow = inRows && chanIdx < chanRows.size() ? chanRows.get(chanIdx).key : null;
                String atCard = null;
                if (atRow != null) {
                    List<Trending.Pick> items = chanRows.get(chanIdx).items;
                    if (chanCol[chanIdx] < items.size()) atCard = cardKey(items.get(chanCol[chanIdx]));
                }
                chanSignature = sig.toString();
                buildChannelRows(list);
                if (inRows && !chanRows.isEmpty()) {
                    int r = Math.min(chanIdx, chanRows.size() - 1);
                    for (int i = 0; i < chanRows.size(); i++) if (chanRows.get(i).key.equals(atRow)) r = i;
                    List<Trending.Pick> items = chanRows.get(r).items;
                    int c = Math.min(chanCol[r], items.size() - 1);
                    for (int j = 0; j < items.size(); j++) if (cardKey(items.get(j)).equals(atCard)) c = j;
                    focusChan(r, c);
                }
                refocusChannels = false;
            }
        }
    }

    /** What a card shows, unchanged by a new score: the match, or the pick itself. */
    private static String cardKey(Trending.Pick p) {
        return p.extra instanceof Cricket.Match ? ((Cricket.Match) p.extra).id : String.valueOf(p.id);
    }

    private Channels.Channel scoresRow(String key, String title, List<Cricket.Match> list) {
        Channels.Channel c = new Channels.Channel();
        c.key = key;
        c.name = title;
        c.title = title;
        for (Cricket.Match m : list) {
            Trending.Pick p = new Trending.Pick();
            p.kind = key;
            p.extra = m;
            p.id = (m.id + m.s1 + m.s2 + m.status).hashCode();
            p.title = m.name;
            // A football match that hasn't started has no status yet: say when it starts instead.
            String st = m.status.isEmpty() && m.start != null ? "starts " + m.start : m.status;
            p.meta = m.kind.isEmpty() ? st : st.isEmpty() ? m.kind : m.kind + " · " + st;
            p.pkg = m.watchPkg;
            p.intentUri = m.watchUri;
            c.items.add(p);
        }
        return c;
    }

    private static boolean isScores(Channels.Channel ch) { return "cricket".equals(ch.key) || "football".equals(ch.key); }

    private void buildChannelRows(List<Channels.Channel> list) {
        chanRows = list;
        channelsBox.removeAllViews();
        chanScrolls.clear();
        chanSprings.clear();
        chanCards.clear();
        int[] old = chanCol;
        chanCol = new int[list.size()];
        for (int r = 0; r < list.size(); r++) {
            Channels.Channel ch = list.get(r);
            if (r < old.length) chanCol[r] = Math.min(old[r], ch.items.size() - 1);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            header.setPadding(px(40), 0, px(40), 0);
            if (isScores(ch)) {
                TextView dot = new TextView(this);
                dot.setText("●");
                dot.setTextColor(0xFFFF453A);
                dot.setTextSize(12);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dl.rightMargin = px(8);
                if (!ch.items.isEmpty() && ((Cricket.Match) ch.items.get(0).extra).live) header.addView(dot, dl);
            } else if (ch.pkg != null) {
                ImageView icon = new ImageView(this);
                try {
                    // App icons can be 512 px; keep a 20 dp copy.
                    Drawable d = pm.getApplicationIcon(ch.pkg);
                    Bitmap b = Bitmap.createBitmap(px(20), px(20), Bitmap.Config.ARGB_8888);
                    d.setBounds(0, 0, px(20), px(20));
                    d.draw(new android.graphics.Canvas(b));
                    icon.setImageBitmap(b);
                } catch (PackageManager.NameNotFoundException ignored) { }
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(20), px(20));
                il.rightMargin = px(10);
                header.addView(icon, il);
            }
            TextView title = new TextView(this);
            title.setTag("chanTitle");
            title.setText(ch.title);
            title.setTextSize(16);
            title.setTypeface(getResources().getFont(R.font.outfit));
            title.setFontVariationSettings("'wght' 550");
            header.addView(title);
            row.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(28)));

            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            hsv.setFocusable(false);
            hsv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            hsv.setClipChildren(false);
            hsv.setClipToPadding(false);
            hsv.setPadding(0, px(8), 0, px(8));
            LinearLayout track = new LinearLayout(this);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setClipChildren(false);
            track.setPadding(px(40), 0, px(960), 0);
            List<View> cards = new ArrayList<>();
            int w = cardW(ch);
            for (int i = 0; i < ch.items.size(); i++) {
                View card = channelCard(ch.items.get(i));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(w), px(CH_H));
                if (i > 0) cl.leftMargin = px(CH_GAP);
                track.addView(card, cl);
                cards.add(card);
            }
            hsv.addView(track);
            row.addView(hsv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CH_H + 16)));

            LinearLayout caption = new LinearLayout(this);
            caption.setTag("caption");
            caption.setOrientation(LinearLayout.VERTICAL);
            caption.setPadding(px(42), px(6), px(40), 0);
            caption.setAlpha(0f);
            TextView name = new TextView(this);
            name.setTag("name");
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextSize(15);
            name.setTypeface(getResources().getFont(R.font.outfit));
            name.setFontVariationSettings("'wght' 600");
            caption.addView(name);
            TextView meta = new TextView(this);
            meta.setTag("meta");
            meta.setSingleLine(true);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            meta.setTextSize(12.5f);
            meta.setTypeface(getResources().getFont(R.font.outfit));
            caption.addView(meta);
            row.addView(caption, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)));

            channelsBox.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CHAN_ROW_H)));
            chanScrolls.add(hsv);
            chanCards.add(cards);
            final int rr = r;
            Spring sp = new Spring(0.34f, 1f, px(chanCol[rr] * (w + CH_GAP)), 0.25f);
            chanSprings.add(sp);
            hsv.post(() -> hsv.scrollTo(Math.round(sp.value), 0));
        }
        styleChannels();
        layoutPage();
    }

    /** Cricket cards are wider: a Test's scores ("425/9d & 84/1 (25)") need the room. */
    private static int cardW(Channels.Channel ch) { return isScores(ch) ? CRICKET_W : CH_W; }

    /** A card in a channel row: its artwork (loaded in the background), or the app's banner and the title. */
    private View channelCard(Trending.Pick p) {
        if (p.extra instanceof Cricket.Match) return cricketCard((Cricket.Match) p.extra);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(theme.tileBg, 0, 10));
        final float r = px(10);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (p.image != null && p.image.isFile()) {
            final File f = p.image;
            background.execute(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
                if (b != null) runOnUiThread(() -> img.setImageBitmap(b));
            });
        } else if (p.pkg != null) {
            showAppLogo(card, img, p.pkg);
        }
        if (p.progress >= 0) {
            View fill = new View(this);
            fill.setBackground(rounded(0xFFFFFFFF, 0, 2));
            FrameLayout track = new FrameLayout(this);
            track.setBackground(rounded(0x4DFFFFFF, 0, 2));
            track.addView(fill, new FrameLayout.LayoutParams(Math.round(px(CH_W - 24) * Math.max(0.02f, p.progress)), ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(px(CH_W - 24), px(3));
            tl.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            tl.bottomMargin = px(8);
            card.addView(track, tl);
        }
        card.setContentDescription(p.title);
        return card;
    }

    /** A match card: each team's flag, short name and score, then the status ("India need 38 off 24"). */
    private View cricketCard(Cricket.Match m) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(theme.tileBg, 0, 10));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(14), px(12), px(14), px(10));
        col.addView(teamLine(m.t1, m.s1, m.img1));
        col.addView(teamLine(m.t2, m.s2, m.img2));
        TextView st = new TextView(this);
        st.setTag("cricketText");
        String kind = m.kind.isEmpty() ? "" : m.kind + "  ·  ";
        String status = m.live ? "LIVE  ·  " + kind + m.status : !m.ended && m.start != null ? kind + "starts " + m.start : kind + m.status;
        SpannableStringBuilder sb = new SpannableStringBuilder(status);
        if (m.live) sb.setSpan(new ForegroundColorSpan(0xFFFF453A), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        st.setText(sb);
        st.setSingleLine(true);
        st.setEllipsize(android.text.TextUtils.TruncateAt.END);
        st.setTextSize(12);
        st.setTypeface(getResources().getFont(R.font.outfit));
        st.setTextColor(theme.text2);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        st.setGravity(android.view.Gravity.BOTTOM);
        col.addView(st, sl);
        card.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.setContentDescription(m.name + ". " + m.status);
        return card;
    }

    private View teamLine(String team, String runs, String flagUrl) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView flag = new ImageView(this);
        flag.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (flagUrl != null && !flagUrl.isEmpty()) {
            background.execute(() -> {
                File f = WatchNext.cached(this, new File(getExternalFilesDir(null), "cricket/flags"), flagUrl, 64);
                Bitmap b = f == null ? null : BitmapFactory.decodeFile(f.getPath());
                if (b != null) runOnUiThread(() -> flag.setImageBitmap(b));
            });
        }
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(px(20), px(20));
        fl.rightMargin = px(8);
        line.addView(flag, fl);
        TextView name = new TextView(this);
        name.setTag("cricketText");
        name.setText(team);
        name.setTextSize(15);
        name.setTypeface(getResources().getFont(R.font.outfit));
        name.setFontVariationSettings("'wght' 600");
        name.setTextColor(theme.text);
        line.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView sc = new TextView(this);
        sc.setTag("cricketText");
        sc.setText(runs);
        sc.setSingleLine(true);
        sc.setTextSize(14);
        sc.setTypeface(getResources().getFont(R.font.outfit));
        sc.setFontVariationSettings("'wght' 500");
        sc.setTextColor(theme.text);
        line.addView(sc);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(26));
        line.setLayoutParams(ll);
        return line;
    }

    private void styleChannels() {
        for (int i = 0; i < channelsBox.getChildCount(); i++) {
            View row = channelsBox.getChildAt(i);
            TextView t = row.findViewWithTag("chanTitle"), n = row.findViewWithTag("name"), m = row.findViewWithTag("meta");
            if (t != null) t.setTextColor(theme.text);
            if (n != null) n.setTextColor(theme.text);
            if (m != null) m.setTextColor(theme.text2);
        }
    }

    // ---------------------------------------------------------------- picks

    private void loadPicks() {
        recentPicks = continuePicks(RecentStore.load(this), watchNextPicks);
        trendPicks = trending.cached(Trending.TRENDING);
        indiaPicks = trending.cached(Trending.INDIA);
    }

    private List<Trending.Pick> picksFor(int t) { return t == 0 ? recentPicks : t == 1 ? trendPicks : indiaPicks; }

    /**
     * Continue: what apps put on Android's Watch Next list (Netflix, Prime Video…, opening the right episode) plus what
     * other apps reported playing, newest first. First the newest title from each app (so one app can't take over the
     * front of the row), then the rest in order, until there are at least ten cards (up to sixteen).
     */
    private List<Trending.Pick> continuePicks(List<Trending.Pick> sessions, List<Trending.Pick> watchNext) {
        // Titles you removed (hold OK → Remove from Continue) stay out until the app reports a new episode.
        Set<String> hidden = prefs.getStringSet("hidden_continue", java.util.Collections.emptySet());
        List<Trending.Pick> wnShown = new ArrayList<>();
        for (Trending.Pick p : watchNext) if (!hidden.contains(continueKey(p))) wnShown.add(p);
        watchNext = wnShown;
        // An app's Watch Next entries (they open the right episode) win over what we saw it play, unless it played
        // something more recently than its newest entry: JioHotstar, for one, doesn't always update Watch Next.
        Map<String, Long> newest = new java.util.HashMap<>();
        for (Trending.Pick p : watchNext) newest.merge(p.pkg, p.at, Math::max);
        List<Trending.Pick> all = new ArrayList<>(watchNext);
        for (Trending.Pick p : sessions) {
            Long wn = newest.get(p.pkg);
            if (wn == null || p.at > wn) all.add(p);
        }
        Collections.sort(all, (a, b) -> Long.compare(b.at, a.at));
        // YouTube fills up fastest (every video you play), so it gets at most two cards.
        List<Trending.Pick> capped = new ArrayList<>();
        int youtube = 0;
        for (Trending.Pick p : all) {
            if ("com.google.android.youtube.tv".equals(p.pkg) && ++youtube > MAX_YOUTUBE) continue;
            capped.add(p);
        }
        List<Trending.Pick> out = new ArrayList<>(), rest = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Trending.Pick p : capped) (seen.add(p.pkg) ? out : rest).add(p);
        for (Trending.Pick p : rest) if (out.size() < MIN_CONTINUE) out.add(p);
        return out.size() > MAX_CONTINUE ? new ArrayList<>(out.subList(0, MAX_CONTINUE)) : out;
    }

    /**
     * The TMDB tabs start somewhere new each hour, so the large card changes through the day: 24 picks moving on by
     * 4 an hour come round every 6 hours (when the list is refreshed anyway). The row shows 12 of them, which keeps
     * the posters in memory the same as before.
     */
    private static List<Trending.Pick> rotated(List<Trending.Pick> all) {
        if (all.isEmpty()) return all;
        int start = (int) ((Trending.localHour() * Trending.SHOWN) % all.size());
        List<Trending.Pick> out = new ArrayList<>();
        for (int i = 0; i < Math.min(ROW_PICKS, all.size()); i++) out.add(all.get((start + i) % all.size()));
        return out;
    }

    private static final int ROW_PICKS = 12;

    private void buildPicksRow(int anchor) {
        pickHour = Trending.localHour();
        if (!forYou) {
            // Hidden: no cards (so focus skips the section), no pick behind the background.
            rowPicks = new ArrayList<>();
            picksRow.setItems(new ArrayList<>(), 0);
            picksRow.setVisibility(View.GONE);
            showPicksHint();
            styleTop();
            updateAmbient();
            return;
        }
        if (picksFor(tabIdx).isEmpty()) {
            for (int t = 0; t < TAB_IDS.length; t++) if (!picksFor(t).isEmpty()) { tabIdx = t; break; }
        }
        rowPicks = tabIdx == 0 ? picksFor(0) : rotated(picksFor(tabIdx));
        StringBuilder from = new StringBuilder();
        if (tabIdx == 0) for (Trending.Pick p : rowPicks) from.append(' ').append(p.pkg.substring(p.pkg.lastIndexOf('.') + 1));
        Log.i(TAG, "row " + TAB_IDS[tabIdx] + ": " + rowPicks.size() + " cards" + from);
        List<View> views = new ArrayList<>();
        for (Trending.Pick p : rowPicks) views.add(pickView(p));
        picksRow.setItems(views, anchor);
        updatePickImages(picksRow.anchor());
        picksRow.setVisibility(rowPicks.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        showPicksHint();
        styleTop();
        updateAmbient();
    }

    /** Nothing to show yet: say why, rather than leave a gap. Without a TMDB key only Continue ever fills. */
    private void showPicksHint() {
        boolean show = forYou && rowPicks.isEmpty();
        picksHint.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        picksHint.setText(Keys.read(this, Keys.TMDB) == null
                ? "Trending and new picks need a free TMDB key: Control Center → Talkies settings → Keys and location"
                : trending.matchedApps() == 0
                ? "Trending shows what's on your streaming apps, and none Talkies knows is installed. With YouTube installed, it shows trailers instead."
                : "Picks appear here once Talkies is online. Shows you watch show up under Continue.");
    }

    /** A pick: the image card (with a progress bar when the app shares one), its title, and a description line. */
    private View pickView(Trending.Pick p) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClipChildren(false);
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(theme.tileBg, 0, 14));
        final float r = px(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        // Two layers: the landscape picture (large card) and the poster on top (small cards); see updatePickImages.
        ImageView back = new ImageView(this);
        back.setTag("backdrop");
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (p.image == null || !p.image.isFile()) showAppLogo(card, back, p.pkg);
        card.addView(back, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView poster = new ImageView(this);
        poster.setTag("poster");
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setAlpha(0f);
        card.addView(poster, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (p.progress >= 0) {
            FrameLayout track = new FrameLayout(this);
            track.setBackground(rounded(0x4DFFFFFF, 0, 2));
            View fill = new View(this);
            fill.setBackground(rounded(0xFFFFFFFF, 0, 2));
            track.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            track.addOnLayoutChangeListener((v, l, t, rr, b, ol, ot, orr, ob) -> {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                int w = Math.round((rr - l) * Math.max(0.02f, p.progress));
                if (lp.width != w) { lp.width = w; fill.setLayoutParams(lp); }
            });
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(4));
            tl.gravity = android.view.Gravity.BOTTOM;
            tl.setMargins(px(14), 0, px(14), px(12));
            card.addView(track, tl);
        }
        item.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(PicksRow.CARD_H)));
        TextView name = new TextView(this);
        name.setTag("name");
        name.setText(p.title);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextSize(16);
        name.setTypeface(getResources().getFont(R.font.outfit));
        name.setFontVariationSettings("'wght' 600");
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = px(9);
        nl.leftMargin = px(2);
        item.addView(name, nl);
        TextView meta = new TextView(this);
        meta.setTag("meta");
        meta.setText(p.meta);
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.setTextSize(12.5f);
        meta.setTypeface(getResources().getFont(R.font.outfit));
        meta.setFontVariationSettings("'wght' 400");
        meta.setAlpha(0f);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = px(2);
        ml.leftMargin = px(2);
        item.addView(meta, ml);
        stylePickText(item);
        return item;
    }

    /**
     * Loads each pick card's picture for its size: the large card gets the full landscape picture (TMDB's 1280 px
     * backdrop); small cards show the portrait poster, or a half-size landscape picture when there is no poster.
     * Pictures a card no longer needs are released, so only one full-size picture is ever in memory.
     */
    private void updatePickImages(int anchor) {
        for (int i = 0; i < picksRow.getChildCount() && i < rowPicks.size(); i++) {
            View item = picksRow.getChildAt(i);
            Trending.Pick p = rowPicks.get(i);
            ImageView back = item.findViewWithTag("backdrop"), poster = item.findViewWithTag("poster");
            if (back == null || poster == null) continue;
            boolean large = i == anchor, usePoster = !large && p.poster != null && p.poster.isFile();
            if (usePoster) loadInto(poster, p.poster, 1);
            poster.animate().alpha(usePoster ? 1f : 0f).setDuration(250).start();
            if (p.image == null || !p.image.isFile()) continue;
            if (large) loadInto(back, p.image, 1);
            else if (!usePoster) loadInto(back, p.image, 2);
            else {
                // Covered by the poster: let it go once the fade is done.
                final ImageView b = back;
                back.postDelayed(() -> {
                    if (b.getAlpha() >= 0 && rowPicks.indexOf(p) != picksRow.anchor() && poster.getAlpha() > 0.99f) {
                        b.setImageDrawable(null);
                        b.setTag(R.id.img_state, null);
                    }
                }, 400);
            }
        }
    }

    /**
     * A card with no picture (JioHotstar shares none while playing): the app's colour, with its logo at its own sharp
     * size in the middle, instead of the small banner stretched across the card.
     */
    private void showAppLogo(View card, ImageView v, String pkg) {
        AppEntry a = appFor(pkg);
        Drawable logo = a != null ? a.banner(pm) : null;
        if (logo == null) {
            try { logo = pm.getApplicationBanner(pkg); } catch (PackageManager.NameNotFoundException e) { return; }
        }
        int c = a != null ? a.glowColor(pm) : 0xFF3A3A3C;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{blend(c, 0xFF000000, 0.45f), blend(c, 0xFF000000, 0.8f)});
        g.setCornerRadius(px(14));
        card.setBackground(g);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageDrawable(logo);
        // The logo takes about half the card's width, whatever the card's size.
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            int padX = Math.round((r - l) * 0.24f), padY = Math.round((b - t) * 0.22f);
            if (view.getPaddingLeft() != padX || view.getPaddingTop() != padY) view.post(() -> view.setPadding(padX, padY, padX, padY));
        });
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** Decodes `file` at 1/`sample` size in the background and shows it, unless it's already showing. */
    private void loadInto(ImageView v, File file, int sample) {
        String want = file.getName() + "@" + sample;
        if (want.equals(v.getTag(R.id.img_state))) return;
        v.setTag(R.id.img_state, want);
        background.execute(() -> {
            Bitmap b = decode(file, sample);
            runOnUiThread(() -> { if (want.equals(v.getTag(R.id.img_state)) && b != null) v.setImageBitmap(b); });
        });
    }

    private void stylePickText(View item) {
        TextView name = item.findViewWithTag("name"), meta = item.findViewWithTag("meta");
        if (name != null) name.setTextColor(theme.text);
        if (meta != null) meta.setTextColor(theme.text2);
    }

    /** Photos don't need an alpha channel: RGB_565 halves their memory with no visible difference here. */
    private static Bitmap decode(File f, int sample) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /**
     * The background glows in the colour of the focused item's app: the dock or All apps tile, the pick's app, or the
     * channel row's app (Netflix red, Prime Video blue…). It keeps the last colour on the icons and in menus, and for
     * picks from apps that aren't installed. Your own wallpaper replaces it.
     */
    private void updateAmbient() {
        if (hasWallpaper) return;
        AppEntry a = null;
        if (zone == Z_DOCK && dockIdx < apps.size()) a = apps.get(dockIdx);
        else if (zone == Z_GRID && dockCount + gridIdx < apps.size()) a = apps.get(dockCount + gridIdx);
        else if ((zone == Z_PICKS || zone == Z_TABS) && !rowPicks.isEmpty())
            a = appFor(rowPicks.get(Math.min(picksRow.anchor(), rowPicks.size() - 1)).pkg);
        else if (zone == Z_CHAN && chanIdx < chanRows.size()) {
            List<Trending.Pick> items = chanRows.get(chanIdx).items;
            if (!items.isEmpty()) a = appFor(items.get(Math.min(chanCol[chanIdx], items.size() - 1)).pkg);
        }
        if (a != null) backdrop.setGlow(a.glowColor(pm));
    }

    private AppEntry appFor(String pkg) {
        if (pkg != null) for (AppEntry a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private void refreshWatchNext() {
        background.execute(() -> {
            List<Trending.Pick> list = WatchNext.load(this);
            runOnUiThread(() -> {
                watchNextPicks = list;
                refreshChannels();
                List<Trending.Pick> merged = continuePicks(RecentStore.load(this), watchNextPicks);
                if (!sameKeys(recentPicks, merged)) {
                    recentPicks = merged;
                    // Also when the row is empty: on a new TV it waits on Trending, which may never fill (no key).
                    if ((tabIdx == 0 || rowPicks.isEmpty()) && zone != Z_PICKS) buildPicksRow(0);
                    else styleTop();
                }
            });
        });
    }

    private static boolean sameKeys(List<Trending.Pick> a, List<Trending.Pick> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).key().equals(b.get(i).key())) return false;
        return true;
    }

    private void onPicks(List<Trending.Pick> all) {
        trendPicks = trending.cached(Trending.TRENDING);
        indiaPicks = trending.cached(Trending.INDIA);
        if (zone != Z_PICKS && zone != Z_TABS) { tabIdx = TAB_TRENDING; buildPicksRow(0); } else styleTop();
    }

    /** The section opens on Trending; if it had to fall back (no picks yet), switch back once they exist. */
    private void backToTrending() {
        if (tabIdx != TAB_TRENDING && !trendPicks.isEmpty() && zone != Z_PICKS && zone != Z_TABS) {
            tabIdx = TAB_TRENDING;
            buildPicksRow(0);
        } else styleTop();
    }

    // ---------------------------------------------------------------- focus

    /** Grows the focused card or tile (and shrinks the previous one); the ring sits on it. */
    private void setFocus(View v, float scale, float radiusDp) {
        View old = focusedView;
        if (old != null && old != v) scaleTo(old, 1f);
        focusedView = v;
        ringRadius = px(radiusDp);
        ring.animate().alpha(v == null ? 0f : 1f).setDuration(150).start();
        if (v == null) return;
        scaleTo(v, scale);
        snapRing();
    }

    private void scaleTo(View v, float scale) {
        Spring sp = scaleSprings.get(v);
        if (sp == null) scaleSprings.put(v, sp = new Spring(0.34f, 0.74f, v.getScaleX()));
        sp.target = scale;
        startMotion();
    }

    private RectF rectOf(View v) {
        int[] a = new int[2], b = new int[2];
        v.getLocationInWindow(a);
        ring.getLocationInWindow(b);
        float l = a[0] - b[0], t = a[1] - b[1];
        tmp.set(l, t, l + v.getWidth() * v.getScaleX(), t + v.getHeight() * v.getScaleY());
        return tmp;
    }

    /** Puts the ring on the focused card, at its current scale. Also called on PicksRow's frames. */
    private void snapRing() {
        if (focusedView == null) return;
        ringNow.set(rectOf(focusedView));
        ring.setRing(ringNow, ringRadius * focusedView.getScaleX());
    }

    /** Keeps the ring on its card for a while (PicksRow and the captions move on their own). */
    private void followRing(long ms) {
        followUntil = Math.max(followUntil, System.nanoTime() + ms * 1_000_000L);
        startMotion();
    }

    private void startMotion() {
        if (motionPosted) return;
        motionPosted = true;
        motionNanos = 0;
        android.view.Choreographer.getInstance().postFrameCallback(this::doMotion);
    }

    /** One frame: steps every spring and applies them. Stops posting itself once all have settled. */
    private void doMotion(long now) {
        float dt = motionNanos == 0 ? 1 / 60f : Math.min(0.05f, (now - motionNanos) / 1e9f);
        motionNanos = now;
        boolean moving = pageSpring.step(dt);
        page.setTranslationY(-pageSpring.value);
        for (int i = 0; i < chanSprings.size() && i < chanScrolls.size(); i++) {
            Spring sp = chanSprings.get(i);
            if (!sp.step(dt) && chanScrolls.get(i).getScrollX() == Math.round(sp.value)) continue;
            moving = true;
            chanScrolls.get(i).scrollTo(Math.round(sp.value), 0);
        }
        for (java.util.Iterator<Map.Entry<View, Spring>> it = scaleSprings.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<View, Spring> e = it.next();
            Spring sp = e.getValue();
            boolean m = sp.step(dt);
            e.getKey().setScaleX(sp.value);
            e.getKey().setScaleY(sp.value);
            if (m) moving = true;
            else if (sp.target == 1f) it.remove();
        }
        snapRing();
        if (moving || now < followUntil) android.view.Choreographer.getInstance().postFrameCallback(this::doMotion);
        else motionPosted = false;
    }

    /** Scrolls the page: the grid rows come up from below the fold; everything else sits at the top. */
    private void scrollPage(float y) {
        if (Math.abs(y - pageSpring.target) < 1) return;
        pageSpring.target = y;
        startMotion();
    }

    private void focusTop(int i) {
        zone = Z_TOP;
        iconIdx = i;
        scrollPage(0);
        setFocus(null, 1f, 0);
        styleTop();
    }

    private void focusTabs(int t) {
        zone = Z_TABS;
        scrollPage(0);
        if (t != tabIdx) {
            tabIdx = t;
            buildPicksRow(0);
        }
        setFocus(null, 1f, 0);
        styleTop();
    }

    private void focusPicks(int i) {
        if (rowPicks.isEmpty()) return;
        zone = Z_PICKS;
        scrollPage(0);
        boolean moved = i != picksRow.anchor();
        picksRow.setAnchor(i, moved);
        updatePickImages(i);
        updateAmbient();
        setFocus(picksRow.anchorCard(), 1.02f, 14);
        styleTop();
        followRing(moved ? 360 : 220);
    }

    private void focusDock(int i) {
        if (dockTiles.isEmpty()) return;
        zone = Z_DOCK;
        dockIdx = Math.max(0, Math.min(i, dockTiles.size() - 1));
        prefs.edit().putInt("last_dock_col", dockIdx).apply();
        scrollPage(0);
        setFocus(dockTiles.get(dockIdx), moving ? 1.22f : 1.14f, 11);
        updateAmbient();
        styleTop();
        followRing(260);
    }

    private void focusAllTile() {
        zone = Z_ALL;
        scrollPage(0);
        setFocus(null, 1f, 0);  // no ring: the button fills in, like the icons at the top (styleTop)
        updateAmbient();
        styleTop();
    }

    private void focusGrid(int i) {
        if (gridTiles.isEmpty()) return;
        zone = Z_GRID;
        gridIdx = Math.max(0, Math.min(i, gridTiles.size() - 1));
        int row = gridIdx / GRID_COLS;
        int gridTop = gridTopDp();
        float rowTop = gridTop + row * (GRID_TILE_H + GRID_GAP_Y);
        scrollPage(px(Math.max(gridTop - 96, rowTop - 190)));
        setFocus(gridTiles.get(gridIdx), moving ? 1.18f : 1.10f, 11);
        updateAmbient();
        styleTop();
        followRing(420);
    }

    private void focusChan(int r, int c) {
        if (chanRows.isEmpty()) return;
        zone = Z_CHAN;
        chanIdx = Math.max(0, Math.min(r, chanRows.size() - 1));
        List<Trending.Pick> items = chanRows.get(chanIdx).items;
        int col = Math.max(0, Math.min(c, items.size() - 1));
        chanCol[chanIdx] = col;
        scrollPage(px(CHAN_TOP + chanIdx * CHAN_ROW_H - 64));
        // Slide the row so the focused card sits at the left, like Projectivy.
        if (chanIdx < chanSprings.size()) chanSprings.get(chanIdx).target = px(col * (cardW(chanRows.get(chanIdx)) + CH_GAP));
        startMotion();
        for (int i = 0; i < channelsBox.getChildCount(); i++) {
            View caption = channelsBox.getChildAt(i).findViewWithTag("caption");
            if (caption == null) continue;
            if (i == chanIdx) {
                Trending.Pick p = items.get(col);
                ((TextView) caption.findViewWithTag("name")).setText(p.title);
                ((TextView) caption.findViewWithTag("meta")).setText(p.meta);
            }
            caption.animate().alpha(i == chanIdx ? 1f : 0f).setDuration(160).start();
        }
        setFocus(chanCards.get(chanIdx).get(col), 1.06f, 10);
        updateAmbient();
        styleTop();
        followRing(440);
    }

    /** Dock column (0–6) → grid column (0–5) and back, by position across the screen. */
    private int dockToGridCol(int c) { return Math.round(c * (GRID_COLS - 1) / (float) Math.max(1, dockCount - 1)); }

    private int gridToDockCol(int c) { return Math.round(c * Math.max(0, dockCount - 1) / (float) (GRID_COLS - 1)); }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean select = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE;
        if (!select && !nav && !back) return super.dispatchKeyEvent(e);
        lastKeyAt = android.os.SystemClock.uptimeMillis();
        // OK and Back act when let go, so a release whose press went to another app must do nothing here: an app
        // that closes as OK goes down (Netflix → Exit) would otherwise have its OK's release open the focused tile,
        // the same app, again.
        if ((select || back) && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) pressedHere = k;
        if ((select || back) && e.getAction() == KeyEvent.ACTION_UP) {
            boolean ours = pressedHere == k;
            pressedHere = 0;
            if (!ours) return true;
        }
        boolean onApp = zone == Z_DOCK || zone == Z_GRID || (zone == Z_MENU && menuMode == MENU_CHANNELS)
                || continueCard() != null || (zone == Z_TOP && iconIdx == P_SETTINGS);
        // Holding OK on an app opens its menu, on a Continue card the card's menu, and in the Channels editor
        // picks the row up (a timer, or the remote's long-press signal, whichever comes first).
        if (select && e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                holdFired = false;
                if (!moving && !menuMoving && onApp) root.postDelayed(holdTimer, HOLD_MS);
            } else if (!holdFired && !moving && !menuMoving && onApp) {
                root.removeCallbacks(holdTimer);
                onHold();
            }
        }
        if (select && e.getAction() == KeyEvent.ACTION_UP) {
            root.removeCallbacks(holdTimer);
            if (holdFired) { holdFired = false; return true; }  // this release just ends the hold
        }
        if (nav && e.getAction() == KeyEvent.ACTION_DOWN) {
            heldKey = e.getRepeatCount() > 0;
            String before = focusSignature();
            if (moving) moveApp(k); else if (menuMoving) moveMenuRow(k); else move(k);
            if (!before.equals(focusSignature())) navigationSound(k);
        }
        if ((select || back) && e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
            if (moving) endMove(select);
            else if (menuMoving) endMenuMove(select);
            else if (select) select();
            else back();
        }
        return true;
    }

    private int nextTab(int t, int step) {
        for (int i = t + step; i >= 0 && i < TAB_IDS.length; i += step) if (!picksFor(i).isEmpty()) return i;
        return -1;
    }

    private void move(int k) {
        boolean left = k == KeyEvent.KEYCODE_DPAD_LEFT, right = k == KeyEvent.KEYCODE_DPAD_RIGHT,
                up = k == KeyEvent.KEYCODE_DPAD_UP, down = k == KeyEvent.KEYCODE_DPAD_DOWN;
        boolean hasPicks = !rowPicks.isEmpty();
        switch (zone) {
            case Z_MENU:
                // The ends wrap around on a fresh press: Up from the first row reaches the last one (About, Updates…)
                // at once. Holding the key stops at the end instead of racing round again.
                if (up && menuIdx > 0) focusMenu(menuIdx - 1);
                else if (up && !heldKey && menuItems.size() > 1) focusMenu(menuItems.size() - 1);
                else if (down && menuIdx < menuItems.size() - 1) focusMenu(menuIdx + 1);
                else if (down && !heldKey && menuItems.size() > 1) focusMenu(0);
                else if (left || right) closeMenu();
                break;
            case Z_TOP:
                if (left && iconIdx > 0) focusTop(iconIdx - 1);
                else if (right && iconIdx < icons.length - 1 && icons[iconIdx + 1].getVisibility() == View.VISIBLE) focusTop(iconIdx + 1);
                else if (down) { if (hasPicks) focusTabs(tabIdx); else focusDock(dockIdx); }
                break;  // Up, and the ends of the row, go nowhere.
            case Z_TABS:
                if (left && nextTab(tabIdx, -1) >= 0) focusTabs(nextTab(tabIdx, -1));
                else if (right && nextTab(tabIdx, 1) >= 0) focusTabs(nextTab(tabIdx, 1));
                else if (up) focusTop(iconIdx);
                else if (down) focusPicks(picksRow.anchor());
                break;
            case Z_PICKS:
                if (left && picksRow.anchor() > 0) focusPicks(picksRow.anchor() - 1);
                else if (right && picksRow.anchor() < rowPicks.size() - 1) focusPicks(picksRow.anchor() + 1);
                else if (up) focusTabs(tabIdx);
                else if (down) focusDock(dockIdx);
                break;
            case Z_DOCK:
                if (left && dockIdx > 0) focusDock(dockIdx - 1);
                else if (right && dockIdx < dockTiles.size() - 1) focusDock(dockIdx + 1);
                else if (right && allTile != null) focusAllTile();
                else if (up) { if (hasPicks) focusPicks(picksRow.anchor()); else focusTop(iconIdx); }
                else if (down && !chanRows.isEmpty()) focusChan(0, chanCol[0]);
                else if (down && !gridTiles.isEmpty()) focusGrid(Math.min(dockToGridCol(dockIdx), gridTiles.size() - 1));
                break;
            case Z_ALL:
                if (left) focusDock(dockTiles.size() - 1);
                else if (up) { if (hasPicks) focusPicks(picksRow.anchor()); else focusTop(iconIdx); }
                else if (down && !chanRows.isEmpty()) focusChan(0, chanCol[0]);
                else if (down) focusGrid(GRID_COLS - 1);
                break;
            case Z_CHAN: {
                int n = chanRows.get(chanIdx).items.size(), c = chanCol[chanIdx];
                if (left && c > 0) focusChan(chanIdx, c - 1);
                else if (right && c < n - 1) focusChan(chanIdx, c + 1);
                else if (up && chanIdx == 0) focusDock(dockIdx);
                else if (up) focusChan(chanIdx - 1, chanCol[chanIdx - 1]);
                else if (down && chanIdx < chanRows.size() - 1) focusChan(chanIdx + 1, chanCol[chanIdx + 1]);
                else if (down && !gridTiles.isEmpty()) focusGrid(0);
                break;
            }
            case Z_GRID: {
                int row = gridIdx / GRID_COLS, col = gridIdx % GRID_COLS;
                if (left && col > 0) focusGrid(gridIdx - 1);
                else if (right && col < GRID_COLS - 1 && gridIdx + 1 < gridTiles.size()) focusGrid(gridIdx + 1);
                else if (up && row == 0 && !chanRows.isEmpty()) focusChan(chanRows.size() - 1, chanCol[chanRows.size() - 1]);
                else if (up && row == 0) focusDock(gridToDockCol(col));
                else if (up) focusGrid(gridIdx - GRID_COLS);
                else if (down && (row + 1) * GRID_COLS < gridTiles.size()) focusGrid(Math.min(gridIdx + GRID_COLS, gridTiles.size() - 1));
                break;
            }
        }
    }

    /**
     * What starts the screensaver now: Google TV Settings' "start screensaver" voice action (what the Assistant uses;
     * also on devices without SystemUI's shortcut, like the Google TV Streamer), else SystemUI's Somnambulator.
     * Null where neither exists: Control Center leaves the row out. The voice action idea is from tvOS Launcher.
     */
    private Intent screensaverIntent() {
        // In a task of its own: it's part of the TV's Settings app, and in Settings' own task a settings page left open
        // in the background would be what waking the TV shows, instead of Talkies.
        Intent voice = new Intent("com.google.android.pano.action.SLEEP")
                .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ResolveInfo r = pm.resolveActivity(voice, 0);
        if (r != null && r.activityInfo != null && !r.activityInfo.name.contains("EmptyStub")) return voice;
        Intent dream = new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
        return pm.resolveActivity(dream, 0) != null ? dream : null;
    }

    /** Where focus is: changes whenever a move took it somewhere, so a press at an end makes no sound. */
    private String focusSignature() {
        return zone + "," + iconIdx + "," + tabIdx + "," + picksRow.anchor() + "," + dockIdx + "," + gridIdx + "," + chanIdx
                + "," + (chanIdx < chanCol.length ? chanCol[chanIdx] : 0) + "," + menuIdx + "," + appIndex();
    }

    /**
     * Android's own navigation and click sounds. Talkies takes the remote's presses itself, so Android doesn't play
     * them; they follow the TV's System sounds switch (off: silent), as everywhere else on the TV.
     */
    private void navigationSound(int k) {
        root.playSoundEffect(k == KeyEvent.KEYCODE_DPAD_UP ? android.view.SoundEffectConstants.NAVIGATION_UP
                : k == KeyEvent.KEYCODE_DPAD_DOWN ? android.view.SoundEffectConstants.NAVIGATION_DOWN
                : k == KeyEvent.KEYCODE_DPAD_LEFT ? android.view.SoundEffectConstants.NAVIGATION_LEFT
                : android.view.SoundEffectConstants.NAVIGATION_RIGHT);
    }

    private void select() {
        root.playSoundEffect(android.view.SoundEffectConstants.CLICK);
        switch (zone) {
            case Z_MENU:
                if (menuIdx < menuActions.size()) {
                    Runnable a = menuActions.get(menuIdx);
                    boolean keep = menuKeepsOpen.get(menuIdx);
                    Object[] parent = {menuOpener, menuIdx};
                    if (!keep) closeMenu();
                    int before = menuGen;
                    fromMenuRow = true;
                    try { a.run(); } finally { fromMenuRow = false; }
                    // A row that opened another menu: Back comes here. (A row that kept its menu open and rebuilt
                    // it is the same level.)
                    if (!keep && menuGen != before && zone == Z_MENU && parent[0] != null) {
                        menuParents.push(parent);
                        menuOpener = a;
                    }
                }
                break;
            case Z_TOP:
                if (iconIdx == P_CONTROL) { openControlCenter(); menuOpener = this::openControlCenter; }
                else if (iconIdx == P_INPUTS) openInputs();
                else if (iconIdx == P_WIFI) start(new Intent(Settings.ACTION_WIFI_SETTINGS));
                else if (iconIdx == P_BELL) openUpdates();
                else openTvSettings();
                break;
            case Z_TABS:
                focusPicks(picksRow.anchor());
                break;
            case Z_PICKS:
                openPick(rowPicks.get(picksRow.anchor()));
                break;
            case Z_CHAN:
                openPick(chanRows.get(chanIdx).items.get(chanCol[chanIdx]));
                break;
            case Z_DOCK:
                startFromTile(apps.get(dockIdx).launchIntent());
                break;
            case Z_GRID:
                startFromTile(apps.get(dockCount + gridIdx).launchIntent());
                break;
            case Z_ALL:
                focusGrid(0);
                break;
        }
    }

    /** Opens a pick or channel card: through the app's own link to that title when it gave one, else the app. */
    private void openPick(Trending.Pick p) {
        if (p.intentUri != null) {
            try {
                startActivity(Intent.parseUri(p.intentUri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), fromTile());
                if (p.pkg != null) RecentApps.note(this, p.pkg);
                return;
            } catch (Exception ex) {
                Log.w(TAG, "Link failed, opening the app: " + ex.getClass().getSimpleName());
            }
        }
        // A match JioHotstar has no card for: open its search page and have SleepService type both teams in
        // (its search links ignore the text). With the service off, the empty search page still opens.
        if (p.extra instanceof Cricket.Match && Cricket.HOTSTAR.equals(p.pkg) && ((Cricket.Match) p.extra).search != null) {
            String text = ((Cricket.Match) p.extra).search;
            if (!SleepService.searchHotstar(text)) Log.i(TAG, "Accessibility off: opening JioHotstar search without " + text);
            start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.hotstar.com/in/explore")).setPackage(Cricket.HOTSTAR));
            return;
        }
        Intent i = p.pkg == null ? null : pm.getLeanbackLaunchIntentForPackage(p.pkg);
        if (i != null) startFromTile(i);
        else if (p.extra instanceof Cricket.Match && ((Cricket.Match) p.extra).football) Toast.makeText(this, "Talkies doesn't know where to watch football yet", Toast.LENGTH_SHORT).show();
        else if (p.extra instanceof Cricket.Match) Toast.makeText(this, "None of your apps has a card for this match", Toast.LENGTH_SHORT).show();
    }

    private void back() {
        if (zone == Z_MENU && !menuParents.isEmpty()) {
            Object[] parent = menuParents.pop();
            Runnable opener = (Runnable) parent[0];
            closeMenu();
            fromMenuRow = true;
            try { opener.run(); } finally { fromMenuRow = false; }
            menuOpener = opener;
            if (zone == Z_MENU) focusMenu(Math.min((Integer) parent[1], menuItems.size() - 1));
        } else if (zone == Z_MENU) closeMenu();
        else if (zone != Z_DOCK) focusDock(dockIdx);
    }

    /**
     * The TV's settings. Some firmware hides the standard entry (some projectors); Android TV's own settings app is
     * usually still there. An idea from CleanYourTV (MIT).
     */
    private void openTvSettings() {
        Intent i = new Intent(Settings.ACTION_SETTINGS);
        if (pm.resolveActivity(i, 0) == null) {
            Intent tv = new Intent(Intent.ACTION_MAIN).setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings");
            if (pm.resolveActivity(tv, 0) != null) i = tv;
            else Toast.makeText(this, "This TV has no settings app Talkies can open", Toast.LENGTH_SHORT).show();
        }
        start(i);
    }

    /**
     * The app grows out of the focused tile or card, from the ring's rectangle. It's the window animation Android
     * draws anyway, only starting from the tile instead of the middle, so it costs nothing extra. Talkies settings
     * → Apps open from their tile turns it off. An idea from Plume (MIT).
     */
    private Bundle fromTile() {
        if (!prefs.getBoolean("open_animation", true) || focusedView == null || ring.rect.isEmpty()) return null;
        RectF r = ring.rect;
        return android.app.ActivityOptions.makeScaleUpAnimation(ring, Math.round(r.left), Math.round(r.top),
                Math.round(r.width()), Math.round(r.height())).toBundle();
    }

    private void startFromTile(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), fromTile());
            String pkg = i.getPackage() != null ? i.getPackage() : i.getComponent() != null ? i.getComponent().getPackageName() : null;
            if (pkg != null && !pkg.equals(getPackageName())) RecentApps.note(this, pkg);
        } catch (ActivityNotFoundException | SecurityException ex) {
            Log.w(TAG, "Cannot start " + i, ex);
        }
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ex) {
            Log.w(TAG, "Cannot start " + i, ex);
        }
    }

    // ---------------------------------------------------------------- rearranging

    private int appIndex() {
        if (zone == Z_DOCK) return dockIdx;
        if (zone == Z_GRID) return dockCount + gridIdx;
        return -1;
    }

    private void startMove() {
        if (zone == Z_MENU) { startMenuMove(); return; }
        int ai = appIndex();
        if (ai < 0 || ai >= apps.size()) return;
        holdFired = true;
        moving = true;
        movingPkg = apps.get(ai).pkg;
        beforeMove = new ArrayList<>(apps);
        ring.setMoving(true);
        refocusMoving();
        moveHint.setText("Moving " + apps.get(ai).label + "  ·  OK to place  ·  Back to cancel");
        moveHint.setAlpha(0f);
        moveHint.setVisibility(View.VISIBLE);
        moveHint.animate().alpha(1f).setDuration(150).start();
        showServiceNote();
    }

    /** Moves the picked-up app one step. In a row it swaps with its neighbour; between the dock and the grid it
     *  trades places with the app there (the dock keeps seven); within the grid the others shift along. */
    private void moveApp(int k) {
        int ai = -1;
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).pkg.equals(movingPkg)) ai = i;
        if (ai < 0) return;
        int gridSize = apps.size() - dockCount, target = -1;
        boolean swap = true;
        if (ai < dockCount) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && ai > 0) target = ai - 1;
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && ai < dockCount - 1) target = ai + 1;
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && gridSize > 0) target = dockCount + Math.min(dockToGridCol(ai), gridSize - 1);
        } else {
            int g = ai - dockCount, row = g / GRID_COLS, col = g % GRID_COLS;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) target = ai - 1;
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && col < GRID_COLS - 1 && ai + 1 < apps.size()) target = ai + 1;
            else if (k == KeyEvent.KEYCODE_DPAD_UP && row == 0) target = Math.min(gridToDockCol(col), dockCount - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_UP) { target = ai - GRID_COLS; swap = false; }
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && (row + 1) * GRID_COLS < gridSize) {
                target = Math.min(ai + GRID_COLS, apps.size() - 1);
                swap = false;
            }
        }
        if (target < 0) return;
        if (swap) Collections.swap(apps, ai, target);
        else apps.add(target, apps.remove(ai));
        buildAppViews();
        refocusMoving();
    }

    /** Focus (and the lifted look) follows the app being moved to wherever it now is. */
    private void refocusMoving() {
        for (int i = 0; i < apps.size(); i++) {
            if (!apps.get(i).pkg.equals(movingPkg)) continue;
            final int at = i;
            root.post(() -> { if (at < dockCount) focusDock(at); else focusGrid(at - dockCount); });
            return;
        }
    }

    private void endMove(boolean keep) {
        if (!moving) return;
        moving = false;
        ring.setMoving(false);
        if (keep) saveOrder();
        else {
            apps.clear();
            apps.addAll(beforeMove);
            buildAppViews();
        }
        int at = 0;
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).pkg.equals(movingPkg)) at = i;
        final int pos = at;
        root.post(() -> { if (pos < dockCount) focusDock(pos); else focusGrid(pos - dockCount); });
        moveHint.animate().alpha(0f).setDuration(120).withEndAction(() -> { moveHint.setVisibility(View.GONE); showServiceNote(); }).start();
    }

    private void saveOrder() {
        StringBuilder d = new StringBuilder(), rest = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            StringBuilder b = i < dockCount ? d : rest;
            if (b.length() > 0) b.append(',');
            b.append(apps.get(i).pkg);
        }
        if (appsOrder() != ORDER_YOURS) {
            // All apps is sorted for you: only the dock's arrangement is yours to keep.
            prefs.edit().putString("order_dock", d.toString()).apply();
            loadApps();
            buildAppViews();
            return;
        }
        prefs.edit().putString("order_dock", d.toString()).putString("order_apps", rest.toString()).apply();
    }

    // ---------------------------------------------------------------- menus (Inputs, Control Center)

    private void beginMenu(int anchor, String heading) {
        menuGen++;
        if (!fromMenuRow) {  // a menu of its own, not one level down
            menuParents.clear();
            menuOpener = null;
        }
        menuAnchor = anchor;
        menuTile = null;
        menuClosed = null;
        menuItems.clear();
        menuActions.clear();
        menuKeepsOpen.clear();
        menuMode = MENU_PLAIN;
        menuScroll.animate().cancel();
        menu.removeAllViews();
        menuScroll.setBackground(rounded(theme.menuBg, theme.menuStroke, 20));
        menuScroll.scrollTo(0, 0);
        if (heading != null) {
            TextView h = new TextView(this);
            h.setText(heading);
            h.setTextSize(13);
            h.setTextColor(theme.text2);
            h.setTypeface(getResources().getFont(R.font.outfit));
            h.setFontVariationSettings("'wght' 500");
            h.setPadding(px(14), px(8), px(14), px(6));
            menu.addView(h);
        }
    }

    /**
     * Outfit's weights for the layouts' text, as (view id, weight) pairs. They're set here rather than in the layouts
     * because the layout attribute only works from Android 9, while this call works from Android 8.
     */
    private static void weigh(java.util.function.IntFunction<View> find, int... idWeight) {
        for (int i = 0; i < idWeight.length; i += 2)
            ((TextView) find.apply(idWeight[i])).setFontVariationSettings("'wght' " + idWeight[i + 1]);
    }

    /** A row with a label, an optional status on the right, and what OK does. */
    private View addRow(String label, String status, Runnable action, boolean keepOpen) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_input, menu, false);
        weigh(row::findViewById, R.id.input_label, 450, R.id.input_status, 400);
        ((TextView) row.findViewById(R.id.input_label)).setText(label);
        row.findViewById(R.id.input_dot).setVisibility(View.GONE);
        setStatus(row, status);
        menu.addView(row);
        menuItems.add(row);
        menuActions.add(action);
        menuKeepsOpen.add(keepOpen);
        return row;
    }

    private static void setStatus(View row, String status) {
        TextView t = row.findViewById(R.id.input_status);
        t.setText(status);
        t.setVisibility(status == null ? View.GONE : View.VISIBLE);
    }

    private void showMenu(int widthDp) {
        RectF a = new RectF(rectOf(menuTile != null ? menuTile : icons[menuAnchor]));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menuScroll.getLayoutParams();
        lp.width = px(widthDp);
        int screenW = getResources().getDisplayMetrics().widthPixels, screenH = getResources().getDisplayMetrics().heightPixels;
        menu.measure(View.MeasureSpec.makeMeasureSpec(px(widthDp), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int h = menu.getMeasuredHeight();
        if (menuTile != null) {
            // By the app or card: left-aligned to it, below it if there's room, else above it.
            lp.leftMargin = Math.max(px(24), Math.min(Math.round(a.left), screenW - px(widthDp + 24)));
            lp.topMargin = a.bottom + px(10) + h <= screenH - px(16) ? Math.round(a.bottom) + px(10)
                    : Math.max(px(16), Math.round(a.top) - px(10) - h);
        } else {
            // Under its icon, right-aligned to it, kept on screen.
            int left = Math.round(a.right) - px(widthDp);
            lp.leftMargin = Math.max(px(24), Math.min(left, screenW - px(widthDp + 24)));
            lp.topMargin = Math.round(a.bottom) + px(10);
        }
        // Long menus (Channels) scroll instead of running off the screen.
        int room = screenH - lp.topMargin - px(24);
        lp.height = h > room ? room : ViewGroup.LayoutParams.WRAP_CONTENT;
        menuScroll.setLayoutParams(lp);
        menuScroll.setVisibility(View.VISIBLE);
        menuScroll.setAlpha(0f);
        menuScroll.setTranslationY(-px(6));
        menuScroll.animate().alpha(1f).translationY(0).setDuration(150).start();
        zone = Z_MENU;
        focusMenu(0);
    }

    private void focusMenu(int i) {
        menuIdx = i;
        for (int r = 0; r < menuItems.size(); r++) {
            View row = menuItems.get(r);
            boolean f = r == i;
            row.setBackground(f ? rounded(theme.menuFocusBg, 0, 12) : null);
            ((TextView) row.findViewById(R.id.input_label)).setTextColor(f ? theme.menuFocusText : theme.text);
            ((TextView) row.findViewById(R.id.input_status)).setTextColor(f ? (theme.menuFocusText & 0x00FFFFFF) | 0x99000000 : theme.text2);
        }
        View row = menuItems.get(i);
        menuScroll.post(() -> {
            int top = row.getTop(), bottom = row.getBottom(), y = menuScroll.getScrollY(), h = menuScroll.getHeight();
            if (top - px(8) < y) menuScroll.smoothScrollTo(0, Math.max(0, top - px(52)));
            else if (bottom + px(8) > y + h) menuScroll.smoothScrollTo(0, bottom + px(52) - h);
        });
    }

    private void closeMenu() {
        menuScroll.animate().alpha(0f).setDuration(110).withEndAction(() -> menuScroll.setVisibility(View.GONE)).start();
        if (menuMode == MENU_CHANNELS && channelsEdited) refreshChannels();
        menuMode = MENU_PLAIN;
        Runnable back = menuClosed;
        menuClosed = null;
        menuTile = null;
        if (back != null) back.run(); else focusTop(menuAnchor);
    }

    private void openInputs() { openInputs(!prefs.getBoolean("inputs_connected_only", false)); }

    /**
     * HDMI and AV ports, then Live TV. With Talkies settings → Empty inputs set to "Under More inputs", ports with
     * nothing plugged in wait behind a More inputs row ({@code all} false). An idea from CleanYourTV (MIT).
     */
    private void openInputs(boolean all) {
        beginMenu(P_INPUTS, null);
        TvInputManager tim = getSystemService(TvInputManager.class);
        List<TvInputInfo> hdmi = new ArrayList<>(), av = new ArrayList<>(), tuners = new ArrayList<>();
        TvInputInfo tuner = null;
        Map<String, TvInputInfo> cec = new java.util.HashMap<>();  // port id → the HDMI-CEC device behind it
        if (tim != null) {
            for (TvInputInfo i : tim.getTvInputList()) {
                if (i.getParentId() != null) { cec.put(i.getParentId(), i); continue; } // devices behind a port: use the port
                switch (i.getType()) {
                    case TvInputInfo.TYPE_HDMI: hdmi.add(i); break;
                    case TvInputInfo.TYPE_COMPOSITE: case TvInputInfo.TYPE_COMPONENT:
                    case TvInputInfo.TYPE_SCART: case TvInputInfo.TYPE_SVIDEO: case TvInputInfo.TYPE_VGA: av.add(i); break;
                    case TvInputInfo.TYPE_TUNER: tuners.add(i); break;
                }
            }
        }
        // App channels (Zee5, Play Movies…) also register as tuners; prefer the TV's own one.
        String vendor = hdmi.isEmpty() ? null : hdmi.get(0).getServiceInfo().packageName;
        for (TvInputInfo i : tuners) {
            boolean system = (i.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (i.getServiceInfo().packageName.equals(vendor)) { tuner = i; break; }
            if (tuner == null && system) tuner = i;
        }
        Collections.sort(hdmi, (a, b) -> label(a).compareTo(label(b)));
        int n = 1, hidden = 0, firstEmpty = -1;
        boolean named = false;
        for (TvInputInfo i : hdmi) {
            String name = fixLabel(label(i), "HDMI " + n++);
            if (!connected(tim, i)) {
                if (!all) { hidden++; continue; }
                if (firstEmpty < 0) firstEmpty = menuItems.size();
            }
            View row = addInput(name, tim, i, passthrough(i));
            TvInputInfo behind = cec.get(i.getId());
            String device = cecStatus(tim, behind);
            if (device != null) {
                setStatus(row, device);
                named = true;
                ImageView dot = row.findViewById(R.id.input_dot);
                if (dot.getVisibility() == View.VISIBLE) dot.setColorFilter(identify(behind).color, PorterDuff.Mode.SRC_IN);
            }
        }
        n = 1;
        for (TvInputInfo i : av) {
            String name = av.size() == 1 ? "AV" : fixLabel(label(i), "AV " + n++);
            if (!connected(tim, i)) {
                if (!all) { hidden++; continue; }
                if (firstEmpty < 0) firstEmpty = menuItems.size();
            }
            addInput(name, tim, i, passthrough(i));
        }
        final TvInputInfo tunerInfo = tuner;
        addRow("Live TV", null, () -> {
            if (tunerInfo != null) {
                Intent v = new Intent(Intent.ACTION_VIEW);
                v.setDataAndType(TvContract.buildChannelsUriForInput(tunerInfo.getId()), "vnd.android.cursor.dir/channel");
                try { startActivity(v.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                catch (ActivityNotFoundException ignored) { }
            }
            Intent tv = new Intent(Intent.ACTION_VIEW);
            tv.setDataAndType(TvContract.Channels.CONTENT_URI, "vnd.android.cursor.dir/channel");
            start(tv);
        }, false);
        if (hidden > 0) addRow("More inputs", String.valueOf(hidden), () -> openInputs(true), false);
        showMenu(named ? 340 : 220);
        // Opened from More inputs: start on the first port it brought back.
        if (all && firstEmpty > 0 && prefs.getBoolean("inputs_connected_only", false)) focusMenu(firstEmpty);
    }

    private static boolean connected(TvInputManager tim, TvInputInfo info) {
        return tim != null && info != null && tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
    }

    private String label(TvInputInfo i) { return String.valueOf(i.loadLabel(this)); }

    private static String fixLabel(String l, String fallback) {
        // Some vendors label ports by hardware ID; show a friendly name instead.
        return (l == null || l.isEmpty() || l.contains("HW") || l.contains("/")) ? fallback : l;
    }

    private Runnable passthrough(TvInputInfo i) {
        return () -> {
            Intent v = new Intent(Intent.ACTION_VIEW);
            v.setDataAndType(TvContract.buildChannelUriForPassthroughInput(i.getId()), "vnd.android.cursor.item/channel");
            start(v);
        };
    }

    private View addInput(String text, TvInputManager tim, TvInputInfo info, Runnable action) {
        boolean connected = connected(tim, info);
        View row = addRow(text, null, action, false);
        ImageView dot = row.findViewById(R.id.input_dot);
        dot.setVisibility(connected ? View.VISIBLE : View.INVISIBLE);
        dot.setColorFilter(Color.rgb(48, 209, 88), PorterDuff.Mode.SRC_IN);
        return row;
    }

    /**
     * "Fire TV · Standby": the device and its power state, as it reports them over HDMI-CEC (the TV keeps them as an
     * input behind the port), under the name it's known by (Devices). Null when nothing CEC-capable is plugged in.
     * Idea from Matinee (MIT).
     */
    private String cecStatus(TvInputManager tim, TvInputInfo device) {
        if (tim == null || device == null) return null;
        String raw = label(device).trim();
        if (raw.isEmpty() || raw.contains("HW") || raw.contains("/")) return null;
        String name = identify(device).name;
        int state;
        try { state = tim.getInputState(device.getId()); } catch (Exception e) { return name; }
        if (state == TvInputManager.INPUT_STATE_CONNECTED) return name + "  ·  On";
        if (state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) return name + "  ·  Standby";
        return name;
    }

    // ---------------------------------------------------------------- weather and now playing (ideas from Luma, MIT)

    /** The next hours, or the next days; OK on any row switches. */
    private void openWeather(boolean days) {
        List<String[]> rows = weather.forecast(days);
        beginMenu(P_CONTROL, rows.isEmpty() ? "Weather  ·  the forecast arrives with the next update"
                : (days ? "Next days" : "Next hours") + "  ·  OK shows the " + (days ? "hours" : "days"));
        for (String[] r : rows) addRow(r[0], r[1], () -> openWeather(!days), false);
        showMenu(340);
    }

    /** The song (or show), its artwork and app, then Play or Pause, Next, Previous and Open. Follows the player live. */
    private void openNowPlaying(MediaController s) {
        beginMenu(P_CONTROL, "Now playing  ·  " + appLabel(s.getPackageName()));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(14), px(4), px(14), px(10));
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        art.setBackground(rounded(theme.menuStroke, 0, 10));
        head.addView(art, new LinearLayout.LayoutParams(px(64), px(64)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(14), 0, 0, 0);
        TextView title = menuText(17, theme.text, 600), artist = menuText(14, theme.text2, 400);
        words.addView(title);
        words.addView(artist);
        head.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        menu.addView(head);

        MediaController.TransportControls go = s.getTransportControls();
        View play = addRow("", null, () -> { if (NowPlaying.playing(s)) go.pause(); else go.play(); }, true);
        if (NowPlaying.can(s, PlaybackState.ACTION_SKIP_TO_NEXT)) addRow("Next", null, go::skipToNext, true);
        if (NowPlaying.can(s, PlaybackState.ACTION_SKIP_TO_PREVIOUS)) addRow("Previous", null, go::skipToPrevious, true);
        addRow("Open " + appLabel(s.getPackageName()), null, () -> {
            android.app.PendingIntent player = s.getSessionActivity();
            try {
                if (player != null) { player.send(); return; }
            } catch (android.app.PendingIntent.CanceledException ignored) { }
            Intent app = pm.getLeanbackLaunchIntentForPackage(s.getPackageName());
            if (app != null) start(app);
        }, false);

        Runnable fill = () -> {
            title.setText(NowPlaying.title(s));
            String a = NowPlaying.artist(s);
            artist.setText(a);
            artist.setVisibility(a.isEmpty() ? View.GONE : View.VISIBLE);
            art.setImageBitmap(NowPlaying.art(s));
            ((TextView) play.findViewById(R.id.input_label)).setText(NowPlaying.playing(s) ? "Pause" : "Play");
        };
        fill.run();
        MediaController.Callback follow = new MediaController.Callback() {
            @Override public void onMetadataChanged(MediaMetadata m) { update(); }
            @Override public void onPlaybackStateChanged(PlaybackState p) { update(); }
            @Override public void onSessionDestroyed() { if (head.isAttachedToWindow() && zone == Z_MENU) closeMenu(); }
            private void update() {
                if (head.isAttachedToWindow()) fill.run(); else s.unregisterCallback(this);  // the menu moved on
            }
        };
        s.registerCallback(follow, new Handler(Looper.getMainLooper()));
        menuClosed = () -> { s.unregisterCallback(follow); focusTop(P_CONTROL); };
        showMenu(380);
    }

    private TextView menuText(int sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTypeface(getResources().getFont(R.font.outfit));
        t.setFontVariationSettings("'wght' " + weight);
        return t;
    }

    private String appLabel(String pkg) {
        try { return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); }
        catch (PackageManager.NameNotFoundException e) { return pkg; }
    }

    private Devices identify(TvInputInfo device) {
        int[] cec = Devices.cec(device);
        return Devices.identify(label(device), cec[0], cec[1]);
    }

    // ---------------------------------------------------------------- live scores

    private String scoresLabel() {
        switch (scores()) {
            case "cricket": return "Cricket";
            case "football": return "Football";
            case "off": return "Off";
            default: return "Cricket and football";
        }
    }

    /** One row per league, On when it's shown; OK turns it on or off. Leaving the menu fetches the new set. */
    private void openFootballLeagues(int focus) {
        beginMenu(settingsAt, football.customLeagues() ? "Football leagues"
                : "Football leagues  ·  defaults for " + Region.name(Region.country(this)));
        List<String> on = football.leagues();
        for (String[] l : Football.LEAGUES) {
            final View[] row = new View[1];
            row[0] = addRow(l[1], on.contains(l[0]) ? "On" : null, () -> {
                football.toggle(l[0]);
                setStatus(row[0], football.leagues().contains(l[0]) ? "On" : null);
                View heading = menu.getChildAt(0);
                if (heading instanceof TextView && !menuItems.contains(heading)) ((TextView) heading).setText("Football leagues");
            }, true);
        }
        if (football.customLeagues()) addRow("Back to the defaults", null, () -> {
            football.useDefaults();
            openFootballLeagues(0);
        }, true);
        menuClosed = () -> {
            background.execute(() -> {
                List<Cricket.Match> saved = football.cached();
                runOnUiThread(() -> { onFootball(saved); refreshOnline(); });
            });
            focusTop(settingsAt);
        };
        showMenu(360);
        focusMenu(focus);
    }

    // ---------------------------------------------------------------- keys and location

    /**
     * Control Center → Keys and location: your own TMDB key (picks), CricketData key (scores) and weather area, typed
     * with the remote or pasted from a phone with the Google TV app's keyboard. Keys are tried before they're saved.
     * They go in the same files the set-*.sh scripts push over adb (see Keys).
     */
    private void openKeysAndLocation(int focus) {
        beginMenu(settingsAt, "Keys and location");
        String tmdb = Keys.read(this, Keys.TMDB), cricket = Keys.read(this, Keys.CRICKET), area = Keys.read(this, Keys.LOCATION);
        String paste = "\n\nTip: Send from your phone (above) is easier, or the Google TV app's keyboard on your phone can paste it.";
        addRow("Send from your phone", "QR code", this::openPhonePage, true);
        addRow("TMDB key", tmdb == null ? "Not set" : Keys.masked(tmdb), () -> askText("TMDB key",
                "For the Trending and New picks. Free at themoviedb.org → Settings → API: the API Key or the API Read "
                        + "Access Token." + paste, null, true, tmdb != null, text -> saveKey(Keys.TMDB, "TMDB", text, 1)), true);
        addRow("Cricket key", cricket == null ? "Not set" : Keys.masked(cricket), () -> askText("CricketData key",
                "For live cricket scores. Free at cricketdata.org (100 requests a day, enough for Talkies)." + paste,
                null, true, cricket != null, text -> saveKey(Keys.CRICKET, "CricketData", text, 2)), true);
        // Coordinates pinpoint a home, so the row (and any screenshot of it) just says so; place names show as typed.
        String where = area == null ? "Not set" : area.matches("[-+0-9.,\\s]+") ? "Coordinates"
                : area.length() > 22 ? area.substring(0, 21) + "…" : area;
        addRow("Weather location", where,
                () -> askText("Weather location", "A town or area, like \"Camden, London\", or latitude,longitude, like "
                        + "\"51.54,-0.14\".", area, false, area != null, this::saveLocation), true);
        showMenu(420);
        focusMenu(focus);
    }

    /** Weather city: name, country (as in Region) and "lat,lon". The TV's own country is listed first. */
    private static final String[][] CITIES = {
            {"Mumbai", "IN", "19.0760,72.8777"}, {"Delhi", "IN", "28.6139,77.2090"}, {"Bengaluru", "IN", "12.9716,77.5946"},
            {"Hyderabad", "IN", "17.3850,78.4867"}, {"Chennai", "IN", "13.0827,80.2707"}, {"Kolkata", "IN", "22.5726,88.3639"},
            {"Pune", "IN", "18.5204,73.8567"}, {"Ahmedabad", "IN", "23.0225,72.5714"},
            {"London", "GB", "51.5074,-0.1278"}, {"Manchester", "GB", "53.4808,-2.2426"},
            {"New York", "US", "40.7128,-74.0060"}, {"Los Angeles", "US", "34.0522,-118.2437"}, {"Chicago", "US", "41.8781,-87.6298"},
            {"Toronto", "CA", "43.6532,-79.3832"}, {"Sydney", "AU", "-33.8688,151.2093"}, {"Melbourne", "AU", "-37.8136,144.9631"},
            {"Dubai", "AE", "25.2048,55.2708"}, {"Singapore", "SG", "1.3521,103.8198"},
            {"Istanbul", "TR", "41.0082,28.9784"}, {"Ankara", "TR", "39.9334,32.8597"}, {"Izmir", "TR", "38.4237,27.1428"},
            {"Paris", "FR", "48.8566,2.3522"}, {"Berlin", "DE", "52.5200,13.4050"}, {"Madrid", "ES", "40.4168,-3.7038"},
            {"Barcelona", "ES", "41.3874,2.1686"}, {"Rome", "IT", "41.9028,12.4964"}, {"Amsterdam", "NL", "52.3676,4.9041"},
            {"Lisbon", "PT", "38.7223,-9.1393"}, {"Tokyo", "JP", "35.6762,139.6503"}, {"S\u00e3o Paulo", "BR", "-23.5505,-46.6333"},
            {"Mexico City", "MX", "19.4326,-99.1332"}, {"Johannesburg", "ZA", "-26.2041,28.0473"}, {"Cairo", "EG", "30.0444,31.2357"},
    };

    /** The list city the weather area is set to, or null when it was typed or sent from a phone. */
    private String cityName() {
        String n = prefs.getString("city_name", null), a = prefs.getString("city_area", null);
        return n != null && a != null && a.equals(Keys.read(this, Keys.LOCATION)) ? n : null;
    }

    /**
     * Talkies settings → Weather city: pick a big city from the list instead of typing a place; OK sets the weather
     * area and the name shown beside the temperature. "Another place…" is the typed box of Keys and location.
     */
    private void openCityPicker(int focus) {
        beginMenu(settingsAt, "Weather city");
        String area = Keys.read(this, Keys.LOCATION), home = Region.country(this);
        final List<String[]> cities = new ArrayList<>();
        for (String[] c : CITIES) if (c[1].equals(home)) cities.add(c);
        for (String[] c : CITIES) if (!c[1].equals(home)) cities.add(c);
        final View[] rows = new View[cities.size()];
        for (int i = 0; i < cities.size(); i++) {
            final int row = i;
            final String[] c = cities.get(i);
            rows[i] = addRow(c[0], c[2].equals(area) ? "Selected" : null, () -> {
                if (!Keys.write(this, Keys.LOCATION, c[2])) {
                    Toast.makeText(this, "Couldn't save the city", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString("city_name", c[0]).putString("city_area", c[2]).apply();
                for (int j = 0; j < rows.length; j++) setStatus(rows[j], j == row ? "Selected" : null);
                weatherLine = null;   // the old temperature was for the old city
                updateInfo();
                Toast.makeText(this, c[0] + ". The weather follows in a moment", Toast.LENGTH_SHORT).show();
                refreshOnline();
            }, true);
        }
        addRow("Another place\u2026", null, () -> askText("Weather location", "A town or area, like \"Camden, London\", or latitude,longitude, like "
                + "\"51.54,-0.14\".", area, false, area != null, this::saveLocation), true);
        showMenu(380);
        focusMenu(focus);
    }

    /** A one-line text box over the home screen. Save hands back the text; Remove (or saving it empty) hands back "". */
    private void askText(String title, String help, String current, boolean code, boolean canRemove,
                         java.util.function.Consumer<String> onSave) {
        android.widget.EditText field = new android.widget.EditText(this);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (code ? android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : 0));
        if (current != null) {
            field.setText(current);
            field.setSelection(current.length());
        }
        FrameLayout box = new FrameLayout(this);
        box.setPadding(px(20), px(4), px(20), 0);
        box.addView(field);
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setMessage(help).setView(box)
                .setPositiveButton("Save", (d, w) -> onSave.accept(field.getText().toString().trim()))
                .setNegativeButton("Cancel", null);
        if (canRemove) b.setNeutralButton("Remove", (d, w) -> onSave.accept(""));
        android.app.AlertDialog dialog = b.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        field.requestFocus();
    }

    /** Tries the key with the service first: a key it turns down isn't saved; one that can't be checked offline is. */
    private void saveKey(String file, String service, String text, int row) {
        if (text.isEmpty()) {
            Keys.write(this, file, null);
            Toast.makeText(this, service + " key removed", Toast.LENGTH_SHORT).show();
            if (zone == Z_MENU) openKeysAndLocation(row);
            return;
        }
        String key = text.replaceAll("\\s", "");
        Toast.makeText(this, "Checking the key with " + service + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int result = file.equals(Keys.TMDB) ? Trending.check(key) : Cricket.check(key);
            runOnUiThread(() -> {
                if (result == Keys.REJECTED) {
                    Toast.makeText(this, service + " didn't accept that key, so it wasn't saved", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!Keys.write(this, file, key)) {
                    Toast.makeText(this, "Couldn't save the key", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, result == Keys.OK
                        ? "The key works. " + (file.equals(Keys.TMDB) ? "Picks arrive" : "Scores arrive") + " in a minute"
                        : "Couldn't reach " + service + " to check it. Saved; Talkies will try it later", Toast.LENGTH_LONG).show();
                if (zone == Z_MENU) openKeysAndLocation(row);
                refreshOnline();
            });
        }, "KeyCheck").start();
    }

    private void saveLocation(String text) {
        prefs.edit().remove("city_name").remove("city_area").apply();   // a typed place has no list name
        if (!Keys.write(this, Keys.LOCATION, text)) {
            Toast.makeText(this, "Couldn't save the location", Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.isEmpty()) {
            weatherLine = null;
            updateInfo();
        }
        Toast.makeText(this, text.isEmpty() ? "Weather location removed" : "Saved. The weather appears in a moment",
                Toast.LENGTH_SHORT).show();
        if (zone == Z_MENU) openKeysAndLocation(3);
        refreshOnline();
    }

    /**
     * Keys and location → Send from your phone: a QR code for a one-time page on the home network where the phone
     * pastes the keys and the weather area (PhonePage). Closing the dialog stops the page.
     */
    private void openPhonePage() {
        java.net.InetAddress lan = PhonePage.lanAddress(this);
        if (lan == null) {
            Toast.makeText(this, "The TV isn't on a home Wi-Fi or Ethernet network, so the phone can't reach it", Toast.LENGTH_LONG).show();
            return;
        }
        TextView status = new TextView(this);
        android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        PhonePage page;
        try {
            page = new PhonePage(lan, new PhonePage.Listener() {
                @Override public void checking() {
                    runOnUiThread(() -> status.setText("Checking with TMDB and CricketData…"));
                }

                @Override public List<String> accept(Map<String, String> fields) {
                    List<String> lines = new ArrayList<>();
                    if (fields.containsKey("tmdb")) lines.add(acceptKey(Keys.TMDB, "TMDB", fields.get("tmdb")));
                    if (fields.containsKey("cricket")) lines.add(acceptKey(Keys.CRICKET, "CricketData", fields.get("cricket")));
                    if (fields.containsKey("location")) lines.add(Keys.write(HomeActivity.this, Keys.LOCATION, fields.get("location"))
                            ? "Weather location saved." : "Couldn't save the weather location.");
                    runOnUiThread(() -> {
                        if (dialog[0] != null) dialog[0].dismiss();
                        Toast.makeText(HomeActivity.this, String.join("\n", lines), Toast.LENGTH_LONG).show();
                        if (zone == Z_MENU) openKeysAndLocation(0);
                        refreshOnline();
                    });
                    return lines;
                }

                @Override public void closed(boolean expired) {
                    if (expired) runOnUiThread(() -> status.setText("This code has expired. Close it and open Send from your phone again."));
                }
            });
        } catch (java.io.IOException e) {
            Toast.makeText(this, "Couldn't open the page for your phone", Toast.LENGTH_SHORT).show();
            return;
        }
        ImageView code = qrCode(page.url);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(px(24), px(8), px(24), 0);
        box.addView(code, new LinearLayout.LayoutParams(px(180), px(180)));
        TextView where = new TextView(this);
        where.setText(page.url);
        where.setTextSize(12);
        where.setPadding(0, px(8), 0, 0);
        box.addView(where);
        status.setText("Waiting for your phone… The page works once, for 5 minutes.");
        status.setPadding(0, px(6), 0, 0);
        box.addView(status);
        dialog[0] = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Send from your phone")
                .setMessage("Scan this with your phone's camera (on the same Wi-Fi as the TV), then paste the keys and your "
                        + "weather area there.")
                .setView(box)
                .setNegativeButton("Close", null)
                .setOnDismissListener(d -> page.close())
                .create();
        dialog[0].show();
    }

    /** A QR code for {@code text}, black on white with its quiet zone, sharp at any size. */
    private ImageView qrCode(String text) {
        boolean[][] modules = Qr.encode(text);
        int quiet = 4, n = modules.length + 2 * quiet, scale = 8;
        int[] pixels = new int[n * n];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        for (int y = 0; y < modules.length; y++)
            for (int x = 0; x < modules.length; x++) if (modules[y][x]) pixels[(y + quiet) * n + x + quiet] = 0xFF000000;
        ImageView code = new ImageView(this);
        code.setImageBitmap(Bitmap.createScaledBitmap(Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888), n * scale, n * scale, false));
        return code;
    }

    private static final String SUPPORT_URL = "https://buymeacoffee.com/rajupp";

    /** About → Support Talkies: the Buy Me a Coffee page as a QR code, since a TV has no easy way to open a link. */
    private void showSupport() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(px(24), px(8), px(24), 0);
        box.addView(qrCode(SUPPORT_URL), new LinearLayout.LayoutParams(px(180), px(180)));
        TextView where = new TextView(this);
        where.setText("buymeacoffee.com/rajupp");
        where.setTextSize(12);
        where.setPadding(0, px(8), 0, 0);
        box.addView(where);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Support Talkies")
                .setMessage("Talkies is free, with no ads or tracking, and built in my spare time. If you enjoy it, "
                        + "scan this with your phone to buy me a popcorn. Thank you!")
                .setView(box)
                .setNegativeButton("Close", null)
                .show();
    }

    /** Checks a key from the phone page with its service and saves it unless turned down; the line the phone shows. */
    private String acceptKey(String file, String service, String text) {
        String key = text.replaceAll("\\s", "");
        int result = file.equals(Keys.TMDB) ? Trending.check(key) : Cricket.check(key);
        if (result == Keys.REJECTED) return service + " didn't accept that key, so it wasn't saved.";
        if (!Keys.write(this, file, key)) return "Couldn't save the " + service + " key.";
        return result == Keys.OK ? service + " key saved, and it works." : service + " key saved. The TV couldn't reach "
                + service + " to check it, so it'll try later.";
    }

    // ---------------------------------------------------------------- remote keys

    /** One row per button with a job ("Prog Red → Open JioHotstar"), then Add a key. */
    private String remoteKeysLabel() {
        int n = RemoteKeys.all(this).size();
        return !SleepService.watchButtons(this) ? "Off" : n == 0 ? "On" : "On · " + n + " set";
    }

    private void openRemoteKeys() {
        boolean on = SleepService.watchButtons(this);
        beginMenu(settingsAt, on ? "Remote keys  ·  On: some TVs then change volume one step per press"
                : "Remote keys  ·  Off: holding Volume works on every TV");
        addRow("Remote keys", on ? "On" : "Off", () -> {
            prefs.edit().putBoolean("remote_keys_on", !on).apply();
            SleepService.applyButtonWatching();
            openRemoteKeys();
        }, false);
        for (Map.Entry<Integer, String> e : RemoteKeys.all(this).entrySet()) {
            int code = e.getKey();
            String hold = RemoteKeys.holdAction(this, code);
            addRow(RemoteKeys.keyName(code), RemoteKeys.describe(this, e.getValue())
                    + (hold == null ? "" : "  ·  hold: " + RemoteKeys.describe(this, hold)), () -> openKeyActions(code), false);
        }
        addRow("Add a key", null, this::learnKey, false);
        showMenu(540);  // wide enough for a key's name beside "Open … · hold: Open …"
    }

    /** Waits for the next remote button (through the accessibility service, so the TV doesn't act on it). */
    private void learnKey() {
        if (!SleepService.isRunning()) { needsTurnOffSetup(); return; }
        if (!SleepService.watchButtons(this)) {
            prefs.edit().putBoolean("remote_keys_on", true).apply();
            SleepService.applyButtonWatching();
            Toast.makeText(this, "Remote keys are on. On some TVs, holding Volume then changes it one step per press",
                    Toast.LENGTH_LONG).show();
        }
        beginMenu(settingsAt, "Press the button on your remote");
        addRow("Waiting for a button…", "Back cancels", () -> { }, true);
        menuClosed = () -> { SleepService.learn(null); focusTop(settingsAt); };
        showMenu(360);
        SleepService.learn(code -> {
            if (zone == Z_MENU) openKeyActions(code);
        });
    }

    /** What the button does: an app, Inputs, Talkies, nothing, or its normal job. */
    private void openKeyActions(int code) {
        menuClosed = null;
        beginMenu(settingsAt, RemoteKeys.keyName(code) + "  ·  now: " + RemoteKeys.describe(this, RemoteKeys.action(this, code)));
        addRow("Open an app", null, () -> openKeyApps(code), false);
        addRow("Inputs", null, () -> setKey(code, RemoteKeys.INPUTS), false);
        addRow("Recent apps", null, () -> setKey(code, RemoteKeys.RECENTS), false);
        addRow("Talkies", null, () -> setKey(code, RemoteKeys.HOME), false);
        addRow("Do nothing", null, () -> setKey(code, RemoteKeys.NONE), false);
        if (RemoteKeys.action(this, code) != null) {
            String hold = RemoteKeys.holdAction(this, code);
            addRow("When held", hold == null ? "same as a press" : RemoteKeys.describe(this, hold), () -> openHoldActions(code), false);
            addRow("Back to normal", null, () -> setKey(code, null), false);
        }
        showMenu(420);
    }

    /** A second job for holding the button (only for buttons with a press job: see RemoteKeys). */
    private void openHoldActions(int code) {
        String hold = RemoteKeys.holdAction(this, code);
        beginMenu(settingsAt, "Hold " + RemoteKeys.keyName(code) + "  ·  now: " + (hold == null ? "same as a press" : RemoteKeys.describe(this, hold)));
        addRow("Open an app", null, () -> openKeyApps(code, true), false);
        addRow("Inputs", null, () -> setHold(code, RemoteKeys.INPUTS), false);
        addRow("Recent apps", null, () -> setHold(code, RemoteKeys.RECENTS), false);
        addRow("Talkies", null, () -> setHold(code, RemoteKeys.HOME), false);
        if (hold != null) addRow("Same as a press", null, () -> setHold(code, null), false);
        showMenu(420);
    }

    private void openKeyApps(int code) { openKeyApps(code, false); }

    private void openKeyApps(int code, boolean hold) {
        beginMenu(settingsAt, "Open which app?");
        for (AppEntry a : apps) addRow(a.label, null, () -> {
            if (hold) setHold(code, RemoteKeys.APP + a.pkg); else setKey(code, RemoteKeys.APP + a.pkg);
        }, false);
        showMenu(360);
    }

    private void setKey(int code, String action) {
        RemoteKeys.set(this, code, action);
        SleepService.applyButtonWatching();  // the first key, or the last one gone, can change whether it's on
        Toast.makeText(this, RemoteKeys.keyName(code) + ": " + RemoteKeys.describe(this, action), Toast.LENGTH_SHORT).show();
        openRemoteKeys();
    }

    private void setHold(int code, String action) {
        RemoteKeys.setHold(this, code, action);
        Toast.makeText(this, "Hold " + RemoteKeys.keyName(code) + ": " + (action == null ? "same as a press"
                : RemoteKeys.describe(this, action)), Toast.LENGTH_SHORT).show();
        openRemoteKeys();
    }

    // ---------------------------------------------------------------- Bluetooth

    /** "Off", "Sony WH-1000XM4" (connected), "2 paired" or "None paired", for Control Center's Bluetooth row. */
    private void bluetoothStatus(java.util.function.Consumer<String> done) {
        if (!Bluetooth.allowed(this)) { done.accept(null); return; }
        if (!Bluetooth.on(this)) { done.accept("Off"); return; }
        List<android.bluetooth.BluetoothDevice> paired = Bluetooth.speakers(this);
        if (paired.isEmpty()) { done.accept("None paired"); return; }
        Bluetooth.audio(this, a2dp -> {
            for (android.bluetooth.BluetoothDevice d : paired) {
                if (Bluetooth.state(a2dp, d) == android.bluetooth.BluetoothProfile.STATE_CONNECTED) { done.accept(Bluetooth.name(d)); return; }
            }
            done.accept(paired.size() + " paired");
        });
    }

    /** Paired speakers and headphones: OK connects or disconnects one. Pairing a new one is the TV's job. */
    private void openBluetooth() {
        if (!Bluetooth.allowed(this)) {
            requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLUETOOTH);
            return;
        }
        if (Bluetooth.adapter(this) == null) {
            Toast.makeText(this, "This TV has no Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Bluetooth.on(this)) {
            // Android's own "Turn on Bluetooth?" question.
            start(new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        Bluetooth.audio(this, a2dp -> {
            List<android.bluetooth.BluetoothDevice> speakers = Bluetooth.speakers(this);
            beginMenu(P_CONTROL, speakers.isEmpty() ? "Bluetooth  ·  no speakers or headphones paired"
                    : "Bluetooth  ·  OK connects or disconnects");
            for (android.bluetooth.BluetoothDevice d : speakers) {
                final View[] row = new View[1];
                row[0] = addRow(Bluetooth.name(d), btLabel(Bluetooth.state(a2dp, d)), () -> {
                    boolean connect = Bluetooth.state(a2dp, d) != android.bluetooth.BluetoothProfile.STATE_CONNECTED;
                    if (!Bluetooth.connect(a2dp, d, connect)) {
                        // Android refused (some TVs): its own Bluetooth page can do it.
                        start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                        return;
                    }
                    setStatus(row[0], connect ? "Connecting…" : "Disconnecting…");
                    watchBluetooth(row[0], a2dp, d, 0);
                }, true);
            }
            addRow("Pair a new device", null, () -> start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)), false);
            showMenu(400);
        });
    }

    /** Updates the row as the connection settles, for up to ~10 s. */
    private void watchBluetooth(View row, android.bluetooth.BluetoothA2dp a2dp, android.bluetooth.BluetoothDevice d, int tries) {
        root.postDelayed(() -> {
            if (!row.isAttachedToWindow()) return;
            int st = Bluetooth.state(a2dp, d);
            boolean settling = st == android.bluetooth.BluetoothProfile.STATE_CONNECTING
                    || st == android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;
            if (settling && tries < 10) { watchBluetooth(row, a2dp, d, tries + 1); return; }
            if (tries < 3 && !settling) {
                // Android may not have started yet: look a little longer before showing the outcome.
                setStatus(row, btLabel(st));
                watchBluetooth(row, a2dp, d, tries + 1);
                return;
            }
            setStatus(row, btLabel(st));
        }, 1000);
    }

    private static String btLabel(int state) {
        switch (state) {
            case android.bluetooth.BluetoothProfile.STATE_CONNECTED: return "Connected";
            case android.bluetooth.BluetoothProfile.STATE_CONNECTING: return "Connecting…";
            case android.bluetooth.BluetoothProfile.STATE_DISCONNECTING: return "Disconnecting…";
            default: return "Not connected";
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_BLUETOOTH && Bluetooth.allowed(this)) openBluetooth();
        // The Wi-Fi name and Watch Next are read as Talkies starts: start again with them.
        if (req == REQ_SETUP && !permissionsMissing()) recreate();
        if (req == REQ_PICTURES && checkSelfPermission(picturesPermission()) == PackageManager.PERMISSION_GRANTED) openTvPictures();
    }

    // ---------------------------------------------------------------- finish setup

    private static final int REQ_SETUP = 8;
    /** Runtime permissions: the Wi-Fi name (Android shares it only with location) and Android TV's Watch Next list. */
    private static final String[] SETUP_PERMISSIONS = {android.Manifest.permission.ACCESS_FINE_LOCATION,
            "android.permission.READ_TV_LISTINGS"};  // not in the public Manifest constants

    private boolean permissionsMissing() {
        for (String p : SETUP_PERMISSIONS) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return true;
        return false;
    }

    private boolean notificationAccess() {
        String on = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return on != null && on.contains(getPackageName() + "/");
    }

    /** How many first-time steps are still to do: the Home button service, notification access, the permissions. */
    private int setupMissing() {
        return (SleepService.isRunning() ? 0 : 1) + (notificationAccess() ? 0 : 1) + (permissionsMissing() ? 1 : 0);
    }

    /**
     * Asked once, the first time Talkies starts without them (an APK installed without a computer): the two
     * permissions. Talkies settings → Finish setup asks again, and lists the switches only Settings can turn on.
     */
    private void askPermissionsOnce() {
        if (!permissionsMissing() || prefs.getBoolean("setup_asked", false)) return;
        prefs.edit().putBoolean("setup_asked", true).apply();
        requestPermissions(SETUP_PERMISSIONS, REQ_SETUP);
    }

    /** Talkies settings → Finish setup: one row per step still to do; OK opens the place it's done. */
    private void openFinishSetup() {
        beginMenu(settingsAt, "Finish setup");
        if (!SleepService.isRunning()) addRow("Home button", "Accessibility → Talkies", () -> {
            Toast.makeText(this, "Switch on Talkies here: it keeps the Home button on Talkies", Toast.LENGTH_LONG).show();
            Intent i = new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity");
            if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            start(i);
        }, false);
        // Android 13 and later grey out these switches for an app installed from an APK, until allowed in App info
        // (or with one adb command where the TV's App info doesn't offer it: README → Questions and fixes).
        if (Build.VERSION.SDK_INT >= 33 && (!SleepService.isRunning() || !notificationAccess()))
            addRow("Switch greyed out?", "App info → Allow restricted settings", () -> {
                Toast.makeText(this, "Look for Allow restricted settings here. Not there? The README's Questions and fixes "
                        + "has the one command that allows it", Toast.LENGTH_LONG).show();
                start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            }, false);
        if (!notificationAccess()) addRow("Notification access", "Continue, Now playing, the bell", () -> {
            Toast.makeText(this, "Switch on Talkies here", Toast.LENGTH_LONG).show();
            start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }, false);
        if (permissionsMissing()) addRow("Wi-Fi name and Watch Next", "OK asks", () -> {
            closeMenu();
            requestPermissions(SETUP_PERMISSIONS, REQ_SETUP);
        }, false);
        if (menuItems.isEmpty()) addRow("All done", null, () -> { }, true);
        showMenu(420);
    }

    // ---------------------------------------------------------------- network debugging

    /**
     * Network debugging (ADB over Wi-Fi) lets a computer on your network control the TV; it's how this launcher was
     * installed. "On · port 5555" when the TV answers on that port, "USB only", or "Off". Idea from CleanYourTV's
     * Remote Access add-on.
     */
    private void checkNetworkDebugging(java.util.function.Consumer<String> done) {
        background.execute(() -> {
            String st;
            if (Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 0) {
                st = "Off";
            } else {
                boolean listening;
                try (java.net.Socket sock = new java.net.Socket()) {
                    sock.connect(new java.net.InetSocketAddress("127.0.0.1", 5555), 400);
                    listening = true;
                } catch (Exception e) {
                    listening = false;
                }
                st = listening ? "On · port 5555" : "USB only";
            }
            final String r = st;
            runOnUiThread(() -> done.accept(r));
        });
    }

    private void openNetworkDebugging() {
        beginMenu(settingsAt, "Network debugging lets a computer on your network control this TV");
        boolean on = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
        if (on && ServiceRepair.canRepair(this)) {
            addRow("Turn off", "computers lose access", () -> {
                try {
                    Settings.Global.putInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0);
                    Toast.makeText(this, "Network debugging is off. Turn it on again in Developer options.", Toast.LENGTH_LONG).show();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Couldn't turn off debugging: " + e);
                    start(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                }
            }, false);
        }
        addRow("Developer options", null, () -> start(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)), false);
        showMenu(460);
    }

    // ---------------------------------------------------------------- hold OK: app and Continue menus

    /** What holding OK does where focus is. The release that follows only ends the hold (holdFired). */
    private void onHold() {
        if (zone == Z_MENU) { startMove(); return; }  // the Channels editor: pick the row up
        holdFired = true;
        if (zone == Z_DOCK || zone == Z_GRID) openAppMenu();
        else if (zone == Z_TOP && iconIdx == P_SETTINGS) {
            openTalkiesSettings(P_SETTINGS);
            menuOpener = () -> openTalkiesSettings(P_SETTINGS);
        }
        else if (continueCard() != null) openContinueMenu();
    }

    /** Move, Uninstall (apps you installed only), App info. */
    private void openAppMenu() {
        int ai = appIndex();
        if (ai < 0 || ai >= apps.size()) return;
        AppEntry a = apps.get(ai);
        final int z = zone, di = dockIdx, gi = gridIdx;
        View tile = z == Z_DOCK ? dockTiles.get(di) : gridTiles.get(gi);
        beginMenu(P_CONTROL, a.label);
        menuTile = tile;
        menuClosed = () -> { if (z == Z_DOCK) focusDock(di); else focusGrid(gi); };
        boolean inDock = ai < dockCount, anyOutside = apps.size() > dockCount;
        if (!inDock) addRow("Add to dock", null, () -> addToDock(a.pkg), false);
        else if (anyOutside) addRow("Remove from dock", null, () -> removeFromDock(a.pkg), false);
        // A sorted All apps would put a moved app straight back, so there only the dock's apps move.
        if (inDock || appsOrder() == ORDER_YOURS) addRow("Move", null, this::startMove, false);
        File own = AppEntry.customImage(a.pkg);
        addRow("Change image", own != null && own.isFile() ? "your picture" : null, () -> openTileImage(a.pkg), false);
        addRow("Hide", null, () -> hideApp(a.pkg), false);
        if (canUninstall(a.pkg)) {
            addRow("Uninstall", null, () -> start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))), false);
        }
        addRow("App info", null, () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.pkg))), false);
        showMenu(240);
    }

    // ---------------------------------------------------------------- dock and hidden apps

    private int indexOf(String pkg) {
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    /** From All apps into the dock's last spot; the app that was there goes to the front of All apps. */
    private void addToDock(String pkg) { addToDock(pkg, true); }

    private void addToDock(String pkg, boolean focus) {
        int ai = indexOf(pkg);
        if (ai < dockCount || dockCount == 0) return;
        AppEntry app = apps.remove(ai), last = apps.remove(dockCount - 1);
        apps.add(dockCount - 1, app);
        apps.add(dockCount, last);
        saveOrder();
        buildAppViews();
        if (focus) root.post(() -> focusDock(dockCount - 1));
        Toast.makeText(this, app.label + " is in the dock; " + last.label + " moved to All apps", Toast.LENGTH_SHORT).show();
    }

    /** Out of the dock to the front of All apps; the first app in All apps takes its spot. */
    private void removeFromDock(String pkg) { removeFromDock(pkg, true); }

    private void removeFromDock(String pkg, boolean focus) {
        int ai = indexOf(pkg);
        if (ai < 0 || ai >= dockCount || apps.size() <= dockCount) return;
        AppEntry app = apps.remove(ai), next = apps.remove(dockCount - 1);
        apps.add(ai, next);
        apps.add(dockCount, app);
        saveOrder();
        buildAppViews();
        if (focus) root.post(() -> focusDock(ai));
        Toast.makeText(this, app.label + " moved to All apps; " + next.label + " is in the dock", Toast.LENGTH_SHORT).show();
    }

    /** Apps you hid (Hide in an app's hold-OK menu): left out of the dock and All apps, still installed. */
    private Set<String> hiddenApps() {
        String s = prefs.getString("hidden_apps", "");
        return s.isEmpty() ? new HashSet<>() : new HashSet<>(Arrays.asList(s.split(",")));
    }

    private void setHidden(String pkg, boolean hide) {
        Set<String> h = hiddenApps();
        if (hide) h.add(pkg); else h.remove(pkg);
        prefs.edit().putString("hidden_apps", String.join(",", h)).apply();
    }

    /** Hides the app; in the dock, the first app in All apps takes its spot. */
    private void hideApp(String pkg) {
        int ai = indexOf(pkg);
        if (ai < 0) return;
        AppEntry app = apps.remove(ai);
        if (ai < dockCount && apps.size() >= dockCount) apps.add(ai, apps.remove(dockCount - 1));
        saveOrder();
        setHidden(pkg, true);
        reloadApps();
        Toast.makeText(this, app.label + " hidden. Talkies settings → Hidden apps brings it back", Toast.LENGTH_LONG).show();
    }

    /** Talkies settings → Hidden apps: OK on one shows it again (at the end of All apps). */
    private void openHiddenApps() {
        beginMenu(settingsAt, "Hidden apps  ·  OK shows it again");
        List<String> pkgs = new ArrayList<>(hiddenApps());
        List<String[]> rows = new ArrayList<>();
        for (String p : pkgs) {
            try {
                rows.add(new String[]{p, String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)))});
            } catch (PackageManager.NameNotFoundException e) {
                setHidden(p, false);  // uninstalled since: forget it
            }
        }
        rows.sort((x, y) -> x[1].compareToIgnoreCase(y[1]));
        for (String[] r : rows) {
            final View[] row = new View[1];
            row[0] = addRow(r[1], "Hidden", () -> {
                boolean nowHidden = !hiddenApps().contains(r[0]);
                setHidden(r[0], nowHidden);
                setStatus(row[0], nowHidden ? "Hidden" : "Shown");
                reloadApps();
            }, true);
        }
        if (rows.isEmpty()) addRow("No hidden apps", null, () -> { }, true);
        showMenu(360);
    }

    /** System apps (and Talkies) can't be uninstalled from here; Android's own dialog asks before removing one. */
    private boolean canUninstall(String pkg) {
        if (pkg.equals(getPackageName())) return false;
        try {
            int f = pm.getApplicationInfo(pkg, 0).flags;
            return (f & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** The focused Continue card (Continue tab or the Watch Next row), or null. */
    private Trending.Pick continueCard() {
        if (zone == Z_PICKS && tabIdx == 0 && !rowPicks.isEmpty()) return rowPicks.get(Math.min(picksRow.anchor(), rowPicks.size() - 1));
        if (zone == Z_CHAN && chanIdx < chanRows.size() && Channels.WATCH_NEXT.equals(chanRows.get(chanIdx).key)) {
            List<Trending.Pick> items = chanRows.get(chanIdx).items;
            if (!items.isEmpty()) return items.get(Math.min(chanCol[chanIdx], items.size() - 1));
        }
        return null;
    }

    /** Remove from Continue, or open the app itself rather than the episode. */
    private void openContinueMenu() {
        Trending.Pick p = continueCard();
        if (p == null) return;
        final int z = zone, anchor = picksRow.anchor(), r = chanIdx;
        final int c = z == Z_CHAN ? Math.min(chanCol[r], chanCards.get(r).size() - 1) : 0;
        beginMenu(P_CONTROL, p.title);
        menuTile = z == Z_PICKS ? picksRow.anchorCard() : chanCards.get(r).get(c);
        menuClosed = () -> { if (z == Z_PICKS) focusPicks(anchor); else focusChan(r, c); };
        addRow("Remove from Continue", null, () -> removeFromContinue(p), false);
        Intent app = p.pkg == null ? null : pm.getLeanbackLaunchIntentForPackage(p.pkg);
        if (app != null) addRow("Open " + Channels.label(pm, p.pkg), null, () -> start(app), false);
        showMenu(320);
    }

    /** "pkg / title / episode line": a new episode of the same show is a new key, so it comes back. */
    private static String continueKey(Trending.Pick p) { return p.pkg + "\n" + p.title + "\n" + p.meta; }

    private void removeFromContinue(Trending.Pick p) {
        if ("recent".equals(p.kind)) {
            RecentStore.remove(this, p.pkg, p.title);
        } else {
            Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_continue", java.util.Collections.emptySet()));
            hidden.add(continueKey(p));
            prefs.edit().putStringSet("hidden_continue", hidden).apply();
        }
        Log.i(TAG, "Removed from Continue: " + p.title);
        recentPicks = continuePicks(RecentStore.load(this), watchNextPicks);
        if (zone == Z_PICKS && tabIdx == 0) {
            int a = Math.max(0, Math.min(picksRow.anchor(), recentPicks.size() - 1));
            buildPicksRow(a);
            if (!rowPicks.isEmpty()) focusPicks(Math.min(a, rowPicks.size() - 1)); else focusDock(dockIdx);
        } else if (tabIdx == 0) {
            buildPicksRow(0);
        }
        refocusChannels = zone == Z_CHAN;
        refreshChannels();
    }

    // ---------------------------------------------------------------- network

    private String lastSpeed = "Test now";
    private boolean speedTesting;

    /** "TPG 5G", "Ethernet", or "Offline". The Wi-Fi name needs the location permission (granted by the install script). */
    private String networkName() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return "Offline";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Connected";
        android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
        String ssid = wm == null || wm.getConnectionInfo() == null ? null : wm.getConnectionInfo().getSSID();
        if (ssid == null || ssid.equals("<unknown ssid>")) return "Wi-Fi";
        return ssid.replaceAll("^\"|\"$", "");
    }

    /** A few seconds' download from Cloudflare; the row shows "Testing…", then "87 Mbps". Idea from Matinee (MIT). */
    private void runSpeedTest(View row) {
        if (speedTesting) return;
        speedTesting = true;
        setStatus(row, "Testing…");
        new Thread(() -> {
            String result;
            try {
                double mbps = SpeedTest.downloadMbps();
                result = (mbps >= 100 ? String.valueOf(Math.round(mbps)) : String.format(Locale.US, "%.1f", mbps)) + " Mbps";
            } catch (Exception e) {
                Log.w(TAG, "Speed test failed: " + e);
                result = "Couldn't test";
            }
            final String r = result;
            runOnUiThread(() -> {
                speedTesting = false;
                lastSpeed = r;
                if (row.isAttachedToWindow()) setStatus(row, r);
            });
        }, "speed-test").start();
    }

    // ---------------------------------------------------------------- app updates

    /** A newer Talkies this APK can install (SelfUpdate's daily check found it), else null. */
    private String talkiesUpdate() {
        return SelfUpdate.takesReleases(this) ? SelfUpdate.available(this) : null;
    }

    /** The bell shows while the Play Store has updates waiting (Updates, fed by WatchService) or a newer Talkies is out. */
    private void onUpdates() {
        boolean any = !Updates.list().isEmpty() || talkiesUpdate() != null;
        icons[P_BELL].setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any && zone == Z_TOP && iconIdx == P_BELL) focusTop(P_SETTINGS);
        if (!any && zone == Z_MENU && menuAnchor == P_BELL) closeMenu();
    }

    /** One row per Play Store notification ("3 updates available" · "YouTube, Netflix…"); OK opens it in the Play Store. */
    private void openUpdates() {
        List<Updates.Item> list = Updates.list();
        String mine = talkiesUpdate();
        if (list.isEmpty() && mine == null) return;
        beginMenu(P_BELL, "App updates");
        if (mine != null) {
            // Talkies first: OK downloads and installs it here, the same as Talkies settings → Updates.
            final View[] row = new View[1];
            row[0] = addRow("Talkies " + mine, "OK installs", () -> SelfUpdate.checkAndInstall(this, st -> runOnUiThread(() -> {
                if (row[0].isAttachedToWindow()) setStatus(row[0], st);
            })), true);
        }
        for (Updates.Item it : list) {
            String label = it.title.isEmpty() ? it.text : it.title;
            addRow(label, it.title.isEmpty() ? null : shorten(it.text, 34), () -> {
                try {
                    if (it.open != null) { it.open.send(); return; }
                } catch (Exception e) {
                    Log.w(TAG, "Couldn't open the update: " + e);
                }
                Intent store = pm.getLeanbackLaunchIntentForPackage("com.android.vending");
                if (store != null) start(store);
            }, false);
        }
        showMenu(480);
    }

    private static String shorten(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    // Ported from tv-vibehack's launcher (pr/control-center, MIT); see THIRD_PARTY_NOTICES.md.
    private static final int REQ_BLUETOOTH = 7;

    private void openControlCenter() {
        beginMenu(P_CONTROL, "Control Center");
        if (serviceStopped) addRow("Fix Home and Turn off", "switch Talkies off and on", () -> {
            Toast.makeText(this, "Talkies → off, then on again", Toast.LENGTH_LONG).show();
            start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }, false);
        addRow("Talkies settings", null, () -> openTalkiesSettings(P_CONTROL), false);
        MediaController playing = NowPlaying.current(this);
        if (playing != null) addRow("Now playing", NowPlaying.title(playing), () -> openNowPlaying(playing), false);
        if (weatherLine != null) addRow("Weather", weatherLine, () -> openWeather(false), false);
        if (Sleep.SUPPORTED) {
            final View[] timer = new View[1];
            timer[0] = addRow("Sleep timer", sleepStatus(), () -> {
                if (!Sleep.available()) { needsTurnOffSetup(); return; }
                Sleep.cycle(this);
                setStatus(timer[0], sleepStatus());
            }, true);
            addRow("Turn off now", null, () -> { if (!Sleep.now()) needsTurnOffSetup(); }, false);
        }
        final View[] mem = new View[1];
        mem[0] = addRow("Free up memory", availableMb() + " MB free", () -> freeMemory(mem[0]), true);
        addRow("Recent apps", null, this::openRecents, false);
        addRow("Storage", storageLine(), () -> { if (!openShortcut("storage")) start(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); }, false);
        final View[] anim = new View[1];
        anim[0] = addRow("Animations", animationLabel(), () -> {
            if (!cycleAnimations()) {
                Toast.makeText(this, "Needs the WRITE_SECURE_SETTINGS grant (see the README)", Toast.LENGTH_LONG).show();
                return;
            }
            setStatus(anim[0], animationLabel());
        }, true);
        addRow("Network", networkName(), () -> { if (!openShortcut("network")) start(new Intent(Settings.ACTION_WIFI_SETTINGS)); }, false);
        final View[] speed = new View[1];
        speed[0] = addRow("Speed test", lastSpeed, () -> runSpeedTest(speed[0]), true);
        final View[] bt = new View[1];
        bt[0] = addRow("Bluetooth", null, () -> { if (!openShortcut("bluetooth")) openBluetooth(); }, false);
        bluetoothStatus(st -> { if (bt[0].isAttachedToWindow()) setStatus(bt[0], st); });
        // Android tells apps a soundbar is connected (HDMI ARC/eARC), not whether it's playing: say only what's known.
        addRow("Sound output", soundbarConnected() ? "Soundbar connected" : "TV speakers", () -> {
            if (openShortcut("sound")) return;
            // Where the speakers are chosen differs by maker: Sony keeps it under Display & sound → Audio output, and
            // its plain Sound page has only System sounds. Elsewhere the Sound page (sound mode, outputs).
            Intent i = new Intent();
            if ("sony".equalsIgnoreCase(Build.MANUFACTURER)) {
                i.setClassName("com.android.tv.settings", "com.android.tv.settings.device.displaysound.DisplaySoundActivity");
                if (pm.resolveActivity(i, 0) == null) i = new Intent();
            }
            if (i.getComponent() == null) i.setClassName("com.android.tv.settings", "com.android.tv.settings.device.sound.SoundActivity");
            if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_SOUND_SETTINGS);
            start(i);
        }, false);
        Intent dream = screensaverIntent();
        if (dream != null) addRow("Screensaver", null, () -> start(dream), false);
        // The TV's own power menu, so it still asks before restarting.
        addRow("Restart…", null, () -> { if (!SleepService.powerMenu()) needsTurnOffSetup(); }, false);
        showMenu(350);
    }

    /**
     * Talkies settings: what you set once and rarely change (Control Center keeps what you reach for while watching).
     * From Control Center's last row, or by holding OK on the gear (a press opens the TV's own settings).
     */
    private void openTalkiesSettings(int anchor) {
        settingsAt = anchor;
        beginMenu(anchor, "Talkies settings");
        // First: what a new TV still needs (only while something does), then the keys and the weather area.
        int missing = setupMissing();
        if (missing > 0) addRow("Finish setup", missing + (missing == 1 ? " step left" : " steps left"), this::openFinishSetup, false);
        addRow("Keys and location", null, () -> openKeysAndLocation(0), false);
        addRow("Weather city", cityName() == null ? "Your own" : cityName(), () -> openCityPicker(0), false);
        final View[] look = new View[1];
        look[0] = addRow("Appearance", APPEARANCE[prefs.getInt("appearance", 0)], () -> {
            int mode = (prefs.getInt("appearance", 0) + 1) % APPEARANCE.length;
            prefs.edit().putInt("appearance", mode).apply();
            setStatus(look[0], APPEARANCE[mode]);
            day = isDayNow();
            applyTheme();
            // Restyle the open menu too.
            menuScroll.setBackground(rounded(theme.menuBg, theme.menuStroke, 20));
            View heading = menu.getChildAt(0);
            if (heading instanceof TextView && !menuItems.contains(heading)) ((TextView) heading).setTextColor(theme.text2);
            focusMenu(menuIdx);
        }, true);
        final View[] fy = new View[1];
        fy[0] = addRow("For You", forYou ? "Shown" : "Hidden", () -> {
            forYou = !forYou;
            prefs.edit().putBoolean("for_you", forYou).apply();
            setStatus(fy[0], forYou ? "Shown" : "Hidden");
            applyForYou();
            tabIdx = TAB_TRENDING;
            buildPicksRow(0);
            if (forYou) refreshOnline();
        }, true);
        final View[] sp = new View[1];
        sp[0] = addRow("Hide spoilers", WatchNext.hideSpoilers(this) ? "On" : "Off", () -> {
            boolean on = !WatchNext.hideSpoilers(this);
            prefs.edit().putBoolean("hide_spoilers", on).apply();
            setStatus(sp[0], on ? "On" : "Off");
            recentPicks = new ArrayList<>();  // rebuild Continue with the other pictures
            refreshWatchNext();
        }, true);
        addRow("Dock apps", dockCount + " in the dock", this::openDockApps, false);
        final View[] ds = new View[1];
        ds[0] = addRow("Dock size", dockSize() + " apps", () -> {
            int n = dockSize() + 1;
            if (n > DOCK_SIZES[DOCK_SIZES.length - 1]) n = DOCK_SIZES[0];
            prefs.edit().putInt("dock_size", n).apply();
            setStatus(ds[0], n + " apps");
            reloadApps();
        }, true);
        final View[] ao = new View[1];
        ao[0] = addRow("All apps order", APPS_ORDER[appsOrder()], () -> {
            int n = (appsOrder() + 1) % APPS_ORDER.length;
            prefs.edit().putInt("apps_order", n).apply();
            setStatus(ao[0], APPS_ORDER[n]);
            reloadApps();
        }, true);
        final View[] ms = new View[1];
        ms[0] = addRow("RAM and storage", meters.getVisibility() == View.VISIBLE ? "Shown" : "Hidden", () -> {
            boolean show = meters.getVisibility() != View.VISIBLE;
            prefs.edit().putBoolean("meters", show).apply();
            meters.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) meters.update();
            setStatus(ms[0], show ? "Shown" : "Hidden");
        }, true);
        final View[] sc = new View[1];
        sc[0] = addRow("Live scores", scoresLabel(), () -> {
            String[] order = {"both", "cricket", "football", "off"};
            String next = order[(Arrays.asList(order).indexOf(scores()) + 1) % order.length];
            prefs.edit().putString("scores", next).apply();
            setStatus(sc[0], scoresLabel());
            updateScore();
            chanSignature = "";
            showChannels(appChannels);
            refreshOnline();
        }, true);
        if (!scores().equals("off") && !scores().equals("cricket")) addRow("Football leagues", football.leagues().size() + " on",
                () -> openFootballLeagues(0), false);
        addRow("Channels", null, this::openChannelsEditor, false);
        int hiddenCount = hiddenApps().size();
        if (hiddenCount > 0) addRow("Hidden apps", String.valueOf(hiddenCount), this::openHiddenApps, false);
        addRow("Remote keys", remoteKeysLabel(),
                this::openRemoteKeys, false);
        final View[] adb = new View[1];
        adb[0] = addRow("Network debugging", "Checking…", this::openNetworkDebugging, false);
        checkNetworkDebugging(st -> { if (adb[0].isAttachedToWindow()) setStatus(adb[0], st); });
        final View[] wake = new View[1];
        wake[0] = addRow("Talkies at power-on", wakeLabel(), () -> {
            if (!SleepService.isRunning()) { needsTurnOffSetup(); return; }
            prefs.edit().putString("home_at_power_on", SleepService.homeOnWake(this) ? "boot" : "always").apply();
            setStatus(wake[0], wakeLabel());
        }, true);
        final View[] ports = new View[1];
        ports[0] = addRow("Empty inputs", prefs.getBoolean("inputs_connected_only", false) ? "Under More inputs" : "Shown", () -> {
            boolean only = !prefs.getBoolean("inputs_connected_only", false);
            prefs.edit().putBoolean("inputs_connected_only", only).apply();
            setStatus(ports[0], only ? "Under More inputs" : "Shown");
        }, true);
        // Switch between Talkies and another launcher with Android's Home app chooser.
        // Google TV keeps its own launcher as the home app whatever is picked, so there Talkies has Home through its
        // Accessibility switch: that's what to turn off to use another home screen.
        ResolveInfo other = otherHome();
        if (other != null) {
            // Another launcher you installed has Home (CleanYourTV's, say), and Talkies steps aside for it. Removing
            // it is the way back; its App info page has Uninstall and Disable.
            String name = String.valueOf(other.loadLabel(pm));
            addRow("Home app", name, () -> {
                Toast.makeText(this, "Uninstall or disable " + name + " here, then press Home: Talkies takes over",
                        Toast.LENGTH_LONG).show();
                start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + other.activityInfo.packageName)));
            }, false);
        }
        else if (isGoogleTvLauncher()) addRow("Home app", SleepService.isRunning() ? "Talkies" : currentHomeName(), () -> {
            Toast.makeText(this, "To use another home screen, turn off Talkies here. Turn it back on to come back to Talkies",
                    Toast.LENGTH_LONG).show();
            Intent i = new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity");
            if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            start(i);
        }, false);
        else addRow("Home app", currentHomeName(), () -> start(new Intent(Settings.ACTION_HOME_SETTINGS)), false);
        addRow("Wallpaper", wallpaperLabel(), this::openWallpaperMenu, false);
        addRow("Control Center shortcuts", null, this::openShortcuts, false);
        final View[] anim = new View[1];
        anim[0] = addRow("Apps open from their tile", prefs.getBoolean("open_animation", true) ? "On" : "Off", () -> {
            boolean on = !prefs.getBoolean("open_animation", true);
            prefs.edit().putBoolean("open_animation", on).apply();
            setStatus(anim[0], on ? "On" : "Off");
        }, true);
        // Off by default: looking a video up means sending its title and channel to YouTube.
        final View[] yt = new View[1];
        yt[0] = addRow("Sharp YouTube pictures", prefs.getBoolean("youtube_art", false) ? "On" : "Off", () -> {
            boolean on = !prefs.getBoolean("youtube_art", false);
            prefs.edit().putBoolean("youtube_art", on).apply();
            setStatus(yt[0], on ? "On" : "Off");
            if (on) refreshOnline();
        }, true);
        final View[] up = new View[1];
        up[0] = addRow("Updates", SelfUpdate.summary(this), () -> SelfUpdate.checkAndInstall(this, st -> runOnUiThread(() -> {
            if (up[0].isAttachedToWindow()) setStatus(up[0], st);
        })), true);
        addRow("About", null, this::openAbout, false);
        showMenu(350);
    }

    private boolean ownWallpaper() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return false;
        for (String n : new String[]{"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) if (new File(dir, n).isFile()) return true;
        return false;
    }

    private String wallpaperLabel() {
        if (ownWallpaper()) return "Your picture";
        return prefs.getBoolean("bing_wallpaper", false) ? "Picture of the day" : "Off";
    }

    // ---------------------------------------------------------------- wallpaper

    /** Talkies settings → Wallpaper: a picture of your own (from your phone or this TV), Bing's, or none. */
    private void openWallpaperMenu() {
        beginMenu(settingsAt, "Wallpaper  ·  now: " + wallpaperLabel());
        addRow("From your phone", "scan a QR code", () -> { pictureFor = null; openPhoneWallpaper(); }, false);
        addRow("From this TV", "Pictures, Downloads, USB drives", () -> { pictureFor = null; openTvPictures(); }, false);
        final View[] bing = new View[1];
        bing[0] = addRow("Picture of the day", prefs.getBoolean("bing_wallpaper", false) ? "On" : "Off", () -> {
            boolean on = !prefs.getBoolean("bing_wallpaper", false);
            prefs.edit().putBoolean("bing_wallpaper", on).apply();
            if (on) this.bing.refreshIfStale(this::loadWallpaper); else { this.bing.clear(); loadWallpaper(); }
            setStatus(bing[0], on ? (ownWallpaper() ? "On · after your picture" : "On") : "Off");
        }, true);
        if (ownWallpaper()) addRow("Remove your picture", prefs.getBoolean("bing_wallpaper", false) ? "back to Bing's" : null, () -> {
            removeOwnWallpaper();
            loadWallpaper();
            Toast.makeText(this, "Your picture is removed", Toast.LENGTH_SHORT).show();
        }, false);
        showMenu(420);
    }

    // ---------------------------------------------------------------- your own app images

    /** The app whose image the picture pickers set (hold OK → Change image), or null for the wallpaper. */
    private String pictureFor;

    /** The app's tile on screen, for a menu about it; null if it isn't showing. */
    private View tileOf(String pkg) {
        int i = indexOf(pkg);
        if (i < 0) return null;
        if (i < dockCount) return i < dockTiles.size() ? dockTiles.get(i) : null;
        return i - dockCount < gridTiles.size() ? gridTiles.get(i - dockCount) : null;
    }

    private String labelOf(String pkg) {
        AppEntry a = appFor(pkg);
        return a != null ? a.label : pkg;
    }

    /** Puts a menu about an app by its tile; closing it puts focus back on the app. */
    private void anchorToApp(String pkg) {
        menuTile = tileOf(pkg);
        menuClosed = () -> {
            int i = indexOf(pkg);
            if (i >= 0 && i < dockCount) focusDock(i);
            else if (i >= dockCount) focusGrid(i - dockCount);
            else focusDock(dockIdx);
        };
    }

    /** Hold OK on an app → Change image: a picture of your own for its tile, from your phone or this TV. */
    private void openTileImage(String pkg) {
        beginMenu(P_CONTROL, labelOf(pkg) + "  ·  image");
        anchorToApp(pkg);
        addRow("From your phone", "scan a QR code", () -> { pictureFor = pkg; openPhoneWallpaper(); }, false);
        addRow("From this TV", "Pictures, Downloads, USB drives", () -> { pictureFor = pkg; openTvPictures(); }, false);
        File own = AppEntry.customImage(pkg);
        if (own != null && own.isFile()) addRow("Original image", null, () -> {
            own.delete();
            reloadApps();
            Toast.makeText(this, labelOf(pkg) + " has its own image again", Toast.LENGTH_SHORT).show();
        }, false);
        showMenu(380);
    }

    /** After a picture was saved for {@code pkg} (null: the wallpaper): show it, and say so. */
    private void pictureSaved(String pkg, boolean ok) {
        if (pkg == null) {
            Toast.makeText(this, ok ? "Your picture is the wallpaper now" : "Couldn't use that picture", Toast.LENGTH_SHORT).show();
            if (ok) loadWallpaper();
            return;
        }
        Toast.makeText(this, ok ? "Your picture is on " + labelOf(pkg) + " now" : "Couldn't use that picture", Toast.LENGTH_SHORT).show();
        if (ok) reloadApps();
    }

    private void removeOwnWallpaper() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return;
        for (String n : new String[]{"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) new File(dir, n).delete();
    }

    /**
     * Saves a picture as files/wallpaper.jpg: cropped to fill the screen's 16:9 at 1920 × 1080 and re-encoded, so a
     * 50 MB photo becomes a small file and keeps nothing but the picture. Off the UI thread; false if it isn't a picture.
     */
    private boolean saveWallpaper(java.util.function.Supplier<java.io.InputStream> open) { return savePicture(open, null); }

    /** The same for an app's tile (files/tiles/<package>.jpg, 640 × 360) when {@code pkg} isn't null. */
    private boolean savePicture(java.util.function.Supplier<java.io.InputStream> open, String pkg) {
        final int w = pkg == null ? 1920 : 640, h = pkg == null ? 1080 : 360;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (java.io.InputStream in = open.get()) { BitmapFactory.decodeStream(in, null, o); }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap src;
            try (java.io.InputStream in = open.get()) { src = BitmapFactory.decodeStream(in, null, o); }
            if (src == null) return false;
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            float k = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            float sw = src.getWidth() * k, sh = src.getHeight() * k;
            new android.graphics.Canvas(out).drawBitmap(src, null,
                    new RectF((w - sw) / 2, (h - sh) / 2, (w + sw) / 2, (h + sh) / 2), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            src.recycle();
            File dir = pkg == null ? getExternalFilesDir(null) : AppEntry.customDir;
            if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
            File tmp = new File(dir, pkg == null ? "wallpaper.tmp" : pkg + ".tmp");
            try (java.io.FileOutputStream f = new java.io.FileOutputStream(tmp)) {
                out.compress(Bitmap.CompressFormat.JPEG, 90, f);
            }
            out.recycle();
            if (pkg != null) return tmp.renameTo(AppEntry.customImage(pkg));
            removeOwnWallpaper();
            return tmp.renameTo(new File(dir, "wallpaper.jpg"));
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "Couldn't save the wallpaper: " + e);
            return false;
        }
    }

    /** Wallpaper → From your phone: the phone page's photo picker, behind a QR code. */
    private void openPhoneWallpaper() {
        java.net.InetAddress lan = PhonePage.lanAddress(this);
        if (lan == null) {
            Toast.makeText(this, "The TV isn't on a home Wi-Fi or Ethernet network, so the phone can't reach it", Toast.LENGTH_LONG).show();
            return;
        }
        final String target = pictureFor;
        TextView status = new TextView(this);
        android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        PhonePage page;
        try {
            page = new PhonePage(lan, true, target == null ? "the TV screen" : labelOf(target) + "'s tile", new PhonePage.Listener() {
                @Override public void checking() {
                    runOnUiThread(() -> status.setText("Saving your picture…"));
                }

                @Override public List<String> accept(Map<String, String> fields) { return new ArrayList<>(); }

                @Override public List<String> acceptWallpaper(byte[] jpeg) {
                    boolean ok = savePicture(() -> new java.io.ByteArrayInputStream(jpeg), target);
                    runOnUiThread(() -> {
                        if (dialog[0] != null) dialog[0].dismiss();
                        pictureSaved(target, ok);
                    });
                    return java.util.Collections.singletonList(ok ? "Done: it's on your TV now." : "The TV couldn't use that picture. Try another one.");
                }

                @Override public void closed(boolean expired) {
                    if (expired) runOnUiThread(() -> status.setText("This code has expired. Close it and open From your phone again."));
                }
            });
        } catch (java.io.IOException e) {
            Toast.makeText(this, "Couldn't open the page for your phone", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(px(24), px(8), px(24), 0);
        box.addView(qrCode(page.url), new LinearLayout.LayoutParams(px(180), px(180)));
        TextView where = new TextView(this);
        where.setText(page.url);
        where.setTextSize(12);
        where.setPadding(0, px(8), 0, 0);
        box.addView(where);
        status.setText("Waiting for your phone… The page works once, for 5 minutes.");
        status.setPadding(0, px(6), 0, 0);
        box.addView(status);
        dialog[0] = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(target == null ? "Wallpaper from your phone" : "A picture for " + labelOf(target))
                .setMessage("Scan this with your phone's camera (on the same Wi-Fi as the TV), then choose a photo.")
                .setView(box)
                .setNegativeButton("Close", null)
                .setOnDismissListener(d -> page.close())
                .create();
        dialog[0].show();
    }

    private static final int REQ_PICTURES = 9;
    private static final int MAX_PICTURES = 60;

    /** Reading pictures: Android 13 and later ask for photos only, earlier versions for all of storage. */
    private String picturesPermission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : android.Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    /**
     * Wallpaper → From this TV: the newest pictures Android knows of, on the TV and any USB drive, each with a small
     * preview; OK makes one the wallpaper. Asks for the permission first.
     */
    private void openTvPictures() {
        if (checkSelfPermission(picturesPermission()) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{picturesPermission()}, REQ_PICTURES);
            return;
        }
        background.execute(() -> {
            List<String[]> found = new ArrayList<>();  // uri, name, folder
            java.util.Set<String> volumes = Build.VERSION.SDK_INT >= 29
                    ? android.provider.MediaStore.getExternalVolumeNames(this) : java.util.Collections.singleton("external");
            List<long[]> order = new ArrayList<>();
            for (String vol : volumes) {
                Uri base = android.provider.MediaStore.Images.Media.getContentUri(vol);
                String[] cols = {android.provider.MediaStore.Images.Media._ID, android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                        android.provider.MediaStore.Images.Media.BUCKET_DISPLAY_NAME, android.provider.MediaStore.Images.Media.DATE_MODIFIED};
                try (android.database.Cursor c = getContentResolver().query(base, cols, null, null,
                        android.provider.MediaStore.Images.Media.DATE_MODIFIED + " DESC")) {
                    while (c != null && c.moveToNext() && found.size() < MAX_PICTURES * 4) {
                        found.add(new String[]{android.content.ContentUris.withAppendedId(base, c.getLong(0)).toString(),
                                c.getString(1), c.getString(2) == null ? "" : c.getString(2)});
                        order.add(new long[]{c.getLong(3), found.size() - 1});
                    }
                } catch (RuntimeException e) {
                    Log.w(TAG, "Couldn't list pictures on " + vol + ": " + e);
                }
            }
            order.sort((a, b) -> Long.compare(b[0], a[0]));  // newest first across the TV and USB drives
            List<String[]> pics = new ArrayList<>();
            for (long[] o : order) if (pics.size() < MAX_PICTURES) pics.add(found.get((int) o[1]));
            runOnUiThread(() -> showTvPictures(pics));
        });
    }

    private void showTvPictures(List<String[]> pics) {
        final String target = pictureFor;
        beginMenu(settingsAt, pics.isEmpty() ? "No pictures on this TV"
                : (target == null ? "Wallpaper" : labelOf(target)) + "  ·  OK on a picture to use it");
        if (target != null) anchorToApp(target);
        if (pics.isEmpty()) {
            addRow("Copy pictures to Pictures or Downloads, or plug in a USB drive", null, () -> { }, true);
            showMenu(460);
            return;
        }
        for (String[] pic : pics) {
            Uri uri = Uri.parse(pic[0]);
            LinearLayout row = (LinearLayout) addRow(pic[1], pic[2], () -> {
                if (target == null) Toast.makeText(this, "Setting the wallpaper…", Toast.LENGTH_SHORT).show();
                background.execute(() -> {
                    boolean ok = savePicture(() -> {
                        try { return getContentResolver().openInputStream(uri); }
                        catch (java.io.FileNotFoundException e) { throw new RuntimeException(e); }
                    }, target);
                    runOnUiThread(() -> pictureSaved(target, ok));
                });
            }, false);
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackground(rounded(theme.tileBg, 0, 4));
            thumb.setClipToOutline(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(64), px(36));
            lp.setMarginEnd(px(12));
            lp.gravity = Gravity.CENTER_VERTICAL;
            row.addView(thumb, 0, lp);
            background.execute(() -> {
                Bitmap t = thumbnail(uri);
                if (t != null) runOnUiThread(() -> thumb.setImageBitmap(t));
            });
        }
        showMenu(520);
    }

    private Bitmap thumbnail(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= 29) return getContentResolver().loadThumbnail(uri, new android.util.Size(px(128), px(72)), null);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= px(128)) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, o); }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** After a restart only (the default), or every time the TV wakes from standby too. */
    private String wakeLabel() { return SleepService.homeOnWake(this) ? "Every time" : "After a restart"; }

    /** Credits for the data Talkies shows; TMDB's wording is what its terms ask for. */
    private void openAbout() {
        String version = "";
        try { version = " " + pm.getPackageInfo(getPackageName(), 0).versionName; } catch (PackageManager.NameNotFoundException ignored) { }
        beginMenu(settingsAt, "Talkies" + version + "  ·  MIT License");
        String[][] credits = {
                {"Weather", "Open-Meteo.com (CC BY 4.0)"},
                {"Airport temperatures", "aviationweather.gov (NOAA)"},
                {"Cricket scores", "CricketData.org"},
                {"Football scores", "ESPN (public scoreboard)"},
                {"Speed test", "Cloudflare"},
                {"Code from", "tv-vibehack, CleanYourTV, Free My TV, Matinee, Luma, GlassLauncher"},
                {"Font", "Outfit (SIL OFL)"},
        };
        addRow("Made by", "Raju  ·  TechPP (techpp.com)", () -> { }, true);
        addRow("Source code", "github.com/rajupp/talkies", () -> { }, true);
        addRow("Support Talkies", "buymeacoffee.com/rajupp", this::showSupport, true);
        // TMDB's terms ask for its logo with the credit: smaller than anything identifying Talkies.
        LinearLayout picks = (LinearLayout) addRow("Picks", null, () -> { }, true);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tmdb_logo);
        logo.setContentDescription("TMDB");
        picks.addView(logo, picks.getChildCount() - 1, new LinearLayout.LayoutParams(px(123), px(16)));
        // TMDB's notice, in full: a row of its own that wraps.
        View notice = addRow("This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise "
                + "approved by TMDB.", null, () -> { }, true);
        TextView nt = notice.findViewById(R.id.input_label);
        nt.setSingleLine(false);
        nt.setMaxLines(3);
        nt.setTextSize(14);
        notice.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        notice.setPadding(notice.getPaddingLeft(), px(8), notice.getPaddingRight(), px(8));
        for (String[] c : credits) addRow(c[0], c[1], () -> { }, true);
        String caption = bing.caption();
        if (prefs.getBoolean("bing_wallpaper", false) && caption != null) addRow("Wallpaper", "Bing: " + caption, () -> { }, true);
        showMenu(720);
    }

    // ---------------------------------------------------------------- recent apps

    /**
     * Control Center → Recent apps (or a remote button set to it): the apps you opened last, newest first, each with
     * its banner. OK opens one. At the end, close them all (apps in the background only, never system apps).
     */
    private void openRecents() {
        if (menuScroll.getVisibility() == View.VISIBLE) closeMenu();
        List<String> recent = RecentApps.list(this);
        beginMenu(P_CONTROL, recent.isEmpty() ? "Recent apps" : "Recent apps  ·  newest first");
        int shown = 0;
        for (String pkg : recent) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;  // uninstalled since
            final Intent open = launch;
            AppEntry a = appFor(pkg);
            String label = a != null ? a.label : Channels.label(pm, pkg);
            LinearLayout row = (LinearLayout) addRow(label, shown == 0 ? "last used" : null, () -> startFromTile(open), false);
            ImageView thumb = new ImageView(this);
            Drawable d = a != null ? a.banner(pm) : null;
            if (d == null) {
                try { d = pm.getApplicationBanner(pkg); } catch (PackageManager.NameNotFoundException ignored) { }
            }
            if (d != null) {
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setImageDrawable(d);
            } else {
                thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                try { thumb.setImageDrawable(pm.getApplicationIcon(pkg)); } catch (PackageManager.NameNotFoundException ignored) { }
            }
            thumb.setBackground(rounded(theme.tileBg, 0, 4));
            thumb.setClipToOutline(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(64), px(36));
            lp.setMarginEnd(px(12));
            lp.gravity = Gravity.CENTER_VERTICAL;
            row.addView(thumb, 0, lp);
            shown++;
        }
        if (shown == 0) {
            addRow("Apps you open show up here", null, () -> { }, true);
        } else {
            final View[] close = new View[1];
            close[0] = addRow("Close them all", "frees memory", () -> {
                ActivityManager am = getSystemService(ActivityManager.class);
                long before = availableMb();
                for (String pkg : RecentApps.list(this)) {
                    try {
                        if ((pm.getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0) am.killBackgroundProcesses(pkg);
                    } catch (PackageManager.NameNotFoundException ignored) { }
                }
                close[0].postDelayed(() -> setStatus(close[0], "Freed " + Math.max(0, availableMb() - before) + " MB"), 800);
            }, true);
        }
        showMenu(460);
    }

    // ---------------------------------------------------------------- control center shortcuts

    /** Control Center rows whose screen you can choose (Talkies settings → Control Center shortcuts): key → row name. */
    private static final String[][] SHORTCUTS = {{"network", "Network"}, {"bluetooth", "Bluetooth"},
            {"sound", "Sound output"}, {"storage", "Storage"}};
    /** Words that make a settings screen a likely match for each row, shown first in the list. */
    private static final String[][] SHORTCUT_WORDS = {{"wifi", "network", "internet", "ethernet"},
            {"bluetooth", "accessor", "remote"}, {"sound", "audio", "speaker", "display"}, {"storage", "memory"}};

    /** The screen you chose for a row, if any: true when it opened. A choice that no longer opens is forgotten. */
    private boolean openShortcut(String key) {
        String c = prefs.getString("shortcut_" + key, null);
        if (c == null) return false;
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(c);
        try {
            if (cn == null) throw new ActivityNotFoundException();
            startActivity(new Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            prefs.edit().remove("shortcut_" + key).apply();
            Toast.makeText(this, "The screen you chose isn't there any more, so the TV's own opens", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /**
     * Talkies settings → Control Center shortcuts: for Network, Bluetooth, Sound output and Storage, which screen of the
     * TV's settings OK opens (some TVs keep the useful one elsewhere, like Sony's speaker choice). An idea from tvOS
     * Launcher's activity picker.
     */
    private void openShortcuts() {
        beginMenu(settingsAt, "Control Center shortcuts  ·  what each row opens");
        for (int i = 0; i < SHORTCUTS.length; i++) {
            final int k = i;
            addRow(SHORTCUTS[i][1], shortcutLabel(SHORTCUTS[i][0]), () -> openShortcutPicker(k, false), false);
        }
        showMenu(460);
    }

    private String shortcutLabel(String key) {
        String c = prefs.getString("shortcut_" + key, null);
        android.content.ComponentName cn = c == null ? null : android.content.ComponentName.unflattenFromString(c);
        if (cn == null) return "TV default";
        try {
            return screenName(pm.getActivityInfo(cn, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return "TV default";
        }
    }

    /** "Display & sound" from the screen's label, or from its class name when the label is just the app's name. */
    private String screenName(android.content.pm.ActivityInfo ai) {
        String label = String.valueOf(ai.loadLabel(pm)).trim();
        String app = String.valueOf(ai.applicationInfo.loadLabel(pm)).trim();
        if (!label.isEmpty() && !label.equals(app)) return label;
        String cls = ai.name.substring(ai.name.lastIndexOf('.') + 1).replace("Activity", "");
        return cls.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /**
     * The screens the TV's settings apps let others open, likely matches for the row first; with {@code all}, every
     * one. OK on one makes it the row's; TV default puts the row back.
     */
    private void openShortcutPicker(int k, boolean all) {
        String key = SHORTCUTS[k][0];
        background.execute(() -> {
            List<android.content.pm.ActivityInfo> found = new ArrayList<>();
            for (android.content.pm.PackageInfo p : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
                if (p.activities == null || p.applicationInfo == null || (p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) continue;
                if (!p.packageName.toLowerCase(Locale.ROOT).contains("settings")) continue;
                for (android.content.pm.ActivityInfo ai : p.activities) {
                    if (ai.exported && ai.enabled && ai.permission == null) found.add(ai);
                }
            }
            List<android.content.pm.ActivityInfo> likely = new ArrayList<>(), rest = new ArrayList<>();
            for (android.content.pm.ActivityInfo ai : found) {
                String n = ai.name.toLowerCase(Locale.ROOT);
                boolean match = false;
                for (String w : SHORTCUT_WORDS[k]) if (n.contains(w)) { match = true; break; }
                (match ? likely : rest).add(ai);
            }
            List<String[]> rows = new ArrayList<>();  // name, component
            for (android.content.pm.ActivityInfo ai : likely) rows.add(new String[]{screenName(ai), ai.packageName + "/" + ai.name});
            if (all) for (android.content.pm.ActivityInfo ai : rest) rows.add(new String[]{screenName(ai), ai.packageName + "/" + ai.name});
            final int more = all ? 0 : rest.size();
            runOnUiThread(() -> {
                beginMenu(settingsAt, SHORTCUTS[k][1] + " opens…");
                addRow("TV default", null, () -> {
                    prefs.edit().remove("shortcut_" + key).apply();
                    openShortcuts();
                }, false);
                for (String[] r : rows) addRow(r[0], r[1].substring(0, r[1].indexOf('/')), () -> {
                    prefs.edit().putString("shortcut_" + key, r[1]).apply();
                    Toast.makeText(this, SHORTCUTS[k][1] + " opens " + r[0] + ". Try it from Control Center", Toast.LENGTH_LONG).show();
                    openShortcuts();
                }, false);
                if (more > 0) addRow("Show every screen", String.valueOf(more), () -> openShortcutPicker(k, true), false);
                showMenu(520);
            });
        });
    }

    // ---------------------------------------------------------------- dock apps

    /**
     * Talkies settings → Dock apps: every app, the dock's first; OK puts one in the dock or takes it out (the same as
     * Add to dock and Remove from dock in an app's hold-OK menu). The rows stay where they are while you change them.
     */
    private void openDockApps() {
        beginMenu(settingsAt, "Dock apps  ·  OK: in or out of the dock");
        final List<String> pkgs = new ArrayList<>();
        for (AppEntry a : apps) pkgs.add(a.pkg);
        for (AppEntry a : new ArrayList<>(apps)) {
            addRow(a.label, null, () -> {
                int at = menuIdx;
                if (indexOf(a.pkg) < dockCount) removeFromDock(a.pkg, false); else addToDock(a.pkg, false);
                dockAppStatuses(pkgs);
                focusMenu(at);  // the dock was rebuilt: put the ring back on this row
            }, true);
        }
        dockAppStatuses(pkgs);
        showMenu(440);
    }

    private void dockAppStatuses(List<String> pkgs) {
        for (int i = 0; i < pkgs.size() && i < menuItems.size(); i++) {
            int ai = indexOf(pkgs.get(i));
            setStatus(menuItems.get(i), ai >= 0 && ai < dockCount ? "In dock · " + (ai + 1) : null);
        }
    }

    // ---------------------------------------------------------------- channels editor

    /** Every channel your apps publish: OK shows or hides it; hold OK, then Up/Down, to move it. */
    private void openChannelsEditor() {
        editList = Channels.list(this, watchNextPicks.size());
        channelsEdited = false;
        fillChannelsMenu();
        showMenu(440);
    }

    private void fillChannelsMenu() {
        beginMenu(settingsAt, "Channels  ·  OK: show or hide  ·  hold OK: move");
        menuMode = MENU_CHANNELS;
        for (Channels.Channel ch : editList) {
            final Channels.Channel c = ch;
            addRow(ch.title, null, () -> {
                c.shown = !c.shown;
                channelsEdited = true;
                Channels.save(this, editList);
                channelStatuses();
            }, true);
        }
        channelStatuses();
    }

    private void channelStatuses() {
        boolean first = true;
        for (int i = 0; i < editList.size() && i < menuItems.size(); i++) {
            Channels.Channel ch = editList.get(i);
            String status = ch.shown ? (first ? "Shown · first" : "Shown") : "Hidden";
            if (ch.shown) first = false;
            if (menuMoving && i == menuIdx) status = "Moving…";
            setStatus(menuItems.get(i), status);
        }
    }

    private void startMenuMove() {
        if (menuMode != MENU_CHANNELS || menuIdx >= editList.size()) return;
        holdFired = true;
        menuMoving = true;
        beforeEdit = new ArrayList<>(editList);
        channelStatuses();
        moveHint.setText("Moving " + editList.get(menuIdx).title + "  ·  OK to place  ·  Back to cancel");
        moveHint.setAlpha(0f);
        moveHint.setVisibility(View.VISIBLE);
        moveHint.animate().alpha(1f).setDuration(150).start();
        showServiceNote();
    }

    /** Swaps the picked-up channel with the one above or below. */
    private void moveMenuRow(int k) {
        int to = k == KeyEvent.KEYCODE_DPAD_UP ? menuIdx - 1 : k == KeyEvent.KEYCODE_DPAD_DOWN ? menuIdx + 1 : -1;
        if (to < 0 || to >= editList.size()) return;
        View a = menuItems.get(menuIdx), b = menuItems.get(to);
        int ia = menu.indexOfChild(a), ib = menu.indexOfChild(b);
        menu.removeView(a);
        menu.addView(a, ib);
        if (ia != ib) {
            menu.removeView(b);
            menu.addView(b, ia);
        }
        Collections.swap(menuItems, menuIdx, to);
        Collections.swap(menuActions, menuIdx, to);
        Collections.swap(menuKeepsOpen, menuIdx, to);
        Collections.swap(editList, menuIdx, to);
        focusMenu(to);
        channelStatuses();
    }

    private void endMenuMove(boolean keep) {
        if (!menuMoving) return;
        menuMoving = false;
        if (keep) {
            channelsEdited = true;
            Channels.save(this, editList);
            channelStatuses();
        } else {
            editList = beforeEdit;
            int at = menuIdx;
            fillChannelsMenu();
            focusMenu(Math.min(at, menuItems.size() - 1));
        }
        moveHint.animate().alpha(0f).setDuration(120).withEndAction(() -> { moveHint.setVisibility(View.GONE); showServiceNote(); }).start();
    }

    /** Google TV's own launcher is installed (visible through the HOME query in the manifest). */
    private boolean isGoogleTvLauncher() {
        try {
            pm.getApplicationInfo("com.google.android.apps.tv.launcherx", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** The launcher that has Home when it's one you installed (not Talkies, not the TV's own), else null. */
    private ResolveInfo otherHome() {
        ResolveInfo ri = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return null;
        String p = ri.activityInfo.packageName;
        boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        return p.equals(getPackageName()) || p.equals("android") || system ? null : ri;
    }

    private String currentHomeName() {
        ResolveInfo ri = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        return ri == null ? null : String.valueOf(ri.loadLabel(pm));
    }

    private String sleepStatus() {
        int left = Sleep.minutesLeft(this);
        return left > 0 ? left + " min left" : "Off";
    }

    /** Turning off needs the accessibility helper, which only you can switch on. Take you there. */
    private void needsTurnOffSetup() {
        Toast.makeText(this, "To turn the TV off, switch on \"" + getString(R.string.sleep_service_label)
                + "\" in Accessibility", Toast.LENGTH_LONG).show();
        closeMenu();
        Intent i = new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity");
        if (pm.resolveActivity(i, 0) == null) i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        start(i);
    }

    private long availableMb() {
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    /** "12.4 GB free of 32 GB": the TV's own storage, where apps and their data live. */
    private String storageLine() {
        try {
            android.os.StatFs fs = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
            return String.format(java.util.Locale.getDefault(), "%.1f GB free of %d GB",
                    fs.getAvailableBytes() / 1e9, Math.round(fs.getTotalBytes() / 1e9));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Control Center → Animations: the speed of every animation on the TV, 1× → 0.5× → Off. Faster makes menus and
     * app switches feel snappier; Off also stills Talkies' own motion. Needs the WRITE_SECURE_SETTINGS grant (the same
     * one that repairs a stopped service). An idea from GlassLauncher (MIT).
     */
    private static final String[] ANIM_KEYS = {Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.ANIMATOR_DURATION_SCALE};

    private float animationScale() {
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    }

    private String animationLabel() {
        float s = animationScale();
        return s == 0f ? "Off" : s == 0.5f ? "0.5×" : s == 1f ? "1×" : s + "×";
    }

    private boolean cycleAnimations() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false;
        float s = animationScale(), next = s >= 1f ? 0.5f : s > 0f ? 0f : 1f;
        try {
            for (String k : ANIM_KEYS) Settings.Global.putFloat(getContentResolver(), k, next);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** A soundbar or receiver on HDMI ARC/eARC takes over the TV's sound while connected. */
    private boolean soundbarConnected() {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return false;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (d.getType() == AudioDeviceInfo.TYPE_HDMI_ARC
                    || (Build.VERSION.SDK_INT >= 31 && d.getType() == AudioDeviceInfo.TYPE_HDMI_EARC)) return true;
        }
        return false;
    }

    /** Stops cached background apps (never system apps) and shows how much memory came back. */
    private void freeMemory(View row) {
        ActivityManager am = getSystemService(ActivityManager.class);
        long before = availableMb();
        setStatus(row, "Freeing…");
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            am.killBackgroundProcesses(ai.packageName);
        }
        row.postDelayed(() -> {
            long after = availableMb();
            setStatus(row, "Freed " + Math.max(0, after - before) + " MB · " + after + " MB free");
        }, 800);
    }

    // ---------------------------------------------------------------- network, wallpaper

    private void updateNetwork(NetworkCapabilities caps) {
        boolean online = caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        icons[P_WIFI].setAlpha(online ? 1f : 0.45f);
        icons[P_WIFI].setContentDescription(online ? "Wi-Fi" : "Wi-Fi: offline");
        // Back online after being offline: whatever failed meanwhile is fetched now, not at its next retry.
        if (online && Boolean.FALSE.equals(wasOnline)) {
            weather.networkBack();
            trending.networkBack();
            cricket.networkBack();
            football.networkBack();
            bing.networkBack();
            refreshOnline();
        }
        wasOnline = online;
    }

    /** Your own wallpaper (files/wallpaper.jpg|png|webp) replaces the soft backdrop. */
    private void loadWallpaper() {
        File dir = getExternalFilesDir(null);
        File f = null;
        if (dir != null) {
            for (String name : new String[]{"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) {
                File c = new File(dir, name);
                if (c.isFile()) { f = c; break; }
            }
            // No picture of your own: Bing's, when that's on.
            File b = new File(dir, Bing.FILE);
            if (f == null && prefs.getBoolean("bing_wallpaper", false) && b.isFile()) f = b;
        }
        long stamp = f == null ? 0 : f.lastModified() ^ f.length();
        if (stamp == wallpaperStamp) return;
        wallpaperStamp = stamp;
        hasWallpaper = f != null;
        if (f == null) {
            wallFit = null;
            backdrop.setWallpaper(null);
            day = isDayNow();
            applyTheme();
            updateAmbient();
            return;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= 960) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), o);
        if (bmp != null) {
            wallFit = Legibility.of(bmp, Theme.of(false).scrimTop & 0x00FFFFFF, Theme.of(true).scrimTop & 0x00FFFFFF);
            backdrop.setWallpaper(bmp);
            // A new photo may suit the other look (Auto), and always needs its own veil.
            day = isDayNow();
            applyTheme();
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private void register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, RECEIVER_EXPORTED);
        else registerReceiver(r, f);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ServiceRepair.restore(this);  // a force stop switched the accessibility service off: put it back
        IntentFilter t = new IntentFilter(Intent.ACTION_TIME_TICK);
        t.addAction(Intent.ACTION_TIME_CHANGED);
        t.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, t);
        IntentFilter p = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        p.addAction(Intent.ACTION_PACKAGE_REMOVED);
        p.addAction(Intent.ACTION_PACKAGE_CHANGED);
        p.addDataScheme("package");
        register(packageReceiver, p);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { runOnUiThread(() -> updateNetwork(c)); }
            @Override public void onLost(Network n) { runOnUiThread(() -> updateNetwork(null)); }
        };
        cm.registerDefaultNetworkCallback(netCallback);
        Network active = cm.getActiveNetwork();
        updateNetwork(active == null ? null : cm.getNetworkCapabilities(active));

        askPermissionsOnce();
        loadWallpaper();
        boolean nowDay = isDayNow();
        if (nowDay != day) { day = nowDay; applyTheme(); }
        weatherLine = weather.cached();
        updateInfo();
        refreshOnline();
        background.execute(() -> {
            List<Cricket.Match> saved = cricket.cached(), savedFootball = football.cached();
            runOnUiThread(() -> {
                if (matches.isEmpty() && !saved.isEmpty()) onMatches(saved);
                if (footballMatches.isEmpty() && !savedFootball.isEmpty()) onFootball(savedFootball);
                refreshOnline();
            });
        });
        // Right after the TV starts, its storage can be late: the saved picks may only now be readable.
        if (trendPicks.isEmpty() || indiaPicks.isEmpty()) {
            trendPicks = trending.cached(Trending.TRENDING);
            indiaPicks = trending.cached(Trending.INDIA);
            backToTrending();
        }
        // Something new may have been watched while we were away.
        List<Trending.Pick> before = recentPicks;
        recentPicks = continuePicks(RecentStore.load(this), watchNextPicks);
        if (!sameKeys(before, recentPicks) && (tabIdx == 0 || rowPicks.isEmpty())) buildPicksRow(0);
        refreshWatchNext();
        getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, watchNextChanged);
    }

    /** Apps update Watch Next a few seconds after you leave them (Netflix does): reread it then, once things settle. */
    private final android.database.ContentObserver watchNextChanged = new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) {
            root.removeCallbacks(rereadWatchNext);
            root.postDelayed(rereadWatchNext, 1500);
        }
    };
    private final Runnable rereadWatchNext = this::refreshWatchNext;

    @Override
    protected void onStop() {
        super.onStop();
        getContentResolver().unregisterContentObserver(watchNextChanged);
        root.removeCallbacks(rereadWatchNext);
        unregisterReceiver(timeReceiver);
        unregisterReceiver(packageReceiver);
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tabs[2].setText("New in " + Region.name(Region.country(this)));  // the country can change (Region)
        inFront = true;
        root.post(this::snapRing);
        // Give Android time to bind the service after Talkies (re)starts before calling it stopped.
        root.removeCallbacks(checkService);
        root.postDelayed(checkService, 6000);
        refreshOnline();   // back on the home screen: catch up on anything that went stale meanwhile
        if (appsOrder() == ORDER_RECENT && !moving && !recentSig.equals(String.join(",", RecentApps.all(this)))) {
            String focused = zone == Z_GRID && dockCount + gridIdx < apps.size() ? apps.get(dockCount + gridIdx).pkg : null;
            reloadApps();
            int at = focused == null ? -1 : indexOf(focused);
            if (at >= dockCount) focusGrid(at - dockCount);
        }
    }

    /** Is the accessibility service down when it should be up? Switch it off and on if we may; else say so. */
    private final Runnable checkService = () -> {
        boolean stopped = ServiceRepair.stopped(this, SleepService.class);
        if (stopped && !rebindTried && ServiceRepair.canRepair(this)) {
            rebindTried = true;
            ServiceRepair.rebind(this, SleepService.class);
            root.postDelayed(this.checkService, 5000);
            return;
        }
        if (!stopped) rebindTried = false;
        serviceStopped = stopped;
        showServiceNote();
    };

    private void showServiceNote() {
        boolean show = serviceStopped && moveHint.getVisibility() != View.VISIBLE;
        if (serviceNote == null) {
            if (!show) return;
            serviceNote = new TextView(this);
            serviceNote.setText("Home, Turn off and Remote keys stopped working  ·  Control Center → Fix");
            serviceNote.setTextSize(13);
            serviceNote.setTypeface(getResources().getFont(R.font.outfit));
            serviceNote.setGravity(android.view.Gravity.CENTER);
            serviceNote.setPadding(px(16), 0, px(16), 0);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(28),
                    android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
            lp.topMargin = px(6);
            ViewGroup parent = (ViewGroup) moveHint.getParent();
            parent.addView(serviceNote, parent.indexOfChild(moveHint), lp);
        }
        serviceNote.setTextColor(theme.text);
        serviceNote.setBackground(rounded(theme.menuBg, theme.menuStroke, 14));
        serviceNote.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** The OK or Back button that went down while Talkies was in front (0: none), so only its release counts. */
    private int pressedHere;

    @Override
    protected void onPause() {
        super.onPause();
        pressedHere = 0;
        SleepService.learn(null);
        inFront = false;
    }

    /**
     * Network refreshes (cricket, weather, picks) run only while the home screen is the app in front and the TV's
     * display is on: never behind another app, under the screensaver, or in standby. Each also has its own interval.
     */
    private void refreshOnline() {
        android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
        if (!inFront || !hasWindowFocus() || power == null || !power.isInteractive()) {
            Log.i(TAG, "Refresh skipped: " + (!inFront ? "another app in front" : !hasWindowFocus() ? "screensaver or dialog on top" : "display off"));
            return;
        }
        if (!scores().equals("off") && !scores().equals("football")) cricket.refreshIfStale(this::onMatches);
        if (scores().equals("both") || scores().equals("football")) football.refreshIfStale(this::onFootball);
        weather.refreshIfStale(this::onWeather);
        if (prefs.getBoolean("bing_wallpaper", false)) bing.refreshIfStale(this::loadWallpaper);
        if (forYou) trending.refreshIfStale(this::onPicks);
        // Once a day: is there a newer Talkies? Said once per version.
        background.execute(() -> {
            boolean news = SelfUpdate.checkIfStale(this);
            runOnUiThread(() -> {
                onUpdates();
                if (news) Toast.makeText(this, "Talkies " + SelfUpdate.available(this) + " is out: the bell, top right, installs it",
                        Toast.LENGTH_LONG).show();
            });
        });
        if (prefs.getBoolean("youtube_art", false)) RecentStore.upgradeArtwork(this, () -> {
            // Sharper YouTube pictures arrived: redraw Continue and the Watch Next row.
            recentPicks = continuePicks(RecentStore.load(this), watchNextPicks);
            if (tabIdx == 0 && zone != Z_PICKS) buildPicksRow(0);
            refreshChannels();
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // The screensaver or a system dialog took the screen, or gave it back.
        if (hasFocus) refreshOnline();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // backup-settings.sh: write files/talkies-backup.json for it to pull. restore-settings.sh: start again, so
        // onCreate applies the file it pushed.
        if (intent.getBooleanExtra("talkies_backup", false)) {
            Toast.makeText(this, Backup.save(this) ? "Settings backed up" : "Couldn't back up the settings", Toast.LENGTH_SHORT).show();
            return;
        }
        if (intent.getBooleanExtra("talkies_restore", false)) {
            recreate();
            return;
        }
        SleepService.learn(null);  // Home while "Waiting for a button…": stop listening
        // Home: back to the dock at the top, on the Trending tab.
        if (menuMoving) endMenuMove(false);
        if (zone == Z_MENU) {
            menuScroll.setVisibility(View.GONE);
            if (menuMode == MENU_CHANNELS && channelsEdited) refreshChannels();
            menuMode = MENU_PLAIN;
            zone = Z_TOP;
        }
        endMove(false);
        loadWallpaper();
        if (tabIdx != TAB_TRENDING && !picksFor(TAB_TRENDING).isEmpty()) {
            tabIdx = TAB_TRENDING;
            buildPicksRow(0);
        }
        focusDock(dockIdx);
        // A remote button set to "Inputs" (Control Center → Remote keys).
        String open = intent.getStringExtra(RemoteKeys.EXTRA_OPEN);
        if (RemoteKeys.INPUTS.equals(open)) root.post(this::openInputs);
        else if (RemoteKeys.RECENTS.equals(open)) root.post(this::openRecents);
    }
}
