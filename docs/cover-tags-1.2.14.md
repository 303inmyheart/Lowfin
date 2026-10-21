# Extended cover tags: Lowfin 1.2.14 / Companion 0.6.0

Adds indexed resolution, HDR, 3D, codec, channel-layout and compact language/VOST labels. Dashboard selection gates each category. Lowfin retains its hide-tags setting and local HDR gate. Stereo defaults off. Rendering stays in card-frame coordinates, bounds its occupied area and uses an overflow counter. Server artwork remains untouched.

Named facts persist beside legacy mask facts, in one transaction for received network results. Existing metadata invalidation clears both. Older clients retain the legacy API fields. Old index migration does not emit a change for every new named-label field. Series/season facts are scoped to visible episodes.

Validation: 448 client tests, 92 plugin tests, 2 feed generator tests; release lint and English coverage audit passed. Native Jellyfin 12.2 fixture validates mixed-resolution DVD majority, named H.264/resolution labels and uncached episode ratings. Release uses the existing signing certificate. No TV installation requested or performed.
