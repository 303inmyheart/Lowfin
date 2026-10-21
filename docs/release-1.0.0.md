# Lowfin 1.0.0 — local release

Client versionCode 72; optional Companion remains 0.3.0. This release consolidates the existing application. No GitHub push, public release or server-plugin update is authorized by the 1.0 naming alone.

## Final optimization scope

- Reuse each control’s own GradientDrawable for focus/accent updates. Keep original fill colors, 2 px strokes, 8 px corner radii and all existing focus listeners, animations and navigation. No shared drawable across controls. Covers general, fixed, card, sidebar, quiet and primary styles.
- Compare cast entries by displayed/action identity fields (Id, Type, Name, Role and primary image tag), preserving order. Avoid allocating complete JSON list strings on repeated detail redraws. Changed roles, images, actor identity and list order still notify the adapter.
- Retain 0.10.29’s actual-provider comparison for Android TV cards, Projectivy heading notifications, nonblocking cache-byte reads and bounded slow-key logging.

No playback, decoder, buffer, preload, plugin-completion or cache-update-policy changes. Existing intermittent long input delays are not claimed fully resolved. No measured universal FPS gain.

## Upgrade and validation

TV .128 was on 0.10.29 (71); .166 and .87 were on 0.7.25 (38). Added a direct pre-Companion schema-4-to-8 regression preserving metadata, library membership, genres, ratings state, lastSync and playback position, then verifying the new Companion visibility field. No production database wipe or forced cache rebuild.

Focus tests repeatedly move focus between separate controls and verify drawable identity, independent state and normal/card fill colors. Cast tests verify unchanged data avoids rebinding and changed roles, portraits, identities and order still refresh. Run the full client suite, release lint and release build and all 40 Companion unit tests.

Use one identical nondebuggable, R8/resource-shrunk APK and the existing signing certificate on all three TVs. Install via adb install -r, preserving app data. Per the latest user instruction, launch Lowfin afterward only on .128; do not launch it on .166 or .87. Record actual install results and package version verification separately. Keep a release APK and SHA256 on the Desktop; older known Lowfin Desktop APKs can be replaced.

Final local checks: 357 client tests, 40 Companion tests, release lint and assembleRelease passed. An initial targeted rapid-scroll test timed out (the same test had also timed out before these changes); the final complete run passed it without relaxing its assertions. Release signing fingerprint retained; manifest nondebuggable. See dist/verification-1.0.0.json for checksum and per-device installation results.

Installation completed: adb update succeeded on .128, .166 and .87. Each device reports versionName 1.0.0, versionCode 72 and no DEBUGGABLE flag. Lowfin was explicitly started only on .128. Application data was not cleared. The upgrade-path regression is not a substitute for testing every function on the two older TVs after their next normal launch.
