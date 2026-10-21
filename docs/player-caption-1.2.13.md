# Player episode caption alignment · 1.2.13

Episode playback uses a shared 200 dp width for the series logo, fallback title and episode caption. The logo uses FIT_CENTER to preserve its aspect ratio; the caption is horizontally centered below its frame and can use two lines with end ellipsis. The fallback title is centered in the same width. Movie and Live TV layouts are unchanged.

The existing PlayerChrome regression verifies alignment after loading wide and portrait images, equal horizontal/vertical image scale, unchanged frame dimensions and the title fallback. Release/test/install results are recorded in dist/verification-1.2.13.json.
