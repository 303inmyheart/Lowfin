# Changelog

All Lowfin versions, starting with the first test version, **0.1.0**. Historical entries were reconstructed from the archived source READMEs and verification reports in `dist/`; no release dates have been inferred. Changes describe each version's implementation, not a guarantee that every TV or stream was tested.

## Unreleased

- GitHub Actions builds on `main` pushes and manual runs: Android 11 unit/UI tests, Android Lint, a signed release APK (R8 and resource shrinking, debug disabled), checksums and build metadata.
- Downloadable APK and diagnostic artifacts, with the existing signing certificate when the repository signing secret is configured.
- Dedicated complete version history linked from both documentation languages.
- Release APKs preserve the existing signing certificate for installation updates; release lint is checked alongside debug lint.

## 0.7.10

- Native Media3 trailer playback replaces the YouTube WebView, with Lowfin transport, buffering animation, hardware-friendly video preference and a 720p ceiling. Public stream resolution follows Moonfin's approach; no Jellyfin credentials are sent to YouTube. YouTube availability and physical-TV playback still require device verification.
- Automatically creates an explanatory `Download/Lowfin-API-Keys.txt` only when no rating keys are configured. Imports stable UTF-8 contents into encrypted storage and deletes the file after verification; empty providers are optional. Includes manual TXT selection, failed-deletion recovery and Android 11 scoped-storage support.
- Sidebar focus no longer scales beyond its card frame, native pressed overlays are disabled, and the dimming overlay disappears immediately when closing the drawer.
- Enter/Done closes the keyboard; search, password login, season jump and key/cache dialogs also run their corresponding action.
- Verified with 178 passing unit/UI tests, debug/release lint and a signed release build. Physical-TV trailer availability remains unverified.

## 0.7.9

- Explicitly notify the Android TV channel collection after publishing changed headings and their recommendation cards, so channel-list observers can refresh cached “Because you watched” names.
- Persist the last notified label per existing channel ID; upgrades also refresh already-correct provider names once. Unchanged titles do not trigger additional collection notifications. Channel IDs, launcher visibility and ordering are preserved.
- Regression tests cover Bad Boys → Asterix with a cached launcher heading and unchanged recommendation item identity, plus the one-time upgrade refresh. Verified with 164 unit/UI tests, debug/release lint and a signed release build; the physical launcher still needs verification.

## 0.7.8

- Holding remote OK/Enter on home or library cards opens Continue, Restart, watched/unwatched, Watch later and favorite actions, plus series details for shows and episodes. Release after a hold never launches the normal click action; remotes without repeat events are supported.
- Continue on a show resolves Jellyfin Next Up, including resumable episodes; Restart resolves its first available episode. Watched and favorite actions update Jellyfin and the local metadata cache.
- Separate persistent device-local Watch later list for each account/server, accessible from the sidebar, independent of server favorites.
- Optional smoother screen/shelf/drawer transitions, focus scaling and saved-action feedback. Settings → Home screen and appearance provides a separate smooth-navigation switch and the UI animation master switch; previous animation preferences are retained.
- English and German translations for new actions/settings. Verified with 162 unit/UI tests, debug/release lint and a signed release build. Physical remote/TV performance still needs device verification.

## 0.7.7

- Continue Watching movies use 16:9 Thumb/backdrop artwork, matching episodes and the More card. Library movie posters retain 2:3 frames.
- Optional 2160p playback limit with the corresponding 3840-pixel width; density-aware artwork requests improve sharpness on higher-density TV displays. UI dimensions continue to use Android dp/sp scaling.
- Additional optional streaming bitrate limits: 16, 25, 40 and 60 Mbit/s. Existing HD defaults are preserved.
- Verified with 153 unit/UI tests, debug/release lint and a signed release build; physical 4K TV playback has not been verified.

## 0.7.6

- Live TV identity displays the Jellyfin channel logo, preferring Logo artwork and falling back to Primary. Missing images retain the channel name.
- Player channel picker shows proportional logos in fixed-size frames beside channel names and numbers, with recycled rows and cached artwork.

## 0.7.5

- Series logo and episode identity appear at top left, with a fixed proportional logo frame and text fallback.
- Position/duration above the timeline on the left; estimated finish clock time on the right while playing.
- Options precedes Next episode at the far right beneath the timeline.
- Live TV displays elapsed viewing time instead of a media position.

## 0.7.4

- Home entry focuses the first Continue watching item instead of its More card after asynchronous loading.
- Bouncing buffering animation also covers opening streams.
- Player options sit below the timeline beside transport controls. Up/down navigation selects timeline/play; Menu opens options.
- Held remote seeking previews an accelerating target starting at 5 seconds and commits once on release; Back cancels the preview.
- Series-only library tab lists resumable episodes scoped to that library, with direct resume and account-scoped offline caching.

## 0.7.3

- Animated slim buffering bar with a softly bouncing accent gradient. Animation stops when hidden, detached or the player leaves the foreground. Error messages remain readable.

## 0.7.2

- Left navigation moves between detail actions; the sidebar opens only at the first action.
- Slightly richer curated accent colors and backdrop saturation.
- Backdrop artwork and gradients fill the screen, with padding applied to foreground content.

## 0.7.1

- Detail actions share a compact horizontal row with consistent button sizing and remote scrolling.
- Embedded audio can be selected before playback, including the correct media version and file stream index. Direct playback applies the selection locally after tracks are discovered.

## 0.7.0

- English UI by default regardless of TV language, with a persistent German option in Settings and before sign-in. Includes navigation, dialogs, player, trailers, errors and TV channel names.
- Separate genre-row settings: account-scoped individual genre toggles, overall visibility and single-focused-row view.
- Home/appearance settings for Continue watching and Next up visibility, 0–5 recommendation rows, poster columns, roulette duration, fixed/random muted accents and foreground-time color intervals.
- Automatic library preloading and refresh interval settings.
- Advanced playback settings for separate intro/outro automation, default subtitles, audio language, seek step, controls timeout and separate adaptive VOD/live RAM ceilings.
- Disabled rows are skipped by remote navigation; recommendation limits also apply to Android TV cards.
- Prevented late ratings callbacks from overwriting a newer ratings rendering.

## 0.6.0

- Removed the Lowfin prefix from Android TV row names while preserving channel IDs.
- Separate account-scoped movie/show library selection for TV channels, mixing cards across selected libraries.
- Redesigned movie roulette with real library titles, a highlighted winner and automatic detail-page opening.
- Curated pool of twelve muted accent colors: new selection at startup and after an hour of foreground use; background time is excluded.
- Permanent on-demand OMDb/MDBList/TMDB ratings storage, including previously loaded seven-day entries. Changing visible sources reuses stored values; library preloading does not fetch ratings.

## 0.5.3

- Restricted watched-movie recommendations to movie baselines and movie results; discarded actor, director and liked-only categories, including stale cached data.
- Added five dynamically named watched-movie Android TV channels whose cards open movie details.
- Applied the genre-style focused row, backdrop, description and ratings view to recommendations.
- Corrected held-key row navigation, Android automatic scrolling, asynchronous row updates and off-screen poster focus.
- Standard TV channel order: Continue watching, Next up, Movies, Shows. Migrated older reversed channel roles without removing IDs or visibility settings.

## 0.5.2

- Added five cached **Because you watched** movie rows below Next up and above pinned genres.
- Used Jellyfin watched-movie recommendations, with similar-movie fallback for other watched titles; unavailable rows are skipped.
- Added 2:3 recommendation cards, detail-page links and Show more grids.
- New LowFin logo: a subtle large L behind the readable wordmark, with normal/adaptive launcher icons, TV banners, PNGs and editable SVG sources.

## 0.5.1

- Smaller genre posters and descriptions/ratings constrained to the HD display.
- Repaired navigation to off-screen cards and the last genre row while retaining card position.
- Disabled default and forced subtitles at playback start; switched live subtitles and audio locally without reopening the live session.
- Stabilized the adaptive buffer's refill target and memory reserve; prevented duplicate reconnect watchdog timers.

## 0.5.0

- Added a live-only stream resolver following Moonfin's opened HTTP source, direct stream and active Jellyfin route preference.
- Explicitly opened unopened live sources, handled reverse-proxy paths and retained negotiated sessions during route fallback; authentication stays scoped to the configured server.
- Bundled Jellyfin's FFmpeg audio decoder for live fallback, including MP2, with local stereo downmix; allowed TS non-IDR entry frames.
- Added encrypted OMDb, MDBList and TMDB API-key settings and individually selectable rating sources.
- Added compact rating badges to genre focus views and details, with on-demand provider lookups and the original seven-day cache.
- Added an exit confirmation; removed the redundant menu button and placed Random movie/Live TV above libraries in the sidebar.

## 0.4.2

- Excluded continuous live streams from the seekable disk cache and shortened live startup/rebuffer thresholds.
- Added a bounded reconnect watchdog and a manual Reconnect stream action; continuous streams no longer appear to end at a known file size.
- Put Show more cards at each row's end; removed Recently added from the app home screen.
- Kept Random movie and Pin genres in the sidebar only, retaining the selected movie-library scope.
- Extended roulette to at least four seconds with a short muted winner animation and automatic detail navigation; cancellation prevents opening.

## 0.4.1

- Revised Back behavior through menus/details to Home before exiting.
- Corrected sidebar focus, Up/Down boundaries and restoration to the previous card when closing the menu.
- Preserved horizontal card selection when changing rows, bounded by the destination row's size.
- Increased movie/show/season grids to eight columns for smaller posters; episode images span two columns.
- Enforced 2:3 poster and 16:9 thumbnail/channel frames; centered cropping for mismatched artwork and contained channel logos.

## 0.4.0

- Collapsible sidebar opens from the first card with Left and closes with Right/Back, with optional lightweight animation.
- Sidebar movie roulette automatically opens the selected film's details.
- Added account-scoped pinned genre rows, with Horror/Thriller defaults and cached random movie selections.
- Added the single-focused-row home view with movie backdrop, metadata and description; bounded horizontal navigation stays in the current row.
- Standardized home movie/show posters and episode thumbnails, including cropped old 4:3 episode images.
- Added in-app YouTube trailers via the official iframe player in Android System WebView, with transport controls and embedding/error handling.

## 0.3.1

- Fixed library-loading stalls and frozen Settings by moving database reads off the UI thread.
- Backfilled the genre index in batches of at most 50 titles after database upgrades, retaining existing metadata, memberships and sign-in.
- Separated library navigation, cache queries and slower content/server work so the sidebar can load independently.
- Added regression checks for incremental indexing, locked database/settings access and busy content workers.

## 0.3.0

- Redesigned lightweight player controls with timeline, pause, seek, next episode, channel selection and an Options menu.
- Added configurable disk video caching with a 500 MB default and adaptive RAM ceilings of 48 MB for VOD and 32 MB for live TV, retaining memory reserve.
- Added partially desaturated, darkened movie/show detail backdrops.
- Made season posters smaller, with six columns and direct season-number navigation.
- Cropped old 4:3 episode artwork into consistent 16:9 thumbnails.
- Added global genre browsing/filtering and movie roulette scoped to the current movie library, with offline cached fallback and automatic detail navigation.

## 0.2.0

- Reworked compact detail pages with poster, metadata, playback/status actions and overview.
- Corrected 2:3 poster presentation and native episode-artwork proportions; preferred genuine thumbnails/backdrops for landscape home cards.
- Added a virtualized season poster grid with four seasons per row, numerical ordering and direct season-number selection.
- Extended artwork preloading to thumbnail/backdrop variants used by home cards.

## 0.1.0 — First test version

- Native Java/Media3 Android TV client for low-end HD devices, with monochrome styling and small optional focus animations.
- Username/password sign-in, encrypted session-token persistence and automatic sign-in.
- Library/sidebar navigation, movies, shows, episodes, large season collections, global search, favorites and watched/unwatched actions.
- Continue watching, Next up and Recently added home rows with progress and playback links.
- Direct local playback, configurable resolution/bitrate, optional server fallback, stereo downmix, track/version/chapter selection and automatic next episodes.
- Channel-only Live TV browsing, automatic stream opening, player channel changes, optional program/recording actions and stream cleanup.
- Intro/outro auto-skip through Jellyfin MediaSegments, with manual controls for other segments.
- Persistent account/server-scoped SQLite metadata and artwork preloading, bounded storage and background refresh.
- Android TV Continue watching, Next up, movie/show channels and Watch Next integration with account-scoped links and local artwork.
- Normal/adaptive launcher icons, TV banner and editable design sources.
