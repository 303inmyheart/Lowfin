# Home layout — 0.10.28

## User controls

Settings → Home exposes an optional library shelf (off by default) and a remote-operated row-order dialog. Select any row and choose Move up or Move down. The selected row receives focus again after the move. Continue, Next Up, all five recommendation slots, pinned genres and Libraries have independent order keys. Existing enable/disable switches remain separate.

## Compatibility and scope

Order is persisted per server/user account in settings. Keys do not depend on translated or dynamic display titles. Missing/new rows are normalized without duplicates; temporary genre/library hiding does not erase stored ordering. The existing default order is preserved without configuration.

HomeAdapter keeps canonical row IDs for loading and callbacks and maps only RecyclerView positions through the display order. This preserves asynchronous recommendations/resume/genre updates, horizontal selections, stable IDs and navigation. Both animated and immediate navigation reveal the destination before requesting card focus, including when an earlier feature row hid its siblings.

Library metadata reuses the existing Views request/cache; no additional library enumeration or per-library metadata request. Cards use a fixed 16:9 frame and the existing wide artwork selection (Thumb, then Backdrop, then Primary fallback). Clicking opens the same scoped browser as the sidebar; no playback or media-action context menu on library tiles. There is no More tile, runtime label or progress display on this shelf. Libraries do not enter the film-description/single-shelf mode.

## Validation

New regression coverage: per-account ordering, duplicate/corrupt/stale preferences, newly available rows, dormant rows, logical/display mapping and async updates, filtering Live TV from Libraries, measured 16:9 cards and Thumb selection, scoped browse/back navigation, remote reorder dialog, and reordered genre/recommendation single-shelf navigation with dynamic labels. Existing home navigation and artwork tests remain applicable.
