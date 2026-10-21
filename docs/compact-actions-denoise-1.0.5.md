# Compact detail actions and optional denoise — 1.0.5

Actions use fixed 104 × 38 dp boxes, 11 sp text (up to two lines), 14 dp vector icons, 5 dp inner padding/icon gap and 6 dp trailing gaps. Eight actions occupy 880 dp, fitting the 892 dp HD content viewport. No count-dependent resizing or hidden actions. Selected audio remains checked in its chooser and in accessibility text. Existing membership state, primary focus, right-edge confinement and cast-to-toolbar scroll are retained.

Video noise reduction is an explicit Playback setting, off by default. Match Media3 effect to existing 1.8.0 dependencies. One local five-tap spatial RGB filter with color-distance weights and a 0.65 blend; no temporal frame retention, CPU frame readback, timestamp changes or server transcoding. This is gentle denoising, not AI restoration or motion-compensated temporal denoising.

Require confirmed SDR video stream dimensions up to 1920×1080 and at most 8-bit; reject live TV, encrypted sources, unknown metadata and HDR transfers/ranges. Runtime shader construction/configuration also rejects unexpected HDR or oversized frames, letting the player retry without effects. Do not initialize the effect pipeline when off or ineligible. Keep one output texture. A processing initialization/frame-processing error, or at least 12 dropped frames over an interval of at least one second, disables effects for the current player activity and reopens at the current position with the existing transport. The saved preference remains untouched. Off settings impose no video shader pass.

Upstream references inspected for the actual 1.8.0 version:
- https://raw.githubusercontent.com/androidx/media/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java
- https://raw.githubusercontent.com/androidx/media/1.8.0/libraries/effect/src/main/java/androidx/media3/effect/GlEffect.java
- https://raw.githubusercontent.com/androidx/media/1.8.0/libraries/effect/src/main/java/androidx/media3/effect/BaseGlShaderProgram.java

Validation: HD UI checks with all eight actions, eligibility unit tests, existing playback/navigation suite, release lint/build. An opt-in instrumentation diagnostic compiles the actual shipped shader on an isolated EGL pbuffer and verifies identity bypass, noise reduction, contrast edges and alpha; it does not change account/cache/playback preferences. Shader verification is not a full-film playback or sustained-performance benchmark.

Verified: 372 client tests passed, release lint/build passed; same signing identity. Device .128 reports 1280×720 at 213 dpi; fixed eight-button row fits the corresponding HD viewport. Inspected HD detail rendering. Installed 1.0.5 (77), nondebuggable. Isolated EGL GPU check on actual Mali-G31 passed shader compilation, identity bypass, noise reduction, preserved contrast edge and alpha. Diagnostic APK removed and Lowfin reopened. No full-film or sustained GPU performance benchmark performed; filter remains experimental and off by default. Desktop APK/checksum replaced the previous local version. No GitHub push.
