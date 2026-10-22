# Companion notice flood (1.2.20)

TV .128 logs on October 22 show sustained LowfinCache batches from 23:27:22 onward, usually 1–8 entries, frequently less than a second apart. NotificationService reports repeated Lowfin toast teardown warnings alongside them. The extended snapshot contains 136 batches / 774 entries and five journal resets (23:28:52, 23:28:53, 23:31:37, 23:31:38, 23:31:39); the client retained existing metadata as designed. These logs contain counts rather than IDs/cursors, so they do not prove whether updates are distinct or repeated, nor establish why the server resets occurred.

CacheNotice previously emitted a new toast every 1.5 seconds during continuous updates. Keep the first short coalescing window, then enforce a 60-second minimum between summaries and accumulate intervening entry counts. Empty polls, disabled notifications, background state, and retired accounts remain silent. Processing of plugin changes, cursors, metadata, artwork and invalidations is unchanged.

Tests cover continuous traffic without starving the summary, count preservation, no empty periodic notices, disabling during cooldown, foreground guards and account retirement.

Validation: all 472 client tests and release lint passed. Release signing certificate preserved. Installed on .128 with ADB Success and started MainActivity following the explicit user request. APK and checksum delivered to Desktop.
