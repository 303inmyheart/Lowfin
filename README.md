# Lowfin

## **100% AI-GENERATED**

> **Personally tested:** As the project owner, I have extensively tested all features myself.

A native Jellyfin client for low-end Android TV devices. Designed for Android 11 and HD displays, with optional 4K playback limits and density-aware artwork; supports Android 8 or later. **Current version: 1.3.**

[Deutsche Dokumentation](README.de.md) · [Complete changelog from 0.1.0](CHANGELOG.md)

The optional **Appearance → Pulsing focus outline** switch adds a subtle accent-colored border pulse (off by default; requires UI animations). Banner covers reveal from their center, and the labeled ratings fit the same text column as the description.

## Install

Locally built, signed release APKs are published directly without running GitHub Actions. Download the current APK and its checksum from [GitHub releases](https://github.com/303inmyheart/Lowfin/releases). Transfer the APK to your TV and install it using a file manager with permission to install unknown apps. To update through ADB:

```sh
adb install -r Lowfin-1.3-release.apk
```

Sign in with your Jellyfin server URL, username and password. The encrypted session token provides automatic sign-in; passwords are not saved. Server URLs with a subpath are supported.

## Features

Version 1.3 improves startup shelf refresh, library focus handling, batched database and launcher-artwork lookups, and reuse of static backgrounds. It also adds an optional accent-colored focus pulse and refines the home banner. Cache contents, account data and playback behavior are preserved. All built-in UI text is available in English (the default) and German; server-provided metadata keeps its own language.

- Optional native movie-discovery banner under Home settings: backdrop, logo/title, poster, synopsis, runtime, genres and compact rating badges from your selected sources; left/right browse, OK opens details. It is reorderable and off by default.
- Optional animated startup logo without a status caption under Appearance (on by default), waiting for fresh Continue Watching and Next Up and visible artwork; remote dismissal and a 15-second network-failure fallback remain available.

- Optional **Lowfin Companion 0.7.0** for Jellyfin 12.2+: explicitly selected completion/resume rules shared across devices using the same user, scoped episode lists and respected home-library exclusions. Without a selected rule, playback remains native. [Plugin installation](https://github.com/303inmyheart/Lowfin-Companion) · [API and compatibility notes](docs/lowfin-companion.md). Lowfin continues to work without the plugin.

- Lightweight native UI, muted accent palette, optional small animations, compact 2:3 movie/show/season posters and 16:9 episode thumbnails.
- Hold remote **OK/Enter** on home/library cards for Continue, Restart, watched/unwatched, Watch later, favorites and series details. **Watch later** is a persistent device-local list scoped to each account, separate from Jellyfin favorites.
- Optional smooth screen, shelf and drawer transitions, focus scaling and action feedback; disable them under **Settings → Home screen and appearance → Smooth navigation and action animations**, or disable **UI animations** entirely.
- Movies, shows, large season collections, global search, favorites, watched/unwatched controls and movie roulette using real library titles.
- Continue watching, Next up, up to five **Because you watched** movie recommendation rows and individually pinned genre rows. Focused rows can show the selected movie's backdrop, description and ratings.
- Optional 16:9 library shelf on home; independently reorder all home shelves in Settings → Home. Ordering is scoped to the current account and uses stable keys for changing recommendation titles.
- Collapsible remote-controlled sidebar, bounded row navigation, return-to-home behavior and exit confirmation.
- Live TV opens directly into the channel list. Live-stream negotiation follows Moonfin's opening behavior, with native audio decoding, bounded reconnect handling and subtitles disabled by default.
- Local playback, stereo downmix, configurable disk video cache and adaptive RAM buffers that preserve memory headroom. Optional server transcoding remains disabled by default. Direct playback requires codecs supported by the device or bundled audio decoder.
- Intro Skipper through Jellyfin MediaSegments on Jellyfin 10.10 or later: automatic intro/outro skipping and manual skip controls for other segments.
- Full-screen YouTube trailers in Lowfin’s native Media3 player, with native controls and no YouTube interface. Stream lookup follows the approach used by [Moonfin](https://github.com/Moonfin-Client/Moonfin-Core/blob/main/lib/data/services/youtube_stream_resolver.dart); availability depends on YouTube returning playable media. Trailer quality is capped at 720p for low-end devices.
- Persistent account/server-scoped library metadata and artwork cache. Cached library browsing survives restarts and connection failures. Foreground disk reads/decoding are separate from downloads; preload and UI request matching artwork sizes. Existing 240px HD artwork remains reusable. The artwork budget is bounded: visible uncached covers can replace the least recently used files when it is full, while background preload stops adding files.
- With recognized Companion incremental caching, completed metadata/artwork caches receive only plugin changes; missing/expired cursors retain the cache and show a history-gap notice, with manual refresh available. Empty caches still need an initial build. Without the plugin, automatic preload uses the last successful refresh (24 hours by default). A crash-aware artwork inventory avoids recounting all files on normal starts; stored covers are retained.
- OMDb, MDBList and TMDB API keys encrypted on device, selectable rating badges and permanent **on-demand** ratings storage. Ratings are never queried as part of library preloading.
- Android TV preview channels for Continue watching, Next up, movies, shows and watched-movie recommendations. Separate movie/show library selection mixes cards across chosen libraries. Launcher support and channel visibility depend on Android TV / Google TV.

## Settings

English is the default language regardless of the device language. Select **Language → Deutsch** to use German; the language picker is also available before sign-in. UI, dialogs, player, trailer messages and TV channel names are translated. Library titles, genres and descriptions retain the server's language.

- **Genre rows:** individual account-scoped genre checkboxes, global visibility and the single-focused-row view.
- **Home screen and appearance:** Continue watching and Next up visibility, 0–5 recommendation rows, poster columns, roulette duration, random/fixed muted accent colors, foreground-time color interval, automatic preloading and cache refresh interval.
- **Advanced playback:** individual intro/outro skipping, default subtitles, preferred audio language, seek interval, controls timeout and separate VOD/live RAM buffer ceilings. Buffers always remain adaptive to available memory.
- Existing controls include playback resolution/bitrate, stereo downmix, optional transcoding, disk cache size, artwork budget, rating sources/API keys, Android TV channels and their library sources.

## Import rating API keys

When no rating API keys are saved, Lowfin creates **Download/Lowfin-API-Keys.txt** on launch. Fill the provider fields, save as UTF-8 and return to Lowfin. The app checks the file every five seconds while open, encrypts the supplied keys and deletes the file after successful storage. Leave unused providers empty. Existing keys disable automatic import; individual keys remain editable in rating settings. A TXT picker is available if your editor replaces the original Download entry.

```ini
OMDB_API_KEY=
MDBLIST_API_KEY=
TMDB_API_KEY=
```

Use the raw key/token after `=` without quotes or a `Bearer` prefix. The generated file explains each provider.

## Build and verify

With JDK 17 and Android SDK platform/build-tools 35 installed, set your local SDK path and use the Gradle wrapper:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleRelease
```

On Linux, `bash tools/bootstrap.sh` installs project-local build tools under `.tools/`; then `bash tools/build.sh testDebugUnitTest lintDebug lintRelease assembleRelease` runs the same checks. Bootstrapping accepts Android SDK licenses. Tools, SDK paths, build directories and private signing keys are excluded from Git.

The `release` APK uses R8/resource shrinking and the local Android debug signing configuration. An existing installation can only be updated using the same signing certificate. The supplied APK preserves the certificate used by earlier Lowfin builds.

Automated tests use JUnit, Robolectric on Android 11, mock HTTP servers and a fake Android TV provider. See `dist/verification.json` for the exact test count, APK checksum and certificate. HD UI renderings are under `design/screenshots/`. Physical TV/live-server behavior is not validated by these simulated tests.

## GitHub builds

[Android build](https://github.com/303inmyheart/Lowfin/actions/workflows/android.yml) is manual-only (`workflow_dispatch`). Source pushes do not start a build or consume build minutes. When explicitly started, it checks tests and lint, builds a signed release APK and publishes `Lowfin-<version>-build-<run>` containing the APK, SHA256SUMS and BUILD.json. Test/lint reports and HD screenshots are uploaded separately. APK artifacts are retained for 30 days; reports for 14 days.

The optional workflow uses the encrypted `LOWFIN_SIGNING_KEYSTORE` Actions secret for the existing Android debug signing key. The job checks that the APK retains the supplied release's certificate, so it can update an existing installation. If the secret is absent in another repository, a new debug key is used and compatibility is reported in BUILD.json. Private keys are never committed or included in build artifacts. All actions are pinned to specific upstream commits; the workflow has read-only repository permissions.

## License and dependencies

Lowfin is GNU GPL v2; see [LICENSE](LICENSE) and [THIRD_PARTY.md](THIRD_PARTY.md). The bundled FFmpeg decoder includes corresponding wrapper, FFmpeg and AndroidX Media source archives in `third-party/`, with rebuild instructions. Source packages, APKs and verification reports from previous local releases are retained under `dist/`.

This product uses the TMDB API but is not endorsed or certified by TMDB. OMDb and MDBList are optional external services; their own terms apply.

### Tracearr playback statistics

Settings → API → Tracearr connects to the **Public API v2**. Create a Public API token in Tracearr under Settings → Data & API → API. Enter the root URL (preserving any reverse-proxy subpath) and token, then select the matching Jellyfin server and test the connection. The logged-in Jellyfin user is mapped by external user ID; names are never used as a fallback. Older Tracearr instances without API v2 need an update.

Counts include only stopped resume chains marked `watched` by Tracearr. They follow Tracearr’s configured completion rule, not proof that every scene played. Series completions are the minimum completed-play count across all episodes currently available on Jellyfin, **including specials**. Newly added episodes may reduce this count. Missing account mappings, unavailable history and incomplete pagination are shown as unavailable, never zero. Pre-recording history cannot be reconstructed.

The existing `Download/Lowfin-API-Keys.txt` template now also supports:

```ini
TRACEARR_URL=http://your-tracearr-server:3000
TRACEARR_API_KEY=trr_pub_your_token
TRACEARR_SERVER_ID=
```

The optional server ID is Tracearr’s Jellyfin-server UUID, not Jellyfin’s own server ID. Leave it empty and choose the server in settings. Tracearr credentials imported while logged in belong to that Jellyfin account.

The **Create API key file again** button is available in API settings even with saved credentials. It creates a blank template; it never exports stored keys or overwrites an existing download file. Filled fields in an explicitly requested import replace those values; **empty fields preserve saved keys**. Successful encrypted storage is verified before the file is deleted. Unattended initial file creation remains disabled once credentials exist.

Companion 0.3 manages provider credentials in the Jellyfin plugin dashboard, with all-user or selected-user access. Lowfin retains provider/rating choices and its default-on, optional local rating cache; authorized users no longer need local key/TXT/Tracearr connection controls.

Optional DVD / 5.1 frame overlays require Companion 0.4.0. Enable them in the server plugin dashboard, and hide them per TV under Settings → Appearance. Cached artwork is unchanged; 4:3 episode thumbnails cropped to 16:9 keep both labels visible.

Companion 0.5.0 adds a persistent event-driven tag index with a 24-hour reconciliation task. HDR tags require HDR playback to be enabled on that TV. Episode ratings remain uncached.

## Server companion and language

[Lowfin Companion](https://github.com/303inmyheart/Lowfin-Companion) has its own source repository, changelog, releases and Jellyfin update manifest. Clone it into the ignored local `companion/` directory if running the local server integration tools from this checkout. The client never requires the plugin.

Lowfin defaults to **English**, independently of the Android system language. Switch between English and German under Settings → Account and language. UI text, settings, playback actions and import templates are localized; movie/show titles, genres, descriptions and remote server errors retain the language supplied by your Jellyfin server.
