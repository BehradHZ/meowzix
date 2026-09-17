# Meowzix — Local-First Adaptive Music Player
## Product Definition, System Architecture, Increment Plan, and AI-Agent Implementation Specification

**Product name:** Meowzix  
**Repository:** `BehradHZ/meowzix`

**Document status:** Implementation-ready specification — source-agnostic training revision  
**Primary target:** Android  
**Reference stack:** Kotlin, Jetpack Compose, Jetpack Media3 / ExoPlayer, Room, TDLib  
**Last reviewed against official platform documentation:** 2026-09-17  
**Audience:** AI coding agents and human developers  
**Language:** English  
**Normative terms:** `MUST`, `MUST NOT`, `SHOULD`, `SHOULD NOT`, and `MAY` are requirements with their usual RFC-style meanings.

---

# 1. Purpose

Build a local-first music player that combines:

1. music already available as normal audio files on the Android device;
2. music stored in the user's Telegram account, especially Saved Messages or one or more user-selected chats/channels;
3. app-managed offline copies of Telegram-hosted music;
4. reliable background playback and system media controls;
5. a **real, non-personalized shuffle mode**;
6. a separate **Smart Shuffle / Adaptive Playback mode** that gradually learns the user's listening patterns, especially **which tracks are preferred at different times**, without changing the semantics of Pure Shuffle;
7. strong offline behavior;
8. local-first privacy and minimal dependence on any custom backend.

The application is not intended to be a general Telegram replacement. Telegram is a cloud source for the user's music library, while the player itself owns the playback experience, local library model, queue, offline behavior, playlists, listening history, and personalization state.

---

# 2. Product Vision

The user should be able to treat Telegram as effectively unlimited personal cloud storage for music while still using a dedicated music-player experience.

The desired mental model is:

```text
                         ┌──────────────────────┐
                         │   Telegram Account   │
                         │  selected music chat │
                         └──────────┬───────────┘
                                    │
                              TDLib integration
                                    │
                                    ▼
┌────────────────────┐    ┌──────────────────────┐
│ Android MediaStore │───►│  Unified Music       │
│ local audio files  │    │  Library             │
└────────────────────┘    └──────────┬───────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
                    ▼                ▼                ▼
            Local playback     Offline copies     Search/playlists
                    │
                    ▼
             Playback Service
                    │
          ┌─────────┴─────────┐
          │                   │
          ▼                   ▼
    Pure Shuffle        Smart Playback
    (true random)       (adaptive)
```

The user must not need Telegram itself to be open for playback from Telegram-backed tracks. The app authenticates as its own Telegram client using TDLib.

---

# 3. Core Product Decisions

These decisions are authoritative unless this document is explicitly revised.

## 3.1 Local-first, not Telegram-first

The application MUST function as a useful offline music player even when:

- the phone has no internet connection;
- Telegram is temporarily unavailable;
- the user is logged out of Telegram;
- the selected Telegram source cannot be refreshed.

Telegram integration is an enhancement to the library, not a runtime prerequisite for all playback.

## 3.2 Android-first

The first production target is Android.

The reference implementation MUST use Android-native platform capabilities. iOS, desktop, and web versions are future scope and MUST NOT distort the Android architecture.

The domain model should nevertheless avoid Android-specific concepts where a neutral abstraction is easy.

## 3.3 No custom backend in the initial architecture

The MVP MUST NOT require a custom server.

The following data is local by default:

- library index;
- playlists;
- favorites;
- queue;
- playback position;
- listening events;
- adaptive preference statistics;
- app settings;
- Telegram source configuration references;
- offline-download state.

A server MAY be introduced later for optional cross-device sync, but must not be required for core playback.

## 3.4 Telegram must be modeled as a source, not as the Track itself

A single logical track may have more than one source.

Example:

```text
Track: "Numb" by Linkin Park
├── Local MediaStore file
├── Telegram message in "My Music"
└── App-managed offline copy
```

The application MUST therefore separate the logical `Track` entity from physical or remote `TrackSource` entities.

## 3.5 Pure Shuffle and Smart Shuffle are separate features

**Pure Shuffle MUST remain genuinely random and MUST NOT be personalized.**

No recommendation score, listening history, favorite status, time-of-day model, artist preference, or ML system may affect Pure Shuffle.

Smart Shuffle is a separate mode and may use personalized information.

## 3.6 Offline playback is a first-class requirement

Tracks may be playable offline because:

- they already exist as local shared audio files;
- TDLib has a complete local Telegram file;
- the app created an explicitly pinned offline copy;
- the app retained a temporary cached copy.

The UI MUST clearly distinguish remote-only and offline-available tracks.

## 3.7 Do not depend on another app's private cache

The implementation MUST NOT assume that it can directly read files from the official Telegram Android app's private cache or `Android/data` area.

If a Telegram-downloaded audio file is also exposed as a normal shared-storage audio/download file and appears through Android media/document APIs, it can be indexed as a normal local source.

Otherwise, the app must obtain its own local copy through its own Telegram session.

## 3.8 Privacy-first personalization

Listening behavior is private.

By default:

- playback history MUST remain on-device;
- adaptive statistics MUST remain on-device;
- no behavioral telemetry may be uploaded to a developer server;
- no precise location is needed;
- microphone, contacts, SMS, and call-log permissions are not required;
- Smart Shuffle must remain usable without an account other than the optional Telegram login.

---

# 4. Source-Agnostic Training and Data Provenance

This product is a personal adaptive player. The learning architecture MUST be **source-agnostic**.

A track's storage/provider origin MUST NOT determine whether it can participate in learning. Music and behavioral data may be used by the personalization pipeline regardless of whether the track came from:

- Android MediaStore;
- Telegram;
- an app-managed offline copy;
- a TDLib-managed local file;
- a future cloud provider;
- a future manually imported file;
- another future `TrackSource` implementation.

The system MUST therefore separate:

```text
Where a track comes from
        │
        ▼
TrackSource / Provider Adapter
        │
        ▼
Canonical Track
        │
        ├── metadata features
        ├── optional audio features
        ├── listening history
        └── context features
                │
                ▼
        Training Dataset Builder
                │
                ▼
        Personalization Model
```

The recommendation/training code MUST NOT contain logic such as:

```text
if source == TELEGRAM:
    exclude_from_training()
```

or equivalent provider-specific exclusions.

## 4.1 Default training policy

All supported source types are **training-eligible by default**.

Training eligibility applies to the canonical `Track`, not just one physical copy of it. If a logical Track has both a local source and a Telegram source, the model should normally treat it as one song rather than two training items.

A user MAY later be given controls to exclude:

- an individual Track;
- a playlist;
- a source/provider;
- all audio-content features while still allowing behavioral learning.

These controls are privacy/product controls, not architectural restrictions.

## 4.2 Data classes that may be used for training

The personalization system MAY learn from the following categories.

### Behavioral signals

Examples:

- manual track selection;
- play start;
- completion;
- early skip;
- late skip;
- replay;
- favorite/unfavorite;
- seek behavior;
- queue removal;
- "play next" selection;
- session duration;
- repeated artist/album choices;
- time-of-day patterns;
- weekday/weekend patterns;
- output-device class when available.

### Canonical metadata features

Examples:

- artist;
- album;
- title-derived normalized tokens;
- duration;
- year;
- track/album ordering;
- genre if available;
- user-created tags if a future feature introduces them.

Do not make filename formatting a dominant feature.

### Audio-content features

When the audio bytes are locally readable, the system MAY extract features such as:

- duration validation;
- loudness / RMS energy;
- tempo / BPM estimate;
- spectral centroid;
- spectral rolloff;
- zero-crossing rate;
- MFCC-style features;
- chroma / tonal features;
- dynamic range;
- silence ratio;
- learned audio embeddings from a local feature extractor;
- other compact descriptors useful for similarity/ranking.

Raw audio MUST NOT be duplicated merely for training when an existing readable source is sufficient.

### Context features

Examples:

- local hour;
- time bucket;
- day of week;
- weekend flag;
- session length;
- recent listening sequence;
- recent artists;
- online/offline mode;
- output class such as speaker, wired, or Bluetooth.

Do not require precise location, microphone input, contacts, SMS, or unrelated personal data.

## 4.3 Source provenance

Every feature/training artifact MUST retain provenance.

The system should be able to answer:

```text
Which logical Track produced this sample?
Which source supplied the bytes used for audio analysis?
Which listening events contributed to the label/reward?
Which feature-extractor version generated the vector?
Which model version consumed it?
```

Provenance exists for debugging, reproducibility, invalidation, and future user controls. It MUST NOT cause source-specific recommendation bias unless explicitly modeled.

Recommended metadata:

```text
TrainingProvenance
- trackId: UUID
- sourceIdUsedForAudioFeatures: UUID?
- sourceType: TrackSourceType?
- featureExtractorVersion: String?
- datasetSchemaVersion: Int
- generatedAt: Instant
```

## 4.4 Deduplicate before learning

A song available through multiple sources MUST NOT receive duplicated behavioral weight simply because it has multiple copies.

Learning operates on canonical `Track.id`.

Physical source IDs are used only when necessary, such as:

- locating audio bytes;
- invalidating an audio feature vector after source replacement;
- debugging import/deduplication;
- determining current playback availability.

## 4.5 Local-first training

The default architecture SHOULD train/update personalization locally on the device.

The initial model family should be selected for:

- incremental updates;
- small datasets;
- predictable resource usage;
- easy reset;
- explainability;
- deterministic tests;
- no dependency on a remote inference service.

A remote training backend MAY be introduced later, but core Smart Shuffle MUST NOT depend on it.

## 4.6 Model lifecycle

Every learned model MUST have:

```text
ModelState
- modelId
- modelType
- modelVersion
- featureSchemaVersion
- trainingDataVersion
- trainedAt
- sampleCount
- checksum
```

The app MUST be able to:

- start with no model;
- rebuild a model from stored local history/features;
- invalidate a model when feature schema changes;
- reset personalization without deleting the music library;
- fall back to heuristic Smart Shuffle if model loading/training fails.

## 4.7 No provider-specific model contract

Provider adapters are responsible only for obtaining and maintaining playable sources.

The training system consumes canonical data.

Bad:

```text
TelegramRecommendationModel
LocalFileRecommendationModel
```

Preferred:

```text
TrackRepository
FeatureRepository
ListeningHistoryRepository
TrainingDatasetBuilder
PersonalizationModel
RecommendationEngine
```

This rule is important because future providers must be able to participate in learning without changing the recommendation architecture.

# 5. Reference Technology Stack

Unless a later architecture decision replaces them, use:

| Concern | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | Layered / Clean-ish architecture with repositories and use cases |
| Async | Kotlin Coroutines + Flow |
| Playback | Jetpack Media3 / ExoPlayer |
| Background playback | `MediaLibraryService` or `MediaSessionService`; prefer `MediaLibraryService` if exposing the library to system clients |
| System controls | Media3 `MediaSession` |
| Local DB | Room |
| Local audio discovery | Android `MediaStore` |
| Telegram | TDLib |
| Dependency injection | Hilt |
| Background work | WorkManager for deferrable sync/index work; playback remains in media service |
| Serialization | Kotlinx Serialization where needed |
| Preferences | DataStore |
| Tests | JUnit + AndroidX test stack + coroutine test tools |
| Build | Gradle Kotlin DSL |

Do not introduce a backend, Firebase, or cloud database unless a later increment explicitly requires it.

---

# 6. Repository / Module Structure

A single Android app module is acceptable initially, but package boundaries MUST reflect the following logical architecture.

Recommended structure:

```text
app/
├── core/
│   ├── model/
│   ├── common/
│   ├── time/
│   └── permissions/
├── data/
│   ├── db/
│   ├── localmedia/
│   ├── telegram/
│   ├── repository/
│   └── settings/
├── domain/
│   ├── library/
│   ├── playback/
│   ├── downloads/
│   ├── playlists/
│   └── recommendation/
├── playback/
│   ├── PlaybackService
│   ├── PlayerController
│   ├── QueueManager
│   └── MediaItemFactory
├── recommendation/
│   ├── PureShuffleEngine
│   ├── CandidateGenerator
│   ├── AdaptiveScorer
│   └── PreferenceStatsUpdater
├── feature/
│   ├── onboarding/
│   ├── library/
│   ├── nowplaying/
│   ├── search/
│   ├── playlists/
│   ├── telegram/
│   ├── downloads/
│   └── settings/
└── test/
```

Rules:

- UI MUST NOT call TDLib directly.
- UI MUST NOT query Room DAOs directly.
- Playback service MUST not depend on Compose.
- Recommendation code MUST not control the player directly.
- Domain code should use repository interfaces.
- Telegram-specific IDs MUST remain inside source-specific data structures whenever possible.
- A `Track` must be addressable by an internal app UUID independent of Telegram IDs or MediaStore IDs.

---

# 7. Domain Model

## 7.1 Track

Represents a logical song.

```text
Track
- id: UUID
- title: String
- normalizedTitle: String
- artist: String?
- normalizedArtist: String?
- album: String?
- durationMs: Long
- trackNumber: Int?
- year: Int?
- artworkRef: String?
- favorite: Boolean
- hidden: Boolean
- createdAt: Instant
- updatedAt: Instant
```

Notes:

- `id` MUST be stable within the app database.
- A track MUST NOT be identified by filename alone.
- Metadata may be incomplete.
- Track metadata should be editable in a later increment without modifying the original file unless explicitly requested.

## 7.2 TrackSource

Represents one playable or potentially playable source for a Track.

```text
TrackSource
- id: UUID
- trackId: UUID
- type: enum
- availability: enum
- contentUri: String?
- localPath: String?
- mimeType: String?
- fileSizeBytes: Long?
- contentHashSha256: String?
- trainingEligible: Boolean = true
- createdAt: Instant
- lastVerifiedAt: Instant?
```

`TrackSource.type`:

```text
LOCAL_MEDIASTORE
TELEGRAM_REMOTE
TDLIB_LOCAL
APP_OFFLINE_COPY
```

`TrackSource.availability`:

```text
AVAILABLE_LOCAL
REMOTE_ONLY
DOWNLOADING
PARTIAL
MISSING
ERROR
```

Do not duplicate one physical source into multiple rows unless source semantics differ.

## 7.3 TelegramTrackSource

Source-specific extension data:

```text
TelegramTrackSource
- trackSourceId: UUID
- accountId: String
- chatId: Long
- messageId: Long
- tdFileId: Int?
- tdPersistentFileId: String?
- fileName: String?
- telegramTitle: String?
- telegramPerformer: String?
- remoteRevisionKey: String?
```

The reliable re-fetch identity is the Telegram message/source relationship. Do not assume a temporary file reference remains valid forever.

## 7.4 LocalMediaSource

```text
LocalMediaSource
- trackSourceId: UUID
- mediaStoreId: Long
- contentUri: String
- relativePath: String?
- displayName: String?
- dateModified: Long?
```

## 7.5 DownloadRecord

```text
DownloadRecord
- id: UUID
- trackId: UUID
- sourceId: UUID
- purpose: enum
- state: enum
- bytesDownloaded: Long
- totalBytes: Long?
- requestedAt: Instant
- completedAt: Instant?
- lastError: String?
```

`purpose`:

```text
PLAY_ON_DEMAND
PIN_OFFLINE
PREFETCH
TEMP_CACHE
```

`state`:

```text
QUEUED
DOWNLOADING
PAUSED
COMPLETE
FAILED
CANCELED
```

## 7.6 Playlist

```text
Playlist
- id: UUID
- name: String
- createdAt: Instant
- updatedAt: Instant
```

```text
PlaylistTrack
- playlistId: UUID
- trackId: UUID
- position: Long
- addedAt: Instant
```

## 7.7 PlaybackQueue

Queue state SHOULD be persisted so process death does not destroy the listening session.

```text
PlaybackQueue
- id: UUID
- mode: enum
- sourceContext: String?
- currentIndex: Int
- shuffleSeed: Long?
- createdAt: Instant
- updatedAt: Instant
```

```text
QueueItem
- queueId: UUID
- trackId: UUID
- queuePosition: Int
- originalPosition: Int?
```

Queue modes:

```text
ORDERED
PURE_SHUFFLE
SMART_SHUFFLE
```

## 7.8 ListeningEvent

This is app-generated behavior and is central to personalization.

```text
ListeningEvent
- id: UUID
- trackId: UUID
- sessionId: UUID
- type: enum
- occurredAt: Instant
- localHour: Int
- dayOfWeek: Int
- timeBucket: enum
- isWeekend: Boolean
- positionMs: Long?
- durationMs: Long?
- completionRatio: Double?
- initiatedBy: enum
- playbackMode: enum
- outputClass: enum?
```

Event types:

```text
PLAY_STARTED
PLAY_COMPLETED
PLAY_STOPPED
SKIPPED_EARLY
SKIPPED_LATE
MANUAL_SELECTED
AUTO_SELECTED
REPLAYED
FAVORITED
UNFAVORITED
SEEKED
```

`initiatedBy`:

```text
USER
PURE_SHUFFLE
SMART_SHUFFLE
QUEUE
SYSTEM_RESUME
```

Do not create a database row for every playback progress tick. Persist meaningful events only.

## 7.9 ListeningSession

```text
ListeningSession
- id: UUID
- startedAt: Instant
- endedAt: Instant?
- initialMode: enum
```

A session may time out after a configurable inactivity period, e.g. 30 minutes.

## 7.10 PreferenceStats

This stores deterministic aggregated statistics used by both heuristic ranking and learned-model features.

```text
TrackPreferenceStats
- trackId: UUID
- totalStarts: Int
- totalCompletions: Int
- earlySkips: Int
- lateSkips: Int
- manualSelections: Int
- replays: Int
- lastPlayedAt: Instant?
```

```text
TrackTimePreference
- trackId: UUID
- timeBucket: enum
- starts: Int
- completions: Int
- earlySkips: Int
- manualSelections: Int
- lastInteractionAt: Instant?
```

Equivalent artist-level statistics MAY be maintained.

## 7.11 AudioFeatureVector

Stores source-independent features for a canonical Track.

```text
AudioFeatureVector
- id: UUID
- trackId: UUID
- sourceIdUsed: UUID
- extractorName: String
- extractorVersion: String
- schemaVersion: Int
- vectorFormat: enum
- vectorBlob: ByteArray
- generatedAt: Instant
- sourceContentHash: String?
```

Rules:

- one logical Track may have multiple historical vectors across extractor versions;
- only the current compatible vector should be used for current training;
- if two physical sources are exact duplicates, extracting from one is sufficient;
- feature extraction must run off the UI thread;
- extraction should be resumable and cacheable.

## 7.12 TrainingSample

A materialized example used by a learned recommender.

```text
TrainingSample
- id: UUID
- trackId: UUID
- sessionId: UUID?
- generatedAt: Instant
- contextVector: ByteArray
- trackFeatureVector: ByteArray
- reward: Double
- weight: Double
- schemaVersion: Int
```

A training sample MAY be generated from one or more `ListeningEvent` rows.

Do not delete raw meaningful listening events merely because samples were materialized. Samples are derived data and may be rebuilt.

## 7.13 ModelState

```text
ModelState
- id: UUID
- modelType: String
- modelVersion: String
- featureSchemaVersion: Int
- trainingDataVersion: Long
- trainedAt: Instant
- sampleCount: Long
- artifactPath: String?
- artifactChecksum: String?
- isActive: Boolean
```

Only one model of a given active model family should be active at a time unless an explicit experiment framework is later introduced.

---

# 8. Time Context

The user explicitly wants the app to learn which music works better at different times.

Initial time buckets:

```text
EARLY_MORNING  05:00–07:59
MORNING        08:00–11:59
AFTERNOON      12:00–16:59
EVENING        17:00–20:59
NIGHT          21:00–00:59
LATE_NIGHT     01:00–04:59
```

Rules:

- use device local time at event occurrence;
- store the resolved bucket with each event;
- also store raw local hour and day of week so future bucket definitions can change;
- do not require location;
- do not infer sensitive context.

Later optional context may include:

- output device class: speaker / wired headphones / Bluetooth;
- charging state;
- online/offline state;
- session length so far.

Do not add context merely because it is technically collectable.

---

# 9. Local Music Discovery

## 9.1 MediaStore

The app MUST index audio files exposed through Android `MediaStore`.

On modern Android versions, request only the media permission required for audio.

The app MUST gracefully support:

- permission granted;
- permission denied;
- permission later revoked;
- files removed externally;
- files added while app is not running;
- metadata changes;
- duplicate files.

## 9.2 Local-source import behavior

When a local audio file is discovered:

1. read available MediaStore metadata;
2. create or match a logical `Track`;
3. create/update `LOCAL_MEDIASTORE` source;
4. mark it `AVAILABLE_LOCAL`;
5. extract artwork lazily when useful;
6. do not copy the file into app storage unless explicitly required.

## 9.3 Local availability

A local source must be verified before playback.

If a content URI becomes invalid:

- mark the source `MISSING`;
- keep the logical Track if another source exists;
- hide the Track only if product rules say to hide unavailable tracks.

---

# 10. Telegram Integration

## 10.1 Use TDLib, not Bot API

The app MUST use the Telegram user API through TDLib.

A bot is not the correct abstraction because the user needs access to their own music stored in their account and selected chats.

## 10.2 Authentication

Implement TDLib authorization as a state machine.

Support the authorization states required by current TDLib, including:

- TDLib parameters;
- phone number where applicable;
- verification code;
- email/email code if Telegram requests it;
- 2FA password if configured;
- ready;
- logout/closed/error states.

UI must represent state, not guess it.

## 10.3 Credentials

Production Telegram API credentials:

- MUST NOT be hardcoded in a public repository;
- SHOULD be injected at build time;
- MUST use an application-specific API ID obtained for this app;
- must be documented for local development.

For open-source builds, provide a clear setup mechanism for developer-owned credentials.

## 10.4 Source selection

The user should explicitly choose which Telegram chats are music sources.

Initial supported sources:

1. Saved Messages;
2. private channel;
3. private group;
4. other accessible chat if explicitly selected.

Do not crawl every Telegram chat by default.

Persist selected source IDs locally.

## 10.5 Telegram message eligibility

Index messages that represent music.

Include:

- Telegram audio messages with music metadata;
- document/file messages whose MIME type is audio;
- known audio file extensions when MIME data is missing or unreliable.

Typical extensions:

```text
.mp3
.m4a
.aac
.flac
.ogg
.opus
.wav
```

Exclude by default:

- voice notes;
- video;
- video notes;
- stickers;
- arbitrary non-audio documents.

If an `.opus` item is a voice note, keep it excluded unless the user explicitly enables voice-message import.

## 10.6 Telegram metadata

Prefer the best available values in this order:

**Title**
1. Telegram audio title if present;
2. embedded file metadata after local download;
3. normalized filename;
4. fallback `Unknown Track`.

**Artist**
1. Telegram performer if present;
2. embedded metadata after local download;
3. unknown.

Metadata refinement after download MUST NOT create a new track unnecessarily. Update the existing logical track when safe.

## 10.7 Telegram file lifecycle

Do not assume a Telegram file reference is permanent.

The system must be able to recover by re-fetching the originating message and refreshing source information.

The `chatId + messageId` relationship is therefore important.

## 10.8 Sync

Source sync must be incremental after initial import.

Persist enough state to avoid rescanning full chat history on every launch.

The sync layer must detect:

- newly added music messages;
- message deletion;
- changed metadata when observable;
- inaccessible source;
- logged-out state.

---

# 11. Unified Library and Deduplication

The same audio may appear both locally and on Telegram.

The user should normally see one Track, not two identical entries.

## 11.1 Exact deduplication

If complete file bytes are available for two sources, SHA-256 equality is strong evidence that they represent the same file.

Do not hash huge libraries eagerly on the UI thread.

Hash lazily or during background indexing.

## 11.2 Provisional metadata matching

When the Telegram file has not yet been downloaded, use a cautious provisional match.

Candidate match signals:

- normalized title equal;
- normalized artist equal when both known;
- duration difference <= 2 seconds;
- file size sufficiently close when known;
- normalized filename similarity.

Never merge solely because titles match.

## 11.3 Merge confidence

Internally classify:

```text
EXACT_HASH
HIGH_CONFIDENCE_METADATA
USER_CONFIRMED
UNMATCHED
```

Automatic merge SHOULD require `EXACT_HASH` or sufficiently high confidence.

If uncertain, keep separate tracks until later.

## 11.4 Source priority for playback

Default source selection:

1. valid `LOCAL_MEDIASTORE`;
2. valid `APP_OFFLINE_COPY`;
3. complete TDLib local file;
4. Telegram remote source if online.

The user should not see source selection in normal playback unless there is an error or an advanced settings screen.

---

# 12. Playback Engine

## 12.1 Media3

Use Media3 / ExoPlayer.

Playback must run independently from the UI using a media service.

The service MUST support:

- play;
- pause;
- seek;
- next;
- previous;
- queue changes;
- repeat;
- background playback;
- lock-screen/system media controls;
- Bluetooth/headset media buttons;
- audio focus;
- notification controls;
- process lifecycle as required by Android.

## 12.2 Audio focus

Expected behavior:

- transient interruption: pause or duck according to platform best practice;
- permanent audio-focus loss: pause;
- headset disconnect: pause instead of suddenly playing through the speaker;
- incoming system events must not corrupt the queue.

## 12.3 Resume

Persist:

- current queue;
- current item;
- playback position;
- playback mode;
- repeat state.

After normal process recreation, the app SHOULD restore the previous session.

Do not automatically start audible playback after reboot without explicit user action.

## 12.4 Supported file formats

Support whatever ExoPlayer/platform decoders reliably support on the target Android version.

At minimum, design and test with common music formats such as:

- MP3;
- AAC/M4A;
- FLAC;
- OGG/Opus;
- WAV where platform support allows.

Do not promise codec support that has not been verified on the device/API level.

---

# 13. Remote Playback Strategy

Reliability is more important than pretending Telegram is a conventional HTTP streaming CDN.

## 13.1 Initial strategy: download-on-play

For the first reliable Telegram playback implementation:

1. user requests a remote-only track;
2. app starts a TDLib download with high priority;
3. UI shows download/preparing state;
4. playback starts as soon as the implementation has a safe playable local file state;
5. the completed local file may be retained according to cache policy.

A complete download before playback is acceptable for the first working increment.

## 13.2 Progressive playback is an enhancement

TDLib / Telegram supports file download progress and Telegram's lower-level API supports partial file retrieval.

However, progressive playback MUST be treated as an optimization after reliable download-on-play works.

Do not build a fragile custom random-access Telegram streaming layer before core playback is stable.

## 13.3 Prefetch

Later, the app MAY prefetch the next one or two likely tracks.

Rules:

- obey metered-network setting;
- respect cache budget;
- do not aggressively download large queues;
- cancel obsolete low-priority prefetch work;
- pinned offline downloads take priority over prefetch.

---

# 14. Offline Storage Model

The app must distinguish **Pinned Offline** from **Temporary Cache**.

## 14.1 Pinned offline

User intent: "Keep this track available offline."

Requirements:

- download must survive app restarts;
- never evict automatically under normal cache cleanup;
- show explicit offline indicator;
- allow manual removal;
- removal must not delete the Telegram cloud copy or unrelated shared local file.

## 14.2 Temporary cache

User intent: reduce repeated downloads and improve playback.

Requirements:

- app may evict automatically;
- configurable size budget;
- LRU-style cleanup is acceptable;
- currently playing files must not be evicted;
- pinned files must not count as evictable cache.

## 14.3 Local shared file

If a Track already has a valid `LOCAL_MEDIASTORE` source, it is already offline.

The app MUST NOT create a second offline copy just to satisfy "Download for offline" unless the user explicitly asks for a managed duplicate.

UI should say something equivalent to:

```text
Available offline
Source: Device
Additional storage: 0 B
```

## 14.4 Storage cleanup

Settings should eventually provide:

- cache size limit;
- clear temporary cache;
- list offline downloads;
- storage usage;
- remove all app-managed offline copies.

Never delete user-owned MediaStore files as part of cache cleanup.

---

# 15. Queue Semantics

The queue is a first-class domain concept.

Operations:

- play now;
- play next;
- add to queue;
- remove from queue;
- reorder;
- clear queue;
- save current queue as playlist.

The queue must remain valid if:

- a track becomes unavailable;
- a Telegram download fails;
- a local file is deleted;
- the app process dies.

Unavailable items should be skipped with a visible non-blocking error.

---

# 16. Pure Shuffle Specification

This section is strict.

## 16.1 Definition

Pure Shuffle means every eligible track appears exactly once in a randomized cycle before the cycle repeats.

Do not select a random track independently on each Next press because that creates premature repeats.

## 16.2 Algorithm

For a source collection of `N` eligible tracks:

1. copy track IDs into an array;
2. generate a high-quality random seed;
3. apply Fisher-Yates shuffle;
4. persist the generated order or persist enough state to restore it;
5. play sequentially through the shuffled permutation.

When the cycle ends:

1. generate a new shuffle;
2. if `N > 1`, prevent the first item of the new cycle from being identical to the last item of the previous cycle;
3. otherwise do not bias the order.

## 16.3 No personalization

Pure Shuffle MUST NOT consider:

- favorite status;
- play count;
- skip count;
- artist;
- album;
- time;
- day;
- listening history;
- recommendation score.

## 16.4 Queue mutation

If tracks are added to the active source while a Pure Shuffle cycle is running:

- the simplest valid behavior is to include them only in the next shuffle cycle;
- alternatively, insert new tracks uniformly among unplayed positions.

Choose one behavior and test it. Do not silently reshuffle already-played items into the remaining cycle.

If a track is removed, remove it from the remaining queue.

## 16.5 Test properties

Automated tests MUST verify:

- no duplicate within a cycle;
- every eligible item appears exactly once;
- distribution is not trivially biased;
- seed restoration reproduces queue order if deterministic restore is used;
- Pure Shuffle results do not change when preference data changes.

---

# 17. Listening Event Semantics

Adaptive behavior is only useful if events are defined consistently.

## 17.1 Completion

Initial definition:

```text
PLAY_COMPLETED if:
completionRatio >= 0.90
OR remaining duration <= 15 seconds
```

Do not count a track as completed twice in the same playback instance.

## 17.2 Early skip

Initial definition:

```text
SKIPPED_EARLY if user moves away from the track and:
played < 30 seconds
AND completionRatio < 0.20
```

Very short tracks may require percentage-based adjustment.

## 17.3 Late skip

A skip after meaningful listening but before completion:

```text
SKIPPED_LATE when:
not completed
AND not early skip
AND user intentionally moves to another track
```

Late skip should be a weaker negative signal than early skip.

## 17.4 Manual selection

Log `MANUAL_SELECTED` when the user explicitly chooses a specific track from UI/search/playlist.

This is a stronger positive context signal than a track merely auto-playing.

## 17.5 Replay

If the user intentionally restarts or selects the same recently completed track, record `REPLAYED`.

## 17.6 Seek

Seek events may be logged for analytics, but Smart Shuffle v1 does not need to score every seek.

---

# 18. Smart Shuffle / Adaptive Playback v1

The first adaptive engine is deterministic and locally inspectable.

It is the bootstrap/fallback implementation. The architecture MUST allow it to be replaced or augmented by a learned local ranking model in the next increment.

## 18.1 Goal

Select a next track that balances:

- known user affinity;
- time-of-day preference;
- recent listening;
- artist repetition;
- discovery;
- availability.

## 18.2 Candidate generation

Before ranking, build a candidate set.

Hard filters:

- track is not hidden;
- at least one usable source exists;
- if offline-only mode is active, an offline source must exist;
- track is not already invalid;
- avoid the currently playing track unless the library only has one track.

Soft exclusions / penalties:

- recently played track;
- artist repeated too recently;
- tracks repeatedly skipped in the current session.

Candidate generation and scoring MUST be separate code.

## 18.3 Affinity statistics

For each track calculate smoothed ratios.

Example:

```text
completionRate =
    (completions + 2) / (starts + 4)

earlySkipRate =
    (earlySkips + 1) / (starts + 4)
```

Smoothing prevents a single play from becoming extreme.

Define normalized global affinity using fixed transparent weights, for example:

```text
globalAffinity =
    + 0.35 * completionRate
    - 0.30 * earlySkipRate
    + 0.20 * normalizedManualSelectionRate
    + 0.10 * normalizedReplayRate
    + 0.05 * favoriteBonus
```

Exact weights MAY be tuned later, but must be centralized in configuration and unit-tested.

## 18.4 Time affinity

For the current time bucket, compute a separate smoothed score from:

- manual selections in this bucket;
- completions in this bucket;
- early skips in this bucket.

Tracks without enough observations should fall back toward their global affinity rather than receiving zero.

Example Bayesian-style blend:

```text
timeConfidence = bucketStarts / (bucketStarts + K)

timeAffinity =
    timeConfidence * observedBucketScore
    + (1 - timeConfidence) * globalAffinity
```

Start with `K = 5` or another documented constant.

## 18.5 Recency penalty

Avoid repetitive playback.

Example:

```text
if played in last 30 minutes: heavy penalty
if played in last 2 hours: medium penalty
if played earlier today: light penalty
otherwise: no penalty
```

Use elapsed time, not just queue position.

## 18.6 Artist repetition penalty

If the same artist was just played, penalize another track by that artist.

The penalty may decay over the last 3–5 played tracks.

Do not forbid the artist completely because small libraries may contain few artists.

## 18.7 Exploration bonus

The system must keep learning.

Give a modest bonus to:

- unplayed tracks;
- rarely played tracks;
- tracks with little time-bucket evidence.

Exploration MUST NOT dominate.

A reasonable starting target is approximately 10–20% of Smart Shuffle choices having meaningful exploratory influence, implemented through score bonus/randomized choice rather than a rigid quota.

## 18.8 Score

A starting formula:

```text
score =
    0.40 * globalAffinity
  + 0.30 * timeAffinity
  + 0.10 * explorationBonus
  + 0.10 * freshnessScore
  + 0.10 * sessionDiversityScore
  - recencyPenalty
  - artistRepeatPenalty
  - sessionSkipPenalty
  + smallRandomJitter
```

This is a starting rule, not a magical truth.

All components should normalize approximately to `[0, 1]` before weights/penalties.

## 18.9 Selection

Do not always take the exact top-scoring track.

To prevent deterministic repetition:

1. rank candidates;
2. take a top window, e.g. top 10–20;
3. sample with probability influenced by score;
4. preserve diversity.

This randomness belongs only to Smart Shuffle and does not change Pure Shuffle semantics.

## 18.10 Explainability

For development/debug builds, provide an optional explanation:

```text
Why this track?
- strong evening completion history
- not played recently
- artist not repeated
- moderate discovery bonus
```

Do not expose raw internal debug scores in normal user UI unless useful.

---

# 18A. Source-Agnostic Model Training Pipeline

This section defines how the application may progress from heuristic Smart Shuffle to an actually learned personal recommender.

## 18A.1 Training objective

The model estimates how suitable a Track is for the current listening context.

Conceptually:

```text
(track features, current context, recent history)
                    │
                    ▼
                 Model
                    │
                    ▼
         predicted preference/reward
```

The prediction is used as one component of Smart Shuffle ranking.

The model MUST NOT control playback directly.

## 18A.2 Reward construction

Convert listening behavior into a bounded reward.

Starting example:

```text
manual selection                     +0.35
completion >= 90%                    +0.60
intentional replay                   +0.40
favorite action                      +0.50
played 60–90%                        +0.20
late skip                            -0.15
early skip                           -0.70
manual queue removal before play     -0.20
```

When multiple events occur for one playback instance, combine them and clamp final reward, for example to `[-1.0, +1.0]`.

Weights MUST be centralized and versioned.

Do not treat autoplay itself as positive feedback.

## 18A.3 Feature groups

### Track identity / collaborative-with-self features

Because this is a single-user system, Track and artist history can be encoded through:

- track aggregate affinity;
- artist aggregate affinity;
- album aggregate affinity;
- favorite state;
- normalized play/skip/completion statistics.

Avoid giant one-hot vectors when the library is large unless the chosen model handles them efficiently.

### Temporal features

At minimum:

- sine/cosine encoding of hour-of-day;
- day of week;
- weekend;
- named time bucket.

Cyclic encoding is preferred for hour/day where appropriate.

### Sequence features

Examples:

- previous Track affinity;
- previous artist;
- same-artist flag;
- number of tracks since same artist;
- recent average energy;
- recent skip streak;
- session position.

### Acoustic features

If available, consume normalized `AudioFeatureVector` data.

The model must still work when audio features are missing.

Represent missingness explicitly rather than substituting arbitrary zero values without a mask/strategy.

## 18A.4 Initial learned model family

For the first trained implementation, prefer a small **online linear ranking model / contextual bandit-style model** over a large neural network.

Reasons:

- small personal dataset;
- incremental learning;
- fast local training;
- interpretable weights;
- easy reset;
- low battery/memory overhead;
- deterministic tests.

A suitable initial abstraction is:

```text
interface PersonalizationModel {
    fun score(context: RecommendationContext, candidates: List<TrackFeatures>): List<TrackScore>
    suspend fun update(samples: List<TrainingSample>)
    suspend fun rebuild(dataset: TrainingDataset)
    suspend fun reset()
}
```

The implementation MAY later use:

- online logistic/linear regression;
- LinUCB-style contextual ranking;
- Thompson-sampling variants;
- pairwise ranking;
- a small neural model;
- an embedding-based ranker.

The interface must not assume one algorithm.

## 18A.5 Bootstrap strategy

Before enough data exists:

```text
if sampleCount < MIN_TRAINING_SAMPLES:
    use HeuristicAdaptiveScorer
else:
    use LearnedModel + heuristic safety/diversity layer
```

A starting `MIN_TRAINING_SAMPLES` may be 50–100 meaningful playback outcomes, but it should be configurable.

The user should not experience a broken Smart mode while the model is cold.

## 18A.6 Training schedule

Local training/update may happen:

- after a small batch of new meaningful events;
- when the player is idle;
- when the app is backgrounded and device conditions are suitable;
- through WorkManager for deferrable rebuilds.

Do not retrain on every playback progress event.

Example:

```text
incremental update:
every 10 new finalized playback outcomes

full rebuild:
after feature-schema change
after major model-version change
after user requests rebuild/reset
```

Battery-sensitive work should prefer charging/idle constraints when appropriate.

## 18A.7 Dataset builder

`TrainingDatasetBuilder` MUST operate on canonical tracks and be independent of provider.

Pseudo-flow:

```text
ListeningEvents
      │
      ├── join Track
      ├── join PreferenceStats
      ├── join Time Context
      ├── join Recent Session Context
      └── left join AudioFeatureVector
                    │
                    ▼
              TrainingSample[]
```

A Telegram track, local file, or future provider follows the exact same path.

## 18A.8 Audio feature extraction

Feature extraction is separate from model training.

Pipeline:

```text
Track
  ↓
choose any readable eligible TrackSource
  ↓
decode/analyze audio
  ↓
AudioFeatureVector
  ↓
training/ranking
```

Source selection for analysis SHOULD prefer:

1. readable local shared file;
2. app-managed offline copy;
3. complete TDLib local file;
4. download remote source if analysis has been explicitly scheduled.

Do not redownload audio when an equivalent local source is already readable.

## 18A.9 Feature invalidation

Re-extract features when:

- audio source content hash changes;
- extractor version changes;
- feature schema changes;
- stored vector is corrupt.

Do not re-extract just because a Telegram message ID or local path changed while content remained identical.

## 18A.10 Model output is not the final queue score

A learned preference score MUST pass through product constraints.

Example:

```text
finalSmartScore =
    learnedPreferenceScore
  + explorationBonus
  + freshnessScore
  + diversityScore
  - recencyPenalty
  - artistRepeatPenalty
  - sessionSkipPenalty
```

This prevents a model from collapsing into playing the same favorite songs repeatedly.

## 18A.11 Exploration during learned ranking

The learned model still needs exploration.

Use one of:

- score uncertainty bonus;
- Thompson-style sampling;
- epsilon/top-window exploration;
- explicit under-observed-track bonus.

Exploration state may use all canonical tracks regardless of provider.

## 18A.12 Model evaluation

Evaluate against held-out or temporally later local events.

Useful metrics:

- average predicted-vs-observed reward;
- pairwise ranking accuracy;
- early-skip rate in Smart mode;
- completion rate in Smart mode;
- manual override rate;
- diversity / artist repetition;
- performance by time bucket.

Do not optimize only for completion rate because that can overplay familiar long-term favorites.

## 18A.13 Reset and reproducibility

User action `Reset personalization` MUST:

- deactivate/delete learned model artifact;
- clear derived preference/model state according to the selected reset level;
- optionally preserve raw listening history if the UI offers separate choices;
- immediately fall back to neutral/heuristic behavior.

Developer builds should support exporting a redacted local model-debug report containing:

- model version;
- feature schema version;
- sample count;
- aggregate metrics;
- no raw audio.

---

# 19. Smart Shuffle Safety and Cold Start

## 19.1 Cold start

When there is insufficient history:

- use a neutral candidate score;
- prioritize diversity;
- mix favorites/manual picks if available;
- avoid aggressive conclusions from one interaction.

## 19.2 Negative feedback

One accidental skip must not permanently bury a track.

Use smoothing and evidence thresholds.

## 19.3 Session adaptation

Repeated early skips in the current session should temporarily reduce similar repeated choices.

Do not permanently apply a strong penalty from a single bad session.

## 19.4 Reset controls

Settings MUST eventually provide:

- clear listening history;
- reset Smart Shuffle learning;
- disable adaptive behavior;
- continue using Pure Shuffle without any history dependence.

---

# 20. Search and Library Organization

Search should be local and fast.

Search fields:

- title;
- artist;
- album;
- filename where useful.

Initial library views:

- Tracks;
- Artists;
- Albums;
- Playlists;
- Favorites;
- Downloads / Offline;
- Recently Played.

Telegram chat browsing is not the main library UI.

---

# 21. UX Requirements

## 21.1 Primary player states

The UI must clearly represent:

```text
IDLE
PREPARING
DOWNLOADING
BUFFERING
PLAYING
PAUSED
ERROR
```

Do not leave the user staring at an unresponsive Play button while a Telegram file downloads.

## 21.2 Track availability badges

Useful states:

- Offline;
- Cloud;
- Downloading;
- Unavailable.

Avoid cluttering every row with technical details.

## 21.3 Modes

The user must clearly see and choose:

```text
Ordered
Pure Shuffle
Smart Shuffle
```

Smart Shuffle must not masquerade as ordinary shuffle.

## 21.4 Now Playing

At minimum:

- artwork;
- title;
- artist;
- seek bar;
- elapsed/remaining duration;
- previous;
- play/pause;
- next;
- queue;
- shuffle mode;
- repeat mode;
- favorite;
- offline/download action.

## 21.5 Errors

Failures should be recoverable.

Examples:

- "This Telegram file is no longer available."
- "You're offline and this track hasn't been downloaded."
- "Telegram session expired. Reconnect to refresh cloud tracks."
- "Local file was moved or deleted."

Never crash because one source failed.

---

# 22. Security

## 22.1 Secrets

Do not commit production Telegram API credentials.

Do not log:

- verification codes;
- 2FA passwords;
- TDLib encryption keys;
- raw authentication tokens;
- private Telegram message content.

## 22.2 Local database

Room database contains listening habits.

Treat it as private app data.

If database export/debug dumps are implemented, they must be explicit developer/user actions.

## 22.3 TDLib local data

Follow current TDLib guidance for local database/encryption configuration.

Do not expose TDLib database files through public shared storage.

## 22.4 Debug logging

Release builds MUST minimize potentially identifying logs.

---

# 23. Performance Targets

These are product targets, not hard platform guarantees.

For a library around 10,000 tracks:

- initial local library UI should remain responsive;
- scrolling should not synchronously parse files;
- database queries should be paged or efficient;
- search should return quickly enough to feel immediate;
- hashing and artwork extraction should run off the main thread;
- Telegram full-history indexing should be paginated/backgrounded;
- playback commands should feel immediate for local tracks;
- recommendation scoring over a candidate set should complete well under perceptible UI latency.

Do not rank every track in an enormous library if candidate reduction can be used first.

---

# 24. Increment Plan

The implementation MUST proceed incrementally. Each increment must leave the app runnable and testable.

Do not implement later intelligence before basic playback and storage correctness work.

---

# Increment 0 — Project Foundation and Architectural Skeleton

## Goal

Create a stable Android project with the correct layers and no product-specific complexity yet.

## Deliverables

- Kotlin Android project.
- Jetpack Compose setup.
- Coroutines/Flow.
- Room.
- Hilt.
- Media3 dependencies.
- DataStore.
- test setup.
- package/module structure from this spec.
- basic navigation shell.
- build variants/config mechanism for Telegram credentials.
- CI build if repository infrastructure exists.

## Required interfaces

Create domain-facing interfaces such as:

```text
MusicLibraryRepository
PlaybackController
QueueRepository
DownloadRepository
TelegramRepository
ListeningHistoryRepository
RecommendationEngine
SettingsRepository
```

They may have fake implementations initially.

## Acceptance criteria

- clean build succeeds;
- unit tests run;
- app launches;
- no Telegram credential is committed;
- architecture does not let UI directly depend on TDLib/Room internals.

## Non-goals

- no real playback;
- no Telegram login;
- no Smart Shuffle.

---

# Increment 1 — Local Music Library

## Goal

Discover and display device-local audio.

## Deliverables

- runtime audio permission flow;
- MediaStore scanner;
- Room entities for Track and TrackSource;
- repository synchronization;
- Tracks library screen;
- basic title/artist/duration metadata;
- local artwork loading where available;
- incremental rescan;
- handling removed files.

## Behavior

- user grants permission;
- local songs appear;
- app restart preserves index;
- rescan reconciles new/deleted media.

## Acceptance criteria

1. At least MP3/M4A/FLAC test files visible through MediaStore appear.
2. Permission denial shows usable empty state.
3. Revoked permission does not crash.
4. Deleting a file externally marks/removes its source on rescan.
5. Scanning runs off main thread.
6. 1,000+ test rows do not freeze the UI.

## Non-goals

- Telegram;
- playlists;
- recommendation.

---

# Increment 2 — Core Playback and Background Media Service

## Goal

Turn the app into a correct local music player.

## Deliverables

- ExoPlayer;
- `MediaLibraryService`/`MediaSessionService`;
- MediaSession;
- player controller abstraction;
- Now Playing screen;
- notification/system controls;
- lock-screen control;
- audio focus;
- headset disconnect behavior;
- seek;
- previous/next;
- playback state persistence.

## Acceptance criteria

1. Local tracks play with screen on/off.
2. Playback continues when app UI leaves foreground.
3. Lock-screen Play/Pause/Next work.
4. Bluetooth/headset media buttons work where supported.
5. Disconnecting headphones does not unexpectedly blast audio through speaker.
6. Process/UI recreation does not corrupt playback state.
7. Seeking works.
8. error in one track advances/recoverably stops rather than crashing.

## Non-goals

- Telegram;
- downloads;
- adaptive playback.

---

# Increment 3 — Queue, Repeat, and True Pure Shuffle

## Goal

Build deterministic queue behavior and genuine shuffle semantics.

## Deliverables

- persisted queue;
- play now;
- play next;
- add to queue;
- reorder/remove;
- repeat off/one/all;
- Pure Shuffle engine using Fisher-Yates;
- shuffle cycle persistence;
- tests for shuffle invariants.

## Acceptance criteria

1. Every eligible track occurs once per Pure Shuffle cycle.
2. No personalization input can alter Pure Shuffle ordering.
3. Queue survives process recreation.
4. Reorder/remove works.
5. end-of-cycle creates a new permutation.
6. if library has >1 track, cycle boundary avoids immediate duplicate where practical.
7. unit tests validate thousands of shuffle cycles for structural invariants.

---

# Increment 4 — Telegram Client Foundation and Authentication

## Goal

Connect the app to the user's Telegram account through TDLib.

## Deliverables

- TDLib integration layer;
- asynchronous request/update adapter;
- authorization state machine;
- login UI;
- logout;
- session state persistence;
- API credential injection;
- error handling;
- developer documentation for obtaining API credentials.

## Acceptance criteria

1. User can reach authorized/ready state.
2. 2FA flow is supported.
3. app restart does not require unnecessary re-login when TDLib session remains valid.
4. logout cleans session state appropriately.
5. UI never blocks waiting synchronously for TDLib.
6. sensitive auth input is not logged.

## Non-goals

- importing all tracks yet;
- Smart Shuffle.

---

# Increment 5 — Telegram Music Source Selection and Indexing

## Goal

Import music metadata from explicitly selected Telegram chats.

## Deliverables

- chat/source picker;
- selected-source persistence;
- Saved Messages support;
- selected private chat/channel support;
- paginated history scan;
- audio/document eligibility filter;
- TelegramTrackSource persistence;
- incremental refresh;
- cloud/offline availability indicator.

## Acceptance criteria

1. User can select a Telegram music source.
2. Supported audio messages appear in the library.
3. voice notes remain excluded by default.
4. remote-only tracks display as Cloud.
5. new music can be imported without rebuilding entire database.
6. deleted/unavailable Telegram messages become missing sources after reconciliation.
7. duplicates are not created on repeated sync.

---

# Increment 6 — Unified Track Model and Deduplication

## Goal

Make local and Telegram music appear as one coherent library.

## Deliverables

- exact hash matching when possible;
- provisional metadata matcher;
- source merge logic;
- source priority logic;
- debug visibility into source linkage;
- safe unmerge/manual correction hook if needed.

## Acceptance criteria

1. identical local + Telegram file can resolve to one Track with two sources.
2. two different songs with same title do not merge solely by title.
3. deleting one source does not remove a Track that still has another valid source.
4. playback uses local source before downloading Telegram copy.
5. reconciliation is idempotent.

---

# Increment 7 — Offline Download Manager

## Goal

Allow Telegram-hosted music to become reliably available offline.

## Deliverables

- TDLib download coordinator;
- progress updates;
- Pin Offline action;
- remove offline copy;
- Download/Offline screen;
- download persistence;
- retry/cancel;
- pinned vs cache distinction;
- storage accounting.

## Acceptance criteria

1. user can pin a remote Telegram track offline.
2. after network is disabled, the pinned track still plays.
3. canceled/failed download exposes retry.
4. removing app offline copy does not delete Telegram message.
5. local MediaStore tracks are recognized as already offline.
6. cache cleanup never deletes user-owned MediaStore file.
7. pinned files are never evicted by temporary-cache cleanup.

---

# Increment 8 — Telegram On-Demand Playback and Prefetch

## Goal

Make remote Telegram tracks convenient even when not pre-downloaded.

## First implementation

Use reliable download-on-play.

## Deliverables

- remote Play triggers high-priority download;
- Preparing/Downloading UI;
- automatic playback when ready;
- next-track prefetch;
- network policy settings;
- cache retention;
- graceful network-loss behavior.

## Optional enhancement

Progressive/partial playback may be added only after the baseline is reliable.

## Acceptance criteria

1. selecting Cloud-only track begins understandable loading state.
2. playback starts without user having to manually press Download first.
3. app handles network loss during download.
4. prefetch is canceled when queue changes substantially.
5. offline mode never tries to auto-download.
6. metered-network policy is respected.

**MVP milestone:** After Increment 8, the core local + Telegram + offline player is usable.

---

# Increment 9 — Library UX, Search, Favorites, and Playlists

## Goal

Make the application usable as a daily music player.

## Deliverables

- Tracks;
- Artists;
- Albums;
- Favorites;
- Recently Played;
- Downloads;
- local search;
- playlists;
- add/remove/reorder playlist tracks;
- queue-to-playlist;
- source availability filters.

## Acceptance criteria

1. search finds title/artist quickly.
2. playlist order persists.
3. favorite state persists.
4. remote/offline availability is understandable.
5. playlist playback respects ordered/pure/smart mode choice.

---

# Increment 10 — Listening History and Local Behavioral Data

## Goal

Collect clean app-generated signals before building personalization.

## Deliverables

- ListeningSession;
- ListeningEvent;
- event definitions from this spec;
- completion/skip classification;
- per-track aggregate stats;
- per-time-bucket stats;
- history/settings screen;
- delete/reset controls.

## Acceptance criteria

1. manual play logs correct event.
2. completion is counted once.
3. early skip classification matches thresholds.
4. time bucket is based on local time at event occurrence.
5. Pure Shuffle behavior is unchanged by history.
6. user can clear history.
7. no behavioral events are uploaded by default.

---

# Increment 11 — Smart Shuffle v1: Deterministic Adaptive Heuristics

## Goal

Select music based on app-generated listening behavior, especially time-of-day patterns.

## Deliverables

- CandidateGenerator;
- AdaptiveScorer;
- smoothed global affinity;
- time-bucket affinity;
- recency penalty;
- artist repetition penalty;
- exploration bonus;
- score-weight configuration;
- Smart Shuffle queue generation;
- debug explanation.

## Acceptance criteria

1. Smart mode and Pure mode are clearly separate.
2. changing listening stats can alter Smart results.
3. changing listening stats does not alter Pure Shuffle semantics.
4. repeated early skips reduce short-term probability.
5. strong evening completion history produces measurable evening preference after enough observations.
6. unseen songs retain some exploration probability.
7. small libraries do not deadlock because of over-strict filters.
8. scorer has deterministic unit tests when random seed is fixed.
9. the initial learned implementation can train and score locally without requiring an external AI/ML API.

---

# Increment 12 — Source-Agnostic Learned Personalization

## Goal

Train and run a personal ranking model using eligible data from **all TrackSource types**, while keeping provider integration separate from recommendation logic.

## Deliverables

- `TrainingDatasetBuilder`;
- source-independent feature schema;
- reward builder from finalized listening outcomes;
- `AudioFeatureExtractor` abstraction;
- `AudioFeatureVector` persistence;
- background feature-extraction jobs;
- initial local online/linear personalization model;
- model state/version persistence;
- incremental model updates;
- full model rebuild;
- learned scorer integrated into Smart Shuffle;
- heuristic fallback;
- exploration/diversity layer after model score;
- reset/rebuild controls;
- debug evaluation report.

## Required source behavior

The following must all be able to contribute to the same model:

```text
LOCAL_MEDIASTORE
TELEGRAM_REMOTE          (after bytes/features are available when needed)
TDLIB_LOCAL
APP_OFFLINE_COPY
future TrackSource types
```

If the same logical Track has multiple physical sources, behavioral samples MUST be counted once at Track level.

Audio features need only be extracted from one suitable readable copy.

## Initial model

Prefer a small locally trainable model suitable for personal data, such as an online linear/contextual ranking model.

The model interface MUST be algorithm-independent so a later neural/embedding ranker can replace it.

## Acceptance criteria

1. training dataset creation contains eligible Tracks regardless of source type;
2. no recommendation/training code has a Telegram-specific exclusion path;
3. a local-only track and a Telegram-only track can both generate training samples;
4. the same song with two sources does not receive duplicated behavioral labels;
5. model can train/rebuild with behavioral features only;
6. model can consume audio features when they exist;
7. missing audio features do not make a Track unrankable;
8. after sufficient repeated positive evening outcomes, the learned model increases that Track's evening suitability relative to a neutral baseline;
9. repeated early skips create negative training signal;
10. model update does not modify Pure Shuffle;
11. model failure automatically falls back to heuristic Smart Shuffle;
12. reset returns the app to a valid cold-start state;
13. feature/model versions are persisted;
14. training work does not block audio playback or the main UI;
15. evaluation tests work with a fixed seed/dataset.

**Learned-personalization milestone:** After Increment 12, Smart Shuffle is no longer limited to hand-authored scoring rules and can learn from data originating from any supported music source.

---

# Increment 13 — Resilience, Privacy, and Storage Hardening

## Goal

Make the player trustworthy under real-world failure conditions.

## Deliverables

- database migration tests;
- corruption/error strategy;
- Telegram logout/relogin reconciliation;
- expired/missing source recovery;
- offline-first startup;
- download resume/retry;
- permission-revocation handling;
- process-death tests;
- storage-low behavior;
- privacy settings;
- release logging policy;
- export/import of app settings where appropriate.

## Acceptance criteria

Run scenario tests for:

1. airplane mode at startup;
2. network lost mid-download;
3. Telegram message deleted;
4. selected Telegram channel inaccessible;
5. Telegram session expired;
6. local audio permission revoked;
7. local file removed;
8. app killed while playing;
9. app killed while downloading;
10. low storage;
11. DB migration from previous schema;
12. queue item unavailable;
13. 10,000-track synthetic library.

---

# Increment 14 — Release Readiness

## Goal

Prepare for a distributable build.

## Deliverables

- final app name and branding;
- privacy policy;
- Telegram API disclosure;
- current platform/API integration review;
- current Google Play policy review;
- production API credential handling;
- app signing;
- crash reporting decision;
- dependency/license review;
- accessibility review;
- performance profiling;
- release checklist.

## Telegram-specific release checks

Before release, verify current rules for:

- API ID;
- API branding;
- title/logo restrictions;
- third-party client obligations;
- sponsored content if applicable to the implemented feature set;
- any current integration requirements relevant to the distributed build.

## Acceptance criteria

No public release should proceed while a known release-blocking integration, security, privacy, or store-policy issue is unresolved.

---

# 25. Future Increments — Explicitly Out of Initial Scope

These are valid future directions but must not complicate the MVP unnecessarily.

## Future A — Progressive Telegram Streaming

Implement custom partial-file playback/prefetch only if download-on-play is not sufficient.

Requires careful handling of:

- byte ranges;
- seek;
- partial availability;
- changing file references;
- download priority;
- cache consistency.

## Future B — Cross-device App State Sync

Sync:

- playlists;
- favorites;
- listening history;
- Smart Shuffle statistics;
- settings.

Must use explicit end-to-end privacy design.

Do not casually repurpose Telegram messages as an opaque database without reviewing product and API implications.

## Future C — iOS

Port domain rules while replacing:

- MediaStore;
- Android permissions;
- Media3 service;
- Android notification system.

## Future D — Desktop

Potential use of TDLib and platform-native or shared audio playback.

## Future E — Metadata Enrichment

Optional album/artist metadata provider.

Must be separate from Telegram and local playback correctness.

## Future F — Audio Analysis

BPM, key, loudness, energy, embeddings, etc.

This is explicitly not part of Smart Shuffle v1.

Audio analysis is source-agnostic: any readable, training-eligible TrackSource may supply bytes for feature extraction.

## Future G — Advanced Personalization Models

The core architecture already supports learned personalization.

Future upgrades may include:

- pairwise ranking;
- sequence models;
- compact neural rankers;
- learned audio embeddings;
- metric-learning / similarity models;
- per-track uncertainty estimates;
- multi-armed/contextual bandit exploration;
- model ensembles;
- optional cross-device model-state sync.

Any replacement MUST preserve:

- source-agnostic datasets;
- canonical Track-level identity;
- local/offline fallback;
- explicit model versioning;
- reset capability;
- Pure Shuffle independence;
- diversity/exploration constraints.

Do not couple a model architecture to Telegram-specific IDs or storage paths.

# 26. Recommendation Evaluation

Do not judge Smart Shuffle only by subjective impressions during development.

Track local aggregate metrics such as:

```text
Smart mode:
- early skip rate
- completion rate
- manual override rate
- replay rate

Pure shuffle:
- early skip rate
- completion rate
```

These metrics stay local unless the user explicitly opts into diagnostics.

The app MAY compare Smart vs Pure behavior locally, but must not silently turn Pure Shuffle into an experiment.

Useful offline evaluation:

- simulate historical events;
- ensure evening preference rises only after sufficient evidence;
- verify smoothing;
- verify exploration;
- verify penalties decay.

---

# 27. Database Constraints and Invariants

The following must always hold:

1. every `TrackSource` references exactly one `Track`;
2. a Track may have zero usable sources temporarily but should be marked unavailable;
3. `APP_OFFLINE_COPY` must represent a file controlled by this app;
4. MediaStore files must never be treated as app-owned cache;
5. Telegram source identity must preserve originating chat/message;
6. one physical Telegram source should not be imported repeatedly by sync;
7. one listening playback instance cannot generate duplicate completion events;
8. queue positions are unique within a queue;
9. playlist positions are stable and deterministic;
10. Pure Shuffle queue contains no duplicate track IDs within a cycle.

Use unique database indexes where practical.

Possible unique keys:

```text
TelegramTrackSource(accountId, chatId, messageId)
LocalMediaSource(contentUri)
QueueItem(queueId, queuePosition)
PlaylistTrack(playlistId, position)
```

---

# 28. Failure Handling Matrix

| Situation | Required behavior |
|---|---|
| Offline + local track | Play normally |
| Offline + pinned Telegram track | Play normally |
| Offline + remote-only Telegram track | Explain unavailable offline; do not spin forever |
| Telegram auth expired | Local library remains functional; request reconnect only for cloud features |
| Telegram source deleted | Mark source missing; use another source if available |
| Local file deleted | Mark local source missing; fall back to Telegram if available |
| Download fails | Retry action; retain metadata |
| Partial download after crash | Reconcile with TDLib/download state |
| DB record exists but file missing | Mark source missing, do not crash |
| Unsupported codec | Show playback error and skip/recover |
| Permission denied | Telegram/offline app-owned library may still work |
| Low storage | Stop new nonessential downloads; never delete pinned/user files silently |
| Corrupt artwork | Ignore artwork error; audio remains playable |

---

# 29. Settings

Initial settings should eventually include:

## Playback

- resume previous queue;
- gapless/crossfade only if later supported;
- repeat default;
- default playback mode.

## Shuffle

- Pure Shuffle;
- Smart Shuffle;
- Smart exploration preference.

## Telegram

- connected account;
- selected source chats;
- sync now;
- Wi-Fi-only cloud downloads;
- disconnect Telegram.

## Offline

- cache size;
- clear cache;
- offline downloads;
- prefetch enabled;
- prefetch on metered networks.

## Privacy

- listening history enabled;
- Smart Shuffle enabled;
- clear listening history;
- reset personalization;
- diagnostics opt-in if ever introduced.

---

# 30. Permissions

Request the smallest set of permissions necessary.

Expected Android permissions/capabilities may include:

- audio media read permission on Android versions that require it for reading other apps' shared audio;
- foreground media playback service permissions;
- notification permission where platform rules require/request it;
- network access.

Do not request:

- location;
- contacts;
- SMS;
- call logs;
- microphone;

unless a future feature has an explicit, user-visible reason and this specification is updated.

Permission requests should be contextual and explain why the feature needs them.

---

# 31. Testing Strategy

## 31.1 Unit tests

Mandatory targets:

- metadata normalization;
- deduplication scoring;
- source priority;
- Pure Shuffle;
- queue mutation;
- listening event classification;
- time bucket mapping;
- preference aggregation;
- adaptive scoring;
- candidate generation;
- cache eviction decisions.

## 31.2 Repository tests

Test Room-backed repository behavior:

- upsert;
- idempotent sync;
- source removal;
- merge;
- migrations.

## 31.3 Playback tests

Device/emulator tests:

- play/pause/seek;
- background;
- notification;
- audio focus;
- headset disconnect;
- queue transition;
- process recreation.

## 31.4 Telegram integration tests

Use a dedicated test account/source when possible.

Test:

- initial login;
- 2FA;
- session restore;
- source selection;
- history pagination;
- new message import;
- deletion;
- download;
- network interruption;
- logout.

Never run destructive tests against an important personal Telegram history.

## 31.5 Offline scenario tests

Must include:

```text
A. local track + airplane mode
B. pinned Telegram track + airplane mode
C. cloud-only Telegram track + airplane mode
D. network disappears after download starts
E. app killed after partial download
F. app reopened without Telegram connectivity
```

## 31.6 Scale tests

Synthetic DB:

- 10,000 tracks;
- 1,000 artists;
- 100 playlists;
- 100,000 listening events.

Verify acceptable query and scoring performance.

---

# 32. AI Agent Execution Rules

Any AI agent implementing this project MUST follow these rules.

## 32.1 Work one increment at a time

Do not jump from Increment 2 directly into Smart Shuffle.

An increment is complete only when:

- code compiles;
- relevant tests pass;
- acceptance criteria are satisfied;
- no known regression from previous increment remains.

## 32.2 Do not invent product behavior silently

If a minor unspecified implementation choice is required:

1. prefer the simplest behavior consistent with this document;
2. document the choice in code or an ADR if consequential;
3. do not expand scope.

## 32.3 Keep hard boundaries

The agent must preserve:

```text
UI
 ↓
Domain/use cases
 ↓
Repositories
 ↓
Room / MediaStore / TDLib
```

and:

```text
Recommendation Engine
        ↓ suggests Track IDs
Queue/Playback layer
        ↓ executes playback
```

The recommender must not directly manipulate TDLib.

## 32.4 Avoid premature abstraction

Do not create a distributed architecture for a single-device MVP.

Do not add:

- microservices;
- GraphQL;
- cloud functions;
- vector database;
- LLM;
- remote analytics pipeline;

unless a later requirement explicitly needs them.

## 32.5 Preserve local-first behavior

Every new Telegram feature must be tested with Telegram unavailable.

## 32.6 Preserve Pure Shuffle semantics

Any code change touching queue generation must include a regression test that preference/history changes cannot influence Pure Shuffle.

## 32.7 Database migrations are mandatory

Never use destructive migration in production merely because schema changed during development.

During very early prototype development destructive migration may be temporarily allowed, but must be removed before release-readiness increment.

## 32.8 Security

Never print secrets to logs.

Never commit real credentials.

## 32.9 API freshness

Before implementing platform-sensitive code, check current official documentation for:

- TDLib method signatures;
- Telegram/TDLib integration behavior;
- Android storage permissions;
- Media3 background playback rules.

Do not trust stale snippets from random tutorials over official docs.

---

# 33. Definition of Done for the Core Product

The core product is functionally complete when all of the following are true:

1. User can grant local audio access and see device music.
2. User can play local music in the background.
3. Lock-screen and system media controls work.
4. Queue survives normal lifecycle events.
5. Pure Shuffle is a true no-repeat permutation cycle.
6. User can log into Telegram through TDLib.
7. User can explicitly select a Telegram source chat.
8. Telegram music appears in the same library.
9. Local and Telegram duplicates can resolve to one logical Track.
10. Remote Telegram track can be downloaded and played.
11. User can pin Telegram tracks offline.
12. Offline playback works with no internet.
13. App does not require the official Telegram app to be open.
14. Search, favorites, and playlists work.
15. Listening events are stored locally.
16. Smart Shuffle can learn from behavioral and optional audio features associated with Tracks from any supported source.
17. Smart Shuffle learns time-of-day preference over repeated interactions using a source-agnostic training pipeline.
18. Smart Shuffle maintains exploration and diversity.
19. User can reset personalization.
20. Pure Shuffle remains unaffected by personalization.
21. Local functionality still works when Telegram is disconnected.
22. Major failure scenarios do not crash the player.
23. No production secrets exist in source control.
24. Release, security, privacy, and platform-integration checks are documented before public distribution.

---

# 34. Suggested Initial User Flow

## First launch

```text
Welcome
  ↓
Allow access to device music?  [Allow] [Not now]
  ↓
Local library becomes usable immediately
  ↓
Optional:
Connect Telegram Music Source
```

Telegram must be optional.

## Telegram setup

```text
Connect Telegram
  ↓
Authorization
  ↓
Choose music source
  ├── Saved Messages
  ├── My private music channel
  └── Another selected chat
  ↓
Index music
  ↓
Unified Library
```

## Playing a remote track

```text
Tap cloud-only track
  ↓
Preparing / downloading
  ↓
Play
  ↓
Optionally retained in temporary cache
```

## Pinning offline

```text
Track menu
  ↓
Download for offline
  ↓
Progress
  ↓
Offline badge
```

## Smart Shuffle learning

```text
User listens over days/weeks
  ↓
Local events accumulate
  ↓
Time-bucket preference statistics improve
  ↓
Smart Shuffle ranking adapts
```

No explicit "training" screen is required.

---

# 35. Example Smart Preference Evolution

This example illustrates desired behavior, not hard-coded values.

Track:

```text
Numb — Linkin Park
```

Observed history:

```text
Morning:
- 5 starts
- 1 completion
- 3 early skips
- 1 manual selection

Night:
- 12 starts
- 10 completions
- 0 early skips
- 5 manual selections
```

Expected behavior:

- Pure Shuffle probability remains exactly governed by the random permutation.
- Smart Shuffle assigns a materially stronger Night affinity than Morning affinity.
- Morning score is not permanently zero because exploration remains possible.
- one future successful Morning session can slowly update the statistics.

---

# 36. Example Source Reconciliation

Scenario:

The user has:

```text
/storage/.../Music/Numb.mp3
```

and the same song in selected Telegram channel message `12345`.

Desired DB representation:

```text
Track
id = 8f...

TrackSource A
type = LOCAL_MEDIASTORE
trackId = 8f...
availability = AVAILABLE_LOCAL

TrackSource B
type = TELEGRAM_REMOTE
trackId = 8f...
availability = REMOTE_ONLY
chatId = ...
messageId = 12345
```

When Play is pressed:

```text
choose A
```

When local file is deleted:

```text
A -> MISSING
B remains REMOTE_ONLY
```

When online and Play is pressed again:

```text
download/use B
```

The Track itself remains stable.

---

# 37. Non-Goals

The first product is not:

- a Telegram chat client;
- a social network;
- a Spotify replacement catalog;
- a music piracy/search service;
- a cloud upload service unrelated to the user's own Telegram account;
- an LLM-powered player;
- a recommendation service trained across users;
- a location-based activity tracker;
- a server-dependent streaming platform.

Do not add music-discovery scraping or third-party copyrighted catalogs without a separate product/legal decision.

---

# 38. Open Decisions That May Be Deferred

These are intentionally not blockers for early increments.

1. Final product name.
2. Exact visual design system.
3. Whether Smart Shuffle exposes detailed explanations in production.
4. Whether app-managed offline files live only in app-specific storage or can optionally be exported to Music.
5. Exact cache default size.
6. Exact adaptive score weights.
7. Whether multiple Telegram accounts are supported.
8. Whether more than one Telegram source chat can be enabled in the first Telegram release.
9. Crossfade/gapless behavior.
10. Cross-device app-state sync.

Until decided, choose the simplest architecture that does not prevent future support.

---

# 39. Implementation Priorities

If tradeoffs are necessary, prioritize in this order:

1. playback correctness;
2. offline reliability;
3. data integrity;
4. user privacy/security;
5. local-first behavior;
6. Telegram sync correctness;
7. queue correctness;
8. Pure Shuffle correctness;
9. UX responsiveness;
10. Smart Shuffle quality;
11. advanced streaming optimization;
12. cosmetic polish.

A clever recommendation system on top of unreliable playback is considered a failed implementation.

---

# 40. Official References

Agents should prefer these official sources and re-check them before implementation because APIs and policies can change.

## Telegram

- TDLib overview: https://core.telegram.org/tdlib
- TDLib getting started: https://core.telegram.org/tdlib/getting-started
- Telegram API ID setup: https://core.telegram.org/api/obtaining_api_id
- Telegram API Terms: https://core.telegram.org/api/terms
- Telegram file downloading: https://core.telegram.org/api/files
- Telegram file references: https://core.telegram.org/api/file-references
- `upload.getFile`: https://core.telegram.org/method/upload.getFile

## Android

- Media3 background playback: https://developer.android.com/media/media3/session/background-playback
- Basic Media3 / ExoPlayer player app: https://developer.android.com/media/implement/playback-app
- Shared media / MediaStore: https://developer.android.com/training/data-storage/shared/media
- Android 13 granular media permissions: https://developer.android.com/about/versions/13/behavior-changes-13
- Media3 offline downloads: https://developer.android.com/media/media3/exoplayer/downloading-media
- Room: https://developer.android.com/training/data-storage/room
- WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent

---

# 41. Final Directive to an AI Coding Agent

Build this system as a **local-first Android music player whose canonical library can be backed by local device audio, Telegram, app-managed offline files, and future music-source adapters**.

Start with reliable local playback. Add Telegram only after local playback, queueing, and Pure Shuffle are correct. Treat every provider as a `TrackSource`; never make provider identity the core identity of a song. Ensure remote songs can be downloaded for offline use, and avoid depending on the official Telegram app's private storage.

Implement **Pure Shuffle** as a genuine randomized permutation with no personalization.

Then collect private listening events and build personalization in two layers:

1. a transparent heuristic Smart Shuffle baseline;
2. a source-agnostic learned ranking model.

The training pipeline MUST be allowed to use eligible data associated with **any supported TrackSource**. Behavioral labels are attached to the canonical Track. Audio-content features may be extracted from any readable eligible copy of that Track. If the same song exists in multiple sources, do not duplicate its learning weight.

Maintain explicit provenance, feature versions, model versions, reset controls, exploration, diversity, and a safe fallback to the heuristic scorer. Never let learned personalization alter Pure Shuffle semantics.

The finished application must remain useful when the network is down, Telegram is disconnected, the model has not been trained yet, model training fails, or only local/offline files are available.

