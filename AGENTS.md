# Project instructions

## Current release coordination

- Keep Lowfin at `versionName 1.5` and Android `versionCode 110` throughout the current unpublished work, including further optimization and local test builds.
- Keep Lowfin Companion at `0.7.0` throughout the current unpublished work.
- Do not increment these versions or push/publish either project or its release artifacts without the user's explicit go-ahead. A push authorization means publishing the agreed versions; it does not by itself request a version increment.
- Local builds and authorized TV installations do not authorize GitHub pushes, releases or workflow builds.

# Publication privacy

Before every commit/push/release, run tools/check_privacy.py on the exact staged or exported content and on all release packages. Never publish personal account/device names, private network addresses, home-directory paths, credentials, conversation excerpts or internal installation/usage logs. Keep private reports and backups under ignored local directories. The owner allowed existing image content except the retired drawer screenshot; do not infer broader permission for new personal disclosures. Preserve third-party license/attribution notices. Use anonymous compiler PathMap settings and verify plugin DLL bytes before packaging. History rewrites and asset replacement require explicit authorization.
