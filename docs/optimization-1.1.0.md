# Lowfin 1.1.0 optimization and detail action layout

## Evidence and scope

The .128 startup log after 1.0.9 showed a saved artwork inventory (24,428 files, 619 MB), home construction 244 ms, a 232-frame Choreographer skip and first artwork at 4,964 ms. This does not indicate a new full cache scan. A separate instrumentation APK sampled the main-thread stack at 20 ms intervals when a 16 ms main-loop heartbeat was delayed by at least 100 ms. It launches the installed optimized release without changing credentials, settings or cache. Instrumentation restarts the application and affects timing; results are diagnostic, not normal-launch benchmarks.

Two unchanged 1.0.9 runs yielded maximum sampled heartbeat delays of 5,011 ms and 727 ms. Most delayed samples were in Android HardwareRenderer.nSetStopped / first draw, not a library scan. Other samples identified Formatter/ICU initialization during rating-slot reservation and unused animated-vector inflation during horizontal progress-bar construction. The large renderer variance already exists before changes; no claim that it is fixed. Retain the previously useful bounded backdrop hardware layer rather than changing rendering architecture speculatively. The second raw sample is in diagnostics/startup-1.0.9-sample.txt.

A short real-device navigation baseline (four right / four left from the first Continue card, ~450 ms host pause plus ADB overhead) recorded 89 frames, 51 janky, median 18 ms, p90 38 ms, p95 40 ms. Source: diagnostics/navigation-1.0.9-gfx.txt. This is not an average FPS measurement. Vendor GPU overflow buckets are unsuitable for conclusions; content, cache warmth, system load and thermals limit before/after comparisons.

## Changes

- MainActivity's view refresh now asks HomeChannels to refresh only its library-specific movie/series selections and publish. Resume, Next Up, recommendations and views already refresh through the main UI and their successful updates still publish channels. Normal hourly HomeJob and explicit settings refreshes retain full independent loading. Remove the unused recently-added request (home-2 has no reader). Retain dynamic heading notifications, existing channel IDs, dismissal settings and account checks. A one-library fixture verifies two requests for the UI path versus seven for the independent path (including Companion discovery); previously both paths redundantly loaded all lists.
- Library.latestItems queries up to 128 distinct IDs at a time instead of one query per card. Launcher resume/next-up/recommendation snapshots and paged watch-later lookups use it. Preserve original order, duplicate entries, original fallback objects when absent, complete metadata and account scope. Existing Companion-aware query decoration remains in use. No new persistent metadata cache or invalidation policy.
- Companion replay first checks for pending lease entries. Empty outboxes no longer start a competing capabilities request. Normal session/playback discovery remains, and pending reports still discover and flush on the serial reports executor. No playback report interval, sequence, manual fence or completion-rule changes.
- Ordinary bounded nonnegative rating values use BigDecimal decimal rounding and the existing comma/scales instead of Formatter. Preserve the previous formatter for unusual/out-of-range/negative values, including negative zero and nonfinite values. Exhaustively compare 48,024 rounding-boundary/value combinations plus unusual-value cases against the previous formatter. Provider requests, visibility and permanent cache unchanged.
- Accent-owned root/shade gradients survive repeated identical lifecycle styling passes; rebuild on color, variant or external background replacement. Pool size, patterns, foreground rotation and explicit focus styling remain unchanged.
- Artwork holders reuse their rounded background when switching movie/channel transparency. Cast holders reuse an owned circular focus shape. More-arrow rendering reuses its Path. Stable media IDs retain identical FNV-1a semantics without a temporary char array. Horizontal shelf progress retains the existing determinate track/tint/progress, but its resource style omits an unused animated indeterminate drawable.
- Per latest user steering, detail actions evenly share the full content-grid width instead of fixed 104 dp. Keep 38 dp height, existing 11 sp/two-line labels, 14 dp icons and 6 dp gaps; no trailing gap. Use the existing row with weights and remove the horizontal scroll wrapper so all actions are laid out together. Preserve right-edge fencing, left navigation, down-to-cast, scroll-to-top on toolbar focus and high-contrast play/resume state. Tests cover four/seven/eight actions and 1280/1920/3840-pixel viewports.

## Limits

No new next-episode preload, buffering thresholds, denoise fallback, codec negotiation, playback/Tracearr reporting, cache deletion, account authentication, server plugin or system setting changes. No performance-driven dependency upgrades. No GitHub push. The diagnostic runner exists only in the separate test APK, not the production app. Do not present reduced allocations/request counts as proof of universally smooth 60 FPS or a resolved Android renderer stall.

Verification and device delivery are recorded below after completion.

## Installation authorization

Latest user steering: do not install this release until explicit Go. Future targets: 192.168.1.128, 192.168.1.87 and 192.168.1.166; start only on .128. No 1.1.0 installation authorized yet. The separate diagnostic APK used against 1.0.9 on .128 is removed after measurement; production release remains unchanged. No after-1.1 device comparison is available before authorization.

Additional delivery instruction: after the future authorized installation succeeds and .128 is verified, shut down .128. Do not shut down before installation Go. Other TVs remain running and are not started into Lowfin.

## Build verification

390 tests passed, zero failures/errors/skips. Release lint: 57 warnings, no errors. Optimized signed release 1.1.0 (82) built, original signing identity preserved, nondebuggable, reflected Media3 builder constructor retained in R8 output. Full-width detail fixture visually inspected. APK and checksum copied to Desktop; prior Desktop APK removed, dist history retained. No installation or post-1.1 on-device measurements without explicit Go. APK SHA-256: 3d8a0d5e770917d7849d08ef094f6a43932165980fee8b16c1507c6e3cf15926.

## Authorized delivery

User gave installation Go. Release 1.1.0 (82), nondebuggable, installed and package version verified on .128, .87 and .166. Started only on .128; current PID 26348 resumed MainActivity, no captured AndroidRuntime error. Saved artwork inventory reused (24,428 files / 619 MB), home build 175 ms, first artwork 7,127 ms. This startup does not establish an end-to-end speedup. Sent requested .128 shutdown via adb shell reboot -p; response Done. No playback test or changes on other devices beyond installation.

Shutdown confirmation: .128 ADB reports device offline after the accepted power-off command.
