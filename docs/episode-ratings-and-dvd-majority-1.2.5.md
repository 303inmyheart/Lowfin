# Lowfin 1.2.5 / Companion 0.4.1

## Changes

- Banner facts now begin with ProductionYear when present, followed by runtime and genres. Missing years add no empty separator; banner width, full rating footer and four overview lines stay intact.
- Episode details request episode-specific IMDb scores through OMDb and TMDB votes. The request path is independent of movie/series permanent caches: no memory peek, state reads/writes or memory result insertion for episodes. Existing stale episode cache values are ignored, not promoted. Concurrent identical requests are deduplicated; a displayed row retains its current text and avoids repeated requests from unrelated detail redraws. Reopening requests fresh values. Provider backoff remains enforced.
- Episode identity uses its own IMDb ID (TMDB find selects only tv_episode_results), or the actual series ID plus season and episode numbers. Specials season zero is accepted. Bare episode TMDB IDs are never treated as movie/series IDs. OMDb responses must explicitly be episodes; series scores do not become episode scores. MDBList movie/show endpoints are not used for episodes. Existing source/provider visibility preferences apply.
- Companion supports the episode rating route with ordinary authentication, own-item visibility, configured user access and Cache-Control: no-store. The server rating gateway stores no episode results. Existing movie/series routing and cache behavior are retained.
- Series/season DVD tags use strictly more than 50% of available, nonvirtual episodes with positive SD dimensions up to 1024×576. Unknown resolution counts toward total but never toward DVD. A tie produces no DVD. This requires two user-scoped count queries with Limit=0, not deserializing every episode/media source. No aggregate 5.1 claim is introduced.
- Episode updates invalidate persisted and in-memory facts for that episode, its season and series, with the existing in-flight fence; unrelated parents keep their facts. Frame-based rendering and per-TV visibility switch are retained.

## Validation

- Full client suite: 425 tests passed before the final missing-coordinate fallback regression; targeted rating/tag tests rerun after that change.
- Companion: 62 tests passed, including fresh episode HTTP requests, rejects of series responses, special-season fallback, strict majority/ties and count-query structure.
- Real isolated Jellyfin 12.2.0: three generated episodes (2 SD, 1 HD) produce DVD on both series and season, with correct per-episode tags. The episode ratings route accepts episodes and returns no-store; no external API credentials were used. Provider score normalization/identity are tested with controlled HTTP fixtures.
- Existing real playback/resume/cache/provider/user-visibility integration is rerun against Companion 0.4.1. The production Jellyfin server and TV installations are not changed as part of verification.

Provider references: https://www.omdbapi.com/ , https://developer.themoviedb.org/reference/tv-episode-details , https://developer.themoviedb.org/reference/find-by-id .
