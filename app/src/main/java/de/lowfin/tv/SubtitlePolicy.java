package de.lowfin.tv;
import androidx.media3.common.*;
/** Suppress automatic and forced tracks locally, without touching the active stream. */
final class SubtitlePolicy {
    static TrackSelectionParameters off(TrackSelectionParameters parameters) {
        return parameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT|C.SELECTION_FLAG_FORCED).build();
    }
}
