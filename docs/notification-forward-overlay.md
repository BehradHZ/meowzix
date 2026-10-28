# Notification Forward: direct translucent Telegram picker

The MediaSession Forward command launches `TelegramForwardActivity` directly
with an immutable activity `PendingIntent`. No opaque black gateway frame is
present. Android SystemUI/OEM firmware controls the notification-shade
transition; a direct translucent activity cannot guarantee automatic shade
dismissal on every Android build. No hidden status-bar APIs or system-overlay
permission are used.

- The player and notification use the same rocket drawable.
- Both entry points provide the Telegram folder chips and searchable chats.
- A newly accepted first send arms a 2-second idle close. Touching, scrolling,
  selecting folders, or typing in the picker before expiry leaves it open.
- The floating card remains transparent around its edges and uses the available
  screen height (minus small edge and system-bar insets), allowing more visible
  chats. The player sheet also extends nearly to the top.
- The selected track ID/title/artist remain pinned across notification launch.
- Enqueue failures and duplicate/already-sent attempts do not arm auto-close.

**Real-device QA:** Check Forward from Android 13+ expanded media controls,
backgrounded and foregrounded Meowzix, different OEM notification shades,
themed icons, and keyboard open/closed. Verify tapping or scrolling during the
2-second countdown prevents close, while no interaction closes the picker.

