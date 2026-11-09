# Lowfin setup and development

[Project overview](../README.md) · [Changelog](../CHANGELOG.md)

## Settings

The settings screen presents the twelve settings categories in a three-column grid with accent-colored icons. D-pad navigation stays within each row/column, Left opens the sidebar at the left edge, and Back from a submenu restores its category tile. Home screen is selected on first entry; HD/4K scaling and English/German labels are retained.

Submenus use category context beside compact grouped controls. Under **Appearance → Sidebar layout**, arrange menu entries separately from libraries: menu order is local to the account/device, while library order is saved to the Jellyfin user profile and followed by clients when they refresh it. Press OK on a row, move it with Up/Down, press OK to finish, then Save. Cancel discards the draft; a conflicting server order requires reload/review. [Details](settings-submenus-and-sidebar-1.3.md).

Lowfin includes **Settings → Diagnostics** for bounded in-memory performance recording and a private ADB interface. Local builds default to on; public release builds default to off. See [diagnostic commands, data and build options](diagnostics.md).

Lowfin also provides **Settings → App updates**: optional daily GitHub checks and notifications, a manual check and readable release changelog. Downloads and installation start only when you select their buttons. Android may require permission/confirmation. See [updater behavior and local release packaging](app-updater.md).

English is the default language regardless of the device language. Select **Language → Deutsch** to use German; the language picker is also available before sign-in. UI, dialogs, player, trailer messages and TV channel names are translated. Library titles, genres and descriptions retain the server's language.

- **Genre rows:** individual account-scoped genre checkboxes, global visibility and the single-focused-row view.
- **Home screen and appearance:** Continue watching and Next up visibility, 0–5 recommendation rows, poster columns, roulette duration, random/fixed muted accent colors, foreground-time color interval, automatic preloading and cache refresh interval.
- **Advanced playback:** individual intro/outro skipping, default subtitles, preferred audio language, seek interval, controls timeout and separate VOD/live RAM buffer ceilings. Buffers always remain adaptive to available memory.
- Existing controls include playback resolution/bitrate, stereo downmix, optional transcoding, disk cache size, artwork budget, rating sources/API keys, Android TV channels and their library sources.

## Import rating API keys

**With Lowfin Companion:** configure OMDb, MDBList, TMDB and Tracearr in the Jellyfin plugin dashboard and share access with all or selected Jellyfin users. For shared providers, local key-entry/import and Tracearr connection controls are hidden; each TV still chooses which providers and ratings to show and whether to cache ratings. The following TXT instructions apply to locally configured connections when server-managed access is unavailable. The Companion manages credentials centrally; it does not read the Android Download TXT file.

When no rating API keys are saved, Lowfin creates **Download/Lowfin-API-Keys.txt** on launch. Fill the provider fields, save as UTF-8 and return to Lowfin. The app checks the file every five seconds while open, encrypts the supplied keys and deletes the file after successful storage. Leave unused providers empty. Existing keys disable automatic import; individual keys remain editable in rating settings. A TXT picker is available if your editor replaces the original Download entry.

```ini
OMDB_API_KEY=
MDBLIST_API_KEY=
TMDB_API_KEY=
TRACEARR_URL=
TRACEARR_API_KEY=
TRACEARR_SERVER_ID=
```

Use the raw key/token after `=` without quotes or a `Bearer` prefix. The generated file explains each provider.

## Build and verify

With JDK 17 and Android SDK platform/build-tools 35 installed, set your local SDK path and use the Gradle wrapper:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleRelease
```

On Linux, `bash tools/bootstrap.sh` installs project-local build tools under `.tools/`; then `bash tools/build.sh testDebugUnitTest lintDebug lintRelease assembleRelease` runs the same checks. Bootstrapping accepts Android SDK licenses. Tools, SDK paths, build directories and private signing keys are excluded from Git.

The `release` APK uses R8/resource shrinking and the local Android debug signing configuration. An existing installation can only be updated using the same signing certificate. The supplied APK preserves the certificate used by earlier Lowfin builds.

Automated tests use JUnit, Robolectric on Android 9 and Android 11, mock HTTP servers and a fake Android TV provider. The release includes [BUILD.json](https://github.com/303inmyheart/Lowfin/releases/latest/download/BUILD.json) with its test count, APK checksum and certificate. Real device captures are shown in the project README; synthetic UI renderings are produced under `app/build/qa/` during tests. Physical TV/live-server behavior is not validated by these simulated tests.

## GitHub builds

[Android build](https://github.com/303inmyheart/Lowfin/actions/workflows/android.yml) is manual-only (`workflow_dispatch`). Source pushes do not start a build or consume build minutes. When explicitly started, it checks tests and lint, builds a signed release APK and publishes `Lowfin-<version>-build-<run>` containing the APK, SHA256SUMS and BUILD.json. Test/lint reports and HD screenshots are uploaded separately. APK artifacts are retained for 30 days; reports for 14 days.

The release workflow uses the encrypted `LOWFIN_SIGNING_KEYSTORE` Actions secret for the existing Android debug signing key. The job checks that the APK retains the supplied release's certificate, so it can update an existing installation. The workflow fails if the signing secret is absent, rather than publishing an incompatible APK. Private keys are never committed or included in build artifacts. All actions are pinned to specific upstream commits; the workflow has read-only repository permissions.

## License and dependencies

Lowfin is GNU GPL v2; see [LICENSE](../LICENSE) and [THIRD_PARTY.md](../THIRD_PARTY.md). The bundled FFmpeg decoder includes corresponding wrapper, FFmpeg and AndroidX Media source archives in `third-party/`, with rebuild instructions. Source packages, APKs and verification reports from previous local releases are retained under `dist/`.

This product uses the TMDB API but is not endorsed or certified by TMDB. OMDb and MDBList are optional external services; their own terms apply.

### Tracearr playback statistics

Settings → API → Tracearr connects to the **Public API v2**. Create a Public API token in Tracearr under Settings → Data & API → API. Enter the root URL (preserving any reverse-proxy subpath) and token, then select the matching Jellyfin server and test the connection. The logged-in Jellyfin user is mapped by external user ID; names are never used as a fallback. Older Tracearr instances without API v2 need an update.

Counts include only stopped resume chains marked `watched` by Tracearr. They follow Tracearr’s configured completion rule, not proof that every scene played. Series completions are the minimum completed-play count across all episodes currently available on Jellyfin, **including specials**. Newly added episodes may reduce this count. Missing account mappings, unavailable history and incomplete pagination are shown as unavailable, never zero. Pre-recording history cannot be reconstructed.

For Tracearr, the same TXT template uses:

```ini
TRACEARR_URL=http://your-tracearr-server:3000
TRACEARR_API_KEY=trr_pub_your_token
TRACEARR_SERVER_ID=
```

The optional server ID is Tracearr’s Jellyfin-server UUID, not Jellyfin’s own server ID. Leave it empty and choose the server in settings. Tracearr credentials imported while logged in belong to that Jellyfin account.

The **Create API key file again** button is available in API settings even with saved credentials. It creates a blank template; it never exports stored keys or overwrites an existing download file. Filled fields in an explicitly requested import replace those values; **empty fields preserve saved keys**. Successful encrypted storage is verified before the file is deleted. Unattended initial file creation remains disabled once credentials exist.

## Server companion and language

[Lowfin Companion](https://github.com/303inmyheart/Lowfin-Companion) has its own source repository, changelog, releases and Jellyfin update manifest. Clone it into the ignored local `companion/` directory if running the local server integration tools from this checkout. The client never requires the plugin.

Lowfin defaults to **English**, independently of the Android system language. Switch between English and German under Settings → Account and language. UI text, settings, playback actions and import templates are localized; movie/show titles, genres, descriptions and remote server errors retain the language supplied by your Jellyfin server.

New installations ask after sign-in whether to enable library precaching. Until a choice is made, automatic library preloading waits. Existing installations keep their preference; change it under Settings → Library and cache.
