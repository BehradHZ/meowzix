# Search dock and playback context

## Search presentation
- The Library/Queue contextual search input remains a single dock-hosted control, never an item in the scrollable list.
- The dock and global-search results share the same bottom layout owner and use `WindowInsets.ime.union(WindowInsets.navigationBars)`. Do not branch on text-field focus to position the dock. The activity uses `adjustResize`.
- During active keyboard input, the collapsed Mini Player is not rendered so it cannot cover the input or matches; it reappears when the keyboard is dismissed. Global search results similarly take priority over the collapsed player.
- Closing search restores the normal navigation dock; selecting a Library track closes search before opening Now Playing.

## Search versus playback context
- Existing normalized, token-based **contains** search remains unchanged. Search controls only visible rows.
- Tapping a track in Library search builds playback from unfiltered Library track IDs, with current sort/group and explicitly selected Offline/Cloud availability filter respected. Global search uses the full unified Library (ALL availability), not the returned search-result page.
- The Library playback mode/repeat policy is respected. For `ORDERED` search playback, the queue is cyclically rotated to place the clicked track first, preserving all tracks. Pure/Smart Shuffle receive the complete eligible candidate set and handle ordering themselves.
- Normal non-search Library selection behavior is intentionally unchanged; playlist, artist, album, and queue-specific selections retain their own playback contexts. Search query changes after tapping do not mutate the queue.
- IDs are materialized only on a play request, not for each keystroke.

## Manual device regression checklist
1. Open Library Search, type a query with a few matches, dismiss/reopen IME, clear query, and exit. Dock must remain anchored above IME/navigation bar and not jump during focus changes.
2. Scroll Library while typing; search dock remains stationary, the list scrolls behind it, and Mini Player does not overlap the keyboard.
3. Play a Library search result near the end of a sorted 1000+ track collection: Ordered queue contains the entire active Library selection with chosen track first and wraps through the remaining tracks.
4. Play the same result in Pure and Smart Shuffle: candidate pool is not constrained by the query. Repeat semantics are preserved.
5. Check Search from Home, search with and without existing Mini Player, and verify global results do not overlap the player.
6. Open Playlist and Queue search; they continue using their own context, not the full Library.
7. Toggle Offline/Cloud and Library sort, then search/play: only the query is removed from playback selection; explicit availability/sort settings remain effective.
