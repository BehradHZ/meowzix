# Increment 12 — local ML recommendations

Status: implementation prepared; Android compilation/tests/device acceptance blocked in the current
workspace. This increment is **not certified complete** under specification §32.1.

All eight recommendation categories plus Why This Song use the shared local model/ranking path.
Home shows mixes, evidence and refresh; Now Playing and track overflow expose Why/Continue the
Vibe. History provides reset, explicit rebuild and constrained local audio backfill. Playback remains
owned by the queue layer. The model learns meaningful finalized listening outcomes across canonical
Tracks, regardless of local/Telegram/multiple sources. No dependency was added.

Architecture, normalization, limits and consequential choices are recorded in
`docs/architecture/ADR_012_LOCAL_RECOMMENDATIONS.md`.

## Validation performed

- Production SQL executed against the committed Room v10 fixture through the 10→11→12→13 path:
  25 migration statements; all 45 changed/relevant DAO queries prepare with SQLite.
- Data preservation, repeated seek storage, one terminal outcome/training row per UUID, durable
  event clock after deletion, offline/source/hidden filters and foreign-key cascade checks pass.
- Chronological batch selection, frozen event-page boundaries, reset-floor exclusions and aggregate
  rebuild from the original decision's time bucket pass against the production SQL.
- Host SQLite scale check passes with 10,000 tracks, 1,000 artists and 100,000 events. These timings
  do not certify Android/Room performance or playback continuity.
- Whitespace/diff checks and independently applying the delivered patch to the exact base tree
  are recorded in the review bundle validation report.
- PureShuffleEngine and its existing regression tests are unchanged from the base snapshot.
- Android backup XML is checked for history/model exclusions.

Run the supplementary SQL check with `python scripts/validate_recommendation_sql.py`.
Add `--scale` to include the large synthetic SQL fixture.
It does not substitute for Room-generated schema validation or Kotlin/Android tests.

## Added/updated tests

| Area | Coverage |
| --- | --- |
| Rewards | 48 action/outcome combinations, clamping, partial boundaries, auto/seek neutrality, favorite toggles, removal and contradictory legacy outcomes |
| Features | immutable order/fingerprint, finite normalization, masks, smoothing, cyclic midnight proximity, canonical-ID independence |
| Causal dataset | no own-label/future-favorite/audio leakage, past statistics, reset floors, orphan/unfinished outcomes, tied timestamps, legacy duplicates, page/list parity |
| Linear algebra/model | known SPD solution, seeded systems, bad matrices/non-finite inputs, weighted updates, contextual learning, uncertainty and copy isolation |
| Artifacts | round-trip metadata/parameters, checksums, truncation, incompatible schemas/configuration and invalid coefficients/SPD matrix |
| Acoustic DSP | silence, sine energy/ZCR/spectral values, dynamics, short/idempotent analysis, FFT and normalized optional similarity |
| Ranking/sections | cold start/blend, hard filters, section evidence, consistent rejection, anchor exclusion, optional audio, seeded uniqueness/variation, bounded exploration and Pure independence |
| Evaluation | frozen temporal holdout, insufficient data, pairwise ties, time ordering |
| Device persistence/training | reload, idempotent updates, corruption/reset floor, source multiplicity, persisted samples, ten-outcome scheduling, chronological batches, neutral-prefix recovery, frozen replay boundary, aggregate rebuild and reset/rebuild race, durable event sequence |
| Device audio/migration | real PCM WAV, unreadable source, fallback source priority/decoder failure, equivalent-content reuse, content/corrupt-vector invalidation, no remote download, 12→13 preservation/schema validation |
| Compose | actual reason text/dismiss action, honest cold-start Vibe state and time-bucket labels |

The Kotlin/Android tests are written but have not executed in this environment. Gradle stopped before compilation
while downloading `https://services.gradle.org/distributions/gradle-9.6.0-bin.zip` with
`java.net.SocketException: Network is unreachable`. There is no cached Gradle/Kotlin/Android SDK
toolchain or emulator available here. No passing Kotlin, lint, APK or device result is claimed.

## Required acceptance in an Android build environment

```sh
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
bash gradlew :app:connectedDebugAndroidTest --no-daemon
```

The Room plugin must generate/stage the current v13 schema. Do not fabricate a schema JSON or
enable destructive fallback. Historical v11/v12 fixtures were not committed in the original repository;
tests reconstruct those versions from the committed v10 fixture and production migrations.

Verify on device with Telegram unavailable: local playback, missing/revoked source fallback,
repeat/seek/notification actions, background training and extraction cancellation, small/new libraries,
offline-only mixes and reset/rebuild. Run the existing playback/queue/scale tests and measure large
library/history latency and memory alongside uninterrupted playback. Review 57-feature-model
calibration, exploration influence and section usefulness on real local listening data before release.
