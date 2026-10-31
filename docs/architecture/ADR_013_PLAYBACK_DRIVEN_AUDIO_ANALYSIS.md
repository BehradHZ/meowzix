# ADR 013 — Playback-driven, resource-bounded local audio analysis

**Status:** Implemented on `recommendation`; CI and real-device performance verification pending.

## Decision

- Never launch acoustic extraction merely by opening a recommendation surface.
- Once Media3 reports actual playback, enqueue **one replaceable listening-window job**:
  - always the currently playing canonical Track;
  - at most the next two distinct queue Tracks **only when charging**.
- A newer listening window replaces stale speculative work. The existing coordinator's mutex
  serializes all decode work (including optional explicit bulk jobs).
- Use the existing compatible versioned Room feature cache. No audio copy or remote inference
  is created. A Telegram-only source without locally readable bytes is deferred, **not**
  downloaded just for analysis.
- The extractor samples three bounded 10-second regions, decimates PCM toward ~11 kHz
  by block averaging, uses 64 KiB raw PCM input buffers, and has a 25-second per-track
  decode deadline. MediaCodec's platform-managed allocations are outside this buffer budget.
- WorkManager requires battery/storage not low. Before/after extraction, an Android
  resource gate checks Battery Saver, API 29+ thermal state (moderate or worse), and Android
  memory pressure (lowMemory / <128 MiB available). No app can promise an exact
  hardware CPU-percentage or total-RAM hard limit through these APIs; these are conservative
  cooperative budgets. The app must remain playable if analysis is deferred or fails.
- Loudness normalization reads cached features and never decodes synchronously in
  the playback/gain path. Explicit bulk analysis remains charging+idle-only.

## Persisted descriptors

`AudioFeatureSchema.VERSION = 5`, `android-pcm-summary/5-key-energy-onset-budgeted-3x10s`:

- RMS (also the existing approximate loudness/dBFS input), silence fraction,
  zero-crossing rate, dynamic range, spectral centroid, spectral rolloff;
- estimated BPM and BPM confidence (autocorrelation of frame-energy onsets);
- onset strength and normalized **intro/middle/outro energy** (representative sampled
  regions, not a continuous full-song envelope);
- estimated pitch-class root, major/minor flag and confidence (chroma/Krumhansl profiles);
  conflicting key predictions are combined by confidence-weighted voting.
  A low-confidence key is unknown, not a definitive music-theory label.

Vectors are content-provenanced and extractor/schema-versioned. Existing v3 vectors
are left intact; v5 is generated as eligible songs become available for analysis.
No Room table migration is necessary.

## Non-goals and limitations

- No heavy on-device neural model or unvalidated Mood/Valence classification.
- Beat positions throughout a full song and standards-based integrated LUFS are **not**
  inferred from the bounded 30-second sample.
- WorkManager execution is deferrable: playback triggers scheduling but cannot guarantee
  that analysis finishes during that first listen. Inaccessible Telegram bytes are retried
  on a later eligible playback; no background download is forced.
- CPU load, thermal impact, perceived quality, key/tempo accuracy and the end-to-end
  Media3 lifecycle still require device benchmarks and Android CI verification.

## Validation

Unit tests cover zero-energy behavior, synthetic tonal templates, onset strength,
and bounded queue selection; existing Room cache tests were updated for the resource gate.
The Android workflow includes pushes to `recommendation`. Neither an emulator test run
nor a device benchmark is claimed as passed until the exact-head run is inspected.
