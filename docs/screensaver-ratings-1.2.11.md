# Screensaver ratings · 1.2.11

Removed the standalone community/critic rating TextView and its spacer from ScreensaverView. Jellyfin CommunityRating and CriticRating are no longer rendered by the screensaver. The shared RatingsRow remains unchanged and continues to respect external provider, visibility and cache settings. No changes to detail pages or other rating displays.

The existing screensaver regression verifies that a Jellyfin community value is absent while an external IMDb score remains visible; the remaining idle, lifecycle, key dismissal, library and layout tests also run. See dist/verification-1.2.11.json for release checks and installation.
