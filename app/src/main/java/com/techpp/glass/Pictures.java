package com.techpp.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The cards' pictures: the ones decoded lately stay in memory, so going back to a tab shows them at once, and two
 * threads of their own decode the rest, so they don't wait behind network work or each other. Never recycle a bitmap
 * from here: other cards may be showing it. (An idea from codermert's build of Talkies.)
 */
final class Pictures {
    interface Done { void with(Bitmap picture); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> DECODED = new LruCache<String, Bitmap>(
            (int) Math.min(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getAllocationByteCount(); }
    };
    private static final ExecutorService DECODERS = Executors.newFixedThreadPool(2, job -> {
        Thread t = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            job.run();
        }, "Pictures");
        t.setDaemon(true);
        return t;
    });

    private Pictures() { }

    /** A rewritten file gets a new key: the art of a card can change under the same name. */
    private static String key(File f, int sample) { return f.getPath() + "@" + sample + "@" + f.lastModified(); }

    /** The picture if it's already decoded, else null. */
    static Bitmap ready(File f, int sample) { return DECODED.get(key(f, sample)); }

    /** Decodes at 1/sample size in the background, then calls back on the main thread (with null if it can't). */
    static void load(File f, int sample, Done done) {
        String key = key(f, sample);
        DECODERS.execute(() -> {
            Bitmap b = DECODED.get(key);
            if (b == null) {
                // Photos don't need an alpha channel: RGB_565 halves their memory with no visible difference here.
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                b = BitmapFactory.decodeFile(f.getPath(), o);
                if (b != null) DECODED.put(key, b);
            }
            Bitmap picture = b;
            MAIN.post(() -> done.with(picture));
        });
    }

    /** Android is short of memory. Cards keep what they're showing. */
    static void clear() { DECODED.evictAll(); }
}
