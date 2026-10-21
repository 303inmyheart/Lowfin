# Lowfin 1.2.3 / Companion 0.4.0 — frame-based cover tags

## Behavior

Only DVD and 5.1 tags were requested. DVD denotes SD/DVD dimensions (positive width ≤1024 and height ≤576), not a proven disc source. A cropped ultrawide HD encode is excluded. 5.1 requires an actual six-channel audio track in the primary media source, even when it is not the selected playback track; stereo and 7.1 do not produce that tag. Unknown stream data produces no tag. Mixed series/season covers are not labeled.

The Companion dashboard has a master switch (default off) and independent DVD/5.1 switches (default on). Lowfin Appearance has a device-local show/hide switch (default on), shown only for a detected compatible Companion. Turning it off immediately hides all bound tags; restoring it reuses available facts without network requests.

## Architecture and performance

Companion supplies authenticated compact metadata in batches of up to 64 IDs using native user/item visibility checks. It neither intercepts Jellyfin image routes nor changes original artwork, ETags, paths or source files. No extra plugin dependencies or image renderer is shipped. JellyTag was reviewed as reference: https://github.com/Atilil/jellyfin-plugins/tree/main/Jellytag . No upstream implementation or assets are incorporated; the narrowed two-tag design is native Lowfin code.

Lowfin draws the two neutral translucent badges after ImageView restores its image transform, in visible frame coordinates. Crop, source aspect ratio and source pixel dimensions cannot push the tags outside the frame. Tag geometry is cached and tied to frame size and UI density, with no new focusable children or animated work.

On-demand requests are debounced and grouped; facts are cached in a bounded 256-item memory cache and account-scoped database state. Images, their URLs and existing persistent cover files remain unchanged. Incremental plugin delivery clears affected facts in the same metadata transaction, invalidates memory and fences stale network results. Recycled views reject results for their former item. Missing/older Companion and transient endpoint errors retain ordinary artwork, with bounded retry delay. No full scan, cover regeneration or precache rebuild is introduced.

## Validation

- 422 full client tests passed; final targeted rerun covers tag layout, local switches, batched/persistent facts, recycled holders, in-flight invalidation and existing cache delivery.
- 54 Companion tests passed, including SD/cropped-HD/unknown resolution, real six-channel detection, and existing playback/provider/cache rules.
- Real isolated Jellyfin 12.2.0 integration passed playback/resume/provider/cache regressions and tag discovery/config, authentication, request limits, deduplication, independent switches and library restrictions for ordinary users.
- Native HD and 4K screenshot fixtures verify 4:3 source artwork cropped to a 16:9 frame. Both badges remain fully visible; original center pixels and focus behavior remain unchanged. Screenshots: app/build/qa/cover-tags-cropped-4x3.png and cover-tags-cropped-4x3-4k.png. No physical 4K TV was used.

Production Jellyfin was read only for its public version (12.2.0). Server plugin update/restart is still required; the test server was isolated. No GitHub publication.

Delivery: install only on TV 192.168.1.128, without launching the app, per the latest explicit user instruction.
