# Meowzix UI/UX — Glass Music Redesign Backlog

Status: active design and implementation plan  
Target branch: `ui`  
Scope baseline: current product implementation through Increment 4  
Design direction: Apple Music-inspired floating glass music interface, implemented natively for Android/Compose  
Brand accent: Meowzix orange over black, white, graphite, warm gray, and artwork-derived ambient color

## 1. Direction

The previous Material 3 Expressive visual direction is superseded by this document.

Meowzix should feel immersive, calm, musical, and content-first. Album artwork and audio activity are the visual center. Navigation and controls sit on a separate floating glass layer rather than looking like ordinary Android bars or cards.

The reference behavior is the modern Apple Music / Liquid Glass interaction model, not a pixel-for-pixel clone. Meowzix keeps its own branding, information architecture, Android behavior, and accessibility semantics.

Core principles:

- content extends edge-to-edge underneath navigation and player chrome;
- glass surfaces float above content and reveal/blur what is behind them;
- the bottom dock is detached from screen edges;
- the mini-player is a floating glass accessory above/attached to the dock;
- Now Playing is artwork-led and immersive;
- background ambience may derive from the current artwork;
- motion should be fluid and restrained rather than bouncy;
- every critical gesture has a visible control alternative;
- audio-reactive visuals must use real playback audio data, never random animation.

## 2. Glass system

- [ ] Shared Haze state for screen backdrop capture.
- [ ] Real backdrop blur where the Android/device renderer supports it.
- [ ] Graceful translucent fallback on devices where full blur/refraction is unavailable.
- [ ] Clear glass style for compact controls.
- [ ] Regular/frosted glass style for dock, mini-player, sheets, and large control surfaces.
- [ ] Thin bright rim/specular highlight.
- [ ] Soft dark edge shadow for separation on bright artwork.
- [ ] Press interaction that subtly compresses/scales the material.
- [ ] No opaque rectangular navigation bars.
- [ ] Avoid stacking multiple heavy blur layers when one shared surface is sufficient.

## 3. Color and ambience

- Meowzix orange remains the brand accent.
- Library surfaces remain mostly neutral: black, white, graphite, warm gray.
- Now Playing may use artwork-derived ambience behind the content.
- Glass tint adapts between light and dark content.
- Selected dock items may use a restrained orange highlight.
- Playback progress may use white/orange depending on contrast.
- Semantic error/success colors remain independent of the brand palette.

## 4. Global foundation

- [ ] Edge-to-edge system bars.
- [ ] Standard Material theme only as the Android component/semantics foundation; no Material Expressive visual language.
- [ ] Shared glass modifiers/components.
- [ ] Shared spacing and corner-radius tokens.
- [ ] Shared floating-surface elevation/shadow rules.
- [ ] Shared icon sizing.
- [ ] Reduced-motion support.
- [ ] High-contrast accessibility review.
- [ ] TalkBack descriptions for icon-only controls.
- [ ] Large-font resilience.
- [ ] RTL-safe layouts.
- [ ] Consistent loading, empty, permission, and error states.

## 5. Floating dock

Top-level destinations currently supported:

- Library
- Queue
- Telegram
- Settings when the product domain is implemented

Dock behavior:

- [ ] detached pill/capsule floating above the bottom safe area;
- [ ] glass/refraction effect over scrolling content;
- [ ] icon-first tabs;
- [ ] selected destination receives a soft highlighted capsule rather than a full opaque tab;
- [ ] optional labels when useful for accessibility and first-time discoverability;
- [ ] dock can compact while scrolling down and expand when the user reverses direction;
- [ ] dock hides/recedes on immersive Now Playing;
- [ ] correct state restoration per destination;
- [ ] tablet/foldable upgrade path to floating side rail.

## 6. Floating mini-player

- [ ] glass capsule above the dock;
- [ ] current artwork;
- [ ] title and artist;
- [ ] play/pause;
- [ ] next action when width permits;
- [ ] subtle progress track;
- [ ] tap opens Now Playing;
- [ ] swipe left/right changes track;
- [ ] compact state can merge visually with the dock;
- [ ] optional low-amplitude audio-reactive spectrum/glow once visualization permission is granted.

## 7. Library

- [ ] large clean title area;
- [ ] floating/glass search affordance;
- [ ] Tracks / Artists / Albums tabs;
- [ ] Playlists / Favorites / Recently Played / Downloads tabs activate only when their domains exist;
- [ ] content scrolls under floating chrome;
- [ ] artwork-led rows rather than container-heavy cards;
- [ ] current-playing state is visible without turning the row into an opaque block;
- [ ] swipe right: Play Next;
- [ ] swipe left: Add to Queue;
- [ ] overflow menu remains available for all gesture actions;
- [ ] search title, artist, and album locally;
- [ ] permission/error/empty states use centered glass panels;
- [ ] rescan action remains explicit and accessible.

## 8. Albums and artists

- [ ] artwork-forward album grid/list;
- [ ] artist list with track counts;
- [ ] artist detail screen;
- [ ] album detail screen;
- [ ] play in order;
- [ ] Pure Shuffle;
- [ ] queue actions;
- [ ] later Smart Shuffle actions;
- [ ] Unknown Artist / Unknown Album handled intentionally.

## 9. Now Playing

Now Playing should be the visual signature of Meowzix.

- [ ] full-screen artwork-derived ambient background;
- [ ] blurred/softened artwork field behind content;
- [ ] large crisp album cover centered in the upper/middle area;
- [ ] floating glass top controls for back, queue, and overflow;
- [ ] title and artist with strong hierarchy;
- [ ] thin playback scrubber;
- [ ] elapsed/remaining time;
- [ ] large previous / play-pause / next controls;
- [ ] Pure Shuffle and Repeat controls;
- [ ] queue shortcut;
- [ ] current queue position;
- [ ] buffering/error states integrated without replacing the entire screen;
- [ ] swipe artwork left/right for next/previous;
- [ ] swipe down to dismiss when navigation state permits;
- [ ] future lyrics layer can slide over the ambient background without changing playback architecture.

## 10. Real audio-reactive spectrum / “equalizer” visualization

The visualizer must react to the actual currently playing audio output.

Implementation contract:

- [ ] use the playback audio session ID from ExoPlayer;
- [ ] attach Android `Visualizer` to the non-zero Meowzix playback session;
- [ ] capture FFT frequency data rather than generating random bars;
- [ ] use the maximum practical capture rate exposed by the platform;
- [ ] use `SCALING_MODE_AS_PLAYED` so playback level affects the visualization;
- [ ] collapse FFT bins into visual frequency bands;
- [ ] normalize and lightly smooth magnitudes to avoid unusable jitter while preserving actual musical movement;
- [ ] release the platform Visualizer when playback/session is destroyed;
- [ ] recover when ExoPlayer creates a new audio session;
- [ ] no global-output capture; only Meowzix’s own playback session;
- [ ] expose spectrum data as a `StateFlow` so any Compose surface can render it.

UI:

- [ ] full-width live spectrum below artwork / around playback information;
- [ ] optional compact spectrum in the mini-player;
- [ ] rounded vertical bars or continuous waveform mode;
- [ ] bars use glass/white/orange based on contrast;
- [ ] paused state settles naturally instead of continuing fake movement;
- [ ] permission-denied state shows a static, intentional placeholder.

Android privacy behavior:

- Android’s platform `Visualizer` API requires `RECORD_AUDIO` permission even when attached to the app’s own audio session.
- Meowzix should ask for this only from the live-spectrum UI, explain why, and not imply that microphone audio is being used as the visualization source.
- Playback itself must remain fully functional without this permission.

## 11. Actual DSP equalizer controls — later audio feature

Keep this distinct from the moving spectrum visualization.

- [ ] Equalizer enable/disable;
- [ ] device-supported band count and center frequencies;
- [ ] per-band gain sliders;
- [ ] Flat preset;
- [ ] additional presets only when exposed reliably by the platform;
- [ ] reset action;
- [ ] attach to Meowzix playback audio session only;
- [ ] persist settings;
- [ ] visual spectrum continues to show output activity independently of slider state.

## 12. Queue

- [ ] floating glass header/actions;
- [ ] current item highlighted subtly;
- [ ] artwork in rows;
- [ ] swipe to remove;
- [ ] accessible move up/down actions;
- [ ] future drag-to-reorder;
- [ ] clear queue confirmation;
- [ ] mode and repeat indicators;
- [ ] later save queue as playlist.

## 13. Telegram

- [ ] glass landing/auth surfaces;
- [ ] phone/code/2FA/email/registration steps keep current TDLib behavior;
- [ ] configuration-required state remains developer-readable;
- [ ] connected account state becomes a clean floating card;
- [ ] error states do not obscure navigation;
- [ ] future chat/source selection uses floating sheets and search.

## 14. Future music features

### Search
- instant local search;
- title/artist/album/filename matching;
- recent searches;
- source filters when Telegram/cloud data exists.

### Playlists
- create/rename/delete;
- add/remove/reorder tracks;
- cover collage;
- ordered/Pure Shuffle/Smart Shuffle starts;
- save current queue.

### Favorites/history
- favorite toggle;
- Favorites collection;
- Recently Played;
- listening-history timeline;
- privacy/clear-history controls.

### Offline/downloads
- cloud/offline/downloading/unavailable states;
- pin offline;
- active/completed/failed downloads;
- storage usage;
- cache management;
- metered/Wi-Fi policies.

### Smart Shuffle
- separate from Pure Shuffle;
- distinct visual identity;
- “Why this track?” explanation;
- reset/disable personalization controls.

### Settings
- appearance;
- playback defaults;
- Telegram account/sources;
- offline/cache;
- privacy/history/personalization;
- about/licenses/version.

## 15. Accessibility and performance

- [ ] critical actions are never gesture-only;
- [ ] minimum practical touch targets;
- [ ] screen-reader playback state descriptions;
- [ ] no information encoded by translucency/color alone;
- [ ] glass tint remains readable over extreme bright/dark artwork;
- [ ] spectrum rendering avoids unnecessary allocations per frame;
- [ ] FFT processing happens outside Compose drawing;
- [ ] shared backdrop capture is preferred over multiple independent captures;
- [ ] test glass performance on API 26 fallback and modern Android devices;
- [ ] reduced-motion mode disables nonessential scale/morph effects while preserving playback feedback.

## 16. Immediate implementation sequence on `ui`

1. **Direction reset** — replace the previous Material Expressive backlog with this glass music contract.
2. **Glass foundation** — Haze-backed backdrop/glass dependency, standard theme, shared glass surfaces.
3. **Floating shell** — detached glass dock and glass mini-player over content.
4. **Immersive Now Playing** — artwork ambience, floating controls, Apple Music-inspired spacing/hierarchy.
5. **Real audio spectrum pipeline** — ExoPlayer session -> Android Visualizer -> FFT bands -> `StateFlow`.
6. **Spectrum UI + permission flow** — live spectrum in Now Playing and compact mini-player treatment.
7. **Library/Queue/Telegram glass polish** — remove remaining heavy Material-Expressive-looking containers.
8. **CI and performance pass** — unit tests + debug build, then tune blur/spectrum cost if needed.

Every meaningful implementation step should remain an independent commit on the `ui` branch.
