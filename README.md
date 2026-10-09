<div align="center">
  <img src="app/src/main/res/drawable/meowzix_logo.jpg" alt="Meowzix logo" width="112">

  # Meowzix

  **Your music, wherever it lives.**

  A local-first Android music player that brings device audio and music from your chosen Telegram chats into one library.

  [![Android CI](https://github.com/BehradHZ/meowzix/actions/workflows/android.yml/badge.svg)](https://github.com/BehradHZ/meowzix/actions/workflows/android.yml)
  [![Latest release](https://img.shields.io/github/v/release/BehradHZ/meowzix?label=release)](https://github.com/BehradHZ/meowzix/releases/latest)
  ![Platform](https://img.shields.io/badge/platform-Android%208%2B-3DDC84)
  ![License](https://img.shields.io/badge/license-AGPL--3.0-blue)

  [Download](https://github.com/BehradHZ/meowzix/releases/latest) · [Features](#features) · [Build](#build-from-source) · [Architecture](#architecture)
</div>

## Overview

Meowzix is designed for people who keep music both on their phone and in Telegram. It combines those sources without making Telegram a prerequisite for local playback. Your queue, playlists, listening history, and recommendations are managed on the device.

## Features

| | |
| --- | --- |
| **Unified music library** | Browse local audio and tracks imported from selected Telegram chats, channels, and Saved Messages. |
| **Offline playback** | Play device files, keep Telegram tracks available offline, and manage downloads. |
| **Reliable queue** | Play Next, reorder, repeat, recover playback sessions, and use a separate no-repeat **Pure Shuffle**. |
| **Smart Shuffle** | Personalized on-device ranking that learns from listening behavior and context, with a non-personalized fallback. |
| **Modern player** | Glass-inspired interface, artwork-led Now Playing, synchronized lyrics, and playback gestures. |
| **Audio controls** | Equalizer, playback visualization, loudness handling, and gapless/crossfade controls where supported. |
| **Library tools** | Search, favorites, playlists, history, backup/restore, and duplicate management. |
| **Android integration** | Background playback, media notification, home-screen widget, and Android Auto browsing. |

Availability of some audio effects, Telegram behavior, and automotive features depends on the Android device and environment. See [implementation and verification status](docs/PRODUCT_IMPROVEMENTS_STATUS.md) for remaining device-validation work.

## Download

Get the latest signed Android package from [GitHub Releases](https://github.com/BehradHZ/meowzix/releases/latest).

1. Download the `Meowzix-v*.apk` file from the release assets.
2. Optionally verify its SHA-256 checksum against `SHA256SUMS.txt`.
3. Install the APK on an Android device running **Android 8.0 (API 26) or newer**.

Installation outside a store may require enabling installation from the file manager/browser you use. Do not install APKs from untrusted mirrors.

## Build from source

**Requirements:** Android SDK (compile SDK 37), JDK 17, and the included Gradle wrapper.

```bash
git clone https://github.com/BehradHZ/meowzix.git
cd meowzix
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

To use Telegram integration in local builds, obtain your own Telegram API credentials and configure them **outside version control**. Follow [Telegram credentials setup](docs/setup/TELEGRAM_CREDENTIALS.md). The local music player can be built without Telegram credentials.

For automated validation:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Android instrumentation tests require a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Architecture

Meowzix is built with **Kotlin**, **Jetpack Compose**, **Media3 / ExoPlayer**, **Room**, **Hilt**, **Coroutines / Flow**, and **TDLib**.

```text
Device media ─────┐
                  ├── Unified track library ── Queue / Playback
Telegram / TDLib ─┘              │
                                └── On-device listening model
```

The music model distinguishes a logical track from its underlying local, offline, or Telegram sources. **Pure Shuffle** remains random; **Smart Shuffle** can use local listening signals to adapt track selection. Personalization does not require a cloud model service.

See [system specification](docs/MEOWZIX_SYSTEM_SPEC.md) and [architecture notes](docs/architecture/ADR_012_LOCAL_RECOMMENDATIONS.md) for details.

## Privacy and security

- Listening history and preference models are stored locally by default.
- Telegram connectivity is optional; local playback continues without a Telegram session.
- Telegram API keys and release signing credentials are supplied outside the repository.
- The app does not require a developer-operated backend for core playback or personalization.

## Contributing

Development and testing guidelines are in [CONTRIBUTING.md](CONTRIBUTING.md). Please include clear reproduction steps for bug reports and describe how changes were verified in pull requests.

## License

Licensed under the [GNU Affero General Public License v3.0](LICENSE).
