# Roadmap

Maintain low-end Android TV performance, remote navigation, optional Companion integration, accessible settings and privacy-safe releases. Preserve compatibility, persistent caches and existing playback behavior.

## Companion index efficiency (Companion 0.8)

- Reuse unchanged media facts and per-user container summaries; batch native permission-scoped episode queries and update affected parents only.
- Coalesce duplicate metadata deliveries while preserving durable history, image changes and cursor reset behavior.
- Verify warm scans, access changes, events during scans, unavailable media and unchanged loudness profiles with regression and isolated-server tests. Verified with regression tests, isolated-server checks and owner production-library testing.

## Audio loudness (Lowfin 1.6 / Companion 0.8)

- Implement server-side per-source/per-audio-track measurement and a persistent, fingerprint-validated cache.
- Run through Jellyfin's scheduled-task manager, automatically on new/changed media and manually on demand; retain daily reconciliation and cancellation.
- Integrate default-on medium-strength local PCM leveling and track-aware background calibration without delaying playback.
- Verify multiple tracks/versions, interruption, file replacement, invalid data, channel linking, peak limiting and seek reset. Verified with regression tests, owner playback testing and on-device calibration diagnostics.
- Live TV: evaluate local real-time leveling separately. Do not pre-scan or persist whole-program measurements for endless channel streams.
