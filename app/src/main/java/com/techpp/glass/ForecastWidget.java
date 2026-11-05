package com.techpp.glass;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * A glass pill under the header: today's high and low, then the next few hours, each as a time over a temperature.
 * It reads the forecast Talkies already keeps for Control Center, so it costs no extra request.
 */
final class ForecastWidget extends LinearLayout {
    private static final int HOURS = 3;
    private final float dp;

    ForecastWidget(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(12 * dp);
        setPadding(pad, 0, pad, 0);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Fills the pill; false (and hidden) while there is no forecast yet. */
    boolean update(List<String[]> hours, String today, int text, int text2, int bg, int stroke) {
        removeAllViews();
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(21 * dp);
        g.setColor(bg);
        g.setStroke(Math.round(dp), stroke);
        setBackground(g);
        if (hours.isEmpty()) {
            setVisibility(GONE);
            return false;
        }
        if (today != null) {
            addCell("TODAY", today, text, text2, 80);
            View rule = new View(getContext());
            rule.setBackgroundColor((text2 & 0x00FFFFFF) | 0x40000000);
            LayoutParams rl = new LayoutParams(Math.round(dp), Math.round(22 * dp));
            rl.leftMargin = Math.round(8 * dp);
            rl.rightMargin = Math.round(8 * dp);
            addView(rule, rl);
        }
        for (int i = 0; i < Math.min(HOURS, hours.size()); i++) {
            String[] row = hours.get(i);
            int space = row[1].indexOf(' ');
            addCell(row[0], space > 0 ? row[1].substring(0, space) : row[1], text, text2, 54);
        }
        setVisibility(VISIBLE);
        return true;
    }

    private void addCell(String top, String bottom, int text, int text2, int widthDp) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = label(top, 9.5f, text2, 500);
        t.setLetterSpacing(0.12f);
        TextView b = label(bottom, 14.5f, text, 700);
        cell.addView(t, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cell.addView(b, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(cell, new LayoutParams(Math.round(widthDp * dp), ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView label(String s, float sp, int color, int weight) {
        TextView v = new TextView(getContext());
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        v.setTypeface(Faces.outfit(getContext(), weight));
        v.setGravity(Gravity.CENTER);
        return v;
    }
}
