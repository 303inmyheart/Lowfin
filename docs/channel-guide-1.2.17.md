# Channel programme guide (1.2.17)

Long OK on a channel opens a native Lowfin guide dialog for that channel. It lists the currently airing programme and upcoming entries with local dates, start/end times, titles and available episode titles. The current show is marked Now. OK opens a bounded description dialog; dismiss restores focus to the selected guide row. Short OK on the channel still starts playback. The Live TV page remains a channel grid and the availability check introduced in 1.2.16 remains intact.

Queries are user/channel scoped and ordered by StartDate ascending. Twelve entries are shown per page, with one lookahead row determining Next. Previous is hidden on the first page; Next is hidden on the last. The guide-open timestamp is retained while paging so normal time progression does not shift offsets. Refresh starts a new snapshot from now. Only requested pages load, without cover requests or a whole-server guide/cache scan. Native API timeouts apply. Channel IDs and date ranges are validated. Stale results cannot modify dismissed dialogs, newer pages or another account.

Rows use fixed focus outlines and small padding gaps without focus scaling/clipping. Dates and EN/DE text follow the existing localization; dialog width/height remain bounded by LowfinDialog/DialogViewport and long lists scroll.

Verification covers bounded future queries, lookahead exclusion, invalid rows, upcoming-page navigation, details focus return and no unintended playback, alongside existing regression tests. User requested installation on .128/.87/.166, launch only on .128, then power off .87 and .166 after successful installation.
