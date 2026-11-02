# Native settings submenus and sidebar order (local 1.3 / 108)

## Analysis and implementation

The previous submenus were full-width stacks of individually filled buttons.
They are still native Android controls, now presented beside an accent icon,
category title and short explanation. The right-hand list uses compact flat rows,
native switches, quieter helper text and section headings. Group playback by
video/audio/subtitles/episodes/player/trailers/buffer, and separate cache cleanup
from background options. Existing setting keys, defaults, conditional Companion
controls and callbacks are retained. Category tiles stay unchanged.

SettingsPosition now finds the focused control's ancestor ScrollView, so nested
pages retain both the value-row focus and scroll offset after rebuilding. A page
selects its first enabled control on entry; restoring a saved control takes
precedence. Provider icons are retained and row styling is applied once, including
when sorting reuses a button. No new rendering framework, artwork loads or polling
loop is introduced. English and German strings are provided.

## Separate sidebar editors

**Appearance → Sidebar layout** exposes two editors:

- Menu entries above libraries: Home, Search, Genres, Live TV when available,
  Random movie, Favorites and Watch later. Persist semantic IDs in account-scoped
  device preferences, retaining dormant/hidden entries. Settings remains accessible
  below libraries; contextual genre actions remain separate.
- Libraries: load the real user's configuration and the server's visible views.
  Read and update native `OrderedViews`; there is no independent local override.
  Hidden libraries and external views retain their slots. Normalize GUID comparison
  and serialize valid GUIDs in canonical dashed form. Preserve the full, latest
  unrelated user configuration. No Companion update is needed.

The current LAN server reported **12.4.0**. Its own OpenAPI schema confirms
UserConfiguration.OrderedViews as an array of UUIDs. The implementation prefers
`POST /Users/Configuration?userId=…`, falling back to the legacy route only on
404/405. Permission failures do not trigger another write. User/View GET routes
retain the existing client compatibility path.

Select a row with OK, move it with Up/Down and press OK to finish moving. Back while
moving restores that row's previous draft position; Cancel discards the complete
draft. A short list gets a compact dialog; a long list scrolls after 200 dp. Reuse
the existing row buttons to retain focus through fast input instead of rebuilding
them each time. Only Save writes. Loading and saving run on background workers;
ordering HTTP calls have bounded timeouts. Closing an already submitted save does
not undo a server commit.

## Synchronization and concurrency limits

Before saving, re-read the user configuration and library membership. A changed
order or changed available library set requires reload/review; an already-applied
desired order avoids another write. After the POST, read back configuration/views
and verify the result. A conflict never triggers a rollback or blind retry loop.

Jellyfin exposes a full-configuration update, without an atomic order-only PATCH
or compare-and-swap contract. The fresh read and verification reduce stale writes,
but cannot make concurrent whole-profile updates atomic between clients. Other
clients follow their own refresh lifecycle. Lowfin refreshes views on normal
foreground/sidebar visits at most once per minute, skips unchanged cache/launcher
updates and fences older in-flight views after a saved order. Direct TV-card
playback keeps its deferred-home fast path and starts without this extra request.

Sidebar rebuilds restore focus using navigation/library IDs, so translation,
renaming or duplicate library names do not determine identity.

## Primary API references

- [Jellyfin UserConfiguration model](https://github.com/jellyfin/jellyfin/blob/master/MediaBrowser.Model/Configuration/UserConfiguration.cs)
- [Jellyfin user configuration controller](https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Api/Controllers/UserController.cs)
- [Jellyfin user view ordering](https://github.com/jellyfin/jellyfin/blob/master/Emby.Server.Implementations/Library/UserViewManager.cs)
