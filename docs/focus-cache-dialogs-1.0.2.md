# Lowfin 1.0.2

Initial library layout focuses card zero before the alphabet shortcut becomes focusable. Saved detail-return positions and explicit letter jumps retain priority.

A per-account SQLite marker records a completed metadata/artwork baseline. Pending covers, interruption and metadata-only completion do not qualify. Companion artwork retries may finish a pending initial baseline. A completed marker survives incremental updates and journal-history warnings. Explicit cover/full-precache deletion resets it. Existing Companion baselines can be adopted only with a saved checkpoint, nonempty metadata, exhausted durable artwork queue and no history gap. Native legacy caches without equivalent evidence require one successful pass. The manual preload button is dimmed and disabled; automatic incremental changes/native refresh semantics remain unchanged.

Dialog rows are styled when attached, before measurement; subsequent layout styling remains as a fallback. No adapter replacement, selection or callback changes.

No GitHub publication.

Verified: 363 client tests, release lint and release assembly passed. Original signing certificate preserved. Installed and launched on .128; version 1.0.2 (74), no DEBUGGABLE flag. APK and checksum on Desktop. First full-suite run had one timeout in the existing animated-home navigation stress test; the complete rerun passed without changing that test or home navigation.
