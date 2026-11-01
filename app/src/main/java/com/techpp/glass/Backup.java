package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Your settings in one file, files/talkies-backup.json, to carry them over an uninstall or to another TV: Talkies
 * settings, the dock and app order, hidden apps, remote keys, channels, football leagues, the keys and the weather
 * area. Not the caches (picks, scores, weather), which fetch again. backup-settings.sh asks for it and pulls it;
 * restore-settings.sh pushes it back, and Talkies applies it the next time it starts, then deletes it. The file holds
 * your keys, so it stays on your computer, never in git.
 */
final class Backup {
    static final String FILE = "talkies-backup.json";
    private static final String TAG = "GlassBackup";
    private static final int VERSION = 1;
    /** Preference files kept whole: they hold settings only. */
    private static final String[] WHOLE = {"home", "remote_keys", "channels"};
    private static final String[] KEYS = {Keys.TMDB, Keys.LOCATION};

    private Backup() { }

    private static File file(Context c) { return new File(c.getExternalFilesDir(null), FILE); }

    /** Writes the backup; false if it couldn't. */
    static boolean save(Context c) {
        try {
            JSONObject prefs = new JSONObject();
            for (String name : WHOLE) prefs.put(name, dump(c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll()));
            // From football's file only your leagues: the rest is its cache.
            String leagues = c.getSharedPreferences("football", Context.MODE_PRIVATE).getString("leagues", null);
            JSONObject football = new JSONObject();
            if (leagues != null) football.put("leagues", new JSONObject().put("t", "s").put("v", leagues));
            prefs.put("football", football);
            JSONObject files = new JSONObject();
            for (String k : KEYS) {
                String v = Keys.read(c, k);
                if (v != null) files.put(k, v);
            }
            JSONObject all = new JSONObject().put("version", VERSION).put("saved_at", System.currentTimeMillis())
                    .put("prefs", prefs).put("files", files);
            File f = file(c), tmp = new File(f.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(all.toString(1).getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(f)) return false;
            Log.i(TAG, "Saved " + f.length() + " bytes");   // never what's in it: it holds the keys
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Couldn't save: " + e);
            return false;
        }
    }

    /**
     * Applies files/talkies-backup.json if it's there, then deletes it; call before anything reads the settings.
     * True when settings were restored.
     */
    static boolean restoreIfPresent(Context c) {
        File f = file(c);
        if (!f.isFile()) return false;
        try {
            JSONObject all;
            try (InputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) Math.min(f.length(), 1 << 20)];
                int n = 0, got;
                while (n < b.length && (got = in.read(b, n, b.length - n)) > 0) n += got;
                all = new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
            }
            if (all.optInt("version") != VERSION) throw new IOException("unknown version " + all.optInt("version"));
            JSONObject prefs = all.getJSONObject("prefs");
            for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
                String name = it.next();
                SharedPreferences.Editor e = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
                if (!name.equals("football")) e.clear();   // the whole file comes back as it was
                load(e, prefs.getJSONObject(name));
                e.commit();
            }
            JSONObject files = all.optJSONObject("files");
            if (files != null) for (String k : KEYS) if (files.has(k)) Keys.write(c, k, files.getString(k));
            Log.i(TAG, "Restored");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Couldn't restore: " + e);
            return false;
        } finally {
            // Applied (or unreadable): either way it goes, so it isn't applied again over later changes.
            if (!f.delete()) Log.w(TAG, "Couldn't delete " + FILE);
        }
    }

    /** Each value with its type, so a number comes back as the int or long it was. */
    private static JSONObject dump(Map<String, ?> values) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> en : values.entrySet()) {
            Object v = en.getValue();
            JSONObject t = new JSONObject();
            if (v instanceof Boolean) t.put("t", "b").put("v", v);
            else if (v instanceof Integer) t.put("t", "i").put("v", v);
            else if (v instanceof Long) t.put("t", "l").put("v", v);
            else if (v instanceof Float) t.put("t", "f").put("v", ((Float) v).doubleValue());
            else if (v instanceof String) t.put("t", "s").put("v", v);
            else if (v instanceof Set) {
                JSONArray a = new JSONArray();
                for (Object s : (Set<?>) v) a.put(String.valueOf(s));
                t.put("t", "ss").put("v", a);
            } else continue;
            o.put(en.getKey(), t);
        }
        return o;
    }

    private static void load(SharedPreferences.Editor e, JSONObject o) throws Exception {
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject t = o.getJSONObject(k);
            switch (t.getString("t")) {
                case "b": e.putBoolean(k, t.getBoolean("v")); break;
                case "i": e.putInt(k, t.getInt("v")); break;
                case "l": e.putLong(k, t.getLong("v")); break;
                case "f": e.putFloat(k, (float) t.getDouble("v")); break;
                case "s": e.putString(k, t.getString("v")); break;
                case "ss": {
                    JSONArray a = t.getJSONArray("v");
                    Set<String> s = new HashSet<>();
                    for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                    e.putStringSet(k, s);
                    break;
                }
                default: break;
            }
        }
    }
}
