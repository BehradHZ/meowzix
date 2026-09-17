# Increment 4 — Telegram Client Foundation and Authentication

## Objective

Connect Meowzix to a user's Telegram account through an asynchronous TDLib boundary, without importing tracks or adding recommendation behavior.

## Implementation checklist

### A. TDLib integration

- [x] Package the TDLib Java/JNI Android runtime for ARM, ARM64, x86, and x86_64
- [x] Grant network access in the Android manifest
- [x] Keep all TDLib classes behind the domain `TelegramRepository`
- [x] Adapt callback requests to cancellable suspending requests
- [x] Deliver TDLib updates in receive order through a coroutine flow
- [x] Surface request, client-thread, and fatal errors without blocking the UI
- [x] Disable TDLib application log forwarding

### B. Authorization state machine

- [x] Initialize TDLib parameters from injected API credentials
- [x] Support phone-number authorization
- [x] Support Telegram authentication codes
- [x] Support two-step verification passwords
- [x] Support required email address and email-code steps
- [x] Support new-user registration
- [x] Support confirmation on another signed-in device
- [x] Represent ready, logging-out, closing, and closed states
- [x] Handle unsupported Premium-gated authorization explicitly

### C. Session and logout behavior

- [x] Store TDLib database and files in the app-private no-backup directory
- [x] Reuse TDLib's persisted session on app restart
- [x] Send TDLib's logout request rather than deleting files behind the client
- [x] Wait for the closed state before creating a clean authorization client
- [x] Preserve passwords exactly and avoid storing auth inputs in repository state
- [x] Clear Compose auth input immediately after submission

### D. Login UI and setup

- [x] Provide non-blocking UI for every supported authorization step
- [x] Mask two-step verification password input
- [x] Display progress and recoverable request errors
- [x] Provide connected/ready and logout states
- [x] Display an actionable missing-credentials state
- [x] Document how to obtain and inject API credentials without committing them

### E. Verification

- [x] Unit-test TDLib-to-domain mapping, including the 2FA state
- [x] Load the packaged native library on an Android emulator
- [x] Create a real TDLib client and receive the first update asynchronously
- [x] Close the native test client cleanly
- [x] Reach the phone-number state with dummy credentials without submitting an account
- [x] Render the missing-credentials state from the packaged app
- [x] Run assemble, lint, JVM-test, and connected-device gates
- [ ] Complete a live sign-in, authorized-session restart, and logout smoke test with user-owned credentials

## Verification — 2026-09-17

- 23 JVM tests passed.
- 8 Android emulator tests passed, including native TDLib asynchronous startup.
- A credential-free packaged build displayed the setup state without starting TDLib.
- A disposable build with non-account dummy credentials initialized TDLib and reached `authorizationStateWaitPhoneNumber`; no phone number, code, password, or account was submitted.
- Android lint completed with zero errors and 12 non-blocking warnings.
- The ordinary debug artifact was rebuilt without dummy credentials after the smoke test.

## External validation still required

The code paths for ready-state restoration, 2FA submission, and logout are implemented, but completing those three live checks requires private API credentials and interactive access to a Telegram account. Follow [Telegram API credentials](../setup/TELEGRAM_CREDENTIALS.md) locally; credentials and authentication values should not be shared or committed.

## Non-goals

- Telegram track discovery or import
- Telegram media downloads
- Smart Shuffle or recommendation behavior
