# Vinyl Home Screen Widget

The Vinyl widget is separate from the ordinary Playback widget and the in-app
Now Playing artwork pager. Its visible layout contains **only the record**
(including cover art, grooves, spindle and tonearm), on a transparent background.
A single tap on the record sends Media3's existing PLAY_PAUSE media key to
PlaybackService. No widget-local player, queue, previous/next buttons, artist,
title, or invisible action zones are introduced.

A paused record stops rotating and the tonearm retracts. When playback resumes,
it returns to the grooves. Reduced Motion disables ongoing rotation.

## Touch/platform boundary

Android AppWidgetProvider uses RemoteViews hosted in the launcher. It can assign
a PendingIntent on click, but does not expose raw pointer movement or custom
View subclasses. Horizontal swipes are owned by the home-screen pager. Thus a
true horizontal track-skip swipe and finger-driven circular seeking **cannot**
be implemented inside this normal Home Screen widget with public Android APIs.
Do not simulate gestures using unseen click regions, accessibility automation,
system-alert overlays, or undocumented launcher behavior. These gestures would
need a separate in-app interaction surface, which is not automatically opened
by tapping the widget because its tap has the requested Play/Pause semantics.

Widget updates are best effort at the host's permitted rate; Home Screen host
rendering/refresh cadence is not guaranteed.

## Smoothness and host limitations

The prior widget redrew a 280-pixel Bitmap and reinflated its entire RemoteViews
every 250 ms (four distinct phases a second). The updated code renders a
240-pixel Bitmap, updates the full view tree only on state changes, sends only
image deltas via `partiallyUpdateAppWidget`, and derives rotation from
`SystemClock.elapsedRealtime()` so delayed frames are dropped, not queued.
It targets ten image phases per second while playing and stops when paused
or reduced motion is enabled. Third-party launcher update throttling and the
energy cost of continuous widget updates still limit actual smoothness; a
60 fps Android Home Screen RemoteViews widget is not promised.

## Nothing-style gesture shortcut (not a proprietary launcher widget)

Nothing's official October 2026 roadmap says Essential Apps is still closed
beta and widget publishing is paused until 2027. No public external gesture
hosting or media-session integration SDK was identified for Meowzix. Instead,
a separate **Vinyl gestures** home-screen *app shortcut* opens a genuine
Meowzix Activity. It renders the identical vinyl with Compose/native Canvas
at display frame rate and supplies the requested raw-pointer interactions:

- Tap: play/pause the existing playback session.
- Straight swipe left/right: next/previous available queue entry.
- Circular drag clockwise/counterclockwise: seek forward/back, five seconds
  for each quarter turn, clamped to track duration.

The gesture resolver checks actual path curvature so a straight swipe
across the disc is not mistaken for a record scratch. No accessibility
overlay or privileged Nothing/launcher APIs are used. The shortcut is
available on standard Android as well as Nothing phones, but a genuine
gesture-sensitive Nothing Home *widget* cannot ship via the standard
AppWidgetProvider/RemoteViews interface alone.
