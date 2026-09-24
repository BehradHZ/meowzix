# Releasing Meowzix on Android

This document describes the first supported release path for Meowzix: a locally signed APK for direct installation and a signed Android App Bundle (AAB) for store upload.

## 1. Preconditions

Before cutting a release:

- `main` must be green in CI.
- Update `versionCode` and `versionName` in `app/build.gradle.kts` when creating a new version.
- Use JDK 17 and the Android SDK/build tools used by the project.
- Never commit Telegram API credentials, a keystore, or signing passwords.

Current first-release version:

```text
versionCode = 1
versionName = 0.1.0
```

## 2. Create the release keystore once

Keep this keystore permanently and back it up securely. Direct APK updates must be signed by the same signing key as the installed app.

From the repository root, run:

```bash
keytool -genkeypair -v \
  -keystore meowzix-release.jks \
  -alias meowzix \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

On Windows PowerShell you can run the same command on one line:

```powershell
keytool -genkeypair -v -keystore meowzix-release.jks -alias meowzix -keyalg RSA -keysize 4096 -validity 10000
```

Do not commit `meowzix-release.jks`.

## 3. Configure signing locally

Copy the example file:

macOS/Linux:

```bash
cp keystore.properties.example keystore.properties
```

Windows PowerShell:

```powershell
Copy-Item keystore.properties.example keystore.properties
```

Then edit `keystore.properties`:

```properties
MEOWZIX_KEYSTORE_FILE=meowzix-release.jks
MEOWZIX_KEYSTORE_PASSWORD=YOUR_KEYSTORE_PASSWORD
MEOWZIX_KEY_ALIAS=meowzix
MEOWZIX_KEY_PASSWORD=YOUR_KEY_PASSWORD
```

`keystore.properties`, `*.jks`, and `*.keystore` are ignored by Git.

You may alternatively provide the same four values as Gradle properties or environment variables:

```text
MEOWZIX_KEYSTORE_FILE
MEOWZIX_KEYSTORE_PASSWORD
MEOWZIX_KEY_ALIAS
MEOWZIX_KEY_PASSWORD
```

## 4. Configure Telegram production credentials

The Android build reads the Telegram credentials from Gradle properties or environment variables:

```text
MEOWZIX_TELEGRAM_API_ID
MEOWZIX_TELEGRAM_API_HASH
```

A convenient local option is to put them in the user-level Gradle properties file, not in this repository.

macOS/Linux:

```text
~/.gradle/gradle.properties
```

Windows:

```text
%USERPROFILE%\.gradle\gradle.properties
```

Example:

```properties
MEOWZIX_TELEGRAM_API_ID=12345678
MEOWZIX_TELEGRAM_API_HASH=replace_with_real_hash
```

If these values are empty, the APK can still be built, but Telegram authentication cannot function correctly.

## 5. Run release checks

Run the existing automated checks before packaging:

macOS/Linux:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Also test the release candidate on a physical Android device, including playback, background playback, notification/system controls, permissions, and the features included in the current increment.

## 6. Build signed APK and AAB

macOS/Linux:

```bash
./gradlew clean :app:assembleRelease :app:bundleRelease
```

Windows PowerShell:

```powershell
.\gradlew.bat clean :app:assembleRelease :app:bundleRelease
```

Expected outputs:

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

Use the APK for direct installation/testing. Use the AAB for Google Play and stores that accept Android App Bundles.

## 7. Verify and install the APK

Verify its signature with the Android SDK build tools:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Install or update it on a connected test device:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

If a debug build of Meowzix is already installed, Android may reject the update because the debug and release signing keys differ. Uninstall the debug app first if necessary. This removes that installation's app data.

## 8. Release naming

For version `0.1.0`, use:

```text
Git tag: v0.1.0
Release title: Meowzix v0.1.0
```

For every later release:

1. increment `versionCode` monotonically;
2. update `versionName`;
3. build using the same signing identity;
4. keep a secure backup of the keystore and passwords;
5. test the signed release artifact before publishing it.

## 9. Store/public-distribution checks

Before a public store release, complete the release-readiness work in the system specification, including privacy policy, Telegram API disclosure/requirements, dependency/license review, accessibility, performance, production credential handling, and current store/platform policy checks.
