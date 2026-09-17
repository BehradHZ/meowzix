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

### B. Repeat and Pure Shuffle

- [x] Support repeat off, repeat one, and repeat all
- [x] Generate Pure Shuffle cycles with Fisher–Yates and a secure random seed
- [x] Play every eligible track exactly once per shuffle cycle
- [x] Start a fresh permutation when a cycle ends
- [x] Avoid the previous cycle's final track at the next cycle boundary when more than one track exists
- [x] Keep favorites, play counts, skips, artists, albums, history, and recommendation data out of the shuffle input
- [x] Defer newly discovered eligible tracks until the next cycle
- [x] Prevent duplicate explicit additions during an active Pure Shuffle cycle

### C. Persistence and recovery

- [x] Persist the exact queue order, current index, position, playback mode, and repeat mode
- [x] Restore the exact active Pure Shuffle cycle after process recreation
- [x] Persist an empty queue so cleared sessions are not resurrected
- [x] Restore paused rather than unexpectedly starting audible playback
- [x] Reconcile the next local-library cycle against currently eligible tracks

### D. Verification

- [x] Verify no duplicates and full coverage across 5,000 generated cycles
- [x] Verify deterministic restoration from the same seed
- [x] Verify first-position distribution across 8,000 seeded cycles
- [x] Verify singleton and invalid duplicate-input behavior
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
