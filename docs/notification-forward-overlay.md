# Notification Share: centered floating Telegram picker

The Media3 custom Share command remains phone-only. It is handled by
`PlaybackService.onCustomCommand`, which sends an explicit immutable PendingIntent to
`TelegramForwardLaunchActivity`, an unexported `Theme.NoDisplay`, no-history,
no-recents Activity with no UI. **The main Meowzix Activity never opens.**

This invisible gateway exists solely for the user-initiated standard Android
activity transition, which lets System UI dismiss the expanded notification shade
without prohibited `ACTION_CLOSE_SYSTEM_DIALOGS`, private status bar APIs, or Accessibility.
Shade collapse is controlled by the OS and may vary with manufacturer/system UI.
The *ongoing playback notification* is retained so media controls continue to work.

- When `Settings.canDrawOverlays` is true, the gateway starts the unexported
  `TelegramForwardOverlayService`, immediately finishes before rendering a frame,
  and preserves the other app underneath.
- When access was never granted or revoked, the gateway presents the existing
  `TelegramForwardActivity` only for explicit special app access onboarding
  (`ACTION_MANAGE_OVERLAY_PERMISSION`); manual in-app sharing remains available.
  The app cannot programmatically grant overlay permission.
- The service creates a focusable `TYPE_APPLICATION_OVERLAY` window with a dim
  touch-blocking scrim and a **centered, max-460dp-wide rounded glass card**.
  The card bounds itself to the available screen/keyboard height; excess content
  scrolls and chat results have a bounded list. Tapping outside or Close dismisses
  the window without touching the app beneath.
- Forward actions share the same composable content and existing
  `TelegramForwardRepository` persistence, options, progress, retry/cancel and settings
  logic as the in-app picker. The selected notification track remains pinned when playback
  advances. The service has a lifecycle, saved-state and ViewModelStore owner.
- `SYSTEM_ALERT_WINDOW` cannot cover privileged/system-security windows or override
  manufacturer policies; on the lock screen the system may restrict presentation.

CI validates Unit Test, Lint, Debug Assemble and emulator instrumentation. The
instrumentation grant path triggers the actual MediaSession Share command, confirms that
the WindowManager overlay stays attached after the invisible gateway finishes, and asserts
that the visible permission Activity does not appear when access is already granted.
Real-device QA should additionally check shade dismissal and current-app preservation on
the target vendor build.
