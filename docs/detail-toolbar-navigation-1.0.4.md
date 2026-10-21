# Detail toolbar navigation — 1.0.4

Each detail action handles right-arrow key down/up locally. Key down focuses the following action when present; the last action consumes right without moving. This is rebuilt with the row, so optional Companion/audio/trailer/restart buttons retain correct boundaries. No changes to down/up, cast scrolling, drawer logic, playback or saved selection.

Regression cases navigate the entire scrollable action row with and without Companion, repeated/held right at its final button, left/back-right within actions, down to a populated cast row, and cast-left without opening the drawer.

Primary detail actions use a bright focused fill, dark 2 dp stroke and matching dark text/icon. The unfocused fill keeps the current accent with luminance-based foreground contrast. Reuse the existing drawable and retain Motion.focus and animation settings. Accent updates reapply the correct focused appearance.

When any detail action gains focus, post a guarded scroll to page top after Android focus handling. Use smooth scrolling only when both UI and scroll animations are enabled. Ignore delayed work if the action has lost focus or the page detached. The toolbar-right regression also scrolls into cast and checks one up press focuses an action and returns the page to zero.

Verified: 371 client tests passed; release lint/build passed and original signing identity preserved. Both toolbar-edge tests passed with cast-to-toolbar scroll-to-top, and the high-contrast play/resume test passed across blur and accent reapplication. HD detail rendering inspected. Installed and launched on .128, package reports 1.0.4 (76), nondebuggable. Desktop APK/checksum updated. An earlier run before the final focus/scroll additions had a timeout in the existing animated-home navigation stress test; the complete final rerun passed without changing that test. No GitHub push.
