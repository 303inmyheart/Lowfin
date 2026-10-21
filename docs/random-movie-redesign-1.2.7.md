# Random movie window · 1.2.7

The roulette window now uses a maximum width of 760 dp instead of 500 dp, bounded by the display width with 32 dp side margins. The wider reel uses larger 20 sp titles and a soft accent selection without a surrounding heavy outline. The winner has its own bold 22 sp label with up to two lines, followed by a short accent underline reveal rather than sparks across the text.

The existing title source, configured duration, cancel behavior, animations preference for the reveal, and automatic detail navigation are retained. Three timing, real-title rendering and cancellation tests pass. Release build and lint results are recorded in dist/verification-1.2.7.json. No Companion update is required.
