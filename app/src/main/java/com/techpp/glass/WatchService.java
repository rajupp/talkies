package com.techpp.glass;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records what video apps play, for the "Recently watched" tab: Android only hands media sessions to an app that holds
 * notification access, so this listener exists to hold that access. Of the notifications themselves it reads only the
 * Play Store's "updates available" ones, for the bell (Updates); everything else is ignored unread.
 * Any app is recorded if it reports a real title; apps that send none (Netflix) or only their own name (Prime Video
 * sends "PrimeVideo") are skipped by that rule, and music apps by the list below.
 * Event-driven (session callbacks), no polling. A title is saved after it has played for 20 seconds, which skips
 * ads, trailers and autoplay previews. How far in it got is noted when it pauses, changes or its session ends, for the
 * card's progress bar and for opening a YouTube video where you left it.
 */
public class WatchService extends NotificationListenerService {
    /** Music and system sessions: not "watched". */
    static final String[] SKIP = {"com.spotify.tv.android", "com.google.android.youtube.tvmusic",
            "com.android.bluetooth", "com.google.android.katniss", "com.techpp.glass"};
    private static final long MIN_PLAY_MS = 20_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<MediaController, Watch> watching = new HashMap<>();
    private MediaSessionManager sessions;
    private final MediaSessionManager.OnActiveSessionsChangedListener onChange = this::onSessions;

    /** One app's session: what it's playing and the pending "it's been playing 20 s" check. */
    private final class Watch extends MediaController.Callback {
        final MediaController c;
        String title = "";
        final Runnable save = this::save;
        /** The title last saved, as the app calls it and as the list knows it; its last position report. */
        String savedRaw = "", savedAs;
        PlaybackState last;
        long duration;

        Watch(MediaController c) { this.c = c; }

        @Override public void onMetadataChanged(MediaMetadata m) { update(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { update(); }
        @Override public void onSessionDestroyed() { notePosition(); stop(this); }

        void update() {
            String now = text(c.getMetadata(), MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
            boolean playing = c.getPlaybackState() != null && c.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
            if (now.equals(savedRaw)) track();
            if (!playing || now.isEmpty() || isAppName(c.getPackageName(), now)) {
                notePosition();
                main.removeCallbacks(save);
                title = "";
                return;
            }
            if (!now.equals(title)) {  // a new title started: wait 20 s before saving it
                notePosition();
                if (!now.equals(savedRaw)) { savedRaw = ""; last = null; }
                title = now;
                logKeys(c.getPackageName(), c.getMetadata());
                Log.i("GlassRecent", c.getPackageName() + " playing a new title; saving it after 20 s");
                main.removeCallbacks(save);
                main.postDelayed(save, MIN_PLAY_MS);
            }
        }

        void save() {
            MediaMetadata m = c.getMetadata();
            if (m == null || !title.equals(text(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE))) return;
            String sub = text(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST,
                    MediaMetadata.METADATA_KEY_ALBUM);
            Bitmap art = art(m);
            savedAs = RecentStore.record(WatchService.this, c.getPackageName(), title, sub, art);
            savedRaw = title;
            track();
        }

        /** Keeps the saved title's latest position. A stopped app often reports 0, which isn't where you were. */
        void track() {
            PlaybackState s = c.getPlaybackState();
            if (s == null || s.getPosition() <= 0
                    || s.getState() != PlaybackState.STATE_PLAYING && s.getState() != PlaybackState.STATE_PAUSED) return;
            last = s;
            MediaMetadata m = c.getMetadata();
            if (m != null) duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }

        void notePosition() {
            if (savedRaw.isEmpty() || savedAs == null || last == null) return;
            long at = last.getPosition();
            if (last.getState() == PlaybackState.STATE_PLAYING && last.getLastPositionUpdateTime() > 0) {
                at += (long) ((SystemClock.elapsedRealtime() - last.getLastPositionUpdateTime()) * last.getPlaybackSpeed());
            }
            RecentStore.notePosition(WatchService.this, c.getPackageName(), savedAs, at, duration);
        }
    }

    @Override
    public void onListenerConnected() {
        Log.i("GlassRecent", "Notification access on; watching video apps");
        attachSessions();
        scanUpdates();
    }

    /**
     * Starts following media sessions. Just after a reinstall Android can bind this listener before it has confirmed
     * its access, and refuse the sessions; it then tries again every 30 seconds until they're allowed (before this,
     * the refusal crashed the whole app, home screen included, on start).
     */
    private void attachSessions() {
        main.removeCallbacks(retryAttach);
        MediaSessionManager m = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, WatchService.class);
        try {
            m.addOnActiveSessionsChangedListener(onChange, me, main);
            sessions = m;
            onSessions(m.getActiveSessions(me));
        } catch (SecurityException e) {
            Log.w("GlassRecent", "Media sessions not allowed yet, trying again in 30 s: " + e.getMessage());
            main.postDelayed(retryAttach, 30_000);
        }
    }

    private final Runnable retryAttach = this::attachSessions;

    @Override public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) { scanUpdates(); }

    @Override public void onNotificationRemoved(android.service.notification.StatusBarNotification sbn) { scanUpdates(); }

    private void scanUpdates() {
        try { Updates.update(getActiveNotifications()); } catch (Exception e) { Log.w("GlassUpdates", "Couldn't read: " + e); }
    }

    @Override
    public void onListenerDisconnected() {
        main.removeCallbacks(retryAttach);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(onChange);
        for (Watch w : watching.values().toArray(new Watch[0])) stop(w);
    }

    private void onSessions(List<MediaController> list) {
        if (list == null) return;
        for (MediaController c : list) {
            if (!watched(c.getPackageName())) continue;
            boolean known = false;
            for (MediaController k : watching.keySet()) if (k.getSessionToken().equals(c.getSessionToken())) known = true;
            if (known) continue;
            Log.i("GlassRecent", "Listening to " + c.getPackageName());
            Watch w = new Watch(c);
            watching.put(c, w);
            c.registerCallback(w, main);
            w.update();
        }
    }

    private void stop(Watch w) {
        main.removeCallbacks(w.save);
        w.c.unregisterCallback(w);
        watching.remove(w.c);
    }

    static boolean watched(String pkg) {
        for (String a : SKIP) if (a.equals(pkg)) return false;
        return true;
    }

    /** True when the "title" is just the app's name, e.g. Prime Video's "PrimeVideo". */
    private boolean isAppName(String pkg, String title) {
        try {
            String app = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
            String t = squash(title), a = squash(app);
            // Non-Latin titles (Hindi, Tamil…) squash to nothing: those are real titles, never the app's name.
            return !t.isEmpty() && (t.equals(a) || a.contains(t));
        } catch (Exception e) {
            return false;
        }
    }

    private static String squash(String s) { return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    static String text(MediaMetadata m, String... keys) {
        if (m == null) return "";
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.toString().trim().length() > 0) return v.toString().trim();
        }
        return "";
    }

    /** What the app shares besides the title (keys, links, picture sizes), for working out artwork. */
    private static void logKeys(String pkg, MediaMetadata m) {
        if (m == null) return;
        StringBuilder b = new StringBuilder(pkg).append(" metadata:");
        for (String k : m.keySet()) {
            Bitmap bm = m.getBitmap(k);
            CharSequence t = bm == null ? m.getText(k) : null;
            b.append(' ').append(k.replace("android.media.metadata.", "")).append('=');
            if (bm != null) b.append(bm.getWidth()).append('x').append(bm.getHeight());
            else if (t != null) b.append('"').append(t.length() > 90 ? t.subSequence(0, 90) + "…" : t).append('"');
            else b.append(m.getLong(k));
        }
        Log.i("GlassRecent", b.toString());
    }

    static Bitmap art(MediaMetadata m) {
        for (String k : new String[]{MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
            Bitmap b = m.getBitmap(k);
            if (b != null) return b;
        }
        return null;
    }
}
