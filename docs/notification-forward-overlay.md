# Notification Forward: transparent, bottom-anchored Telegram picker

The Media3 custom Forward command remains phone-only. Its handler in
`PlaybackService.onCustomCommand` launches an explicit immutable PendingIntent to
`TelegramForwardActivity`, a **visible translucent Activity** with no background dim.
Unlike the old `Theme.NoDisplay` trampoline followed by a WindowManager overlay,
this is a real Activity transition. Android can therefore collapse the expanded
notification shade as part of its normal system UI navigation.

- The previous app stays visible behind the transparent Activity, and reappears when
  the picker closes. Only the rounded picker card casts a shadow: **no dark fullscreen
  scrim** is rendered.
- The card sits above the navigation/keyboard insets at the bottom of the screen.
  Its height adapts to content and is capped at 82% of the available viewport; long
  contents and the chat list remain scrollable.
- Destination chat titles and the Forward heading explicitly use high-contrast white.
  A mostly opaque dark-glass card makes the text readable over arbitrary apps.
- The notification route does **not** depend on special `SYSTEM_ALERT_WINDOW`
  permission. Existing in-app Forward UI and send jobs still share
  `TelegramForwardSheet`, `TelegramForwardRepository`, and persisted options.
  The selected track is pinned from notification metadata, even before the local
  library has finished loading.
- Tapping outside the picker or using Back dismisses the Activity without cancelling
  playback or deleting the media notification.

**Platform boundary:** Android owns the notification shade, and vendor System UI
implementations differ. Third-party apps targeting Android 12+ cannot legally force
dismissal via `ACTION_CLOSE_SYSTEM_DIALOGS` or hidden status-bar APIs. The visible
Activity navigation is the supported approach, but shade auto-collapse must still
be confirmed on real devices (including Xiaomi/HyperOS variants).

CI runs unit tests, Lint, debug assembly and instrumentation where configured.
The Android test verifies that the Forward command leaves the Activity visible and
that dismissal returns to the previous Activity. A real-device QA pass must additionally
expand the shade, tap the media Forward action, verify that the shade fully retracts,
and confirm that the previous app remains visible behind the new bottom picker.

The former `TelegramForwardOverlayService` and `TelegramForwardLaunchActivity`
remain in the source for compatibility with older entry points, but the system-media
Forward action does not route through them.
