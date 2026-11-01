package com.techpp.glass;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * A tile for an app that ships no TV banner, made from its icon instead of showing the small icon on a blank card:
 * a logo on a flat colour fills the tile in that colour with the logo in the middle; any other icon sits beside the
 * app's name on white (dark when the logo is light), the way TV banners look. Adapted from GlassLauncher's tile
 * builder (GlassLauncher, MIT).
 */
final class Tiles {
    private static final Typeface NAME = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private Tiles() { }

    static Bitmap make(Drawable icon, String label, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Integer flat = a.getBackground() == null ? null : uniform(a.getBackground());
            if (flat != null && a.getForeground() != null) {
                // The foreground is drawn whole; its logo sits in the middle two thirds.
                c.drawColor(flat);
                draw(c, a.getForeground(), w, h, Math.round(h * 1.12f));
            } else {
                wordmark(c, icon, label, w, h);
            }
        } else {
            Probe p = probe(icon);
            if (p.edgeOpaque) {
                // A full icon (logo on colour): the tile carries on its colour and the icon grows just enough to hide its
                // margins and rounded corners.
                c.drawColor(p.edgeColor);
                draw(c, icon, w, h, Math.round(h * 1.05f));
            } else {
                wordmark(c, icon, label, w, h);
            }
        }
        return out;
    }

    /**
     * An input's tile: the input glyph above its name ("PlayStation 5", "HDMI 2"), white on the device's brand colour,
     * or on graphite for a device Talkies doesn't know.
     */
    static Bitmap input(Drawable glyph, String label, int color, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(color == Devices.PLAIN ? 0xFF2C2C2E : darker(color));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(NAME);
        p.setColor(Color.WHITE);
        p.setTextSize(h * 0.17f);
        while (p.measureText(label) > w * 0.86f && p.getTextSize() > 12f) p.setTextSize(p.getTextSize() * 0.94f);
        float size = h * 0.3f, gap = h * 0.07f;
        Paint.FontMetrics fm = p.getFontMetrics();
        float textH = fm.descent - fm.ascent, top = (h - (size + gap + textH)) / 2f;
        Drawable g = glyph.mutate();
        g.setTint(Color.WHITE);
        g.setBounds(Math.round((w - size) / 2f), Math.round(top), Math.round((w + size) / 2f), Math.round(top + size));
        g.draw(c);
        c.drawText(label, (w - p.measureText(label)) / 2f, top + size + gap - fm.ascent, p);
        return out;
    }

    /** A brand colour deepened enough for white text on it (Fire TV's orange, Apple TV's silver). */
    private static int darker(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(hsv[2], 0.62f);
        return Color.HSVToColor(hsv);
    }

    private static void draw(Canvas c, Drawable d, int w, int h, int size) {
        int left = (w - size) / 2, top = (h - size) / 2;
        d.setBounds(left, top, left + size, top + size);
        d.draw(c);
    }

    /** Logo on the left and the name on the right, on white, or on near-black when the logo itself is light. */
    private static void wordmark(Canvas c, Drawable icon, String label, int w, int h) {
        boolean dark = probe(icon).logoLuminance > 0.8f;
        c.drawColor(dark ? 0xFF1C1C1E : Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(NAME);
        p.setColor(dark ? Color.WHITE : 0xFF5F6368);
        p.setTextSize(h * 0.22f);
        float iconSize = h * 0.46f, gap = h * 0.1f;
        while (iconSize + gap + p.measureText(label) > w * 0.86f && p.getTextSize() > 12f) p.setTextSize(p.getTextSize() * 0.94f);
        float left = (w - (iconSize + gap + p.measureText(label))) / 2f, top = (h - iconSize) / 2f;
        icon.setBounds(Math.round(left), Math.round(top), Math.round(left + iconSize), Math.round(top + iconSize));
        icon.draw(c);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(label, left + iconSize + gap, h / 2f - (fm.ascent + fm.descent) / 2f, p);
    }

    /** The layer's colour when it's one flat colour, else null. */
    private static Integer uniform(Drawable d) {
        int n = 24;
        int[] px = pixels(d, n);
        int ref = px[n * n / 2 + n / 2], close = 0;
        for (int p : px) {
            if (Color.alpha(p) > 230 && Math.abs(Color.red(p) - Color.red(ref)) < 14
                    && Math.abs(Color.green(p) - Color.green(ref)) < 14 && Math.abs(Color.blue(p) - Color.blue(ref)) < 14) close++;
        }
        return close > px.length * 0.92f ? ref : null;
    }

    private static final class Probe {
        boolean edgeOpaque;
        int edgeColor = Color.WHITE;
        float logoLuminance;
    }

    /** Is the icon's edge solid, in what colour, and is the logo light? */
    private static Probe probe(Drawable d) {
        int n = 96;
        int[] px = pixels(d, n);
        // A ring 14 % in from the edge: inside rounded corners and the usual margins.
        int inset = Math.round(n * 0.14f), opaque = 0, total = 0;
        long r = 0, g = 0, b = 0;
        for (int i = inset; i < n - inset; i++) {
            for (int p : new int[]{px[inset * n + i], px[(n - 1 - inset) * n + i], px[i * n + inset], px[i * n + n - 1 - inset]}) {
                total++;
                if (Color.alpha(p) > 230) { opaque++; r += Color.red(p); g += Color.green(p); b += Color.blue(p); }
            }
        }
        float lum = 0;
        int count = 0;
        for (int p : px) {
            if (Color.alpha(p) > 200) {
                lum += (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f;
                count++;
            }
        }
        Probe out = new Probe();
        out.edgeOpaque = opaque > total * 0.6f;
        if (opaque > 0) out.edgeColor = Color.rgb((int) (r / opaque), (int) (g / opaque), (int) (b / opaque));
        out.logoLuminance = count > 0 ? lum / count : 0;
        return out;
    }

    private static int[] pixels(Drawable d, int n) {
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, n, n);
        d.draw(new Canvas(bmp));
        int[] px = new int[n * n];
        bmp.getPixels(px, 0, n, 0, 0, n, n);
        bmp.recycle();
        return px;
    }
}
