package com.techpp.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.icu.lang.UCharacter;
import android.icu.lang.UProperty;
import android.os.SystemClock;
import android.view.View;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Board screensaver: a split-flap board, 22 tiles by 6 rows, like an old station departures board. Only the tiles
 * whose letter changes flip, each running through a few letters on the way, a column at a time. Drawn on a plain
 * Canvas and idle between changes, so it costs nothing on a small TV. An idea from Departures (MIT).
 */
final class FlapBoard extends View {
    static final int COLS = 22, ROWS = 6;
    /** The letters on a tile's drum. An emoji the TV can draw also gets a tile, stopping there after a few letters; anything else is a blank. */
    private static final String FLAPS = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,:'!?-/&+%()#@°";
    private static final long FLIP_MS = 70, COL_DELAY_MS = 22, ROW_DELAY_MS = 45;
    /** Letters a tile shows on its way to the new one, at most: enough to look mechanical, few enough to settle fast. */
    private static final int MAX_STEPS = 6;

    /** Each tile's letter or emoji (an emoji can be several chars, so these are strings). */
    private final String[][] shown = new String[ROWS][COLS];
    /** For each tile, the letters still to show, the last being where it stops; null when still. */
    private final String[][][] queue = new String[ROWS][COLS][];
    private final int[][] step = new int[ROWS][COLS];
    private final long[][] startAt = new long[ROWS][COLS];
    private final int[] rowColor = new int[ROWS];
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG), flap = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), emoji = new Paint(Paint.ANTI_ALIAS_FLAG), hinge = new Paint();
    private final BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
    private final RectF cell = new RectF();
    private float tileW, tileH, gap, left, top;

    FlapBoard(Context c, Typeface font) {
        super(c);
        for (String[] r : shown) java.util.Arrays.fill(r, " ");
        java.util.Arrays.fill(rowColor, 0xFFEDEDED);
        tile.setColor(0xFF1B1B1D);
        flap.setColor(0xFF232326);
        hinge.setColor(0xFF000000);
        text.setTypeface(font);
        text.setFontVariationSettings("'wght' 560");
        text.setTextAlign(Paint.Align.CENTER);
        emoji.setTextAlign(Paint.Align.CENTER);
    }

    /** Up to six lines, each centred on its row, in its colour; the tiles that differ start flipping. */
    void show(String[] lines, int[] colors) {
        long now = SystemClock.uptimeMillis();
        for (int r = 0; r < ROWS; r++) {
            List<String> line = fit(r < lines.length ? lines[r] : null);
            int pad = (COLS - line.size()) / 2;
            if (r < colors.length) rowColor[r] = colors[r];
            for (int c = 0; c < COLS; c++) {
                String want = c >= pad && c - pad < line.size() ? line.get(c - pad) : " ";
                String from = queue[r][c] != null ? queue[r][c][queue[r][c].length - 1] : shown[r][c];
                if (want.equals(from)) continue;
                queue[r][c] = path(shown[r][c], want);
                step[r][c] = 0;
                startAt[r][c] = now + c * COL_DELAY_MS + r * ROW_DELAY_MS;
            }
        }
        invalidate();
    }

    /** Every letter (not the blanks) turns once round the drum back to itself, in a wave from the top left. */
    void ripple() {
        long now = SystemClock.uptimeMillis();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (shown[r][c].equals(" ") || queue[r][c] != null) continue;
                queue[r][c] = path(shown[r][c], shown[r][c]);
                step[r][c] = 0;
                startAt[r][c] = now + c * COL_DELAY_MS + r * ROW_DELAY_MS;
            }
        }
        invalidate();
    }

    /** A row's tiles: capitals, what the board has or the TV can draw, and no more than a row holds. */
    private List<String> fit(String s) {
        List<String> tiles = new ArrayList<>();
        if (s == null) return tiles;
        String up = s.toUpperCase(Locale.ROOT);
        clusters.setText(up);   // whole emoji, including skin tones, flags and joined ones
        for (int from = clusters.first(), to = clusters.next(); to != BreakIterator.DONE; from = to, to = clusters.next()) {
            String one = up.substring(from, to);
            tiles.add(FLAPS.contains(one) || isEmoji(one) ? one : " ");
        }
        trim(tiles);
        if (tiles.size() > COLS) {
            tiles.subList(COLS, tiles.size()).clear();
            trim(tiles);
        }
        return tiles;
    }

    private static void trim(List<String> tiles) {
        while (!tiles.isEmpty() && tiles.get(0).equals(" ")) tiles.remove(0);
        while (!tiles.isEmpty() && tiles.get(tiles.size() - 1).equals(" ")) tiles.remove(tiles.size() - 1);
    }

    private boolean isEmoji(String s) {
        return UCharacter.hasBinaryProperty(s.codePointAt(0), UProperty.EMOJI) && emoji.hasGlyph(s);
    }

    /**
     * The tiles from one to the next, forward round the drum as a real one turns, thinned to MAX_STEPS; a whole turn when
     * they're the same. Emoji aren't on the drum: leaving one starts from the blank, and reaching one ends a short run on it.
     */
    private static String[] path(String from, String to) {
        int a = Math.max(0, FLAPS.indexOf(from)), b = FLAPS.indexOf(to), n = FLAPS.length();
        int dist = b < 0 ? MAX_STEPS : ((b - a) % n + n) % n;
        if (dist == 0) dist = n;
        int steps = Math.min(dist, MAX_STEPS);
        String[] p = new String[steps];
        for (int i = 1; i <= steps; i++) p[i - 1] = String.valueOf(FLAPS.charAt((a + Math.round((float) dist * i / steps)) % n));
        if (b < 0) p[steps - 1] = to;
        return p;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        tileW = w * 0.88f / COLS;
        tileH = tileW * 1.4f;
        if (tileH * ROWS > h * 0.8f) { tileH = h * 0.8f / ROWS; tileW = tileH / 1.4f; }
        gap = tileW * 0.09f;
        left = (w - tileW * COLS) / 2;
        top = (h - tileH * ROWS) / 2;
        text.setTextSize(tileH * 0.66f);
        emoji.setTextSize(tileW * 0.72f);   // emoji are wider than letters: this keeps them inside the tile
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        boolean moving = false;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                cell.set(left + c * tileW + gap / 2, top + r * tileH + gap / 2, left + (c + 1) * tileW - gap / 2, top + (r + 1) * tileH - gap / 2);
                String[] q = queue[r][c];
                if (q == null || now < startAt[r][c]) {
                    drawTile(canvas, shown[r][c], rowColor[r], 1f, 0);
                    moving |= q != null;
                    continue;
                }
                float p = (now - startAt[r][c]) / (float) FLIP_MS;
                while (p >= 1f && q != null) {   // a slow frame: catch up a letter or more
                    shown[r][c] = q[step[r][c]];
                    if (++step[r][c] == q.length) { queue[r][c] = q = null; break; }
                    startAt[r][c] += FLIP_MS;
                    p -= 1f;
                }
                if (q == null) { drawTile(canvas, shown[r][c], rowColor[r], 1f, 0); continue; }
                moving = true;
                String next = q[step[r][c]], old = shown[r][c];
                drawTile(canvas, next, rowColor[r], 1f, -1);   // the new letter's top, uncovered
                drawTile(canvas, old, rowColor[r], 1f, 1);     // the old letter's bottom, still showing
                // The flap: the old top falling to the hinge, then the new bottom falling from it.
                if (p < 0.5f) drawTile(canvas, old, rowColor[r], 1f - 2 * p, -1);
                else drawTile(canvas, next, rowColor[r], 2 * p - 1, 1);
            }
        }
        if (moving) postInvalidateOnAnimation();
    }

    /** One tile, or its top (half -1) or bottom (half 1) half, squashed toward the hinge by scaleY. */
    private void drawTile(Canvas canvas, String ch, int color, float scaleY, int half) {
        float mid = cell.centerY();
        canvas.save();
        if (half < 0) canvas.clipRect(cell.left, cell.top, cell.right, mid);
        if (half > 0) canvas.clipRect(cell.left, mid, cell.right, cell.bottom);
        if (scaleY < 1f) canvas.scale(1f, Math.max(0.001f, scaleY), cell.centerX(), mid);
        float round = tileW * 0.08f;
        canvas.drawRoundRect(cell, round, round, scaleY < 1f ? flap : tile);
        if (!ch.equals(" ")) {
            Paint p = FLAPS.contains(ch) ? text : emoji;
            p.setColor(color);   // a colour emoji keeps its own colours; a plain symbol takes the row's
            Paint.FontMetrics fm = p.getFontMetrics();
            canvas.drawText(ch, cell.centerX(), mid - (fm.ascent + fm.descent) / 2, p);
        }
        canvas.restore();
        canvas.drawRect(cell.left, mid - gap / 6, cell.right, mid + gap / 6, hinge);
    }
}
