# Increment 11 — Smart Shuffle v1

Status: complete

Smart Shuffle is a third playback mode, separate from ordered playback and Pure Shuffle. It builds candidates from canonical Tracks across every available source, applies offline constraints, and orders them with deterministic seeded weighted selection.

The local heuristic combines smoothed global and time-bucket affinity, discovery, freshness, artist diversity, favorites, and short-session early-skip penalties. Every score retains a debug breakdown and human-readable explanation. Small libraries relax current-track exclusion so queue generation cannot deadlock.

Pure Shuffle remains independent and does not read listening history or recommendation scores. Smart Shuffle needs no network ML service: all behavior statistics and scoring stay on device.

Verification:

- fixed seeds produce deterministic Smart queues;
- repeated early skips lower score;
- established time-bucket behavior changes affinity;
- unseen tracks keep an exploration bonus;
- hidden, unavailable, and offline-ineligible candidates are excluded;
- a one-track library remains playable.
