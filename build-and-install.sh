#!/bin/bash
# Builds Talkies, installs it on the TV and makes it the home screen.
# Usage: ./build-and-install.sh [tv-ip:port]
set -euo pipefail
. "$(dirname "$0")/tv-address.sh"
DEV="${1:-$(tv_default)}"; need_tv
PKG=com.techpp.glass
HERE="$(cd "$(dirname "$0")" && pwd)"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"

adb connect "$DEV" >/dev/null || true
# Talkies needs Android 8.0 (API 26) or newer; say so before building rather than failing at install.
SDK="$(adb -s "$DEV" shell -n getprop ro.build.version.sdk | tr -d '\r')"
if [ -n "$SDK" ] && [ "$SDK" -lt 26 ]; then
  echo "!! This TV runs Android API $SDK; Talkies needs API 26 (Android 8.0) or newer." >&2
  exit 1
fi

echo "== Building"
(cd "$HERE" && ./gradlew -q assembleRelease)
APK="$HERE/app/build/outputs/apk/release/app-release.apk"

echo "== Installing on $DEV"
adb -s "$DEV" install -r "$APK"

# Lets the Wi-Fi pill show the network name (Android hides SSIDs from apps without location).
adb -s "$DEV" shell -n pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
# Lets Recently watched read Android TV's Watch Next list (Netflix, Prime Video…).
adb -s "$DEV" shell -n pm grant $PKG android.permission.READ_TV_LISTINGS || true

# Trending picks: put back the TMDB key saved by set-tmdb-key.sh (e.g. after a fresh install).
if [ -f "$HERE/tmdb_key.txt" ]; then
  adb -s "$DEV" shell -n mkdir -p /sdcard/Android/data/$PKG/files
  adb -s "$DEV" push "$HERE/tmdb_key.txt" /sdcard/Android/data/$PKG/files/tmdb_key.txt >/dev/null
fi

echo "== Setting as home"
adb -s "$DEV" shell -n cmd package set-home-activity --user 0 $PKG/.HomeActivity || true
# Google TV's launcher and setup wizard stay on, though both claim HOME: Talkies' accessibility service (Settings →
# Accessibility → Talkies) sends the Home button and the Google TV home screen back to Talkies. The Assistant opens
# its results there, and the Ambient mode screensaver needs the wizard for your Google Photos (without it, it shows
# Google's art). Earlier versions of this script turned the wizard off: put it back.
WIZARD=com.google.android.tungsten.setupwraith
if adb -s "$DEV" shell -n pm list packages -d $WIZARD | grep -q $WIZARD; then
  adb -s "$DEV" shell -n pm enable $WIZARD >/dev/null 2>&1 && echo "Turned Google's setup wizard back on (your screensaver's photos need it)"
fi
adb -s "$DEV" shell -n pm enable com.google.android.apps.tv.launcherx >/dev/null 2>&1 || true
if ! adb -s "$DEV" shell -n settings get secure enabled_accessibility_services | grep -q "$PKG"; then
  echo "!! Switch on Settings → Device Preferences → Accessibility → Talkies, or Home will open Google TV."
fi
echo "Home apps on the TV:"
adb -s "$DEV" shell -n cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep / || true
echo "Default home now: $(adb -s "$DEV" shell -n cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
# After an install Android takes a few seconds to reconnect the accessibility service; until then Home would land on
# Google TV. Wait for it (up to 20 s) before pressing Home.
for _ in $(seq 1 20); do
  adb -s "$DEV" shell -n dumpsys accessibility 2>/dev/null | grep -q "Bound services:.*Talkies" && break
  sleep 1
done
adb -s "$DEV" shell -n input keyevent KEYCODE_HOME
