# Further optimization — 0.10.25

Baseline: 0.10.24, preserving centered and bounded dialogs. No server/plugin update and no GitHub publication.

## Findings and changes

- The 2-second main status timer wrote TextViews even while the sidebar was hidden. It now updates visible status views only and skips equal text. Opening the drawer refreshes its status immediately. Detached cache-status references are released when rebuilding the screen. The existing timer interval and displayed status policy are unchanged.
- Cached resume/list paths read Companion state once per medium. `withCompanionItems` now reads distinct IDs in batches of at most 128 using bound SQL arguments and restores the original order, including duplicates. State parsing and plugin-absent decoration stripping retain the original behavior. Database/account scope and resume visibility rules are unchanged. This reduces an 18- or 60-item decoration pass from 18/60 individual lookups to one query; larger batches are bounded. Launcher metadata item reads still use their existing path.
- Warm ratings previously reset all slots to placeholders before restoring cached values, and a delayed duplicate result rewrote them again. Warm preparation now directly applies memory scores; equal chip text is not rewritten or reanimated. Changed asynchronous values retain the brief fade, missing values retain fixed slots, and binding tokens continue invalidating stale responses. No disk/network work was moved to the UI thread, and disabled rating cache remains respected.

## Verification

Added regression tests for 260-item multi-chunk Companion decoration, duplicate/order preservation, original metadata immutability, independent account databases, plugin-unavailable stripping, unchanged/changed/missing rating text, hidden sidebar text work and immediate/current status on opening. Existing Companion completion/resume, cache journal, focus, dialog and playback tests remain part of the full suite.

No FPS improvement is claimed from these deterministic reductions without a controlled TV comparison. Cache-history warnings are not cleared or reconciled by this update; completed caches still refresh automatically only on plugin changes when Companion is available.
