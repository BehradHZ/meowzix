# Increment 3 — Queue, Repeat, and True Pure Shuffle

## Objective

Add a durable, user-editable playback queue and repeat controls, with Pure Shuffle implemented as an unbiased permutation rather than a personalized recommendation mode.

## Implementation checklist

### A. First-class queue

- [x] Expose queue state and commands through a domain `QueueRepository`
- [x] Support play now, play next, add to queue, remove, reorder, and clear
- [x] Display the current item and ordered queue in a dedicated screen
- [x] Expose play-next and add-to-queue actions from the local library
- [x] Keep queue mutations synchronized with the active Media3 session
- [x] Advance past unavailable playback items without crashing
- [x] Keep the complete logical queue separate from the smaller Media3 playback window
- [x] Initially materialize the current Track plus 10 upcoming Tracks, with a small Previous buffer
- [x] Refill 10 more Tracks when 3 or fewer materialized Tracks remain ahead
- [x] Trim old materialized history so the Media3 timeline stays bounded for very large queues
- [x] Prevent duplicate canonical `Track.id` entries in progressive queue mutation without hashing audio files

### B. Repeat and Pure Shuffle

- [x] Support repeat off, repeat one, and repeat all
- [x] Generate Pure Shuffle cycles with Fisher–Yates and a secure random seed
- [x] Shuffle lightweight canonical Track IDs, not fully resolved Media3 items
- [x] Play every eligible track exactly once per shuffle cycle
- [x] Start a fresh permutation when a cycle ends
- [x] Avoid the previous cycle's final track at the next cycle boundary when more than one track exists
- [x] Keep favorites, play counts, skips, artists, albums, history, and recommendation data out of the shuffle input
- [x] Defer newly discovered eligible tracks until the next cycle
- [x] Prevent duplicate explicit additions during an active Pure Shuffle cycle
- [x] Never independently reshuffle each refill batch; refill follows the already-generated logical permutation

### C. Persistence and recovery

- [x] Persist the exact logical queue order, current logical index, playback mode, repeat mode, and shuffle seed
- [x] Restore the exact active Pure Shuffle cycle after process recreation
- [x] Restore a small Media3 playback window rather than eagerly recreating the entire queue
- [x] Persist hot playback position separately so a 1,000+ Track logical queue is not re-serialized every second
- [x] Persist an empty queue so cleared sessions are not resurrected
- [x] Restore paused rather than unexpectedly starting audible playback
- [x] Reconcile the next local-library cycle against currently eligible tracks

### D. Verification

- [x] Verify no duplicates and full coverage across 5,000 generated cycles
- [x] Verify deterministic restoration from the same seed
- [x] Verify first-position distribution across 8,000 seeded cycles
- [x] Verify singleton and invalid duplicate-input behavior
- [x] Verify progressive queue initial-window, refill-threshold, bounded-history, and duplicate-ID behavior
- [x] Verify playback-session codec preserves logical queue topology and remains backward-compatible with v1 state
- [x] Verify a real Media3 repeat-all cycle rolls over and avoids a boundary duplicate
- [x] Verify queue reorder and remove through the emulator UI
- [x] Verify Pure Shuffle order and repeat policy survive a forced process stop
- [x] Run assemble, lint, JVM-test, and connected-device gates

## Acceptance verification — 2026-09-17

- 20 JVM tests passed, including thousands of permutation cycles and a distribution sanity check.
- 7 Android emulator tests passed, including real Media3 Pure Shuffle cycle rollover.
- A six-track real local-media queue was switched to Pure Shuffle with repeat all, displayed in the queue editor, reordered, and reduced to five items without playback failure.
- A forced process stop restored the same Pure Shuffle policy, repeat-all policy, queue position, and paused item.
- The cycle boundary test retained the exact eligible ID set and did not repeat the previous cycle's last item first.
- Newly scanned local tracks are not inserted into an active cycle; the next cycle is rebuilt from the eligible local catalog.
- Android lint completed with zero errors and 11 non-blocking baseline warnings.

## Progressive queue policy — 2026-09-22

- The logical queue remains complete and is keyed by canonical `Track.id`.
- Media3 receives only a sliding materialized window instead of the entire library.
- The default forward window is 10 Tracks and refills by 10 when only 3 remain ahead.
- Old played history is trimmed while a small Previous buffer remains available.
- Pure Shuffle creates one complete ID permutation and windowing consumes that permutation sequentially.
- Queue duplicate prevention uses canonical Track IDs; SHA-256 remains reserved for physical source/file deduplication.
- Large queue topology is persisted only on queue/current-item changes; the frequently changing playback position is stored separately.

## Pure Shuffle policy

- The active cycle is an explicit persisted permutation.
- User preference and listening behavior never influence the permutation.
- Newly discovered tracks wait for the next cycle.
- Tracks removed from local availability are excluded when the next cycle is built.
- Repeat off prepares a fresh cycle at the end but stays paused; repeat all immediately continues into it.

## Non-goals

- Smart Shuffle or recommendations
- Telegram authentication or imports
- Download management
