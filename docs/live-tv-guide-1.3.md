# Live TV programme guide — local 1.3 / 108

The initial guide implementation and its first device verification are documented
below. Subsequent local work replaces fixed eight-channel pages with configurable
10–50 channel pages (default 25), recycled rows, compact paging and an optional
default-on guide-data filter. See [the current pagination design](pagination-and-page-sizes-1.3.md).

## Entry and design

The existing Live TV channel grid remains the entry screen. An explicit **TV guide**
button in that screen's heading opens the new native guide. Up from a rested first
channel row reaches that button; ordinary channel entry retains media-card focus.
Back and the guide's Channels button return to the existing sender list.

The guide uses Lowfin's background, accent, fixed focus outlines and native dialogs.
Channel logos retain their aspect ratio and transparent backgrounds. A two-hour
window displays four half-hour labels, proportionally clipped programme cells,
explicit EPG gaps and an accent current-time marker. A bounded eight-channel page
scrolls vertically above a separate, fixed programme-description area. The guide
has a seven-day date chooser, Now, Earlier/Later and conditional channel-page
controls. Short cells ellipsize while the selected full title, time and description
remain available below or in a details dialog. Watch channel starts the selected
channel through the existing playback route, including when a future show is selected.

## Navigation and lifecycle

Up/Down between channels retain a temporal anchor, rather than matching unrelated
column indices or drifting across programmes of different durations. Left/Right
move between adjacent cells and the channel label. Only Left at a channel label or
the first toolbar control opens the sidebar. Toolbar/pager navigation has explicit
boundaries. Returning from details restores the selected programme.

Guide state belongs to the existing MainActivity screen. There is no WebView or
new client architecture. Pause/close removes the minute callback and invalidates
in-flight responses; every response is fenced by generation, account and activity.
A minute callback repaints the current-time marker while visible. Follow-Now data
rolls at half-hour boundaries; a manually chosen future window stays selected.
Resume reloads the bounded channel page and programmes. Now also refreshes manually.
No whole-server EPG scan or persistent programme-artwork cache is introduced.

## Jellyfin API and resource bounds

Use the configured server and existing user authentication. `GET /LiveTv/Channels`
requests eight channels plus one lookahead, sorted as the existing channel screen,
without a separate current-programme query per channel. `GET /LiveTv/Programs`
requests only these channel IDs with `MinEndDate` and `MaxStartDate`, retaining
programmes that started before the window but still overlap it. Programmes are
ordered by StartDate/Name and requested without images/user data, with Overview.
Dense responses use 256-item pages plus one lookahead and a hard eight-page limit;
exceeding that limit is an error rather than a silently incomplete guide. HTTP
calls retain the existing bounded LiveTv transport and diagnostics. Channel-page
changes and time changes cannot mix cached rows from different channel pages.

The pure GuideWindow model rejects malformed dates, zero-length, unrelated and
out-of-window rows, normalizes channel GUIDs, sorts out-of-order listings and
clips duplicate/overlapping rows. Native segment layout uses absolute time edges
rather than subtracting per-cell margins from weighted widths. The API is read-only;
stream negotiation, server recording rules, Companion state and cache policy stay
with the existing implementations.

Primary API reference: [Jellyfin LiveTvController](https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Api/Controllers/LiveTvController.cs).

