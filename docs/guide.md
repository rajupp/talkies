# Talkies guide

Everything Talkies does, screen by screen. For installing and first-time setup, see the [README](../README.md); for
building it yourself, [building.md](building.md).

**The scripts in this guide** (`./set-tmdb-key.sh`, `./set-location.sh`…) run on a computer with the Talkies source
and `adb` (see [building.md](building.md)); `$TALKIES_TV` is your TV's address, like `192.168.1.20:5555`. Almost
everything they do can also be done on the TV, in Talkies settings.

- [Remote controls](#remote-controls)
- [Control Center](#control-center)
- [Talkies settings](#talkies-settings)
- [Trending and New in …](#trending-and-new-in-)
- [Continue](#continue)
- [Channels](#channels)
- [Weather](#weather)
- [Cricket](#cricket)
- [Football](#football)
- [Hold OK: app and Continue menus](#hold-ok-app-and-continue-menus)
- [Rearrange apps](#rearrange-apps)
- [Change the wallpaper](#change-the-wallpaper)
- [Use another home screen](#use-another-home-screen)
- [Google Assistant and the Google TV launcher](#google-assistant-and-the-google-tv-launcher)
- [Google TV's setup wizard](#google-tvs-setup-wizard)

## Remote controls

| Where you are | Up | Down | Left / Right | OK | Back |
|---|---|---|---|---|---|
| Icon buttons | — | Tabs (or the dock when there are no picks) | Next button; stops at the ends | Open | Dock |
| Tabs | Icon buttons | Picks | Switch tab; stops at the ends | Picks | Dock |
| Picks | Tabs | Dock | Next pick (it grows, the row slides); stops at the ends | Open it | Dock |
| Dock | Picks (or the icon buttons) | First channel (or All apps) | Next app; past the last, the round **All apps** button | Launch (hold: dock, Move, Change image, Hide, Uninstall, App info); on the All apps button, jump to All apps | — |
| Channels | Row above (the dock from the first) | Row below (All apps after the last) | Next card; the row slides | Open that title | Dock |
| All apps | One row up (the last channel from the top row) | One row down | Next app in the row | Launch (hold: dock, Move, Change image, Hide, Uninstall, App info) | Dock |
| Inputs / Control Center / settings | Previous (from the first, the last) | Next (from the last, the first) | Close | Choose | Close |

**Home** always brings you back to the dock, at the top, on the Trending tab.

**Navigation sounds:** Talkies plays Android's own click and move sounds when the TV's **System sounds** switch is on
(Settings → Display & sound, or Sound), and is silent when it's off, like the rest of the TV.

## Control Center

Go up to the icon buttons and choose the first one (sliders). It has what you reach for while watching; everything
you set once is in **Talkies settings**, at the top.

| Row | What OK does |
|---|---|
| Fix Home and Turn off | Only when Talkies' accessibility service has stopped (see below): opens Accessibility, where you switch Talkies off and on |
| Talkies settings | Opens Talkies settings (below) |
| Now playing | Only while an app plays (or has paused) something: the song or show, its artwork, artist and app, then **Pause** / **Play**, **Next**, **Previous** (when the app allows skipping) and **Open** the app. It follows the player as tracks change |
| Weather | The current weather on the right; OK shows the next six hours, and OK again the next six days (high / low and conditions) |
| Sleep timer | Cycles Off → 30 → 60 → 90 min; the time left shows on the right. A minute before, a card counts down over whatever is playing: **OK** gives 15 more minutes |
| Turn off now | Puts the TV in standby |
| Free up memory | Stops background apps (never system apps) and shows how much RAM came back |
| Recent apps | The apps you opened through Talkies (dock, All apps, picks, channels, remote buttons), newest first, with their banners; OK opens one, **Close them all** frees their memory. Also on a remote button: Remote keys → Recent apps |
| Storage | Shows the free space ("12.4 GB free of 32 GB"); OK opens the TV's storage settings |
| Animations | Cycles the speed of every animation on the TV: 1× → 0.5× → Off. Faster makes menus and app switches feel snappier; Off also stills Talkies' own motion. Needs the `WRITE_SECURE_SETTINGS` grant (see [Google Assistant](#google-assistant-and-the-google-tv-launcher)) |
| Network | Shows the Wi-Fi name (or Ethernet); OK opens Wi-Fi settings |
| Speed test | A few seconds' download from Cloudflare's speed test; shows e.g. "206 Mbps" |
| Bluetooth | Shows the connected speaker or headphones. OK lists your paired ones: OK on one connects or disconnects it; **Pair a new device** opens the TV's Bluetooth settings. Bluetooth can't be turned off here, since many remotes use it |
| Sound output | Shows *TV speakers*, or *Soundbar connected* when one is on HDMI ARC (Android doesn't say whether it's the one playing), and opens the TV's sound settings: on Sony, Display & sound, where Audio output chooses the speakers |
| Screensaver | Starts the TV's screensaver right away, the way Google Assistant does (or through Android's own shortcut). Not shown on devices that offer apps neither |
| Restart… | Opens the TV's own power menu, which asks before restarting. On some TVs (OnePlus) that menu puts the TV to sleep after 10 seconds if you choose nothing; Back closes it |

## Talkies settings

Control Center → **Talkies settings**, or **hold OK on the ⚙ icon** (a press still opens the TV's own settings).

| Row | What OK does |
|---|---|
| Finish setup | Only while a first-time step is left: lists them (the Home button's Accessibility switch, notification access, the Wi-Fi name and Watch Next permissions), and OK opens each one's switch or asks |
| Keys and location | Your TMDB key (picks), CricketData key (scores) and weather area. **Send from your phone** shows a QR code: scan it with a phone on the same Wi-Fi and paste them into the page it opens (it works once, for 5 minutes, only on your home network, and the keys stay on the TV). Or type or paste each one; keys are tried with the service first and only saved if it accepts them. **Remove** clears one |
| Appearance | Cycles Auto (light by day, dark at night) → Light → Dark. With a wallpaper, Auto picks the look that suits the photo instead, and every look adds just enough veil behind the text to keep it readable |
| For You | Shown or Hidden. Hidden removes the tabs and picks (the weather, time and date stay, larger), Up from the dock goes to the icons, and TMDB picks aren't fetched |
| Hide spoilers | Off (default) or On. On: an episode you haven't started yet shows the show's own art in Continue instead of the app's still from that episode, or the still blurred when the app gives nothing else. Episodes you're partway through keep their still |
| Dock apps | Every app, the dock's first (numbered): OK puts one in the dock or takes it out, the same as **Add to dock** and **Remove from dock** when you hold OK on an app. To change the order, hold OK on an app in the dock and choose **Move** |
| Dock size | 5, 6, 7 (default), 8, 9 or 10 apps. The tiles grow or shrink so the dock always fills the screen's width; apps that no longer fit move to the front of All apps, and come back when it grows again |
| All apps order | **Your order** (default: apps you moved with hold OK → Move stay put, the rest A to Z), **Recently opened** (the apps you last opened through Talkies first, the rest A to Z) or **A to Z**. In the two sorted orders only the dock's apps can be moved; the dock always keeps your order |
| RAM and storage | Shown (default) or Hidden: the two meters beside the icon buttons, with free RAM and free storage, updated every minute |
| Live scores | Cricket and football (default), Cricket, Football or Off: what the score line and the score rows show |
| Football leagues | The leagues the Football row follows; see [Football](#football) |
| Channels | Choose which app channels show below the dock, and their order |
| Hidden apps | Shown once you've hidden an app: the apps you hid; OK shows one again |
| Remote keys | Give a remote button another job: open an app, Inputs, Recent apps, Talkies, or nothing. **Add a key**, press the button, choose. Once it has a job, **When held** gives it a second one for holding it (over half a second). Navigation, OK, Back, Home, volume and power can't be changed. A button Android has no name for shows as *Button 0x…* (its code from the remote). If a button still just does its own job while Talkies waits for it (the app buttons on some TVs), the TV's software handles it before Android sees it, and no app can change it. **Remote keys: On or Off** at the top: on once you've given a button a job (or whenever you switch it on). On some TVs, while it's on, holding Volume changes it only one step per press, in every app: Android stops repeating a held button once an app watches the remote. Off, holding Volume works, and Home reaches Talkies with Google TV's home showing for a moment first |
| Network debugging | "On · port 5555" while computers on your network can control the TV over ADB (how Talkies was installed). **Turn off** (needs the permission below) or open Developer options; turning it on again is done in Developer options |
| Talkies at power-on | **After a restart** (default), or **Every time**: also when the TV wakes from standby into the last HDMI input or app. Pressing a button as it wakes keeps what's on screen |
| Empty inputs | **Shown** (default), or **Under More inputs**: Inputs lists only ports with something plugged in, and a More inputs row brings back the rest |
| Wallpaper | A picture of your own **from your phone** (a QR code) or **from this TV** (pictures on the TV or a USB drive), Bing's **picture of the day** (checked every few hours while Talkies is in front), or **Remove your picture**. See [Change the wallpaper](#change-the-wallpaper) |
| Control Center shortcuts | Which screen Network, Bluetooth, Sound output and Storage open in Control Center: the TV's default, or any screen its settings apps offer (likely matches first, **Show every screen** for the rest). Useful where the TV keeps the useful page elsewhere |
| Apps open from their tile | On by default: an app's window grows out of the tile or card you chose. It's Android's normal opening animation, started from the tile, so it costs nothing extra |
| Home app | Shows the current home screen and opens Android's *Default home app* chooser (on Google TV the choice doesn't stick; see [Use another home screen](#use-another-home-screen)) |
| Sharp YouTube pictures | Off by default. On: YouTube cards in Continue get YouTube's 1280-pixel thumbnails, looked up by title and channel |
| Updates | The installed version, and whether a newer release is out (checked once a day while Talkies is in front). OK checks now and, on the APK from the releases, downloads the new one, checks it (GitHub's size and SHA-256, newer, signed with the project's key) and hands it to Android's installer, which asks first; the first time Android also asks to allow Talkies to install apps. A Talkies built on a computer is only told a new version is out |
| About | Credits for the data and code Talkies uses |

**One-time setup for Turn off now, the sleep timer and cricket search:** this TV has no "device admin" support, so the
launcher turns the TV off through a small accessibility service. The same service types a match into JioHotstar's
search when a Cricket card has no direct link. It only sees JioHotstar's screen, only acts for 30 seconds after you
pick a card, and only looks for the search box. Switch it on once at **Settings → Device Preferences → Accessibility →
Talkies**. Until then, choosing Turn off or the sleep timer takes you to that screen.

**If the service stops:** Android can stop the service (a crash, or memory running low) while its switch still looks
on, and then Home, Turn off and Remote keys quietly stop working. Talkies checks a few seconds after it opens: with
the `WRITE_SECURE_SETTINGS` grant (see [Google Assistant](#google-assistant-and-the-google-tv-launcher)) it switches the service off and on itself; without it, a
note at the top of the home screen says so, and Control Center gets a **Fix Home and Turn off** row.

## Trending and New in …

The picks row sits above the dock. Two of its tabs come from TMDB, for the TV's country:

- **Trending**: what's popular right now on your apps here: titles from your country mixed with English ones, two of
  yours for every English one (in India, Hindi, Tamil, Telugu and other Indian titles with English ones; TMDB's own
  trending list is worldwide and almost all English). In English-speaking countries (US, UK, Canada, Australia,
  New Zealand, Ireland) it's simply what's popular there. Titles already in the "New in" tab are left out.
- **New in *your country*** ("New in India", "New in the US"): your country's films from the last 6 months and series
  with new episodes in the last 60 days.

Both only include titles that stream (included in your subscription, not rent or buy) in your country on an app
installed on this TV: Netflix, Prime Video, Disney+ / JioHotstar, Apple TV, Max, Hulu, Peacock, Paramount+, ZEE5,
SonyLIV, MX Player, Xstream Play, FanCode, Crunchyroll or MUBI, or any other installed app named like a streaming
service TMDB lists for your country (Viaplay, Canal+, RTL+, Joyn, Stan, Crave…).

**No streaming apps?** With none of them installed, the tabs show the same kind of titles anyway, and OK plays the
title's trailer in YouTube. The line under the card says where it streams ("Trailer · on Netflix").

**Which country:** the TV's time zone (where it actually is; many TVs stay on "English (United States)" wherever
they are), then its language setting. If that's wrong for you, set it: `./set-region.sh GB` (`--remove` to go back).
It also picks the cricket and °C/°F behaviour below.
Each tab keeps up to 24 and shows 12 of them, moving on by 4 each hour, so the large card and the row change through
the day and the same set comes back only after 6 hours. The lists refresh at most every 6 hours (TMDB's popularity
rankings change about daily, so refreshing more often would mostly fetch the same lists); a refresh takes a few seconds. Under the large card: film or series, genre, language (when not English) and the app.
Pictures: the large card uses TMDB's 1280-pixel backdrop and the small cards the title's poster, so neither is
stretched; only the large card's full-size picture is kept in memory.
**OK** on a pick opens that app.
TMDB doesn't know each title's ID inside Netflix, Hotstar and the rest, so the app opens to its home screen, not the title.

The picks come from [TMDB](https://www.themoviedb.org), which needs a free key of your own:

1. Create an account at [themoviedb.org](https://www.themoviedb.org/signup) and open
   [Settings → API](https://www.themoviedb.org/settings/api). Request a developer key (personal use).
2. On the TV: Talkies settings → **Keys and location** → **Send from your phone**, scan the code and paste the **API
   Read Access Token** (or the shorter **API Key**) on your phone. Or from a computer, run the key script and paste it
   when it asks (nothing is shown while you paste):

   ```bash
   ./set-tmdb-key.sh
   ```

3. Press Home on the remote. The first picks take about a minute.

The key is stored in `tmdb_key.txt` here (readable only by you) and in the launcher's folder on the TV; it is never
built into the app, and `build-and-install.sh` puts it back after a reinstall. To turn the row off:
`./set-tmdb-key.sh --remove`. If the row doesn't appear, check the log:

```bash
adb -s "$TALKIES_TV" logcat -d -s GlassTrending
```

## Continue

The first tab shows at least ten recent titles: first the newest from each app you've watched (so one app can't take
over the front of the row), then the next newest. YouTube gets at most two cards. Cards from Watch Next show how far in you are when the app shares it (Prime Video and Xstream Play do).
It combines two sources:

- **Android TV's Watch Next list.** Netflix, Prime Video, JioHotstar, Apple TV and Xstream Play publish what you're
  in the middle of, with the show, season, episode and artwork. **OK** opens that exact title or episode
  (tested with Netflix: it opened the episode and played it). Only "continue" and "next episode" entries are used;
  "new season" and watchlist entries are promotions and are left out.
- **What apps report while playing** (media sessions), for apps that don't use Watch Next, such as YouTube and ZEE5,
  and for apps whose Watch Next is behind: JioHotstar doesn't always update it, so a title it played more recently
  than its newest Watch Next entry shows too. Watch Next is reread whenever an app changes it (Netflix updates it a
  few seconds after you leave), and the Watch Next channel row shows this same list.
  Pictures: YouTube shares only a 640-pixel picture while playing (no video ID). With Talkies settings → **Sharp
  YouTube pictures** on (it's off by default), Talkies looks the video up on YouTube by title and channel,
  confirms it through YouTube's public oEmbed endpoint, and swaps in the 1280 × 720 thumbnail (only the title and
  channel are sent; once per video, while the home screen is in front). This reads YouTube's search page, which
  YouTube's terms don't really invite; that's why it's opt-in. Apps that share no
  picture (JioHotstar) get a card in the app's colour with its logo, sharp, in the middle.
  A title is added after 20 seconds of playback, so ads and previews are skipped. **OK** opens the app.

An app that publishes to Watch Next is taken from there only, so it doesn't appear twice. Music apps (Spotify,
YouTube Music) are left out.

| App | Source | What you see |
|---|---|---|
| Netflix, Prime Video, Apple TV, Xstream Play | Watch Next | Show, season and episode with artwork; opens that episode |
| JioHotstar | Watch Next | Same as above (its playback reports only had the episode name) |
| YouTube | While playing | Video title and thumbnail; opens YouTube |
| ZEE5 | While playing | Title (e.g. "Max") on the ZEE5 banner; opens ZEE5 |

**Permissions:** Watch Next needs the *TV listings* permission: Talkies asks for it the first time it starts (or Talkies settings → **Finish setup**), and `build-and-install.sh` grants it.
For the "while playing" titles, Android only shares what's playing with an app that has *notification access*.
On the TV: **Settings → Apps → Special app access → Notification access → Talkies → Allow.**
Talkies reads no notifications; it uses the access only to see apps' media sessions (what's playing), and
keeps the list on the TV (`/sdcard/Android/data/com.techpp.glass/files/recent/`). To stop it, switch the same toggle off;
to clear the list:

```bash
adb -s "$TALKIES_TV" shell rm -rf /sdcard/Android/data/com.techpp.glass/files/recent
```

## Channels

Rows below the dock, read from the channels your apps publish to Android TV (the same ones Projectivy shows), plus
**Watch Next**. Each row shows up to 15 cards, the one you watched last first when the app says when (Nuvio does;
others keep their own order); the focused card's title and description appear under it, and **OK**
opens that title in its app (for example a JioHotstar card opens that film's page). Rows refresh whenever the home
screen opens.

**Choose channels:** Talkies settings → **Channels** lists every channel your apps publish.

- **OK** shows or hides a channel.
- **Hold OK**, then **Up/Down**, moves it; **OK** places it, **Back** cancels. The first shown channel is the top row.

To start with, Watch Next and each app's own home-screen row are shown (the rows an app asks Android TV to show,
or, where the TV doesn't say, as on Google TV, each app's first row); the rest start hidden, and so do channels an
app adds later. A TV that had Talkies before this keeps the rows it had.
Artwork is cached on the TV in `files/channels/`.

## Weather

The weather shows at the top left, for example "27° SHOWERS", in °C, or °F where that's the custom (the US and a
few others; see [Which country](#trending-and-new-in-)).
The weather part combines two sources:

- **Temperature** is measured at the **nearest airport weather station**. Airports publish observations (METAR) every
  30–60 minutes; Talkies reads them from the US National Weather Service's free
  [aviationweather.gov](https://aviationweather.gov) service, picking the closest report from the last two hours
  within about 200 km (in a big city, usually the city's airport). Closer stations may exist (national weather
  services, personal weather stations), but few publish freely for apps.
- **Rain and cloud** come from [Open-Meteo](https://open-meteo.com)'s model for your exact coordinates, because an
  airport 15–20 km away can be dry while it rains at home.

Without a recent airport report, the temperature also comes from Open-Meteo. It refreshes at most every
30 minutes, and only while the home screen is showing.

Set your area once, on the TV in Talkies settings → **Keys and location** (or **Send from your phone**), or from a
computer:

```bash
./set-location.sh "12.97,77.64"     # your latitude,longitude
```

Coordinates are best. A place name also works (`./set-location.sh "Indiranagar, Bengaluru"`), looked up with
Open-Meteo's place search; if it doesn't know the exact locality, it uses the city centre. Running the script again refreshes the weather straight away;
`./set-location.sh --remove` leaves just the time and date. Today's sunrise and sunset come from the same Open-Meteo
request and set when the Auto appearance switches between light and dark.

## Cricket

While a match is live or on today, the line under the weather shows the most relevant one, for example
"● LIVE  ODI · AFG v BAN 116/4 (29.5)" (your country's team first, then other internationals, then domestic), and a
**Cricket** row appears at the top of the channel rows: each card has both teams' flags and scores, the match type and
status ("LIVE · Test · Day 4: 3rd Session…"); the caption under it names the series.

At stumps, lunch, tea or a rain delay nobody is playing, so a match isn't shown as live: the line reads
"Test · AUS v RSA 329 · Day 1: Stumps", it's still checked every 15 minutes for play to resume, and a football match
being played comes first (the Football row then sits above Cricket).

- **Match type:** Test, ODI or T20I for internationals; First-class, One-day or T20 for domestic matches.
- **Tests show every innings:** "425/9d & 84/1 (25)" and "99": all out shows the runs, a declaration adds "d", the
  innings in progress shows wickets and overs.

- **Which matches:** internationals between national teams (men's and women's; "A" sides and franchise leagues
  abroad are left out) and your country's domestic competitions: in India the IPL, WPL, Ranji Trophy, Syed Mushtaq
  Ali, Vijay Hazare, Duleep, Irani, Deodhar and Cooch Behar; in Australia the Big Bash, Sheffield Shield and Marsh
  Cup; in the UK the County Championship, The Hundred and the T20 Blast; and the main leagues of New Zealand, South
  Africa, Pakistan, Sri Lanka, Bangladesh, the Caribbean, the UAE and the US.
- **OK** opens the stream: if one of your apps has a card for the match in its TV channel (one naming both teams),
  that card's link opens the match itself. Otherwise, outside India, the card says no app has it (rights vary too much
  by country to guess). In India the app with the rights there opens: JioHotstar for BCCI matches (home internationals, domestic, IPL) and ICC events, FanCode for the
  Lanka Premier League, SonyLIV for the Asia Cup. For India's away tours and other countries' series, rights vary; the
  fallback is JioHotstar, which may not have them. When it's JioHotstar, its search opens with both teams typed in
  ("Afghanistan vs Bangladesh"), so the match is one click away if JioHotstar streams it. JioHotstar's search links
  ignore the text, so the Talkies accessibility service types it (see [Talkies settings](#talkies-settings)); with the service off,
  the search page opens empty. JioHotstar's results list highlights and replays too, and a series it doesn't stream
  shows only older clips.
- **Data:** [CricketData.org](https://cricketdata.org)'s `cricScore` (all current matches in one request). The free
  key allows 100 requests a day, so it refreshes every 15 minutes while a match is live and hourly otherwise, only
  while Talkies is in front with the TV's display on, and stops for the day at 92. The quick list only has each team's latest innings,
  so a live Test's full innings come from one extra request (`match_info`): every 15 minutes for the Test shown first,
  every 45 for up to two more (45 for all once 70 requests are used). The feed sometimes credits an innings to the
  wrong team (a follow-on came through as the other side's); the launcher checks the latest innings against the
  status ("Australia trail by 242 runs") and moves it to the side the numbers fit. Scores can lag the TV broadcast by a few minutes.

Set your key once (a free account at cricketdata.org gives one), the same way as the TMDB key: on the TV in Talkies
settings → **Keys and location**, or from a computer:

```bash
./set-cricket-key.sh
```

`./set-cricket-key.sh --remove` turns cricket off. Both scripts press Home on the TV when done. The log shows what it
found and where each Watch button points: `adb -s "$TALKIES_TV" logcat -d -s GlassCricket`.

## Football

Football works like cricket, with no key: the line under the weather shows the most relevant match
("● LIVE  67'  ·  ARS 2–1 CHE", a live cricket match comes first), and a **Football** row with both clubs' badges,
the score and the minute ("LIVE · Premier League · 67'"), kick-off time ("starts Wed 7:30 pm") or result.

- **Which matches:** live now, starting within a day, or finished in the last 12 hours, from your leagues.
- **Your leagues:** Talkies settings → **Football leagues**. Each country starts with its own (India: Premier League,
  Champions League and Indian Super League; the UK: Premier League, Champions League and the Scottish Premiership;
  the US: MLS, Premier League and Champions League; Turkey: Süper Lig, Champions League and Premier League…), plus the World Cup and the Euros when they're on. Press OK to
  turn one on or off; **Back to the defaults** undoes your changes.
- **Updates:** every 15 minutes, and every minute for a league with a match on, only while Talkies is in front.
- **Data:** ESPN's public scoreboard. It's free and needs no key, but it isn't an official API: ESPN can change it
  without notice, and then the Football row just stays away until Talkies is updated.
- **Watching:** not yet. Which app shows which league differs by country, so OK on a match card says so for now.

## Hold OK: app and Continue menus

**Hold OK** (about half a second) on:

- **An app** in the dock or All apps:
  - **Add to dock** (from All apps): it takes the dock's last spot, and the app that was there moves to the front of
    All apps. **Remove from dock** does the opposite: the first app in All apps takes its spot.
  - **Move** (below), to place it exactly.
  - **Change image**: a picture of your own for its tile, **From your phone** (scan a QR code and pick a photo; it's
    cropped to the tile on the phone) or **From this TV** (Pictures, Downloads, USB drives). **Original image** puts
    the app's own back.
  - **Hide**: it leaves the dock and All apps but stays installed (voice search and Settings still open it).
    Talkies settings → **Hidden apps** lists them; OK shows one again, at the end of All apps.
  - **Uninstall** opens Android's own "Uninstall this app?" dialog, and only shows for apps you installed (not system
    apps or Talkies). **App info** opens the app's page in Settings.
- **A Continue card** (the Continue tab or the Watch Next row): **Remove from Continue**, or **Open** the app itself
  rather than the episode. A removed title stays out until the app reports a new episode of it.

**Back** closes a menu without doing anything.

## Rearrange apps

1. **Hold OK** on any app in the dock or All apps and choose **Move**. It lifts out, with a thicker ring,
   and a hint appears at the top.
2. **Use the arrows** to move it. Within a row it trades places with its neighbour; in the grid, Up and Down move it a
   row and the other apps shift along. Moving between the dock and All apps swaps it with the app there, so the dock
   always keeps seven.
3. **Press OK** to place it, or **Back** to cancel and put everything back as it was.

The order is saved on the TV and survives restarts and reinstalls. Apps you install later are added to the end of the
grid. Picks (Continue, Trending, New in …) can't be moved.

The starting dock, before you've moved anything, comes from `app/src/main/res/values/dock.xml`. Package names are
listed with:

```bash
adb -s "$TALKIES_TV" shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER
```

## Change the wallpaper

Talkies settings → **Wallpaper**. Your picture replaces the app-colour glow, and Talkies adds just enough veil behind
the text to keep it readable.

- **From your phone:** scan the QR code with your phone (on the same Wi-Fi as the TV) and choose a photo. Your phone
  crops it to the screen and sends only that picture, without its location or other details. The page works once,
  for 5 minutes.
- **From this TV:** the newest pictures on the TV and any USB drive plugged into it (Pictures, Downloads, DCIM…),
  each with a small preview; OK on one uses it. Android asks once to let Talkies see your photos.
- **Picture of the day:** Bing's, checked every few hours. Your own picture wins while there is one.
- **Remove your picture:** back to the glow (or to Bing's picture, when that's on).

From a computer, copy an image to Talkies' folder instead. It can be `wallpaper.jpg`, `wallpaper.png` or
`wallpaper.webp`; 1920×1080 works best:

```bash
adb -s "$TALKIES_TV" push my-picture.jpg /sdcard/Android/data/com.techpp.glass/files/wallpaper.jpg
```

Press Home on the remote to see it. To remove it from a computer, delete the file:

```bash
adb -s "$TALKIES_TV" shell rm /sdcard/Android/data/com.techpp.glass/files/wallpaper.jpg
```

## Use another home screen

- **Google TV:** Google TV keeps its own launcher as the home app whatever you choose in *Default home app* (checked
  on this TV), so third-party launchers, Talkies included, take the Home button with an accessibility service. To use
  another one, turn off **Settings → Device Preferences → Accessibility → Talkies** (Talkies settings → **Home app**
  opens it) and turn on that launcher's own Home option. Turn Talkies' back on to return.
- **Android TV:** Talkies settings → **Home app** opens Android's chooser; pick another launcher and Talkies' service
  leaves the Home button to it. Pick Talkies there to come back.
- **From a computer:**

  ```bash
  ./switch-home.sh list                       # the home screens on the TV
  ./switch-home.sh talkies                    # Talkies
  ./switch-home.sh stock                      # the TV's own home screen
  ./switch-home.sh com.spocky.projengmenu     # any launcher you installed, by package name
  ```

  It says so when the TV ignores the choice (Google TV).

To just peek at another launcher, open its tile under All apps; Home still brings you back to Talkies.

## Google Assistant and the Google TV launcher

The TV's own launcher stays **enabled**. On Google TV (`com.google.android.apps.tv.launcherx`) the Assistant shows
many answers in it: "show me comedy shows" opens its results page, "Stranger Things" its title page. With it disabled
those queries failed with "Google Assistant is having a problem processing your request", while opening apps still
worked.

Enabled, it claims the Home button, so the **Talkies accessibility service must be on** (Settings → Device
Preferences → Accessibility → Talkies). It:

- opens Talkies when you press Home;
- opens Talkies over the TV's built-in home screen when that appears on its own (after a restart, or backing out
  of its pages), except within 8 seconds of the Assistant, so its results stay up. The built-in launchers are found
  when the service starts (Google TV's, Android TV's or the TV maker's: system apps that can be the home screen);
  launchers you installed yourself, like Projectivy, are left alone;
- sees only the windows of JioHotstar, those launchers and the Assistant, and filters only Home, OK while the sleep
  warning is up, and remote buttons you've given a job.

If the service is ever off, Home shows Google TV (with its ads) until you switch it back on. `build-and-install.sh`
warns when it's off. Android switches it off if Talkies is force-stopped (it counts as a crash), so don't use
"Force stop" on Talkies; the scripts here don't.

**Optional: put it back automatically.** Grant Talkies permission to change secure settings, once:

```bash
adb -s "$TALKIES_TV" shell pm grant com.techpp.glass android.permission.WRITE_SECURE_SETTINGS
```

Then, if a force stop switches the service off, Talkies switches it back on the next time it starts (you open it
from Google TV's apps, the TV restarts, or it's updated). It only does that if the service was on when it was killed;
if you switch it off yourself, it stays off. The same permission lets Talkies settings → Network debugging turn
debugging off, and Control Center → Animations change the TV's animation speed. It's a powerful permission (an app holding it can change system settings), so it's your call;
`adb shell pm revoke com.techpp.glass android.permission.WRITE_SECURE_SETTINGS` takes it back.

Remote buttons and Home are seen by the service only from a real remote: keys sent with `adb shell input keyevent`
skip Android's accessibility key filter, so Home sent that way opens Google TV. Known gap: choosing a film inside the Assistant's own results list doesn't open its page (the
Google app declines to start it); asking for the title by name does.

## Google TV's setup wizard

Google TV's setup wizard (`com.google.android.tungsten.setupwraith`) answers the Home button too, but leave it on:
Talkies' Accessibility switch sends Home to Talkies anyway, and the Ambient mode screensaver needs the wizard to show
your Google Photos (without it, it quietly shows Google's art instead). Talkies doesn't turn anything off.

Earlier versions of `build-and-install.sh` turned the wizard off. If your screensaver lost your photos, turn it back on
(`build-and-install.sh` now does this too):

```bash
adb -s "$TALKIES_TV" shell pm enable com.google.android.tungsten.setupwraith
```

Android may then make the wizard or the Google TV launcher the default Home app. With the Accessibility switch on
that doesn't matter, but to make Talkies the default again:

```bash
adb -s "$TALKIES_TV" shell cmd package set-home-activity --user 0 com.techpp.glass/.HomeActivity
```
