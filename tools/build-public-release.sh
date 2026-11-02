#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Locally build public artifacts with performance recording off by default.
# Does not upload, publish or start GitHub Actions.
bash "$ROOT/tools/build.sh" "$@" -PlowfinPublicRelease=true testDebugUnitTest lintRelease assembleRelease
python3 "$ROOT/tools/make-update-metadata.py"
