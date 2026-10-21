# Pending cover diagnosis — TV .128, 2026-10-15

Read-only same-signature instrumentation, client 0.10.28 (70). No cache deletion, metadata mutation, preference changes or release replacement. Temporary test APK removed; Lowfin restarted.

- Actual queue count: 1; stored status also says 1. This is not merely a stale count.
- Pending ID: 00015a3b6f109667f648b0dde46e86db. Cached name: Made_in_Abyss_S01E10; SeriesName: Made in Abyss. ImageTags empty, SeriesId JSON null/missing, SeriesPrimaryImageTag fd99d7403360a43c59d6a0a349b13a1f.
- Current authenticated Jellyfin item request HTTP 200: name Das Gift und der Fluch, SeriesId 252627fa490a97e2cc25dab456d74ed2, own Primary d67ab9680389510cfb433988ea376d76.
- Cached retryAt: 2026-10-15T07:31:45.482Z (already due at inspection).
- Companion available=true, incrementalCache=true, autoPrecache=false. CacheChanges.tick returns before discovery/journal reads/artwork retries when autoPrecache is false. Thus the newer metadata/image is not processed automatically with the current setting.
- Cached fallback tag has no owning series ID; Api.image retains episode ID and applies the series image tag, an invalid owner/tag combination. The diagnostic did not perform this known-invalid image request. Fresh server metadata now has a valid own image and series relationship.

Observed cache: 25,778 items, 23,091 artwork files, 612,741,269 bytes. No whole-library artwork scan or cache rebuild was requested. Existing state diagnostics also inspected recent local artwork timestamps; these do not prove server image modification times.

No fix claimed/applied. User can re-enable automatic precache to allow Companion updates/retries; keep explicit disabled preference intact unless requested otherwise. A targeted item refresh or narrower separation of baseline and incremental controls can be implemented separately.
