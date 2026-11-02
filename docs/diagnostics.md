# Lowfin performance diagnostics (unreleased)

Local builds record by default. Public GitHub release builds must use
`bash tools/build-public-release.sh` (or `-PlowfinPublicRelease=true`) so recording
defaults to off. This is separate from Android's debuggable flag: both builds can
be signed, optimized release APKs. An explicit setting overrides the build default.
No upload or workflow execution is part of the build script.

Settings → Diagnostics contains the recording switch, current snapshot and clear
control. Switching off clears the in-memory history and unregisters frame listeners.
No background polling, network listener, file history or Companion upload is added.
The frame worker exists only while recording and a Lowfin activity is resumed.

## ADB interface

Requires an already authorized ADB connection and a running Lowfin process:

```sh
adb -s TV_IP:5555 shell dumpsys activity provider de.lowfin.tv/.DiagnosticsProvider
adb -s TV_IP:5555 shell dumpsys activity provider de.lowfin.tv/.DiagnosticsProvider --enable
adb -s TV_IP:5555 shell dumpsys activity provider de.lowfin.tv/.DiagnosticsProvider --clear
adb -s TV_IP:5555 shell dumpsys activity provider de.lowfin.tv/.DiagnosticsProvider --disable
```

Android adds a short provider header followed by one JSON line. The provider is
not exported; normal content queries and mutations are unsupported. Android's
ActivityManager DUMP permission check protects this debugging route. See the
[official ContentProvider.dump documentation](https://developer.android.com/reference/android/content/ContentProvider#dump(java.io.FileDescriptor,%20java.io.PrintWriter,%20java.lang.String[])).
Disabling the recording persists an explicit choice; use the TV switch or `--enable`
to turn it on again. `--clear` begins a new measurement session without changing
the enabled setting, playback, cache or user data. Account changes clear history.

## JSON schema 1

Snapshots include build version, session age, playback activity, Java/native heap
values, executor active/queued/completed counts, shared HTTP call counts and
cached Companion capability booleans. Reading a snapshot does not access the
library database or contact Jellyfin. Heap values are not total process or system
RAM; use Android meminfo for those. Queues are instantaneous observations.

The ring contains at most 192 events. `atMs` is milliseconds since the current
recording started; `evictedEvents` counts overwritten older events. Event names
are fixed enum values and their `a`–`d` fields are numeric:

| Event | a | b | c | d |
|---|---|---|---|---|
| SCREEN_BUILD | synchronous page build ms | 0 | 0 | 0 |
| NAV_CACHE / NAV_SERVER | executor wait ms | work ms, including persistence | UI delivery wait ms | returned item count |
| NAV_DISCARDED | executor wait ms | work ms if run | UI wait ms if delivered | 0 |
| NAV_ERROR | executor wait ms | elapsed work ms | 0 | 0 |
| HTTP_READ / HTTP_WRITE | JSON-object API request duration ms | HTTP status, 0 if unavailable | 0 | 0 |
| LIBRARY_STORE | metadata/membership persistence ms, including writer wait | response item count | 1 if scoped to a library | 0 |
| HOME_NEXT / HOME_RESUME | executor wait ms | network ms | persistence ms | Companion reconciliation ms |
| GRID_KEY | Android vertical key code | effective adapter position, -1 if unavailable | repeat count | 1 if deliberate header entry allowed |
| GRID_TARGET | source adapter position | intended adapter position | preferred span center × 1000 | Android focus direction |
| GRID_FOCUS | committed adapter position | span index | span group | preferred span center × 1000, -1000 if unset |
| PLAYER_STATE | Media3 state integer | position ms | buffered ahead ms | 0 |
| FIRST_FRAME | time from load to first rendered video frame ms | 0 | 0 | 0 |
| VIDEO_DROPPED | video frames dropped | reporting interval ms | 0 | 0 |
| PLAYER_ERROR | Media3 error code | position ms | buffered ahead ms | 0 |
| CACHE_CHANGES | number of changed entries | 0 | 0 | 0 |
| CACHE_RESET | 0 | 0 | 0 | 0 |

`uiFrames` aggregates Android Window FrameMetrics TOTAL_DURATION into eight
buckets: ≤8/16/24/33/50/100/250 ms and >250 ms. `reportDrops` means Android dropped
frame-metric callbacks, not dropped video frames. No per-frame objects are retained.
These observations are UI timings, **not video FPS or a guaranteed jank rate**.
HTTP samples cover Api.call JSON requests; artwork, streaming and separate Companion
transport calls are not individually instrumented. Events never accept free-form
titles, media/user IDs, addresses, headers, tokens, request bodies or exception text.

Measurement itself has overhead, especially frame callbacks and snapshot rendering.
Compare like-for-like warm-cache navigation, record configuration, and distinguish
queue wait, work/persistence and UI delivery. Missing events or overwritten history
are not proof that a problem did not occur. No performance improvement is claimed
solely from adding diagnostics.
