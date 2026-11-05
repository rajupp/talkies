package com.techpp.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Decoded pictures for the cards: a small memory cache, so going back to a tab (or sliding back to a card) shows
 * its picture at once, and two decoder threads, so the large card's picture isn't stuck behind fifteen posters.
 * Pictures are RGB_565 (half the memory, no visible difference here).
 */
final class Images {
    interface Callback { void onImage(Bitmap b); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(14 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    /** Two threads a little below the UI thread's priority. */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int n;
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE / 2);
                r.run();
            }, "Pictures-" + (++n));
            t.setDaemon(true);
            return t;
        }
    });

    private Images() { }

    private static String key(File f, int sample) { return f.getPath() + "@" + sample + "@" + f.lastModified(); }

    /** The picture if it is already decoded, else null. */
    static Bitmap peek(File f, int sample) { return CACHE.get(key(f, sample)); }

    /** Decodes in the background (or hands over the cached picture) and calls back on the UI thread. */
    static void load(File f, int sample, Callback cb) {
        final String k = key(f, sample);
        Bitmap hit = CACHE.get(k);
        if (hit != null) {
            cb.onImage(hit);
            return;
        }
        POOL.execute(() -> {
            Bitmap b = CACHE.get(k);
            if (b == null) {
                b = decode(f, sample);
                if (b != null) CACHE.put(k, b);
            }
            final Bitmap out = b;
            MAIN.post(() -> cb.onImage(out));
        });
    }

    /** Decodes ahead of time, with nobody waiting for it. */
    static void prefetch(File f, int sample) {
        if (f == null || !f.isFile()) return;
        final String k = key(f, sample);
        if (CACHE.get(k) != null) return;
        POOL.execute(() -> {
            if (CACHE.get(k) == null) {
                Bitmap b = decode(f, sample);
                if (b != null) CACHE.put(k, b);
            }
        });
    }

    private static Bitmap decode(File f, int sample) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }
}
