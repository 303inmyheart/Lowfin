# Conservative optimization audit — 0.10.29

## Observed evidence

Read-only TV .128 gfxinfo sample before changes: 611 frames, 175 janky (28.64%), median 6 ms, p90 36 ms, p95 65 ms, p99 400 ms, individual histogram buckets above 2 seconds. This cumulative sample contains different views and startup; it is not a controlled benchmark and does not identify the cause of an individual delayed remote input. OEM GPU percentile overflow values are not used as measurements.

Historical logs also include garbage collections from the preceding cache-diagnostic process and Android system processes. Those are not treated as proof of a normal Lowfin UI memory leak or multi-second stop-the-world pause. No new fatal playback decoding failure was established by the reviewed log sample.

## Changes justified by code and tests

1. HomeChannels previously rewrote every preview/Watch Next card on every publish. Read a bounded column projection from the actual TV provider and compare only desired values before updating. Deleted rows are still inserted and externally modified values repaired; changes in progress, titles, ranking, intent, images and episode metadata still publish. Retain the existing stored engagement timestamp until explicit engagement changes rather than manufacturing a new timestamp on every refresh. Keep channel creation/IDs, label updates/collection notifications, Projectivy compatibility, manual dismissal and foreign-package filtering. No optimistic in-memory publish fingerprint that could miss external changes.
2. Images.bytes previously acquired the same monitor held during file sorting/deletion and inventory persistence. Publish the byte count through a volatile long and read it without that monitor. Mutations remain serialized; no scan, eviction policy, cache budget or persistence change.
3. Slow remote-input diagnostics separate event age on arrival (500 ms threshold) from synchronous dispatch duration (100 ms threshold). At most one log per two seconds; log only navigation key code and screen kind, no typed input, titles or secrets. No polling thread or continuous profiler.

## Scope intentionally kept narrow

No decoder/buffer, playback reporting, Companion cache policy, animation timing, visual redesign or thread-priority changes. Startup/background issues requiring further runtime evidence are not assigned speculative fixes. The prior cover issue and disabled automatic cache setting are unchanged.

## Verification

Provider tests prove an unchanged repeated publish causes zero program updates; one external title change is repaired with one preview update; progress updates both matching programs while preserving IDs; a deleted unchanged program is recreated. Existing dynamic-heading notification and dismissal tests remain required. An image test holds the maintenance lock while proving the size read finishes independently. Full navigation, cache and playback regressions plus release lint/build are run before installation. No controlled on-device speedup or complete elimination of the intermittent freeze is claimed.

On TV .128 the native provider accepted both explicit column projections without an unknown-column/schema error. Shell identity returned no visible program rows; therefore this check does not establish native write counts. No-op write counts are established by the instrumented provider regression fixture.
