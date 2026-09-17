# Meowzix UI/UX — Material 3 Expressive Redesign Backlog

Status: active design and implementation plan  
Target branch: `ui`  
Scope baseline: current product implementation through Increment 4  
Design language: Material 3 Expressive  
Brand palette: calico-inspired black, white, warm gray, and orange

## 1. Design direction

Meowzix should feel playful, tactile, musical, and unmistakably Android-native without becoming visually noisy.

The redesign should use Material 3 Expressive as the system foundation rather than merely copying its appearance. That means the app should use:

- `MaterialExpressiveTheme`.
- expressive motion via `MotionScheme.expressive()`.
- large, varied, high-character shapes.
- prominent typography hierarchy.
- large touch targets and icon-first controls.
- responsive component transformations instead of static rectangular UI.
- clear tonal surfaces and high contrast.
- motion that communicates state and hierarchy.
- accessible alternatives for every gesture.

The calico palette should be recognizable without making every component orange. Orange is the accent; black, white, charcoal, graphite, stone, and warm off-white are the visual foundation.

## 2. Brand color system

### Core colors

- Calico Orange — primary expressive accent.
- Burnt Orange — pressed/strong accent states.
- Warm Cream — light-theme background.
- Pure White — elevated/high-priority surfaces.
- Ink Black — dark-theme background and primary text.
- Charcoal — dark containers.
- Warm Gray — secondary containers and separators.
- Soft Gray — low-emphasis surfaces.

### Rules

- Orange should emphasize primary actions, active playback, selected tabs, and key progress states.
- Avoid orange-on-orange low-contrast combinations.
- Do not use arbitrary colors for common states when Material semantic roles exist.
- Error, warning, success, and info states retain semantic differentiation.
- The default Meowzix appearance should preserve the brand palette instead of replacing it with wallpaper dynamic colors.
- Dynamic color may be an optional future setting.

## 3. Global design-system features

- [ ] Material 3 Expressive theme root.
- [ ] Expressive motion scheme.
- [ ] Complete branded light color scheme.
- [ ] Complete branded dark color scheme.
- [ ] Custom expressive typography scale.
- [ ] Custom expressive shape scale.
- [ ] Edge-to-edge system bars.
- [ ] Transparent/tonal system navigation handling.
- [ ] Predictive back support where navigation supports it.
- [ ] Shared spacing tokens.
- [ ] Shared elevation/tone rules.
- [ ] Shared icon sizing rules.
- [ ] Shared minimum touch target rules.
- [ ] Reduced-motion compatibility.
- [ ] High-contrast accessibility review.
- [ ] TalkBack descriptions for icon-only actions.
- [ ] Large-font resilience.
- [ ] RTL-safe layout behavior.
- [ ] Consistent loading, empty, error, and permission states.

## 4. App shell and navigation

- [ ] Persistent expressive bottom navigation for top-level destinations.
- [ ] Library destination.
- [ ] Queue destination.
- [ ] Telegram destination.
- [ ] Settings destination when settings are implemented.
- [ ] Selected destination represented by tonal pill/shape treatment.
- [ ] Compact mini-player above bottom navigation whenever a track exists.
- [ ] Mini-player artwork, title, artist, play/pause, and progress.
- [ ] Tap mini-player to open Now Playing.
- [ ] Swipe mini-player horizontally for previous/next as an optional shortcut.
- [ ] Bottom navigation automatically hides or transforms on immersive Now Playing.
- [ ] Correct back-stack preservation between top-level tabs.
- [ ] Deep-link-ready route definitions.
- [ ] Smooth shared transition from mini-player to Now Playing when platform/API support is appropriate.

## 5. Library — top-level experience

- [ ] Expressive large header with Meowzix identity.
- [ ] Search affordance integrated into header.
- [ ] Expandable search field.
- [ ] Search title, artist, and album locally.
- [ ] Pull-to-refresh/rescan gesture.
- [ ] Explicit rescan action as accessible fallback.
- [ ] Library count and lightweight status information.
- [ ] Permission state shown as a branded card rather than a generic text screen.
- [ ] Empty-state illustration/icon treatment.
- [ ] Error card with retry action.
- [ ] Refresh progress integrated without blocking the entire screen.

## 6. Library tabs

Tabs should only expose data the current product can truthfully support. Additional tabs become active as later increments land.

### Available from current Track data

- [ ] Tracks.
- [ ] Artists.
- [ ] Albums.

### Activate in later increments

- [ ] Playlists.
- [ ] Favorites.
- [ ] Recently Played.
- [ ] Downloads / Offline.
- [ ] Smart Mixes.

### Tab interaction

- [ ] Swipe horizontally between tabs.
- [ ] Tap tab labels/icons.
- [ ] Remember selected tab.
- [ ] Preserve scroll position per tab.
- [ ] Animate indicator and content using expressive motion.

## 7. Track rows and gestures

- [ ] Larger artwork with expressive rounded shape.
- [ ] Clear title/artist hierarchy.
- [ ] Duration rendered with low visual priority.
- [ ] Overflow menu instead of permanent text buttons.
- [ ] Swipe right: Play Next.
- [ ] Swipe left: Add to Queue.
- [ ] Gesture confirmation feedback.
- [ ] Haptic feedback for completed swipe action where appropriate.
- [ ] Tap row: Play Now.
- [ ] Long press: open contextual action sheet.
- [ ] Animated currently-playing indicator.
- [ ] Selected/current-track tonal surface.
- [ ] Cloud/offline/downloading/unavailable badges when source features exist.
- [ ] Favorite indicator when favorites exist.
- [ ] Explicit menu fallback for every swipe action.

## 8. Artist view

- [ ] Artist list generated from canonical Track metadata.
- [ ] Track count per artist.
- [ ] Artist detail screen.
- [ ] Play artist.
- [ ] Pure Shuffle artist.
- [ ] Smart Shuffle artist when Smart mode exists.
- [ ] Artist track list.
- [ ] Artist album grouping.
- [ ] Unknown Artist grouping handled cleanly.

## 9. Album view

- [ ] Album grid/list generated from canonical Track metadata.
- [ ] Artwork-first album cards.
- [ ] Artist + year metadata when available.
- [ ] Album detail screen.
- [ ] Ordered album playback.
- [ ] Pure Shuffle album.
- [ ] Add album to queue.
- [ ] Unknown Album grouping handled cleanly.

## 10. Now Playing — hero screen

- [ ] Immersive expressive composition.
- [ ] Large album artwork.
- [ ] Artwork shape adapts/morphs subtly with player state where practical.
- [ ] Track title and artist hierarchy.
- [ ] Favorite action when implemented.
- [ ] More-actions menu.
- [ ] Expressive seek/progress control.
- [ ] Elapsed and remaining time.
- [ ] Large central play/pause control.
- [ ] Previous and next controls.
- [ ] Pure Shuffle control.
- [ ] Smart Shuffle control when implemented.
- [ ] Repeat control.
- [ ] Queue shortcut.
- [ ] Offline/download shortcut when implemented.
- [ ] Playback status and recoverable error UI.
- [ ] Buffering/preparing indicator integrated into playback control.
- [ ] Queue position indicator.

### Now Playing gestures

- [ ] Swipe artwork left: next track.
- [ ] Swipe artwork right: previous track.
- [ ] Swipe down: return to previous surface when navigation behavior is unambiguous.
- [ ] Drag seek control normally.
- [ ] Long press previous/next reserved for future seek behavior only if platform-consistent.
- [ ] Gesture alternatives remain visible as buttons.

## 11. Queue

- [ ] Modern queue header.
- [ ] Current playback mode shown as a chip.
- [ ] Repeat mode shown as a chip.
- [ ] Current item highlighted.
- [ ] Artwork in queue rows.
- [ ] Drag handle and drag-to-reorder.
- [ ] Swipe to remove.
- [ ] Undo snackbar after removal.
- [ ] Clear queue confirmation.
- [ ] Jump to currently playing item.
- [ ] Save queue as playlist when playlists exist.
- [ ] Queue empty state.
- [ ] Accessible move-up/move-down fallback for drag behavior.

## 12. Telegram connection experience

- [ ] Branded Telegram integration landing surface.
- [ ] Step-specific expressive iconography.
- [ ] Phone input with clear country-code guidance.
- [ ] Code entry optimized for numeric/security flow.
- [ ] 2FA password state.
- [ ] Email state.
- [ ] Registration state.
- [ ] Other-device confirmation state.
- [ ] Connected account card.
- [ ] Logout action with confirmation.
- [ ] Error message cards instead of floating raw text.
- [ ] Configuration-required developer state.
- [ ] Loading indicator that explains what is happening.
- [ ] Sensitive fields maintain secure visual transformation.

## 13. Telegram source selection — later Increment 5 UI

- [ ] Saved Messages card.
- [ ] Private channels.
- [ ] Private groups.
- [ ] Search chats.
- [ ] Selected-source chips.
- [ ] Sync status per source.
- [ ] Last sync time.
- [ ] Import progress.
- [ ] Source empty/error states.
- [ ] Enable/disable source without deleting indexed metadata immediately.

## 14. Downloads and cloud state — later Increments 7–8 UI

- [ ] Cloud badge.
- [ ] Offline badge.
- [ ] Downloading badge/progress.
- [ ] Unavailable badge.
- [ ] Pin Offline action.
- [ ] Remove offline copy action.
- [ ] Global Downloads screen.
- [ ] Active downloads section.
- [ ] Completed offline section.
- [ ] Failed downloads section.
- [ ] Retry/cancel controls.
- [ ] Storage usage card.
- [ ] Cache size visualization.
- [ ] Clear cache action.
- [ ] Wi-Fi-only indicator.
- [ ] Download-on-play preparing state.

## 15. Search — later full-library version

- [ ] Instant local search.
- [ ] Search title.
- [ ] Search artist.
- [ ] Search album.
- [ ] Search filename as lower-priority metadata.
- [ ] Recent searches.
- [ ] Clear search history.
- [ ] Search result sections.
- [ ] Search actions: Play, Play Next, Add to Queue.
- [ ] Search filters: local/cloud/offline when source data exists.
- [ ] Search keyboard actions.
- [ ] Empty query suggestions.

## 16. Playlists — later Increment 9 UI

- [ ] Playlist collection.
- [ ] Create playlist sheet.
- [ ] Rename playlist.
- [ ] Delete playlist confirmation.
- [ ] Add track to playlist sheet.
- [ ] Multi-select tracks.
- [ ] Reorder playlist tracks.
- [ ] Swipe remove with undo.
- [ ] Playlist cover collage.
- [ ] Play in order.
- [ ] Pure Shuffle.
- [ ] Smart Shuffle.
- [ ] Save current queue as playlist.

## 17. Favorites and history — later Increments 9–10 UI

- [ ] Favorite toggle in track rows/menu.
- [ ] Favorite toggle in Now Playing.
- [ ] Favorites collection.
- [ ] Recently Played collection.
- [ ] Listening-history timeline.
- [ ] Clear-history confirmation.
- [ ] Privacy explanation.
- [ ] History disabled state.

## 18. Smart Shuffle — later Increments 11–12 UI

- [ ] Separate Smart Shuffle identity; never reuse ordinary shuffle semantics.
- [ ] Smart mode icon/label distinct from Pure Shuffle.
- [ ] Smart mode quick start from library/artist/album/playlist.
- [ ] Optional "Why this track?" sheet.
- [ ] Lightweight learning-state explanation.
- [ ] Cold-start explanation without technical jargon.
- [ ] Reset personalization action.
- [ ] Disable Smart Shuffle action.
- [ ] Exploration preference control if exposed.
- [ ] Model rebuild/reset developer diagnostics kept out of normal UI.

## 19. Settings architecture

### Appearance

- [ ] System / Light / Dark.
- [ ] Meowzix branded palette default.
- [ ] Optional dynamic color.
- [ ] Reduced motion / follow system.

### Playback

- [ ] Resume previous queue.
- [ ] Default playback mode.
- [ ] Default repeat behavior.
- [ ] Future crossfade/gapless controls only when implemented.

### Telegram

- [ ] Connected account.
- [ ] Source chats.
- [ ] Sync now.
- [ ] Wi-Fi-only cloud download.
- [ ] Disconnect.

### Offline

- [ ] Cache size limit.
- [ ] Clear temporary cache.
- [ ] Offline downloads.
- [ ] Prefetch toggle.
- [ ] Metered-network behavior.

### Privacy

- [ ] Listening history toggle.
- [ ] Smart Shuffle toggle.
- [ ] Clear history.
- [ ] Reset personalization.
- [ ] Diagnostics opt-in only if diagnostics ever exist.

### About

- [ ] Version.
- [ ] Open-source licenses.
- [ ] GitHub repository.
- [ ] Privacy policy when release-ready.

## 20. Sheets, menus, and feedback

- [ ] Expressive modal bottom sheets for track actions.
- [ ] Menus use icon + concise label.
- [ ] Snackbar undo for reversible destructive actions.
- [ ] Confirmation dialogs only for meaningful irreversible actions.
- [ ] Haptics for completed gestures and major player transitions where appropriate.
- [ ] Loading feedback should not block unrelated playback controls.

## 21. Motion

- [ ] Expressive Material motion scheme globally.
- [ ] Animated content transitions between empty/loading/content states.
- [ ] Animated selected tab shape.
- [ ] Play/pause icon transition.
- [ ] Mini-player enter/exit animation.
- [ ] Now Playing artwork/content transition.
- [ ] Swipe action shape/color morphing.
- [ ] Queue reorder motion.
- [ ] Reduce or disable nonessential motion when system requests reduced motion.

## 22. Icons

Use a consistent Material icon family and avoid text-only controls when a well-known icon is clearer.

Required icon concepts include:

- play / pause;
- previous / next;
- shuffle;
- smart shuffle;
- repeat / repeat one;
- queue music;
- playlist add;
- library music;
- person/artist;
- album;
- search;
- refresh;
- more vertical;
- favorite;
- download;
- cloud;
- offline;
- Telegram/link account;
- settings;
- back;
- close;
- delete/remove;
- drag handle;
- error/info/check.

Every icon-only control must have a content description unless it is purely decorative.

## 23. Accessibility and ergonomics

- [ ] 48dp minimum touch targets unless Material component handles it automatically.
- [ ] Contrast checked in both themes.
- [ ] Orange not used as the sole carrier of state.
- [ ] Screen-reader descriptions for playback state and gestures.
- [ ] Do not require gestures for any critical action.
- [ ] Text remains usable at large font scales.
- [ ] Important controls reachable one-handed on common phone sizes.
- [ ] Landscape behavior remains usable.
- [ ] Tablet layouts can expand into two-pane library/player later without domain rewrites.

## 24. Responsive layouts

### Phone compact

- bottom navigation;
- single-pane library;
- full-screen Now Playing;
- modal/sheet contextual actions.

### Phone landscape / medium width

- wider artwork/content balance;
- avoid excessively stretched lists;
- optional navigation rail when width is sufficient.

### Tablet / foldable future-ready

- navigation rail;
- two-pane library + Now Playing/queue;
- adaptive grid for albums/artists;
- hinge/fold-safe layout.

## 25. Visual assets and identity

- [ ] Final Meowzix app icon.
- [ ] Adaptive Android icon.
- [ ] Monochrome Android icon.
- [ ] Splash screen treatment.
- [ ] Calico-inspired subtle brand motif.
- [ ] Default artwork placeholder that looks intentional.
- [ ] Avoid literal cat graphics everywhere; keep the identity sophisticated.

## 26. Immediate implementation sequence on `ui`

The following sequence is designed to keep every commit independently understandable and keep the app buildable as much as possible:

1. **Design backlog and UI contract** — this document.
2. **Expressive design system** — Material 3 Expressive dependency/theme, colors, shapes, typography, edge-to-edge foundation.
3. **Expressive app shell** — bottom navigation, icon system, mini-player foundation, top-level navigation behavior.
4. **Library redesign** — search, Tracks/Artists/Albums tabs, expressive rows/cards, rescan interaction.
5. **Track gestures** — swipe Play Next / Add to Queue with explicit action fallbacks.
6. **Now Playing redesign** — artwork-led hero layout, icon controls, expressive mode controls, artwork swipe previous/next.
7. **Queue redesign** — artwork rows, current-track state, swipe/remove/reorder affordances using current queue capabilities.
8. **Telegram auth redesign** — expressive authentication flow and connected/error/progress states.
9. **Accessibility and polish pass** — content descriptions, touch targets, dark/light contrast, motion and layout cleanup.
10. **Build/test pass** — compile/test fixes and final documentation of what is implemented versus deferred.

## 27. Scope rule

The UI branch must not fake product capabilities that the current domain layer does not have.

For example:

- Artists and Albums can be derived from the current `Track` model and are valid now.
- Playlists, Favorites, Downloads, Recently Played, and Smart Shuffle should be designed in this backlog but only made interactive when their corresponding domain increments exist.
- Pure Shuffle must retain its exact non-personalized semantics.
- Telegram authentication may be redesigned now, while Telegram source-selection UI waits for Increment 5.

This keeps the redesign ambitious without turning the UI into a collection of non-functional placeholders.
