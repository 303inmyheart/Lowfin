#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="$ROOT/.tools/jdk"
export ANDROID_HOME="$ROOT/.tools/android"
export GRADLE_USER_HOME="$ROOT/.tools/gradle-cache"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$ROOT"
exec "$ROOT/.tools/gradle-8.10.2/bin/gradle" --no-daemon "$@"
