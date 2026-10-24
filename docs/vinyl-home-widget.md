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
