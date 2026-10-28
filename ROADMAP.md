# Lowfin roadmap

Existing Android TV client architecture, remote-first navigation, scoped persistent artwork/metadata caches, optional integrations and low-end performance remain the baseline. GitHub publication stays paused until explicitly authorized.

## Current: Lowfin 1.2.6 / Companion 0.5.0

- Persistent server tag index with indexed request reads, background event updates, permission-scoped summaries and 24-hour reconciliation. Register a native scheduled task and show last/next-check status in the dashboard.
- HDR10/HDR10+/HLG/Dolby Vision content tag, controlled by the server switch and the existing device-local HDR playback option (default off). Keep episode ratings uncached.
- Preserve cache files and native frame scaling; automatically retry pending index values. Validated persistence, cancellation, concurrent changes, permissions, checkpoints, scheduling and HDR gating. 430 client and 76 Companion tests plus isolated Jellyfin integration passed. Corrected metadata/focus restoration race found by regressions. Release APK and plugin ZIP/checksums on Desktop; no installation/publication. [Details](docs/tag-index-and-hdr-1.2.6.md).

## Previous: Lowfin 1.2.5 / Companion 0.4.1

- Banner release year; episode-specific ratings without storing results in memory/disk caches. Preserve movie/series caches and provider/display choices.
- DVD strict-majority tags for mixed-quality series/seasons, implemented as scoped server count queries. Invalidate affected episode/season/series facts on journal delivery.
- Validated uncached direct/proxied episode requests, series-ID/season/episode fallback, wrong-series rejection, ties/unknowns and real Jellyfin metadata/count behavior. 425 full client tests plus 29 final targeted tests, 62 Companion tests, release lint/signature and isolated Jellyfin integration passed. APK/Companion ZIP on Desktop, no installation or GitHub publication. [Details](docs/episode-ratings-and-dvd-majority-1.2.5.md).

## Previous: Lowfin 1.2.4

- Align the discovery banner to shelf bounds; use a full-width rating footer with proportional icons and unchanged type size, and allow four description lines.
- Validate HD/4K layout and existing banner navigation. Build and copy APK; installation is explicitly deferred by the user.

## Previous: Lowfin 1.2.3 / Companion 0.4.0

- Optional DVD/5.1 facts in Companion, independent admin switches, compact authenticated batches with user access checks.
- Native frame-based overlays with per-TV Appearance switch, 4:3 crop safety and HD/4K scaling. Preserve cached bitmaps and existing image endpoints.
- Persist facts per account; invalidate on incremental metadata events and fence stale in-flight results. No broad library scan or JellyTag dependencies.
- Validated: 422 full client tests, 22 final targeted tests, 54 Companion tests, release lint, native HD/4K crop screenshots and isolated Jellyfin 12.2 integration including user/library access. Plugin ZIP and APK/checksums on Desktop; installation only .128 without launching. [Details](docs/cover-tags-1.2.3.md).

## Previous: Lowfin 1.2.2

- Add the existing MDBList vector to the shared rating badges across banner, genre/recommendation shelves and details. Preserve configured visibility and compact/normal fitting. No GitHub publication.

## Previous: Lowfin 1.2.1

- Add compact runtime, genres and rating badges to the native discovery banner; remove visible remote-control hints. Reuse existing provider/cache preferences and reserve layout space. Retain navigation and pause rating UI work offscreen.
- Completed: 415 tests, release lint/signature/shrinking checks, HD and 3840×2160 native layout verification, and real banner scores on .128. Installed/opened only .128; Desktop APK/checksum updated. No GitHub push. [Details](docs/banner-information-1.2.1.md).

## Previous: Lowfin 1.2.0

- Text-free startup logo animation, retaining both fresh-row gates and offline escape.
- Reduce database blob/index churn for unchanged refreshes, batch home-return and Companion resume metadata reads, use existence checks for incremental baselines, and avoid parsing home snapshots on empty cursor advances.
- Reduce filesystem calls during cover eviction without changing persistent cache behavior or image keys.
- Retain tested playback, buffering, reporting, navigation and plugin fallback. Completed: 411 client tests, release lint/signature/shrinking checks, text-free logo/home screenshot verification, installation and reopening only on .128. Desktop APK/checksum updated; GitHub publication remains paused. [Audit and limits](docs/optimization-1.2.0.md).

## Previous: Lowfin 1.1.3

- Native optional discovery banner integrated into existing home order, remote navigation, shared caches and animation settings. No WebView or alternate UI architecture.
- Bounded startup logo waits for freshly refreshed Continue Watching and Next Up and visible artwork. Offline fallback and manual dismissal preserve access.
- Verify full regressions and release shrinking/signature; install and open only .128. No GitHub publication.

## Previous: Lowfin 1.0.0

- Consolidate the existing client and optional Companion integration as a local 1.0 release. Keep account data, settings, cache formats and the signing identity.
- Final narrow optimizations: reuse focus shapes and avoid full cast-list serialization. Retain the 0.10.29 provider-write and nonblocking status improvements.
- Verify full client/Companion regressions and the schema-4-to-8 upgrade used by TVs still on 0.7.25. Install on .128/.166/.87 and start only .128, per user instruction.
- Continue diagnosis of intermittent input stalls; no guaranteed frame rate or complete freeze fix is claimed. GitHub publication remains paused.

## Companion implementation history — Smart Completion & Persistent Resume

### Initial larger client release

The next larger client release is **0.8.0** (version code 39), with optional Companion 0.1.0 integration. It updates the existing account-scoped library database from schema 4 to 5 without clearing metadata or settings. Existing signing identity is preserved. GitHub publication still requires an explicit Go.

### Implemented and verified locally

- Plugin rules, authenticated versioned API, atomic per-user/media persistence, lease/sequence ordering, manual fences and long-video fallback are implemented. 21 plugin tests pass, including a genuinely concurrent manual watched change during progress.
- Real isolated Jellyfin 12.2.0 testing passes for a twelve-hour fixture, independent resume, native session notifications, manual reset/card reconciliation, completion/default rules, authentication, global thresholds, meta.json and plugin artwork.
- Lowfin uses the existing reports executor and player for managed playback, durable retry/coalescing, explicit restart, pause/stop checkpoints and native fallback. Detail dialogs and Playback settings expose optional rules in English and German.
- Continue shelves, TV channels and begun-episode views use independent positions. Cached cards are reconciled in the same resume request, avoiding an individual HTTP request per card after manual changes.
- The complete plugin package is in `Schreibtisch/lowfin`, with local manifest, image, ZIP, checksums and installation notes. Client regression tests, release lint, shrinking and signing verification pass. Lowfin 0.8.0 was installed as an update and started on TV `.128`; version code 39 and a non-debuggable package were verified on-device.
- Production server installation and validation on `.111` remain pending; the isolated test server and client tests do not constitute a production deployment.

### Follow-up: 0.8.1 / Companion 0.1.1

- Companion is an explicit choice. Unconfigured media keep native Jellyfin playback and completion; no independent progress is recorded. A deliberately enabled matching long-video rule also counts as a choice. Existing selected rules remain shared per user/media across devices.
- Episode resume results are constrained by the selected library on the server and saved in separate scoped snapshots. Global cached membership is not used to add unrelated episodes; older plugins fall back to native scoped lists.
- Jellyfin home-library exclusions are respected without clearing positions. Explicit library/detail navigation remains resumable. Selecting a first rule does not resurrect obsolete positions left by the original automatic behavior.
- 26 plugin tests and 223 client regression tests pass; isolated Jellyfin 12.2 testing includes two library roots, two authenticated devices, native playback without rules and excluded-library visibility. The current client cache migrates to schema 6 without clearing existing metadata.
- Keep only the latest verified Lowfin APK on the user's desktop; remove older Lowfin APKs and matching checksums after the new artifact has passed checks. Other apps and repository history stay untouched.
- Release client 0.8.1 was installed and started on `.128`; on-device version code 40 and non-debuggable flags were verified. The matching plugin package is in `Schreibtisch/lowfin`. Server plugin deployment still requires the user to copy the current version and restart Jellyfin; no production server files were modified.

### Implemented: 0.9.0 / Companion 0.2.0 — incremental precache

- Jellyfin 12.2 exposes supported `ILibraryManager.ItemAdded`, `ItemUpdated` and `ItemRemoved` events. Use these to maintain a persistent, ordered change journal rather than repeatedly enumerating the complete library from every TV.
- Provide an authenticated, paginated changes API with an opaque cursor. Filter both metadata and deletion events by the current user's library permissions; handle permission and library-membership changes explicitly.
- While Lowfin is active, notify it that changes are available and fetch small batches through its existing metadata/artwork cache. Coalesce repeated edits, transfer only changed metadata and fetch images only when their tags change. Never download artwork on the Jellyfin event handler thread.
- Save each device's cursor only after its cache transaction succeeds. Catch up after app/server restarts; signal deletions and an expired journal cursor so a full reconciliation can recover safely.
- Keep the initial full precache, manual rebuild and native periodic fallback when the plugin is absent. With a healthy compatible plugin, replace the regular complete scan with incremental catch-up. Implemented in 0.9.0 with Companion 0.2.0. Library deletion, ambiguous visibility changes, structural changes and server restarts intentionally request a full safety reconciliation because previous access cannot be verified through these event arguments.

### Verified target and limits

- The configured server `http://192.168.1.111:8096/System/Info/Public` reports **12.2.0**. Implementation is pinned to Jellyfin.Controller 12.2.0 and .NET 10; source reviewed at `v12.2`, commit `ca0f16eb7c195f720a1493ed469a27c4657db0c6`.
- `IUserDataManager.UpdatePlayState` applies global thresholds internally. No supported per-item replacement/pre-save cancellation hook is exposed.
- `IUserDataManager.UserDataSaved` is synchronous, after persistence but before SessionManager propagates a completed video's state. Its save reason and the media/user are available; the originating playback session is **not**. A blanket listener cannot reliably distinguish concurrent clients and is rejected.
- Supported plugin surfaces: `BasePlugin`, service registration, authenticated ASP.NET controllers, `ISessionManager`, user-data events and session events. No server patch, reflection, database-schema modification or global threshold change.

### Implementation sequence

1. **Analysis and contract**: document API, auth, race model, failure behavior and compatibility limits.
2. **Optional server plugin**: durable per-user/media rule and resume records; decimal percentage, remaining time, never-auto and default policies; optional per-user long-video fallback.
3. **Serialized playback gateway**: Lowfin reports through Companion, which invokes normal Jellyfin session APIs with unchanged playback positions. Request-scoped correction restores the intended watched/resume state through supported user-data saves. Session leases, increasing sequence numbers, rule revisions and manual-action fences reject stale writes.
4. **Client detection and fallback**: cached capability detection, hidden controls when absent, normal Jellyfin playback fallback, separate resumable position and small durable client outbox for crash recovery.
5. **Existing UI integration**: remote-friendly detail-page policy controls, reset-progress action and optional settings under Playback. Default policy remains Jellyfin's behavior.
6. **Validation**: 12-hour videos, 99.5%, two-minute remaining threshold, unknown duration, crashes, resumed sessions, duplicate/out-of-order/old-session reports, concurrent devices, manual watched/unwatched/reset, absent plugin, persistence/restart, normal session notifications and existing Android tests.
7. **Artifacts and rollout**: produce reviewable plugin and client artifacts; publish/install the server plugin only with an explicitly authorized server-install route. Validate against the real server before declaring production compatibility.

### Compatibility decisions

- Companion's independent resume storage is authoritative only for participating Lowfin sessions. Other clients keep Jellyfin's normal completion behavior; manual actions from them act as fences against older Lowfin writes.
- Original playback DTOs still reach SessionManager. Third-party statistics (including Tracearr) retain their own completion criteria; Lowfin's custom threshold does not redefine Tracearr's watched threshold.
- Stop/start/progress errors must not blindly replay a non-idempotent standard start or stop after an ambiguous timeout. The Companion protocol makes retries idempotent; absence fallback is decided before a playback session is routed.
- No synthetic progress while the client is crashed. Jellyfin's automatic interpolation is stopped only for Companion-managed sessions; the client reports real progress at the existing ten-second cadence and on pause/stop.
- Initial implementation must fail safely if host compatibility checks or supported APIs differ. Residual third-party/manual concurrency limitations must be documented and tested rather than hidden behind a polling repair loop.

## Later Companion capabilities

- Completion-history API for precise Lowfin statistics, independent of native PlayCount.
- Shared watch-later and client preferences with explicit user scoping.
- Additional optional capabilities negotiated through the versioned discovery API.

## Existing work retained

- Native player remote controls, autoplay, Intro Skipper rules, stereo audio, adaptive RAM/disk caches.
- Tracearr Public API v2 integration and encrypted TXT key import.
- Projectivy channel identity fix, dynamic recommendations, user-chosen libraries.
- Monochrome UI with curated accent/background pools, settings focus preservation and API sections.

### Current rollout and startup checks

- Sender diagnosis on .128: 30,863 expected artwork references, exactly 54 missing; every missing image and media ID returned HTTP 404 and was absent from the current 66-channel Live-TV list. These were stale channel rows rather than missing current M3U logos. Complete list reconciliation now prunes obsolete rows; failed/partial fetches preserve them.
- Startup feedback concerns the filled home screen, not just Activity launch (which measured 1.75 s). Cached home shelves are loaded before recommendations, automatic full work is delayed to give visible cards priority, background image hits no longer touch every file timestamp, and first-artwork/shelf timings are logged for on-device verification.
- Companion 0.2.0 requires a server update and restart. It is tested locally; the production server remains unchanged. GitHub pushes remain paused.

- Final 0.9.0 validation: 33 plugin tests and 232 client tests pass, release lint/build/signature checks pass; installed on .128 with version code 41. Current APK and checksums are on the desktop; obsolete 0.8.1 desktop APK removed as authorized.

## Companion 0.3.0 / Lowfin 0.10.0 — server API providers

- Native Jellyfin plugin dashboard page for OMDb, MDBList, TMDB and Tracearr credentials; shared all-user or selected-user access enforced by the API. No key distribution or client-key upload.
- Per-device provider/rating switches retained. Hide local TXT/key/Tracearr connection controls only for a detected, authorized server-provider capability; other users and absent/older plugins retain local configuration. Hide periodic cache hours only with incremental-cache capability.
- Keep existing local rating values, default-on permanent cache with a no-read/no-write switch, provider-specific selection variants, and no rating precache. Playback count presentation is now “N Wiedergaben” / “N plays”.
- Plugin/client regression, network-cache, provider permission/secret and actual Jellyfin dashboard configuration tests are required before packaging. GitHub publication stays paused.

- 0.3 validation: 40 plugin tests and 236 client tests passed. The isolated Jellyfin 12.2 integration verifies admin configuration saves, selected-user capability/access, ordinary-user denial of configuration, secret-free client status and registered dashboard page, alongside all prior playback/cache checks. Simplified banner is packaged in Desktop/lowfin/Lowfin.Companion_0.3.0.0.

- Lowfin 0.10.0 release installed and started on .128; version code 42 and non-debuggable package verified. Current APK is on Desktop; older 0.9.0 desktop APK/checksum removed as authorized. Production Jellyfin plugin deployment remains the user’s server-side step.

## 0.10.1 — start diagnosis and optimization

- .128 logs confirm cached shelf delays up to 15.2 s. TV-card startup also fetched metadata in MainActivity and again in PlayerActivity, after loading unrelated home content.
- Valid own-account play/resume links now bypass home loading/shared content workers and open the player immediately; only one fresh metadata read resolves native and Companion resume before negotiation. Detail/open links retain their existing flow; the home screen is constructed on return.
- WAL allows metadata reads during cache writes, server resume states are reconciled as one transaction without HTTP inside it, and image-inventory work has its own background-priority executor so both decoding workers remain available. No cache is discarded and existing playback ordering remains.
- Quantities use Android plural resources for German/English. Regression tests cover occupied home workers, launcher scopes/actions and current/restart/restored positions.

## 0.10.2 — interrupted cover-cache recovery

- On TV .128 the cache contained 29,737 metadata records but no Companion cursor, while SyncService was running.
- Save a complete metadata/membership baseline and persistent artwork queue atomically before checking covers; retry only pending artwork after interruption.
- Require a valid initial Companion journal anchor and limit repeated baseline starts. Existing caches remain available.
- Keep GitHub publication paused; verify release on .128 and retain the newest Desktop APK.

- Sender logos are requested from Jellyfin on demand; no persistent sender-artwork cache or background sender-cover work. Existing film/series/episode caching stays enabled.

- Validation: 242 client tests, release lint, signed non-debuggable 0.10.2 (44) installed on .128; latest APK/checksum on Desktop. No GitHub push.
- On-device 0.10.2 smoke check: cached home shelves appeared after 1.9 s; first artwork after 2.7 s. The required initial baseline is still running (1,500 / 29,671 records observed); its completion on the TV has not yet been verified. Interruption/checkpoint behavior is covered by the automated tests.

## 0.10.3 — general navigation optimization

- TV .128 graphics statistics: 97 / 573 janky frames, including a 1.75 s frame; aggregate data includes player and startup and is not a per-screen benchmark.
- Move library diff comparisons off the UI thread; avoid full DTO serialization for home shelves. Replace full-grid invalidation with incremental updates and keep existing home focus behavior.
- Isolate foreground detail/browse/season requests from home work; skip queued requests after their screen is abandoned.
- Detail pages display Jellyfin source Width × Height and Size, paired per media version; English/German number formatting and unknown metadata tested.
- Keep GitHub publication paused.

- Validation: 247 client tests and release lint passed; HD detail layout inspected. Signed release 0.10.3 (45) and checksum on Desktop. TV .128 was playing during diagnosis, so no installation or post-update TV benchmark was performed.

## 0.10.4 — branding, settings focus and safe episode preloading

- New launcher artwork uses the Companion's minimalist style through existing editable SVG/geometry sources and deterministic density exports.
- Persist settings focus/scroll in navigation history and default to Home on fresh settings entry.
- Preload one next episode's direct-file prefix into the current disk cache, without ExoPlayer/codec allocation or session reports. Default 8 MB and 30 seconds before a skipped Outro or ordinary end; options in Playback.
- Require healthy ongoing playback with at least 10 s buffered, available RAM above threshold/64 MB, enabled video cache and disk reserve; limit to one attempt per episode and cancel on media change/release.
- Scope/timeouts are independent from the actual player's requests. Fresh server metadata/negotiation/Companion state still resolve at real playback start.
- Gapless playback through decoder/source reuse remains a separate future optimization; no guarantee of a fully invisible transition in this release.
- User authorizes future APK installations without an additional confirmation. No GitHub push remains in force.

- Validation: 251 client tests and release lint passed; default settings focus/return category and reuse of prefetched bytes across actual playback session keys tested. Native artwork preview inspected. Signed non-debuggable 0.10.4 (46) installed/verified on .128; APK/checksum on Desktop. Actual near-outro autoplay transition timing remains unmeasured.

## 0.10.5 — fixed detail title artwork

- Show the title logo at the top right in a 228 × 80 dp frame with FIT_CENTER and no dimension adjustment.
- Hide the textual title only when an image is ready; failed/missing logos retain the title. Resolve episode series logos from scoped cached metadata or the server in background.
- Test wide, tall and square images, missing-image fallback and HD layout/scale matrix.
- Fixed logo geometry uses dp; request logo pixels at display density (320 px at HD TV density, 960 px at xxxhdpi), bounded by the existing 1440 px artwork limit. Add a 4K-density network regression test.
- Persistent user preference: after every authorized APK installation, launch Lowfin again on that same target device.

- Validation: 254 client tests and release lint passed, including proportional detail-logo placement and xxxhdpi logo request width. Signed non-debuggable 0.10.5 (47) installed on .128 and launched after installation; newest APK/checksum on Desktop. No GitHub push.

## 0.10.6 — cast and person navigation

- Add virtualized horizontal cast cards with hardware-clipped circular portraits and names. Load only visible images through the existing persistent artwork cache.
- Person pages use native authenticated Jellyfin person/item metadata and PersonIds/PersonTypes filters for Movie/Series, with 60-item pagination and existing clickable library cards.
- Keep scoped offline snapshots for biography and visited filmography pages; retain navigation history and originating actor/scroll on return.
- Completely remove next-episode preload implementation, controls and tests. The earlier preload/gapless follow-up is withdrawn on user request; no further work is planned for it.
- Verify actor/crew filtering, movie/series-only results, clickable titles and return navigation before release/install/start on .128.

- Validation: 254 client tests and release lint passed; circular cast/person HD renders inspected. Cast → person → film → back to original cast focus tested. Signed non-debuggable 0.10.6 (48) installed on .128 and launched; APK/checksum on Desktop. Episodic preloading absent from main code/resources. No GitHub push.

## 0.10.7 — cast focus/navigation correction

- Root cause: MainActivity intercepted Left on all non-button detail controls before cast cards received it. Route cast horizontal events first.
- Explicit CastList pending focus follows all offscreen actors during rapid remote repeats and clamps at row boundaries without opening the drawer.
- Use a circular accent ring and highlighted caption; remove the rectangular tile focus background/scale.
- Verify 40 actors, rapid traversal in both directions, boundary focus and closed sidebar, then build/install/start .128 and replace the Desktop APK.

- Validation: 255 client tests and release lint passed. HD cast-ring preview inspected; rapid traversal across 40 actors in both directions keeps the drawer closed. Signed non-debuggable 0.10.7 (49) installed and launched on .128; verified APK/checksum on Desktop. No GitHub push.

### 0.10.8 — event-only updates of completed Companion caches

- Completed local caches are retained across absent/expired plugin cursors; no automatic full scan in Companion mode. Subscribe to the server head and report an unverifiable history gap, with manual reconciliation available.
- Initial population remains required for empty caches. Without a recognized incremental-cache plugin, native periodic refresh remains in effect.
- Plugin change listening continues during foreground playback with reduced artwork work; no background cron.
- Future server journal recovery could resolve missed events selectively; the current API cannot reconstruct an expired journal.

### 0.10.9 — performance pass

- Reduce startup disk work with a crash-aware persistent artwork inventory; only exceptional recovery recounts local files. This inventory does not scan Jellyfin or request metadata/artwork.
- Lighter list requests, transactional single-item writes, unchanged-genre index reuse and schema-8 query indexes reduce network, parsing and SQLite work. Full detail/playback data remains available.
- Deduplicate concurrent bitmap decoding, discard abandoned home cache tasks and streamline video-cache key formatting. Preserve remote navigation, bounded image memory and the Companion-only event cache policy.
- Verify recovery, account/cache persistence, list/detail field separation, database upgrades, late image callbacks and the existing complete client suite. Compare real-TV startup logs without claiming a universal speedup.

- Avoid repeated account-scope hashing and Android Keystore decryption for unchanged provider keys; verify imported/changed/deleted keys invalidate memory and stay encrypted on disk.

### 0.10.10 — uniform boolean controls

- One remote-accessible native switch style across boolean settings, genre/library selection, ratings and Companion policy controls. Preserve focus on local changes.
- Replace activation-only TV channel actions with reversible publication switches while preserving channel IDs and leaving launcher approval/visibility with the launcher.
- Add an explicit video-cache switch that remembers its previous capacity. More-card arrows use rounded joined paths and live accent updates.

### 0.10.11 — compact dialogs and verified paging

- Remove the legacy skip master after a one-time effective-state migration; use separate intro/outro controls and retain the first-episode exception.
- One Lowfin dialog appearance across settings, media actions and playback, keeping existing native dialog callbacks and input behavior.
- Compact person biography action and conditional Previous/Next controls using one-item lookahead without changing 60-item page boundaries.

### 0.10.12 — dialog sizing and footer navigation

- Uniform sidebar item spacing between Genres and Live TV.
- Adaptive multiline dialog actions, retaining existing native button identities/callbacks.
- Unscaled focus frames and explicit remote navigation between each paginated grid and its footer, with return to the originating card.

### 0.10.13 — player spacing and pause clock

- Add timeline/time-label clearance above the transport row. Keep finish time visible during pauses and move it forward with wall time, using the existing UI tick.
- Compare actual device metadata/artwork inventory with the previous measurement using the read-only cache diagnostic; no recache or cache reset.

- Cache diagnostics: keep the latest 50 Companion metadata deliveries (local receipt time, item title/type and series/episode identifiers) atomically with the cache cursor. Ordinary navigation and empty polls do not alter this audit. Historical item save timestamps do not prove a Companion delivery; server event timestamps are not exposed by Companion 0.3.

### 0.10.14 — TV channel branding

Refresh existing TV-provider row logos for current artwork using a versioned success marker. Preserve channel IDs, visibility and recommendations. Retry failed writes. Diagnose cached Scooby artwork by item/tag/size; do not interpret cache-use timestamps as download evidence.

### 0.10.15 — Optional native HDR and random genres

HDR playback remains disabled by default under Playback. Check current display and decoder capabilities before advertising HDR10/HDR10+/HLG; use existing SurfaceView player, no new preload or forced transcoding. Do not claim Dolby Vision compatibility. Random selection adds all-movie and genre menus, preserving selected library and genre in online and offline queries, slot animation and direct detail navigation.

### 0.10.16 — Smooth backgrounds and cast roles

Replace all eight old accent compositions with eight new softly illuminated patterns, keeping the existing hue pool and rotation policy. Use static GPU composition with bounded shared dithering instead of animated backgrounds or large bitmap allocations. Apply the same treatment to background shading/player chrome. Display item-specific Jellyfin cast roles beneath names, with missing-role and recycled-card handling; preserve horizontal focus and person/detail links.

### 0.10.17 — Conservative stream-start optimization

Memoize stable decoder capabilities only after successful reads, retry transient failures, and prepare the local cache index during requested playback negotiation. Do not preload media or the next episode. Preserve all buffering thresholds, RAM/storage budgets, live routing, fresh server playback checks and ordered Companion reporting. Add phase timings and concurrent cache-initialization/capability regression tests; distinguish observed timings from controlled performance claims.

### 0.10.18 — Optional scrolling and library alphabet

Use native RecyclerView scrolling for horizontal home shelves, vertical row changes and library grids, with a separate default-off Appearance switch. Replace pending targets during rapid D-pad input and fence stale animation completion; restore one visible genre/recommendation row at rest, including viewport resizing. Preserve focus and paging boundaries. Add a compact header letter menu with count-only, library/user/type-scoped server lookups and scoped offline fallback. Do not enumerate/download the whole library to locate a letter, and keep initial library focus on its first medium.

### 0.10.19 — Home information motion

Move genre/recommendation information upward into place using compositor translation and a temporary rendering layer. Avoid repeated unchanged viewport layout requests during shelf changes; preserve focus/navigation, animation switches and single-shelf visibility. Validate with existing rapid remote and content refresh regression tests.

Profile TV frame times with gfxinfo; cache static filtered backdrop and shades together on displays up to 1080p to avoid repeated full-screen shader passes during foreground animation. Bound added GPU memory and preserve image/accent updates.

### 0.10.20 — Stable home ratings and card motion

Reserve selected rating slots before the home panel enters, reuse chip views, preserve their widths/heights across missing and delayed responses, and clear previous-film values. Use a memory-only immediate rating lookup while retaining debounced provider requests and cache-off behavior. Reduce rating chip allocations and validate remote navigation plus delayed-rating geometry. An additional temporary card-layer experiment did not establish a repeatable frame-time gain and was removed.

Keep recommendation/genre headers invisible until selected so the first header cannot peek clipped below Continue/Next Up; preserve row geometry and the multi-shelf option.

### 0.10.21 — Fully visible home headings

Refine header visibility to actual viewport geometry, rather than focused-row identity. Show fully visible recommendation/genre headings while Next Up is focused; hide partial headings during scrolling, preserving row layout and focus.

### 0.10.22 — Compact configurable dialogs

Prioritize smaller native Lowfin dialogs and a persisted 0–100% background transparency control with preview, Save/Cancel and bilingual labels. Preserve remote focus, wrapped actions and input semantics. General optimization changes are parked separately until this UI release is installed.

### 0.10.23 — Conservative hot-path optimization

After installing the compact-dialog release, avoid unchanged home shelf rebind/focus resets while refreshing raw action metadata. Bound image URL hash memoization to 256 entries, cache mixed-grid spans, reuse player clock formatting and skip identical label writes. Validate changed metadata, labels, watched state, pending navigation, source tags, cache eviction, pause/timezone changes and long runtimes. Retain playback transport, buffers, Companion/Tracearr ordering and plugin-only completed-cache updates.

Include content-sized dialog width and height in 0.10.23: bound native content to 82% screen height, scroll long lists/messages/custom controls, preserve action access and transparency.

### 0.10.24 — Balanced centered dialogs

Center dialog headings/messages/list labels/actions. Refine size bounds to 280–480 dp width, minimum 112 dp content height and maximum 70% screen height including decor padding, respecting smaller available space. Preserve scrolling, input semantics, transparency and all conservative optimizations.

### 0.10.25 — Lower idle and per-list overhead

Batch per-user Companion state reads in bounded chunks, preserve list order and visibility semantics, avoid writing hidden/unchanged status labels and release detached status views. Reuse warm rating content without placeholder resets and skip unchanged chip writes. Keep dialog geometry/centering, persistent cache policy, playback reporting and server rules unchanged. Validate account isolation, multi-chunk lists, duplicates, plugin loss, changed ratings and immediate sidebar status refresh.

### 0.10.26 — Full precache reset and playback diagnosis

- Confirmed full account library precache deletion, preserving independent ratings and Companion state; retire stale writers and recreate cache workers.
- Bounded private playback failure history plus decoder/buffering/dropped-frame logs. Investigate the reported runtime decoding failure before changing playback policy; startup codec warnings alone do not establish a cause.

### 0.10.27 — Centered brand and transparent sender artwork

- Keep the existing mark geometry, centered behind the readable Lowfin wordmark in square/adaptive icons and TV banners.
- Transparent sender image frames with alpha-preserving on-demand decoding; hide the redundant live player caption.

### 0.10.28 — Configurable home shelves

- Optional library shelf with 16:9 Thumb/Backdrop imagery and scoped library navigation.
- Account-scoped home shelf order, independently movable resume, next-up, recommendation slots, genre pins and libraries; remote-operated native Lowfin dialog.
- Stable semantic identities decoupled from display positions and changing recommendation titles. Preserve asynchronous update routing, focus and single-shelf descriptions.

### 0.10.29 — Conservative navigation overhead reduction

- Compare actual Android TV program content before writing unchanged cards; preserve dynamic-heading compatibility notifications, stable channel IDs, external repair and dismissals.
- Nonblocking cached-image size display during storage maintenance.
- Rate-limited key queue/handler timing for reported intermittent multi-second delays. TV frame history confirms outliers but does not establish one root cause; no general FPS improvement or complete freeze fix claimed.

### 1.0.1 — Focus outline and detail-return corrections

- Bound focused/action scaling by clipping ancestor bounds, including scrolling/padding, rather than letting focus frames grow beyond containers.
- Capture selected grid media ID and fallback index alongside scroll state; restore after asynchronous list commits and cancel stale restoration on navigation.
- Install on TV .128 and launch there as specifically requested.

- 1.0.2: deterministic library entry focus, persistent completed-precache control, consistent genre-dialog typography.

- 1.0.3: independent detail watch-later/favorites actions, vector icons and completion-based movie watch-list removal.

- 1.0.4: bounded detail-toolbar navigation, high-contrast play/resume focus and immediate scroll-to-top when returning from cast.

- 1.0.5: fixed compact detail toolbar fitting all eight actions; optional experimental SDR GPU noise reduction, off by default, native fallback on processing failure/frame drops.

- 1.0.6: fix Media3 effect-factory constructor retention under R8; validate optimized reflection path alongside GPU shader pixels.

- 1.0.7: correct lifetime-frame-drop false alarm during episode changes; keep error/performance fallback. General optimization work and label change deferred at user request (docs/deferred-optimization-2026-10-16.md).

- 1.0.8: resume deferred UI allocation optimizations (detail rating-view reuse, selective home metadata copies), remove filter experimental label, preserve 1.0.7 fallback.

- Weitere Optimierung nach 1.0.8: vollständiger Metadatenvergleich ohne DTO-Serialisierung, vorzeitiger Abbruch und wiederverwendete Feldlisten; Prüfung siehe `docs/optimization-followup-2026-10-16.md`.

- 1.1.0: weniger doppelte Start-/Launcher-Abfragen, gebündelte Metadaten-Lookups, leerer Companion-Ausgang ohne Zusatz-Probe, leichtere Bewertungen und wiederverwendete UI-Zeichenobjekte. Messung und Grenzen: `docs/optimization-1.1.0.md`.

- 1.1.0 fertig gebaut/geprüft (390 Tests); Installation wartet auf ausdrückliches Go: .128, .87 und .166, Start nur .128, danach .128 nach erfolgreicher Prüfung herunterfahren. Detail-Aktionsleiste nutzt volle Inhaltsbreite.

- 1.1.0 nach Go auf .128, .87 und .166 installiert und geprüft; nur .128 gestartet, anschließend wie beauftragt heruntergefahren (ADB offline).

- 1.1.1: Weiterschauen/Next Up unabhängig und mit reservierten Workern aktualisieren; optionale, gebündelte Companion-Meldung unter Bibliothek und Cache. Aktuelle Installationsvorgabe (2026-10-21): fertige Builds automatisch nur auf .128 installieren und öffnen; .87/.166 nicht anfassen. Details: `docs/home-refresh-notices-1.1.1.md`.

- 1.1.1 fertig: 397 Tests, Release-Prüfung bestanden; ausschließlich auf .128 installiert und geöffnet. Next Up beim geprüften Start vor dem langsameren Companion-Resume-Abgleich aktualisiert. APK auf dem Schreibtisch.

- 1.1.2: optionaler Standby-Schutz während aktiver Precache-Arbeit; Startseiten-Reihe, Medien-ID, Mehr-anzeigen-Auswahl und Scrollposition bei Detail-Rückkehr erhalten. Lokale Metadaten nachladen, keine sofortige neue Genre-Zufallsauswahl. Tests/Dokumentation: `docs/home-return-awake-1.1.2.md`. Installation nur .128.

- 1.1.2 fertig, 401 Tests bestanden; Release auf .128 installiert und geöffnet. APK auf dem Schreibtisch, keine Installation auf .87/.166.

- [x] 1.2.7: Breitere, dezente Zufallsfilm-Animation mit größeren Titeln und zweizeiliger Gewinneranzeige.

- [x] Companion 0.5.1: Cover-Index mit echtem Aufgabenfortschritt, Phasenzählern und regelmäßigen Diagnose-Logs statt stiller 0-%-Anzeige.

- [x] 1.2.8: Zufallsfenster-Breite in der gemeinsamen Dialogformatierung korrigiert; 660 dp mit Bildschirmbegrenzung, optisches Facelift und Regressionstest nach Dialog-Attachment.

- [x] 1.2.9: Optionaler nativer Bildschirmschoner mit 1–20 Minuten Inaktivität, wählbarem Bildwechsel, Bewertungen, Logos, sanften Überblendungen und Wachhalten nur während der Anzeige. Keine Companion-Bildtags; vollständiges Abfangen der ersten Fernbedienungstaste und Fokus-Rückkehr.
- [x] Getrennte kontobezogene Bibliotheksauswahl für Bildschirmschoner und Home-Banner, auch beim Offline-Fallback.
- [x] Einheitliche Abstände in den Einstellungsseiten statt doppelter oder fehlender Abstandshalter.

- [x] 1.2.10: Genreübersicht mit lokalen Vektor-Icons in der Akzentfarbe, Zuordnung anhand der tatsächlichen TV-Genres; deutscher/englischer Alias-Abgleich. Fehlerhaften ['Gaming']-Eintrag nur in der Übersicht auslassen.

- [x] 1.2.11: Jellyfin-interne Bewertungszeile aus dem Bildschirmschoner entfernt; nur ausgewählte externe Bewertungen anzeigen.

- [x] 1.2.12: Cover-Tag-Binding vom Datenbankschreiber entkoppelt, neue Tag-Daten gebündelt speichern und persistente Treffer nicht erneut schreiben; ereignisgesteuerter Schoner-Timer statt sekündlichem Polling.

- [x] 1.2.13: Staffel-/Folgenzeile im Player mittig unter dem proportionalen Serienlogo, mit gemeinsamem festen Rahmen und zentriertem Titel-Fallback.

- [x] 1.2.14 / Companion 0.6.0: Alle JellyTag-Kategorien als native, begrenzte Cover-Tags, serverseitig wählbar, sprachneutraler Index und abwärtskompatible API.

- [x] 1.2.15: Bildrate, Audiokanäle, Trailer-Auflösung, Untertitelsprache, Banner-Wechsel und Spulverhalten lokal konfigurierbar; einheitliche Einstellungsabstände.

- [x] 1.2.16: Sender lang drücken für aktuelles Programm; Live-TV-Seitenleisteneintrag nur bei verfügbaren Sendern, mit kontobezogener Hintergrundprüfung.

- [x] 1.2.17: Sender-Programmübersicht per langem OK, jetzt/danach mit Tagesangaben und Uhrzeiten, blätterbare Folgesendungen und Detailbeschreibung bei OK.

- [x] 1.2.18: Navigationsoptimierung anhand .128-Logs: unnötige Seitenleisten-Neuaufbauten vermeiden, Beschreibungen mit begrenztem HTML-Cache und schnellem Klartextpfad, Cover-ID-Prüfung ohne wiederholte Regex-Kompilierung.

- [x] 1.2.19: Remove the remaining main-thread cover-tag persistence wait; fence retry deadlines by metadata generation and test blocked-writer completion.

- [x] 1.2.20: Rate-limit sustained Companion cache notices while continuing immediate incremental updates.

- [x] 1.21: Reduce redundant badge redraws, rating placeholder layouts and cover-label draw/validation allocations without changing cache or playback policy.

- [x] 1.21: Remove duplicate TV-channel writes and batch persistent Companion tag reads.

- [x] 1.21 (requested release name, versionCode 107): Batch launcher recommendation and selected-library reads; includes previous local optimization rounds.

- [x] Companion 0.7.0: Dashboard-Seitenleiste mit originalem Lowfin-L, gegliederte Einstellungen und optionale Discord-Präsenz inklusive Live-TV-EPG-Zeitachse und manueller Nutzerzuordnung ohne Standalone-Import.
- [x] Companion 0.7.0: Obere Jellyfin-Versionssperre entfernen, Mindestversion 12.2 behalten; API-Tests gegen 12.2/12.3 und isolierter Server-Ladetest.
- [x] Companion 0.7.0: Repository-Manifest und meta.json aus dem lokalen Paket erzeugen; Veröffentlichung beider Projekte bis zur erneuten Freigabe zurückgestellt.

- [x] Companion 0.7.0: Discord-Verbindungsaufbau an bereitgestellten Originalcode angleichen (unversionierter Gateway, Client-Header, große READY-Nachrichten); reale lokale WebSocket-Tests und Close-Code-Diagnose.
- [x] Companion 0.7.0: Discord-Tokens verschlüsselt und nutzergebunden speichern, alte Werte migrieren, persistenten Schlüsselring und Backup-Grenzen dokumentieren.

- [x] Companion 0.7.0: Originalen Discord-Cover-Ablauf von rsbxDev übernehmen, inklusive entfernten Bildern, quadratischem PNG, Originalbild-Fallback, persistentem Upload-Cache und begrenzten Wiederholungen; Herkunft dokumentiert.

- [x] 1.3 (versionCode 108): Prioritize fresh Next Up presentation over cache persistence and share startup Companion discovery; retain resume reconciliation and add shelf-stage timings.
- [x] 1.3: Reduce repeated numeric metadata-comparison and focus-animation allocations while preserving numeric precision, animation toggles and focus geometry.
- [x] 1.3: Replace the measured full-library Companion resume candidate scan with indexed native candidates and managed IDs; validate equivalence for visibility, scope and pagination.

- [x] 1.3: Deduplicate launcher artwork resolution within each publication, retain retries/image-tag changes/cache-clear fencing, and remove redundant cover-file checks.

- [x] 1.3: Optional accent focus pulse with lifecycle shutdown, centered banner-cover reveal, aligned compact ratings with labels and fewer redundant banner updates.
