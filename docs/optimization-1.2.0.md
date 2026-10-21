# Lowfin 1.2.0 — measured work reduction and text-free startup

## Audit scope and evidence

Reviewed startup/account initialization and executors, home/library/detail navigation and focus restoration, metadata/Companion persistence, incremental and full precache, image/network/decode queues and eviction, Android TV channel publishing, rating/statistic caches, player preparation, disk video cache, adaptive memory buffering, transport ticks, animation visibility and native dialogs. Work remains in the existing Java/native Android architecture. No new dependency or client/plugin architecture.

Existing .128 logs show a saved artwork inventory, not a full recount. In the previous corrected 1.1.3 startup, cached shelves arrived around 4.3 seconds, fresh Next Up at 4.33 seconds, fresh Resume at 9.67 seconds and first artwork at 5.27 seconds. Those end-to-end times include server/Companion/network scheduling and Android renderer delays; they are not evidence of a cache rebuild. No current Lowfin crash was present in the inspected filtered logs.

The existing device gfxinfo aggregate spans both browsing and playback (MainActivity was hidden when read), includes continuous banner/other animations and contains implausible vendor GPU overflow buckets. Its aggregate median frame bucket was 23 ms and p95 38 ms, with long-tail stalls. It is not a controlled navigation benchmark, average FPS, or before/after proof. No claim of stable 60 FPS or eliminating every TV stall. No playback interruption was introduced just to collect an extra startup baseline.

## Changes and preserved semantics

### Startup

Remove the status TextView and its LinearLayout wrapper. Keep a single centered, proportionally scaled existing Lowfin logo with its optional pulse. Both fresh Continue Watching and Next Up gates, visible artwork checks, first-card focus, 15-second failure bound and remote dismissal remain. The option and master animation behavior are unchanged. No added startup network work.

### Identical metadata refreshes

Library.put previously parsed/merged a partial DTO, serialized the entire merged JSON, and INSERT OR REPLACEd its row on every normal refresh. Now compare every incoming non-Companion field using the existing full JSON comparator. If nothing changes, UPDATE only stamp; keep the stored JSON and row ID. This still advances freshness/baseline stamps and cached resume ordering. Real metadata, nested UserData, image tags, unknown fields and explicit null changes take the original full-write route. Retain existing detail fields omitted by short DTOs, genre indexes, account databases and local-decoration exclusion. No schema migration or persistent cache clear.

A SQLite insert-trigger regression sends 100 identical short refreshes after a full item: zero replacement inserts versus the former 100. It checks the actual stamp, preserved JSON/row ID, detail source size above 2^53, genre rows, subsequent watched/position changes, genre replacement and explicit null. This is work-count evidence, not a TV speed multiplier.

### Bounded batch reads

Home return now flattens the data-only shelf snapshots and hydrates them through one deduplicated, 128-ID-per-query pass, then restores their original boundaries. Banner/library rows are excluded. Generation, screen, snapshot-identity and fresh-network guards remain. Ordered duplicate IDs, missing-item fallbacks and selected media/More/horizontal positions retain their behavior. A ten-row fixture covers overlaps, missing items, empty rows and independent Companion positions.

Companion mergeResume still accepts response states first and retains the plugin-row path and permitted missing-item fetches. It then reads native/cached candidates in bounded batches instead of querying each candidate again individually (up to 100 cached plus 18 native cards). Missing native metadata still receives batched current Companion decoration; missing cached metadata is not resurrected. Native/plugin order, duplicate elimination, visibility, limit/type filtering and the prohibition on adding global cached rows to a scoped library remain. Existing manual reset, revision/sequence, account, old-plugin and playback-report tests remain. A larger fixture verifies fresh native metadata, missing native fallback, duplicate IDs, no extra server requests and selected-library isolation.

### Incremental polling and eviction

Use SELECT 1 FROM items LIMIT 1 for baseline existence, retaining the empty/missing-lastSync behavior; exact counts remain available where displayed. An empty changes batch with a new opaque cursor still commits that cursor, but skips loading/parsing every stored home/genre/recommendation snapshot. Nonempty batches, journal/audit writes, history-gap handling, notices, artwork retry and completion gates remain unchanged.

When a visible artwork download must evict at the existing disk limit, snapshot each candidate timestamp once and perform a stable in-memory oldest-first sort. Previously the comparator called File.lastModified repeatedly during O(n log n) comparisons. The timestamp-read test checks exactly one read per candidate, tie ordering and empty input. Deletion sizes, disk quota/reserve, clear-generation guards, saved inventory, legacy image reuse and hashes retain their behavior. The hexadecimal alphabet is shared rather than recreated on every uncached hash calculation.

## Deliberately retained after review

- Bounded image queues, independent disk/decode/download work, existing 6 MiB bitmap LRU and 64-entry ratings memory cache; no full-cache scan or new periodic refresh.
- Persisted metadata/artwork inventories and Companion-only incremental invalidation; live channel logos stay on demand and off persistent disk.
- Independent Resume/Next Up workers, on-demand permanent rating cache, explicit provider controls, Tracearr user mapping and statistics semantics.
- TV channel identity/name notifications and actual-provider reconciliation. These fix prior Projectivy compatibility problems and were not weakened to reduce writes.
- Native remote focus/scroll navigation, action rows, optional animations and bounded dialogs; no arbitrary throttling of remote keys or large rendering-layer changes.
- Playback source negotiation, stream routing, subtitle defaults, seeks, disk/RAM buffer budgets, HDR and denoise safeguards, reporting order/outbox/manual fences and no next-episode preload. No evidence supports changing these parameters in this release.
- Existing first-draw renderer variance and server response time remain possible; these local work reductions do not promise a particular frame rate.

## Delivery scope

Build 1.2.0 (86) as a nondebuggable optimized release with the original signature. Verify full regressions/lint, copy APK/checksum to Desktop, install and open only .128. No .87/.166 access, shutdown, GitHub push, server/plugin installation or user cache deletion. Verification results follow after completion.

## Completed release verification

All 411 tests pass with zero failures/errors/skips, including repeated missing native IDs retaining the first fallback. Release lint passes with 58 warnings and no errors. Optimized release 1.2.0 (86) preserves the original certificate; no DEBUGGABLE flag. Installed only on .128, opened, then restarted once from home to capture the brief logo phase. Live screenshots show the centered logo without caption, followed by the populated banner/poster and first Continue-card focus. First startup PID 7883 reported cached Resume at 1632 ms, fresh Next Up at 1697 ms, fresh Resume at 9164 ms and first artwork at 2508 ms; this is not a controlled speedup claim. Saved inventory reused: 26,749 files / 652 MB. Latest running PID 8475. APK/checksum are on Desktop and archived in dist; previous Desktop 1.1.3 APK/checksum removed as authorized. No cache rebuild/deletion, other TVs, shutdown, server changes or GitHub push. SHA-256: c579ad7e39556110a97540e1a56d99899f36104fbc81ea36764b1a77d9629f37.

Second observed startup (PID 8475): home construction 113 ms, cached shelves 568 ms, fresh Next Up 1292 ms, fresh Resume 5874 ms, first artwork 1183 ms. Saved inventory now 26,757 files / 653 MB. This warm repeated start illustrates variance, not a controlled before/after comparison. MainActivity remains resumed and no AndroidRuntime error appears in the captured process logs. Temporary remote screenshot files were removed; local QA copies remain.
