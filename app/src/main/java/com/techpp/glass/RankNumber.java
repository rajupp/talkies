package com.techpp.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * The big outlined number on a Trending card: dark fill, gradient stroke, the way best-seller lists show it.
 * Gold, silver and bronze for the first three, white for the rest.
 */
final class RankNumber extends View {
    private static final int[][] COLORS = {
            {0xFFFFF3B0, 0xFFFFD24A, 0xFFC98A12},
            {0xFFFFFFFF, 0xFFD9DCE6, 0xFF8C90A0},
            {0xFFFFD2B0, 0xFFE0915A, 0xFF9A4E22},
    };
    private static final int[] BRAND = {0xE6FFFFFF, 0xB3FFFFFF, 0x66FFFFFF};   // the launcher's white, fading

    private final String text;
    private final int[] colors;
    private final float dp;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float size;

    RankNumber(Context c, int rank) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        text = String.valueOf(rank);
        colors = rank >= 1 && rank <= 3 ? COLORS[rank - 1] : BRAND;
        size = 58 * dp;
        for (Paint p : new Paint[]{fill, stroke}) {
            p.setTypeface(Faces.outfit(c, 900));
            p.setTextSize(size);
            p.setLetterSpacing(-0.06f);
        }
        fill.setColor(0xFF0B0B0E);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.6f * dp);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(fill.measureText(text) + 8 * dp), Math.round(size * 0.86f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        stroke.setShader(new LinearGradient(0, 0, w * 0.4f, h, colors, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float x = 4 * dp, y = getHeight() - 5 * dp;
        c.drawText(text, x, y, fill);
        c.drawText(text, x, y, stroke);
    }
}
