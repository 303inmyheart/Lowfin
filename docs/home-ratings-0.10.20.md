# Stable home information and navigation

Home recommendation/genre panels reserve one chip per enabled rating source, measured once using its maximum formatted value. Chip views, widths and wrapping remain the same while values load, are unavailable, or change with the focused movie. Missing values use a subdued dash. Arriving values fade for 150 ms when existing motion settings permit; no row insertion or overview displacement. Detail-page ratings retain their original collapsing behavior.

Warm process-memory ratings are read synchronously without disk or network work. Cache-off bypasses this lookup. Cold provider work remains on demand and debounced. Binding tokens continue rejecting previous-film callbacks. No bulk provider precache was added.

An additional focus-layer experiment was removed because short TV measurements did not establish a repeatable improvement. Existing card focus rendering remains. Recommendation/genre headers are invisible until their row is active, preserving row height and navigation geometry in both single-shelf and multi-shelf modes.

Regression coverage includes fixed rating geometry with full/missing/changing scores and narrow layouts, disabled sources, ordinary detail badges, cache-off memory lookup, title visibility, rapid navigation, content updates and paging.

TV baseline immediately before this update: installed 0.10.19, eight horizontal steps in the Asterix recommendation shelf, four right/four left, 400 ms waits plus ADB latency. 89 frames, 15 janky (16.85%), median 5 ms, p90 18 ms, p95 20 ms, p99 34 ms. This warmer repeat already outperformed the earlier 0.10.19 sample and illustrates the limits of attributing changes to short uncontrolled measurements. No constant 60 FPS claim.

The temporary card-layer candidate measured median 12 ms / p90 20 ms just after launch and median 10 ms / p90 19 ms in a warm repeat (80 frames, 18.75% janky, p95 20 ms, p99 26 ms). The selected card/viewport differed from baseline. This does not establish a repeatable gain, so that experiment was removed. The retained stability change is fixed reusable rating geometry and avoiding chip/icon reconstruction.
