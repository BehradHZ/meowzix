# Notification-triggered Telegram Forward overlay

The Media3 custom Share command is still exposed only to phone controllers. It targets
`TelegramForwardActivity` through a direct immutable activity PendingIntent (no notification
broadcast/service trampoline). The activity is a user-visible permission gateway.

- The user explicitly taps **Enable floating sharing**. This invokes Android's special-app-access
  `ACTION_MANAGE_OVERLAY_PERMISSION` page. This is not a regular runtime permission:
  `SYSTEM_ALERT_WINDOW` must be declared and `Settings.canDrawOverlays` is rechecked after
  returning from Settings. No programmatic granting or repeated automatic prompts.
- With access granted, the gateway starts an unexported `TelegramForwardOverlayService`
  using the pinned notification track ID/title/artist and immediately finishes.
- The service owns one focusable full-screen `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
  window, hosting a `ComposeView` with lifecycle, saved-state and ViewModel-store owners.
  The foreground app remains underneath. The window closes on explicit Close, scrim tap,
  or service destruction; the keyboard can be used in chat search.
- The original Forward picker content is shared: a ModalBottomSheet inside the app and
  the same glass body drawn directly in the WindowManager overlay, **not** a nested dialog
  requiring an Activity window token. Sending uses the existing TelegramForwardRepository,
  persisted send jobs, retry and cancel controls, and existing settings defaults.
- If access is denied or restricted by the device, **Continue with in-app picker** preserves
  the current Activity flow. A revoked permission is checked again before attaching a window.
  No blanket screen capture, accessibility, or unrelated background overlay permission is used.
- Overlay permission does not permit drawing above Android's privileged/system-security windows.
  Device vendors and policy can still restrict display. Android 15+ limits foreground-service
  launches from the background even with this permission; this implementation starts the
  short-lived overlay service from the user-opened gateway Activity, not from an idle process.

Validation: CI `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and emulator
`:app:connectedDebugAndroidTest`. Manual device QA should check first-time grant, allow,
deny, revoke, foreground app preservation, lock screen restrictions, keyboard, scrim dismissal,
and a full Telegram send. See Android's Settings.canDrawOverlays and SYSTEM_ALERT_WINDOW docs.
