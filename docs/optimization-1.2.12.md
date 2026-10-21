# Safe optimization · 1.2.12

On the active .128 process, recent logs showed 92- and 40-frame skips, home construction at 287 ms and the first artwork at 2,507 ms. The persistent artwork inventory reported 26,642 files / 523 MB loaded from its saved inventory. These observations do not establish the cause of every dropped frame or a measured FPS improvement.

Concrete source-level work removed:

- Cover-tag binding previously acquired the same lock held by background SQLite writes. UI binding and request queue dispatch now use only short cache/queue operations, independent from the persistence lock. LruCache supplies its own synchronization; an independent queue lock and volatile epoch preserve bounded batching and stale-result rejection. Invalidation/persistence fencing remains serialized.
- Disk hits were written back unchanged after every cold in-memory load. Persist only newly fetched server facts, in one SQLite transaction per batch, retaining existing retired-cache protection.
- The screensaver previously posted an idle callback every second, even when disabled. It now schedules the actual idle deadline and listens for settings changes while resumed. Disabled and active savers do not poll; a temporarily unfocused window is rechecked every 5 seconds only after the idle deadline. Pause unregisters the listener and cancels callbacks.

Regression tests deliberately hold the persistence lock while binding and dispatching on the UI thread, audit SQLite writes when loading persistent facts and verify enable/disable rescheduling without restarting the activity. Existing pending-index retry, metadata invalidation, permissions/HDR toggles, cancellation, key dismissal and playback behavior remain covered. No changes to codecs, buffers, completion rules, server plugins or metadata-precache policy.

Full test, release, lint and installation evidence: dist/verification-1.2.12.json.

Post-install .128 smoke check: MainActivity is resumed, no startup crash observed. Saved artwork inventory remains available (26,668 files / 524 MB). One startup sample logged home construction at 191 ms and first artwork at 2,456 ms, with 91- and 30-frame skips still reported. This is not a controlled benchmark and startup frame skips are not fully resolved by this patch.
