# Configurable playback and banner behavior (1.2.15)

Playback settings now expose maximum frame rate (automatic/30/60), audio channels (automatic/stereo/5.1/7.1), trailer resolution (automatic/480/720/1080/2160), subtitle language independent of app language, seek start step (5/10/15/30 seconds) and hold acceleration. Home settings expose automatic banner rotation and a 5/10/15/20/30/60-second interval.

Defaults remain 30 FPS, 5.1 with the existing stereo-downmix switch taking priority, 720p trailers, app subtitle language, 5-second accelerated seeking and 15-second banner rotation. Automatic removes application negotiation caps; device/stream capabilities still apply. These are selection limits, not frame conversion or guarantees that a provider offers each resolution. Channel limits do not force audio passthrough.

Limits apply to the server profile and native track selection. Trailer resolution applies both to muxed fallback selection and native HLS track selection. Banner manual navigation stays available with automatic rotation off. Seek labels, initial preview and held preview use the same step.

Settings continue to use SettingsColumn: 8dp control gaps, 4dp helper gaps and 16dp section gaps, with duplicate legacy spacers ignored. Account settings, focus restoration and the existing UI architecture remain unchanged.
