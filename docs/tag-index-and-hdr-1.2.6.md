# Lowfin 1.2.6 / Companion 0.5.0

## Server index

Persistent atomic JSON snapshots contain raw per-medium DVD/5.1/HDR facts and DVD-only container summaries keyed by Jellyfin user and visibility-policy fingerprint. Native item permissions remain checked on each request; a mismatched user scope queues background work and cannot receive a summary for the old policy. The request path does not call media-source detection or container count queries.

The first build and restart reconciliation run in a hosted background worker, preserving a readable previous snapshot. Jellyfin add/update/remove events coalesce into bounded incremental batches; stored old parent IDs allow a removed or moved episode to update former series/season majorities. User changes queue scope checks; unchanged scopes do not recalculate container counts. Changed indexed masks notify the existing metadata journal after publication, so a client that fetched an old mask before index completion receives another targeted invalidation. New user indices do not broadcast irrelevant metadata updates to existing users.

A native Jellyfin task has a default 24-hour interval and supports manual execution. A durable timestamp also lets the worker catch overdue checks. Full scans page through the native library in batches of 200; counts and stream reads stay on the server. This is a metadata index, not a filesystem scan or external ratings precache. Incremental snapshots checkpoint at most once per 30 seconds and on graceful stop; a restart reconciles any crash gap. Initial/full snapshot writes are atomic and durable. Cancellation/failure preserves the last complete index. No provider secrets are stored in it.

Dashboard index status shows readiness/build activity, entry count and last full/update/next-check UTC timestamps. The index is maintained only while server cover tags are enabled.

## Lowfin and HDR

Raw mask bit 4 denotes HDR metadata (HDR10, HDR10+, HLG, Dolby Vision). It is drawn only if the server enables HDR badges, local tags are enabled, and the existing hdrPlayback preference is true. That playback preference remains false by default and is never changed by tag display. Preference changes hide/show an available HDR fact immediately. Actual player HDR capability negotiation/fallback stays unchanged. Container-majority badges remain DVD-only.

Badge geometry is drawn after the source image transform in frame coordinates. Three badges retain their proportions and stay within narrow cropped frames. The tag-fact namespace advances to v2 for the extra bit; original image URLs, source files and persistent bitmap keys remain unchanged. No full artwork rebuild occurs. Unknown initial index answers are not cached as zero; bound tags retry pending responses after about five seconds. Older/absent Companion still leaves normal artwork available. Episode ratings remain on-demand and uncached.

## Validation

Core tests cover warm/restarted lookups without source queries, persisted due time, 24-hour task trigger, incremental parent updates and removal, permission scope changes, events arriving during a scan, cancellation retaining snapshots, corrupt files, and coalesced checkpoints/flush. HDR unit tests cover supported video ranges. Native client tests cover local HDR gating, no-refetch toggles, pending-index retry, and three-tag containment/aspect ratio; ordinary navigation/cache/player tests remain in the full suite.

Real isolated Jellyfin 12.2 checks exercise asynchronous index population on a 2-SD/1-HD series, correct series and season DVD tags, dashboard status and registration of the 24-hour task, alongside the existing playback/resume/provider/cache/permission integration. No production Jellyfin configuration or TV installation is changed by these checks.

## Focus restoration regression found during validation

The full regression run exposed an existing race on returning to a recommendation/genre detail shelf: fresh metadata could update an already created shelf before its remembered card reacquired focus. The update read that unfocused shelf's empty selection as column zero. HomeAdapter now reads a live card selection only when the shelf actually has focus, otherwise retaining its recorded/pending column. A targeted regression temporarily moves focus out of the row before metadata delivery and checks return to the remembered twelfth card. The stress fixture also drains the independent home/disk workers before injecting synthetic rows. No navigation architecture or key mapping was replaced.

Final validation: all 430 client tests and 76 Companion tests passed, release assembly/lint and original signature verified. Packages are on Desktop; no TV or production-plugin installation and no GitHub publication in this change.
