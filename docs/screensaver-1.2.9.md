# Native screensaver · 1.2.9

Enable under Settings → Screensaver (off by default). The idle delay defaults to 3 minutes and supports every value from 1 to 20 minutes; invalid stored values are clamped. Slide intervals: 10, 20 (default), 30 or 60 seconds. The feature runs only in the foreground browsing activity outside playback. It stops on pause/destroy, disabling, account changes or dismissal. The overlay alone uses keepScreenOn and releases it on dismissal; precache wake behavior stays independent.

A slide uses two recycled ordinary ImageViews for background crossfades and one fixed, proportional logo frame with text fallback. These views cannot render Companion cover tags. The existing Images and Ratings repositories are reused. Native community/critic ratings appear even without external provider keys; external ratings honor existing settings and cache behavior. Existing animation preferences are honored.

The first key down, repeats and matching key up are consumed. Touch dismissal consumes the gesture. The underlying browsing view is hidden while the overlay is active and restored with its previous focus. All timers and view animations are cancelled on pause; late network/image/rating results cannot recreate a dismissed overlay. Requests are bounded to 10 seconds and one item per slide interval; no worker service, library rescan or new precache is introduced.

Separate account-scoped library choices are available for the screensaver and the Home banner. Missing preference means all suitable libraries; [] means none. A selected library is chosen for each random request via the supported ParentId filter. Offline fallback uses that same library filter. The banner remains movie-only. Its existing history is cleared when the selection changes, and stale results from the old selection are rejected. No Companion update is needed.

SettingsColumn normalizes the direct settings controls to 8 dp gaps, helper text to 4 dp and section headings to 16 dp. Old anonymous spacer Views are ignored, including dynamically appended controls. Existing settings selection restoration remains intact.

Validation: full 436-test suite passed before the final additional timer/library/layout cases; final focused checks and release details are recorded in dist/verification-1.2.9.json. Screenshot: app/build/qa/screensaver-hd.png (synthetic backdrop; no real user media).
