# App updater

The native **Settings → App updates** page shows the installed build, latest public
release, last successful check, status and readable GitHub release notes. The page
uses existing settings spacing, switches, scrolling and focus restoration; no
WebView or alternate UI framework is introduced. English/German UI is supplied;
release notes retain their original language.

## User control

- Optional periodic GitHub metadata check every 24 hours, enabled by default.
  Android JobScheduler can delay it for connectivity/power constraints. An existing
  job is retained on process starts so frequent launches do not postpone it forever.
- Optional update notification, enabled by default. Android 13+ notification permission
  can be granted from this page. Notification availability/display also depends on
  the TV firmware/launcher. The notification opens the updater settings.
- Manual Check now bypasses the daily gate and supports conditional ETag requests.
- **Automatic work never downloads an APK or installs anything.** Select Download
  update yourself, review the changelog/status, then select Install downloaded update.
- Installation is blocked while Lowfin reports playback. The app first requests
  permission to install apps from this source if necessary. It uses PackageInstaller
  for a self-update. Android 12+ may accept the explicit install action without a
  further dialog; STATUS_PENDING_USER_ACTION remains supported and presents a
  separate confirmation action. No ADB/root/device-owner workaround is used.

The updater contacts only the public Lowfin release feed and GitHub asset hosts;
it uses an independent unauthenticated HTTP client, never Jellyfin authorization
headers. Checks use neither navigation workers nor the library write monitor.
The persisted job is separate from TV home-channel publication. No extra foreground
service, polling loop or exact alarm is added.

TV notification metadata uses the public AndroidX `NotificationCompat.TvExtender`
API (Core 1.12.0), so it remains available on supported older Android TVs rather
than calling the framework TV extender introduced as public API in Android 15.

## Release selection and validation

Only the latest stable, non-draft public release of `303inmyheart/Lowfin` is selected.
One unambiguous `Lowfin-VERSION-release.apk` asset is required, with a GitHub SHA-256
digest and a declared size of at most 64 MiB. APK URLs must match the expected
GitHub repository/tag/name. Responses and downloads have explicit limits/timeouts;
partial downloads are discarded and files remain in Lowfin's private storage.

Optional `update.json` binds the public display/internal version, package, size and
digest. Legacy release bodies may supply one unambiguous versionCode. If neither
is available, a higher numeric display version is only a discovery hint. The
downloaded archive remains authoritative: verify its checksum/length, package,
minimum Android version and current signing-certificate set. The APK's versionCode
must exceed the installed build before a session is committed. Equal/older builds
are not installed, including an older public APK sharing the local version name.
Certificate rotation requires a deliberate future compatibility change.

Install result broadcasts are private and bound to a per-session nonce/id. A
pending Android confirmation does not launch from a background check. Restarting
a manual install abandons its earlier unfinished session. Uninstall/reinstall,
cache deletion, account changes and server configuration are not part of updating.

## Preparing a public release locally

```sh
bash tools/build-public-release.sh
```

This runs tests/lint and a signed optimized release build with diagnostics off by
default, then prepares these files under `app/build/outputs/apk/release/`:

- `Lowfin-VERSION-release.apk`
- `update.json`

Upload both **only after the user's explicit publication Go**, together with the
usual checksum and release notes. No upload, release or Actions run is performed
by this script. The manually triggered CI packaging path includes the same sidecar.
For packaging an already prepared public build, `python3 tools/make-update-metadata.py`
creates the files without compiling or publishing; it does not change build defaults.

Every newly published APK needs a higher Android versionCode. Local iterative builds
keep the versions agreed in the project instructions until another version is authorized. Do not use a real release
or increment a production version merely to exercise the updater in tests.

## Platform references

- [PackageInstaller user-action rules](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int))
- [Periodic JobScheduler scheduling](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPeriodic(long))
- [AndroidX TV notification extender](https://developer.android.com/reference/androidx/core/app/NotificationCompat.TvExtender)

Test coverage includes metadata-only checks, ETag/offline behavior, notifications,
same-display-name/internal-version checks, bounded/failed downloads, package/signer
validation, legacy Android signatures, manual install sessions and pending-user-action
handling. End-to-end platform installation tests require a genuine APK with a higher internal
version and the existing signing certificate. No fake release is needed for unit tests.
