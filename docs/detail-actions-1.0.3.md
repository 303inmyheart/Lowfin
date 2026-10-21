# Detail actions and watch-list completion — 1.0.3

Watch later remains the existing local per-account list; favorites remain Jellyfin user data. Each has a separate action, state-sensitive label, selected indicator and add/remove accessibility description. All detail buttons use individual 18 dp vector symbols; primary playback actions remain first and selected action focus survives redraw.

Movie removal uses natural player end, or a confirmed unwatched-to-watched transition after reporting playback. Native playback adds at most one item verification on stop, only if that movie is queued. Managed playback reuses the Companion report state and effective watched status. No periodic requests or global Jellyfin completion changes. Playback errors/early interruption do not unconditionally remove entries. Already watched movies explicitly added again stay queued until an actual replay end. Episodes and series are not automatically removed. A unique local membership token prevents stale asynchronous completion from deleting a newly re-added entry. Internal tokens are omitted from returned media DTOs.

Detail membership refresh reads local preferences on the existing visible-activity status tick, so a movie removed during playback loses its saved indicator after returning.

No GitHub publication.

Watched actions offer the inverse action (mark watched / mark unwatched), including full accessibility labels. Existing context menus already use that inverse state for media and seasons. Refresh details once after returning from another activity to pick up playback status; retain the focused action during redraw.

Verified: 368 client tests passed, including independent reversible watch-list/favorites/watched UI state, per-action vector presence, completion protection for incomplete playback, effective Companion status and re-added entries. Release lint and assembly passed; signature preserved. HD detail screenshot inspected. Installed and opened on .128, package confirms 1.0.3 (75), nondebuggable. Desktop APK/checksum replaced the preceding version.
