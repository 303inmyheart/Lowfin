# Navigation stalls (1.2.19)

On TV .128, Android InputDispatcher repeatedly waited for unprocessed MainActivity events between 23:41:38 and 23:41:49 on October 21. No ANR was recorded. A short later CPU sample was mostly idle, so it does not prove persistent CPU saturation. Earlier aggregate CPU measurements included installation and launcher activity and cannot identify the cause of current navigation stalls.

Inspection found that cover-tag batch completion on the main thread still acquired the persistence gate. Background tag writes and metadata invalidation use that gate around SQLite work. Completion now reads independently synchronized bounded caches without acquiring the persistence gate. Retry entries include their generation; invalidated responses cannot suppress refreshed facts. Persistence ordering and background write fencing remain intact.

Regression tests exercise main-thread completion while another thread holds the persistence gate, and reject stale batch retry updates. This removes a concrete blocking path; a complete resolution of the observed TV stalls still requires on-device observation.

Validation: all 470 unit tests and release lint passed. Locally built release preserves the existing signing certificate. ADB installation on .128 succeeded and MainActivity was started; .87/.166 were not touched. APK and checksum delivered to Desktop.
