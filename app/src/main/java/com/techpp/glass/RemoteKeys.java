package com.techpp.glass;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Remote keys (Control Center → Remote keys): a button on the remote given another job, e.g. the Netflix button
 * opening JioHotstar. SleepService, Talkies' accessibility service, sees the press before the TV does and does
 * the job instead; buttons with no job pass through untouched. Kept in the "remote_keys" preferences as
 * key code → action: "app:<package>", "inputs", "recents", "control", "tv_settings", "wifi", "home" or "none" ("k<code>"), and the job for holding the button
 * ("h<code>"), which only a button with a press job can have: a press with no job has to reach the TV untouched,
 * and Talkies can't tell a press from a hold until the button is let go. An idea from Plume and CleanYourTV.
 *
 * A button is known by its key code, or, for one Android has no code for (KEYCODE_UNKNOWN: the app buttons on some
 * remotes, such as the Mi Box's Netflix and Prime Video), by its scan code from the remote, stored negated so it
 * can't clash with a key code. See {@link #id}.
 */
final class RemoteKeys {
    static final String INPUTS = "inputs", HOME = "home", NONE = "none", APP = "app:", RECENTS = "recents", CONTROL = "control",
            SETTINGS = "tv_settings", WIFI = "wifi";
    static final String EXTRA_OPEN = "com.techpp.glass.OPEN";

    private RemoteKeys() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("remote_keys", Context.MODE_PRIVATE); }

    /** The button's id: its key code, or minus its scan code when Android has no key code for it (0: neither). */
    static int id(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN) return e.getKeyCode();
        return e.getScanCode() > 0 ? -e.getScanCode() : 0;
    }

    /** Navigation, OK, Back, Home, volume and power keep their jobs: remapping them could lock you out of the TV. */
    static boolean learnable(int code) {
        if (code < 0) return true;  // a button known only by its scan code
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE: case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP: case KeyEvent.KEYCODE_TV_POWER: case KeyEvent.KEYCODE_UNKNOWN:
                return false;
            default:
                return true;
        }
    }

    static String action(Context c, int code) { return prefs(c).getString("k" + code, null); }

    static void set(Context c, int code, String action) {
        // Back to normal drops the hold job too.
        if (action == null) prefs(c).edit().remove("k" + code).remove("h" + code).apply();
        else prefs(c).edit().putString("k" + code, action).apply();
    }

    /** What holding the button does; null when holding it is the same as a press. */
    static String holdAction(Context c, int code) { return prefs(c).getString("h" + code, null); }

    static void setHold(Context c, int code, String action) {
        if (action == null) prefs(c).edit().remove("h" + code).apply();
        else prefs(c).edit().putString("h" + code, action).apply();
    }

    /** Button id ({@link #id}) → action, in id order. */
    static Map<Integer, String> all(Context c) {
        Map<Integer, String> out = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (!e.getKey().startsWith("k")) continue;  // hold jobs
            try {
                out.put(Integer.parseInt(e.getKey().substring(1)), String.valueOf(e.getValue()));
            } catch (RuntimeException ignored) {
                // Not ours.
            }
        }
        return out;
    }

    /** "Prog Red", "Button 3", "Key 1201" for a vendor key Android has no name for, "Button 0x1a5" by scan code. */
    static String keyName(int code) {
        if (code < 0) return "Button 0x" + Integer.toHexString(-code);
        String s = KeyEvent.keyCodeToString(code);
        if (!s.startsWith("KEYCODE_")) return "Key " + code;
        StringBuilder b = new StringBuilder();
        for (String w : s.substring(8).toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    static String describe(Context c, String action) {
        if (action == null) return "Normal";
        if (action.equals(INPUTS)) return "Inputs";
        if (action.equals(RECENTS)) return "Recent apps";
        if (action.equals(CONTROL)) return "Control Center";
        if (action.equals(SETTINGS)) return "TV settings";
        if (action.equals(WIFI)) return "Wi-Fi";
        if (action.equals(HOME)) return "Talkies";
        if (action.equals(NONE)) return "Nothing";
        if (action.startsWith(APP)) return "Open " + Channels.label(c.getPackageManager(), action.substring(APP.length()));
        return action;
    }

    /** Does the job. Called on the accessibility service's thread (main). */
    static void run(SleepService s, String action) {
        try {
            if (action.equals(HOME)) {
                s.openHome();
            } else if (action.equals(SETTINGS) || action.equals(WIFI)) {
                s.startActivity(settings(s, action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (action.equals(INPUTS) || action.equals(RECENTS) || action.equals(CONTROL)) {
                s.startActivity(new Intent(Intent.ACTION_MAIN).setClass(s, HomeActivity.class).putExtra(EXTRA_OPEN, action)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            } else if (action.startsWith(APP)) {
                PackageManager pm = s.getPackageManager();
                String pkg = action.substring(APP.length());
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                if (i != null) {
                    s.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    RecentApps.note(s, pkg);
                }
            }
            // NONE: the button does nothing.
        } catch (RuntimeException e) {
            Log.w("GlassKeys", "Couldn't run " + action + ": " + e);
        }
    }

    /** The screen itself, else the TV's settings app, for TVs that hide the standard screens. */
    private static Intent settings(Context c, String action) {
        PackageManager pm = c.getPackageManager();
        Intent wifi = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        if (action.equals(WIFI) && pm.resolveActivity(wifi, 0) != null) return wifi;
        Intent i = new Intent(android.provider.Settings.ACTION_SETTINGS);
        if (pm.resolveActivity(i, 0) != null) return i;
        return new Intent(Intent.ACTION_MAIN).setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings");
    }

    static List<Integer> codes(Context c) { return new ArrayList<>(all(c).keySet()); }
}
