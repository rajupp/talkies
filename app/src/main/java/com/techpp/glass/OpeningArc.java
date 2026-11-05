package com.techpp.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;

/**
 * The curve under the name in the opening animation: a shallow smile that draws itself from left to right
 * (setProgress 0 to 1), a small dot leading it. Drawn from a measured path, so each frame costs one short stroke.
 */
final class OpeningArc extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path full = new Path(), part = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] tip = new float[2];
    private float progress;
    private final float dp;

    OpeningArc(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(3.5f * dp);
        line.setColor(0xFFF5F5F7);
        dot.setColor(0xFFFFFFFF);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        full.reset();
        float pad = 6 * dp;
        full.moveTo(pad, h * 0.18f);
        full.quadTo(w * 0.5f, h * 1.55f, w - pad, h * 0.18f);
        measure.setPath(full, false);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (progress <= 0f) return;
        float len = measure.getLength();
        part.reset();
        measure.getSegment(0, len * progress, part, true);
        c.drawPath(part, line);
        measure.getPosTan(len * progress, tip, null);
        dot.setAlpha(Math.round(255 * Math.min(1f, (1f - progress) * 4f + 0.35f)));
        c.drawCircle(tip[0], tip[1], 4.5f * dp, dot);
    }
}
