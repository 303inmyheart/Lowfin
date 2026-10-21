# Further optimization after 1.0.8

The library AsyncListDiffer compared complete JSON snapshots by serializing both into strings. Compare all object fields and ordered array elements directly instead, with identity and early-difference exits. Object key insertion order no longer causes unnecessary updates. Playback sources, chapters, user state and every unknown field remain part of the full comparison; this is not a presentation-only shortcut. Numeric comparison retains JSON rendering semantics without lossy double conversion, including large integer sizes/ticks. Missing fields remain different from explicit null.

Home presentation comparisons reuse static field-name arrays and compare their nested JSON fields directly rather than serializing image tags, genres and provider IDs. Existing selective presentation fields, action-data replacement and callback guards remain unchanged.

The library comparison normally runs on AndroidX's background executor; this reduces transient string allocation and wasted rebinds, not proven main-thread freeze latency. Current .128 logs show saved artwork inventory reuse, home construction 195 ms, first artwork 1967 ms, and no recorded navigation stall in the selected log excerpt. No controlled before/after benchmark or universal FPS claim. No playback, filtering, cache invalidation or network policy changes.

Regression tests cover complete DTO comparison without serialization, reordered object fields, nested stream changes, array order/length, scalar types, missing/null distinctions, and adjacent large integer precision.

Verification: full testDebugUnitTest suite passed: 380 tests, zero failures/errors/skips. Source changes only; no new APK built or installed in this pass. Installed release remains 1.0.8 on .128 and .87. No GitHub push.

Release delivery: 1.0.9 (81), signed original identity, R8/resource shrinking, release lint passed without errors. Installed and verified on .128 and .87; started only on .128. APK/checksum on Desktop. No GitHub push.
