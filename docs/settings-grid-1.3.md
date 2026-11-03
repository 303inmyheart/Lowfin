# Settings category grid (local 1.3 / 108)

The overview uses three columns and twelve native category buttons. Each has a
matching outline vector icon tinted with the current accent. The existing
submenus, translated labels, switches, persisted preferences and settings-position
restoration are reused. No new UI framework, image downloads, polling loop or
recycled category holders are introduced.

Categories retain their previous order: Home screen, Genre rows, Appearance;
Screensaver, Playback, Live TV; Library and cache, Android TV home screen, API;
Account and language, Diagnostics, App updates. Home screen receives focus on
first entry. Back from a submenu restores that category's existing view path.

## Remote navigation

- Left/Right move within the current row. Right at its end stays on the same tile.
- Up/Down preserve the column. At the first/last available row they stay in place.
- Left at the first column opens the existing sidebar; closing it restores the tile.
- The Menu button retains its existing sidebar behavior. OK opens one submenu;
  Back retains the existing submenu/settings/home/exit handling.
- Explicit D-pad handling, directional focus IDs and focus-search boundaries all
  use the same targets. Repeated edge presses cannot wrap or choose unrelated views.
- A partial future final row retains equal widths and does not move vertically
  into another column. Category labels remain the only focusable tile contents;
  accessibility exposes collection and row/column information.

Icons are 28 dp and tiles are 96 dp high, with 12 dp between tiles/rows and an
8 dp outside inset. Existing accent recoloring, focus outlines, optional pulse
and animation preferences apply. Normal HD/4K layouts fit all twelve categories;
the native ScrollView can still handle a smaller viewport.

## Regression found during validation

The full suite exposed an existing initial-library-focus timing case. A committed
nonempty result can arrive before its first card is attached. The old fallback
could then focus the available pager. A deterministic test holds card layout back
while leaving page controls visible; it fails on the original behavior. The fix
waits for a child attachment and coalesces callbacks, preserving an existing card
selection or intentional sidebar/pager navigation and rejecting old screen generations.
An empty result retains its previous page-control fallback.
