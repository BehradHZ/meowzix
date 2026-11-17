<p align="center">
  <img src="app/src/main/res/drawable/meowzix_logo.jpg" alt="Meowzix app icon" width="116">
</p>

<h1 align="center">Meowzix</h1>

<p align="center">
  <strong>Your music, wherever it lives.</strong>
</p>

<p align="center">
  A beautiful, local-first music player for Android.<br>
  Bring the music on your phone and in your Telegram chats together — in one place.
</p>

<p align="center">
  <a href="https://github.com/BehradHZ/meowzix/releases/latest">
    <img alt="Latest release" src="https://img.shields.io/github/v/release/BehradHZ/meowzix?logo=github&label=Release&style=for-the-badge">
  </a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&style=for-the-badge">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&style=for-the-badge">
  <a href="LICENSE">
    <img alt="AGPL-3.0 license" src="https://img.shields.io/badge/License-AGPL--3.0-007EC6?style=for-the-badge">
  </a>
</p>

<p align="center">
  <a href="https://github.com/BehradHZ/meowzix/releases/latest"><strong>Download APK</strong></a>
  ·
  <a href="#screenshots">Screenshots</a>
  ·
  <a href="#features">Features</a>
  ·
  <a href="#build-from-source">Build from source</a>
  ·
  <a href="#architecture">Architecture</a>
</p>

<p align="center">
  <a href="https://github.com/BehradHZ/meowzix/actions/workflows/android.yml">
    <img alt="Android CI" src="https://github.com/BehradHZ/meowzix/actions/workflows/android.yml/badge.svg">
  </a>
</p>

---

## Screenshots

<!-- SCREENSHOT GALLERY
Place the four portrait screenshots at:
  assets/screenshots/01-library.png
  assets/screenshots/02-now-playing.png
  assets/screenshots/03-telegram-playlist.png
  assets/screenshots/04-live-lyrics.png
When images are ready, replace "Screenshot coming soon" in each cell with:
  <img src="assets/screenshots/01-library.png" alt="Meowzix library" width="190">
Use the corresponding path and alt text for the other three cells.
Do not add image tags until the files exist, to avoid broken images.
-->

<table align="center">
  <tr>
    <td align="center" width="190">
      <strong>Library</strong><br>
      <sub>Screenshot coming soon</sub>
    </td>
    <td align="center" width="190">
      <strong>Now Playing</strong><br>
      <sub>Screenshot coming soon</sub>
    </td>
    <td align="center" width="190">
      <strong>Telegram Playlists</strong><br>
      <sub>Screenshot coming soon</sub>
    </td>
    <td align="center" width="190">
      <strong>Live Lyrics</strong><br>
      <sub>Screenshot coming soon</sub>
    </td>
  </tr>
</table>

<details>
<summary><strong>More screenshots</strong> — Queue, Smart Shuffle, Downloads, Widget, Equalizer & Search</summary>

<!-- ADDITIONAL SCREENSHOTS
  assets/screenshots/05-queue.png
  assets/screenshots/06-smart-shuffle.png
  assets/screenshots/07-downloads.png
  assets/screenshots/08-widget.png
  assets/screenshots/09-equalizer.png
  assets/screenshots/10-search.png
Replace each placeholder with an image once its file has been added.
-->

| Queue | Smart Shuffle | Downloads |
|:---:|:---:|:---:|
| _Screenshot coming soon_ | _Screenshot coming soon_ | _Screenshot coming soon_ |

| Home Screen Widget | Equalizer / Visualizer | Search |
|:---:|:---:|:---:|
| _Screenshot coming soon_ | _Screenshot coming soon_ | _Screenshot coming soon_ |

</details>

## Why Meowzix?

Most music players only see the files already on your phone. Meowzix also lets you turn **music from Telegram chats and channels into playlists**, without giving up the reliability of a native Android player.

- **One music library.** Play local audio alongside tracks from selected Telegram chats, channels, and Saved Messages.
- **Your music, even offline.** Keep Telegram tracks available on your device; local files work without a Telegram connection.
- **A player that learns your taste.** Smart Shuffle uses on-device listening signals to adapt recommendations over time, while Pure Shuffle stays independent of personalization.
- **Privacy by design.** Your listening history and personalization model stay on your device by default. No Meowzix account or developer-operated cloud recommender is required.

## Features

### Music library & Telegram

- Browse music from your device and the Telegram sources **you explicitly select**.
- Organize imported chats as playlists, alongside local and custom playlists.
- Search your library, manage favorites, and explore your listening history.
- Download Telegram tracks for offline listening and see their availability.
- Manage duplicate tracks and back up or restore local app data.
- Send or forward music through Telegram integration.

### Playback & queue

- Background playback powered by **Media3 / ExoPlayer**, with Android media controls.
- Reorder the queue, Play Next, repeat, and restore listening sessions.
- **Pure Shuffle:** a randomized, no-repeat cycle; not influenced by your listening history.
- **Smart Shuffle:** personalized on-device track ranking with discovery and diversity.
- Sleep timer, equalizer, visualizer, loudness controls, and supported gapless/crossfade transitions.
- Bluetooth/headset media controls and a home-screen playback widget.

### Designed around the music

- Glass-inspired interface with artwork-led visuals and fluid transitions.
- Full-screen Now Playing with gestures and contextual actions.
- Synchronized lyrics with an automatically following lyric view.
- Personalized mixes, smart playlist tools, and adaptable playback controls.
- Android Auto media-library browsing through the existing phone player.

> **Compatibility note:** Audio effects, codecs, Telegram connectivity, and some Android Auto behavior depend on the device and environment. Certain features still need real-device or car-host validation; see the [implementation and verification status](docs/PRODUCT_IMPROVEMENTS_STATUS.md).

## Download & installation

Get the latest Android APK from **[GitHub Releases](https://github.com/BehradHZ/meowzix/releases/latest)**.

1. Download the Meowzix APK from the latest release assets.
2. Optionally check its SHA-256 against the release's <code>SHA256SUMS.txt</code>.
3. Install it on a device running **Android 8.0 (API 26) or newer**.
4. Allow access to your device's audio to import local music. Connecting Telegram is optional.

You may need to allow installation from your browser or file manager. Only download APKs from a source you trust.

## How it works

### From Telegram to your library

1. Connect your Telegram account inside Meowzix.
2. Choose the chats, channels, or Saved Messages you want to use as music sources.
3. Browse their music as playlists in Meowzix.
4. Play the songs and optionally download them for offline use.

Your phone's existing music works independently of those steps. Meowzix is a dedicated music player, **not a replacement Telegram chat client**.

### Pure Shuffle vs. Smart Shuffle

| | Pure Shuffle | Smart Shuffle |
|---|---|---|
| **Purpose** | Random playback without repeats within a cycle | Adapt playback to your listening habits |
| **Uses listening history?** | No | Yes, locally |
| **Uses preferences & context?** | No | Yes, where available |
| **Approach** | Randomized queue permutation | On-device ranking with exploration and diversity |
| **Needs a cloud AI service?** | No | No |

Smart Shuffle learns from actions such as plays, completions, skips, and favorites. Its learning pipeline is **source-agnostic**: a song can contribute regardless of whether it came from device storage or Telegram. It can fall back to non-learned ranking when there is not enough data.

## Architecture

Meowzix is built for **Android**, with a local-first architecture that separates music sources, the unified track model, playback, and personalization.

~~~text
        Android MediaStore        Telegram (TDLib)
                |                        |
                +-----------+------------+
                            |
                    Unified Library
                    (Tracks & Sources)
                            |
               +------------+------------+
               |                         |
          Queue / Playback          Listening Events
               |                         |
        Media3 / ExoPlayer           On-device Model
               |                         |
        Android media UI  <---  Smart Shuffle ranking
~~~

| Area | Technology |
|---|---|
| Language | **Kotlin** |
| UI | **Jetpack Compose**, Material 3 |
| Playback | **Jetpack Media3 / ExoPlayer** |
| Telegram integration | **TDLib** |
| Persistence | **Room**, DataStore |
| Dependency injection | **Hilt** |
| Async/background work | **Coroutines**, Flow, WorkManager |
| Build | **Gradle Kotlin DSL** |

The underlying library distinguishes a logical track from the local file, Telegram message, or downloaded copy that can play it. Recommendation logic operates on the track instead of being tied to a particular provider.

For implementation details, see the [system specification](docs/MEOWZIX_SYSTEM_SPEC.md) and [local recommendation architecture](docs/architecture/ADR_012_LOCAL_RECOMMENDATIONS.md).

## Build from source

**Requirements:** JDK 17, Android SDK (compile SDK 37), and the included Gradle wrapper.

~~~bash
git clone https://github.com/BehradHZ/meowzix.git
cd meowzix
./gradlew :app:assembleDebug
~~~

On Windows, use <code>gradlew.bat</code> instead of <code>./gradlew</code>. The debug APK is generated in <code>app/build/outputs/apk/debug/</code>.

Telegram integration requires developer-owned API credentials supplied **outside version control**. Follow the [Telegram credentials setup guide](docs/setup/TELEGRAM_CREDENTIALS.md). You can build and use the local music player without Telegram credentials.

To run the source checks:

~~~bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
~~~

Android instrumentation tests additionally require an emulator or connected device:

~~~bash
./gradlew :app:connectedDebugAndroidTest
~~~

## Privacy & security

- Listening history, preference data, and recommendation models are local by default.
- You choose which Telegram sources to connect; Meowzix does not need to scan every chat.
- The app's core music player does not depend on a custom backend.
- Telegram API credentials and app-signing secrets must never be committed to the repository.
- Your device audio remains usable when Telegram is disconnected.

## Project status

Meowzix is under active development. The source includes advanced playback, personalization, and Android integration features, but **implementation does not imply full validation on every physical device**. Check the [verification ledger](docs/PRODUCT_IMPROVEMENTS_STATUS.md) and [releases](https://github.com/BehradHZ/meowzix/releases) for the current details.

## Contributing

Contributions, bug reports, and feedback are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for development instructions and testing expectations.

## License

Meowzix is licensed under the **[GNU Affero General Public License v3.0](LICENSE)**.

---

<p align="center">
  Built with care for people who keep their music everywhere.<br>
  <sub>Meowzix · Android · Local-first</sub>
</p>
