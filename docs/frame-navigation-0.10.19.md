# TV navigation frame diagnosis

Device: 192.168.1.128, Android TV, logical 1280×720 at 60 Hz.

Baseline installed client: 0.10.18. Android `dumpsys gfxinfo` during eight horizontal remote steps, four right then four left, approximately 400 ms apart plus ADB latency: 74 rendered frames, 48 janky (64.86%), median 30 ms, p90 46 ms, p95 57 ms, p99 61 ms. No slow bitmap uploads; 8 slow UI-thread and 27 slow draw-command frames reported. This is a short navigation sample, not an average FPS counter or proof of one bottleneck. Vendor GPU percentile values overflow into the 4950 ms bucket and are unsuitable for conclusions.

Conservative changes: skip unchanged shelf viewport layout parameters; move information entrance from horizontal to vertical compositor translation; use a temporary panel layer during its animation. Cache the static filtered backdrop and both shading passes as one hardware layer up to 1080p (approximately 3.5 MiB for the measured 720p layer, up to 7.9 MiB at 1080p). The layer is invalidated normally when artwork or accent changes. Larger logical displays keep normal rendering to bound memory use. No playback/cache/session/focus mechanics changed.

Controlled end-to-end improvement has not yet been established. Compare the same screen, navigation path and existing animation settings after installation; image/cache/network/thermal differences remain possible confounders.

After installation, the same eight horizontal steps on another recommendation shelf yielded 115 frames, median 17 ms and p90 32 ms (51.30% janky). Returning to the original Asterix shelf yielded 92 frames, median 10 ms, p90 20 ms, p95 26 ms and p99 36 ms; 30 janky frames (32.61%), 2 slow UI-thread frames and 9 slow draw-command frames. The selected film and viewport offset differed by one card from the baseline; this is an observed improvement, not an isolated controlled benchmark. Remaining missed deadlines mean 60 FPS is not consistently achieved. Release signature preserved, installed and started on .128, 321 tests and release lint passed.
