# Building Talkies

For changing Talkies, or installing it from source. To just install it, the ready APK in the
[README](../README.md#install) is simpler. Everything Talkies does is in the [guide](guide.md).

- [What you need](#what-you-need)
- [Connect to the TV](#connect-to-the-tv)
- [Build and install](#build-and-install)
- [Install by hand](#install-by-hand)
- [Scripts](#scripts)
- [Signing and releases](#signing-and-releases)

## What you need

| | |
|---|---|
| **The TV** | Android TV or Google TV, **Android 8.0 (API 26) or newer**. The install script checks this before building. |
| **A computer** | On the same Wi-Fi as the TV. macOS or Linux run the scripts as they are; on Windows use WSL, or the commands under [Install by hand](#install-by-hand). |
| **Tools** | `adb` (Android platform tools), JDK 17, and the Android SDK command-line tools with platform 35. |

Installing the tools on macOS with [Homebrew](https://brew.sh):

```bash
brew install openjdk@17
brew install --cask android-platform-tools android-commandlinetools
sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

On Linux, install JDK 17 and `adb` from your package manager and the
[command-line tools](https://developer.android.com/studio#command-line-tools-only) from Google, then run the same
`sdkmanager` line. If yours aren't in Homebrew's paths, set `JAVA_HOME` and `ANDROID_HOME`. The first build downloads
Gradle, so it takes a few minutes.

## Connect to the TV

1. **On the TV**, turn on developer options (Settings → Device Preferences → About → press **Build** seven times),
   then **USB debugging** / **Network debugging** in Developer options. Note the TV's IP address
   (Settings → Network & Internet → your Wi-Fi network), something like `192.168.0.105`.

   **No Network debugging, only Wireless debugging?** Newer TVs (Android 11 and later) may only offer *Wireless
   debugging*, which needs pairing once. Open it on the TV, choose **Pair device with pairing code**, and on the
   computer run `adb pair` with the address and code it shows (the pairing port is different from the connect
   port):
   ```bash
   adb pair 192.168.1.20:37123
   ```
   Then use the address and port shown under **Wireless debugging** itself (not 5555) in the steps below.

   > **Every `192.168.1.20` here is an example.** Put your own TV's address in its place; `:5555` stays as it is
   > (it's the port network debugging uses).
2. **Connect once** and accept "Allow debugging?" on the TV:
   ```bash
   adb connect 192.168.1.20:5555
   ```
3. **Tell the scripts which TV** (this file stays on your computer; git ignores it):
   ```bash
   echo 192.168.1.20:5555 > .tv
   ```
   For the `adb` commands in these docs, also `export TALKIES_TV=192.168.1.20:5555`.

## Build and install

1. **Choose your dock** in `app/src/main/res/values/dock.xml` (package names; list yours with the command under
   [Rearrange apps](guide.md#rearrange-apps)). You can rearrange on the TV later.
2. **Build and install**: `./build-and-install.sh`. It makes Talkies the home screen and grants the permissions it
   can (location for the Wi-Fi name, TV listings for Watch Next), and puts back keys saved by the key scripts.
3. **On the TV**, finish the [first-time setup](../README.md#first-time-setup) (Accessibility and notification
   access).
4. **Keys and your weather area**: on the TV in Talkies settings → **Keys and location**, or from here:
   ```bash
   ./set-tmdb-key.sh                    # picks (paste the key when it asks)
   ./set-location.sh "Camden, London"   # weather: a place, or "lat,lon"
   ```
   Keys are stored on the TV and in git-ignored files next to the scripts; your location only on the TV. Neither is
   in the app or the repository.

## Install by hand

What `build-and-install.sh` does, one command at a time (also the way on Windows without WSL: use `gradlew.bat`).

```bash
./gradlew assembleRelease                                   # → app/build/outputs/apk/release/app-release.apk
adb connect 192.168.1.20:5555
adb -s 192.168.1.20:5555 install -r app/build/outputs/apk/release/app-release.apk
adb -s 192.168.1.20:5555 shell cmd package set-home-activity --user 0 com.techpp.glass/.HomeActivity
adb -s 192.168.1.20:5555 shell pm grant com.techpp.glass android.permission.ACCESS_FINE_LOCATION   # Wi-Fi name
adb -s 192.168.1.20:5555 shell pm grant com.techpp.glass android.permission.READ_TV_LISTINGS       # Watch Next
```

Optional keys are plain text files the app reads from its folder on the TV:

```bash
adb -s 192.168.1.20:5555 shell mkdir -p /sdcard/Android/data/com.techpp.glass/files
adb -s 192.168.1.20:5555 push tmdb_key.txt /sdcard/Android/data/com.techpp.glass/files/
```

**Google TV only:** leave its launcher and setup wizard (`com.google.android.tungsten.setupwraith`) on, although both
answer Home: Talkies' Accessibility switch keeps the Home button on Talkies. The screensaver needs the wizard for your
Google Photos. More in the guide: [Google TV's setup wizard](guide.md#google-tvs-setup-wizard).

## Scripts

```bash
./build-and-install.sh                 # optimised build (about 200 KB, no libraries), install, set as home
./update.sh                            # the latest release over adb, with app data kept
./restore.sh                           # the TV's own home screen back, Talkies removed
./restore.sh --keep-talkies            # the TV's own home screen back, Talkies kept
./switch-home.sh list|talkies|stock    # choose the home screen (see the guide)
./set-region.sh GB                     # use another country for picks and scores, like Talkies settings → Country (--remove: the TV's own)
./set-tmdb-key.sh / set-location.sh   # key and weather area (--remove clears one)
./backup-settings.sh                   # copy Talkies' settings from the TV to talkies-backup.json here
./restore-settings.sh                  # put them back (after reinstalling, or on another TV)
./release-apk.sh                       # maintainer only: attach the signed talkies.apk to this version's release
```

Every script takes the TV's address as an optional last argument; otherwise it uses `TALKIES_TV` or the `.tv` file.
The build script uses Homebrew's paths unless `JAVA_HOME` / `ANDROID_HOME` say otherwise.

**Back up your settings:** `./backup-settings.sh` saves Talkies settings, the dock and app order, hidden apps, remote
keys, channels, football leagues, the keys and the weather area (and your `wallpaper.jpg`) to `talkies-backup.json`
next to the scripts. After an uninstall, or on another TV, install Talkies, open it once, and run
`./restore-settings.sh`: it starts again with everything as it was. The picks, scores and weather aren't in it; they
fetch again. The file holds your keys, so git ignores it; keep it private.

**Screens:** the layout is drawn for a 960 × 540 dp screen and scales itself to any TV's width, so it fills the
screen the same way whatever density the TV reports.

## Signing and releases

- **Your build** is signed with your computer's debug key. A build from another computer, or the release APK, can't
  install over it: uninstall first (back up your settings before). It can't take updates from Talkies settings →
  Updates either; it's only told when a new version is out.
- **The release APK** (`talkies.apk` on each release) is signed with the project's key, which only the maintainer
  has (`release.properties`, never in git). Talkies only installs updates signed with that key.
- **Making a release** (maintainer): bump `versionCode` and `versionName` in `app/build.gradle` and the CHANGELOG,
  write the short notes Talkies shows after the update in `app/src/main/assets/whats_new.md` (a bold name, one
  sentence, where to find it),
  commit, tag `vX.Y.Z`, push, `gh release create vX.Y.Z`, then `./release-apk.sh` builds, checks the signature and
  attaches `talkies.apk`.
- Talkies uses an accessibility service, which is fine for sideloading but not allowed on Google Play for this
  purpose.
