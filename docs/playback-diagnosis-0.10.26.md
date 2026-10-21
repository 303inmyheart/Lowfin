# Playback diagnosis — 0.10.26

TV: 192.168.1.128, Android 11, installed client 0.10.25. Read-only observation on 2026-10-15; active playback was not interrupted.

## Observations

- H.264 video hardware decoder: OMX.MS.AVC.Decoder.
- At 10:58:57 local log time, output DynamicANWBuffer setPortMode returned -1010. At 10:58:59 requests for output buffer counts 11 and 10 returned -22. First frame nevertheless rendered after 12,560 ms.
- At 11:07:14–15 the same startup warnings recurred, followed by a successful first frame after 3,736 ms. These warnings alone do not prove the cause of the reported later decoding failure.
- The original fatal ERROR_CODE_DECODING_FAILED was not present in the available log ring. No repeated fatal failure was captured during this observation.
- Sampled process PSS ~84 MiB, RSS ~92 MiB, swap PSS ~24 MiB. This snapshot does not establish memory exhaustion or exclude transient pressure.
- media.codec dumpsys service unavailable. media.metrics contained older data and did not establish the current failure cause.

## Instrumentation

0.10.26 adds error diagnostics before existing retry/fallback handling. Capture error name, playback position, buffered duration ahead, live flag, transport, renderer, format, exception class chain and available codec diagnostic code/name. Never copy exception messages or media URLs. Persist a bounded 16 KiB history in private playback-diagnostics preferences, independently of the library precache.

Log video decoder initialization, dropped-frame reports and buffering/ready transitions. No periodic polling, new decoder selection policy, codec fallback behavior, cache thresholds or server-transcoding policy changes. Tests cover format/context reporting, unavailable buffer and exclusion of exception messages.

A next failure with these diagnostics is required before choosing a targeted playback workaround. Installation is deferred while PlayerActivity is active.
