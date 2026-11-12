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
 * shows what you last watched in the app when Talkies knows it (its Continue picture), else the app's logo on its
 * colour, or on a soft blur of its own banner when that has no one colour. Behind the cards, the focused one's colours,
 * blurred and dim. Left and Right move, OK opens, Back closes. Down (or Menu, or holding OK) offers Remove from list and
 * App info for the focused app: App info is where Android keeps Force stop, which only system apps may do themselves.
 * The layout is after Flow launcher's; a TV app can't show other apps' live screens (and video would be black).
 */
final class RecentsCards extends FrameLayout {
    static final class Entry {
        final String pkg, label;
        /** What you last watched there, from Continue: its picture, title and how far in (0–1, or -1). Null with none. */
        java.io.File still;
        String watched;
        float progress = -1;
        Entry(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private static final float UNFOCUSED = 0.88f, SHADE = 0.32f;
    private final List<Entry> entries;
    private final List<ImageView> pictures = new ArrayList<>(), chips = new ArrayList<>();
    private final List<FrameLayout> frames = new ArrayList<>();
    private final List<View> shades = new ArrayList<>(), headers = new ArrayList<>(), captions = new ArrayList<>();
    private final List<LinearLayout> cards = new ArrayList<>();
    /** Each card's colours, tiny: stretched behind everything while it has focus. */
    private final List<Bitmap> ambient = new ArrayList<>();
    private final ImageView backOld, backNew;
    private final LinearLayout row;
    private final float dp;
    private final int cardW, cardH, step, pictureTop;
    private final long glideMs;
    private final android.view.animation.Interpolator ease = new android.view.animation.PathInterpolator(0.2f, 0.9f, 0.2f, 1f);
    private int at;
    private boolean leaving;
    /** The options under the focused card: -1 when they're closed, else which one has focus. */
    private int action = -1;
    private final LinearLayout actions;
    private final TextView hint;
    private static final String[] ACTIONS = {"Remove from list", "App info"};

    RecentsCards(Context c, List<Entry> entries, int cardBackground, boolean lite) {
        super(c);
        this.entries = entries;
        dp = c.getResources().getDisplayMetrics().density;
        glideMs = lite ? 0 : 280;
        int screenW = c.getResources().getDisplayMetrics().widthPixels, screenH = c.getResources().getDisplayMetrics().heightPixels;
        cardW = Math.round(screenW * 0.6f);
        cardH = cardW * 9 / 16;
        step = cardW + px(6);
        pictureTop = (screenH - cardH) / 2 - px(18);
        int headerH = px(34), headerGap = px(12);
        // Nothing of Home showing through (its text ghosted behind the title): black, then the focused card's colours.
        setBackground(new ColorDrawable(0xFF0A0A0E));
        setClickable(true);
        setClipChildren(false);
        backOld = backdrop(c);
        backNew = backdrop(c);
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xD60A0A0E, 0xB30A0A0E, 0xE00A0A0E}));
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = text(c, "RECENT APPS", 13, 600);
        title.setLetterSpacing(0.12f);
        title.setTextColor(0xB3F5F5F7);
        LayoutParams titleLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        titleLp.leftMargin = px(48);
        titleLp.topMargin = px(34);
        addView(title, titleLp);

        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Wide enough for every card: the row slides, it doesn't scroll.
        LayoutParams rowLp = new LayoutParams(step * Math.max(1, entries.size()), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        rowLp.topMargin = pictureTop - headerH - headerGap;
        addView(row, rowLp);

        for (Entry e : entries) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setClipChildren(false);
            // The app's name on the card's top edge, with its banner small beside it once the card shows a picture.
            LinearLayout header = new LinearLayout(c);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            ImageView chip = new ImageView(c);
            chip.setVisibility(GONE);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.rightMargin = px(10);
            header.addView(chip, chipLp);
            TextView label = text(c, e.label, 20, 600);
            label.setTextColor(0xFFF5F5F7);
            label.setSingleLine(true);
            header.addView(label);
            LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, headerH);
            headerLp.leftMargin = px(4);
            headerLp.bottomMargin = headerGap;
            card.addView(header, headerLp);

            FrameLayout frame = new FrameLayout(c);
            GradientDrawable back = new GradientDrawable();
            back.setColor(cardBackground);
            back.setCornerRadius(px(16));
            frame.setBackground(back);
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(16)); }
            });
            frame.setClipToOutline(true);
            frame.setElevation(px(14));
            ImageView picture = new ImageView(c);
            picture.setScaleType(ImageView.ScaleType.CENTER);
            frame.addView(picture, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (e.progress > 0 && e.still != null) {
                // How far in, as on Home's Continue cards.
                View track = new View(c), done = new View(c);
                track.setBackgroundColor(0x59FFFFFF);
                done.setBackgroundColor(0xFFF5F5F7);
                LayoutParams tl = new LayoutParams(cardW - px(40), px(4), Gravity.BOTTOM | Gravity.START);
                tl.leftMargin = px(20);
                tl.bottomMargin = px(16);
                frame.addView(track, tl);
                LayoutParams dl = new LayoutParams(Math.round((cardW - px(40)) * Math.min(1f, e.progress)), px(4), Gravity.BOTTOM | Gravity.START);
                dl.leftMargin = px(20);
                dl.bottomMargin = px(16);
                frame.addView(done, dl);
            }
            // Neighbours step back under a shade, not by fading: a faded white card is a grey one.
            View shade = new View(c);
            shade.setBackgroundColor(0xFF000000);
            shade.setAlpha(SHADE);
            frame.addView(shade, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(frame, new LinearLayout.LayoutParams(cardW, cardH));

            TextView caption = text(c, e.watched != null && e.still != null ? "Last watched  ·  " + e.watched : " ", 15, 400);
            caption.setTextColor(0xFFC7C7CC);
            caption.setSingleLine(true);
            caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            capLp.topMargin = px(12);
            capLp.leftMargin = px(4);
            card.addView(caption, capLp);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.rightMargin = step - cardW;
            row.addView(card, cardLp);
            card.setPivotX(cardW / 2f);
            card.setPivotY(headerH + headerGap + cardH / 2f);
            pictures.add(picture);
            chips.add(chip);
            frames.add(frame);
            shades.add(shade);
            headers.add(header);
            captions.add(caption);
            cards.add(card);
            ambient.add(null);
        }
        if (entries.isEmpty()) {
            TextView none = text(c, "Apps you open show up here", 20, 400);
            none.setTextColor(0xFFA1A1A6);
            addView(none, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        // The options, and the hint they replace, right under the focused card.
        int under = pictureTop + cardH + px(50);
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
        LayoutParams al = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        al.topMargin = under;
        addView(actions, al);
        hint = text(c, "Down for options", 14, 400);
        hint.setTextColor(0xFF9A9AA0);
        LayoutParams hl = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        hl.topMargin = under + px(9);
        addView(hint, hl);
        if (entries.isEmpty()) hint.setVisibility(GONE);
        place(false);
        if (!lite && !entries.isEmpty()) {
            // The cards come forward as the screen fades in.
            row.setPivotX(cardW / 2f);
            row.setPivotY(headerH + headerGap + cardH / 2f);
            row.setScaleX(0.94f);
            row.setScaleY(0.94f);
            row.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(ease).start();
        }
    }

    private ImageView backdrop(Context c) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setAlpha(0f);
        addView(v, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
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
        if (entries.isEmpty() || leaving) return;
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
        if (leaving) return false;
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
        float x = screenW / 2f - cardW / 2f - at * step;
        long ms = glide ? glideMs : 0;
        row.animate().translationX(x).setDuration(ms).setInterpolator(ease).start();
        for (int i = 0; i < cards.size(); i++) {
            boolean on = i == at;
            float scale = on ? 1f : UNFOCUSED;
            cards.get(i).animate().scaleX(scale).scaleY(scale).setDuration(ms).setInterpolator(ease).start();
            shades.get(i).animate().alpha(on ? 0f : SHADE).setDuration(ms).start();
            headers.get(i).animate().alpha(on ? 1f : 0.55f).setDuration(ms).start();
            captions.get(i).animate().alpha(on ? 1f : 0f).setDuration(ms).start();
            // The focused card's light line; it steps back while the options below have focus.
            GradientDrawable line = null;
            if (on) {
                line = new GradientDrawable();
                line.setCornerRadius(px(16));
                line.setColor(0);
                line.setStroke(px(2), action >= 0 ? 0x66FFFFFF : 0xE6FFFFFF);
            }
            frames.get(i).setForeground(line);
        }
        showAmbient(glide);
    }

    /** The focused card's colours behind everything, fading over the last card's. */
    private void showAmbient(boolean fade) {
        Bitmap b = at < ambient.size() ? ambient.get(at) : null;
        if (b == null || backNew.getTag() == b) return;
        android.graphics.drawable.Drawable was = backNew.getDrawable();
        backNew.animate().cancel();
        if (was != null) {
            backOld.setImageDrawable(was);
            backOld.setAlpha(1f);
        }
        backNew.setImageBitmap(b);
        backNew.setTag(b);
        backNew.setAlpha(0f);
        backNew.animate().alpha(1f).setDuration(fade && glideMs > 0 ? 360 : 0).start();
    }

    /** OK on a card: it grows to fill the screen, then {@code then} opens its app. Keys wait meanwhile. */
    void launch(Runnable then) {
        if (leaving) return;
        if (glideMs == 0 || entries.isEmpty()) { then.run(); return; }
        leaving = true;
        int screenW = getResources().getDisplayMetrics().widthPixels, screenH = getResources().getDisplayMetrics().heightPixels;
        float grow = Math.max(screenW / (float) cardW, screenH / (float) cardH) * 1.02f;
        frames.get(at).setForeground(null);
        actions.animate().alpha(0f).setDuration(120).start();
        hint.animate().alpha(0f).setDuration(120).start();
        for (int i = 0; i < cards.size(); i++) {
            if (i != at) cards.get(i).animate().alpha(0f).setDuration(140).start();
        }
        headers.get(at).animate().alpha(0f).setDuration(100).start();
        captions.get(at).animate().alpha(0f).setDuration(100).start();
        cards.get(at).animate().scaleX(grow).scaleY(grow).translationY(screenH / 2f - (pictureTop + cardH / 2f))
                .setDuration(220).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(then).start();
    }

    /** True while a card is growing into its app. */
    boolean leaving() { return leaving; }

    /**
     * The app's art arrived (it's loaded off the main thread, so the cards come up at once): at its own size in the middle
     * of a card of its colour, never stretched. A card that shows what you last watched keeps that picture, and gets the
     * app's banner small beside its name instead.
     */
    void showArt(String pkg, Bitmap art, int color, Bitmap blur, Bitmap chip) {
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).pkg.equals(pkg)) continue;
            if (pictures.get(i).getScaleType() == ImageView.ScaleType.CENTER_CROP) {
                if (chip != null) {
                    chips.get(i).setImageBitmap(chip);
                    chips.get(i).setVisibility(VISIBLE);
                }
                continue;
            }
            FrameLayout f = frames.get(i);
            if (blur != null) {
                android.graphics.drawable.BitmapDrawable back = new android.graphics.drawable.BitmapDrawable(getResources(), blur);
                back.setFilterBitmap(true);
                f.setBackground(back);
            } else if (f.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) f.getBackground()).setColor(color);
            }
            pictures.get(i).setImageBitmap(art);
            Bitmap glow = blur;
            if (glow == null) {
                glow = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                glow.setPixel(0, 0, color);
            }
            ambient.set(i, glow);
            if (i == at) showAmbient(true);
        }
    }

    /** What you last watched in the app, to fill its card. */
    void showStill(String pkg, Bitmap still) {
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).pkg.equals(pkg)) continue;
            ImageView v = pictures.get(i);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setImageBitmap(still);
            // A dozen points of it, stretched over the screen: its colours without its shapes.
            ambient.set(i, Bitmap.createScaledBitmap(still, 6, 4, true));
            if (i == at) showAmbient(true);
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
    int artWidth() { return cardW * 2 / 5; }

    /** How wide a card is: what a picture for it is decoded to. */
    int cardWidth() { return cardW; }

    /** The focused app, or null with none. */
    Entry focused() { return entries.isEmpty() ? null : entries.get(at); }

}
