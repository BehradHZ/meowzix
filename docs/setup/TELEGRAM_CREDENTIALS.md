# Telegram API credentials

Meowzix uses TDLib to connect directly to a user's Telegram account. TDLib requires an application API ID and API hash before it can begin authorization.

## Obtain credentials

1. Sign in to [my.telegram.org](https://my.telegram.org/) with an active Telegram account.
2. Open **API development tools**.
3. Create an application and record its `api_id` and `api_hash`.
4. Review and comply with Telegram's API Terms of Service before distributing the app.

The sample API ID from Telegram's open-source clients is limited to testing those clients and must not be used for a released Meowzix build.

## Inject credentials locally

Use either Gradle properties or environment variables. Never commit real credentials to this repository.

### User-level Gradle properties

Add these entries to `%USERPROFILE%\.gradle\gradle.properties` on Windows:

```properties
MEOWZIX_TELEGRAM_API_ID=123456
MEOWZIX_TELEGRAM_API_HASH=replace_with_your_api_hash
```

### Environment variables

Set both variables before invoking Gradle:

```powershell
$env:MEOWZIX_TELEGRAM_API_ID = "123456"
$env:MEOWZIX_TELEGRAM_API_HASH = "replace_with_your_api_hash"
./gradlew.bat :app:assembleDebug
```

If an existing Gradle daemon was started before the environment variables were set, run `./gradlew.bat --stop` once and rebuild.

## Runtime behavior

- A build without both credentials shows **Telegram credentials required** and does not start TDLib.
- A configured build initializes TDLib asynchronously and follows the authorization state it reports: phone number, code, email, registration, two-step verification password, or confirmation on another device.
- TDLib stores its database and session under the app's private no-backup directory. Reopening the app reuses a valid session instead of requesting another login.
- **Log out** sends TDLib's logout request, waits for the session to close, and starts a clean authorization client.
- Authentication values are passed directly to TDLib, cleared from Compose input state after submission, and never written to application logs.

## Verification

Without using a real account, run:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

The device suite loads the packaged native library, creates a real TDLib client, receives `authorizationStateWaitTdlibParameters` asynchronously, and closes the client. A complete live sign-in and logout smoke test requires valid credentials and interactive access to the Telegram account.
