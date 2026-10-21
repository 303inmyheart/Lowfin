# Denoise release initialization — 1.0.6

Actual .128 log: ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED before the first decoded frame. Root cause NoSuchMethodException for androidx.media3.effect.DefaultVideoFrameProcessor$Factory$Builder.<init>(). The 1.0.5 R8 mapping retained reflected builder methods but omitted its default constructor. This was not a dropped-frame fallback or shader compilation failure.

Media3 1.8.0 exoplayer consumer rules preserve build() and setEnableReplayableCache(boolean), but omit the reflected builder constructor. Add a scoped constructor keep rule; keep optimization/shrinking for the rest of the app. Preserve existing failure fallback and experimental filter constraints.

Extend the on-device diagnostic to load the actual installed target classes and invoke the same public no-argument builder constructor, setEnableReplayableCache, build, and SingleInputVideoGraph factory constructor. Then run the shipped shader pixel check. This specifically tests the release reflection boundary missed by the original isolated GPU shader test; it is not a full-film playback benchmark.

Verified: 372 tests, release lint and release build passed. R8 mapping now contains the retained no-argument constructor. Installed and verified 1.0.6 (78), nondebuggable, original signing identity. On .128 the optimized-target builder constructor, reflected methods and graph factory construction now pass, followed by the real Mali-G31 GPU pixel checks. Diagnostic APK removed; Lowfin reopened. Full-film/sustained playback remains unmeasured. Desktop APK/checksum updated. No GitHub push.
