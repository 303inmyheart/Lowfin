# 1.2.1 — compact discovery-banner information

The native discovery banner places runtime (minutes) and up to three existing Jellyfin genres immediately below its title/logo, with one-line truncation for long metadata. Missing runtime/genres produce no zero value or stray separators. Its synopsis retains a bounded three-line area. Compact source-icon rating badges sit at the bottom of the text column, outside the unchanged 2:3 poster frame. The visible left/right/OK hint is removed; the root accessibility description still explains navigation and opening details.

RatingsRow adds an opt-in compact presentation for the banner only. Ordinary home/detail rating badges retain their original typography, icon sizes, padding and behavior. Banner badges reserve the widest legal score widths for enabled sources, so delayed/missing scores do not move the synopsis. The existing selected rating sources/provider controls, optional Companion delivery and permanent on-demand client rating cache remain authoritative. There is no rating precache or new API/key setting.

Prepare cached in-memory scores immediately during binding. Start a rating request only after the banner is visible/allowed, with a 250 ms debounce to avoid fetching every rapidly passed film. Remove pending callbacks and invalidate view bindings when hidden/detached; late network results may still enter the shared rating cache but cannot replace the hidden banner's displayed scores. Film changes keep the existing binding/account guards. All sources disabled means no rating request and no badge row. Rotation, history, focus, controls, startup readiness and artwork loading remain unchanged.

Tests cover cached scores, runtime/genre formatting, absence of visible hint, information staying inside the banner/outside the poster, fixed geometry after score changes, disabled sources and missing metadata, compact versus normal typography, and rejecting a delayed hidden-view result while retaining reusable cached scores. Existing detail/back/down navigation and actual-poster pixel checks remain.

Delivery: optimized release 1.2.1 (87), install/open only .128, Desktop APK/checksum and prior Desktop APK replacement. No other TVs, shutdown or GitHub publication.

4K validation uses an actual 3840 × 2160 layout with xxxhdpi density, not an enlarged HD screenshot. Check a 504 × 756 pixel poster (126 × 189 dp), 44-pixel metadata text (11 sp), 40-pixel rating text (10 sp), proportional image fitting and all information within the banner/outside the poster. The existing HD fixture remains 1280 × 720 at tvdpi.

## Release verification

415 tests pass, zero failures/errors/skips. Release lint passes with 58 warnings and no errors. Original certificate, R8/resource shrinking and nondebuggable flags verified. Release 1.2.1 (87) installed and MainActivity opened only on .128 (PID 13985). Real HD screenshot confirms 78 min / Horror and populated IMDb, Rotten Tomatoes, TMDB, Trakt, Letterboxd and MDBList badges, with no visible remote hint. HD and native 4K layout screenshots were inspected; 4K was tested in the native layout fixture, not on a separate physical TV. Desktop APK/checksum match dist; previous Desktop 1.2.0 artifacts removed as authorized. No other TVs, shutdown, cache clearing, server changes or GitHub push. SHA-256: 1e36a31866523b65c235e57dc6a8af9fc0f3c0432042c3368fc256df473c4715.

## Additional authorized delivery

The user explicitly requested installation and startup on both remaining TVs. The same verified 1.2.1 APK was installed successfully on .87 and .166. Both report version code 87, version name 1.2.1 and nondebuggable flags; MainActivity is resumed on both. No changes or restart on .128, no shutdown and no GitHub push.
