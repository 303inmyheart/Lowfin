package de.lowfin.tv;
import androidx.media3.common.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class SubtitlePolicyTest {
    @Test public void offDisablesForcedTracksAndClearsOnlyTextOverrides() {
        TrackGroup text=new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setSelectionFlags(C.SELECTION_FLAG_FORCED).build());
        TrackGroup audio=new TrackGroup(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build());
        TrackSelectionParameters selected=TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon().setOverrideForType(new TrackSelectionOverride(text,0)).setOverrideForType(new TrackSelectionOverride(audio,0)).build();
        TrackSelectionParameters off=SubtitlePolicy.off(selected);
        assertTrue(off.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)); assertFalse(off.overrides.containsKey(text)); assertTrue(off.overrides.containsKey(audio));
        assertEquals(C.SELECTION_FLAG_DEFAULT|C.SELECTION_FLAG_FORCED,off.ignoredTextSelectionFlags);
    }
}
