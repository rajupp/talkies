package com.techpp.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * The whole background in one layer: the theme's gradient, the current pick's image (tiny, so stretching it blurs it
 * for free) or your wallpaper, and the scrim, composed once into a small bitmap and drawn stretched to the screen.
 * Drawing them as separate full-screen views painted every pixel four times a frame, which this TV's GPU couldn't
 * keep up with while scrolling. A new image crossfades (two layers for 450 ms).
 */
public class Backdrop extends View {
    private static final int W = 192, H = 108;      // ambient: enough for smooth gradients once filtered
    private static final long FADE_MS = 450;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF full = new RectF();
    private int bgTop, bgBottom, scrimTop, scrimMid, scrimBottom;
    private Bitmap image, cur, prev;
    private boolean wallpaper;
    private float imageAlpha;
    private int[] scrimStops;   // with a wallpaper: the veil Legibility worked out, top to bottom (null: the theme's)
    private int glow;           // with For You hidden: a soft glow in the focused app's colour (0: none)
    private boolean day;
    private long fadeStart;
    private Paint veil;         // with a wallpaper: the veil's gradient, made again when the veil or the height changes
    private int veilHeight;
    private boolean lite;       // Lite effects: no glow, and a new background shows at once

    public Backdrop(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
    }

    void setTheme(boolean day, int bgTop, int bgBottom, int scrimTop, int scrimMid, int scrimBottom, float ambientAlpha) {
        this.day = day;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.scrimTop = scrimTop;
        this.scrimMid = scrimMid;
        this.scrimBottom = scrimBottom;
        if (!wallpaper) imageAlpha = ambientAlpha;
        compose(false);
    }

    /** Lite effects: the theme's gradient alone, changed without a crossfade. */
    void setLite(boolean on) {
        lite = on;
        if (on) {
            glow = 0;
            if (!wallpaper) image = null;
        }
        compose(false);
    }

    /** The current pick's image (or null), crossfaded in. Ignored while a wallpaper is set. */
    void setAmbient(Bitmap small, float alpha) {
        if (wallpaper) return;
        glow = 0;
        image = small;
        imageAlpha = alpha;
        compose(true);
    }

    /** A glow in {@code color} (the focused app's), crossfaded in, in place of a pick's image; 0 for none. */
    void setGlow(int color) {
        if (lite) color = 0;
        if (wallpaper || (image == null && color == glow)) return;
        image = null;
        glow = color;
        compose(true);
    }

    /** The veil over a wallpaper as evenly spaced colours, top to bottom; null for the theme's own three. */
    void setScrimStops(int[] colors) {
        scrimStops = colors;
        compose(false);
    }

    /** Your own wallpaper, or null to go back to the picks' images. */
    void setWallpaper(Bitmap bmp) {
        wallpaper = bmp != null;
        image = bmp;
        imageAlpha = 1f;
        compose(false);
    }

    private void compose(boolean fade) {
        if (wallpaper) {
            // Drawn as it is, veil on top (onDraw): a full-size copy blended in advance would double its memory.
            cur = prev = null;
            veil = null;
            invalidate();
            return;
        }
        int w = W, h = H;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, bgTop, bgBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        if (image != null) {
            // Centre crop.
            float s = Math.max((float) w / image.getWidth(), (float) h / image.getHeight());
            float iw = image.getWidth() * s, ih = image.getHeight() * s;
            p.setAlpha(Math.round(255 * imageAlpha));
            c.drawBitmap(image, null, new RectF((w - iw) / 2, (h - ih) / 2, (w + iw) / 2, (h + ih) / 2), p);
            p.setAlpha(255);
        }
        if (glow != 0 && image == null) {
            // Strongest left of centre, fading out well before the edges; fainter on the light theme.
            int a = day ? 0x80 : 0xF0, rgb = glow & 0x00FFFFFF;
            p.setShader(new RadialGradient(w * 0.36f, h * 0.5f, w * 0.62f, new int[]{(a << 24) | rgb, ((a * 2 / 5) << 24) | rgb, rgb},
                    new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            if (!day) {
                // Deepen the glow's centre colour so it reads as a lit backdrop, not a flat tint.
                p.setShader(new RadialGradient(w * 0.36f, h * 0.5f, w * 0.3f, new int[]{0x66000000 | rgb, rgb}, null, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
            }
        }
        int[] scrim = wallpaper && scrimStops != null ? scrimStops : new int[]{scrimTop, scrimMid, scrimBottom};
        p.setShader(new LinearGradient(0, 0, 0, h, scrim, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        prev = fade && !lite && cur != null ? cur : null;
        cur = out;
        fadeStart = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        full.set(0, 0, getWidth(), getHeight());
        if (wallpaper && image != null) {
            // Centre crop at the screen's size, so the photo stays sharp, then the veil that keeps text readable.
            float s = Math.max(full.width() / image.getWidth(), full.height() / image.getHeight());
            float iw = image.getWidth() * s, ih = image.getHeight() * s;
            paint.setAlpha(255);
            canvas.drawBitmap(image, null, new RectF((full.width() - iw) / 2, (full.height() - ih) / 2,
                    (full.width() + iw) / 2, (full.height() + ih) / 2), paint);
            if (veil == null || veilHeight != getHeight()) {
                int[] scrim = scrimStops != null ? scrimStops : new int[]{scrimTop, scrimMid, scrimBottom};
                veil = new Paint();
                veil.setShader(new LinearGradient(0, 0, 0, getHeight(), scrim, null, Shader.TileMode.CLAMP));
                veilHeight = getHeight();
            }
            canvas.drawRect(full, veil);
            return;
        }
        if (cur == null) return;
        float f = prev == null ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - fadeStart) / (float) FADE_MS);
        if (f < 1f) {
            paint.setAlpha(255);
            canvas.drawBitmap(prev, null, full, paint);
            float e = 1 - (1 - f) * (1 - f);   // ease out
            paint.setAlpha(Math.round(255 * e));
            canvas.drawBitmap(cur, null, full, paint);
            postInvalidateOnAnimation();
        } else {
            prev = null;
            paint.setAlpha(255);
            canvas.drawBitmap(cur, null, full, paint);
        }
    }

    @Override public boolean hasOverlappingRendering() { return false; }
}
