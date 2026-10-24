# Notification Forward: opaque gateway, then floating Telegram picker

On Android 13+, a SystemUI MediaSession Forward button dispatches a custom command
to `PlaybackService`; it is **not** a SystemUI-sent activity PendingIntent.
Sending a translucent activity PendingIntent directly from the service can leave
Quick Settings/notification shade open in front of the picker.

The service now starts an explicit immutable PendingIntent for
`NotificationForwardGatewayActivity`, a **real opaque Activity** in its own
non-recents task. After the gateway resumes and receives window focus, it opens
the existing translucent `TelegramForwardActivity` with the selected track's
pinned ID/title/artist and immediately finishes. The old app can therefore remain
visible behind the floating sheet after this handoff. The transient gateway
doesn't require overlay permission, a timer, special accessibility access, or
any status-bar/internal APIs.

- The Forward sheet keeps the same searchable chat picker, send options, dark
  glass card, bottom anchor, insets and height cap.
- A loading state remains in the existing picker while its ViewModel prepares
  the selected track and Telegram chats.
- A repeated Forward intent refreshes its pinned selected-track snapshot.
- Closing the picker returns to the previously visible app and leaves playback
  and notification intact.
- The retired NoDisplay/WindowManager paths stay in source for compatibility;
  system-media Forward does not use them.

**Platform boundary:** SystemUI/OEM firmware decides when its own shade retracts.
On Android 13+ there is no public app API to force-close it after receiving a
MediaSession custom command. `ACTION_CLOSE_SYSTEM_DIALOGS` is restricted, and
hidden status-bar APIs must not be used. The opaque gateway provides a normal
foreground activity transition instead of placing an overlay underneath the
shade. This improves the route but does not guarantee shade animation on every
OEM or lock-screen configuration.

CI covers session command dispatch and Activity lifecycle handoff. A real-device
QA pass must expand Quick Settings, tap Forward from the media controls, confirm
the shade retracts and the Telegram picker is interactive, and repeat when
Meowzix is backgrounded, the phone is locked/unlocked, and Forward is tapped twice.
