# Lowfin optimization audit — 0.10.23

Baseline: 0.10.21, then the user-requested compact-dialog release 0.10.22 installed first. Preserve UI design, playback behavior, plugin-driven completed-cache updates and account boundaries. No GitHub publication.

Reviewed application/account startup and worker separation; MainActivity cached/network loading, home updates and mixed grids; HomeAdapter/MediaAdapter binding and diffing; image keys, disk/decode/download queues and memory budgets; Library merge/index/transaction paths; Companion discovery/cache journal and outbox; launcher publication; ratings and Tracearr; player ticks, transport negotiation, next-episode resolution, local video cache and player chrome.

## Retained changes

- Home shelf refresh compares stable IDs, displayed/focus metadata and row labels. Identical presentation updates refresh action metadata without outer adapter notifications or restarting pending remote navigation. Changed labels, artwork, watched state and progress still follow normal updates. Pending adapter changes and initial entry retain the existing path. Attached card data is refreshed even when its view need not rebind.
- Artwork URL-to-SHA256 memoization is bounded to 256 entries per image loader. Exact disk keys and URL/tag/width/account distinctions remain unchanged. This saves repeated hashing in scrolling/binding and precache/view overlap; it does not add a persistent cache or media prefetch. Close releases the memo.
- Mixed-grid span-index and span-group caches use RecyclerView's existing adapter-change invalidation.
- Player time labels are rewritten only when text changes. End-time formatting is cached by minute, locale and timezone, including paused wall-clock advances. Elapsed formatting avoids Formatter allocation while preserving long durations and boundary padding. Timeline progress, live watchdog, segment checks and report intervals are untouched.

## Deliberately retained

- No larger bitmap/video/RAM buffers, next-episode preload, dependency upgrades or renderer changes.
- Disk hits, decoding and downloads already use separate workers; bitmap memory remains 6 MiB. No priority change that might trade navigation smoothness for delayed artwork without evidence.
- Database reads/writes already run primarily on workers with query indexes and transactional merges. No journal-mode/schema/connection-lifetime change without a demonstrated lock/migration issue. Outstanding account jobs make aggressive database-close changes risky.
- Plugin cache cursor/audit transactions, expired-history retention, channel-artwork exclusion and explicit manual baseline remain intact.
- Native playback reports, Companion outbox sequencing and Tracearr completeness logic retain their existing semantics. No timing shortcuts that could change completion or resume state.
- Launcher channel IDs, labels, notifications and program updates retain their behavior; provider-write optimizations need a separately measured comparison to avoid reintroducing launcher refresh bugs.

## Validation and limits

Tests cover unchanged shelf notifications with fresh action metadata, rapid pending navigation, changed row labels and watched state, bounded hash eviction and tag changes, identical player ticks, paused end-time progression, timezone changes and 12/100-hour durations. Existing paging, header visibility, rating geometry, focus and playback tests remain required, as do release lint and signature verification.

These changes remove deterministic work. No overall FPS/startup speedup is claimed without a controlled device comparison. The earlier short TV samples varied considerably with warmed artwork and selected cards; see frame-navigation-0.10.19.md and home-ratings-0.10.20.md.

## Additional requested dialog sizing

Shared dialogs now derive their width from title/message/list labels/actions with a 240 dp minimum, 480 dp maximum and screen-edge allowance. Measuring list labels inspects at most 64 adapter items without creating/binding rows or starting image requests. Custom text fields and sliders retain usable width. A bounded wrap-content viewport limits height to 82% of the logical screen (and available parent/keyboard constraints); native lists/messages and wrapped custom content scroll while action buttons remain outside. Long-list and 80-line custom-content tests verify height bounds, scroll reachability and button access. Short dialog height remains intrinsic. Existing transparency and remote focus are retained.

## Final dialog refinement — 0.10.24

The user requested centered text and tighter balanced bounds after the 0.10.23 installation. Dialog titles, body/list labels and action labels/groups are centered. Minimum width is 280 dp, maximum 480 dp (or available screen width); minimum content height is 112 dp and the overall height cap is 70% of the logical screen including the 12 dp decor inset. Smaller parent/keyboard constraints take precedence. Tests include numeric lists to prevent narrow, tall menus, text centering, bounded long lists/custom content and reachable buttons. The 0.10.23 hot-path optimizations remain included.
