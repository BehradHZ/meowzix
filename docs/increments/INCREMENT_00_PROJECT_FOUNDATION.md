# Increment 0 — Project Foundation and Architectural Skeleton

## Objective

Establish a reproducible, runnable Android foundation with clear UI, domain, and data boundaries. Product behavior beyond the architectural skeleton is out of scope.

## Implementation checklist

### A. Reproducible Android project

- [x] Kotlin Android application configured with Gradle Kotlin DSL
- [x] Checked-in Gradle wrapper
- [x] Jetpack Compose UI setup
- [x] Coroutines and Flow dependencies
- [x] JUnit, AndroidX, and coroutine test setup
- [x] CI unit-test and debug-build workflow

### B. Foundation libraries

- [x] Room persistence and schema export
- [x] Hilt dependency injection
- [x] Media3 player/session dependencies without playback behavior
- [x] DataStore preferences dependency

### C. Architecture skeleton

- [x] Package boundaries for core, data, domain, features, navigation, and UI
- [x] Domain-facing `MusicLibraryRepository`
- [x] Domain-facing playback, queue, download, Telegram, history, recommendation, and settings interfaces
- [x] Compose navigation shell with the library as the start destination
- [x] No Compose dependency on Room DAOs, MediaStore, or Telegram internals

### D. Configuration and safety

- [x] Telegram API ID/hash supplied through Gradle properties or environment variables
- [x] Generated `BuildConfig` defaults credentials to empty strings
- [x] No Telegram credential committed
- [x] No playback, Telegram login, or Smart Shuffle behavior implemented

### E. Acceptance verification

- [x] Clean debug build succeeds
- [x] Unit tests run successfully
- [x] Debug APK installs and cold-launches on an Android emulator
- [x] Main activity reaches the resumed state without a runtime crash

## Verification commands

```powershell
.\gradlew.bat clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:installDebug
```

## Non-goals

- real playback
- Telegram login
- Smart Shuffle
