package com.techpp.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-screen overlay that draws a ring around the app picked up for moving (hold OK → Move), placed by the activity.
 * Focus itself has no ring by default: the focused card grows and lifts on a shadow, like Plume's. Talkies settings →
 * Focus → Ring keeps the ring on it always, for busy wallpapers where a shadow doesn't show. There, and in the day
 * theme, a faint dark edge keeps the ring visible on light backgrounds.
 */
public class HighlightView extends View {
    final RectF rect = new RectF();
    private float radius;
    private boolean day, moving, always;
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
        if (moving || always) invalidate();
    }

    void setDay(boolean d) { day = d; invalidate(); }

    void setMoving(boolean m) { moving = m; invalidate(); }

    void setAlways(boolean a) { always = a; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        if ((!moving && !always) || rect.isEmpty()) return;
        float w = 3 * dp;
        ring.setStrokeWidth(w);
        tmp.set(rect);
        tmp.inset(-w / 2, -w / 2);
        canvas.drawRoundRect(tmp, radius + w / 2, radius + w / 2, ring);
        if (day || always) {
            tmp.inset(-w / 2 - dp / 2, -w / 2 - dp / 2);
            canvas.drawRoundRect(tmp, radius + w + dp / 2, radius + w + dp / 2, edge);
        }
    }
}
