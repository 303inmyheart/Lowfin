# Scoped UI allocation optimizations — 1.0.8

Detail rating rows previously removed and recreated all badges at each redraw, even for unchanged cached scores after favorite/watch-list/audio changes. Retain the same badge/icon/background objects while the ordered visible-provider set is unchanged; update only changed text/accessibility labels. Keep existing badges visible during same-item fetches. Switching media clears old badges immediately, and existing binding/repository guards reject delayed results. Changed provider sets, empty results and disabled repositories still clear/rebuild as needed. Do not bypass requests or alter persistent rating-cache settings. Reserved home-shelf rating slots retain their existing behavior.

Home plugin updates previously cloned every shelf list before checking for matching IDs. Allocate only upon the first match, update every occurrence (including duplicate IDs), and retain unrelated list objects. Existing update logic still refreshes nonvisual action metadata without rebinding identical presentation. Dynamic labels, navigation, focus and library ordering are unchanged.

Remove experimental wording in DE/EN filter title as requested. No filter shader, eligibility, playback buffering, stream negotiation or 1.0.7 fallback logic changes.

Observation before this work: user stopped the requested stability observation early; record in docs/diagnostics/filter-monitor-2026-10-16.json. The last ~142-second SurfaceFlinger video-layer capture averaged 24.046 FPS, with zero discarded submitted frames and occasional longer present intervals. These statistics are not decoder-drop counts or proof of perfect pacing. No active-navigation timing incident was captured while the user was watching video. This optimization has no measured end-to-end latency/sustained-FPS improvement claim.

## Release verification

378 unit tests passed; release lint passed with warnings and no errors. Signed release 1.0.8 (80), nondebuggable, installed and package version verified on 192.168.1.128 and 192.168.1.87. Lowfin started only on .128 per latest user instruction; no launch requested on .87. APK and checksum copied to Desktop. No GitHub push.
