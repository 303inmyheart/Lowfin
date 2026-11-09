# Lowfin

## **100% AI-GENERATED**

> **Personally tested:** As the project owner, I have extensively tested both Lowfin and Lowfin Companion myself. **Official releases are published only after my own manual testing.**

A native Jellyfin client designed for low-end **Android TV, Google TV and Fire TV** devices. Lowfin combines a remote-friendly interface, local library caching and a native media player. Supports Android 8 or newer, with layouts for HD and 4K screens. The interface is available in English (default) and German.

[Download the latest release](https://github.com/303inmyheart/Lowfin/releases/latest) · [Changelog](CHANGELOG.md) · [Deutsch](README.de.md)

## Optional server plugin

**[Lowfin Companion](https://github.com/303inmyheart/Lowfin-Companion)** extends Lowfin with custom completion/resume rules, incremental library-cache updates, cover tags and centrally managed ratings/Tracearr connections. Lowfin discovers the plugin automatically and shows the available controls. **Lowfin also works without Companion.**

## Main features

- **Movies and series:** library browsing, global search, large season collections, cast pages, favorites, Watch later and watched/unwatched controls. Sign in with a Jellyfin username/password and return automatically to the last account.
- **Customizable home:** Continue watching, Next up, Because you watched, genre rows and Recently added for each library. Optional discovery banner and library shelf; configurable visibility and row order.
- **Media details and ratings:** artwork, synopsis, genres, runtime, resolution/file size and optional OMDb, MDBList and TMDB ratings. Enter/import keys locally or use access shared by Companion.
- **Native playback:** audio/subtitle selection, 5.1-to-stereo downmix, configurable buffers/video cache, intro/outro skipping through Jellyfin media segments and native trailer playback. Playback capabilities depend on the device and media format.
- **Live TV:** channel list, programme information and a TV guide. Live TV is hidden when unavailable on the server.
- **Persistent library cache:** optional metadata/artwork preloading for faster browsing. With Companion, completed caches receive incremental changes instead of repeated full scans.
- **TV integration:** optional Android TV home channels for playback progress and recommendations; availability depends on the launcher.
- **Adjustable appearance:** muted accent colors, optional animations, a library-based screensaver and remote-friendly settings. English and German UI throughout.

## Install and update

1. Download the release APK from [GitHub Releases](https://github.com/303inmyheart/Lowfin/releases/latest).
2. Copy it to your TV and install it with a file manager. Allow installation from that source when Android requests it.
3. Open Lowfin and enter your Jellyfin server address and account. Choose whether to enable library preloading during first-time setup.

Under **Settings → App updates**, Lowfin can check daily or manually and show the release changelog. It notifies you first; you choose when to download and install. Updates preserve the existing app data and require the same signing certificate.

## Screenshots

Captured on an Android TV device. These examples show the German interface; English is also available.

| Home | Library |
|---|---|
| ![Home and discovery banner](docs/showcase/screenshots/01-home.png) | ![Movie library](docs/showcase/screenshots/02-library.png) |

| Media details | Settings |
|---|---|
| ![Media details and ratings](docs/showcase/screenshots/03-details.png) | ![Settings categories](docs/showcase/screenshots/04-settings.png) |

| Recommendations | Genres |
|---|---|
| ![Because you watched row](docs/showcase/screenshots/06-because-you-watched.png) | ![Genre row with ratings](docs/showcase/screenshots/07-genre-shelf.png) |

![Live TV guide](docs/showcase/screenshots/05-tv-guide.png)

## More information

- [Setup, API-key import, Tracearr and building from source](docs/setup-and-development.md)
- [In-app updater](docs/app-updater.md) · [Diagnostics](docs/diagnostics.md)
- [Companion integration and technical limits](docs/lowfin-companion.md)
- [GitHub Actions builds](https://github.com/303inmyheart/Lowfin/actions/workflows/android.yml)

Licensed under [GNU GPL v2](LICENSE). See [third-party notices](THIRD_PARTY.md) for dependencies and source requirements. This product uses the TMDB API but is not endorsed or certified by TMDB.
