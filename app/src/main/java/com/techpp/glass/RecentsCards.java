package com.techpp.glass;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Recent apps as big cards across the screen, newest first: double-press Home, or Control Center → Recent apps. A card
 * shows the app's logo on its colour, or on a soft blur of its own banner when that has no one colour. Left and Right
 * move, OK opens, Back closes. Down (or Menu, or holding OK) offers Remove from list and App info for the focused app:
 * App info is where Android keeps Force stop, which only system apps may do themselves.
 */
final class RecentsCards extends FrameLayout {
    static final class Entry {
        final String pkg, label;
        Entry(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private static final float UNFOCUSED = 0.86f;
    private final List<Entry> entries;
    private final List<ImageView> pictures = new ArrayList<>();
    private final List<LinearLayout> cards = new ArrayList<>();
    private final LinearLayout row;
    private final float dp;
    private final int cardW;
    private final long glideMs;
    private int at;
    /** The options under the focused card: -1 when they're closed, else which one has focus. */
    private int action = -1;
    private final LinearLayout actions;
    private final TextView hint;
    private static final String[] ACTIONS = {"Remove from list", "App info"};

    RecentsCards(Context c, List<Entry> entries, int cardBackground, boolean lite) {
        super(c);
        this.entries = entries;
        dp = c.getResources().getDisplayMetrics().density;
        glideMs = lite ? 0 : 170;
        cardW = Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.4f);
        int cardH = cardW * 9 / 16;
        // No blur or glass, and nothing of Home showing through: its text ghosted behind the title.
        setBackground(new ColorDrawable(0xFF0A0A0E));
        setClickable(true);

        TextView title = text(c, "Recent apps", 26, 600);
        title.setTextColor(0xFFF5F5F7);
        LayoutParams titleLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        titleLp.leftMargin = px(48);
        titleLp.topMargin = px(36);
        addView(title, titleLp);

        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        setClipChildren(false);
        // Wide enough for every card: the row slides, it doesn't scroll.
        LayoutParams rowLp = new LayoutParams((cardW + px(10)) * Math.max(1, entries.size()), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        addView(row, rowLp);

        for (Entry e : entries) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setClipChildren(false);
            ImageView picture = new ImageView(c);
            picture.setScaleType(ImageView.ScaleType.CENTER);
            GradientDrawable back = new GradientDrawable();
            back.setColor(cardBackground);
            back.setCornerRadius(px(14));
            picture.setBackground(back);
            picture.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(14)); }
            });
            picture.setClipToOutline(true);
            picture.setElevation(px(10));
            card.addView(picture, new LinearLayout.LayoutParams(cardW, cardH));
            TextView label = text(c, e.label, 18, 500);
            label.setTextColor(0xFFF5F5F7);
            label.setSingleLine(true);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = px(14);
            card.addView(label, labelLp);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.rightMargin = px(10);
            row.addView(card, cardLp);
            card.setPivotX(cardW / 2f);
            card.setPivotY(cardH / 2f);
            pictures.add(picture);
            cards.add(card);
        }
        if (entries.isEmpty()) {
            TextView none = text(c, "Apps you open show up here", 20, 400);
            none.setTextColor(0xFFA1A1A6);
            addView(none, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        for (String name : ACTIONS) {
            TextView b = text(c, name, 16, 500);
            b.setPadding(px(18), px(9), px(18), px(9));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = bl.rightMargin = px(6);
            actions.addView(b, bl);
        }
        actions.setVisibility(GONE);
        LayoutParams al = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        al.bottomMargin = px(64);
        addView(actions, al);
        hint = text(c, "Down for options", 14, 400);
        hint.setTextColor(0xFF8E8E93);
        LayoutParams hl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        hl.bottomMargin = px(72);
        addView(hint, hl);
        if (entries.isEmpty()) hint.setVisibility(GONE);
        place(false);
    }

    private TextView text(Context c, String s, float sp, int weight) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(Fonts.outfit(c, weight));
        return t;
    }

    private int px(float v) { return Math.round(v * dp); }

    /** Whether the options are open: then Left and Right choose between them, and OK does the chosen one. */
    boolean choosing() { return action >= 0; }

    /** The chosen option: 0 removes the app from the list, 1 opens its App info. */
    int action() { return action; }

    /** Opens the options under the focused card, or closes them. */
    void choose(boolean open) {
        if (entries.isEmpty()) return;
        action = open ? 0 : -1;
        actions.setVisibility(open ? VISIBLE : GONE);
        hint.setVisibility(open ? GONE : VISIBLE);
        styleActions();
        place(true);
    }

    private void styleActions() {
        for (int i = 0; i < actions.getChildCount(); i++) {
            TextView b = (TextView) actions.getChildAt(i);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(20));
            g.setColor(i == action ? 0xFFF5F5F7 : 0x1FFFFFFF);
            b.setBackground(g);
            b.setTextColor(i == action ? 0xFF111113 : 0xFFF5F5F7);
        }
    }

    /** Left or Right: true if the focus moved. */
    boolean move(int step) {
        if (action >= 0) {
            int to = action + step;
            if (to < 0 || to >= ACTIONS.length) return false;
            action = to;
            styleActions();
            return true;
        }
        int next = at + step;
        if (next < 0 || next >= entries.size()) return false;
        at = next;
        place(true);
        return true;
    }

    private void place(boolean glide) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        float x = screenW / 2f - cardW / 2f - at * (cardW + px(10));
        long ms = glide ? glideMs : 0;
        row.animate().translationX(x).setDuration(ms).start();
        for (int i = 0; i < cards.size(); i++) {
            float scale = i == at ? 1f : UNFOCUSED;
            cards.get(i).animate().scaleX(scale).scaleY(scale).alpha(i == at ? 1f : 0.5f).setDuration(ms).start();
            // The focused card's light line; it steps back while the options below have focus.
            GradientDrawable line = null;
            if (i == at) {
                line = new GradientDrawable();
                line.setCornerRadius(px(14));
                line.setColor(0);
                line.setStroke(px(2), action >= 0 ? 0x66FFFFFF : 0xE6FFFFFF);
            }
            pictures.get(i).setForeground(line);
        }
    }

    /**
     * The app's art arrived (it's loaded off the main thread, so the cards come up at once): at its own size in the middle
     * of a card of its colour, never stretched.
     */
    void showArt(String pkg, Bitmap art, int color, Bitmap blur) {
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).pkg.equals(pkg)) continue;
            ImageView v = pictures.get(i);
            if (blur != null) {
                android.graphics.drawable.BitmapDrawable back = new android.graphics.drawable.BitmapDrawable(getResources(), blur);
                back.setFilterBitmap(true);
                v.setBackground(back);
            } else if (v.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) v.getBackground()).setColor(color);
            }
            v.setImageBitmap(art);
        }
    }

    /**
     * For art with no one colour (a gradient banner, a poster): the art itself, tiny and a little darker. Stretched
     * across the card it's a soft blur of the app's own colours, where their average was a muddy grey. Null when the
     * corners agree: then the plain colour joins the logo with no edge.
     */
    static Bitmap blurOf(Drawable d) {
        if (d == null) return null;
        try {
            Bitmap small = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
            android.graphics.Rect was = new android.graphics.Rect(d.getBounds());
            d.setBounds(0, 0, 64, 36);
            d.draw(new android.graphics.Canvas(small));
            d.setBounds(was);
            if (plain(small)) { small.recycle(); return null; }
            // Just four points of it: more, and a logo in the middle shows as a pale smudge behind itself.
            Bitmap tiny = Bitmap.createScaledBitmap(small, 2, 2, true);
            if (tiny != small) small.recycle();
            new android.graphics.Canvas(tiny).drawColor(0x40000000);
            return tiny;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean plain(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int dx = Math.max(1, w / 8), dy = Math.max(1, h / 8);
        int[] corners = {b.getPixel(dx, dy), b.getPixel(w - 1 - dx, dy), b.getPixel(dx, h - 1 - dy), b.getPixel(w - 1 - dx, h - 1 - dy)};
        for (int c : corners) {
            if (Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(corners[0])) > 12
                    || Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(corners[0])) > 12
                    || Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(corners[0])) > 12) return false;
        }
        return true;
    }

    /**
     * The colour to put behind a tile or picture: its corners' when they agree (a logo on a plain tile, so the logo sits
     * on the same colour with no edge), else its average.
     */
    static int colorOf(android.graphics.Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int dx = Math.max(1, w / 8), dy = Math.max(1, h / 8);
        int[] corners = {b.getPixel(dx, dy), b.getPixel(w - 1 - dx, dy), b.getPixel(dx, h - 1 - dy), b.getPixel(w - 1 - dx, h - 1 - dy)};
        boolean agree = true;
        for (int c : corners) {
            if (Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(corners[0])) > 12
                    || Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(corners[0])) > 12
                    || Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(corners[0])) > 12) agree = false;
        }
        int c = corners[0];
        if (!agree) {
            android.graphics.Bitmap one = android.graphics.Bitmap.createScaledBitmap(b, 1, 1, true);
            c = one.getPixel(0, 0);
            if (one != b) one.recycle();
            // Corners that differ are a poster or a gradient: its average, deep, so the picture stands out from it.
            c = android.graphics.Color.rgb(android.graphics.Color.red(c) * 35 / 100, android.graphics.Color.green(c) * 35 / 100,
                    android.graphics.Color.blue(c) * 35 / 100);
        }
        return c | 0xFF000000;
    }

    /** The same for a drawable; {@code fallback} if it can't be drawn. */
    static int colorOf(Drawable d, int fallback) {
        if (d == null) return fallback;
        try {
            android.graphics.Bitmap small = android.graphics.Bitmap.createBitmap(64, 36, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Rect was = new android.graphics.Rect(d.getBounds());
            d.setBounds(0, 0, 64, 36);
            d.draw(new android.graphics.Canvas(small));
            d.setBounds(was);
            int c = colorOf(small);
            small.recycle();
            return c;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** The art at {@code width} across with rounded corners, drawn at that size, so it's as sharp as the art allows. */
    static android.graphics.Bitmap rounded(Drawable d, int width, float radiusPx) {
        if (d == null) return null;
        int iw = Math.max(1, d.getIntrinsicWidth()), ih = Math.max(1, d.getIntrinsicHeight());
        int w = Math.min(width, iw * 3), h = Math.max(1, w * ih / iw);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(b);
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(new android.graphics.RectF(0, 0, w, h), radiusPx, radiusPx, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        android.graphics.Rect was = new android.graphics.Rect(d.getBounds());
        d.setBounds(0, 0, w, h);
        d.draw(canvas);
        d.setBounds(was);
        return b;
    }

    /** How wide art is drawn on a card: about its size on Home, where it's sharp. */
    int artWidth() { return cardW * 3 / 5; }

    /** The focused app, or null with none. */
    Entry focused() { return entries.isEmpty() ? null : entries.get(at); }

}
