# Projectivy channel title diagnosis

Local investigation, 2026-10-13. No GitHub publication authorized.

The installed Lowfin release publishes recommendation programs with stable channel IDs. A read-only instrumentation APK queried the TV provider under Lowfin's own UID and confirmed that all five recommendation rows have current display names. Projectivy's visible recommendation headings can still retain earlier names while their programs update.

The installed Projectivy code keeps a cached channel list (`y60.b()` in the locally decompiled APK), and its category heading reads a custom title when present or the stored channel object's `display_name` (`d5.h()`). These obfuscated identifiers are specific to the inspected APK. A Projectivy restart did not resolve the reported heading mismatch. Clearing the fixed title on one automatically named recommendation category also did not establish a working automatic refresh. Category activation was preserved.

The subsequent Smali analysis identifies the cause directly. In the installed APK's `o40.smali`, channel import sets `d5.t` from `display_name`, then constructs `d5.l` (the serialized `channelUniqueId`) by concatenating the package name with `display_name`. `d5.d()` uses the hash of that string as the category identity. A renamed channel therefore changes Projectivy's category identity even though Android's channel ID and Lowfin's internal provider ID remain stable. `p40.j()` preserves category preferences against this identity. This explains both stale old categories and newly imported hidden categories.

Changing notification timing or supplying an unchanged internal provider ID cannot fix this naming-based identity. The inspected import path does not use that provider ID for category identity. No supported original-launcher setting/API to replace this identity was found. The user requires dynamic headings and has rejected both fixed headings and a modified launcher APK. Under those constraints there is no verified Lowfin-only fix. A launcher-side fix would key categories by package + internal provider ID (with Android channel ID as fallback) and refresh imported titles while preserving user custom titles and visibility.

This is a diagnosis, not a completed runtime fix. Do not recreate Lowfin channels as a workaround: that can lose the user's launcher activation and order.

To repeat the read-only provider check, build `assembleDebugAndroidTest`, install its separate APK, and run:

```
adb -s <authorized-TV> shell am instrument -w de.lowfin.tv.test/de.lowfin.tv.ChannelDiagnostics
```

The test APK uses the existing app signing certificate and reads only channels owned by Lowfin. Remove `de.lowfin.tv.test` after diagnostics. It does not replace the installed Lowfin release.

## 0.10.14 icon follow-up

On .128, the separate launcher favorite tile displayed the old Lowfin banner while its app context menu displayed the current Companion-style banner. Lowfin channel logo marker also still used 0.5.2 and now refreshes existing row logos once. Android settings was used to clear only the isolated launcher cache (95.66 MiB), preserving application data/settings, followed by direct launcher restart. Filesystem artwork timestamps are LRU-touched and do not prove downloads.

Verified in a fresh on-device screenshot after cache reload: both the favorite tile and Continue row logo show current Companion-style Lowfin artwork. Categories and favorites remain present. Closed launcher settings and reopened Lowfin.
