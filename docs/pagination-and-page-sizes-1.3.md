# Compact pagination and configurable page sizes — local 1.3 / 108

## Shared page controls

The existing PageControls is reused by libraries, search/special lists, genre/person
lists, channel schedule dialogs and the TV guide. Its footer now centers a quiet
page number between small vector chevrons. Arrows use the current accent, transparent
idle backgrounds and a fixed, inset focus ring. They remain native Buttons with
localized accessibility descriptions and fixed 36 × 32 dp bounds. There is no focus
zoom, bitmap, new rendering dependency or large filled text-button panel.

Previous/Next only exist visually when that direction is available. Existing
lookahead, footer-to-card return, portrait/landscape grid spans and external focus
restoration guards remain active. The TV guide keeps its own deterministic pager
navigation around the shared footer. HD/4K screenshots cover both designs.

## Seekbars and remote acceleration

- Live TV: **Channels per guide page**, 10–50, default 25.
- Library and cache: **Items per library page**, 50–150, default 60.

RangeChoice uses native SeekBar minimum/maximum values, a live numeric label,
accent thumb/track, range labels and existing Lowfin dialogs. Save writes the
value; Cancel discards the draft. The existing settings-position mechanism restores
the edited row and scroll position after Save. Guide-only **Hide channels without
guide data** is enabled by default and remains a standard native settings switch.

Single Left/Right presses change one unit. Repeats are limited to one update every
120 ms; increments are 1 initially, 2 after 700 ms, 5 after 1600 ms, and at most 10
(or 5 for the smaller guide range) after 3000 ms. Release, direction change,
focus loss and detachment reset the hold. Values are clamped to the real bounds.
No polling/timer thread drives the slider. Touch and accessibility retain native
range semantics.

## Library consistency

Each library/episode/started-episode screen captures its chosen size once. Both
server and cached reads use that same size plus one lookahead, with offsets
`page * size`; letter jumps use the same quotient/remainder. Returning from a detail
view preserves the original screen's size and media identity even if preferences
changed meanwhile. Reopening the list uses the new setting. Season grids retain
their existing full-season overview, and unrelated search/favorites/genre/person
page sizes retain their existing limit. Started-episode snapshot keys include
non-default sizes, while keeping the existing default-size cache compatible.

An integration test with changed cached/fresh metadata exposed an early focus
release: a response being received does not mean AsyncListDiffer has committed it.
MediaAdapter now distinguishes server receipt from committed server content. Cached
focus restoration retains the desired media ID until the fresh list commits; a
new user navigation still cancels that pending restoration immediately.

## Guide filtering and resource bounds

LiveGuide now recycles native channel rows through the existing RecyclerView
stack. Only attached rows and a small view cache own programme Buttons/ImageViews;
preparing/grouping the full chosen page's timeline models happens on the navigation
worker. A pending offscreen row/time anchor survives rapid directional events and
is fulfilled when its holder attaches. State belongs to the existing MainActivity
screen, with the same generation/account/lifecycle fencing as the guide entry build.

The guide filter never changes the normal Live TV list or server configuration.
GuideSource walks bounded 50-channel candidate batches, collecting enough eligible
channels for the chosen page plus lookahead. It retains that cursor during paging
so removed entries do not shrink pages or skip eligible channels. A user-scoped
EPG presence batch asks only for those channel IDs and programmes ending after
now, without a selected-window start cutoff. Thus a channel with only upcoming
data remains present during a temporary window gap. An empty/complete response
proves absence; a 513-item truncated response triggers bounded follow-up batches
for the still-unknown channels. Subsequent probes return at most 17 records; each
truncated answer must identify additional positive channels or fail explicitly. Errors propagate instead of becoming false absence.
Requests and parsing run off the UI thread and check cancellation between calls.

Presence/cursors live only in this guide instance. Resume, Now and half-hour
Follow-Now refreshes revise the catalogue. Changing filter/page size restarts page
zero; normal channel ordering is retained. Malformed/repeated channel pages and an
oversized catalogue fail explicitly rather than creating an unbounded scan loop.
Turning filtering off restores direct server channel pagination without EPG probes.
