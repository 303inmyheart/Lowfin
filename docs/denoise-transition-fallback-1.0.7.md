# Episode-transition false denoise fallback — 1.0.7

Actual .128 event at 2026-10-16 17:27:07 local: buffering at position 1210499 ms, followed by decoder notification of 42 drops over 554940 ms, then fallback. No frame-processing exception. The absolute-count guard (count >=12, interval >=1 second) wrongly treated a roughly nine-minute aggregate as a short overload.

Require a short 1–10 second callback interval with at least 12 drops and at least 3 drops/second, plus active READY playback continuously settled for five seconds. Reset the settled timer on preparation, non-READY states and pause. Check state again inside delayed fallback. The actual processing-error fallback remains independent. Keep a reason-specific log.

Regression tests include the exact reported event, state transitions, pause, warmup, sparse drops, invalid intervals and real high-rate drops. General optimization edits and label removal were saved for later, not included in this build.

Verified: 374 client tests passed, including the exact 42/554940 ms event and real overload cases. Release lint/build passed, original signing identity retained. Installed and opened 1.0.7 (79) on .128, nondebuggable. Desktop APK/checksum replaced the prior version. General optimizations/label change excluded and saved for later. No GitHub push. No second long episode-transition playback test has been performed after this installation.
