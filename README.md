# Lowfin

## **100% AI-GENERATED**

A native Jellyfin client for low-end Android TV devices. Designed for Android 11 and HD displays; supports Android 8 or later. **Current version: 0.7.5.**

[Deutsche Dokumentation](README.de.md) · [Complete changelog from 0.1.0](CHANGELOG.md)

## Install

Download `dist/Lowfin-0.7.0.apk`, transfer it to your TV and install it using a file manager with permission to install unknown apps. To update through ADB:

```sh
adb install -r dist/Lowfin-0.7.0.apk
```

Sign in with your Jellyfin server URL, username and password. The encrypted session token provides automatic sign-in; passwords are not saved. Server URLs with a subpath are supported.

## Features

- Lightweight native UI, muted accent palette, optional small animations, compact 2:3 movie/show/season posters and 16:9 episode thumbnails.
- Movies, shows, large season collections, global search, favorites, watched/unwatched controls and movie roulette using real library titles.
- Continue watching, Next up, up to five **Because you watched** movie recommendation rows and individually pinned genre rows. Focused rows can show the selected movie's backdrop, description and ratings.
- Collapsible remote-controlled sidebar, bounded row navigation, return-to-home behavior and exit confirmation.
- Live TV opens directly into the channel list. Live-stream negotiation follows Moonfin's opening behavior, with native audio decoding, bounded reconnect handling and subtitles disabled by default.
- Local playback, stereo downmix, configurable disk video cache and adaptive RAM buffers that preserve memory headroom. Optional server transcoding remains disabled by default. Direct playback requires codecs supported by the device or bundled audio decoder.
- Intro Skipper through Jellyfin MediaSegments on Jellyfin 10.10 or later: automatic intro/outro skipping and manual skip controls for other segments.
- In-app YouTube trailers through Android System WebView; availability depends on the video's embedding restrictions.
- Persistent account/server-scoped library metadata and artwork cache. Cached library browsing survives restarts and connection failures.
- OMDb, MDBList and TMDB API keys encrypted on device, selectable rating badges and permanent **on-demand** ratings storage. Ratings are never queried as part of library preloading.
- Android TV preview channels for Continue watching, Next up, movies, shows and watched-movie recommendations. Separate movie/show library selection mixes cards across chosen libraries. Launcher support and channel visibility depend on Android TV / Google TV.

## Settings in 0.7.0

English is the default language regardless of the device language. Select **Language → Deutsch** to use German; the language picker is also available before sign-in. UI, dialogs, player, trailer messages and TV channel names are translated. Library titles, genres and descriptions retain the server's language.

- **Genre rows:** individual account-scoped genre checkboxes, global visibility and the single-focused-row view.
- **Home screen and appearance:** Continue watching and Next up visibility, 0–5 recommendation rows, poster columns, roulette duration, random/fixed muted accent colors, foreground-time color interval, automatic preloading and cache refresh interval.
- **Advanced playback:** individual intro/outro skipping, default subtitles, preferred audio language, seek interval, controls timeout and separate VOD/live RAM buffer ceilings. Buffers always remain adaptive to available memory.
- Existing controls include playback resolution/bitrate, stereo downmix, optional transcoding, disk cache size, artwork budget, rating sources/API keys, Android TV channels and their library sources.

## Build and verify

With JDK 17 and Android SDK platform/build-tools 35 installed, set your local SDK path and use the Gradle wrapper:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleRelease
```

On Linux, `bash tools/bootstrap.sh` installs project-local build tools under `.tools/`; then `bash tools/build.sh testDebugUnitTest lintDebug lintRelease assembleRelease` runs the same checks. Bootstrapping accepts Android SDK licenses. Tools, SDK paths, build directories and private signing keys are excluded from Git.

The `release` APK uses R8/resource shrinking and the local Android debug signing configuration. An existing installation can only be updated using the same signing certificate. The supplied APK preserves the certificate used by earlier Lowfin builds.

Automated tests use JUnit, Robolectric on Android 11, mock HTTP servers and a fake Android TV provider. See `dist/verification.json` for the exact test count, APK checksum and certificate. HD UI renderings are under `design/screenshots/`. Physical TV/live-server behavior is not validated by these simulated tests.

## GitHub builds

[Android build](https://github.com/303inmyheart/Lowfin/actions/workflows/android.yml) runs on pushes to `main` and can be started manually. It checks tests and lint, builds a signed release APK and publishes `Lowfin-<version>-build-<run>` containing the APK, SHA256SUMS and BUILD.json. Test/lint reports and HD screenshots are uploaded separately. APK artifacts are retained for 30 days; reports for 14 days.

The private repository uses the encrypted `LOWFIN_SIGNING_KEYSTORE` Actions secret for the existing Android debug signing key. The job checks that the APK retains the supplied release's certificate, so it can update an existing installation. If the secret is absent in another repository, a new debug key is used and compatibility is reported in BUILD.json. Private keys are never committed or included in build artifacts. All actions are pinned to specific upstream commits; the workflow has read-only repository permissions.

## License and dependencies

Lowfin is GPL-3.0; see [LICENSE](LICENSE) and [THIRD_PARTY.md](THIRD_PARTY.md). The bundled FFmpeg decoder includes corresponding wrapper, FFmpeg and AndroidX Media source archives in `third-party/`, with rebuild instructions. Source packages, APKs and verification reports from previous local releases are retained under `dist/`.

This product uses the TMDB API but is not endorsed or certified by TMDB. OMDb and MDBList are optional external services; their own terms apply.
