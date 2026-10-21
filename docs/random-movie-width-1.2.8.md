# Random movie dialog width · 1.2.8

The attachment-time styling in LowfinDialog previously overwrote the width set immediately after show(). The random dialog now passes an explicit width into its builder. Both initial layout and fitContent preserve that width, bounded by screen width minus 48 dp. Other dialogs retain their existing content-sized width.

The random dialog requests a moderate 660 dp, bounded by the screen width minus 48 dp. It occupies about two thirds of a 960 dp TV viewport. The reel uses 22 sp titles, an accent-colored spaced heading and a soft selection band. Duration, cancel, real movie titles and automatic navigation remain unchanged.

The regression opens a real styled AlertDialog, waits for attachment/layout callbacks and verifies the width. Existing roulette tests cover timing and cancellation. See dist/verification-1.2.8.json for final checks and deployment.
