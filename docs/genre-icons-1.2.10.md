# Genre icons · 1.2.10

Read-only same-signer instrumentation inspected the active account’s stored Movie/Series genre names on TV 192.168.1.128. It returned only genre names, not credentials. The temporary de.lowfin.tv.test package was removed afterwards and Lowfin reopened.

The genre overview now uses bundled vector assets with a consistent 1.55-unit rounded outline and accent tint. Glyphs sit in a 32 dp frame to the left of the name and existing count; card dimensions, focus target, click filtering and navigation are unchanged. Plain ImageViews do not create bitmap downloads/cache entries. German/English aliases and accented names are normalized for icon lookup only; display labels and Jellyfin metadata are not modified. Unsupported future genres receive a film icon.

The malformed ['Gaming'] entry is omitted from GenreAdapter; the normal Gaming genre remains visible. Tests cover the inspected taxonomy, aliases, recycled tile identity/click target and exclusion of the malformed label. The existing TV UI test verifies opening a genre, its Movie/Series filter and return navigation. Visual QA artifacts: app/build/qa/genre-icons.png and genres-hd.png.

No Companion change or server update is required.
