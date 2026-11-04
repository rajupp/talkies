#!/bin/bash
# Updates Talkies on the TV from its latest GitHub release, over adb. This is the fallback when the in-app updater
# downloads an APK but Android's install-confirmation screen does not appear or cannot be completed. adb installs
# through Android's package manager, and -r replaces the app while keeping its app data.
# Usage: ./update.sh [tv-ip:port]   (or TALKIES_TV, or the .tv file, like the other scripts)
set -euo pipefail
. "$(dirname "$0")/tv-address.sh"
DEV="${1:-$(tv_default)}"; need_tv
HERE="$(cd "$(dirname "$0")" && pwd)"
RELEASE="$(curl -fsSL https://api.github.com/repos/rajupp/talkies/releases/latest)"
TAG="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])' <<<"$RELEASE")"
ASSET="$(python3 -c 'import json,sys; print(next((a.get("digest","")+" "+str(a.get("size",0))) for a in json.load(sys.stdin)["assets"] if a["name"]=="talkies.apk"), "")' <<<"$RELEASE")"
read -r DIGEST SIZE <<<"$ASSET"
[[ "$TAG" =~ ^v[0-9A-Za-z._-]+$ ]] || { echo "!! Unexpected release tag: $TAG" >&2; exit 1; }
[[ "$DIGEST" =~ ^sha256:[0-9a-fA-F]{64}$ && "$SIZE" =~ ^[1-9][0-9]*$ && "$SIZE" -le 20971520 ]] \
  || { echo "!! The release APK has no valid SHA-256 digest or size" >&2; exit 1; }
APK="$HERE/build/talkies-$TAG.apk"
mkdir -p "$HERE/build"
echo "== Downloading Talkies $TAG"
curl -fL --progress-bar -o "$APK" "https://github.com/rajupp/talkies/releases/download/$TAG/talkies.apk"
ACTUAL_SIZE="$(wc -c < "$APK" | tr -d ' ')"
[ "$ACTUAL_SIZE" = "$SIZE" ] || { echo "!! APK size doesn't match GitHub's release metadata" >&2; exit 1; }
ACTUAL_DIGEST="$(python3 -c 'import hashlib,sys; h=hashlib.sha256(); f=open(sys.argv[1],"rb"); [h.update(b) for b in iter(lambda:f.read(65536),b"")]; print(h.hexdigest())' "$APK")"
[ "$ACTUAL_DIGEST" = "${DIGEST#sha256:}" ] || { echo "!! APK SHA-256 doesn't match GitHub's release metadata" >&2; exit 1; }

SIGNER="$(command -v apksigner || true)"
AAPT="$(command -v aapt || true)"
if [ -z "$SIGNER" ] || [ -z "$AAPT" ]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$SDK" ]; then
    for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
      if [ -d "$candidate/build-tools" ]; then SDK="$candidate"; break; fi
    done
  fi
  if [ -n "$SDK" ] && [ -d "$SDK/build-tools" ]; then
    BUILDTOOLS="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | tail -1 || true)"
    if [ -n "$BUILDTOOLS" ]; then
      [ -n "$SIGNER" ] || SIGNER="${BUILDTOOLS}apksigner"
      [ -n "$AAPT" ] || AAPT="${BUILDTOOLS}aapt"
    fi
  fi
fi
[ -x "$SIGNER" ] || { echo "!! apksigner is required (Android SDK Build Tools); set ANDROID_HOME or add it to PATH" >&2; exit 1; }
[ -x "$AAPT" ] || { echo "!! aapt is required (Android SDK Build Tools); set ANDROID_HOME or add it to PATH" >&2; exit 1; }
EXPECTED_CERT="$(sed -n 's/.*RELEASE_CERT = "\([0-9a-f]*\)".*/\1/p' "$HERE/app/src/main/java/com/techpp/glass/SelfUpdate.java" | head -1)"
[[ "$EXPECTED_CERT" =~ ^[0-9a-f]{64}$ ]] || { echo "!! Couldn't read Talkies' pinned release certificate" >&2; exit 1; }
VERIFY="$("$SIGNER" verify --print-certs "$APK")" || { echo "!! APK signature verification failed" >&2; exit 1; }
ACTUAL_CERT="$(printf '%s\n' "$VERIFY" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr '[:upper:]' '[:lower:]')"
[ "$ACTUAL_CERT" = "$EXPECTED_CERT" ] || { echo "!! APK isn't signed with Talkies' pinned release key" >&2; exit 1; }
BADGING="$("$AAPT" dump badging "$APK")" || { echo "!! Couldn't read the APK manifest" >&2; exit 1; }
PACKAGE="$(printf '%s\n' "$BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
VERSION="$(printf '%s\n' "$BADGING" | sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" | head -1)"
[ "$PACKAGE" = "com.techpp.glass" ] || { echo "!! APK package is '$PACKAGE', expected com.techpp.glass" >&2; exit 1; }
[ "$VERSION" = "${TAG#v}" ] || { echo "!! APK version is '$VERSION', expected ${TAG#v}" >&2; exit 1; }

adb connect "$DEV" >/dev/null || true
echo "== Installing on $DEV"
adb -s "$DEV" install -r "$APK"
echo "Done: Talkies $TAG. Your settings stay as they are."
