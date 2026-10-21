# Lowfin 0.10.9 performance verification

Release 0.10.9 (code 51), R8/resource shrinking, non-debuggable, unchanged signing certificate. 272 Android unit/Robolectric tests and release lint pass. Installed and launched on 192.168.1.128:5555. No GitHub publication.

## Observed TV startup

| Measurement | 0.10.8 | 0.10.9 normal restart |
| --- | ---: | ---: |
| Cached Continue shelf, from queued cache work | 1556 ms | 490 ms |
| First artwork, from image-loader creation | 2423 ms | 1084 ms |
| Home view construction | 85 ms | 58 ms |

The normal restart read the saved artwork inventory: 28,024 files, 527 MiB. It did not enumerate all cover files. These are individual observed runs, not a controlled repeated benchmark; filesystem caches, server/network traffic and device load differ. First-artwork timing excludes work before image-loader creation.

The first start after upgrade required schema-8 index creation and one initial local artwork recount. Its first-artwork time was 15,122 ms; this upgrade run must not be confused with an ordinary restart. Existing cached content, memberships, settings and Companion cursor remained intact.

## Changes and verification

- Durable artwork inventory with a dirty marker and atomic summary. Tests cover normal restart, interruption, corruption, external directory changes and saving after settled downloads.
- Shared concurrent bitmap decoding; regression coverage verifies cached images, blocked concurrent downloads, old HD cache reuse, sender images without disk persistence, clear-during-download and recycled-view late callbacks.
- List requests omit chapters, sources and stream payloads; detail/player requests still retrieve full items. Existing rich metadata survives partial cache writes.
- Schema-8 indexes cover case-insensitive title sorting, native resume order, reverse library membership and artwork retry selection. Query-plan test rejects temporary sorting; migration preserves items, genres, memberships and plugin cursor.
- Individual metadata writes are transactional and unchanged genres are not rebuilt. Abandoned home-cache work stops after navigation.
- Account scope hashes remain byte-compatible and are reused. Bounded per-instance decrypted-key memory checks ciphertext/IV before reuse; replacement/removal invalidates values, and persistent preferences remain encrypted.
- Video-cache key hex formatting avoids repeated formatter allocation; existing video-cache tests verify unchanged semantics.

Companion-managed completed caches still update through plugin events; missing/expired history does not start an automatic full scan. Without a recognized plugin, native cache refresh remains unchanged. Playback buffers, codecs, frame rates and autoplay were not altered by this performance pass.
