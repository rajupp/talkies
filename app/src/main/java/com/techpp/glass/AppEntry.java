package com.techpp.glass;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

/**
 * One launchable TV app, or one of the TV's inputs you put on Home (Talkies settings → Inputs on Home). Drawables and
 * the glow colour are loaded lazily and cached.
 */
final class AppEntry {
    /** The package, or {@link #INPUT} and the input's ID for an input. */
    final String pkg;
    final String label;
    final ComponentName component;
    private final ActivityInfo info;
    /** False for a phone app with no TV entry (Chrome, MacroDroid…): opened as a phone launcher would. */
    final boolean tv;
    static final String INPUT = "input:";
    /** An input's ID (null for an app), the device's colour and the input glyph its tile is drawn with. */
    final String input;
    private final int inputColor;
    private final Drawable inputGlyph;

    private boolean loaded;
    private Drawable banner;
    private Drawable icon;
    private int glow = 0;

    AppEntry(ActivityInfo info, String label) { this(info, label, true); }

    AppEntry(ActivityInfo info, String label, boolean tv) {
        this.info = info;
        this.pkg = info.packageName;
        this.label = label;
        this.component = new ComponentName(info.packageName, info.name);
        this.tv = tv;
        input = null;
        inputColor = 0;
        inputGlyph = null;
    }

    /** Fire TV's input switcher, which its own Inputs menu and the remote's input buttons use. */
    private static final String FIRE_INPUTS = "com.amazon.tv.inputpreference.service";

    /**
     * Switches the TV to an input. Android TV's standard link for it opens Live TV's settings on Fire TV, so there it's
     * the link Fire TV's own Inputs menu sends. From Macario's log of a Xiaomi Fire TV.
     */
    static Intent inputIntent(android.content.Context c, String inputId) {
        Intent fire = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tvinput://tunenow?inputId=" + inputId))
                .setPackage(FIRE_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (c.getPackageManager().resolveActivity(fire, 0) != null) return fire;
        return passthrough(inputId);
    }

    private static Intent passthrough(String inputId) {
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId), "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static final java.util.regex.Pattern HW = java.util.regex.Pattern.compile("/HW(\\d+)$");

    /**
     * Switches the TV to an input, and says whether something took the request. On Fire TV: the link its Inputs menu
     * sends, else Amazon's input switch service with the port's hardware number (HW2 is HDMI 1), which any app may
     * call. On a TCL Fire TV (AFTDEC012E) HDMI 4 is HW5 but its switch wants 10. That service and the TCL detail are
     * from Free My TV (MIT), tested there on Xiaomi and TCL Fire TVs. Elsewhere, Android TV's standard link.
     */
    static boolean openInput(android.content.Context c, String inputId, android.os.Bundle options) {
        Intent fire = inputIntent(c, inputId);
        if (FIRE_INPUTS.equals(fire.getPackage())) {
            try {
                c.startActivity(fire, options);
                return true;
            } catch (RuntimeException e) {
                android.util.Log.w("GlassHome", "Fire TV's input link refused: " + e);
            }
        }
        java.util.regex.Matcher m = HW.matcher(inputId);
        if (m.find()) {
            Intent sw = new Intent().setComponent(new ComponentName(FIRE_INPUTS, FIRE_INPUTS + ".KaminoInputSwitchService"));
            if (c.getPackageManager().resolveService(sw, 0) != null) {
                int hardware = Integer.parseInt(m.group(1));
                int type = "AFTDEC012E".equalsIgnoreCase(android.os.Build.MODEL) && hardware == 5 ? 10 : hardware;
                try {
                    if (c.startService(sw.putExtra("inputType", type)) != null) {
                        android.util.Log.i("GlassHome", "Fire TV input switch: HW" + hardware + ", type " + type);
                        return true;
                    }
                } catch (RuntimeException e) {
                    android.util.Log.w("GlassHome", "Fire TV's input switch refused: " + e);
                }
            }
        }
        try {
            c.startActivity(passthrough(inputId), options);
            return true;
        } catch (RuntimeException e) {
            android.util.Log.w("GlassHome", "Couldn't switch to " + inputId + ": " + e);
            return false;
        }
    }

    /** An input: its tile is the glyph and the name on the device's colour. */
    AppEntry(String inputId, String label, int color, Drawable glyph) {
        info = null;
        pkg = INPUT + inputId;
        this.label = label;
        component = null;
        tv = true;
        input = inputId;
        inputColor = color;
        inputGlyph = glyph;
    }

    Intent launchIntent(android.content.Context c) {
        if (input != null) return inputIntent(c, input);  // switches the TV to that input, as Inputs does
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    private static int bannerW = 320, bannerH = 180;
    /** Pictures you gave apps (hold OK → Change image), as files/tiles/<package>.jpg; set by HomeActivity. */
    static java.io.File customDir;

    static java.io.File customImage(String pkg) {
        // An input's ID has slashes ("com.vendor.tvinput/.HdmiService/HW5").
        return customDir == null ? null : new java.io.File(customDir, pkg.replaceAll("[^A-Za-z0-9._-]", "_") + ".jpg");
    }

    /** The pixel size banners are prepared at: the largest a tile is ever drawn. */
    static void setBannerSize(int w, int h) { bannerW = w; bannerH = h; }

    private synchronized void load(PackageManager pm) {
        if (loaded) return;
        loaded = true;
        banner = customBanner();
        if (banner == null && input != null) {
            try {
                Bitmap tile = Tiles.input(inputGlyph, label, inputColor, bannerW, bannerH);
                tile.setDensity(android.util.DisplayMetrics.DENSITY_DEFAULT);
                banner = new android.graphics.drawable.BitmapDrawable(null, tile);
            } catch (RuntimeException e) {
                icon = inputGlyph;
            }
            return;
        }
        if (banner == null) banner = sharpBanner(pm);
        if (banner == null) banner = info.loadBanner(pm);
        if (banner == null) banner = info.applicationInfo.loadBanner(pm);
        if (banner == null) {
            // No banner: a tile made from the icon (Tiles); the plain icon only if that fails.
            icon = info.loadIcon(pm);
            try {
                Bitmap tile = Tiles.make(icon, label, bannerW, bannerH);
                tile.setDensity(android.util.DisplayMetrics.DENSITY_DEFAULT);
                banner = new android.graphics.drawable.BitmapDrawable(null, tile);
            } catch (RuntimeException e) {
                banner = null;
            }
        }
    }

    /**
     * The app's banner at its best: the highest-density version it ships (Android otherwise picks the one for this
     * TV's density, e.g. Netflix's 320 px instead of its 480 px), scaled once with good filtering to exactly the tile's
     * pixel size, with mipmaps so it stays clean when the tile is drawn smaller. Vector banners are drawn at that size.
     */
    private Drawable sharpBanner(PackageManager pm) {
        try {
            int id = info.banner != 0 ? info.banner : info.applicationInfo.banner;
            if (id == 0) return null;
            android.content.res.Resources res = pm.getResourcesForApplication(info.applicationInfo);
            Drawable d = res.getDrawableForDensity(id, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
            if (d == null) return null;
            Bitmap src;
            if (d instanceof android.graphics.drawable.BitmapDrawable) {
                src = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            } else {
                // Vector or layered banner: draw it straight at the target size.
                src = Bitmap.createBitmap(bannerW, bannerH, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, bannerW, bannerH);
                d.draw(new Canvas(src));
            }
            Bitmap out = fit(src, bannerW, bannerH);
            out.setHasMipMap(true);
            out.setDensity(android.util.DisplayMetrics.DENSITY_DEFAULT);
            return new android.graphics.drawable.BitmapDrawable(res, out);
        } catch (Exception e) {
            return null;
        }
    }

    /** Your picture for this app, if you chose one. */
    private Drawable customBanner() {
        java.io.File f = customImage(pkg);
        if (f == null || !f.isFile()) return null;
        try {
            Bitmap src = android.graphics.BitmapFactory.decodeFile(f.getPath());
            if (src == null) return null;
            Bitmap out = fit(src, bannerW, bannerH);
            out.setHasMipMap(true);
            out.setDensity(android.util.DisplayMetrics.DENSITY_DEFAULT);
            return new android.graphics.drawable.BitmapDrawable(null, out);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Center-crops to w×h, halving first while the source is over twice as big (cleaner than one big jump). */
    private static Bitmap fit(Bitmap src, int w, int h) {
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Bitmap b = src;
        while (scale < 0.5f) {
            b = Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() / 2), Math.max(1, b.getHeight() / 2), true);
            scale *= 2;
        }
        int sw = Math.round(w / scale), sh = Math.round(h / scale);
        int sx = Math.max(0, (b.getWidth() - sw) / 2), sy = Math.max(0, (b.getHeight() - sh) / 2);
        sw = Math.min(sw, b.getWidth() - sx);
        sh = Math.min(sh, b.getHeight() - sy);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(b, new android.graphics.Rect(sx, sy, sx + sw, sy + sh), new android.graphics.Rect(0, 0, w, h), p);
        return out;
    }

    /** The same app, listed again: its art is already in memory, if it was loaded. */
    synchronized void keepArt(AppEntry old) {
        synchronized (old) {
            if (!old.loaded) return;
            loaded = true;
            banner = old.banner;
            icon = old.icon;
            glow = old.glow;
        }
    }

    Drawable banner(PackageManager pm) { load(pm); return banner; }

    Drawable icon(PackageManager pm) { load(pm); return icon; }

    /** A bright, saturated version of the banner's colourful pixels' average (grey if it has none), for the glow. */
    int glowColor(PackageManager pm) {
        if (glow != 0) return glow;
        load(pm);
        Drawable d = banner != null ? banner : icon;
        int avg = 0xFF8E8E93;
        if (d != null) {
            Bitmap b = Bitmap.createBitmap(12, 7, Bitmap.Config.ARGB_8888);
            Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
            copy.setBounds(0, 0, 12, 7);
            copy.draw(new Canvas(b));
            long r = 0, g = 0, bl = 0, wsum = 0;
            for (int y = 0; y < 7; y++) {
                for (int x = 0; x < 12; x++) {
                    int c = b.getPixel(x, y);
                    if (Color.alpha(c) < 128) continue;
                    float[] hsv = new float[3];
                    Color.colorToHSV(c, hsv);
                    // Only colourful pixels count (Netflix's banner is mostly white around red letters).
                    if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
                    int w = 1 + (int) (hsv[1] * hsv[2] * 8);
                    r += (long) Color.red(c) * w; g += (long) Color.green(c) * w; bl += (long) Color.blue(c) * w;
                    wsum += w;
                }
            }
            b.recycle();
            if (wsum > 0) avg = Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum));
        }
        float[] hsv = new float[3];
        Color.colorToHSV(avg, hsv);
        if (avg != 0xFF8E8E93) {
            hsv[1] = Math.min(1f, Math.max(hsv[1], 0.8f));
            hsv[2] = Math.max(hsv[2], 0.9f);
        }
        glow = Color.HSVToColor(hsv);
        return glow;
    }
}
