package de.lowfin.tv;

import androidx.media3.common.C;
import androidx.media3.common.audio.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.nio.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class LocalPlaybackTest {
    @Test public void RepeatedNegotiationKeepsFreshRequestValuesAndIdenticalCapabilities() throws Exception {
        org.json.JSONObject first=PlaybackPolicy.request("a",720,4000000,false,false,0,1,-1,null,true);
        org.json.JSONObject second=PlaybackPolicy.request("b",2160,25000000,false,false,120000,3,2,"source",false);
        DeviceCodecs.warmUp();LocalPlayback.negotiate(first);LocalPlayback.negotiate(second);
        assertEquals(first.getJSONObject("DeviceProfile").getJSONArray("DirectPlayProfiles").toString(),second.getJSONObject("DeviceProfile").getJSONArray("DirectPlayProfiles").toString());
        assertEquals("b",second.getString("UserId"));assertEquals(1200000000L,second.getLong("StartTimeTicks"));assertEquals(3,second.getInt("AudioStreamIndex"));assertEquals(2,second.getInt("SubtitleStreamIndex"));assertEquals("source",second.getString("MediaSourceId"));
    }
    @Test public void surroundCenterDialogueReachesBothStereoOutputs() throws Exception {
        ChannelMixingAudioProcessor processor=new ChannelMixingAudioProcessor();
        processor.putChannelMixingMatrix(LocalPlayback.stereoMatrix(6));
        AudioProcessor.AudioFormat output=processor.configure(new AudioProcessor.AudioFormat(48000,6,C.ENCODING_PCM_16BIT));
        assertEquals(2,output.channelCount); processor.flush();
        ByteBuffer input=ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());
        for(int channel=0;channel<6;channel++) input.putShort((short)(channel==2?12000:0)); input.flip(); processor.queueInput(input);
        ByteBuffer result=processor.getOutput().order(ByteOrder.nativeOrder()); assertEquals(4,result.remaining());
        short left=result.getShort(),right=result.getShort(); assertTrue(left>0); assertEquals(left,right);
    }
    @Test public void correlatedSurroundSamplesDoNotClip() throws Exception {
        for(int channels=3;channels<=6;channels++) {
            ChannelMixingMatrix matrix=LocalPlayback.stereoMatrix(channels);
            for(int output=0;output<2;output++) { float sum=0; for(int input=0;input<channels;input++) sum+=matrix.getMixingCoefficient(input,output); assertTrue(sum<=1.00001f); }
        }
    }
    @Test public void localNegotiationKeepsSurroundInputForDeviceMixing() throws Exception {
        org.json.JSONObject request=PlaybackPolicy.request("user",720,4000000,false,false,0,-1,-1,null,true);
        LocalPlayback.negotiate(request); assertEquals(6,request.optInt("MaxAudioChannels"));
        assertEquals(0,request.getJSONObject("DeviceProfile").getJSONArray("CodecProfiles").length());
        assertTrue(request.optBoolean("EnableDirectPlay")); assertFalse(request.optBoolean("EnableTranscoding"));
    }
}
