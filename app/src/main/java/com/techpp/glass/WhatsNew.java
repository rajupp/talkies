package com.techpp.glass;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What's new in this version, in assets/whats_new.md: short notes for the TV, written for each release (CHANGELOG.md
 * has the long version, for GitHub). Each item is a bold name, a sentence, and where to find it. After an update Home
 * shows the first few once; Talkies settings → About → What's new has them all. An idea from Plume.
 */
final class WhatsNew {
    static final class Section {
        final String title;                       // "New", "Changed", "Fixed", "Removed"
        final List<String> items = new ArrayList<>();   // each item's markdown, its lines joined
        Section(String title) { this.title = title; }
    }

    private static final Pattern MARKUP = Pattern.compile("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`(.+?)`|\\[(.+?)\\]\\([^)]*\\)");

    private WhatsNew() { }

    /** The sections, in the changelog's order; empty if the file is missing. */
    static List<Section> read(Context c) {
        List<Section> out = new ArrayList<>();
        String text;
        try (InputStream in = c.getAssets().open("whats_new.md")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            text = new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return out;
        }
        Section s = null;
        StringBuilder item = null;
        for (String line : text.split("\n")) {
            if (line.startsWith("### ")) {
                if (item != null && s != null) s.items.add(item.toString());
                item = null;
                s = new Section(line.substring(4).trim());
                out.add(s);
            } else if (line.startsWith("- ") && s != null) {
                if (item != null) s.items.add(item.toString());
                item = new StringBuilder(line.substring(2).trim());
            } else if (line.startsWith("  ") && item != null) {
                item.append(' ').append(line.trim());
            } else if (line.trim().isEmpty() && item != null && s != null) {
                s.items.add(item.toString());
                item = null;
            }
        }
        if (item != null && s != null) s.items.add(item.toString());
        out.removeIf(x -> x.items.isEmpty());
        return out;
    }

    /** Up to {@code max} items for the card: what's new, else what changed or was fixed, each cut to its name and first sentence. */
    static List<String> highlights(List<Section> all, int max) {
        List<String> out = new ArrayList<>();
        for (String want : new String[]{"New", "Changed", "Fixed"}) {
            for (Section s : all) {
                if (!s.title.equalsIgnoreCase(want)) continue;
                for (String it : s.items) {
                    if (out.size() >= max) return out;
                    // "**Name.** One sentence. Select **…**." → "**Name.** One sentence."
                    Matcher m = Pattern.compile("^(\\*\\*.+?\\*\\*\\s*[^.]*\\.?)").matcher(it);
                    out.add(m.find() ? m.group(1).trim() : it);
                }
            }
        }
        return out;
    }

    /**
     * Bold for Outfit, a variable font: its weight is a setting of the font (StyleSpan's bold doesn't reach it).
     * Text after the span keeps the TextView's own weight.
     */
    static final class Weight extends android.text.style.MetricAffectingSpan {
        private final int weight;
        Weight(int weight) { this.weight = weight; }
        @Override public void updateDrawState(android.text.TextPaint p) { p.setFontVariationSettings("'wght' " + weight); }
        @Override public void updateMeasureState(android.text.TextPaint p) { p.setFontVariationSettings("'wght' " + weight); }
    }

    /** Markdown's **bold**, *italic*, `code` and [links](…) as styled text (the link keeps only its words). */
    static CharSequence styled(String md) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        Matcher m = MARKUP.matcher(md);
        int at = 0;
        while (m.find()) {
            b.append(md, at, m.start());
            int start = b.length();
            if (m.group(1) != null) {
                b.append(m.group(1));
                b.setSpan(new Weight(650), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(2) != null) {
                b.append(m.group(2));
                b.setSpan(new StyleSpan(Typeface.ITALIC), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                b.append(m.group(3) != null ? m.group(3) : m.group(4));
            }
            at = m.end();
        }
        b.append(md, at, md.length());
        return b;
    }

    /**
     * Whether Home should show the card now: Talkies was updated (not freshly installed) to a version whose card
     * hasn't been shown. A fresh install just remembers its version.
     */
    @SuppressWarnings("deprecation")   // versionCode: longVersionCode is Android 9+
    static boolean due(Context c, SharedPreferences prefs) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            int seen = prefs.getInt("news_seen", 0);
            if (seen >= pi.versionCode) return false;
            if (pi.firstInstallTime == pi.lastUpdateTime) {
                prefs.edit().putInt("news_seen", pi.versionCode).apply();
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    static void markSeen(Context c, SharedPreferences prefs) {
        try {
            prefs.edit().putInt("news_seen", c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode).apply();
        } catch (Exception ignored) { }
    }
}
