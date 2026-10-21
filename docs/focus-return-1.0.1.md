# Focus corrections — 1.0.1

## Shared focus outline

Motion.focus and action feedback now bound their requested scale against ancestor clipping and padding rectangles, accounting for layout positions and parent scrolling. Keep the original zoom where there is enough space; remain at scale 1 at flush edges rather than drawing a border outside a clipped parent. No blanket disabling of clipping or focus animations and no navigation-direction changes.

## Return from details

Capture the selected grid media ID, page-relative index, decorated vertical offset including margin correction, and existing layout-manager scroll state before pushing a child screen. When asynchronous cache/server lists commit, find the media by ID (nearest bounded index if absent), wait for the relevant card to attach, request focus and reinstate its saved offset. Re-resolve IDs against the current list before each delayed attempt.

Consume saved scroll state only when data exists. Fence callbacks by activity generation, current grid, selected ID and footer focus. Explicit grid navigation cancels pending restoration; ordinary fresh list commits do not refocus the first visible card if a child already has focus. Existing letter-jump priority and paging footer focus remain separate.

## Validation

New geometry tests cover edge-aligned full-width controls, spare room, a clipping outer ancestor around an unclipped inner container, and padded edges. New UI regression opens a film at index 41 in a 60-item scoped library, returns from details and checks the exact media/card focus and a nonzero viewport offset before and after fresh data. Existing navigation, settings focus, home shelves, paging, alphabet jumps and cast tests are required.

User explicitly requested installation and launch on 192.168.1.128 only for this correction. No other TV is updated by this release task.

Verified: full suite 359 tests passed; an additional animated-return regression passed. Release lint/build and the original signing fingerprint verified. Installed and started only on .128; package reports 1.0.1 (73), no DEBUGGABLE flag.
