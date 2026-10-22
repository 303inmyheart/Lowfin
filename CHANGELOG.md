# Changelog

## 1.2.20

- Limit Companion cache-update notices to one per minute during sustained updates, accumulating the intervening entry counts. Metadata and artwork processing remain immediate.
- Preserve foreground, settings and account-lifecycle guards; test sustained bursts and disabling during the notification cooldown.

## 1.2.19

- Removed a remaining UI-thread wait on the Companion cover-tag persistence lock when an asynchronous batch completes. Retry deadlines carry their metadata generation so stale responses cannot delay updated tags.
- Added regression coverage for completion during blocked persistence and stale batch retries. Navigation stalls were confirmed in Android input logs; this fix does not establish that every stall has the same cause.

## 1.2.18

- Avoid rebuilding sidebar buttons for unchanged library and Live TV availability responses. Metadata/artwork changes still update home library cards; renamed/reordered libraries still refresh navigation.
- Render ordinary home descriptions without invoking the HTML parser; preserve formatting/entities/whitespace through a bounded formatted-text cache.
- Replace per-cover GUID regex compilation with equivalent allocation-light validation for cover tags and invalidation IDs.

## 1.2.17

- Replace the single current-programme popup with a compact channel programme guide on long OK: now and upcoming shows, local times and day headings.
- Open programme descriptions with OK and return to the selected row; load additional entries in bounded pages with correct Previous/Next visibility.
- Add refresh/retry controls, account/dialog lifecycle guards and English/German labels. The main Live TV page remains a channel list.

## 1.2.16

- Long-press a Live TV channel to show its currently airing programme, local start/end times, episode title and description in a native Lowfin dialog. Short press still starts playback.
- Hide the sidebar Live TV entry when the signed-in user has no available channels. Probe one channel in the background and retain the last successful result during temporary server failures.
- Add English/German loading, unavailable and empty-guide messages; preserve compact dialog sizing and scrolling.

## 1.2.15

- Add device-local frame-rate limits (automatic/30/60 FPS), audio channel limits (automatic/2.0/5.1/7.1) and trailer resolution selection. Stereo downmix retains priority.
- Add an independent subtitle language, configurable banner rotation/interval and seek step/hold acceleration. Seek button labels follow the selected step.
- Keep existing defaults and consistent settings control/helper spacing; include English and German labels.

## 1.2.14

- Display all Companion cover tag categories in compact, frame-aligned monochrome pills: resolution, HDR variants, 3D, video/audio formats and language/subtitle codes.
- Respect server tag selection, the local tag visibility switch and the local HDR setting. Dense tag lists use a bounded overflow indicator.
- Persist named tag facts independently of images and refresh them through existing Companion invalidations. Legacy Companion clients remain supported.

## 1.2.13

- Center the episode caption directly under the series logo in the player. Share a fixed 200 dp frame with proportional centered artwork and a centered title fallback; long captions can use two lines. Live TV and movie headers retain their existing layout.

- Complete English UI audit, including the season-number input hint; English remains the default with German selectable.
- Publish locally built releases without automatic push-triggered workflows. Companion source, changelog and Jellyfin update feed live in a separate repository.

## 1.2.12

- Keep cover-tag binding and request batching independent from background database persistence locks. Persist newly fetched tag facts in one transaction and avoid rewriting facts already read from disk.
- Replace the screensaver’s once-per-second polling with an idle-deadline timer; disabled and already-visible screensavers need no idle polling. Settings changes still take effect immediately.

## 1.2.11

- Removed the extra Jellyfin community/critic rating block from the screensaver. Only the existing external rating row remains, respecting the selected providers and visible ratings.

## 1.2.10

- Added local, accent-tinted vector icons to the genre overview, covering the genres inspected on the TV and their German/English aliases. No image requests or additional image cache are needed.
- Omitted the malformed `['Gaming']` tile from the overview while retaining the normal Gaming genre. Jellyfin metadata remains unchanged.
- Genre card focus, filtering, counts and navigation are preserved. The temporary same-signer genre diagnostic was removed from the TV after read-only inspection.

## 1.2.9

- Added an optional native in-app screensaver for movies and series: backdrop fades, slow zoom, proportional logos with title fallback, overview and ratings. Companion cover labels never appear.
- Screensaver settings include enable/disable, every idle delay from 1–20 minutes, slide interval and an independent account-scoped library selection. The TV stays awake only while the screensaver is visible; remote dismissal consumes the full key press and restores the previous focus. Playback and leaving the app stop the saver.
- Added independent account-scoped library selection for the home banner, including restricted offline fallback and invalidation of old banner history after a selection change.
- Unified settings spacing for controls, headings and helper text, removing duplicated legacy spacer Views.

## 1.2.8

- Fixed random movie window shrinking again when shared dialog styling ran on attachment. Its explicit width now survives styling and content fitting.
- Expanded the window to a moderate 660 dp bounded by display width with 24 dp side margins, with larger reel titles, accent heading and a softly illuminated selection band.
- Added a regression test that opens the actual styled dialog and verifies its width after attachment.

## Companion 0.5.1

- Fixed cover-index scheduled task showing 0% until completion. Added progress for scan, per-user summaries and persistence, plus progress while waiting for an active background rebuild.
- Added start/completion/cancellation/error logs and 15-second diagnostic phase/counter logs; dashboard status includes the current phase and counters.
- 78 Companion tests passed, including progress publication and concurrent rebuild regression coverage.

## 1.2.7

- Redesigned the random movie animation with a wider, screen-bounded window, larger titles, soft accent selection and a restrained winner reveal. The selected movie can use two title lines. Duration, cancellation and automatic detail navigation remain unchanged.

## 1.2.6 / Companion 0.5.0

- Add a persistent server-side DVD/5.1/HDR fact index and user-scoped series/season DVD summaries. Serve indexed values instead of computing them on client requests.
- Update affected entries from Jellyfin events and reconcile every 24 hours through a registered scheduled task; expose index timestamps/status in the plugin dashboard.
- Draw an optional HDR badge only when HDR playback is enabled on that Lowfin device. Keep episode ratings uncached and preserve source images/cover files.
- Retry pending initial index results automatically without persisting unknown values as zero; extend tag metadata to HDR without rebuilding artwork.
- Preserve the remembered home card when metadata refreshes a restored shelf before it reacquires focus.


## 1.2.5 / Companion 0.4.1

- Show the release year with runtime and genres in the discovery banner.
- Display episode-specific IMDb/OMDb and TMDB scores on episode details, with fresh on-demand requests and no memory/disk rating cache. Keep movie/series caching unchanged.
- Show DVD on series/season covers when a strict majority of available episodes is SD/DVD resolution. Use user-scoped database counts and invalidate parent tag facts on episode metadata delivery.


## 1.2.4

- Align the home discovery banner with the full shelf content width.
- Give banner ratings a dedicated full-width bottom row at their original size; extend the overview to four lines. Preserve poster proportions, controls and rating cache behavior.


## 1.2.3 / Companion 0.4.0

- Add optional DVD (SD/DVD resolution) and 5.1 (six-channel audio track) tags controlled by the Companion dashboard and a per-TV Appearance switch.
- Draw the minimalist transparent tags in native artwork-frame coordinates, retaining visibility when 4:3 episode images crop to 16:9 and scaling with HD/4K UI density.
- Batch authenticated tag facts only for requested media, cache them per account, invalidate on plugin metadata delivery and reject stale results/recycled-view callbacks. Preserve original image URLs and artwork cache.


## 1.2.2

- Use the existing monochrome MDBList provider vector in the shared rating badge renderer. Display it in the discovery banner, genre and watched-film recommendation shelves, and detail pages; preserve proportional icon fitting and reserved score widths.

## 1.2.1

- Add runtime and up to three genres beneath the discovery-banner identity, and compact provider-icon rating badges below its synopsis. Reuse existing source switches, optional Companion providers and the persistent on-demand rating cache. Reserve badge widths to keep the layout stable while scores arrive; omit unavailable facts and disabled rating sources.
- Remove the visible previous/next/OK hint while retaining its accessibility description and all native remote controls. Keep proportional poster/logo frames. Debounce banner rating work until visible, cancel queued work on hide/detach and reject late callbacks after hiding or switching films. Existing non-banner rating layouts remain unchanged.

## 1.2.0

- Show only the existing animated Lowfin logo during startup; remove its status caption and extra wrapper. Preserve fresh Resume/Next Up readiness, the optional switch, remote dismissal and bounded offline fallback.
- Refresh identical media metadata by updating its freshness stamp only, preserving JSON, row identity, genre membership and detail data instead of replacing every item/index. Compare all incoming fields including nested playback changes and explicit nulls; retain partial-DTO merge semantics and ignore local Companion decoration.
- Batch restored home-row metadata lookups across shelves, deduplicating IDs while preserving row/card order, missing-item fallbacks, account scope, fresh-response guards and selected focus.
- Batch Companion resume reconciliation for native/cached candidates after accepting current state. Preserve plugin ordering, missing native-item decoration, manual fences, watched visibility and selected-library isolation. No playback-report or server API changes.
- Check baseline existence with SELECT 1 LIMIT 1 instead of counting the complete library on every incremental cycle; skip home-snapshot parsing for empty journal batches that only advance the cursor. Keep cache completion, history-gap behavior and persistent artwork intact.
- Snapshot artwork timestamps once before eviction sorting, preserving stable oldest-first order and existing size/clear guards. Reuse the static hash alphabet; leave cache keys and limits unchanged.

## 1.1.3

- Add an optional native movie-discovery home banner with backdrop, fitted logo/title, 2:3 poster, overview, 15-second rotation and a bounded 20-movie history. Left/right browse; OK opens details. Reorder it with existing home shelves. Pause rotation/zoom offscreen and respect the animation switch; reuse existing authenticated image and metadata caches. Disabled by default.
- Keep banner poster drawables fully opaque while animating View opacity; verify actual artwork pixels as well as download completion.
- Add a reversible startup-logo option under Appearance, enabled by default. Wait for fresh Continue Watching and Next Up responses plus visible shelf artwork, rather than accepting cached rows alone. Keep underlying loading concurrent, preserve initial card focus and avoid replaying on detail returns. Allow remote dismissal and a 15-second offline/error fallback.

## 1.1.2

- Add an opt-in Prevent sleep during precache switch under Library and Cache. Keep the foreground UI awake only during a full precache or active incremental artwork work; release afterward and preserve player-owned wake behavior. No new permission or global TV setting changes.
- Restore the home shelf, selected media or More card and horizontal layout after returning from details/list views. Keep prior genre/recommendation selections on immediate return instead of randomizing them again; continue fresh Resume/Next Up loading and ID-based selection recovery when their ordering changes. Store data/layout state without retaining old view hierarchies.

## 1.1.1

- Refresh Continue Watching and Next Up concurrently on two reserved workers so a slow resume/Companion response or busy recommendation workers do not delay the other shelf. Preserve cached rows, Companion rules, account/screen guards and launcher publishing.
- Add a reversible plugin update notification switch under Library and Cache, enabled by default and shown only with incremental Companion support. Notify only for committed new plugin batches, group nearby deliveries, suppress empty/reset/baseline checks and discard notices on backgrounding, account retirement or disablement. Image information announcements do not claim completed artwork downloads.

## 1.1.0

- Let detail actions share the full content-grid width evenly while keeping compact height, spacing and bounded remote navigation; all available actions stay in one row.

- Reuse the start page’s resume/next-up/recommendation/view snapshots for launcher publication instead of fetching them a second time. Keep independent hourly launcher refreshes and dynamic channel-name notifications; remove the unused recently-added fetch.
- Batch launcher and watch-later metadata lookups in groups of 128; preserve order, missing-item fallbacks, account scope and Companion decoration.
- Avoid a redundant Companion capability request when the persistent playback outbox is empty. Pending-report replay, normal discovery and playback ordering remain intact.
- Format ordinary rating scores without per-badge Formatter/ICU initialization, preserving decimal rounding, scales and existing display strings.
- Reuse unchanged accent gradients, artwork card backgrounds, actor focus rings and arrow paths; retain color rotation and transparent live-TV artwork. Shelf progress bars no longer inflate an unused animated spinner.
- Add bounded startup main-loop sampling in the separate diagnostic APK. Document variable Android renderer waits without claiming they are fixed.

## 1.0.9

- Compare complete library metadata directly instead of serializing full JSON snapshots. Retain nested playback fields, ordered arrays, null distinctions and large-integer precision. Ignore object key order to avoid unnecessary updates.
- Reuse home comparison field lists and compare nested presentation metadata without JSON serialization.

## 1.0.8

- Reuse unchanged detail-rating badge views, icons and backgrounds; keep existing same-item scores visible during refresh, update changed/missing/provider-selected values, and retain stale-result guards on item switches. Provider requests and cache semantics remain unchanged.
- Copy home-shelf lists only when plugin changes actually match entries; keep unrelated rows untouched while refreshing all matching occurrences and nonvisual action metadata.
- Remove experimental wording from DE/EN noise-reduction settings. Preserve processing-error and rate-based performance fallback from 1.0.7.

## 1.0.7

- Fix denoise performance fallback falsely triggered by decoder lifetime drop totals at episode changes. Require at least three drops/second within a 1–10 second report interval, at least 12 drops and five seconds of settled, actively playing READY state. Recheck playback state before delayed fallback; keep processing-error fallback. Log the fallback reason.
- Defer general UI allocation optimizations and experimental-label removal at user request while prioritizing playback.

## 1.0.6

- Preserve the no-argument effect-processor factory builder constructor that Media3 1.8.0 accesses by reflection. Fix immediate denoise fallback in optimized release builds.
- Extend on-device diagnostics to exercise the actual optimized Media3 reflected builder, methods and graph factory before GPU pixel verification.

## 1.0.5

- Use fixed 104 × 38 dp detail actions, 11 sp captions, 14 dp icons and 6 dp gaps; all eight optional actions fit the HD content width. Keep labels to two lines and move the selected audio-track description into accessibility text and its existing checked dialog.
- Add optional experimental local video noise reduction, disabled by default: one five-sample, edge-aware GPU pass, no temporal frame history or server conversion. Limit to known SDR files up to 1080p, exclude live/HDR/encrypted/unknown formats, and retry without the filter on video-processing errors or substantial dropped frames.

## 1.0.4

- Scroll the detail page back to its top as soon as an action button gains focus from the cast row; respect existing scroll-animation preferences.

- Highlight focused primary detail actions with a bright surface, dark label/icon and a stronger outline; preserve accent-colored resting state and existing animation preferences.

- Keep right-arrow navigation inside the detail action row, including at its last button and under held/repeated keys. Preserve down-to-cast and existing left navigation, with or without Companion.

## 1.0.3

- Separate watch-later and Jellyfin favorites on details, with reversible membership labels, selected indicators and accessible action descriptions.
- Give every detail action its own scalable vector symbol. Watched actions offer the inverse watched/unwatched action, including after returning from playback.
- Remove queued movies after actual playback end or a playback-confirmed unwatched-to-watched transition; respect effective Companion status, retain episodes/series and previously watched films until replayed, and fence stale reports against explicit re-additions.

## 1.0.2

- Hand initial library focus to the first media card before enabling the letter shortcut; retain detail-return and alphabet-jump selection.
- Persist full-precache completion after metadata and artwork succeed; disable and dim manual preload until covers or the full precache are explicitly cleared. Incremental Companion updates remain enabled.
- Style dialog list rows before first layout, including newly attached/recycled genre entries, for consistent centered 13 sp labels.

## 1.0.1

- Restore the selected grid media ID, fallback index and saved viewport after returning from details. Fence stale restoration callbacks and preserve user navigation and paging/letter-jump focus.
- Bound focus/action zoom by clipping ancestors so outlines stay inside visible containers; retain zoom where space permits and existing animation switches.

## 1.0.0

- First 1.0 local release, consolidating the existing Lowfin client and optional Companion integration. Preserve existing signing identity, account/settings/cache data and stable TV channel IDs.
- Reuse per-control focus backgrounds across focus/accent changes while retaining exact colors, border dimensions, animations and navigation.
- Compare cast display fields directly instead of serializing both entire lists on each detail refresh; role, portrait, identity and ordering changes still refresh.
- Validate the schema-4-to-8 upgrade path for TVs still on 0.7.25, alongside existing migration, navigation, playback and Companion tests.
- Install the same release on TVs .128, .166 and .87; open Lowfin only on .128. GitHub publication remains paused.

## 0.10.29

- Skip writes for unchanged Android-TV preview/Watch Next programs by comparing actual provider values. Repair externally changed or missing rows; preserve channel IDs, dynamic label notifications and manual dismissal. Retain existing engagement times when no explicit engagement changed.
- Read the published artwork-byte count without waiting for image-cache maintenance/eviction locks.
- Add rate-limited slow remote-input diagnostics (queue age vs handler duration), without media names, entered text or credentials.

## 0.10.28

- Optional home library shelf with 16:9 library artwork, preferring Jellyfin Thumb then Backdrop; open the selected library directly. Reuse existing cached view metadata and image pipeline.
- Reorder every home shelf independently in Home settings using remote-operated move-up/down actions. Persist semantic row keys per account, retaining ordering across dynamic recommendation titles and temporarily hidden rows.
- Separate display positions from shelf identities throughout focus, scrolling, asynchronous updates and single-shelf descriptions; preserve existing default ordering when unconfigured.

## 0.10.27

- Center the existing stylized L behind the Lowfin wordmark in launcher icons and TV banners; refresh channel logos in place while preserving channel IDs.
- Remove opaque sender artwork frames and preserve alpha when decoding on-demand channel logos. Normal poster backgrounds and channel disk-cache exclusion remain unchanged.
- Hide the redundant Live TV caption below the sender logo in player controls.

## 0.10.26

- Add confirmed, account-scoped deletion of the entire library precache: metadata, artwork and derived lists. Preserve ratings, credentials, settings, watch-later entries and Companion rules/resume; fence late writers and restart cache workers safely.
- Record bounded private playback error diagnostics without URLs, tokens or exception messages. Log decoder initialization, buffering transitions and dropped-frame reports for stutter investigation; preserve playback/fallback behavior.

## 0.10.25

- Batch Companion decoration lookups in cached resume, library lists and launcher resume publication; preserve order, duplicates, account scopes and plugin-unavailable behavior.
- Skip hidden/unchanged status text updates; refresh sidebar status immediately on opening and release detached cache-status view references.
- Avoid placeholder resets and repeated chip text writes for warm/unchanged ratings. Keep stable rating geometry and fade only changed arriving values. No provider precache or cache-history reconciliation added.

## 0.10.24

- Center dialog titles, messages, list labels and action labels/groups. Keep input editing behavior.
- Balance content-sized dialogs with a 280 dp minimum width, 480 dp maximum width, 112 dp minimum content height and 70% maximum overall screen height, constrained further by available space. Long content scrolls with accessible buttons. Preserve transparency and the 0.10.23 optimizations.

## 0.10.23

- Size dialogs to their text and actions, with bounded width and wrap-content height. Long list/message/custom content scrolls within an 82% screen-height limit; keep title and buttons accessible.

- Avoid rebinding unchanged home shelves and restarting pending navigation; refresh nonvisual action metadata in place. Changed labels, progress, watched state and artwork still update normally.
- Memoize up to 256 artwork URL hashes without changing disk keys, image tags, account boundaries or image-cache budgets. Cache mixed-grid span lookups with normal RecyclerView invalidation.
- Reuse player end-time formatting by minute, locale and timezone; avoid rewriting unchanged transport/time labels. Preserve live duration, long videos, paused end time and seek previews.

## 0.10.22

- Compact shared Lowfin dialogs: narrower panels, smaller list rows and action padding; preserve wrapping and remote focus.
- Appearance: adjustable dialog background transparency from 0–100%, opaque by default, with remote-operated live preview and explicit Save/Cancel. Text and focus frames retain their opacity; no blur.

## 0.10.21

- Show recommendation/genre headings whenever their full height fits inside the home viewport, including while Next Up is focused. Hide only clipped headings; retain shelf geometry and navigation. Recheck on scroll, layout and recycled-holder binding.

## 0.10.20

- Keep recommendation/genre headings hidden until their shelf is selected, preserving layout and remote navigation.

- Reserve reusable, fixed-width rating slots on home genre/recommendation panels. Loading and missing scores no longer move the overview; source switches still apply. Warm memory scores appear immediately, cold provider requests remain debounced/on demand, with a short fade for arriving values. Detail ratings retain their compact wrapping behavior.
- Reuse rating chip views across focus changes instead of rebuilding icons and view hierarchies. Preserve animation switches and focus navigation.

## 0.10.19

- Home genre/recommendation information enters from below with a temporary rendering layer, respecting existing animation switches. Preserve row focus and selected media.
- Reuse a single GPU layer for static filtered backdrop and shades up to 1080p (bounded extra texture); larger displays retain normal rendering.
- Avoid redundant shelf viewport layout requests when navigating between rows in the same display mode. Retain scroll targets, paging, cached metadata and single-shelf behavior.

## 0.10.18

- Optional native smooth scrolling for home shelves and library grids under Appearance, off by default and respecting the UI animations master. Rapid remote input replaces the pending target; single-shelf genre/recommendation visibility is restored when scrolling settles. Existing paging, focus and row-end boundaries remain.
- Library header letter menu (#, A–Z) jumps across the complete scoped library using two count-only server queries, with cached-library fallback offline. Preserve first-medium focus when opening a library and focus the correct item on the target page.


## 0.10.17

- Reuse successfully enumerated device decoder capabilities for the process; temporary enumeration failures remain retryable. Keep per-playback account, source, audio, subtitle and HDR display checks fresh.
- Prepare the local video-cache index concurrently with metadata/stream negotiation after playback is requested. No media or next-episode downloads; disabled cache remains disabled. Keep start/rebuffer thresholds, memory limits, session reports, Companion ordering and live routing unchanged.
- Add phase timings for device-profile creation, data-source readiness and player construction to distinguish negotiation delays from buffering/decoder startup.


## 0.10.16

- Replace background compositions with eight new soft light-pool patterns for every existing accent color. Static GPU gradients and a shared 16 KiB noise tile reduce visible color banding without continuous animations or full-screen bitmap caches. Apply dithering to detail/home shading and player chrome gradients as well.
- Show Jellyfin character roles beneath cast names, hiding absent roles and clearing recycled labels. Preserve round portraits, person links and horizontal remote navigation.


## 0.10.15

- Playback settings: native HDR toggle, disabled by default. Negotiate HDR10 / HDR10+ / HLG only with compatible display and 10-bit decoder; preserve existing explicit server-fallback policy. Dolby Vision is not advertised. Live TV remains unchanged.
- Random movie opens All movies / Choose genre submenus; server and cached fallback both retain movie-only, library and genre filters. Existing title slot and detail navigation retained.


## 0.10.14

- Refresh Android TV channel logos once for the current Companion-style branding, retaining existing channel IDs and visibility. Retry failed logo writes rather than marking them complete.
- Read-only Scooby-Doo artwork diagnostics match tagged cache variants; filesystem modification times can also represent cache use and are not proof of downloads.


All Lowfin versions, starting with the first test version, **0.1.0**. Historical entries were reconstructed from the archived source READMEs and verification reports in `dist/`; no release dates have been inferred. Changes describe each version's implementation, not a guarantee that every TV or stream was tested.

## 0.10.13 — player timeline clearance and paused end time

- Move the seekbar and its time labels 12 dp upward relative to the bottom transport row, giving focused controls room without overlap.
- Keep the estimated finish time visible while paused and recalculate it from the current clock plus remaining media time using the existing 500 ms player UI tick.
- Extend the read-only on-device cache diagnostic to count persistent image files and bytes alongside metadata records.

## 0.10.12 — dialog labels and remote paging focus

- Remove the extra sidebar gap between Genres and Live TV.
- Reflow dialog action buttons into full-width rows when their translated labels cannot share a row; allow multiline labels and preserve original buttons, listeners and validation callbacks.
- Use unscaled, elevation-free focus styling with inset spacing for dialog actions and pagination buttons.
- Explicitly connect the final grid row to available page controls using Down, and return to the originating card with Up. Clamp horizontal/downward footer navigation and retain the existing conditional page visibility.

## 0.10.11 — playback settings, dialogs and pagination

- Remove the duplicate Intro/Outro master switch. Migrate its old effective state into the separate controls once, preserving existing disabled skipping and the season-premiere exception.
- Style all in-app dialogs with Lowfin panels, rounded accent borders, compact typography and remote focus styling while retaining native input, selection and dismissal behavior.
- Fit the person biography button to its label instead of stretching it across the information panel.
- Share conditional paging controls across paged screens. Request one lookahead item while retaining 60-item offsets; hide Previous on the first page and Next when no following item exists, including full final pages.

## 0.10.10 — consistent settings switches and accent arrows

- Use one accessible native TV switch style for boolean settings, providers, rating visibility/cache, genre pinning, library selection and Companion policy dialogs. Local changes retain focus without rebuilding the page.
- Add reversible per-channel publication switches. Disabling clears its preview/Watch Next cards; re-enabling restores them with the existing channel ID. Launcher visibility/approval remains controlled by Android/the launcher.
- Separate video-cache on/off from its size and restore the previous size when re-enabled.
- Draw More cards with a joined, rounded arrowhead in the active accent color; update existing cards when the accent changes.

## 0.10.9 — reduce startup and library work

- Persist a small, atomic artwork inventory instead of inspecting every cover file on every normal launch. Recount after the first upgrade, an interrupted cache mutation, corruption or an external directory change; debounce inventory writes while downloads are active.
- Share concurrent bitmap decoding for identical artwork. Keep recycled views protected from late image delivery and retain the existing low-memory image budget and sender-image policy.
- Omit chapter/media-source/stream payloads from library and begun-episode lists. Detailed metadata and playback still fetch the complete item; existing detailed cache fields are preserved.
- Add schema-8 indexes for case-insensitive library sorting, resume ordering, reverse membership and artwork retries. Preserve metadata, memberships, settings and Companion cursors during migration.
- Reuse immutable account-scope hashes and memoize decrypted provider keys in a bounded instance cache; encrypted preference changes/removals invalidate cached values, and disk storage remains encrypted.
- Write single metadata items transactionally, avoid rebuilding unchanged genre indexes, abandon cached home work after navigation, and remove formatter allocation from video-cache keys.

## 0.10.8 — preserve Companion-managed caches

- With a recognized incremental-cache plugin, keep an existing completed cache when the change cursor is missing or expires. Subscribe to the new server head without a full scan or requeuing all covers.
- Display a history-gap notice rather than claiming missed changes were verified. An explicit manual refresh remains available. Empty caches still receive their initial build; clients without the plugin retain periodic native refresh.
- Allow plugin changes during playback with a reduced artwork batch and background thread priority. Follow foreground activity lifecycle across the player; keep the cache settings status current.

## 0.10.7 — circular cast focus and horizontal navigation

- Replace the rectangular cast focus background with a thin accent ring around the portrait and an accent-colored name.
- Handle cast Left/Right before the detail-page sidebar shortcut. Keep focus inside the row at both ends and explicitly scroll to offscreen actors, including rapid key repeats.
- Add a 40-person remote-navigation regression test covering both ends and verifying the sidebar stays closed.

## 0.10.6 — cast and local filmography

- Add a virtualized cast row below title details with circular, density-scaled portraits and names. Actors and guest stars open a person page.
- Show Jellyfin biography, dates/locations where available, and paginated, clickable films/series visible to the current Jellyfin user. Cache person information and visited filmography pages locally; preserve navigation back to the person and originating cast row.
- Remove next-episode preloading completely, including its settings and background requests, at user request. Ordinary autoplay and video cache remain available.

## 0.10.5 — fixed detail-page title logos

- Display the Jellyfin film/series logo at the upper-right of details in a fixed 228 × 80 dp frame, fitting the entire image proportionally.
- Scale logo requests with display density so 4K UI does not enlarge a fixed 320-pixel source.
- Replace the text title only after the logo actually loads; retain readable text when metadata or an image is missing. Episodes can resolve their series logo from cached/server series metadata.

## 0.10.4 — companion-style branding and bounded next-episode preload

- Match the Companion's flat charcoal/white visual style with an editable two-part L and a lavender base; update square/adaptive icons and TV banners.
- Settings initially focus Home; returning from a settings page restores the parent category and scroll position.
- Optionally preload the next episode's direct file into the existing account-scoped video cache, default 8 MB / 30 seconds before outro or end, with configurable size/timing. No extra decoder or playback reports, no live/HLS/transcode prefetch, independent short-timeout requests, and current-stream/RAM/disk guards.
- Actual playback retains fresh Jellyfin and Companion negotiation; preloading reduces startup IO but does not promise a gapless decoder transition.

## 0.10.3 — responsive list navigation and source details

- Run library-card snapshot comparisons on AndroidX's shared background executor; home shelves compare only visible metadata, without serializing full playback DTOs. Apply individual updates instead of rebuilding the whole library grid, preserving stable IDs and explicit shelf focus restoration.
- Give foreground navigation two workers, independent of general home-content requests; separate cached season reads from network refreshes and discard queued work for abandoned screens.
- Show original video dimensions and file size in film/episode details, with separate compact lines for multiple versions and no fabricated values when metadata is missing.
- Record screen construction timings without item names or credentials for subsequent performance diagnosis.

## 0.10.2 — persistent cache checkpoints

- Commit the Companion change cursor after the full metadata and library-membership baseline, before the cover pass. Interrupted cover work persists independently and resumes without downloading all metadata again.
- Refuse to start a Companion baseline without a valid journal anchor; back off repeated failed full-sync attempts.
- Load live-TV artwork directly from Jellyfin on demand, using only the bounded in-memory image cache; exclude sender logos from full and incremental cover queues.
- Do not rewrite cached home snapshots on unchanged journal responses.

## 0.10.1 — faster TV-card startup and cache access

- Playback/resume launcher cards open the player immediately, avoiding home-page requests and a duplicate item-metadata fetch. The player resolves the current native/Companion resume position; open/detail links and account validation are preserved.
- Enable SQLite WAL for cached metadata readers during background writes, batch Companion resume reconciliation into one transaction, and move artwork inventory off the bitmap-decoding workers at background priority.
- Keep a normal home screen when returning from launcher playback. Add separate metadata, Companion, playback-negotiation and first-video-frame timings.
- Use proper German/English quantity forms: “1 Wiedergabe” / “2 Wiedergaben”, “1 play” / “2 plays”.

## 0.10.0 — Companion 0.3 server API providers

- Jellyfin dashboard provider credentials and global/selected-user sharing, enforced on authenticated server endpoints. Clients receive ratings and their own completed-play statistics without receiving keys.
- Retain provider/rating toggles while hiding superseded local keys/TXT/Tracearr controls and incremental-cache scheduling options by capability.
- Keep default-on local persistent rating cache; a new switch bypasses memory/disk reads and writes without deleting stored values.
- Show completed-play counts as “N Wiedergaben” / “N plays”. Simplify the plugin banner.

## 0.9.0 — Companion-driven incremental precache

- Optional Companion 0.2.0 metadata-change journal with authenticated long polling, persistent per-device cursors and small native DTO batches. Compatible clients use incremental updates instead of the normal timed full-library scan; initial/manual and safe recovery scans remain.
- Atomic cache metadata/membership/cursor updates and persistent changed-artwork queue with retry backoff. Existing completion rules, playback reporting, user scoping and native fallback are preserved.
- Reconcile complete Live-TV lists to remove obsolete local sender IDs. Diagnosis on TV .128 found exactly 54 obsolete sender entries returning HTTP 404; current M3U sender logos remained available.
- Prioritize cached home content before automatic full precache; stop touching every cached image timestamp during background checks. Add first-image and cached-shelf timing diagnostics.
- No GitHub publication. Plugin/server restart still requires user deployment; APKs are local release artifacts.

## Unreleased


- GitHub Actions builds on `main` pushes and manual runs: Android 11 unit/UI tests, Android Lint, a signed release APK (R8 and resource shrinking, debug disabled), checksums and build metadata.
- Downloadable APK and diagnostic artifacts, with the existing signing certificate when the repository signing secret is configured.
- Dedicated complete version history linked from both documentation languages.
- Release APKs preserve the existing signing certificate for installation updates; release lint is checked alongside debug lint.

## 0.8.1

- Makes Companion an explicit opt-in: unconfigured media use native Jellyfin playback with no independent resume. A deliberately enabled long-video rule remains supported; choosing Jellyfin default disables Companion for that item.
- Requests independent episode resume lists in the selected server-side library scope instead of mixing a global list through cached membership. Rejects mismatched scopes and uses only native scoped results with older plugins.
- Companion 0.1.1 respects Jellyfin home-library exclusions without deleting progress, while allowing resume in the explicitly opened library/detail page. Rules remain shared across devices using the same Jellyfin account.
- Refreshes Companion capability detection when reopening Lowfin. Migrates the scoped library cache to schema 6 while keeping existing metadata/settings.

## 0.8.0

- Adds optional Lowfin Companion 0.1.0 for Jellyfin 12.2.x: user/media completion rules (native default, precise custom percentage, remaining time, never automatically watched) and a general rule for long videos. Leaves Jellyfin’s global thresholds unchanged.
- Adds independent persistent resume with account-scoped local cache, durable coalesced playback outbox, sequence/lease ordering and explicit watched/reset fences. Keeps normal Jellyfin session reporting through a scoped gateway; Live TV and servers without the plugin keep existing playback routes.
- Integrates remote-friendly Smart Completion controls into movie/episode details and general long-video controls into Playback settings. Hides these options when the plugin is absent. Watched items with saved progress remain resumable in continue shelves and TV channels; Restart and episode autoplay explicitly start at zero.
- Packages the plugin DLL with a portable local meta.json, new Companion artwork, ZIP, checksums and installation/compatibility notes. Documents residual external-reset and ambiguous server-crash limitations rather than claiming cross-database exactly-once delivery.
- Prevents delayed initial Continue-row loading from stealing focus after the user explicitly navigates to another home shelf.

## 0.7.25

- Renames the provider settings page to API and embeds all Tracearr controls directly under a Tracearr heading. Removes the separate Tracearr page; common TXT import and recreation controls appear once.

## 0.7.24

- Adds optional Tracearr Public API v2 statistics scoped to the selected Jellyfin server and the authenticated user. Counts stopped, watched resume chains; series counts complete sets of currently available episodes, including specials. Server-side completion rules apply. Unknown or incomplete histories are never displayed as zero.
- Extends the existing API-key TXT template with TRACEARR_URL, TRACEARR_API_KEY and optional TRACEARR_SERVER_ID. Adds explicit file recreation and manual populated-field updates; blank fields preserve saved values and existing download files are never overwritten. API credentials remain encrypted.

## 0.7.23

- Adds Go to episode to episode long-press actions. Playback launched from an episode detail page returns the latest successfully opened episode, including autoplay; direct home/library playback keeps its original screen. Preserves the detail page navigation history.

## 0.7.22

- Adds a compact native Jellyfin PlayCount field to movie, episode and series detail pages. Labels it as plays including unfinished playback, rather than completed viewings; missing counts are shown as unavailable.

## 0.7.21

- Tunes background gradients to a middle strength between the initial subtle palette and the stronger palette, retaining visible hue with quieter contrast.

## 0.7.20

- Strengthens accent background gradients with approximately three times the peak tint and greater contrast between gradient stops, while keeping text surfaces dark.

## 0.7.19

- Adds eight subtle static background gradients per accent hue. Login, library and settings backgrounds share a randomly selected composition that updates together with the accent and remains stable while browsing.

## 0.7.18

- Reorganizes settings into dedicated home, genres, appearance, playback, live TV, library/cache, TV launcher, ratings, and account/language pages. Removes duplicated animation controls and moves preload options out of appearance. Preserves settings focus and submenu Back navigation.

## 0.7.17

- Keeps focus on the changed settings control and preserves its page scroll position when settings views refresh, including appearance, playback, genres and ratings.
- Adds an optional playback setting to keep intros in episode 1 of each season. Manual skipping remains available; outros and subsequent episodes retain the configured automatic skipping behavior.
- Keyboard confirmation advances through the login fields while keeping the keyboard open; the final field dismisses it and runs its action. Handles Next and numeric Enter as well as Done, and consumes held Enter repeats.
- Reduces the animated loading bar width from 240dp to 160dp for media, live TV and native trailers.
- Remote OK immediately pauses playback and opens hidden controls. Arrow keys first reveal hidden controls; their entire opening press, held repeats and key release are consumed before seeking/navigation is allowed on the next press. Applies to media and native trailers.

## 0.7.16

- Holding OK on a season poster opens a watched/unwatched action for the whole season. Successful server changes update the season card and locally cached episodes, including clearing resume positions.
- Preserves existing TV channel identities even when an OEM provider returns zero from an update; recovers channels by their stable provider key when a saved ID is missing, preserving launcher activation.
- Notifies channel collection, individual channel and channel-program observers after publishing, including updates with unchanged recommendation titles. Existing recommendation rows receive new titles and cards without recreation.
- Marking media watched/unwatched from detail pages or the long-press menu requests a fresh server recommendation/channel refresh.
- Adds a regression covering changed recommendations, missing saved IDs, zero update counts and preserved visibility. Updates the sidebar test for the removed Pin genres button.

## 0.7.15

- Removes the redundant Pin genres entry from the sidebar. Genre row configuration remains available in Settings.

## 0.7.14

- The bottom-left status distinguishes an initial cache build from refreshing an existing library. Refreshing displays that cached data remains available rather than a counter restarting from zero. Detailed per-run progress remains available in Settings and the background notification.

## 0.7.13

- Separates foreground disk reads/bitmap decoding from network downloads. Removes the image-cache monitor held across HTTP requests; slow preload requests no longer serialize all cover loads. Concurrent requests for the same artwork share one download.
- Preload and UI use matching density-aware artwork sizes. Existing 240px HD covers remain usable, and cache files survive loader/app restarts. Image requests have dedicated, bounded timeouts and do not share cancellation with playback/API calls.
- Restores saved cache status or existing artwork counts on startup instead of falsely displaying an empty cache. Automatic metadata refresh remains controlled by the configured interval; existing cache files are reused.
- Uses allocation-light image-key encoding, bounded bitmap decoding, atomic image writes, and cache-clear generation checks. At a full artwork budget, visible misses evict least-recently-used artwork while preload avoids churning the cache.
- Verified with 185 passing tests, debug/release lint and a signed release build. Installed as an update on the authorized Android 11 HD TV; 27,898 artwork files (524 MB) remained present after the update and a full app-process restart, and cached library pages rendered their covers.

## 0.7.12

- Automatic next-episode playback keeps player controls and title/logo overlays hidden during opening and startup. Remote input still opens controls normally; manually starting media or choosing Next episode retains the existing startup controls.
- Returning from buffering no longer reopens the player controls automatically.
- Home recommendation/pinned-genre focus details show up to three genres inline with the existing metadata, without adding height. Continue watching and Next up remain unchanged. Detail genres use the same compact separator and stay on one line; empty/duplicate names are filtered.
- Verified with 180 passing unit/UI tests, debug/release lint and a signed release build. Genre layout was checked in the HD preview; physical-TV autoplay verification remains pending.

## 0.7.11

- Sidebar gray background now follows keyboard/remote focus exclusively. The current page retains its brighter label but no longer displays a second persistent selection box after the cursor moves.
- Completing a trailer closes its player automatically and returns to the existing detail page.
- Playback position/duration and estimated end time above the seekbar use larger 15sp text, with a brighter end-time label.
- Seekbar focus uses an accent outline and tinted background, a larger white marker and a thicker brighter track. Shared by media and native trailer playback; focusing the bar does not seek.
- Regression tests cover exclusive sidebar highlighting and seekbar focus appearance/restoration. Verified with 180 passing unit/UI tests, debug/release lint and a signed release build; physical-TV verification remains pending.

## 0.7.10

- Native Media3 trailer playback replaces the YouTube WebView, with Lowfin transport, buffering animation, hardware-friendly video preference and a 720p ceiling. Public stream resolution follows Moonfin's approach; no Jellyfin credentials are sent to YouTube. YouTube availability and physical-TV playback still require device verification.
- Automatically creates an explanatory `Download/Lowfin-API-Keys.txt` only when no rating keys are configured. Imports stable UTF-8 contents into encrypted storage and deletes the file after verification; empty providers are optional. Includes manual TXT selection, failed-deletion recovery and Android 11 scoped-storage support.
- Sidebar focus no longer scales beyond its card frame, native pressed overlays are disabled, and the dimming overlay disappears immediately when closing the drawer.
- Enter/Done closes the keyboard; search, password login, season jump and key/cache dialogs also run their corresponding action.
- Verified with 178 passing unit/UI tests, debug/release lint and a signed release build. Physical-TV trailer availability remains unverified.

## 0.7.9

- Explicitly notify the Android TV channel collection after publishing changed headings and their recommendation cards, so channel-list observers can refresh cached “Because you watched” names.
- Persist the last notified label per existing channel ID; upgrades also refresh already-correct provider names once. Unchanged titles do not trigger additional collection notifications. Channel IDs, launcher visibility and ordering are preserved.
- Regression tests cover Bad Boys → Asterix with a cached launcher heading and unchanged recommendation item identity, plus the one-time upgrade refresh. Verified with 164 unit/UI tests, debug/release lint and a signed release build; the physical launcher still needs verification.

## 0.7.8

- Holding remote OK/Enter on home or library cards opens Continue, Restart, watched/unwatched, Watch later and favorite actions, plus series details for shows and episodes. Release after a hold never launches the normal click action; remotes without repeat events are supported.
- Continue on a show resolves Jellyfin Next Up, including resumable episodes; Restart resolves its first available episode. Watched and favorite actions update Jellyfin and the local metadata cache.
- Separate persistent device-local Watch later list for each account/server, accessible from the sidebar, independent of server favorites.
- Optional smoother screen/shelf/drawer transitions, focus scaling and saved-action feedback. Settings → Home screen and appearance provides a separate smooth-navigation switch and the UI animation master switch; previous animation preferences are retained.
- English and German translations for new actions/settings. Verified with 162 unit/UI tests, debug/release lint and a signed release build. Physical remote/TV performance still needs device verification.

## 0.7.7

- Continue Watching movies use 16:9 Thumb/backdrop artwork, matching episodes and the More card. Library movie posters retain 2:3 frames.
- Optional 2160p playback limit with the corresponding 3840-pixel width; density-aware artwork requests improve sharpness on higher-density TV displays. UI dimensions continue to use Android dp/sp scaling.
- Additional optional streaming bitrate limits: 16, 25, 40 and 60 Mbit/s. Existing HD defaults are preserved.
- Verified with 153 unit/UI tests, debug/release lint and a signed release build; physical 4K TV playback has not been verified.

## 0.7.6

- Live TV identity displays the Jellyfin channel logo, preferring Logo artwork and falling back to Primary. Missing images retain the channel name.
- Player channel picker shows proportional logos in fixed-size frames beside channel names and numbers, with recycled rows and cached artwork.

## 0.7.5

- Series logo and episode identity appear at top left, with a fixed proportional logo frame and text fallback.
- Position/duration above the timeline on the left; estimated finish clock time on the right while playing.
- Options precedes Next episode at the far right beneath the timeline.
- Live TV displays elapsed viewing time instead of a media position.

## 0.7.4

- Home entry focuses the first Continue watching item instead of its More card after asynchronous loading.
- Bouncing buffering animation also covers opening streams.
- Player options sit below the timeline beside transport controls. Up/down navigation selects timeline/play; Menu opens options.
- Held remote seeking previews an accelerating target starting at 5 seconds and commits once on release; Back cancels the preview.
- Series-only library tab lists resumable episodes scoped to that library, with direct resume and account-scoped offline caching.

## 0.7.3

- Animated slim buffering bar with a softly bouncing accent gradient. Animation stops when hidden, detached or the player leaves the foreground. Error messages remain readable.

## 0.7.2

- Left navigation moves between detail actions; the sidebar opens only at the first action.
- Slightly richer curated accent colors and backdrop saturation.
- Backdrop artwork and gradients fill the screen, with padding applied to foreground content.

## 0.7.1

- Detail actions share a compact horizontal row with consistent button sizing and remote scrolling.
- Embedded audio can be selected before playback, including the correct media version and file stream index. Direct playback applies the selection locally after tracks are discovered.

## 0.7.0

- English UI by default regardless of TV language, with a persistent German option in Settings and before sign-in. Includes navigation, dialogs, player, trailers, errors and TV channel names.
- Separate genre-row settings: account-scoped individual genre toggles, overall visibility and single-focused-row view.
- Home/appearance settings for Continue watching and Next up visibility, 0–5 recommendation rows, poster columns, roulette duration, fixed/random muted accents and foreground-time color intervals.
- Automatic library preloading and refresh interval settings.
- Advanced playback settings for separate intro/outro automation, default subtitles, audio language, seek step, controls timeout and separate adaptive VOD/live RAM ceilings.
- Disabled rows are skipped by remote navigation; recommendation limits also apply to Android TV cards.
- Prevented late ratings callbacks from overwriting a newer ratings rendering.

## 0.6.0

- Removed the Lowfin prefix from Android TV row names while preserving channel IDs.
- Separate account-scoped movie/show library selection for TV channels, mixing cards across selected libraries.
- Redesigned movie roulette with real library titles, a highlighted winner and automatic detail-page opening.
- Curated pool of twelve muted accent colors: new selection at startup and after an hour of foreground use; background time is excluded.
- Permanent on-demand OMDb/MDBList/TMDB ratings storage, including previously loaded seven-day entries. Changing visible sources reuses stored values; library preloading does not fetch ratings.

## 0.5.3

- Restricted watched-movie recommendations to movie baselines and movie results; discarded actor, director and liked-only categories, including stale cached data.
- Added five dynamically named watched-movie Android TV channels whose cards open movie details.
- Applied the genre-style focused row, backdrop, description and ratings view to recommendations.
- Corrected held-key row navigation, Android automatic scrolling, asynchronous row updates and off-screen poster focus.
- Standard TV channel order: Continue watching, Next up, Movies, Shows. Migrated older reversed channel roles without removing IDs or visibility settings.

## 0.5.2

- Added five cached **Because you watched** movie rows below Next up and above pinned genres.
- Used Jellyfin watched-movie recommendations, with similar-movie fallback for other watched titles; unavailable rows are skipped.
- Added 2:3 recommendation cards, detail-page links and Show more grids.
- New LowFin logo: a subtle large L behind the readable wordmark, with normal/adaptive launcher icons, TV banners, PNGs and editable SVG sources.

## 0.5.1

- Smaller genre posters and descriptions/ratings constrained to the HD display.
- Repaired navigation to off-screen cards and the last genre row while retaining card position.
- Disabled default and forced subtitles at playback start; switched live subtitles and audio locally without reopening the live session.
- Stabilized the adaptive buffer's refill target and memory reserve; prevented duplicate reconnect watchdog timers.

## 0.5.0

- Added a live-only stream resolver following Moonfin's opened HTTP source, direct stream and active Jellyfin route preference.
- Explicitly opened unopened live sources, handled reverse-proxy paths and retained negotiated sessions during route fallback; authentication stays scoped to the configured server.
- Bundled Jellyfin's FFmpeg audio decoder for live fallback, including MP2, with local stereo downmix; allowed TS non-IDR entry frames.
- Added encrypted OMDb, MDBList and TMDB API-key settings and individually selectable rating sources.
- Added compact rating badges to genre focus views and details, with on-demand provider lookups and the original seven-day cache.
- Added an exit confirmation; removed the redundant menu button and placed Random movie/Live TV above libraries in the sidebar.

## 0.4.2

- Excluded continuous live streams from the seekable disk cache and shortened live startup/rebuffer thresholds.
- Added a bounded reconnect watchdog and a manual Reconnect stream action; continuous streams no longer appear to end at a known file size.
- Put Show more cards at each row's end; removed Recently added from the app home screen.
- Kept Random movie and Pin genres in the sidebar only, retaining the selected movie-library scope.
- Extended roulette to at least four seconds with a short muted winner animation and automatic detail navigation; cancellation prevents opening.

## 0.4.1

- Revised Back behavior through menus/details to Home before exiting.
- Corrected sidebar focus, Up/Down boundaries and restoration to the previous card when closing the menu.
- Preserved horizontal card selection when changing rows, bounded by the destination row's size.
- Increased movie/show/season grids to eight columns for smaller posters; episode images span two columns.
- Enforced 2:3 poster and 16:9 thumbnail/channel frames; centered cropping for mismatched artwork and contained channel logos.

## 0.4.0

- Collapsible sidebar opens from the first card with Left and closes with Right/Back, with optional lightweight animation.
- Sidebar movie roulette automatically opens the selected film's details.
- Added account-scoped pinned genre rows, with Horror/Thriller defaults and cached random movie selections.
- Added the single-focused-row home view with movie backdrop, metadata and description; bounded horizontal navigation stays in the current row.
- Standardized home movie/show posters and episode thumbnails, including cropped old 4:3 episode images.
- Added in-app YouTube trailers via the official iframe player in Android System WebView, with transport controls and embedding/error handling.

## 0.3.1

- Fixed library-loading stalls and frozen Settings by moving database reads off the UI thread.
- Backfilled the genre index in batches of at most 50 titles after database upgrades, retaining existing metadata, memberships and sign-in.
- Separated library navigation, cache queries and slower content/server work so the sidebar can load independently.
- Added regression checks for incremental indexing, locked database/settings access and busy content workers.

## 0.3.0

- Redesigned lightweight player controls with timeline, pause, seek, next episode, channel selection and an Options menu.
- Added configurable disk video caching with a 500 MB default and adaptive RAM ceilings of 48 MB for VOD and 32 MB for live TV, retaining memory reserve.
- Added partially desaturated, darkened movie/show detail backdrops.
- Made season posters smaller, with six columns and direct season-number navigation.
- Cropped old 4:3 episode artwork into consistent 16:9 thumbnails.
- Added global genre browsing/filtering and movie roulette scoped to the current movie library, with offline cached fallback and automatic detail navigation.

## 0.2.0

- Reworked compact detail pages with poster, metadata, playback/status actions and overview.
- Corrected 2:3 poster presentation and native episode-artwork proportions; preferred genuine thumbnails/backdrops for landscape home cards.
- Added a virtualized season poster grid with four seasons per row, numerical ordering and direct season-number selection.
- Extended artwork preloading to thumbnail/backdrop variants used by home cards.

## 0.1.0 — First test version

- Native Java/Media3 Android TV client for low-end HD devices, with monochrome styling and small optional focus animations.
- Username/password sign-in, encrypted session-token persistence and automatic sign-in.
- Library/sidebar navigation, movies, shows, episodes, large season collections, global search, favorites and watched/unwatched actions.
- Continue watching, Next up and Recently added home rows with progress and playback links.
- Direct local playback, configurable resolution/bitrate, optional server fallback, stereo downmix, track/version/chapter selection and automatic next episodes.
- Channel-only Live TV browsing, automatic stream opening, player channel changes, optional program/recording actions and stream cleanup.
- Intro/outro auto-skip through Jellyfin MediaSegments, with manual controls for other segments.
- Persistent account/server-scoped SQLite metadata and artwork preloading, bounded storage and background refresh.
- Android TV Continue watching, Next up, movie/show channels and Watch Next integration with account-scoped links and local artwork.
- Normal/adaptive launcher icons, TV banner and editable design sources.

- Cache diagnostics: keep the latest 50 Companion metadata deliveries (local receipt time, item title/type and series/episode identifiers) atomically with the cache cursor. Ordinary navigation and empty polls do not alter this audit. Historical item save timestamps do not prove a Companion delivery; server event timestamps are not exposed by Companion 0.3.
