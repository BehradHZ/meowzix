<p align="center">
  <img src="app/src/main/res/drawable/meowzix_logo.jpg" alt="Meowzix logo" width="120">
</p>

<h1 align="center">Meowzix</h1>
<p align="center"><strong>Your music, wherever it lives.</strong></p>
<p align="center">
  A local-first Android music player combining your phone's audio with music from Telegram.<br>
  Beautiful playback, offline access, and personalized discovery — all in one app.
</p>

<p align="center">
  <a href="https://github.com/BehradHZ/meowzix/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/BehradHZ/meowzix?logo=github&label=Release&style=for-the-badge"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&style=for-the-badge">
  <a href="LICENSE"><img alt="AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://github.com/BehradHZ/meowzix/releases/latest"><strong>Download APK</strong></a> ·
  <a href="#features">Features</a> ·
  <a href="#installation">Installation</a> ·
  <a href="#build-from-source">Build from source</a>
</p>

<table align="center">
  <tr>
    <td align="center" valign="top"><img src="assets/screenshots/01-home.png" alt="Home with recommended tracks and recently played music" width="205"><br><sub><b>Home</b></sub></td>
    <td align="center" valign="top"><img src="assets/screenshots/02-now-playing.png" alt="Now Playing with artwork-led player controls" width="205"><br><sub><b>Now Playing</b></sub></td>
    <td align="center" valign="top"><img src="assets/screenshots/08-telegram-playlist.png" alt="Telegram playlist with playback and queue actions" width="205"><br><sub><b>Telegram Playlist</b></sub></td>
  </tr>
</table>
<p align="center"><sub>Actual Meowzix Android screenshots</sub></p>

---

## Features

### Music from your phone and Telegram

Meowzix unifies music indexed from your Android device with audio from **Telegram Saved Messages, selected chats, groups, and channels**. Each chosen Telegram chat can act as a playlist — you don't have to copy every song into your phone's music folder or keep the Telegram app open.

- **Persistent library:** Imported tracks remain organized across app restarts, with cloud/offline availability shown per track.
- **Offline downloads:** Save Telegram music for later listening, track download progress, and retry failed transfers.
- **Telegram sharing:** Send or forward tracks using an in-app chat picker, including chat folders and search.
- **Source-independent playback:** Local audio continues working when Telegram or the network isn't available.

<p align="center"><img src="assets/screenshots/05-telegram-sources.png" alt="Telegram chat source selector with selected music sources" width="285"><br><sub><b>Telegram source management</b></sub></p>

### Playback and queue

Built on **Media3 / ExoPlayer**, Meowzix supports uninterrupted background listening with lock-screen controls, media notifications, Bluetooth/headset buttons, and audio-focus handling.

- **Editable queue:** Play Next, add, remove, and reorder tracks; resume the previous session.
- **Playback modes:** Ordered play, repeat options, and separate Pure/Smart Shuffle modes.
- **Sleep Timer:** Stop at a selected time or at the end of the track, with optional fade.
- **Seamless transitions:** Gapless and configurable crossfade, plus loudness handling where supported.

<p align="center"><img src="assets/screenshots/03-queue.png" alt="Up Next queue with playback controls and draggable tracks" width="285"><br><sub><b>Editable playback queue</b></sub></p>

### Smart Shuffle and personalized listening

**Pure Shuffle** randomly orders eligible tracks without repeats inside a cycle. It never uses your preferences. **Smart Shuffle** is different: it learns locally from skips, completions, favorites, listening time, and recent history to help choose what plays next.

Personalized mixes and exploration help surface less-played music instead of only replaying the same favorites. The source-agnostic recommendation pipeline can learn from local and Telegram tracks; it does **not** need a cloud AI service.



### Lyrics and a more expressive player

- **Synchronized lyrics:** A compact three-line view highlights the current lyric while fading adjacent lines; expand it to scroll through the full song.
- **Follow along:** Lyrics automatically follow playback and support manual scrolling.
- **Artwork-led design:** Glass-inspired surfaces, animated transitions, album-art accents, and customizable light/dark colors.
- **Personal themes:** Preset or custom accents, with Material You integration on supported Android versions.

<table align="center">
  <tr>
    <td align="center" valign="top"><img src="assets/screenshots/06-synced-lyrics.png" alt="Player showing synchronized lyrics and visual waveform seekbar" width="285"><br><sub><b>Synced lyrics + waveform</b></sub></td>
    <td align="center" valign="top"><img src="assets/screenshots/04-expanded-lyrics.png" alt="Expanded synchronized lyrics view" width="285"><br><sub><b>Expanded lyrics</b></sub></td>
  </tr>
</table>

### Sound and visual controls

- **Equalizer:** Adjust the audio session with presets and available device-supported effects.
- **Audio visualizer:** Live spectrum/waveform views and selectable seekbar styles.
- **Output controls:** Integration with Android audio routing and connected playback devices.

Effect availability varies with Android hardware and the audio route.



### Library, search, and organization

Browse songs, artists, albums, favorites, playlists, and recent listening history. Search works across the unified library, with normalization designed to handle Persian and English text.

There are also tools for **duplicate management**, locally stored library data, and **backup/restore**. Your playlists, queue, and preferences are managed within Meowzix.

<p align="center"><img src="assets/screenshots/07-library-search.png" alt="Music library with filters and the contextual search field" width="285"><br><sub><b>Library filters + contextual search</b></sub></p>

### Music beyond the app

Control playback from the **animated vinyl home-screen widget**, complete with a spinning record and moving tonearm. The Android media notification can show track metadata, progress, and configurable controls. An **Android Auto** media-library integration is also implemented for compatible hosts.



> **Compatibility:** Some audio effects and Android integrations depend on the device. Source-code checks do not replace physical-device and Android Auto host testing. See [verification status](docs/PRODUCT_IMPROVEMENTS_STATUS.md).

## Installation

Download the latest release-signed APK from **[GitHub Releases](https://github.com/BehradHZ/meowzix/releases/latest)**.

1. Install on **Android 8.0 (API 26) or newer**.
2. Allow access to device audio for local music.
3. Optionally connect Telegram and select the chats containing your music.

The release also includes a checksum file for APK verification.

## Build from source

Requires **JDK 17**, **Android SDK 37**, and the included Gradle wrapper.

~~~bash
git clone https://github.com/BehradHZ/meowzix.git
cd meowzix
./gradlew :app:assembleDebug
~~~

On Windows, use <code>gradlew.bat</code>. For Telegram-enabled builds, supply your own API credentials outside version control using the [setup guide](docs/setup/TELEGRAM_CREDENTIALS.md).

## Architecture and privacy

**Kotlin · Jetpack Compose · Material 3 · Media3 · Room · Hilt · Coroutines / Flow · TDLib**

The library models a song separately from its device, Telegram, or downloaded sources. Listening history, recommendation training, and personalization state are kept **on-device by default**; the player does not require a developer-operated backend.

[System specification](docs/MEOWZIX_SYSTEM_SPEC.md) · [Recommendation architecture](docs/architecture/ADR_012_LOCAL_RECOMMENDATIONS.md) · [Contributing](CONTRIBUTING.md) · [AGPL-3.0 License](LICENSE)

---

<p align="center"><sub>Made for music that lives everywhere.</sub></p>
