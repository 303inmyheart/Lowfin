# Lowfin Companion v1 contract

Server plugin 0.3.0 targets Jellyfin 12.2.x / .NET 10. Lowfin 0.10.0 keeps its existing native UI and Media3 player. The configured server was read-only verified as 12.2.0; integration tests use an isolated portable 12.2.0 server. Installation instructions and current limitations are in [the plugin guide](https://github.com/303inmyheart/Lowfin-Companion).

## Authentication and discovery

All routes below use the existing Jellyfin `Authorization` header. The authenticated user is derived exclusively from Jellyfin claims; clients cannot supply another user ID. A userless server API key cannot configure an arbitrary account. Items must be visible to that user and be movies or episodes.

Base route: `/Lowfin/Companion/v1`.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/capabilities` | `available`, `apiVersion`, plugin/host versions, negotiated features |
| GET | `/items/{itemId}` | Rule, effective rule, independent resume, watched status, revision and lease sequence |
| PUT | `/items/{itemId}/rule` | `{expectedRevision, rule}`; null rule inherits the user’s general rule |
| POST | `/items/{itemId}/actions` | `{expectedRevision, action}`; `MarkSeen`, `MarkUnseen`, `ResetResume` |
| GET / PUT | `/long-videos` | Per-user fallback; PUT takes `{expectedRevision, rule}` |
| GET | `/resume?limit=18&amp;knownItems=id1,id2&amp;parentId=libraryId` | Recent independent entries and cached-card states; optional server-enforced library scope echoed as parentId; maximum 100 entries/known IDs |
| POST | `/items/{itemId}/playback` | Lowfin-only gateway for actual start/progress/stop reports |

JSON names use camel case; completion mode names are `Default`, `CustomPercentage`, `RemainingTime`, `NeverAutoComplete`. Rules contain `percentage` (decimal, >0..100), `remainingSeconds` (0..604800) and `persistentResume` (boolean). A long-video fallback has `enabled`, `minimumSeconds` (60..2592000) and `rule`; an explicit Default rule overrides that fallback.

Companion is active only for an explicitly chosen non-default rule, an explicitly enabled independent-resume rule, or a matching deliberately enabled long-video fallback. An unconfigured medium uses Default with persistentResume false and returns active false. The client retains native playback routes in that case. Choosing Default in the UI also disables independent resume; its separate toggle is an explicit opt-in. Existing chosen rules are preserved and fetched on other devices at playback preparation.

State additionally carries resumeVisible independently of hasResume. Global resume lists mirror Jellyfin’s LatestItemsExcludes library preferences using allowed library ancestry. An explicitly opened library may still return its hidden-home items; their positions remain usable in details. Scoped results never incorporate arbitrary global cached membership. Features scopedResume, resumeVisibility and explicitOptIn are advertised; older plugins fall back to native scoped lists rather than accidentally returning global entries.

Absent or incompatible plugins expose no extra controls. Discovery reuses the existing HTTP pool, has a five-second total deadline, rejects redirects, and caches successful capabilities for fifteen minutes; absent capabilities are checked at most every thirty seconds when needed. Account-scoped capability/resume caches survive restarts.

## Playback and races

Gateway requests contain `leaseId`, `expectedRevision`, `sequence`, `event` (`Start`, `Progress`, `Stopped`) and `playback`, the original Jellyfin playback DTO. Ticks remain Jellyfin’s 100 ns units. Start sequence is zero; later sequences increase, even for a backward seek. The authenticated device and Jellyfin PlaySessionId own the lease. Replies contain `accepted`, `duplicate`, `reason` and authoritative `state`.

The plugin invokes supported SessionManager methods with unchanged reported positions. Synchronous UserDataSaved correction is restricted to the actual asynchronous request scope, before core completed-state propagation. It never infers ownership of an unrelated user-data write merely from the currently playing item. Asynchronous start notifications are matched to the durable lease; stale stops/progress cannot overwrite a replacement lease.

Automatic server interpolation is stopped only for managed Lowfin sessions. Ten-second real progress reports, pause/resume and stop checkpoints continue; crashes do not fabricate progress. A small durable client outbox coalesces unsent progress per lease while preserving its start. Retries stay on Companion routes and cannot double-report a native start/stop. Requests are serialized on Lowfin’s existing reports executor, not the UI thread. Playback preparation waits behind pending cleanup before reading the next authoritative resume state.

Manual actions increment the revision and fence the existing lease. A subsequent explicit new playback start clears that fence. ResetResume clears the independent/native position while preserving watched status. Existing manually watched items remain watched. Favorite-only updates are not interpreted as resets.

The store uses bounded striped locks, atomic replacement of small per-user/media JSON records, and a lazy resume index. A write-ahead sequence reservation prevents duplicate non-idempotent core delivery. This is not a cross-database atomic transaction; the documented ambiguous-start crash window remains. External identical-value native resets are likewise a documented interface limitation.

## UI and fallback

- Film/episode detail pages get one Smart Completion action only after discovery and a readable item state.
- Rule selection, percentage/minute input, independent-resume toggle, inheritance and explicit reset use existing native TV dialogs.
- Playback settings contain the optional long-video rule, minimum duration and completion/resume settings.
- Continue shelves and TV channels retain watched titles with independent resume. Explicit Restart always starts at zero; autoplay episodes also start at zero.
- Native Jellyfin playback, Live TV, Intro Skipper, Tracearr and existing account/cache/UI behavior remain the fallback when the plugin is absent. Tracearr’s own completion thresholds are unchanged.

## Build and verification

```sh
.tools/dotnet10/dotnet build companion/src/Lowfin.Companion/Lowfin.Companion.csproj -c Release
.tools/dotnet10/dotnet test companion/tests/Lowfin.Companion.Tests/Lowfin.Companion.Tests.csproj
python3 tools/package-companion.py /home/rico/Schreibtisch/lowfin
bash tools/test-companion-server.sh start
python3 tools/test-companion-live.py
bash tools/test-companion-server.sh stop
bash tools/build.sh testDebugUnitTest lintRelease assembleRelease
```

The portable test server binds only to localhost:18096 and uses `.tools/companion-test`; the live-test script hardcodes localhost and dummy credentials. It never targets the user’s LAN server. Release packaging includes only the plugin DLL, local Jellyfin `meta.json` and artwork, without copying Jellyfin’s own assemblies.

Source review: [Jellyfin v12.2](https://github.com/jellyfin/jellyfin/tree/v12.2), particularly UserDataManager, SessionManager, SessionInfo, PluginManager and PluginsController.

## Incremental cache API (Companion 0.3.0 / Lowfin 0.10.0)

`GET /Lowfin/Companion/v1/cache/changes?cursor=...&waitSeconds=20&limit=8` uses the same authenticated user claims. Discovery advertises `incrementalCache`. The envelope has `cursor`, `reset`, `more`, and `changes`; each change contains a native PascalCase Jellyfin `item` DTO and its visible root `libraries`. Limits: 1–20 journal entries per request, 0–20 seconds waiting, client response cap 2 MiB. No cursor returns a baseline marker without enumerating items.

The cursor binds journal epoch, monotonic sequence and an opaque hash of user identity, permissions, parental limits, preferences and visible roots. No metadata is returned for inaccessible items. Invalid, future, expired or incompatible cursors return `reset` with a new head cursor. Since client 0.10.8, a completed local cache is retained and subscribed to that head, with a persistent history-gap notice and an explicit manual refresh option. No automatic full scan repairs a history gap in Companion mode; missed changes cannot be reconstructed by this API. Restart creates a reset event because a supported plugin cannot observe modifications while the host is stopped. Deletion/structure/visibility ambiguity requests reconciliation rather than guessing old access rights.

The client captures a baseline **before** its full sync and saves it only after successful completion; later events are replayed. Incremental metadata, root membership and cursor are one SQLite transaction. Artwork work is queued in that transaction and processed separately with persistent failure backoff. The client continues listening during foreground playback, with an artwork batch of one; it cancels its waiting request when all Lowfin activities leave the foreground. An empty/incomplete cache still requires its initial baseline. Without a recognized incremental-cache plugin, native periodic refresh remains available. Ordinary Jellyfin playback reporting and Companion rule opt-in are unchanged.

## Server providers (0.3.0)

The admin-only standard Jellyfin plugin configuration contains credentials and an all-users/explicit-user allow list. Discovery advertises `serverProviders` only for a permitted authenticated user. `GET /providers` returns boolean availability only; `/items/{id}/ratings?sources=omdb,mdblist,tmdb` returns normalized `scores` and provider-name `errors`, after checking the current user's visibility and provider permission. Fixed provider hosts and validated media provider IDs prevent clients from choosing arbitrary upstream URLs. Redirects are disabled and response sizes/concurrency are bounded.

`GET /items/{id}/statistics` uses only the caller's Jellyfin user ID to resolve Tracearr accounts, with the admin-selected Tracearr server ID, pagination guards, unique completed-session IDs and a 30-second budget. A bounded 60-second per-user/item server statistics cache avoids repeatedly enumerating histories. `/providers/tracearr/servers` requires an administrator and exposes only Jellyfin server IDs/names for the dashboard picker. Keys never appear in client API responses. Local permanent rating storage remains account-scoped and optional (default enabled); disabled means no rating memory/disk cache reads or writes, while in-flight requests can still deduplicate.
