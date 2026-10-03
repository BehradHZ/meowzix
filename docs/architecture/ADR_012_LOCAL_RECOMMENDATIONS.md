# ADR 012: shared local contextual recommendations

Status: implemented; Android build/device acceptance pending.

Baseline: `BehradHZ/meowzix`, main `2670a07f6ee01147e63c071fb380e6822a4f8869`, app v0.4, Room v12.
The supplied `MEOWZIX_SYSTEM_SPEC.md` is authoritative. Existing canonical Tracks, listening
instances, AdaptiveScorer, source adapters, playback queue, Room, WorkManager and Hilt are reused.

## Decision and boundaries

Use one shared 57-dimensional regularized contextual linear ranker, behind PersonalizationModel.
There are no song-ID/artist-ID hash buckets, per-song matrices, remote inference, analytics upload,
LLM calls, new dependencies, or direct player/TDLib calls from the recommender. Sources supply
readable bytes and availability; canonical Track UUIDs identify all decisions and rewards.

Listening events feed causal preference replay, versioned rewards/features, persisted TrainingSamples
and local LinUCB updates. RecommendationEngine returns canonical IDs and structured evidence.
The queue layer executes playback. PureShuffleEngine and its randomness/order are unchanged.

## Rewards and occurrence identity

Existing playbackInstanceId is retained. Media3 occurrence IDs distinguish repeats of the same song.
In-memory position accounting excludes seeks, backwards jumps, buffering and discontinuities;
progress ticks never write events. Notification-control seeks/skips use the same event path as UI.
Unknown durations do not imply completion; very short songs cannot complete at their start.
The near-end completion allowance is capped at 10% of duration and 15 seconds.

Reward schema 2 combines manual +0.35, completion +0.60, intentional replay +0.40, favorite +0.50,
60–90% listening +0.20, late skip −0.15, early skip −0.70, and unplayed queue removal −0.20,
then clamps to [−1,1]. Autoplay, seeks and the neutral QUEUE_OVERRIDDEN evaluation marker are neutral. Completion classification takes precedence
over contradictory legacy outcomes. Repeated favorite toggles use the final action. There is one
reward per finalized occurrence; incomplete/orphan occurrences do not train.

Room 12→13 preserves existing data and adds:

- persisted TrainingSamples keyed by playback UUID, with reward/feature versions and provenance;
- a durable eventSequence and single-row clock, surviving row deletion and SQLite VACUUM;
- a nullable unique outcomeKey, enforcing one terminal outcome while permitting repeated seeks
  and favorite actions. Legacy duplicate terminals remain readable but only their first terminal
  receives a deduplication key and participates in training.

## Causality and normalization

Features freeze before the first decision action, using only earlier behavior. The current outcome,
future favorites and audio extracted after the decision cannot enter that training vector.
Past unknown favorite state has an availability mask; ranking uses the actual current favorite state.
Canonical metadata is current metadata; historical tag edits are not reconstructed.

RecommendationFeatureSchemaV2 centralizes names, order and ranges. An immutable schema
fingerprint test and artifact order check prevent silent reordering. Add a new schema version for
changed feature meaning/order. Numeric contracts are:

| Feature group | Range and normalization |
| --- | --- |
| Completion/skip/manual/replay rates | [0,1], four prior observations; completion prior 2, early skip prior 1 |
| Favorite, availability, recent-play, weekend, output and bucket flags | 0 or 1 |
| Play/completion counts | ln(1 + count capped at 1000) / ln(1001) |
| Elapsed days | elapsed / 90 days, clipped [0,1], separate last-play mask |
| Global, artist, album and bucket affinity | bounded smoothed evidence [0,1], separate evidence/availability masks |
| Hour/day cycles | sine/cosine in [−1,1], 24-hour and seven-day cycles |
| Session position / skip streak | position / 50 and streak / 5, clipped [0,1] |
| Artist distance | tracks since artist / 10, clipped [0,1], artist/context availability masks |
| Duration | milliseconds / 1,200,000, clipped [0,1], separate availability mask |
| Acoustic descriptors | six normalized [0,1] values, separate audio availability mask |

## Learning, scheduling and persistence

A = I + Σ(wxxᵀ), b = Σ(wrx). A cached Cholesky factor solves Aθ=b and Ax'=x. Prediction is
θᵀx, uncertainty is sqrt(xᵀx'). No inverse is formed. Bounded round-off jitter is available;
invalid/non-finite artifacts are rejected rather than repaired silently. Updates train a copy and
publish only after a checksummed AtomicFile write succeeds. Metadata and parameters share one
artifact under noBackupFilesDir, including model/feature/reward versions, data watermark, reset
floor, training time, count, active state, lambda, alpha and SHA-256 checksum. Reads are bounded
to 1 MB, including AtomicFile backup recovery. Corruption falls back to AdaptiveScorer.

WorkManager schedules finalized outcomes, never ticks. Ordinary updates wait for ten meaningful
new samples. A bounded outcome query skips full feature replay when even ten terminal outcomes
are unavailable. Startup/schema incompatibility and explicit rebuild trigger a rebuild. Work is serialized
against reset, cancellable and off the playback/main thread. Failed jobs retry a bounded number of times.

Historical events replay in pages of 2000. Rebuilds select at most the latest 20,000 finalized
occurrences; older events still contribute causal prior statistics, but older outcome vectors do not
occupy the training batch. Incremental updates consume the oldest pending outcomes first and schedule
another batch when a backlog remains. A frozen sequence boundary prevents a running replay from
following newly appended playback events indefinitely. A large neutral-feedback prefix cannot hide
later meaningful outcomes: sparse batches replay their causal past until enough rewards are found
or the frozen boundary is exhausted. This can require additional scans for sparse histories, but keeps
stored vectors bounded. This bounds sample/matrix memory and makes a rebuild a recent-history
model. Ordinary updates retain prior learned sufficient statistics. Versioned derived rows are written
in chunks of 250. State scales with tracks/sessions and active decisions; replay still scans historical
events and has not been benchmarked on a device in this environment.

Reset preserves raw history but clears derived statistics/samples and establishes a durable privacy
floor. Old or already-started occurrences cannot repopulate learning. Explicit rebuild intentionally
uses stored history again. Rebuild also reconstructs the global/time aggregates consumed by live
ranking; otherwise a rebuild after reset would train on history absent from ranking inputs.
Aggregation is one Room transaction, deduplicates legacy terminal outcomes, and assigns outcomes
to the original decision's bucket. Reset clears those aggregates under the same mutex as rebuilds,
so an in-flight worker cannot restore pre-reset preferences. Separate floor metadata survives
model corruption. Clear history removes
raw history and resets the model. Maintenance failures display a message without crashing playback.

## Shared product ranking and sections

Below 75 meaningful samples, ranking uses AdaptiveScorer. From 75 through 199, learned weight
rises from 0.05 toward 0.85; at 200 it is capped at 0.85. Uncertainty contributes a bounded
alpha 0.15 exploration bonus using uncertainty/(1+uncertainty). Freshness, recent track/artist
penalties, session skips and diversity remain active. There are no permanent soft bans.

One bounded hydrated/scored pool (800 plus recent/context anchors) drives For You Now, current
Time Mix, Rediscover, Hidden Gems, up to two positive Because You Listen To anchors, On Repeat,
Try Again and Continue the Vibe. Section-specific evidence filters select subsets; they do not train
separate models. Top-window seeded weighted selection and sequential artist penalties vary order.
Large Smart queues preserve their unscored tail only for IDs that passed the engine's hard filters.
This tail retains original queue order; the learned model ranks the bounded leading window.

Hidden/unavailable/invalid sources, offline-ineligible tracks and the current track (with the one-track
exception) are filtered before ranking. Rediscover needs prior positive evidence and defaults to
14 days, relaxing to two days. Hidden Gems require promising evidence, not just zero plays.
On Repeat uses 28-day interactions with seven-day decay. Try Again permits one ambiguous early
skip, excluding repeated consistent rejection. Continue the Vibe combines artist, album and optional
normalized audio similarity, with contextual preference and diversity.

Structured reasons describe actual preference, model confidence, freshness, discovery, similarity
and artist variety evidence. Cards retain their decision reasons; other actions use a bounded
explanation cache or current ranking. Positive linear feature contributions participate in confidence
evidence. The UI shows concise reasons and honest insufficient-evidence/empty states, never matrices
or coefficients. Home, Now Playing and track overflow actions delegate playback to QueueRepository.
Live session features use only the latest unexpired session, matching causal training rather than
carrying an older session's skip streak into a new visit. The 30-minute inactivity window is shared
with the listening-history repository.

## Audio and privacy

Extractor/schema 2 analyzes a maximum 30-second decoded PCM prefix. It supports PCM WAV directly,
and Android MediaCodec PCM output (8/16-bit and float), downmixed by channel averaging. It measures
RMS, zero crossings, silent frames, p90−p10 frame-energy range, Hann-window spectral centroid and
85% spectral rolloff. The compact radix-2 FFT has dedicated tests. Frequencies are normalized to
Nyquist, not raw Hertz. These are acoustic proxies, not BPM, mood labels or semantic embeddings.

Readable priority is MediaStore, app offline copy, complete TDLib local copy, then already-readable
remote provenance. SHA-256 content checks, compatible extractor/schema checks and cache reuse
avoid decoding equivalent content again. Content is rechecked after analysis before persistence.
Unavailable priority sources fall through. No extraction path requests a download. Opportunistic
work is small; explicit bulk analysis requires charging, idle and battery-not-low constraints.
A decoder exception also falls through to the next readable source; cancellation still propagates.
Failure to enqueue optional audio work cannot prevent recommendation or Smart queue generation.

The Android backup rules exclude the shared Room database and legacy model DataStore from cloud
backup/device transfer. Because history shares a database with library metadata, the entire database
is excluded; this also means library/playlist database metadata is not restored by Android backup.
Learned artifacts already use noBackupFilesDir. No raw audio is persisted by analysis. Explicit debug
export is redacted, user-triggered, and disabled in normal release UI.

## Evaluation and acceptance

A separate frozen chronological 80/20 model reports held-out MAE, an uncalibrated heuristic baseline,
reward means, pairwise ranking accuracy and time-bucket slices on at most 10,000 recent causal
samples. Behavioral metrics use a bounded 28-day window and report Smart/Pure/ordered completion,
early skips, explicit manual queue overrides, replay, coverage and artist repetition. These are observational local
metrics; no off-policy uplift, collaborative filtering or unbiased experiment is claimed.

See INCREMENT_12_LOCAL_RECOMMENDATIONS.md for the test matrix and remaining build/device gates.
Compilation and device acceptance are mandatory before calling this increment complete.
