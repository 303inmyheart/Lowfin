#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/.tools"
mkdir -p "$DEST"
if [ ! -x "$DEST/jdk/bin/java" ]; then
  curl -fL --retry 3 'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse' -o "$DEST/jdk.tar.gz"
  mkdir -p "$DEST/jdk"
  tar -xzf "$DEST/jdk.tar.gz" -C "$DEST/jdk" --strip-components=1
fi
if [ ! -x "$DEST/gradle-8.10.2/bin/gradle" ]; then
  curl -fL --retry 3 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' -o "$DEST/gradle.zip"
  unzip -qo "$DEST/gradle.zip" -d "$DEST"
fi
if [ ! -x "$DEST/android/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fL --retry 3 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' -o "$DEST/android-tools.zip"
  mkdir -p "$DEST/android/cmdline-tools"
  unzip -qo "$DEST/android-tools.zip" -d "$DEST/android/cmdline-tools"
  mv "$DEST/android/cmdline-tools/cmdline-tools" "$DEST/android/cmdline-tools/latest"
fi
export JAVA_HOME="$DEST/jdk"
export ANDROID_HOME="$DEST/android"
export GRADLE_USER_HOME="$DEST/gradle-cache"
export PATH="$JAVA_HOME/bin:$PATH"
# License acceptance is explicit in this project-local setup script.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null
set -o pipefail
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/local.properties"
echo 'Build-Werkzeuge sind im Projektordner eingerichtet.'
