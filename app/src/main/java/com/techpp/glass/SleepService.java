package com.techpp.glass;

// Adapted from tv-vibehack's launcher (SleepService.java, pr/control-center), MIT License,
// Copyright (c) 2026 Gathin. See THIRD_PARTY_NOTICES.md.

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Puts the TV in standby for "Turn off now" and the sleep timer. This TV has no device-admin support,
 * so lockNow() isn't available; the accessibility "lock screen" global action does the same job.
 *
 * Also types a match into JioHotstar's search when a Cricket card has no direct link: JioHotstar's search
 * links open an empty search page. It only acts for 30 s after you pick a card and looks for nothing but the search box.
 *
 * And keeps Talkies as the home screen while the TV's own launcher stays enabled (on Google TV the Assistant needs
 * it: results such as "show me action movies" open on its home screen), though Android may send Home to it. So the
 * Home key opens Talkies, and so does the built-in launcher's home screen appearing on its own (after a restart,
 * or backing out of its pages), except within a few seconds of the Assistant, when it is showing results. The
 * built-in launchers are found at start: system apps that can be the home screen (launchers you installed, like
 * Projectivy, are left alone). The approach follows Matinee's HomeKeyService (MIT). Events come only from JioHotstar,
 * those launchers and the Assistant.
 *
 * Keys: it filters Home (above), OK while the sleep warning is up ("15 more minutes"), and remote buttons you've
 * given a job in Talkies settings → Remote keys (RemoteKeys); every other key passes through untouched. A button with a
 * hold job does it when held for HOLD_MS, and its press job when let go sooner.
 *
 * With "At power-on: Talkies, every time" set, Talkies also comes up each time the TV wakes from standby, unless you
 * press a button first (an idea from CleanYourTV's power_on_home_always, MIT); with an app or input chosen there, that
 * opens instead, after a restart too (an idea from Aerial, MIT).
 * The sleep warning is a small card drawn as an accessibility overlay, so no "display over other apps" permission.
 * You switch it on yourself in Settings → Device Preferences → Accessibility; if a force stop switches it off,
 * ServiceRepair can put it back (see there).
 */
public class SleepService extends AccessibilityService {
    private static final String TAG = "GlassSleep";
    private static final String SEARCH_BAR = Cricket.HOTSTAR + ":id/search_bar";
    private static final long SEARCH_WAIT_MS = 30_000;
    private static SleepService running;
    private static String pendingSearch;
    private static long pendingUntil;
    private static final String ASSISTANT = "com.google.android.katniss";
    /** The built-in launchers' home screens (package/class), found at start. */
    private final java.util.Set<String> stockHomes = new java.util.HashSet<>();
    private final java.util.Set<String> stockPackages = new java.util.HashSet<>();
    private static final long ASSISTANT_GRACE_MS = 8_000, BOOT_WINDOW_MS = 2 * 60_000, REOPEN_GUARD_MS = 1_000;
    private long assistantAt, openedAt, keyAt;
    private static final long HOLD_MS = 600, WAKE_DELAY_MS = 800;
    /** The held button's hold job, waiting for HOLD_MS; null once it ran or the button came up. */
    private Runnable holding;
    private boolean held;
    /** Home held for HOLD_MS: Google TV's dashboard (Android's notification panel), as Home does without Talkies. */
    private final Runnable dashboard = () -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    private final Runnable homeHold = () -> { homeHeld = true; dashboard.run(); };
    private boolean homeHeld;
    private BroadcastReceiver screenOn;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Remote keys: the next button pressed is reported here instead of doing anything (null when not learning). */
    interface KeyListener { void onKey(int keyCode); }
    private static KeyListener learning;

    static boolean isRunning() { return running != null; }

    static boolean lock() {
        return Sleep.SUPPORTED && running != null && running.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** The TV's own power menu (Restart, and Power off on most TVs); it asks before it acts. */
    static boolean powerMenu() {
        return running != null && running.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    /**
     * Talkies settings → At power-on: "boot" (Talkies after a restart only), "always" (every wake from standby too) or
     * "open" (an app or input, every time). True when waking from standby does something.
     */
    static boolean homeOnWake(Context c) {
        return !"boot".equals(c.getSharedPreferences("home", MODE_PRIVATE).getString("home_at_power_on", "boot"));
    }

    /** The app's package, or AppEntry.INPUT and an input's ID, to open at power-on; null for Talkies. */
    static String openAtPowerOn(Context c) {
        SharedPreferences p = c.getSharedPreferences("home", MODE_PRIVATE);
        return "open".equals(p.getString("home_at_power_on", "boot")) ? p.getString("power_on_open", null) : null;
    }

    /** Types {@code text} into JioHotstar's search box once it shows up. False if the service is off. */
    static boolean searchHotstar(String text) {
        if (running == null) return false;
        pendingSearch = text;
        pendingUntil = SystemClock.uptimeMillis() + SEARCH_WAIT_MS;
        return true;
    }

    @Override protected void onServiceConnected() {
        running = this;
        findStockHomes();
        ServiceRepair.connected(this, SleepService.class);
        if (ScoreCard.pinnedId(this) != null) scoreChanged(null);
        // After a restart the Google TV home screen is up before this service binds: take over once.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) powerOn();
        // After Talkies updates itself (or the service comes back for any other reason), Android shows the built-in
        // launcher while this service is unbound, so the window event that would bring Talkies back is missed: if
        // that launcher is what's in front now, take over once. Some TVs bring back the last app instead (TCL, and
        // any TV whose launcher is disabled), so after an update you started in Talkies, take over whatever's there.
        main.postDelayed(() -> {
            if (SelfUpdate.justUpdated(this)) {
                openHome();
                return;
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null && stockPackages.contains(root.getPackageName().toString())) openHome();
        }, 500);
        // Waking from standby isn't a restart: the TV may bring back the last input or app. Registered always, so
        // the setting works without restarting the service; the TV keeps this process up while the service runs.
        if (screenOn == null) {
            screenOn = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (!homeOnWake(c)) return;
                    long on = SystemClock.elapsedRealtime();
                    // After the TV has put its last source back; not if you've already pressed a button.
                    main.postDelayed(() -> { if (keyAt < on) powerOn(); }, WAKE_DELAY_MS);
                }
            };
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled();
        keyAt = SystemClock.elapsedRealtime();
        if (code == KeyEvent.KEYCODE_HOME) {
            // You chose a launcher you installed yourself (Projectivy…) as the home app: Home is its, not ours.
            if (otherLauncherChosen()) return false;
            // Home is caught here and Talkies opened at once, so the TV's own home screen never starts (it costs
            // 80-170 MB of RAM and flashes on screen for seconds on this box). The one thing lost is HDMI-CEC "One
            // Touch Play" (Home waking the TV and switching it to this box), which Android only does when it opens
            // the home app itself. Only where Talkies really outranks the built-in launcher is Home let through.
            if (talkiesIsDefaultHome()) {
                // Held: the dashboard here too. With Talkies the default home app Android doesn't open it; where it
                // does, asking again leaves it open.
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) main.postDelayed(dashboard, HOLD_MS);
                else if (event.getAction() == KeyEvent.ACTION_UP) main.removeCallbacks(dashboard);
                return false;
            }
            // Pressed: Talkies. Held: the dashboard, which catching Home would otherwise take away.
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                homeHeld = false;
                main.postDelayed(homeHold, HOLD_MS);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                main.removeCallbacks(homeHold);
                if (up && !homeHeld) openHome();
                homeHeld = false;
            }
            return true;
        }
        if (warning != null && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
            if (up) { hideWarning(); Sleep.extend(this); }
            return true;
        }
        int id = RemoteKeys.id(event);
        KeyListener l = learning;
        if (l != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            // What the remote sent, for anyone checking with adb logcat why a button can't be changed (a button the
            // TV's software handles itself never shows up here at all).
            Log.i(TAG, "Remote keys: " + KeyEvent.keyCodeToString(code) + " scan " + event.getScanCode()
                    + (RemoteKeys.learnable(id) ? "" : " (can't be changed)"));
        }
        if (l != null && RemoteKeys.learnable(id)) {
            if (up) { learning = null; main.post(() -> l.onKey(id)); }
            return true;
        }
        String action = l == null && id != 0 ? RemoteKeys.action(this, id) : null;
        if (action == null) return false;
        String hold = RemoteKeys.holdAction(this, id);
        if (hold == null) {
            if (up) RemoteKeys.run(this, action);
            return true;
        }
        // Held buttons repeat their down event: only the first starts the wait.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (holding != null) main.removeCallbacks(holding);
            held = false;
            holding = () -> { holding = null; held = true; RemoteKeys.run(this, hold); };
            main.postDelayed(holding, HOLD_MS);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (holding != null) { main.removeCallbacks(holding); holding = null; }
            if (up && !held) RemoteKeys.run(this, action);
            held = false;
        }
        return true;
    }

    /** Reports the next remote button to {@code l} (or stops listening, with null). False if the service is off. */
    static boolean learn(KeyListener l) {
        learning = l;
        applyButtonWatching();
        return running != null;
    }

    // ---------------------------------------------------------------- sleep timer warning

    private LinearLayout warning;
    private TextView warningText;
    private ScoreCard scoreCard;
    private long warningEnds;
    private final Runnable tick = this::tick;

    /** A minute before the sleep timer: a card counting down, "Press OK for 15 more minutes". */
    static void warn(long at) {
        SleepService s = running;
        if (s != null) s.main.post(() -> s.showWarning(at));
    }

    /** Hold OK on a match → Pin over TV: the pinned match changed. False with this service off. */
    static boolean scoreChanged(Cricket.Match m) {
        SleepService s = running;
        if (s == null) return false;
        s.main.post(() -> {
            if (s.scoreCard == null) s.scoreCard = new ScoreCard(s);
            s.scoreCard.sync(m);
        });
        return true;
    }

    static void scoreVisibility() {
        SleepService s = running;
        if (s != null) s.main.post(() -> { if (s.scoreCard != null) s.scoreCard.applyVisibility(); });
    }

    static void cancelWarning() {
        SleepService s = running;
        if (s != null) s.main.post(s::hideWarning);
    }

    private void showWarning(long at) {
        warningEnds = at;
        if (warning == null) {
            float dp = getResources().getDisplayMetrics().density;
            warning = new LinearLayout(this);
            warning.setOrientation(LinearLayout.VERTICAL);
            warning.setPadding(Math.round(22 * dp), Math.round(16 * dp), Math.round(22 * dp), Math.round(16 * dp));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xEB1C1C1E);
            bg.setCornerRadius(18 * dp);
            bg.setStroke(Math.round(dp), 0x33FFFFFF);
            warning.setBackground(bg);
            warningText = new TextView(this);
            warningText.setTextColor(0xFFF5F5F7);
            warningText.setTextSize(20);
            warningText.setTypeface(getResources().getFont(R.font.outfit));
            warningText.setFontVariationSettings("'wght' 600");
            TextView hint = new TextView(this);
            hint.setText("Press OK for 15 more minutes");
            hint.setTextColor(0xFFA1A1A6);
            hint.setTextSize(14);
            hint.setTypeface(getResources().getFont(R.font.outfit));
            warning.addView(warningText);
            warning.addView(hint);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.END;
            lp.x = Math.round(40 * dp);
            lp.y = Math.round(40 * dp);
            try {
                getSystemService(WindowManager.class).addView(warning, lp);
            } catch (RuntimeException e) {
                Log.w(TAG, "Couldn't show the sleep warning: " + e);
                warning = null;
            }
            updateKeyFilter();  // OK has to reach the warning
        }
        tick();
    }

    private void tick() {
        main.removeCallbacks(tick);
        long left = warningEnds - System.currentTimeMillis();
        if (left <= 500) {
            hideWarning();
            Sleep.fire(this);
            return;
        }
        if (warningText != null) warningText.setText("Turning off in " + ((left + 999) / 1000) + " s");
        main.postDelayed(tick, Math.max(50, left % 1000 == 0 ? 1000 : left % 1000));
    }

    private void hideWarning() {
        main.removeCallbacks(tick);
        if (warning == null) return;
        try {
            getSystemService(WindowManager.class).removeView(warning);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
        warning = null;
        warningText = null;
        updateKeyFilter();
    }

    /**
     * System apps that can be the home screen, other than Talkies and the boot-time fallbacks (negative priority):
     * Google TV's launcher, Android TV's, or the TV maker's. Window events are then limited to them, JioHotstar and
     * the Assistant.
     */
    private void findStockHomes() {
        stockHomes.clear();
        stockPackages.clear();
        android.content.pm.PackageManager pm = getPackageManager();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_ALL)) {
            android.content.pm.ActivityInfo ai = ri.activityInfo;
            if (ri.priority < 0 || ai.packageName.equals(getPackageName())) continue;
            if ((ai.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) continue;
            stockHomes.add(ai.packageName + "/" + ai.name);
            stockPackages.add(ai.packageName);
        }
        java.util.Set<String> watched = new java.util.HashSet<>(stockPackages);
        watched.add(Cricket.HOTSTAR);
        watched.add(ASSISTANT);
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.packageNames = watched.toArray(new String[0]);
            setServiceInfo(info);
        }
        updateKeyFilter();
        Log.i(TAG, "Built-in home screens: " + stockHomes);
    }

    /**
     * Talkies settings → Remote keys, On (the default) or Off. On, Home goes straight to Talkies, as other launchers do.
     * Off, the service doesn't watch the remote's buttons, because on some TVs any service that does stops a held
     * Volume button repeating, in every app; Home still reaches Talkies through the window watcher, with Google TV's
     * home showing for a moment. The sleep warning's OK and choosing a button for a remote key turn watching on while they need it.
     */
    static boolean watchButtons(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("home", MODE_PRIVATE);
        return p.getBoolean("remote_keys_on", true);
    }

    /** After Remote keys changes: the running service starts or stops watching buttons now. */
    static void applyButtonWatching() {
        if (running != null) running.main.post(running::updateKeyFilter);
    }

    private boolean filtering;

    /** Watches the buttons while remote keys are on, the sleep warning is up, or a button is being chosen. */
    private void updateKeyFilter() {
        boolean want = watchButtons(this) || warning != null || learning != null || catchesHome();
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        boolean has = (info.flags & android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
        if (has == want && filtering == want) return;
        info.flags = want ? info.flags | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                : info.flags & ~android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        filtering = want;
        Log.i(TAG, want ? "Watching the remote's buttons" : "Remote's buttons left to the TV");
    }

    /**
     * Whether Home is caught here (see onKeyEvent): when a built-in launcher outranks Talkies, so Android would open
     * that one first, which on a 1 GB box costs its RAM and several seconds of its screen. Needs the key filter on.
     */
    private boolean catchesHome() {
        return !otherLauncherChosen() && !talkiesIsDefaultHome();
    }

    private boolean talkiesIsDefaultHome() {
        // The highest-priority home app, which is what Android's Home key opens on this kind of TV: a "preferred
        // home" mark set over adb is ignored there, so it must not count.
        java.util.List<android.content.pm.ResolveInfo> all = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        return !all.isEmpty() && all.get(0).activityInfo != null && getPackageName().equals(all.get(0).activityInfo.packageName);
    }

    /** A box or stick plugged into a TV (Google TV Streamer, Shield…) rather than a TV: it has no HDMI inputs of its own. */
    private boolean streamingBox() {
        android.media.tv.TvInputManager tim = getSystemService(android.media.tv.TvInputManager.class);
        if (tim == null) return true;
        for (android.media.tv.TvInputInfo i : tim.getTvInputList()) if (i.getType() == android.media.tv.TvInputInfo.TYPE_HDMI) return false;
        return true;
    }

    /**
     * Whether the default home app is a launcher you installed (not Talkies, not one the TV came with, not Android's
     * "which app?" chooser). Then Talkies stays out of the way of the Home button.
     */
    private boolean otherLauncherChosen() {
        android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (r == null || r.activityInfo == null) return false;
        String pkg = r.activityInfo.packageName;
        return !pkg.equals(getPackageName()) && !pkg.equals("android") && !stockPackages.contains(pkg)
                && (r.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0;
    }

    /** At power-on: the app or input you chose, else Talkies (also when that app is gone). */
    private void powerOn() {
        String open = openAtPowerOn(this);
        Intent i = null;
        if (open != null && open.startsWith(AppEntry.INPUT)) {
            i = new Intent(Intent.ACTION_VIEW).setDataAndType(android.media.tv.TvContract.buildChannelUriForPassthroughInput(
                    open.substring(AppEntry.INPUT.length())), "vnd.android.cursor.item/channel");
        } else if (open != null) {
            i = getPackageManager().getLeanbackLaunchIntentForPackage(open);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(open);
        }
        if (i == null) { openHome(); return; }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "Couldn't open " + open + ": " + e);
            openHome();
        }
    }

    void openHome() {
        long now = SystemClock.elapsedRealtime();
        if (now - openedAt < REOPEN_GUARD_MS) return;  // the key and the window it brings up arrive together
        openedAt = now;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClassName(this, HomeActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) {
            Log.w(TAG, "Couldn't open Talkies: " + e);
        }
    }

    @Override public boolean onUnbind(Intent intent) {
        // Unbound while still running: you switched it off, so don't put it back (a force stop never gets here).
        ServiceRepair.disconnected(this, SleepService.class);
        hideWarning();
        if (scoreCard != null) scoreCard.hide();
        if (screenOn != null) {
            try { unregisterReceiver(screenOn); } catch (RuntimeException ignored) { }
            screenOn = null;
        }
        running = null;
        return super.onUnbind(intent);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            if (ASSISTANT.contentEquals(pkg)) {
                assistantAt = SystemClock.elapsedRealtime();
            } else if (stockPackages.contains(pkg.toString()) && event.getClassName() != null
                    && (stockHomes.contains(pkg + "/" + event.getClassName())
                        || event.getClassName().toString().endsWith("ProfileChooserActivity"))) {
                // A built-in launcher's home screen, or the "Choose an account" screen Google TV puts up when it starts.
                if (SystemClock.elapsedRealtime() - assistantAt > ASSISTANT_GRACE_MS) openHome();
                else assistantAt = SystemClock.elapsedRealtime();  // browsing the results: keep them
            }
        }
        String text = pendingSearch;
        if (text == null) return;
        if (SystemClock.uptimeMillis() > pendingUntil) { pendingSearch = null; return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !Cricket.HOTSTAR.contentEquals(root.getPackageName())) return;
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(SEARCH_BAR);
        if (found.isEmpty()) return;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (found.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            pendingSearch = null;
            Log.i(TAG, "Searched JioHotstar for " + text);
        }
    }

    @Override public void onInterrupt() { }
}
