# Navigation optimization (1.2.18)

Read-only observations on TV .128, PID 7802, version 1.2.17: home screen construction 234 ms on first observation and 46 ms on a later return, Live TV construction 536 ms, repeated Choreographer skips of 30–72 frames, and substantial short-lived allocation/GC activity. Existing gfxinfo accumulated 1,023 rendered frames, 507 classified janky frames (49.56%), p90 29 ms and p99 250 ms. These counters include startup and mixed user activity; they are not a controlled navigation benchmark or an FPS measurement.

Changes target concrete redundant work rather than changing the rendering architecture:

- Cached/fresh library responses with identical data no longer rebuild all sidebar buttons or resubmit home library cards. Only changed button identity/name/type/order rebuilds navigation; artwork-only changes still publish refreshed library cards. Live-TV probes rebuild only when availability actually changes.
- Plain canonical overview strings bypass Html.fromHtml during home focus changes. HTML, entities and noncanonical whitespace use the same parser as before and retain spans, with immutable formatted text cached within a 32,768-character key/value budget. Oversized descriptions do not evict the small reusable entries.
- Cover binding and cache invalidation normalize and validate IDs with a fixed 32-character check instead of compiling a regex on each call. Existing accepted IDs, normalized keys, library scoping and persistent caches remain unchanged.

Regression coverage checks retained sidebar view/focus identity, renamed-library updates, artwork changes, exact HTML/whitespace output, cache freshness/bounds and strict ID normalization. The existing navigation, guide, playback and cache suites run unchanged alongside those checks. Source-level removal of repeated work is verified; no universal 60 FPS or quantified TV speedup is claimed from the aggregate baseline.

Validation: 469 client tests and release lint passed, English coverage remains 471 entries, original signing certificate preserved. APK delivered to Desktop. Following the explicit user request to install and restart, ADB installation on .128 succeeded, version 1.2.18/code 104 was verified, and MainActivity was opened. TVs .87/.166 were left untouched. A controlled on-device after-measurement is pending a suitable idle window.
