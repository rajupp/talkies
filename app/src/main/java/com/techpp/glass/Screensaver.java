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
 * minute, with the title and app (Picks). Or, instead of all that, a split-flap board with the same and the day's
 * high and low (Board). Your message from the phone shows under it, and in quiet hours it all dims further. It shows
 * over any app after the TV's idle time, and any button wakes the TV. An idea from Home Deck (MIT); the board, the
 * message and quiet hours from Departures (MIT).
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
    private FlapBoard board;
    /** Quiet hours: how bright everything is then. */
    private static final float QUIET_ALPHA = 0.35f;
    private List<Trending.Pick> picks;
    private int pickAt;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            root.animate().alpha(quiet(Screensaver.this) ? QUIET_ALPHA : 1f).setDuration(3000).start();
            if (board != null) {
                updateBoard();
                shiftBoard();
            } else {
                update();
                move();
            }
            block.postDelayed(this, MOVE_MS);
        }
    };
    /** The seconds: the board (or the clock) again as each second begins. */
    private final Runnable second = new Runnable() {
        @Override public void run() {
            if (board != null) updateBoard(); else update();
            block.postDelayed(this, 1_020L - System.currentTimeMillis() % 1_000L);
        }
    };
    /** Every 15 seconds every letter on the board turns once round, a wave from the top left, so it never sits still. */
    private static final long RIPPLE_MS = 15_000L;
    private final Runnable ripple = new Runnable() {
        @Override public void run() {
            board.ripple();
            block.postDelayed(this, RIPPLE_MS);
        }
    };
    private Weather wx;
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
        root.setAlpha(quiet(this) ? QUIET_ALPHA : 1f);
        Typeface outfit = getResources().getFont(R.font.outfit);
        if (mode.equals("board")) {
            board = new FlapBoard(this, outfit);
            root.addView(board, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else if (!mode.equals("clock")) {
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
        clock = text(outfit, 96, 300, 0xB3FFFFFF);
        clock.setFontFeatureSettings("tnum");   // even-width digits: the seconds don't jiggle the line
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
        if (board != null) block.setVisibility(View.GONE);
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
        block.postDelayed(second, 1_020L - System.currentTimeMillis() % 1_000L);
        if (board != null) block.postDelayed(ripple, RIPPLE_MS);
    }

    @Override
    public void onDreamingStopped() {
        block.removeCallbacks(tick);
        block.removeCallbacks(second);
        block.removeCallbacks(ripple);
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
        // The seconds smaller and fainter after the time, "10:58·42"-like, so the time still reads first.
        String hm = DateFormat.format(h24 ? "H:mm" : "h:mm", now).toString(), sec = DateFormat.format(":ss", now).toString();
        android.text.SpannableString t = new android.text.SpannableString(hm + sec);
        t.setSpan(new android.text.style.RelativeSizeSpan(0.42f), hm.length(), t.length(), 0);
        t.setSpan(new android.text.style.ForegroundColorSpan(0x66FFFFFF), hm.length(), t.length(), 0);
        clock.setText(t);
        date.setText(DateFormat.format("EEEE  d MMMM", now).toString().toUpperCase(java.util.Locale.getDefault()));
        if (wx == null) wx = new Weather(this);
        String w = wx.cached();
        weather.setText(w == null ? "" : w);
        weather.setVisibility(w == null ? View.GONE : View.VISIBLE);
        // Your message, where the picks' caption isn't using the line.
        if (!mode.equals("picks")) {
            String m = message(this);
            caption.setText(m == null ? "" : m.replace('\n', ' '));
            caption.setVisibility(m == null ? View.GONE : View.VISIBLE);
        }
    }

    /** The message sent from the phone (Talkies settings → Screensaver message), up to two lines; null for none. */
    static String message(android.content.Context c) {
        String m = c.getSharedPreferences("home", MODE_PRIVATE).getString("board_message", "").trim();
        return m.isEmpty() ? null : m;
    }

    /** Quiet hours, "23-7" (the default), "22-6", "0-7" or "off": whether it's that time now. */
    static boolean quiet(android.content.Context c) {
        String q = c.getSharedPreferences("home", MODE_PRIVATE).getString("quiet_hours", "23-7");
        String[] f = q.split("-");
        if (f.length != 2) return false;
        int from = Integer.parseInt(f[0]), to = Integer.parseInt(f[1]);
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return from > to ? h >= from || h < to : h >= from && h < to;
    }

    /**
     * The board's rows: the time, the day, the weather, then rain coming or the day's high and low, then your
     * message; centred top to bottom, with a blank under the time when there's room.
     */
    private void updateBoard() {
        Date now = new Date();
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        int white = 0xFFF2F2F2, grey = 0xFFB8B8BC, blue = 0xFF8CCBFF, amber = 0xFFFFC266;
        boolean h24 = DateFormat.is24HourFormat(this);
        lines.add(DateFormat.format(h24 ? "HH:mm:ss" : "h:mm:ss a", now).toString());
        colors.add(white);
        lines.add(new java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.ENGLISH).format(now));
        colors.add(grey);
        if (wx == null) wx = new Weather(this);
        String w = wx.cached();
        String[] hl = wx.highLow();
        String extra = hl == null ? null : "HIGH " + hl[0] + "  LOW " + hl[1];
        if (w != null) {
            String[] parts = w.split(" · ");
            lines.add(parts[0]);
            colors.add(grey);
            if (parts.length > 1) { lines.add(parts[1]); colors.add(blue); }
            else if (extra != null) { lines.add(extra); colors.add(grey); extra = null; }
        }
        String m = message(this);
        if (m != null) for (String l : m.split("\n")) { if (lines.size() < FlapBoard.ROWS) { lines.add(l); colors.add(amber); } }
        if (extra != null && lines.size() < FlapBoard.ROWS && w != null && w.contains(" · ")) { lines.add(extra); colors.add(grey); }
        if (lines.size() <= FlapBoard.ROWS - 2) { lines.add(1, ""); colors.add(1, white); }
        int pad = (FlapBoard.ROWS - lines.size()) / 2;
        String[] rows = new String[FlapBoard.ROWS];
        int[] tint = new int[FlapBoard.ROWS];
        java.util.Arrays.fill(tint, white);
        for (int i = 0; i < lines.size() && i + pad < FlapBoard.ROWS; i++) { rows[i + pad] = lines.get(i); tint[i + pad] = colors.get(i); }
        board.show(rows, tint);
    }

    /** A few pixels somewhere else every 20 seconds, slowly, so the tiles' edges never sit on the same pixels for long. */
    private void shiftBoard() {
        int w = root.getWidth();
        if (w == 0) return;
        float range = w * 0.012f;
        board.animate().translationX((random.nextFloat() * 2 - 1) * range).translationY((random.nextFloat() * 2 - 1) * range)
                .setDuration(4000).start();
    }

    /** A new random spot inside the screen, with a margin. */
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
