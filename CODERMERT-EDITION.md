# Talkies, codermert edition

A personal build of [Talkies](https://github.com/rajupp/talkies) (MIT) for a 1 GB Xiaomi Mi TV box, made on top of
upstream 1.2.8 plus its unreleased main. Everything of upstream's is still here; this edition adds:

- **Opening animation** on a cold start (the name, a curve drawing itself, "by codermert"); any button skips it.
  Talkies settings → Opening animation turns it off.
- **Popular, Upcoming and Artists** tabs from TMDB, next to Trending and New in; rank numbers (gold, silver, bronze
  for the first three) and a large card with a glass tag, a title and an Open pill; the picks slide on by themselves.
- **App colours back:** the glow follows the focused app. Effects defaults to Full (upstream's Auto picks Lite on
  TVs under 1.6 GB, which turns the glow off).
- **24-hour clock** everywhere, a forecast pill (today, then the next hours), a glass dock that names the focused app.
- **Home is caught directly**, so Google TV's home screen never starts (it cost 80-200 MB of RAM). The price is
  HDMI-CEC "One Touch Play" on a streaming box.
- **Weather city** (a list of big cities) and a **Developer** section at the top of Talkies settings.
- **Turkish matches on the score line:** in Turkey the default league is the Süper Lig only, cricket is on by default only
  where it is followed, and matches are looked for three days ahead, so the line is a Turkish match.
- **Smoother loading:** shimmering placeholders on cards, a picture cache, cached fonts, the first cards built
  before the rest.

## Download

[**talkies-codermert.apk**](https://github.com/codermert/talkies/releases/latest/download/talkies-codermert.apk) (289 KB),
from the [latest release](https://github.com/codermert/talkies/releases/latest). SHA-256: `46b1acd9e9c2d8c2e6b4e2a3a5a7347e399c7bfaf7082f33a0ea2dcd9f37f9cc`

## Install

The APK is signed with its own key, so an original Talkies on the TV has to be removed first.

1. Put the APK on the TV: Downloader (type the release link), a USB stick and a file manager, or
   `adb connect TV_IP:5555` then `adb install -r Talkies-codermert-1.2.8.apk`.
2. Open Talkies. Switch on Settings → Device Preferences → Accessibility → Talkies, so Home and a restart open it.
3. Talkies settings → Keys and location: your own free TMDB key; Weather city: your city.
4. Optional, from a computer, once: `adb shell pm grant com.techpp.glass android.permission.WRITE_SECURE_SETTINGS`
   lets Talkies switch its own Accessibility service back on after an update.

## Türkçe

Talkies'in 1 GB RAM'li Xiaomi Mi TV kutusu için kişisel sürümüdür. Açılış animasyonu, Popular / Upcoming / Artists
sekmeleri, uygulama renginde arka plan parıltısı, 24 saatlik saat, Home tuşunun doğrudan Talkies'i açması, şehir seçici
ve Developer bölümü eklenmiştir. Kurulum: APK'yı TV'ye al, eski Talkies varsa önce kaldır, Erişilebilirlik > Talkies'i aç,
kendi TMDB anahtarını gir.
