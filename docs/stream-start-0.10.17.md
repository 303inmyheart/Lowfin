# Stream startup, 0.10.17

Scope: time from requesting playback to the first video frame. Detail-page rendering is unchanged.

## Evidence and conservative changes

The existing TV .128 log contains a previous observed stream start with 84 ms for fresh item metadata, 87 ms for ordered Companion preparation and 43 ms for PlaybackInfo. First frame was reported 3028 ms after `loadBegan`. These are a single historical observation, not a controlled benchmark. Time outside these phases includes profile construction, cache startup, player/extractor/decoder setup and buffering.

`LocalPlayback.negotiate` enumerated decoder types on every load. `HdrPolicy` constructed further codec lists when HDR was enabled. Successful, process-stable decoder information and supported MIME types are now shared; failures are not memoized. Request-specific account, source, audio/subtitle indices, start position and the current display HDR capabilities remain fresh.

The local SimpleCache index used to be opened only after server negotiation. It is now prepared on an existing worker concurrently with fresh metadata and PlaybackInfo work, only after the user has requested playback. The normal factory still works if preparation has not run or fails. No video bytes are downloaded by preparation, and there is no next-episode preloading. A disabled video cache stays disabled.

Media3 1.8.0 initializes SimpleCache on its own thread while holding the instance monitor. Reading cache space waits for that initialization; it makes no media request. Source: https://github.com/androidx/media/blob/1.8.0/libraries/datasource/src/main/java/androidx/media3/datasource/cache/SimpleCache.java

## Stability boundaries

No changes to AdaptiveLoadControl, initial playback/rebuffer thresholds, allocator limits, video cache sizes/keys, live routing, stream URLs, extractor options, subtitles, session reporting or Companion ordering. No fresh server/permission checks are skipped. Decoder catalog caching is independent of user credentials and does not cache playback responses.

Tests cover concurrent capability reads, recovery after failed/missing capability reads, fresh request-specific fields with shared capability data, media-free cache-index preparation, retention of cached bytes, concurrent preparation/factory use and disabled caching. The complete existing regression suite remains required.

## Measurement limits

New phase logs distinguish local concurrent preparation, request-profile construction, data-source setup and player construction. First-frame reporting remains the end-to-end playback-load metric. No guaranteed saving or percentage is claimed: stream format, network, cache state and decoder startup vary. No real user media was automatically started for a fabricated before/after comparison, so an end-to-end gain needs actual subsequent playback observations.

A later pre-update real playback observation on TV .128 (01:06:19, old 0.10.16) reported metadata 99 ms, Companion 51 ms, negotiation 44 ms and first frame 2247 ms. Different starts/cache conditions already vary substantially; neither old observation is a matched performance comparison.
