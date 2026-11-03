package com.techpp.glass;

import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Talkies as the TV's screensaver (Settings → Device Preferences → Screen saver → Talkies): a dim clock, the date
 * and the weather, moving to a new spot every 20 seconds so nothing burns in. Behind it, by Talkies settings →
 * Screensaver: black (Clock), your wallpaper, dimmed and drifting slowly (Wallpaper), or the picks' backdrops, one a
 * minute, with the title and app (Picks). It shows over any app after the TV's idle time, and any button wakes the
 * TV. An idea from Home Deck (MIT).
 */
public class Screensaver extends DreamService {
    private static final long MOVE_MS = 20_000L, PICK_MS = 60_000L, FADE_MS = 1500L;
    /** How far the picture drifts and grows over each minute, so no pixel holds still. */
    private static final float DRIFT = 0.04f;
    private final Random random = new Random();
    private FrameLayout root;
    private ImageView[] art;   // two, crossfading
    private int front;
    private LinearLayout block;
    private TextView clock, date, weather, caption;
    private String mode;
    private List<Trending.Pick> picks;
    private int pickAt;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            move();
            block.postDelayed(this, MOVE_MS);
        }
    };
    /** Wallpaper mode: the picture, shown again (a fresh drift) every ten minutes. */
    private Bitmap still;
    private final Runnable nextPicture = new Runnable() {
        @Override public void run() {
            if (still != null) show(still, null); else showNext();
            root.postDelayed(this, still != null ? 10 * PICK_MS : PICK_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any button wakes the TV
        setFullscreen(true);
        setScreenBright(false);  // dimmed, like the TV's own screensaver
        SharedPreferences prefs = getSharedPreferences("home", MODE_PRIVATE);
        mode = prefs.getString("screensaver", "clock");
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        if (!mode.equals("clock")) {
            art = new ImageView[]{new ImageView(this), new ImageView(this)};
            for (ImageView v : art) {
                v.setScaleType(ImageView.ScaleType.CENTER_CROP);
                v.setAlpha(0f);
                root.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            }
            View dim = new View(this);   // keeps the picture quiet and the clock readable
            dim.setBackgroundColor(0xB8000000);
            root.addView(dim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        Typeface outfit = getResources().getFont(R.font.outfit);
        clock = text(outfit, 96, 300, 0xB3FFFFFF);
        date = text(outfit, 22, 500, 0x80FFFFFF);
        date.setLetterSpacing(0.12f);
        weather = text(outfit, 20, 400, 0x66FFFFFF);
        caption = text(outfit, 18, 400, 0x66FFFFFF);
        caption.setPadding(0, 16, 0, 0);
        block.addView(clock);
        block.addView(date);
        block.addView(weather);
        block.addView(caption);
        caption.setVisibility(View.GONE);
        root.addView(block, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (mode.equals("picks")) picks = new Trending(this).cached(Trending.TRENDING);
        if (mode.equals("wallpaper")) still = wallpaper();
        if (still != null || (mode.equals("picks") && picks != null && !picks.isEmpty())) root.post(nextPicture);
        block.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        block.removeCallbacks(tick);
        root.removeCallbacks(nextPicture);
        super.onDreamingStopped();
    }

    private TextView text(Typeface font, int sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTypeface(font);
        t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setShadowLayer(12, 0, 2, 0xCC000000);   // readable over a bright picture
        return t;
    }

    /** The time (12 or 24 hours as the TV is set), the date, and the weather line Talkies last showed. */
    private void update() {
        Date now = new Date();
        boolean h24 = DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(h24 ? "H:mm" : "h:mm", now));
        date.setText(DateFormat.format("EEEE  d MMMM", now).toString().toUpperCase(java.util.Locale.getDefault()));
        String w = new Weather(this).cached();
        weather.setText(w == null ? "" : w);
        weather.setVisibility(w == null ? View.GONE : View.VISIBLE);
    }

    /** A new random spot inside the screen, with a margin; the clock ticks by minute, so 20 s keeps it right enough. */
    private void move() {
        block.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = root.getWidth(), h = root.getHeight(), bw = block.getMeasuredWidth(), bh = block.getMeasuredHeight();
        if (w == 0 || h == 0) return;
        int margin = w / 20;
        block.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            block.setTranslationX(margin + random.nextInt(Math.max(1, w - bw - 2 * margin)));
            block.setTranslationY(margin + random.nextInt(Math.max(1, h - bh - 2 * margin)));
            block.animate().alpha(1f).setDuration(600).start();
        }).start();
    }

    /** The next pick's backdrop and "Jailer · Prime Video" under the clock. */
    private void showNext() {
        Trending.Pick p = picks.get(pickAt++ % picks.size());
        Bitmap b = decode(p.image);
        if (b != null) show(b, p.title + "  ·  " + p.service);
    }

    /**
     * Fades the picture in over the last one, which fades out and lets go of its memory, then lets it drift and grow a
     * little until the next: a still picture would burn in on an OLED TV as surely as a still clock.
     */
    private void show(Bitmap b, String text) {
        ImageView in = art[1 - front], out = art[front];
        front = 1 - front;
        for (ImageView v : art) v.animate().cancel();
        in.setImageBitmap(b);
        in.setScaleX(1f);
        in.setScaleY(1f);
        in.setTranslationX(0);
        in.setTranslationY(0);
        int w = Math.max(1, root.getWidth());
        float dx = (random.nextBoolean() ? 1 : -1) * DRIFT * w / 2, dy = (random.nextBoolean() ? 1 : -1) * DRIFT * w / 4;
        long life = still != null ? 10 * PICK_MS : PICK_MS + FADE_MS;
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        android.animation.ObjectAnimator fadeIn = android.animation.ObjectAnimator.ofFloat(in, View.ALPHA, in.getAlpha(), 1f).setDuration(FADE_MS);
        android.animation.ObjectAnimator fadeOut = android.animation.ObjectAnimator.ofFloat(out, View.ALPHA, out.getAlpha(), 0f).setDuration(FADE_MS);
        fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { if (still == null) out.setImageDrawable(null); }
        });
        android.animation.ObjectAnimator drift = android.animation.ObjectAnimator.ofPropertyValuesHolder(in,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f + DRIFT),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f + DRIFT),
                android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_X, dx),
                android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dy)).setDuration(life);
        drift.setInterpolator(new android.view.animation.LinearInterpolator());
        set.playTogether(fadeIn, fadeOut, drift);
        set.start();
        caption.setText(text == null ? "" : text);
        caption.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    /** Your picture, else Bing's when that's on: what Home shows as its wallpaper. */
    private Bitmap wallpaper() {
        File dir = getExternalFilesDir(null);
        if (dir == null) return null;
        for (String name : new String[]{"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) {
            File f = new File(dir, name);
            if (f.isFile()) return decode(f);
        }
        File bing = new File(dir, Bing.FILE);
        if (getSharedPreferences("home", MODE_PRIVATE).getBoolean("bing_wallpaper", false) && bing.isFile()) return decode(bing);
        return null;
    }

    /** At about the screen's width, or half that on a low-memory TV: dimmed behind the clock, nobody sees the difference. */
    private Bitmap decode(File f) {
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        boolean low = am.isLowRamDevice() || mi.totalMem < 1600L * 1024 * 1024;
        int want = getResources().getDisplayMetrics().widthPixels / (low ? 2 : 1);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= want) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        if (low) o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
