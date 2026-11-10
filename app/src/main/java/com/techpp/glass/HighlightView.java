package com.techpp.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-screen overlay that draws the line around the focused card, placed by the activity, and a bold ring around the
 * app picked up for moving (hold OK → Move). Talkies settings → Focus: Outline (the default) adds a thin light line to
 * the lift, so focus is clear on dark posters; Lift has the lift and shadow alone, like Plume's; Ring is bold, for busy
 * wallpapers. A faint dark edge keeps the line visible on light backgrounds.
 */
public class HighlightView extends View {
    final RectF rect = new RectF();
    private float radius;
    static final int OUTLINE = 0, LIFT = 1, RING = 2;
    private boolean day, moving;
    private int style = OUTLINE;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final RectF tmp = new RectF();

    public HighlightView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFFFFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0x55000000);
        edge.setStrokeWidth(dp);
        setFocusable(false);
        setWillNotDraw(false);
    }

    void setRing(RectF r, float radiusPx) {
        rect.set(r);
        radius = radiusPx;
        if (moving || style != LIFT) invalidate();
    }

    void setDay(boolean d) { day = d; invalidate(); }

    void setMoving(boolean m) { moving = m; invalidate(); }

    void setStyle(int s) { style = s; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        if ((!moving && style == LIFT) || rect.isEmpty()) return;
        boolean bold = moving || style == RING;
        float w = (bold ? 3 : 2) * dp;
        ring.setStrokeWidth(w);
        ring.setColor(bold ? 0xFFFFFFFF : 0xE6FFFFFF);
        tmp.set(rect);
        tmp.inset(-w / 2, -w / 2);
        canvas.drawRoundRect(tmp, radius + w / 2, radius + w / 2, ring);
        if (day || bold) {
            tmp.inset(-w / 2 - dp / 2, -w / 2 - dp / 2);
            canvas.drawRoundRect(tmp, radius + w + dp / 2, radius + w + dp / 2, edge);
        }
    }
}
