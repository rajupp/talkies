package com.techpp.glass;

import android.os.SystemClock;

/** What the accessibility service needs to know about the Home screen, and Home pressed twice. Main thread, apart from the flag. */
final class HomeState {
    /** Talkies in front: pressing Home leaves no app. */
    static volatile boolean inFront;

    private HomeState() { }

    /**
     * Talkies opened one of the TV's settings screens, and they're part of its built-in launcher (Fire TV), whose
     * screens the accessibility service would otherwise take for its home screen and replace with Talkies. Until Home
     * is pressed or Talkies is back in front.
     */
    static volatile boolean inSettings;

    static void openingSettings(android.content.Context c, android.content.Intent screen) {
        android.content.pm.PackageManager pm = c.getPackageManager();
        android.content.pm.ResolveInfo r = pm.resolveActivity(screen, 0);
        if (r == null || r.activityInfo == null) return;
        android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME).setPackage(r.activityInfo.packageName);
        if (!pm.queryIntentActivities(home, 0).isEmpty()) inSettings = true;
    }

    // Home pressed twice. The first press is already bringing Talkies up, so the second only has to reach it: it's
    // shown now if Talkies is in front, else as Talkies comes to the front. Starting Talkies again for it could lose
    // to the first press's own Home, which would close it.
    private static final long RECENTS_WAIT_MS = 2_000;
    private static long recentsAskedAt;
    private static Runnable recentsHere;

    /** The Home screen, while it exists, for Recent apps to open on. */
    static void recentsHere(Runnable r) { recentsHere = r; }

    static void askRecents() {
        if (inFront && recentsHere != null) {
            // Runs inside the accessibility service's key handling: a slip here mustn't take the service down.
            try {
                recentsHere.run();
            } catch (RuntimeException e) {
                android.util.Log.w("GlassHome", "Recent apps didn't open", e);
            }
        } else recentsAskedAt = SystemClock.elapsedRealtime();
    }

    /** Was Recent apps asked for and not shown yet? Answers once. */
    static boolean takeRecentsAsked() {
        boolean asked = recentsAskedAt != 0 && SystemClock.elapsedRealtime() - recentsAskedAt < RECENTS_WAIT_MS;
        recentsAskedAt = 0;
        return asked;
    }
}
