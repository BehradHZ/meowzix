# Graph Report - meowzix  (2026-09-26)

## Corpus Check
- 192 files · ~123,012 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 24 file(s) not represented in the graph (top: .xml 16, (none) 3, .properties 3)

## Summary
- 2626 nodes · 6055 edges · 192 communities (148 shown, 44 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 94 edges (avg confidence: 0.87)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `e983e026`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- DataModule.kt
- HistoryDao
- TelegramRepository
- NowPlayingRoute.kt
- Meowzix UI/UX — Glass Music Redesign Backlog
- LibraryRouteV3.kt
- AndroidAudioVisualizer.kt
- AndroidPlaybackController.kt
- TelegramAuthRoute.kt
- TdLibClientAdapter
- ProgressiveQueue
- TrackArtwork.kt
- TdLibTelegramForwardRepository.kt
- PlaybackController
- MEOWZIX_SYSTEM_SPEC.md
- TdLibTelegramRepository
- PlaybackService.kt
- TelegramForwardSheet.kt
- LibraryDao.kt
- TelegramAuthStep
- TdLibTelegramRepository.kt
- TimeBucket
- Track
- TdLibStreamingDataSource.kt
- LocalMusicLibraryRepository
- TelegramAuthViewModel
- PlaybackServiceTest.kt
- MainActivity.kt
- Implementation checklist
- 18A. Source-Agnostic Model Training Pipeline
- OfflineDownload
- Implementation checklist
- SourceAvailability
- 7. Domain Model
- PlaybackCatalog
- playbackmode
- LibraryDao
- PersistedPlaybackSession
- MeowzixHaptics.kt
- 4. Source-Agnostic Training and Data Provenance
- Implementation checklist
- LibraryRouteV2.kt
- Implementation checklist
- QueueRepository
- 18. Smart Shuffle / Adaptive Playback v1
- 32. AI Agent Execution Rules
- 10. Telegram Integration
- Type.kt
- TelegramForwardRepository
- DownloadRecordEntity
- gradlew
- migration
- MeowzixApp.kt
- DownloadRepository
- ListeningHistoryRepository
- RecommendationEngine.kt
- PlaybackMode
- NowPlayingViewModel
- 25. Future Increments — Explicitly Out of Initial Scope
- DownloadsViewModel
- 3. Core Product Decisions
- entity
- AndroidPcmAudioFeatureExtractor.kt
- PerformanceMetrics
- PlaylistRepository
- 17. Listening Event Semantics
- tracksourcetype
- 31. Testing Strategy
- ResolvingPlaybackController
- MeowzixDatabase
- 16. Pure Shuffle Specification
- 21. UX Requirements
- 29. Settings
- 34. Suggested Initial User Flow
- locallibraryrefreshresult
- Increment 0 — Project Foundation and Architectural Skeleton
- Increment 1 — Local Music Library
- Increment 8 — Telegram On-Demand Playback and Prefetch
- Increment 12 — Source-Agnostic Learned Personalization
- 11. Unified Library and Deduplication
- 12. Playback Engine
- 14. Offline Storage Model
- 19. Smart Shuffle Safety and Cold Start
- 22. Security
- Increment 2 — Core Playback and Background Media Service
- Increment 4 — Telegram Client Foundation and Authentication
- Increment 14 — Release Readiness
- RTK - Rust Token Killer (Codex CLI)
- ListeningEventType
- 13. Remote Playback Strategy
- 9. Local Music Discovery
- Increment 3 — Queue, Repeat, and True Pure Shuffle
- Increment 5 — Telegram Music Source Selection and Indexing
- Increment 6 — Unified Track Model and Deduplication
- Increment 7 — Offline Download Manager
- Increment 9 — Library UX, Search, Favorites, and Playlists
- Increment 10 — Listening History and Local Behavioral Data
- Increment 11 — Smart Shuffle v1: Deterministic Adaptive Heuristics
- Increment 13 — Resilience, Privacy, and Storage Hardening
- AGENTS.md
- Implementation checklist
- MorphingPlayerOverlay.kt
- TdLibTelegramForwardRepository
- Increment 3 — Queue, Repeat, and True Pure Shuffle
- PlaybackServiceTest
- tdapi
- MigrationTest
- LibraryViewModel.kt
- PlaybackService
- AudioPermissionStatus
- PersonalizationDao
- TdLibDownloadRepository
- RoomListeningHistoryRepository
- CurlyMusicSlider.kt
- PlaylistDao
- AudioPermissionTest
- TelegramChatKind
- RoomPlaylistRepositoryTest
- Increment 6 — Unified Track Model and Deduplication
- Increment 7 — Offline Download Manager
- Increment 8 — Telegram On-Demand Playback and Prefetch
- Increment 9 — Library UX, Search, Favorites, and Playlists
- nowplayingtrack
- .outcome
- uuid
- TelegramDao
- channel
- receiveasflow
- TelegramSendDao
- RoomListeningHistoryRepositoryTest
- telegramchatkind
- telegramsyncresult
- TdLibRemoteTrackPlaybackResolver
- Increment 10 — Listening History and Local Behavioral Data
- RoomPlaylistRepository
- .refreshSelectableChats
- LibraryScreenV3
- INCREMENT_11_SMART_SHUFFLE.md
- DataStoreSettingsRepository.kt
- .newCycle
- LocalLinearPersonalizationModel
- TelegramSendState
- RecommendationEngine
- .generate
- ArtworkRepairCoordinator
- TelegramSendQueueWorker.kt
- PersonalizationModel.kt
- LibraryGroupMode
- PlaybackContextPolicyStore
- QueueActionFeedback.kt
- SettingsRepository
- PlaylistGroupBy
- NoOpPersonalizationModel
- NetworkPlaybackSettings
- .sample
- .persistTelegramMessage
- QueueViewModel.kt
- PagedLibraryTracks.kt
- LibrarySortMode
- .preferenceScore
- HistoryViewModel
- TelegramForwardOptions
- PersonalizationTrainer
- calculateChatDownloadProgress
- .observeEvents
- PersonalizationFeatureVectorizerTest
- LibrarySectionV2
- LibrarySectionV3
- ProgressiveQueueTailAppendTest
- telegramSendDedupeKey
- AvailabilityV3
- flattenGroupedTracksV3
- ArtworkGestureAxis
- .handlePlaybackFailure
- PlaylistSortMode
- PlaylistListV3
- PlaybackStateStore
- audiomanager
- hypot
- keyboardarrowup
- listeningeventsemantics
- listeningeventtype
- ln1p
- playbackstatus
- sourceavailability
- trackartworkbackdrop
- volumedown
- volumeoff

## God Nodes (most connected - your core abstractions)
1. `Track` - 60 edges
2. `PlaybackMode` - 50 edges
3. `TdLibTelegramRepository` - 43 edges
4. `ProgressiveQueue` - 43 edges
5. `LibraryDao` - 40 edges
6. `PlaybackService` - 35 edges
7. `AndroidPlaybackController` - 34 edges
8. `ResolvingPlaybackController` - 32 edges
9. `RepeatMode` - 31 edges
10. `LibraryViewModel` - 31 edges

## Surprising Connections (you probably didn't know these)
- `4.1 Default training policy` --references--> `Track`  [INFERRED]
  docs/MEOWZIX_SYSTEM_SPEC.md → app/src/main/java/dev/behradhz/meowzix/core/model/Track.kt
- `6. Repository / Module Structure` --references--> `Track`  [INFERRED]
  docs/MEOWZIX_SYSTEM_SPEC.md → app/src/main/java/dev/behradhz/meowzix/core/model/Track.kt
- `9.2 Local-source import behavior` --references--> `Track`  [INFERRED]
  docs/MEOWZIX_SYSTEM_SPEC.md → app/src/main/java/dev/behradhz/meowzix/core/model/Track.kt
- `C. Architecture skeleton` --references--> `MusicLibraryRepository`  [INFERRED]
  docs/increments/INCREMENT_00_PROJECT_FOUNDATION.md → app/src/main/java/dev/behradhz/meowzix/domain/library/MusicLibraryRepository.kt
- `B. Audio behavior and recovery` --references--> `PlaybackController`  [INFERRED]
  docs/increments/INCREMENT_02_CORE_PLAYBACK.md → app/src/main/java/dev/behradhz/meowzix/domain/playback/PlaybackController.kt

## Import Cycles
- None detected.

## Communities (192 total, 44 thin omitted)

### Community 0 - "DataModule.kt"
Cohesion: 0.09
Nodes (20): AndroidPcmAudioFeatureExtractor, bindAudioFeatureExtractor(), bindPersonalizationModel(), bindRemoteTrackPlaybackResolver(), AudioFeatureExtractor, PersonalizationModel, RemoteTrackPlaybackResolver, binds (+12 more)

### Community 1 - "HistoryDao"
Cohesion: 0.11
Nodes (6): HistoryDao, Flow, ListeningEventEntity, ListeningSessionEntity, TrackPreferenceStatsEntity, TrackTimePreferenceEntity

### Community 2 - "TelegramRepository"
Cohesion: 0.12
Nodes (5): bindTelegramRepository(), StateFlow, TelegramAuthState, TelegramRepository, TelegramSyncResult

### Community 3 - "NowPlayingRoute.kt"
Cohesion: 0.12
Nodes (23): activityresultcontracts, ArtworkGestureZone(), EmptyNowPlaying(), formatDuration(), FloatArray, HazeState, Modifier, ModeIconButton() (+15 more)

### Community 4 - "Meowzix UI/UX — Glass Music Redesign Backlog"
Cohesion: 0.08
Nodes (23): 10. Real audio-reactive spectrum / “equalizer” visualization, 11. Actual DSP equalizer controls — later audio feature, 12. Queue, 13. Telegram, 14. Future music features, 15. Accessibility and performance, 16. Immediate implementation sequence on `ui`, 1. Direction (+15 more)

### Community 5 - "LibraryRouteV3.kt"
Cohesion: 0.05
Nodes (99): album, alignment, animatecontentsize, animatedpasstate, DownloadRowCard(), DownloadsRoute(), SectionTitle(), SettingToggle() (+91 more)

### Community 6 - "AndroidAudioVisualizer.kt"
Cohesion: 0.12
Nodes (12): bindAudioVisualizerRepository(), AudioSpectrumState, AudioVisualizerRepository, StateFlow, AndroidAudioVisualizer, ByteArray, FloatArray, StateFlow (+4 more)

### Community 7 - "AndroidPlaybackController.kt"
Cohesion: 0.11
Nodes (16): EventTimeContext, QueueItem, AndroidPlaybackController, containsTrack(), currentTimeBucket(), indexOfTrack(), MediaController, StateFlow (+8 more)

### Community 8 - "TelegramAuthRoute.kt"
Cohesion: 0.10
Nodes (29): TelegramMusicSourceState, HazeState, MessageState(), OtherDeviceConfirmation(), ProgressState(), ReadyState(), RegistrationForm(), SingleValueForm() (+21 more)

### Community 9 - "TdLibClientAdapter"
Cohesion: 0.16
Nodes (12): TdLibClientAdapterTest, AuthorizationReadyGate, Exception, Flow, TdApi, TdLibClientAdapter, TdLibException, AuthorizationReadyGateTest (+4 more)

### Community 10 - "ProgressiveQueue"
Cohesion: 0.12
Nodes (5): PlayableTrack, ProgressiveQueue, ProgressiveQueueSnapshot, QueueWindowPlan, ProgressiveQueueTest

### Community 11 - "TrackArtwork.kt"
Cohesion: 0.12
Nodes (26): ContinueListeningCard(), ArtworkBitmapCache, artworkTargetBucket(), decodeSampledArtwork(), Context, Dp, Modifier, Uri (+18 more)

### Community 12 - "TdLibTelegramForwardRepository.kt"
Cohesion: 0.09
Nodes (22): Flow, Uri, localPhotoRef(), safeExtension(), telegramUsernameCandidate(), toForwardSummary(), toSummary(), TelegramChatSummary (+14 more)

### Community 13 - "PlaybackController"
Cohesion: 0.11
Nodes (12): bindPlaybackController(), StateFlow, NowPlayingTrack, PlaybackController, PlaybackStatus, BUFFERING, DOWNLOADING, ERROR (+4 more)

### Community 14 - "MEOWZIX_SYSTEM_SPEC.md"
Cohesion: 0.08
Nodes (23): 15. Queue Semantics, 1. Purpose, 20. Search and Library Organization, 23. Performance Targets, 24. Increment Plan, 26. Recommendation Evaluation, 28. Failure Handling Matrix, 2. Product Vision (+15 more)

### Community 15 - "TdLibTelegramRepository"
Cohesion: 0.20
Nodes (3): StateFlow, TdLibTelegramRepository, toDomainStep()

### Community 16 - "PlaybackService.kt"
Cohesion: 0.10
Nodes (19): acceptedresultbuilder, activityoptions, Job, audioattributes, bundle, cancel, CommandButton, connectionresult (+11 more)

### Community 17 - "TelegramForwardSheet.kt"
Cohesion: 0.09
Nodes (29): ForwardOptionRow(), GlassOptionGroup(), HazeState, statusLabel(), telegramForwardRepository(), TelegramForwardSheet(), TelegramSendStatusCard(), GlassSurface() (+21 more)

### Community 18 - "LibraryDao.kt"
Cohesion: 0.30
Nodes (6): LocalPlaybackRow, dao, insert, onconflictstrategy, query, upsert

### Community 19 - "TelegramAuthStep"
Cohesion: 0.14
Nodes (14): Closed, Closing, ConfigurationRequired, Initializing, LoggingOut, TelegramAuthStep, Unsupported, WaitCode (+6 more)

### Community 20 - "TdLibTelegramRepository.kt"
Cohesion: 0.09
Nodes (41): TextNormalizer, localReusePriority(), NetworkPolicy, NetworkUse, PREFETCH, USER_REQUEST, applicationcontext, atomicboolean (+33 more)

### Community 21 - "TimeBucket"
Cohesion: 0.12
Nodes (16): TimeBucket, AFTERNOON, EARLY_MORNING, EVENING, LATE_NIGHT, MORNING, NIGHT, AudioFeatureExtractor (+8 more)

### Community 22 - "Track"
Cohesion: 0.14
Nodes (7): Track, PlaybackContextKeys, albumV3(), artistV3(), groupKeyV3(), orderTracksV3(), LibraryViewModel

### Community 23 - "TdLibStreamingDataSource.kt"
Cohesion: 0.11
Nodes (17): ByteArray, Uri, MeowzixDataSourceFactory, RoutingDataSource, TdLibStreamingDataSource, BaseDataSource, c, DataSource (+9 more)

### Community 24 - "LocalMusicLibraryRepository"
Cohesion: 0.05
Nodes (20): FakeLocalMediaScanner, LocalMusicLibraryRepositoryTest, bindLocalMediaScanner(), LocalMediaScanner, MediaStoreScanner, ScannedLocalTrack, ExistingLocalSourceSnapshot, LocalLibraryReconciler (+12 more)

### Community 26 - "PlaybackServiceTest.kt"
Cohesion: 0.07
Nodes (35): ActivityScenario, after, androidjunit4, StartupSmokeTest, applicationprovider, assertarrayequals, assertequals, assertfalse (+27 more)

### Community 27 - "MainActivity.kt"
Cohesion: 0.20
Nodes (14): androidentrypoint, Bundle, ComponentActivity, TelegramForwardActivity, MeowzixTheme(), enableedgetoedge, fillmaxsize, getvalue (+6 more)

### Community 28 - "Implementation checklist"
Cohesion: 0.10
Nodes (18): A. TDLib integration, B. Authorization state machine, C. Session and logout behavior, D. Login UI and setup, E. Verification, External validation still required, Implementation checklist, Increment 4 — Telegram Client Foundation and Authentication (+10 more)

### Community 29 - "18A. Source-Agnostic Model Training Pipeline"
Cohesion: 0.11
Nodes (18): 18A.10 Model output is not the final queue score, 18A.11 Exploration during learned ranking, 18A.12 Model evaluation, 18A.13 Reset and reproducibility, 18A.1 Training objective, 18A.2 Reward construction, 18A.3 Feature groups, 18A.4 Initial learned model family (+10 more)

### Community 30 - "OfflineDownload"
Cohesion: 0.15
Nodes (23): OfflineDownload, LibraryTrackAvailability, CLOUD, OFFLINE, UNAVAILABLE, Flow, PlaylistSummary, availabilityLabelV2() (+15 more)

### Community 31 - "Implementation checklist"
Cohesion: 0.13
Nodes (14): A. Platform and permission boundary, Acceptance criteria, B. Canonical persistence model, C. MediaStore ingestion, D. Idempotent reconciliation, E. Repository boundary, F. Library UI, G. Verification (+6 more)

### Community 32 - "SourceAvailability"
Cohesion: 0.12
Nodes (14): SourceAvailability, AVAILABLE_LOCAL, DOWNLOADING, ERROR, MISSING, PARTIAL, REMOTE_ONLY, TrackSourceType (+6 more)

### Community 33 - "7. Domain Model"
Cohesion: 0.15
Nodes (13): 7.10 PreferenceStats, 7.11 AudioFeatureVector, 7.13 ModelState, 7.1 Track, 7.2 TrackSource, 7.3 TelegramTrackSource, 7.4 LocalMediaSource, 7.5 DownloadRecord (+5 more)

### Community 36 - "LibraryDao"
Cohesion: 0.09
Nodes (8): Flow, LibraryDao, LocalMediaSourceEntity, TrackEntity, TrackSourceEntity, localSourcePriority(), localSourcePriority(), PagingSource

### Community 37 - "PersistedPlaybackSession"
Cohesion: 0.24
Nodes (4): PersistedPlaybackItem, PersistedPlaybackSession, PlaybackSessionCodec, PlaybackSessionCodecTest

### Community 38 - "MeowzixHaptics.kt"
Cohesion: 0.11
Nodes (24): horizontalSwipeNavigation(), Modifier, Modifier, MeowzixHapticCue, DragDrop, DragStart, LightClick, LongPress (+16 more)

### Community 39 - "4. Source-Agnostic Training and Data Provenance"
Cohesion: 0.17
Nodes (12): 4.1 Default training policy, 4.2 Data classes that may be used for training, 4.3 Source provenance, 4.4 Deduplicate before learning, 4.5 Local-first training, 4.6 Model lifecycle, 4.7 No provider-specific model contract, 4. Source-Agnostic Training and Data Provenance (+4 more)

### Community 40 - "Implementation checklist"
Cohesion: 0.18
Nodes (10): A. Reproducible Android project, B. Foundation libraries, C. Architecture skeleton, D. Configuration and safety, E. Acceptance verification, Implementation checklist, Increment 0 — Project Foundation and Architectural Skeleton, Non-goals (+2 more)

### Community 41 - "LibraryRouteV2.kt"
Cohesion: 0.08
Nodes (42): AlbumKeyV2, albumNameV2(), AlbumsSectionV2(), artistNameV2(), ArtistsSectionV2(), AvailabilityFilterV2, ALL, CLOUD (+34 more)

### Community 42 - "Implementation checklist"
Cohesion: 0.18
Nodes (10): A. Playback engine and Android service, Acceptance verification — 2026-09-17, B. Audio behavior and recovery, C. Queue construction and state persistence, D. Now Playing UI, E. Verification, Implementation checklist, Increment 2 — Core Playback and Background Media Service (+2 more)

### Community 43 - "QueueRepository"
Cohesion: 0.15
Nodes (4): bindQueueRepository(), StateFlow, QueueRepository, QueueState

### Community 44 - "18. Smart Shuffle / Adaptive Playback v1"
Cohesion: 0.18
Nodes (11): 18.10 Explainability, 18.1 Goal, 18.2 Candidate generation, 18.3 Affinity statistics, 18.4 Time affinity, 18.5 Recency penalty, 18.6 Artist repetition penalty, 18.7 Exploration bonus (+3 more)

### Community 45 - "32. AI Agent Execution Rules"
Cohesion: 0.20
Nodes (10): 32.1 Work one increment at a time, 32.2 Do not invent product behavior silently, 32.3 Keep hard boundaries, 32.4 Avoid premature abstraction, 32.5 Preserve local-first behavior, 32.6 Preserve Pure Shuffle semantics, 32.7 Database migrations are mandatory, 32.8 Security (+2 more)

### Community 46 - "10. Telegram Integration"
Cohesion: 0.22
Nodes (9): 10.1 Use TDLib, not Bot API, 10.2 Authentication, 10.3 Credentials, 10.4 Source selection, 10.5 Telegram message eligibility, 10.6 Telegram metadata, 10.7 Telegram file lifecycle, 10.8 Sync (+1 more)

### Community 47 - "Type.kt"
Cohesion: 0.50
Nodes (3): sp, textstyle, typography

### Community 48 - "TelegramForwardRepository"
Cohesion: 0.15
Nodes (9): bindTelegramForwardRepository(), toDomain(), Flow, TelegramForwardRepository, TelegramSendJob, TelegramDestinationRow(), MeowzixApplication, Application (+1 more)

### Community 49 - "DownloadRecordEntity"
Cohesion: 0.19
Nodes (5): DownloadDao, Flow, DownloadRecordEntity, CacheEvictionPolicy, CacheEvictionPolicyTest

### Community 50 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 52 - "MeowzixApp.kt"
Cohesion: 0.10
Nodes (29): HistoryRoute(), HomeRoute(), HomeTrackSection(), PlaylistSection(), SectionTitle(), LibraryRoute(), ProfileDestinationRow(), ProfileRoute() (+21 more)

### Community 54 - "DownloadRepository"
Cohesion: 0.12
Nodes (9): bindDownloadRepository(), DownloadRepository, DownloadStatus, CANCELED, COMPLETED, DOWNLOADING, FAILED, QUEUED (+1 more)

### Community 55 - "ListeningHistoryRepository"
Cohesion: 0.25
Nodes (3): bindListeningHistoryRepository(), Flow, ListeningHistoryRepository

### Community 56 - "RecommendationEngine.kt"
Cohesion: 0.17
Nodes (8): AdaptiveScorer, AdaptiveScoreWeights, CandidateGenerator, PreferenceSnapshot, ScoreBreakdown, SmartCandidate, SmartSelector, AdaptiveScorerTest

### Community 57 - "PlaybackMode"
Cohesion: 0.15
Nodes (19): PlaybackMode, ORDERED, PURE_SHUFFLE, SMART_SHUFFLE, RepeatMode, ALL, OFF, ONE (+11 more)

### Community 60 - "NowPlayingViewModel"
Cohesion: 0.05
Nodes (21): bindAudioOutputController(), AudioOutputController, AudioOutputRoute, AudioOutputState, StateFlow, AudioOutputRow(), AudioOutputSheet(), NowPlayingRoute() (+13 more)

### Community 61 - "25. Future Increments — Explicitly Out of Initial Scope"
Cohesion: 0.25
Nodes (8): 25. Future Increments — Explicitly Out of Initial Scope, Future A — Progressive Telegram Streaming, Future B — Cross-device App State Sync, Future C — iOS, Future D — Desktop, Future E — Metadata Enrichment, Future F — Audio Analysis, Future G — Advanced Personalization Models

### Community 62 - "DownloadsViewModel"
Cohesion: 0.22
Nodes (5): ChatDownloadSession, DownloadsViewModel, isActiveDownload(), isTerminalDownload(), ViewModel

### Community 63 - "3. Core Product Decisions"
Cohesion: 0.17
Nodes (12): TrackSource, 27. Database Constraints and Invariants, 3.1 Local-first, not Telegram-first, 3.2 Android-first, 3.3 No custom backend in the initial architecture, 3.4 Telegram must be modeled as a source, not as the Track itself, 3.5 Pure Shuffle and Smart Shuffle are separate features, 3.6 Offline playback is a first-class requirement (+4 more)

### Community 64 - "entity"
Cohesion: 0.33
Nodes (4): entity, foreignkey, index, primarykey

### Community 65 - "AndroidPcmAudioFeatureExtractor.kt"
Cohesion: 0.08
Nodes (28): AudioFeatureDao, AudioFeatureVectorEntity, accept(), add(), decode(), extract(), AudioFeatureVector, DoubleArray (+20 more)

### Community 66 - "PerformanceMetrics"
Cohesion: 0.12
Nodes (10): PerformanceMetrics, PerformanceSnapshot, averageOrZero(), format4(), Outcome, PersonalizationDebugReportBuilder, Prediction, Bundle (+2 more)

### Community 68 - "17. Listening Event Semantics"
Cohesion: 0.29
Nodes (7): 17.1 Completion, 17.2 Early skip, 17.3 Late skip, 17.4 Manual selection, 17.5 Replay, 17.6 Seek, 17. Listening Event Semantics

### Community 70 - "31. Testing Strategy"
Cohesion: 0.29
Nodes (7): 31.1 Unit tests, 31.2 Repository tests, 31.3 Playback tests, 31.4 Telegram integration tests, 31.5 Offline scenario tests, 31.6 Scale tests, 31. Testing Strategy

### Community 71 - "ResolvingPlaybackController"
Cohesion: 0.10
Nodes (3): StateFlow, T, ResolvingPlaybackController

### Community 72 - "MeowzixDatabase"
Cohesion: 0.21
Nodes (6): MeowzixDatabase, DatabaseModule, Context, database, RoomDatabase, typeconverters

### Community 73 - "16. Pure Shuffle Specification"
Cohesion: 0.33
Nodes (6): 16.1 Definition, 16.2 Algorithm, 16.3 No personalization, 16.4 Queue mutation, 16.5 Test properties, 16. Pure Shuffle Specification

### Community 74 - "21. UX Requirements"
Cohesion: 0.33
Nodes (6): 21.1 Primary player states, 21.2 Track availability badges, 21.3 Modes, 21.4 Now Playing, 21.5 Errors, 21. UX Requirements

### Community 75 - "29. Settings"
Cohesion: 0.33
Nodes (6): 29. Settings, Offline, Playback, Privacy, Shuffle, Telegram

### Community 76 - "34. Suggested Initial User Flow"
Cohesion: 0.33
Nodes (6): 34. Suggested Initial User Flow, First launch, Pinning offline, Playing a remote track, Smart Shuffle learning, Telegram setup

### Community 78 - "Increment 0 — Project Foundation and Architectural Skeleton"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Deliverables, Goal, Increment 0 — Project Foundation and Architectural Skeleton, Non-goals, Required interfaces

### Community 79 - "Increment 1 — Local Music Library"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Behavior, Deliverables, Goal, Increment 1 — Local Music Library, Non-goals

### Community 80 - "Increment 8 — Telegram On-Demand Playback and Prefetch"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Deliverables, First implementation, Goal, Increment 8 — Telegram On-Demand Playback and Prefetch, Optional enhancement

### Community 81 - "Increment 12 — Source-Agnostic Learned Personalization"
Cohesion: 0.33
Nodes (6): Acceptance criteria, Deliverables, Goal, Increment 12 — Source-Agnostic Learned Personalization, Initial model, Required source behavior

### Community 82 - "11. Unified Library and Deduplication"
Cohesion: 0.40
Nodes (5): 11.1 Exact deduplication, 11.2 Provisional metadata matching, 11.3 Merge confidence, 11.4 Source priority for playback, 11. Unified Library and Deduplication

### Community 83 - "12. Playback Engine"
Cohesion: 0.40
Nodes (5): 12.1 Media3, 12.2 Audio focus, 12.3 Resume, 12.4 Supported file formats, 12. Playback Engine

### Community 84 - "14. Offline Storage Model"
Cohesion: 0.40
Nodes (5): 14.1 Pinned offline, 14.2 Temporary cache, 14.3 Local shared file, 14.4 Storage cleanup, 14. Offline Storage Model

### Community 85 - "19. Smart Shuffle Safety and Cold Start"
Cohesion: 0.40
Nodes (5): 19.1 Cold start, 19.2 Negative feedback, 19.3 Session adaptation, 19.4 Reset controls, 19. Smart Shuffle Safety and Cold Start

### Community 86 - "22. Security"
Cohesion: 0.40
Nodes (5): 22.1 Secrets, 22.2 Local database, 22.3 TDLib local data, 22.4 Debug logging, 22. Security

### Community 87 - "Increment 2 — Core Playback and Background Media Service"
Cohesion: 0.40
Nodes (5): Acceptance criteria, Deliverables, Goal, Increment 2 — Core Playback and Background Media Service, Non-goals

### Community 88 - "Increment 4 — Telegram Client Foundation and Authentication"
Cohesion: 0.40
Nodes (5): Acceptance criteria, Deliverables, Goal, Increment 4 — Telegram Client Foundation and Authentication, Non-goals

### Community 89 - "Increment 14 — Release Readiness"
Cohesion: 0.40
Nodes (5): Acceptance criteria, Deliverables, Goal, Increment 14 — Release Readiness, Telegram-specific release checks

### Community 90 - "RTK - Rust Token Killer (Codex CLI)"
Cohesion: 0.40
Nodes (4): Meta Commands, RTK - Rust Token Killer (Codex CLI), Rule, Verification

### Community 91 - "ListeningEventType"
Cohesion: 0.17
Nodes (12): ListeningEventType, AUTO_SELECTED, FAVORITED, MANUAL_SELECTED, PLAY_COMPLETED, PLAY_STARTED, PLAY_STOPPED, REPLAYED (+4 more)

### Community 92 - "13. Remote Playback Strategy"
Cohesion: 0.50
Nodes (4): 13.1 Initial strategy: download-on-play, 13.2 Progressive playback is an enhancement, 13.3 Prefetch, 13. Remote Playback Strategy

### Community 93 - "9. Local Music Discovery"
Cohesion: 0.50
Nodes (4): 9.1 MediaStore, 9.2 Local-source import behavior, 9.3 Local availability, 9. Local Music Discovery

### Community 94 - "Increment 3 — Queue, Repeat, and True Pure Shuffle"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 3 — Queue, Repeat, and True Pure Shuffle

### Community 95 - "Increment 5 — Telegram Music Source Selection and Indexing"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 5 — Telegram Music Source Selection and Indexing

### Community 96 - "Increment 6 — Unified Track Model and Deduplication"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 6 — Unified Track Model and Deduplication

### Community 97 - "Increment 7 — Offline Download Manager"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 7 — Offline Download Manager

### Community 98 - "Increment 9 — Library UX, Search, Favorites, and Playlists"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 9 — Library UX, Search, Favorites, and Playlists

### Community 99 - "Increment 10 — Listening History and Local Behavioral Data"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 10 — Listening History and Local Behavioral Data

### Community 100 - "Increment 11 — Smart Shuffle v1: Deterministic Adaptive Heuristics"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 11 — Smart Shuffle v1: Deterministic Adaptive Heuristics

### Community 101 - "Increment 13 — Resilience, Privacy, and Storage Hardening"
Cohesion: 0.50
Nodes (4): Acceptance criteria, Deliverables, Goal, Increment 13 — Resilience, Privacy, and Storage Hardening

### Community 103 - "Implementation checklist"
Cohesion: 0.15
Nodes (12): Ready, A. Source selection, B. Telegram persistence, C. Eligibility and metadata, D. Initial and incremental sync, E. Unified library UX, F. Automated verification, Implementation checklist (+4 more)

### Community 104 - "MorphingPlayerOverlay.kt"
Cohesion: 0.05
Nodes (53): abs, animate, animatedcontent, animatedvisibility, PlaybackState, animatedSeparatorHalfGap(), ArtworkBackdropTransition, ArtworkCropRole (+45 more)

### Community 105 - "TdLibTelegramForwardRepository"
Cohesion: 0.18
Nodes (8): DeliveryResult, DeliveryUncertainException, Failure, Exception, TdApi, PreparedLocalAudio, Success, TdLibTelegramForwardRepository

### Community 106 - "Increment 3 — Queue, Repeat, and True Pure Shuffle"
Cohesion: 0.17
Nodes (11): A. First-class queue, Acceptance verification — 2026-09-17, B. Repeat and Pure Shuffle, C. Persistence and recovery, D. Verification, Implementation checklist, Increment 3 — Queue, Repeat, and True Pure Shuffle, Non-goals (+3 more)

### Community 107 - "PlaybackServiceTest"
Cohesion: 0.11
Nodes (18): MediaController, MediaItem, T, PlaybackServiceTest, ComponentActivity, MainActivity, Context, PlaybackWidgetProvider (+10 more)

### Community 108 - "tdapi"
Cohesion: 0.16
Nodes (9): bestTitle(), cleanOrNull(), isSupportedAudioDocument(), TelegramAudioCandidate, toAudioCandidate(), TdLibAuthorizationStateMapperTest, TelegramAudioMessageMapperTest, assertsame (+1 more)

### Community 110 - "LibraryViewModel.kt"
Cohesion: 0.10
Nodes (25): bindMusicLibraryRepository(), Flow, LibraryTrack, LocalLibraryRefreshResult, MusicLibraryRepository, HomeUiState, HomeViewModel, StateFlow (+17 more)

### Community 112 - "AudioPermissionStatus"
Cohesion: 0.20
Nodes (8): AudioPermission, AudioPermissionStatus, DENIED, GRANTED, REQUIRED, LibraryRouteV2(), LibraryRouteV3(), manifest

### Community 113 - "PersonalizationDao"
Cohesion: 0.24
Nodes (3): PersonalizationDao, AudioFeatureEntity, PersonalizationModelEntity

### Community 114 - "TdLibDownloadRepository"
Cohesion: 0.20
Nodes (3): Flow, LocalCopy, TdLibDownloadRepository

### Community 115 - "RoomListeningHistoryRepository"
Cohesion: 0.33
Nodes (3): ActivePlayback, RoomListeningHistoryRepository, Session

### Community 116 - "CurlyMusicSlider.kt"
Cohesion: 0.08
Nodes (40): animatable, animatefloatasstate, AudioSpectrum(), Color, FloatArray, Modifier, SpectrumCanvas(), CurlyMusicSlider() (+32 more)

### Community 117 - "PlaylistDao"
Cohesion: 0.12
Nodes (5): Flow, PlaylistDao, PlaylistSummaryRow, PlaylistEntity, PlaylistTrackEntity

### Community 119 - "TelegramChatKind"
Cohesion: 0.33
Nodes (6): TelegramChatKind, BASIC_GROUP, PRIVATE, SAVED_MESSAGES, SECRET, SUPERGROUP_OR_CHANNEL

### Community 121 - "Increment 6 — Unified Track Model and Deduplication"
Cohesion: 0.40
Nodes (4): Implementation, Increment 6 — Unified Track Model and Deduplication, Status, Verification

### Community 122 - "Increment 7 — Offline Download Manager"
Cohesion: 0.40
Nodes (4): Implementation, Increment 7 — Offline Download Manager, Status, Verification

### Community 123 - "Increment 8 — Telegram On-Demand Playback and Prefetch"
Cohesion: 0.33
Nodes (5): Implementation, Increment 8 — Telegram On-Demand Playback and Prefetch, Status, Telegram refresh discipline, Verification

### Community 124 - "Increment 9 — Library UX, Search, Favorites, and Playlists"
Cohesion: 0.40
Nodes (4): Implementation, Increment 9 — Library UX, Search, Favorites, and Playlists, Status, Verification

### Community 127 - "uuid"
Cohesion: 0.16
Nodes (13): ListeningEventSemantics, PlaybackInitiator, PURE_SHUFFLE, QUEUE, SMART_SHUFFLE, SYSTEM_RESUME, USER, TrackPreferenceStats (+5 more)

### Community 128 - "TelegramDao"
Cohesion: 0.16
Nodes (3): TelegramDao, TelegramSelectedSourceEntity, TelegramTrackSourceEntity

### Community 131 - "TelegramSendDao"
Cohesion: 0.15
Nodes (3): Flow, TelegramSendDao, TelegramSendJobEntity

### Community 132 - "RoomListeningHistoryRepositoryTest"
Cohesion: 0.25
Nodes (4): AudioFeatureExtractor, AudioFeatureVector, NoOpAudioFeatureExtractor, RoomListeningHistoryRepositoryTest

### Community 135 - "TdLibRemoteTrackPlaybackResolver"
Cohesion: 0.32
Nodes (3): Job, ResolvedTelegramSource, TdLibRemoteTrackPlaybackResolver

### Community 136 - "Increment 10 — Listening History and Local Behavioral Data"
Cohesion: 0.40
Nodes (4): Implementation, Increment 10 — Listening History and Local Behavioral Data, Status, Verification

### Community 137 - "RoomPlaylistRepository"
Cohesion: 0.23
Nodes (3): bindPlaylistRepository(), Flow, RoomPlaylistRepository

### Community 139 - "LibraryScreenV3"
Cohesion: 0.20
Nodes (15): LibraryDisplaySettings, Album, Artist, detailTitleV3(), DisplaySheetV3(), Favorites, groupLabelV3(), GroupListV3() (+7 more)

### Community 141 - "DataStoreSettingsRepository.kt"
Cohesion: 0.15
Nodes (6): bindSettingsRepository(), DataStoreSettingsRepository, androidx, Flow, booleanpreferenceskey, emptypreferences

### Community 142 - ".newCycle"
Cohesion: 0.21
Nodes (6): T, PureShuffleEngine, ShuffleCycle, PureShuffleEngineTest, random, securerandom

### Community 143 - "LocalLinearPersonalizationModel"
Cohesion: 0.31
Nodes (4): DoubleArray, PersonalizationModel, TrainingSample, LocalLinearPersonalizationModel

### Community 144 - "TelegramSendState"
Cohesion: 0.15
Nodes (13): TelegramSendState, CANCELED, CHECKING, FAILED, PREPARING_FILE, QUEUED, RETRYING, SENDING (+5 more)

### Community 145 - "RecommendationEngine"
Cohesion: 0.21
Nodes (7): bindRecommendationEngine(), HeuristicRecommendationEngine, DoubleArray, ByteArray, DoubleArray, RecommendationEngine, SmartQueue

### Community 146 - ".generate"
Cohesion: 0.30
Nodes (4): DoubleArray, RecommendationContext, TrackPersonalizationFeatures, LinearRankerMathTest

### Community 148 - "TelegramSendQueueWorker.kt"
Cohesion: 0.20
Nodes (9): TelegramSendQueueWorker, TelegramSendWorkerEntryPoint, CoroutineWorker, entrypoint, entrypointaccessors, installin, Result, singletoncomponent (+1 more)

### Community 149 - "PersonalizationModel.kt"
Cohesion: 0.21
Nodes (7): PersonalizationFeatureVectorizer, PersonalizationModel, PersonalizationModelState, TrainingSample, cos, pi, sin

### Community 150 - "LibraryGroupMode"
Cohesion: 0.18
Nodes (7): LibraryGroupMode, ALBUM, ARTIST, NONE, YEAR, ViewModel, LibraryDisplayViewModel

### Community 152 - "QueueActionFeedback.kt"
Cohesion: 0.29
Nodes (8): QueueActionFeedback, QueueActionFeedbackBus, QueueActionKind, ADD_TO_END, PLAY_NEXT, assharedflow, mutablesharedflow, SharedFlow

### Community 153 - "SettingsRepository"
Cohesion: 0.24
Nodes (3): Flow, SettingsRepository, TelegramForwardSettings

### Community 154 - "PlaylistGroupBy"
Cohesion: 0.20
Nodes (10): PlaylistGroupBy, ALBUM, ARTIST, NONE, YEAR, PlaylistOrganizationControlsV3(), PlaylistTrackSort, CUSTOM (+2 more)

### Community 155 - "NoOpPersonalizationModel"
Cohesion: 0.25
Nodes (4): DoubleArray, PersonalizationModel, TrainingSample, NoOpPersonalizationModel

### Community 156 - "NetworkPlaybackSettings"
Cohesion: 0.44
Nodes (3): NetworkPolicyRules, NetworkPlaybackSettings, NetworkPolicyRulesTest

### Community 157 - ".sample"
Cohesion: 0.47
Nodes (3): DoubleArray, TrainingSample, TrainingDatasetBuilder

### Community 159 - "QueueViewModel.kt"
Cohesion: 0.13
Nodes (7): EmptyQueue(), QueueScreen(), StateFlow, ViewModel, QueueAuxState, QueueViewModel, asstateflow

### Community 160 - "PagedLibraryTracks.kt"
Cohesion: 0.32
Nodes (5): Flow, PagedLibraryTracks, pager, pagingconfig, PagingData

### Community 161 - "LibrarySortMode"
Cohesion: 0.25
Nodes (6): LibrarySortMode, ARTIST_ASC, OLDEST_ADDED, RECENTLY_ADDED, TITLE_ASC, TITLE_DESC

### Community 162 - ".preferenceScore"
Cohesion: 0.39
Nodes (4): DoubleArray, TrainingSample, LinearRankerMath, tanh

### Community 163 - "HistoryViewModel"
Cohesion: 0.25
Nodes (3): HistoryRow, HistoryViewModel, ViewModel

### Community 164 - "TelegramForwardOptions"
Cohesion: 0.29
Nodes (5): AlreadyInChat, AlreadyQueued, Queued, TelegramForwardOptions, TelegramSendEnqueueResult

### Community 166 - "calculateChatDownloadProgress"
Cohesion: 0.53
Nodes (3): calculateChatDownloadProgress(), ChatDownloadProgress, ChatDownloadProgressTest

### Community 167 - ".observeEvents"
Cohesion: 0.40
Nodes (3): Flow, ListeningEvent, 7.12 TrainingSample

### Community 169 - "LibrarySectionV2"
Cohesion: 0.40
Nodes (5): LibrarySectionV2, ALBUMS, ARTISTS, PLAYLISTS, TRACKS

### Community 170 - "LibrarySectionV3"
Cohesion: 0.40
Nodes (5): LibrarySectionV3, ALBUMS, ARTISTS, PLAYLISTS, TRACKS

### Community 173 - "AvailabilityV3"
Cohesion: 0.50
Nodes (4): AvailabilityV3, ALL, CLOUD, OFFLINE

### Community 174 - "flattenGroupedTracksV3"
Cohesion: 0.83
Nodes (4): flattenGroupedTracksV3(), Header, Song, TrackListItemV3

### Community 175 - "ArtworkGestureAxis"
Cohesion: 0.50
Nodes (4): ArtworkGestureAxis, HORIZONTAL, UNDECIDED, VERTICAL

### Community 177 - "PlaylistSortMode"
Cohesion: 0.67
Nodes (3): PlaylistSortMode, ARTIST, PLAYLIST_ORDER

### Community 178 - "PlaylistListV3"
Cohesion: 0.67
Nodes (3): androidx, PlaylistListV3(), PlaylistRowV3()

## Knowledge Gaps
- **453 isolated node(s):** `LOCAL_MEDIASTORE`, `TELEGRAM_REMOTE`, `TDLIB_LOCAL`, `APP_OFFLINE_COPY`, `AVAILABLE_LOCAL` (+448 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 895 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **44 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Track` connect `Track` to `LibraryRouteV3.kt`, `RoomPlaylistRepository`, `TrackArtwork.kt`, `MEOWZIX_SYSTEM_SPEC.md`, `TdLibTelegramRepository.kt`, `LocalMusicLibraryRepository`, `OfflineDownload`, `QueueViewModel.kt`, `PagedLibraryTracks.kt`, `Implementation checklist`, `4. Source-Agnostic Training and Data Provenance`, `LibraryRouteV2.kt`, `flattenGroupedTracksV3`, `MeowzixApp.kt`, `3. Core Product Decisions`, `PlaylistRepository`, `9. Local Music Discovery`, `LibraryViewModel.kt`, `uuid`?**
  _High betweenness centrality (0.155) - this node is a cross-community bridge._
- **Why does `27. Database Constraints and Invariants` connect `3. Core Product Decisions` to `MEOWZIX_SYSTEM_SPEC.md`, `Track`?**
  _High betweenness centrality (0.058) - this node is a cross-community bridge._
- **Why does `6. Repository / Module Structure` connect `MEOWZIX_SYSTEM_SPEC.md` to `Track`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `Track` (e.g. with `B. Canonical persistence model` and `27. Database Constraints and Invariants`) actually correct?**
  _`Track` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 12 inferred relationships involving `ProgressiveQueue` (e.g. with `.`tail append near exhausted window is exposed by normal forward refill`()` and `.`tail append outside active window stays logical until refill is needed`()`) actually correct?**
  _`ProgressiveQueue` has 12 INFERRED edges - model-reasoned connections that need verification._
- **What connects `LOCAL_MEDIASTORE`, `TELEGRAM_REMOTE`, `TDLIB_LOCAL` to the rest of the system?**
  _453 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `DataModule.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.08666666666666667 - nodes in this community are weakly interconnected._