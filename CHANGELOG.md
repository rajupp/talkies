# Changelog

Versions are numbered *major.minor.fix*. While Talkies is in beta they start with 0: a new minor number brings new
features, a new fix number only fixes. The version shows in Talkies settings → About.

## Unreleased

### New
- **Emoji on the Board.** Hearts and other emoji in your Screensaver message now get a tile of their own, instead of a
  blank.
- **A live score over TV.** Hold OK on a match → **Pin over TV**: a small card stays in a corner, updating while you
  watch anything (not on Talkies' Home or the screensaver), and leaves about five minutes after the match ends. Control Center unpins it. An idea from Gulam.

### Changed
- **Rows the screen cuts off fade away.** Moving down the page, the strips of the rows above and below fade out, and
  come back once they fit. An idea from dossantosee in Discussion #17.
- **Many more jobs for Remote keys.** A button can now go to Previous app, Resume watching (the first Continue card) or
  one input you choose, control Play / pause or Mute, or open Talkies settings, Sound output, Bluetooth, the Screensaver
  or Turn off, on a press or when held. Asked for by Abdullah AlQudaihi.
- **All apps button.** Talkies settings → **All apps button** hides the round button at the end of the dock, so the apps
  fill its width. All apps is still below, further down the page. Asked for by athul.
- **TV's screensaver.** A new row in Talkies settings makes Talkies the TV's screensaver if it has the
  `WRITE_SECURE_SETTINGS` grant, and shows how otherwise, for TVs whose settings don't list it. Reported on a Sony Bravia.

### Fixed
- **Continue shows what you just watched.** Today's titles now come before cards months old, YouTube can show up to
  four from the last two days, and each app keeps its own share of the saved list. A YouTube card opens that video,
  and a JioHotstar title opened from its card keeps the show's name and picture.

## 1.2.8 — 4 November 2026

### New
- **A welcome card on a new install.** The first time Home shows, if the Home button isn't set up yet, a small card
  takes you straight to its switch. Later closes it for good; Talkies settings → Finish setup stays.
- **A device turns on.** With Home on screen, a console or box on HDMI that switches on gets a card: "PlayStation 5
  is on · Switch to HDMI 2". Talkies settings → **A device turns on** turns it off. An idea from Home Deck.
- **Edit inputs.** Talkies settings → **Edit inputs** names a port ("Living room box") or hides it (a soundbar's ARC
  port, Live TV without an aerial). An idea from Home Deck, and from AndyVRD in Discussion #22.
- **Talkies screensaver.** A dim clock with seconds, the date and the weather, moving every 20 seconds so nothing burns in. Pick
  it in the TV's Settings → Screen saver. Talkies settings → **Screensaver** puts your wallpaper (dimmed, drifting) or
  the picks (a new one each minute) behind it. An idea from Home Deck.
- **Board screensaver.** Talkies settings → **Screensaver → Board**: a split-flap board, like a station's departures
  board, with the time to the second, the date, the weather and the day's high and low. Only the letters that change
  flip, and every 15 seconds a wave turns them all once round. An idea from Departures.
- **Screensaver message.** Two short lines typed on your phone ("Dinner at 8"), shown on the screensaver. Talkies
  settings → **Screensaver message** shows the QR code. An idea from Departures.
- **Quiet hours.** The screensaver dims at night, 11 PM to 7 AM unless you pick other hours (or Off) in Talkies
  settings → **Quiet hours**. An idea from Departures.
- **Rain coming.** The weather line adds "Rain around 4 PM" when rain is likely in the next hours and it's dry now.
  An idea from Departures.
- **Weather without setup.** Until you set a location, Talkies goes by your internet address: roughly your city, looked
  up once a week (ipwho.is, then ipapi.co or geojs.io). Talkies settings → Keys and location → **Weather location**
  shows "Near Bengaluru"; set a place for better weather, or Remove to turn it off. A location you already set stays
  in charge, and removing one still leaves just the time and date. An idea from tvOS Launcher, Aerial and Plume.
- **A backup for the weather.** If Open-Meteo doesn't answer, MET Norway's forecast fills in. An idea from tvOS Launcher.
- **What's playing on Home.** While music plays, the line under the icons shows the song, the artist and the app.
  An idea from Home Deck.

### Changed
- **Blurred art as the background.** Talkies settings → **Background** → **Blurred art** shows the focused pick or
  Continue card's own picture, blurred, behind Home. App colour stays the default, and apps in the dock keep their
  colour either way. Asked for by AndyVRD in Discussion #22, who pointed to Free My TV.
- **Continue apps.** Talkies settings → **Continue apps** turns each app on or off for the Continue tab and the Watch
  Next row. Asked for by AndyVRD.
- **Bigger apps.** Dock size now goes down to 4 apps, with larger tiles, and Talkies settings → **All apps size** shows
  6, 5 or 4 across. Asked for by Abdullah AlQudaihi.
- **More for Remote keys.** A button can now open Control Center, TV settings or Wi-Fi, on a press or when held. Asked
  for by Abdullah AlQudaihi.
- **Refresh Continue.** Hold OK on a Continue card → **Refresh Continue** reads what the apps have reported again. Asked
  for by Abdullah AlQudaihi.
- **Focus: Lift or Ring.** Talkies settings → **Focus** → **Ring** keeps a white outline on whatever is selected, for
  busy wallpapers where the lift and shadow are hard to see. Lift is still the default. Asked for by kdkasucl.
- **StreamRadar picks without its apps.** With none of its six apps installed, Trending and New in India stayed empty and
  their tabs vanished. They now show every app's lists, and OK searches YouTube for the trailer. Reported by kdkasucl.
- **TV makers' extras hidden.** Manuals, firmware updaters, store demos and factory tools are left out of All apps
  from the start (Talkies settings → Hidden apps shows one again). The list is Home Deck's.
- **Effects: Auto notices a slow TV.** Talkies times its frames the first few times you move with the arrows, and a
  TV that turns out slow gets Lite. An idea from Home Deck.

### Fixed
- **Updates that can't install now say so.** On a box with no install screen, the update downloaded and then nothing
  happened. Talkies now says so and points to the computer fallback. Found by wpfyorg on a Jio set-top box.
- **Back to Talkies after an update.** On TVs that bring back the last app instead of the built-in launcher, updating
  from Talkies left you in that app. Talkies now comes back. Reported by Shahnawaz on a TCL TV.
- **Dock tiles no longer shrink for empty places.** With fewer apps than Dock size, the tiles are sized for the apps
  there are. Reported by AndyVRD.
- **Remote key menus in the right place.** A button set to Inputs or Recent apps, pressed while scrolled down the page,
  put the menu off the top of the screen.
- **Send from your phone on any home network.** The QR code page now also works when the router hands out
  addresses outside the usual private ranges; there it answers only phones on the TV's own network
  ([#20](https://github.com/rajupp/talkies/issues/20)). Thanks to wpfyorg for tracking down the cause.

## 1.2.7 — 3 November 2026

### New
- **Picks in India without a key.** Until you add a TMDB key, a TV in India fills Trending with the Top 10s of its
  apps (JioHotstar, Netflix, Prime Video, ZEE5, SonyLIV, Apple TV) and *New in India* with what's just come out on
  them, from StreamRadar India. OK opens the title's page in its app where StreamRadar has a link. A TMDB key takes
  over at once, and Talkies settings → **Picks from** (on TVs in India) switches between TMDB and StreamRadar any
  time. Thanks to Swakshan for pointing it out.
- **NBA and NFL scores**, from the same ESPN scoreboard as football: live games, start times and results, away
  team first ("CHI at SEA").
- **Sports.** Talkies settings → **Sports** (was Live scores) turns Cricket, Football, NBA and NFL on or off one by
  one, in any country. Cricket and football to start, plus the NBA and NFL in the US and Canada; your Live scores
  choice carries over. **Football leagues** shows only while Football is on. In the US and Canada, football is
  called Soccer.
- **Country.** Talkies settings → **Country** picks the country the picks and scores are for: Automatic (by the
  TV's time zone), common countries first, or any country A to Z. The *New in …* tab, Trending and the cricket and
  football defaults follow at once; the weather's °C/°F stays with where the TV is. Suggested on X.

### Changed
- **Talkies settings in groups.** Its rows are now under five headings, most used first: Look, For You and scores,
  Apps and inputs, Remote and power, and Talkies (keys, updates, About).

### Fixed
- **Picks on flaky networks.** A TMDB request whose connection is reset is tried up to twice more, instead of the
  whole refresh failing until the next try half an hour later.

## 1.2.6 — 2 November 2026

### New
- **Inputs on Home.** Talkies settings → Inputs on Home puts an HDMI or AV port on Home as a tile, named after the
  device on it (*PlayStation 5*, *Set-top box*): one press switches the TV to it. An idea from Aerial.
- **Open an app or input at power-on.** Talkies settings → At power-on can now open HDMI 1 (with your set-top box),
  or any app, each time the TV turns on, instead of Talkies. An idea from Aerial.
- **Effects: Lite** for slow TVs: no glow behind the apps, and focus and scrolling move at once instead of gliding.
  **Auto**, the default, picks Lite on TVs with under 1.6 GB of memory. An idea from Aerial.
- **Report a problem** (Talkies settings → About): a QR code that opens a GitHub bug report on your phone with your
  TV's model, versions and key settings already filled in.
- **What's new after an update.** The first time Home shows after Talkies updates, a card lists a few of the new
  version's changes in a line each; **See all changes** (also in Talkies settings → About → What's new) opens all of
  them in a panel on the right, with Updates at the top. Short notes written for the TV, built in, so it works
  offline. An idea from Plume.
- **A weather icon** before the weather at the top left: a small line drawing of the sky (sun, moon, partly cloudy
  by day or night, cloud, rain, snow, thunder, fog), in the text's colour.

### Changed
- **Focus without a ring.** The focused card or app grows and lifts on a soft shadow, like Plume's, instead of
  wearing a white ring with a glow. Only an app you're moving (hold OK → Move) gets a ring, a thin one.
- **Cricket needs no key.** Scores now come from ESPN, like football: no CricketData account, no daily limit, and
  they refresh every minute during a match instead of every 15. The Cricket key row, its box on the phone page and
  `set-cricket-key.sh` are gone. Thanks @Swakshan.

### Fixed
- **Finish setup no longer gets stuck on Notification access** on TVs whose settings have no such screen (many
  Android 8 TVs), where OK did nothing. It switches it on itself when Talkies has the `WRITE_SECURE_SETTINGS` grant;
  otherwise the step is marked optional, stops counting as a step left, and OK shows the one adb command that
  switches it on. Thanks @123nitin.
- **The first card in a row is no longer cut off** when it grows on focus: the first card of each channel row and
  the first column and row of All apps lost a strip at their left (and All apps' top) edge. Thanks @ag7tweets and
  @taurus77777.
- **Picks no longer go blank for a moment** as you move along Trending or New in your country: the card that grows
  kept showing its poster until its wide picture was ready, instead of an empty card on slower TVs.

### Removed
- **Free up memory and Close them all.** Android lets an app close only its own background processes (on Android 14,
  and on some TVs before it), so neither could close YouTube, Netflix or anything else, and the memory they reported
  as freed was just the TV's own coming and going. Android closes apps itself when memory runs low. Thanks
  @arjunmr97 for noticing.

## 1.2.5 — 1 November 2026

### New
- **Phone apps in All apps.** Apps with no TV version (Chrome, MacroDroid, Speedtest… installed from a file) now
  show in All apps, with a tile made from their icon. **Phone apps** in Talkies settings hides them again. An idea
  from @dossantosee.
- **Süper Lig and Turkey's football defaults** (Süper Lig, Champions League, Premier League), and a new set of
  leagues is fetched straight away. Thanks @codermert.

### Fixed
- **Holding Home opens the dashboard on streaming boxes too** (Mi Box, Chromecast, Google TV Streamer…), and where
  Talkies is the default home app. 1.2.4 only did it on TVs. Thanks @Greyxvvl.

### Changed
- **Remote keys is On by default**, so Home goes straight to Talkies without Google TV's home showing first, as with
  other launchers. If holding Volume then changes it only one step per press on your TV, turn it Off (Talkies
  settings → Remote keys). If you had turned it Off, it stays Off.
- **With Remote keys Off, Google TV's home shows for less time** before Talkies covers it.

## 1.2.4 — 31 October 2026

### Fixed
- **Wallpapers are sharp.** Your picture showed at half the screen's resolution and in reduced colour (soft, with
  banding in skies); now it's full HD, and it takes less memory than before. Thanks @AntonyBOrtega.
- **Holding Home opens Google TV's dashboard again** (Screensaver, Inputs, Picture, Wi-Fi…) on TVs where Talkies
  catches Home; a short press still opens Talkies. Thanks @Greyxvvl.

## 1.2.3 — 31 October 2026

### New
- **A wallpaper folder.** Talkies settings → Wallpaper → From this TV now lists your folders first (on the TV and USB
  drives): OK on one shows a new picture from it each day, in name order. Choosing one picture, or **Remove your
  picture**, stops it.
- **Picks languages** (Talkies settings, under For You): show Trending and New in … only in the languages you
  choose, say Tamil and English. The list offers the languages your country's picks come in; All is the default.
  An idea from @MaheshKRamasamy.

## 1.2.2 — 30 October 2026

### New
- **All apps order** (Talkies settings): keep your own order, put the apps you opened most recently first, or sort
  A to Z. Recently opened counts only apps opened through Talkies, so it still watches no other app.
- **Your own app images.** Hold OK on an app → **Change image**: a picture from your phone (QR code) or from this
  TV or a USB drive. **Original image** puts the app's own back.
- **Trending works in more countries.** Besides the 17 services Talkies knew, any installed app named like a
  streaming service TMDB lists for your country counts (Viaplay, Canal+, RTL+, Joyn, Stan…).
- **Trending without streaming apps.** With none installed, the tabs still fill, and OK plays the title's trailer in
  YouTube, with where it streams under the card. Before, the row stayed empty.
- **Hide spoilers** (Talkies settings, off by default): episodes in Continue you haven't started show the show's art
  instead of the app's still from the episode, or the still blurred. An idea from @harrydbarnes.

### Fixed
- **Home app names the launcher that has Home.** When another launcher you installed owns Home (CleanYourTV's, for
  one), Talkies steps aside for it, and the Home app row tried a settings screen Google TV doesn't have. It now names
  that launcher, and OK opens its App info page to uninstall or disable it.
- **The empty picks row says why.** With a TMDB key but no streaming app Talkies knows (and no YouTube), it said
  picks would appear once online; it now says none of your apps is one it knows.
- **Back goes up one level in menus.** In a menu opened from another (Control Center → Talkies settings → Dock
  apps), Back now returns to the menu above, on the row you came from, instead of closing everything.

## 1.2.1 — 30 October 2026

### Fixed
- **Netflix no longer reopens after Exit Netflix.** Netflix closes as OK goes down, and the button's release reached
  Talkies, which opened the focused tile (Netflix again). Talkies now ignores an OK or Back release when the press
  went to another app. Thanks to @AntonyBOrtega for the video.

## 1.2.0 — 30 October 2026

### New
- **Your own wallpaper, without a computer**: Talkies settings → **Wallpaper**.
  - **From your phone:** scan a QR code and choose a photo. Your phone crops it to the screen and sends just that
    picture, without its location or other details.
  - **From this TV:** pick from the newest pictures on the TV or a USB drive, with previews. Android asks once for
    access to your photos.
  - **Picture of the day** (Bing's) and **Remove your picture** are in the same menu, which replaces the Bing
    wallpaper row.
- **Navigation sounds**: Android's click and move sounds, when the TV's System sounds switch is on.
- **Recent apps**, in Control Center or on a remote button: the apps you opened through Talkies, newest first, with
  their banners. OK opens one; Close them all frees their memory.
- **Control Center shortcuts**, in Talkies settings: choose which of the TV's settings screens Network, Bluetooth,
  Sound output and Storage open, for TVs that keep the useful page somewhere else.

### Fixed
- **Holding Volume changes it smoothly again** on TVs (like OnePlus's) where it moved one step per press while
  Talkies was on: Android stops repeating a held button while an app watches the remote's buttons. Talkies now
  watches them only while **Remote keys** is on (Talkies settings → Remote keys, On or Off), which it is once you've
  given a button a job. Off, Home still brings Talkies back, with Google TV's home showing for a moment first; the
  sleep timer's "Press OK for 15 more minutes" still works.
- **The Remote keys list shows each button's name** beside a long job ("Open … · hold: Open …").
- **Talkies settings → Home app on Google TV** opens the Accessibility switch, which is what to turn off to use
  another home screen (Google TV ignores the default home app you pick, so the old chooser did nothing).
- **Screensaver works on the Google TV Streamer** and other devices without Android's screensaver shortcut
  ([#7](https://github.com/rajupp/talkies/issues/7)): Talkies starts it the way Google Assistant does (an idea from
  [tvOS Launcher](https://github.com/techgenius1/tvOS-Launcher)), so the button is back where 1.1.2 had to hide it.
- **Sound output** says *Soundbar connected* rather than *Soundbar*: Android shows a soundbar is plugged in, not that
  it's playing. On Sony TVs it opens Display & sound, where Audio output chooses the speakers, instead of a page with
  only System sounds.

## 1.1.3 — 30 October 2026

### Fixed
- **Home switches the TV to your streaming box again** ([#13](https://github.com/rajupp/talkies/issues/13)). On a box
  or stick (Google TV Streamer, Shield…), Android's own Home also turns the TV to that box's input over HDMI-CEC, and
  Talkies caught the button before Android could. Talkies now lets Home through on a box, and wherever it's the
  default home app; Google TV's home may flash before Talkies comes back, unless Talkies is the default home app.

## 1.1.2 — 30 October 2026

### New
- **Dock apps**, in Talkies settings: every app in one list, the dock's first. OK puts an app in the dock or takes it
  out, without hunting for it and holding OK.

### Fixed
- **The screensaver shows your Google Photos again** on TVs set up with `build-and-install.sh`. The script turned off
  Google TV's setup wizard (so it couldn't take the Home button), and Ambient mode needs the wizard for your photos:
  without it, it showed Google's art. Talkies' Accessibility switch already keeps Home on Talkies, so the script now
  leaves the wizard on, and turns it back on where an earlier version turned it off. Installed from the APK? This
  never applied.
- **No Screensaver button where it can't work** ([#7](https://github.com/rajupp/talkies/issues/7)). On devices that
  don't let apps start the screensaver (Google TV Streamer), Control Center leaves it out, instead of opening settings
  that have no Start now.

## 1.1.1 — 29 October 2026

### New
- **Support Talkies** in Talkies settings → About: a QR code for [buymeacoffee.com/rajupp](https://buymeacoffee.com/rajupp),
  also in the README and as GitHub's Sponsor button.

### Fixed
- **Remote keys can change buttons Android has no name for**, such as the app buttons on some remotes. They used to
  go straight to their app while Talkies waited; now they're known by the remote's own code and show as *Button 0x…*.
  (A button the TV's own software handles, like the Mi Box's Netflix button may be, still can't be changed.) While it
  waits, Talkies also logs every button it sees.
- **The glow around the focus ring fades smoothly.** It was two overlapping outlines, which showed as steps up close.

## 1.1.0 — 29 October 2026

### New
- **Dock size**, in Talkies settings: 5, 6, 7, 8, 9 or 10 apps. The tiles grow or shrink to fill the width (seven to
  start, as before).
- **An All apps button** at the end of the dock: Right past the last app, then OK, goes straight to All apps instead of
  down past every channel row.

## 1.0.3 — 29 October 2026

### Fixed
- **For you no longer starts blank** on a new TV ([#4](https://github.com/rajupp/talkies/issues/4)). With no picks yet
  the row waited on Trending, so Continue filling in later didn't show until For you was turned off and on.
- **The focused icon at the top right isn't cut off** ([#3](https://github.com/rajupp/talkies/issues/3)): it grows a
  little when focused, and its row clipped it.
- **Screensaver** in Control Center opens the screensaver settings (Start now) on devices that don't let apps start it,
  such as the Google TV Streamer ([#7](https://github.com/rajupp/talkies/issues/7)).

### New
- **An empty For you says why**: no TMDB key yet (and where to add one), or not online yet
  ([#6](https://github.com/rajupp/talkies/issues/6)).
- **Finish setup → Switch greyed out?** on Android 13 and newer, which grey out the Accessibility and notification
  switches for an installed APK: it opens App info, and the README has the command for TVs without the option
  ([#5](https://github.com/rajupp/talkies/issues/5)).
- **README fixes** for "App not installed.", no weather, Inputs showing only Live TV, and the restricted setting.

## 1.0.2 — 29 October 2026

### New
- **Finish setup** (Talkies settings, only while something's left): the Home button's Accessibility switch,
  notification access and the Wi-Fi name and Watch Next permissions, each one OK away. Talkies also asks for the two
  permissions the first time it opens, so an APK installed without a computer works fully.
- **A README for everyone**: install without a computer (the Downloader app), with one, or through an AI assistant,
  then a first-time checklist, updates, privacy and fixes. The full reference moved to `docs/guide.md`, and building
  from source to `docs/building.md`.

### Changed
- **Up lands on Control Center** the first time, instead of Inputs; after that it still goes to the icon you used
  last.
- **Menus wrap around**: Up on the first row goes to the last, Down on the last to the first, in Control Center,
  Talkies settings and every other menu.
- **About** says who made Talkies (Raju · TechPP) and where its source code is, and credits Luma and GlassLauncher
  too.

### Fixed
- **Back to Talkies after an update.** Installing an update left the TV on Google TV's home screen until you pressed
  Home; Talkies now takes over again as soon as it's back.

## 1.0.1 — 29 October 2026

### New
- **The bell tells you about Talkies updates too.** When a newer Talkies is out, the bell at the top right appears
  with it as the first row; OK downloads and installs it.
- README: the first update needs Android's *Install unknown apps* switch for Talkies, and on some TVs one adb
  command as well.

## 1.0.0 — 29 October 2026

### New
- **A ready APK, and updates from Talkies settings.** Each release has `talkies.apk`, signed with the project's key,
  to install with adb without building. Talkies settings → **Updates** shows whether a newer release is out (checked
  once a day) and installs it, with Android asking first. Only the APK from the releases takes updates; one built on
  a computer is told a new version is out. Adapted from Free My TV's updater (MIT).

### Fixed
- **No crash on a fresh install.** Right after installing, Android can connect Talkies' media listener before it allows
  it, and the refusal crashed Talkies on start (Home and the app icon did nothing). It now waits and tries again every
  30 seconds.

## 0.4.1 — 29 October 2026

### New
- **RAM and storage on the home screen**: two slim meters beside the icon buttons show how much of each is free,
  updated every minute (Talkies settings → RAM and storage hides them).
- **Send from your phone** (Talkies settings → Keys and location): a QR code opens a page on your phone to paste the
  TMDB key, the CricketData key and the weather area, instead of typing them with the remote. It works once, for 5
  minutes, only on your home network; each key is still checked before it's saved. Adapted from Free My TV (MIT).
- **Back up and restore your settings**: `./backup-settings.sh` copies Talkies settings, the dock and app order,
  hidden apps, remote keys, channels, football leagues, the keys and the weather area to `talkies-backup.json` on your
  computer; `./restore-settings.sh` puts them back after a reinstall or on another TV.

### Changed
- **Keys and location** is now the first row in Talkies settings.

### Fixed
- **Back online, fetched at once**: when the Wi-Fi drops and comes back, the weather, picks, scores and Bing photo
  that failed meanwhile are fetched straight away instead of at their next retry. An idea from Free My TV.

## 0.4.0 — 28 October 2026

### New
- **Bing wallpaper** (Talkies settings, off by default): Bing's picture of the day, with its credit in About.
- **Apps open from their tile**: an app grows out of the tile or card you chose (Talkies settings, on by default).
- **Now playing** in Control Center: the song or show playing on the TV, with artwork, Play / Pause, Next, Previous
  and Open. An idea from Luma.
- **Weather forecast** in Control Center: the next hours, and OK again for the next days.
- **Inputs know your consoles**: a PlayStation, Xbox, Switch, Apple TV, Fire TV or SHIELD shows under its real name,
  with a dot in its brand's colour (Luma's device list, MIT).
- **Proper tiles for apps without a TV banner**: the logo on its own colour, or the logo and name on white, instead of
  a small icon on a blank card (GlassLauncher's tile builder, MIT).
- **Animations** in Control Center: 1×, 0.5× or Off for the whole TV (with the `WRITE_SECURE_SETTINGS` grant).
- **Storage** in Control Center: free space at a glance. Both ideas from GlassLauncher.
- **A stopped Home service is noticed.** If Android stops Talkies' accessibility service, Talkies switches it off and
  on itself (with the `WRITE_SECURE_SETTINGS` grant), or says so on the home screen and in Control Center.

### Changed
- **Runs on Android 8.0 and later** (it needed Android 11). On Android 8, the Sleep timer and Turn off now are hidden,
  since standby needs Android 9. Untested on those versions so far.
- **Text stays readable on a wallpaper.** Talkies measures the photo and adds just enough veil behind the text for
  4.5:1 contrast, top to bottom; with Appearance on Auto it picks the light or dark look, whichever suits the photo.
- Talkies settings is the first row of Control Center.
- A stronger focus ring, with a soft halo, so the selected tile is easy to see from the sofa.
- Channel cards show what you watched last first, for apps that say when (Nuvio does).

## 0.3.0 — 28 October 2026

### New
- **Bluetooth in Control Center:** your paired speakers and headphones, with OK to connect or disconnect one.
- **Restart…** in Control Center, through the TV's own power menu, so it still asks first.
- **Talkies at power-on: Every time** (Talkies settings): Talkies also comes up when the TV wakes from standby, not
  only after a restart, unless you press a button first.
- **Hold jobs for remote keys:** a button with a job can do a second one when held.
- **Empty inputs: Under More inputs** (Talkies settings): Inputs lists only ports with something plugged in.

### Changed
- A new logo: a cinema ticket with a play button, on a warm dark background, for the app icon and the TV banner
  (it replaces the two glass cards from when Talkies was called Glass Home).
- Menus are fully opaque, so nothing behind them shows through the text.
- The ⚙ icon opens Android TV's own settings on TVs that hide the standard settings screen.
- The README covers pairing with `adb pair` for TVs that only have Wireless debugging.

## 0.2.0 — 28 October 2026

### New
- **Live football scores.** A Football row and score line like cricket's, from ESPN's public scoreboard, with no
  key needed: Premier League, Champions League, LaLiga, Serie A, Bundesliga, ISL, MLS and more. Each country starts
  with its own leagues; change them in Talkies settings → Football leagues. Updates every minute while a match is on.
- **Live scores setting:** cricket, football, both or off.
- **Pin and hide apps.** Hold OK on an app: **Add to dock**, **Remove from dock**, or **Hide** (it stays installed).
  Talkies settings → Hidden apps brings them back.
- **Keys and location on the TV.** Type or paste your TMDB key, CricketData key and weather location in Talkies
  settings → Keys and location; no computer needed. Keys are checked with the service before they're saved.
- **Talkies settings,** separate from Control Center. Control Center keeps the quick actions (sleep timer, turn off,
  memory, network, speed test, sound, screensaver); everything you set once moved to Talkies settings, opened from
  Control Center's last row or by holding OK on the ⚙ icon.

### Changed
- A Test match at stumps, lunch, tea or a rain delay shows as paused, not live; a live football match then takes
  the score line, and its row moves above Cricket.
- The score line shows the football league: "● LIVE  Serie A · 54' · SAS 2–4 LAZ".
- New installs start with Watch Next and each app's own home-screen row instead of the author's channel choice
  (TVs that had Talkies before keep theirs).
- On Android TV, Home goes to a launcher you picked as the home app; the README explains switching launchers on
  Google TV, which keeps its own launcher as the home app.
- `switch-home.sh` takes `talkies`, `stock`, any launcher's package name, or `list`.
- Transparent images (team badges) are cached as PNG, so they don't get black backgrounds.

### Fixed
- Score rows didn't update while focus rested on them, so a live match could show in the score line but not in
  the rows.

## 0.1.0 — 25 October 2026

First public version: a small, ad-free home screen for Android TV and Google TV. Weather, clock and date; For You
picks from TMDB with Continue, Trending and New in your country; a seven-app dock; channel rows; live cricket
scores; Control Center with sleep timer, turn off, speed test and HDMI-CEC inputs; remote key remapping; a
background that glows in the focused app's colour; light by day and dark at night. About 200 KB, no libraries,
MIT licensed.
