#!/bin/bash
# Updates Talkies on the TV from its latest GitHub release, over adb. For TVs and boxes that can't install an app
# themselves: some operator set-top boxes have no package installer, so the bell downloads the update and then no
# Install screen opens, and Downloader or a USB drive can't finish it either. adb goes straight to Android's package
# manager, and -r replaces the app with your settings, dock, keys and wallpaper as they are.
# Usage: ./update.sh [tv-ip:port]   (or TALKIES_TV, or the .tv file, like the other scripts)
set -euo pipefail
. "$(dirname "$0")/tv-address.sh"
DEV="${1:-$(tv_default)}"; need_tv
HERE="$(cd "$(dirname "$0")" && pwd)"
TAG="$(curl -fsSL https://api.github.com/repos/rajupp/talkies/releases/latest \
  | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' | head -1)"
[ -n "$TAG" ] || { echo "!! Couldn't ask GitHub for the latest release" >&2; exit 1; }
APK="$HERE/build/talkies-$TAG.apk"
mkdir -p "$HERE/build"
echo "== Downloading Talkies $TAG"
curl -fL --progress-bar -o "$APK" "https://github.com/rajupp/talkies/releases/download/$TAG/talkies.apk"
adb connect "$DEV" >/dev/null || true
echo "== Installing on $DEV"
adb -s "$DEV" install -r "$APK"
echo "Done: Talkies $TAG. Your settings stay as they are."
