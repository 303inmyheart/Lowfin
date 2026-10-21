package de.lowfin.tv;

import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlaybackException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class PlaybackDiagnosticsTest {
    @Test public void rendererFailureIncludesFormatAndBufferButNeverExceptionMessages() {
        Format format=new Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).build();
        ExoPlaybackException error=ExoPlaybackException.createForRenderer(
            new IllegalStateException("http://secret-server/video?api_key=secret"),"MediaCodecVideoRenderer",0,format,4,false,
            PlaybackException.ERROR_CODE_DECODING_FAILED);
        String detail=PlaybackDiagnostics.describe(error,120000,145000,false,"DirectPlay");
        assertTrue(detail.contains("ERROR_CODE_DECODING_FAILED"));
        assertTrue(detail.contains("bufferedAheadMs=25000"));
        assertTrue(detail.contains("size=1920x1080"));
        assertTrue(detail.contains("IllegalStateException"));
        assertFalse(detail.contains("secret"));
    }
    @Test public void unavailableBufferDoesNotBecomeNegative() {
        PlaybackException error=new PlaybackException("secret",null,PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        assertTrue(PlaybackDiagnostics.describe(error,100,0,true,"DirectStream").contains("bufferedAheadMs=0"));
    }
}
