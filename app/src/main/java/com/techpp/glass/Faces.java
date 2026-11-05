package com.techpp.glass;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.SparseArray;

/**
 * Outfit at a given weight, built once. setFontVariationSettings makes a new typeface on every call, which on this
 * kind of box costs milliseconds each and ran dozens of times per tab change and per key press; a cached typeface
 * set with setTypeface costs nothing.
 */
final class Faces {
    private static final SparseArray<Typeface> CACHE = new SparseArray<>();

    private Faces() { }

    static synchronized Typeface outfit(Context c, int weight) {
        Typeface t = CACHE.get(weight);
        if (t == null) {
            Paint p = new Paint();
            p.setTypeface(c.getResources().getFont(R.font.outfit));
            p.setFontVariationSettings("'wght' " + weight);
            t = p.getTypeface();
            CACHE.put(weight, t);
        }
        return t;
    }
}
