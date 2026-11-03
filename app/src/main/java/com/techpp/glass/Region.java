package com.techpp.glass;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The country the picks and scores are for (country): files/region.txt, set in Talkies settings → Country (or by
 * set-region.sh), else where the TV is (here): the time zone's country (many TVs are left on "English (United
 * States)" wherever they are), the language setting's country, and finally the US. The weather's °C or °F and which
 * app has the cricket follow where the TV is, whatever country you choose.
 */
final class Region {
    private static final List<String> ENGLISH = Arrays.asList("US", "GB", "AU", "CA", "NZ", "IE");
    private static final List<String> FAHRENHEIT = Arrays.asList("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH");

    private Region() { }

    /** Two-letter country code, e.g. "IN". */
    static String country(Context c) {
        String override = override(c);
        return override != null ? override : here(c);
    }

    /** The country you chose in Talkies settings → Country, or null: automatic. */
    static String chosen(Context c) { return override(c); }

    /** Chooses the country for the picks and scores; null goes back to where the TV is. */
    static void choose(Context c, String cc) {
        File f = new File(c.getExternalFilesDir(null), "region.txt");
        if (cc == null) {
            if (f.isFile() && !f.delete()) android.util.Log.w("GlassRegion", "Couldn't remove " + f);
            return;
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(cc.getBytes(StandardCharsets.US_ASCII));
        } catch (java.io.IOException e) {
            android.util.Log.w("GlassRegion", "Couldn't save the country: " + e);
        }
    }

    /** Where the TV is, whatever country is chosen: the time zone's, the language setting's, else the US. */
    static String here(Context c) {
        try {
            String tz = android.icu.util.TimeZone.getRegion(TimeZone.getDefault().getID());
            if (tz != null && tz.matches("[A-Z]{2}") && !tz.equals("ZZ")) return tz;
        } catch (RuntimeException ignored) {
            // Unknown zone ("UTC", "GMT"): fall back to the language setting.
        }
        String loc = Locale.getDefault().getCountry();
        return loc != null && loc.matches("[A-Z]{2}") ? loc : "US";
    }

    private static String override(Context c) {
        File f = new File(c.getExternalFilesDir(null), "region.txt");
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[16];
            int n = in.read(b);
            String s = n > 0 ? new String(b, 0, n, StandardCharsets.US_ASCII).trim().toUpperCase(Locale.ROOT) : "";
            return s.matches("[A-Z]{2}") ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** "India", "the US", "the UK": for "New in …". */
    static String name(String cc) {
        switch (cc) {
            case "US": return "the US";
            case "GB": return "the UK";
            case "AE": return "the UAE";
            case "NL": return "the Netherlands";
            case "PH": return "the Philippines";
            default:
                String n = new Locale("", cc).getDisplayCountry(Locale.ENGLISH);
                return n.isEmpty() || n.length() > 14 ? cc : n;
        }
    }

    /** "India", "United States": the country's full name, for Talkies settings → Country. */
    static String fullName(String cc) {
        String n = new Locale("", cc).getDisplayCountry(Locale.ENGLISH);
        return n.isEmpty() ? cc : n;
    }

    /** Countries whose own titles are mostly in English: the Trending mix is then just "popular here". */
    static boolean englishSpeaking(String cc) { return ENGLISH.contains(cc); }

    static boolean fahrenheit(String cc) { return FAHRENHEIT.contains(cc); }

    /** "Hindi", "Korean"…: a language's English name from its ISO 639-1 code, or "" when unknown. */
    static String language(String code) {
        if (code == null || code.isEmpty()) return "";
        String n = new Locale(code).getDisplayLanguage(Locale.ENGLISH);
        return n.equalsIgnoreCase(code) ? "" : n;
    }
}
