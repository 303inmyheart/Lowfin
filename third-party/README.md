# Corresponding FFmpeg extension sources

The APK bundles Jellyfin media3-ffmpeg-decoder 1.8.0+1, enabled only during Live TV.

Wrapper/build commit: 84ef4b7201a9e1edfc699c31acbc505511a4af1f
FFmpeg commit: d388c347d41e4eb516dec05910551c5461e65615
AndroidX Media commit: b7bbc6e2bc3e45ff3ed99884c114c50f03bba5c9

The upstream source archives accompany these build scripts. Extract the archives under `ffmpeg-decoder/ffmpeg` and `ffmpeg-decoder/media` (strip the archive's single root folder), then follow `ffmpeg-decoder/build.sh` and its Gradle build files using the Android NDK. FFmpeg configuration and enabled decoders are specified in that script.

Modified decoder AARs can replace the Maven dependency using `implementation files('libs/modified-decoder.aar')` in app/build.gradle. Rebuild and re-sign the APK to replace the shared decoder library. Reverse engineering for debugging modifications to these libraries is permitted.

The original wrapper Apache-2.0 license is in ffmpeg-decoder/LICENSE. FFmpeg's LGPL license and full sources are in FFmpeg-source.tar.gz; AndroidX Media's license/source are in AndroidX-Media-source.tar.gz.

The AndroidX archive omits test-only binary media fixtures under src/test/assets and src/androidTest/assets. Production source, JNI code, build files, and licenses are preserved.
