package com.techpp.glass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.util.ArrayList;

/**
 * A card that is still loading: its colour with a soft highlight sliding across, the way streaming apps show a
 * placeholder. One shared clock drives every shimmer on screen, at about 30 frames a second, and runs only while
 * one exists; a shimmer stops when its picture arrives, when it is replaced, or when its view leaves the screen.
 */
final class Shimmer extends Drawable {
    private static final long PERIOD_MS = 1500;
    private static final ArrayList<Shimmer> LIVE = new ArrayList<>();
    private static boolean ticking;
    private static int frame;
    private static final Choreographer.FrameCallback TICK = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            ticking = false;
            if (LIVE.isEmpty()) return;
            if ((++frame & 1) == 0) {
                for (int i = LIVE.size() - 1; i >= 0; i--) {
                    Shimmer s = LIVE.get(i);
                    Callback cb = s.getCallback();
                    if (!(cb instanceof View) || !((View) cb).isAttachedToWindow()) {
                        LIVE.remove(i);
                        s.running = false;
                    } else {
                        s.invalidateSelf();
                    }
                }
            }
            if (!LIVE.isEmpty()) {
                ticking = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG), sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF box = new RectF();
    private final float radius;
    private final int highlight;
    private LinearGradient gradient;
    private boolean running;
    private final long offset = (long) (Math.random() * PERIOD_MS);

    Shimmer(int baseColor, int highlightColor, float radiusPx) {
        base.setColor(baseColor);
        highlight = highlightColor;
        radius = radiusPx;
    }

    void start() {
        if (running) return;
        running = true;
        LIVE.add(this);
        if (!ticking) {
            ticking = true;
            Choreographer.getInstance().postFrameCallback(TICK);
        }
    }

    void stop() {
        running = false;
        LIVE.remove(this);
    }

    @Override protected void onBoundsChange(Rect b) {
        box.set(b);
        int clear = highlight & 0x00FFFFFF;
        gradient = new LinearGradient(0, 0, Math.max(1, b.width() * 0.55f), 0,
                new int[]{clear, highlight, clear}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        sweep.setShader(gradient);
    }

    @Override public void draw(Canvas c) {
        if (box.isEmpty()) return;
        c.drawRoundRect(box, radius, radius, base);
        if (!running || gradient == null) return;
        float t = ((SystemClock.uptimeMillis() + offset) % PERIOD_MS) / (float) PERIOD_MS;
        float w = box.width();
        matrix.setTranslate(box.left + (-0.6f + 2.0f * t) * w, 0);
        gradient.setLocalMatrix(matrix);
        c.drawRoundRect(box, radius, radius, sweep);
    }

    @Override public void setAlpha(int alpha) { base.setAlpha(alpha); sweep.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
