# Deferred work at user request

Paused while fixing the denoise fallback on episode change. No optimization or label change is part of 1.0.7. Candidate edits saved in `.tools/deferred-optimization-20261016/deferred.patch` with complete edited file snapshots under the same directory:
- reuse unchanged non-reserved detail rating badges and retain them during same-item refresh; needs regression tests for changed/missing values, provider selection, stale callbacks and item switches;
- copy home-shelf lists only for matching plugin metadata changes; needs existing navigation/metadata regression tests;
- remove experimental wording in DE/EN denoise setting (user requested); fallback remains.

Resume and test these after the playback fix; do not overwrite newer changes with saved full-file snapshots.

Resumed at explicit user request for 1.0.8. Applied the scoped patch against current source, not the full-file snapshots. Added dedicated rating-view reuse, changed/provider/empty-result, delayed item-switch and matching/unrelated home-journal tests.
