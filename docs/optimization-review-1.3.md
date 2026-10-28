# Lowfin 1.3 startup optimization review

Local release name: **1.3**, Android versionCode **108**. This supersedes the unpublished 1.21 / 107 build. No GitHub publication is part of this change.

## Evidence and scope

An earlier device capture recorded fresh Next Up after 1,061 ms and Continue Watching after 3,707 ms in one opening. This is not a measurement of the reported ten-second case and does not establish its cause. The existing timing excluded time waiting for the home executor.

Inspection found synchronous SQLite persistence before presenting both network results, plus a forced capability refresh in session validation competing with the normal Companion discovery used by resume reconciliation. Capability discovery is synchronized and can spend up to five seconds in its HTTP call. Forcing a second probe could serialize another request after an initial probe.

## Changes

- Session validation uses the normal shared capability discovery. The first discovery in each Companion instance still contacts the server; ordinary subsequent calls reuse its bounded discovery cache. Explicit configuration refreshes retain forced discovery.
- Fresh Next Up results reach the UI before metadata and shelf snapshots are written on the existing cache executor.
- Continue Watching still writes native metadata before Companion reconciliation. This ordering is required for current Jellyfin user data, manual watched changes, exclusions and persistent resume semantics. Its final shelf snapshot is deferred until after UI delivery.
- Launcher publication follows persistence. Existing account and screen-generation guards remain in place for UI delivery and deferred persistence.
- Fresh-shelf diagnostics include executor queue, network and reconciliation time. They contain counts and elapsed timings, not media names or authentication information.

## Boundaries

No playback, timeout, Companion rule, full-cache refresh or UI architecture changes. The server and Companion remain authoritative; their responses are not bypassed to display a falsely fresh resume list. Device speedup has not yet been measured for this build.

## Regression coverage

The new concurrent-discovery regression verifies that simultaneous session/resume-style probes share one capability request and that an explicit forced refresh still detects plugin removal. Existing resume tests cover native ordering/current metadata, missing items, exclusions, library scope and manual-action fences.

## Additional conservative optimizations

Common same-type integer comparisons in metadata snapshots now compare values directly instead of creating two decimal strings. Mixed numeric types still use the existing normalization; floating-point and invalid-number behavior is unchanged. Regressions cover large integers, signed boundaries, mixed types and non-finite values. Focus/entry animations share their immutable deceleration interpolator, and focus events read animation switches once. Animation settings are not cached across events, so changes still take effect immediately. These changes reduce small repeated allocations; no independent FPS gain is claimed.

## Follow-up review

Reviewed image disk/network/decode queues, rating memory/pending paths, home-row rebinding/diffs and launcher refresh work. The image loader already separates disk/decode from downloads and checks recycled-view identity. Ratings UI peeks do not read disk or start provider work. Home rows already skip unchanged presentation and launcher startup refresh already avoids fetching the home shelves twice. No additional broad rewrite of these paths was justified by this review. Slow server responses, image misses and workload-dependent queue/lock contention remain measurement targets rather than eliminated possibilities.

## Measured local resume bottleneck

On .128, initial fresh shelves measured 976 ms for Next Up and 13,306 ms for Continue Watching; the latter included 607 ms for Jellyfin and 12,689 ms after the response. A subsequent instrumented launch measured 6,344 ms in `Library.resumable`, 42 ms in the Companion request, 303 ms merging states and 56 ms final reconciliation. Thus the local candidate lookup, including database-handle acquisition and query work, is a demonstrated bottleneck; the optional server must not be blamed for that local delay.

The cross-table OR over the full items/companion join is replaced by an ID candidate set: native resume candidates from the existing partial index UNION managed resume IDs from the state table. Full metadata is then looked up by primary key and receives the same visibility/scope/sort/limit/offset/filter logic. No migration, cache deletion or global Jellyfin setting change. Differential tests compare old/new results across plugin enabled/disabled, scopes, offsets, hidden/inactive/managed states and orphan states; a query-plan test checks the existing resume index and metadata primary-key access.

A manual navigation trace retained 11.72 seconds: longest recorded UI doFrame 74.537 ms; longest UI measure 27.392 ms and postAndWait 38.837 ms. It shows moderate render/layout stalls but no second-long pause in that retained segment. Aggregate gfxinfo counters are not a controlled FPS measurement. Raw traces and screenshots remain private in `.tools`.

## Fast-scroll focus guard

LibraryGrid only enables its shortcut guard when a letter button is attached. It permits a distinct initial Up from the top row after a 180 ms vertical-key gap, blocks repeated/rapid escapes to that button and restores the originating attached card when pressing Down on the button. The test exercises real focus search and geometry, including ViewRoot's final directional stage which Robolectric does not dispatch automatically. Other media grids retain their prior focus search.

## Follow-up: helper monitor contention

The candidate-set build still measured 4,807 ms in one foreground lookup versus 823 ms in a concurrent launcher lookup. The query change alone did not eliminate startup delay (6,762 ms foreground resume). Library serializes write batches on its own monitor; SQLiteOpenHelper also acquires that same monitor when obtaining the already open database handle. Thus readers could wait behind metadata comparison/write batches even though the database uses WAL.

Library now publishes only fully initialized handles returned by SQLiteOpenHelper, and reuses an open handle for reads. SQLiteDatabase remains responsible for connection pooling and transactions. Opening/migrations and writer serialization are unchanged; close clears the reference and reopening still goes through the helper. A regression holds the writer/helper monitor on another thread and verifies that an already-open read can complete before that monitor is released, then verifies close/reopen returns a new handle with persisted data intact.

The first on-device fast-scroll test still reached the header despite the candidate guard: Android's global post-dispatch focus fallback was not reliably routed through the RecyclerView override. The final grid handles vertical library keys through RecyclerView's own focus search before that global fallback, so the layout manager can expose offscreen rows. Device confirmation below is required; an isolated unit test alone was insufficient evidence.

After the read-handle change, a device start recorded fresh Next Up at 1,519 ms and Continue Watching at 2,370 ms. The foreground candidate stage was 329 ms (concurrent launcher: 76 ms), versus 4,807/823 ms in the preceding candidate-only build and 6,344 ms before either change. Native response and write times also vary. These are separate single launches under uncontrolled TV/server load, not a controlled benchmark or a guaranteed startup limit.

## Final validation and device confirmation

All 488 unit/UI tests, release lint and R8 release build passed; original certificate and all four native decoder ABIs verified. Release 1.3 / 108 installed and opened on .128 only; APK and checksum copied to Desktop. No GitHub push or Companion change.

Final-build device start: fresh Next Up 1,435 ms, Continue Watching 1,440 ms; foreground candidate lookup 107 ms. This is another uncontrolled single launch, not a startup guarantee. Fast Down/Up navigation from column 3 returned to column 3 at the first media row; screenshot confirms the letter button did not acquire focus. A deliberate Up after a pause still reached the letter button.

Final manual checks: Down from the deliberately selected letter shortcut returned to column 3; opening the second-row film and pressing Back restored that same film/column. Lowfin was then returned to Home and left running.

## Additional review: launcher artwork

HomeChannels previously called Images.fetch for each program even when the same image appeared in multiple recommendation/base rows and Watch Next. A successful hit repeated file checks; a missing/broken cover could repeat its failed request for every duplicate card during the same publication.

ChannelArtwork is a temporary resolver owned by one publishNow call, keyed by the actual account-scoped Images image key, rather than only a media ID. Successful and fallback results are reused within that publication. The next publication retries failures. A cover-cache generation read under the existing image mutation lock invalidates the temporary resolver when the user clears covers (and prevents observing a half-cleared directory), and image-tag changes produce separate keys. No extra persistent artwork cache, metadata cache or retry timeout was introduced. Publisher diagnostics report resolutions/reuses, without item IDs, URLs or credentials.

Tests exercise 30 duplicates after a failed lookup (one request), successful retry in the next publication, reuse of persisted files, changed tags, deletion/refetch within a publication and no-image media. A provider integration test repeats a missing cover across base channels, all five recommendation channels and Watch Next, verifying one request per publication while all channels/programs remain present.

Images.cached also keeps the result of its first file-type check instead of repeating the same check before its length validation. The optional old HD-file fallback, empty-file rejection and foreground access timestamp policy are retained. These are operation-count reductions, not a claimed FPS improvement.

Additional-round validation: all 492 tests, release lint and signed R8 build passed. Version remains 1.3 / 108; Companion remains 0.7.0 and was not changed. Installed/opened only on .128; Desktop APK/checksum replaced. No push/release. The device recorded four publications with 144 artwork resolutions and 48 reused results each (192 card lookups total), confirming fewer repeated lookups, not 48 avoided network downloads. Fresh Next Up/Continue Watching measured 1,390/2,287 ms in that single launch; no independent FPS/startup gain is attributed to the artwork change.

## Optional focus pulse and banner alignment

FocusBackground extends the existing owned GradientDrawable backgrounds. It changes only the focused outline brightness within the current accent hue, without layout, view scaling, blur, bitmap or layer allocation. It schedules at most one tick every 50 ms per visible focused outline (2.4-second period), and starts no work by default. It checks the pulse setting, global UI animations, focus, attachment, window focus and local visibility. Stopping restores the original stroke; drawable window-focus state changes invalidate the ring so it resumes after a dialog closes. Banner/actor rings explicitly follow their owning focus target. The switch uses the existing 8 dp separation and changes preferences without rebuilding the page. English/German resources are provided.

The banner cover uses a 0.94-to-1 symmetric scale and opacity reveal with zero X/Y translation. One shared layout listener maintains the center pivot after resizing, including pre-layout binding. New item/image tags trigger the reveal; metadata-only updates do not replay it. Unchanged banner text no longer calls setText; a UI regression verifies zero text writes for unchanged data and a correct description update when metadata changes.

Ratings are aligned to the description's actual padded left/right edges; the right margin sums the individually rounded density-scaled components to avoid a one-pixel HD mismatch. Banner-only badges use 9 sp text, slightly smaller icons/padding and the original provider names and values, without scale transforms. HD/4K tests verify all eight fixed slots in one row, no distortion and no change to screensaver sizes.

The pulse intentionally remains off by default to preserve the existing idle render workload on low-end devices. Enabling a continuous visual effect necessarily schedules drawing; no performance gain is attributed to that feature.

Pulse/banner validation: all 499 tests, release lint and signed R8 build passed for unchanged version 1.3 / 108. Installed/opened on .128 only; Desktop APK/checksum updated. Banner screenshot confirms labeled scores remain on one row within the description column. On-device pulse samples changed from RGB (215,181,137) to (188,158,119), matching the current accent track RGB (220,185,140) at different brightnesses. The optional effect produced 94 rendered frames over 4.928 s; after restoring the original off setting, the static settings screen recorded zero frames over 3.136 s. This checks animation gating, not general navigation FPS. The user confirmed the effect works. No push/release or Companion change.


## Renderer follow-up on TV .128

A private gfx/view/sched trace of the active home screen showed 697 main-thread draw sections totaling about 17.1 s; about 16.9 s was postAndWait. View draw recording itself totaled about 107 ms. RenderThread sections included GPU/buffer waits. This points to renderer/back-pressure in this sample, rather than heavy main-thread metadata work. The cumulative GPU percentile buckets from this TV include implausible 4,950 ms samples and are not used as an FPS measurement. The recording was observational, without replaying identical remote input.

Home/detail already own a static backdrop/shade hardware layer at display sizes up to 1080p. Put the opaque ambient gradient into that existing layer instead of drawing it on the root beneath the cached layer on every animation frame. Root keeps a cheap solid fallback. All original colors, shade ordering, eight variants and accent bindings remain; above 1080p the existing normal-rendering fallback remains. No additional layer/bitmap allocation is introduced. UI integration checks ownership and unchanged-accent reuse.

Images.show now applies a bitmap memory hit directly. It no longer temporarily removes an existing drawable before applying that hit. Cold asynchronous loads and missing images still clear the old drawable; tag checks, download coalescing, persistence and per-view badges are unchanged. A regression checks zero intermediate clears, exact bitmap reuse, no second request, and clearing for a subsequent missing image.

Validation: 500 tests, release lint, localization coverage (473 English/German entries) and signed R8 release passed. Installed and launched only on .128. Post-install screenshot shows ambient gradient, banner scores and focus ring correctly; no controlled before/after FPS comparison is claimed. Desktop APK/checksum updated. Versions stay 1.3 / 108 and Companion 0.7.0; no GitHub push.
