<p align="center">
  <img src="app/src/main/res/drawable/meowzix_logo.jpg" alt="Meowzix logo" width="112">
</p>

<h1 align="center">Meowzix</h1>

<p align="center"><strong>Your music, wherever it lives.</strong></p>
<p align="center">A local-first Android music player that brings your device audio and Telegram music together.</p>

<p align="center">
  <a href="https://github.com/BehradHZ/meowzix/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/BehradHZ/meowzix?label=Release&logo=github&style=for-the-badge"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&style=for-the-badge">
  <a href="LICENSE"><img alt="AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="https://github.com/BehradHZ/meowzix/releases/latest"><strong>Download APK</strong></a> ·
  <a href="#screenshots">Screenshots</a> ·
  <a href="#features">Features</a> ·
  <a href="#build">Build</a>
</p>

---

## Screenshots

<!-- Add portrait screenshots to these paths, then replace each "Screenshot coming soon"
     with <img src="assets/screenshots/FILE.png" alt="SCREEN NAME" width="280">.
     No broken image links are displayed before the files exist.
  assets/screenshots/01-library.png
  assets/screenshots/02-now-playing.png
  assets/screenshots/03-telegram-playlist.png
  assets/screenshots/04-live-lyrics.png
  assets/screenshots/05-queue.png
  assets/screenshots/06-smart-shuffle.png
  assets/screenshots/07-downloads.png
  assets/screenshots/08-widget.png
  assets/screenshots/09-equalizer.png
  assets/screenshots/10-search.png
-->

<table align="center">
  <tr>
    <td align="center" width="310">
      <strong>Library</strong><br>
      <sub>Screenshot coming soon</sub><br><br><br><br><br>
    </td>
    <td align="center" width="310">
      <strong>Now Playing</strong><br>
      <sub>Screenshot coming soon</sub><br><br><br><br><br>
    </td>
  </tr>
  <tr>
    <td align="center" width="310">
      <strong>Telegram Playlists</strong><br>
      <sub>Screenshot coming soon</sub><br><br><br><br><br>
    </td>
    <td align="center" width="310">
      <strong>Live Lyrics</strong><br>
      <sub>Screenshot coming soon</sub><br><br><br><br><br>
    </td>
  </tr>
  <tr>
    <td align="center" width="310">
      <strong>Queue</strong><br>
      <sub>Screenshot coming soon</sub><br><br><br><br><br>
    </td>
    <td align="center" width="310">
      <strong>Smart Shuffle</strong><br>
      <sub>Screenshot coming soon</sub><br><br><br><br><br>
    </td>
  </tr>
</table>

<details>
<summary><strong>More screenshots</strong> — Downloads, Widget, Equalizer & Search</summary>

| Screen | Preview |
|:--|:--:|
| **Offline Downloads** | _Screenshot coming soon_ |
| **Home Screen Widget** | _Screenshot coming soon_ |
| **Equalizer / Visualizer** | _Screenshot coming soon_ |
| **Search** | _Screenshot coming soon_ |

</details>

## Features

- **Local + Telegram library:** Play music from your phone, Saved Messages, and selected Telegram chats or channels as playlists.
- **Offline-first:** Download Telegram tracks and keep playing your device music without a network connection.
- **Two shuffle modes:** Pure Shuffle avoids repeats within a randomized cycle; Smart Shuffle learns from on-device listening behavior.
- **Modern playback:** Glass-inspired UI, synchronized lyrics, queue controls, sleep timer, equalizer, visualizer, and a home-screen widget.
- **Made for Android:** Media3 background playback, system media controls, and Android Auto library integration.

> Some features and audio effects depend on the device. See [verification status](docs/PRODUCT_IMPROVEMENTS_STATUS.md) for remaining real-device checks.

## Download

Install the latest APK from **[GitHub Releases](https://github.com/BehradHZ/meowzix/releases/latest)**. Requires **Android 8.0+**. Connecting Telegram is optional.

## Build

Requires **JDK 17** and **Android SDK 37**.

~~~bash
git clone https://github.com/BehradHZ/meowzix.git
cd meowzix
./gradlew :app:assembleDebug
~~~

Windows: use `gradlew.bat`. For Telegram integration, follow the [API credentials guide](docs/setup/TELEGRAM_CREDENTIALS.md); never commit credentials.

## Under the hood

**Kotlin · Jetpack Compose · Media3 / ExoPlayer · Room · Hilt · Coroutines · TDLib**

Tracks from local files and Telegram share a unified library. Listening history and Smart Shuffle personalization stay **on-device by default**; Pure Shuffle is never personalized. Core playback needs no custom backend.

[Architecture & system spec](docs/MEOWZIX_SYSTEM_SPEC.md) · [Contributing](CONTRIBUTING.md) · [AGPL-3.0 License](LICENSE)

---

<p align="center"><sub>Built for music that lives everywhere.</sub></p>
