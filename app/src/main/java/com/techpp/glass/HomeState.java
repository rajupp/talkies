package com.techpp.glass;

import android.os.SystemClock;

/** What the accessibility service needs to know about the Home screen, and Home pressed twice. Main thread, apart from the flag. */
final class HomeState {
    /** Talkies in front: pressing Home leaves no app. */
    static volatile boolean inFront;

    private HomeState() { }

    // Home pressed twice. The first press is already bringing Talkies up, so the second only has to reach it: it's
    // shown now if Talkies is in front, else as Talkies comes to the front. Starting Talkies again for it could lose
    // to the first press's own Home, which would close it.
    private static final long RECENTS_WAIT_MS = 2_000;
    private static long recentsAskedAt;
    private static Runnable recentsHere;

    /** The Home screen, while it exists, for Recent apps to open on. */
    static void recentsHere(Runnable r) { recentsHere = r; }

    static void askRecents() {
        if (inFront && recentsHere != null) recentsHere.run();
        else recentsAskedAt = SystemClock.elapsedRealtime();
    }

    /** Was Recent apps asked for and not shown yet? Answers once. */
    static boolean takeRecentsAsked() {
        boolean asked = recentsAskedAt != 0 && SystemClock.elapsedRealtime() - recentsAskedAt < RECENTS_WAIT_MS;
        recentsAskedAt = 0;
        return asked;
    }
}
