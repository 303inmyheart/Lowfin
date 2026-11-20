package de.lowfin.tv;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.audio.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.nio.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class LoudnessAudioProcessorTest {
    @Test public void DiagnosticsDistinguishMeasuredUnityGainFromLocalFallbackAndCountOnlyCompleteFrames() throws Exception {
        LoudnessAudioProcessor p=processor(2);assertFalse(p.measured());assertEquals(0,p.processedFrames());
        p.measurement(-20,-5);assertTrue(p.measured());assertEquals(0,p.calibrationDb(),.00001);
        p.queueInput(tone(128,2,(short)500));p.getOutput();assertEquals(128,p.processedFrames());
        p.flush();assertTrue(p.measured());assertEquals(128,p.processedFrames());
        p.measurement(Double.NaN,-2);assertFalse(p.measured());
        p.measurement(-10,-2);assertTrue(p.measured());assertEquals(-10,p.calibrationDb(),.00001);
        p.reset();assertFalse(p.measured());
    }
    private LoudnessAudioProcessor processor(int channels) throws Exception {
        LoudnessAudioProcessor p=new LoudnessAudioProcessor(2);p.configure(new AudioProcessor.AudioFormat(48000,channels,C.ENCODING_PCM_16BIT));p.flush();return p;
    }
    private ByteBuffer tone(int frames,int channels,short amplitude) {
        ByteBuffer data=ByteBuffer.allocateDirect(frames*channels*2).order(ByteOrder.nativeOrder());
        for(int i=0;i<frames;i++) for(int channel=0;channel<channels;channel++) data.putShort((short)(i%2==0?amplitude:-amplitude));data.flip();return data;
    }
    @Test public void EveryChannelCountPreservesDurationAndLinkedChannelRatios() throws Exception {
        for(int channels:new int[]{1,2,6,8}) {
            LoudnessAudioProcessor p=processor(channels);ByteBuffer input=ByteBuffer.allocateDirect(1024*channels*2).order(ByteOrder.nativeOrder());
            for(int frame=0;frame<1024;frame++) for(int c=0;c<channels;c++) input.putShort((short)(1000*(c+1)));input.flip();p.queueInput(input);
            ByteBuffer out=p.getOutput().order(ByteOrder.nativeOrder());assertEquals(1024*channels*2,out.remaining());
            while(out.hasRemaining()) { int first=out.getShort();for(int c=1;c<channels;c++) assertEquals(first*(c+1),out.getShort(),channels); }
            p.queueEndOfStream();assertTrue(p.isEnded());
        }
    }
    @Test public void QuietScenesGetLouderAndFullScaleScenesNeverClip() throws Exception {
        LoudnessAudioProcessor p=processor(2);int last=0;
        for(int block=0;block<250;block++) { p.queueInput(tone(1024,2,(short)600));ByteBuffer out=p.getOutput().order(ByteOrder.nativeOrder());while(out.hasRemaining()) last=Math.abs(out.getShort()); }
        assertTrue("Quiet dialogue should receive gain",last>900);
        p.measurement(-40,-12);
        for(int block=0;block<100;block++) {
            p.queueInput(tone(1024,2,Short.MAX_VALUE));ByteBuffer out=p.getOutput().order(ByteOrder.nativeOrder());
            while(out.hasRemaining()) assertTrue("Linked limiter ceiling",Math.abs((int)out.getShort())<=31130);
        }
    }
    @Test public void SilenceStaysSilentAndSeekFlushDropsPreviousSceneGain() throws Exception {
        LoudnessAudioProcessor p=processor(2);
        for(int block=0;block<200;block++) { p.queueInput(tone(1024,2,(short)700));p.getOutput(); }
        p.queueInput(tone(1024,2,(short)0));ByteBuffer silent=p.getOutput().order(ByteOrder.nativeOrder());while(silent.hasRemaining()) assertEquals(0,silent.getShort());
        p.flush();p.queueInput(tone(1,2,(short)1000));assertEquals(1000,p.getOutput().order(ByteOrder.nativeOrder()).getShort(),1);
    }
    @Test public void FragmentedFramesAreIdenticalToAlignedBuffers() throws Exception {
        LoudnessAudioProcessor whole=processor(6),split=processor(6);ByteBuffer input=tone(100,6,(short)800);byte[] bytes=new byte[input.remaining()];input.get(bytes);
        whole.queueInput(ByteBuffer.wrap(bytes));ByteBuffer output=whole.getOutput();byte[] expected=new byte[output.remaining()];output.get(expected);
        java.io.ByteArrayOutputStream actual=new java.io.ByteArrayOutputStream();
        for(int offset=0;offset<bytes.length;offset+=7) {
            split.queueInput(ByteBuffer.wrap(bytes,offset,Math.min(7,bytes.length-offset)));ByteBuffer out=split.getOutput();while(out.hasRemaining()) actual.write(out.get());
        }
        assertArrayEquals(expected,actual.toByteArray());split.queueEndOfStream();assertTrue(split.isEnded());
    }
    @Test public void BadCalibrationAndUnsupportedPcmAreSafelyIgnored() throws Exception {
        LoudnessAudioProcessor p=processor(2);p.measurement(Double.NaN,-1);p.measurement(-24,Double.POSITIVE_INFINITY);p.queueInput(tone(1,2,(short)1000));assertEquals(1000,p.getOutput().order(ByteOrder.nativeOrder()).getShort(),1);
        assertEquals(AudioProcessor.AudioFormat.NOT_SET,p.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_FLOAT)));assertFalse(p.isActive());
        p.reset();
    }
    @Test public void PcmOnlySinkRejectsPassthroughAndOffload() {
        AudioSink delegate=new DefaultAudioSink.Builder(org.robolectric.RuntimeEnvironment.getApplication()).build();AudioSink sink=LocalPlayback.pcmOnly(delegate);
        Format ac3=new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).setChannelCount(6).setSampleRate(48000).build();
        assertFalse(sink.supportsFormat(ac3));assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED,sink.getFormatSupport(ac3));assertFalse(sink.getFormatOffloadSupport(ac3).isFormatSupported);
        Format raw=new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).setChannelCount(2).setSampleRate(48000).build();assertTrue(sink.supportsFormat(raw));
    }
    @Test public void CalibrationChangesStartingLevelAndClearingRestoresLocalFallback() throws Exception {
        LoudnessAudioProcessor calibrated=processor(2),local=processor(2);calibrated.measurement(-10,-1);int measuredSample=0,localSample=0;
        for(int block=0;block<150;block++) {
            calibrated.queueInput(tone(1024,2,(short)1000));local.queueInput(tone(1024,2,(short)1000));
            measuredSample=Math.abs(calibrated.getOutput().order(ByteOrder.nativeOrder()).getShort());localSample=Math.abs(local.getOutput().order(ByteOrder.nativeOrder()).getShort());
        }
        assertTrue(measuredSample<localSample*.7);
        calibrated.clearMeasurement();calibrated.flush();local.flush();
        ByteBuffer a=tone(2048,2,(short)1000),b=tone(2048,2,(short)1000);calibrated.queueInput(a);local.queueInput(b);assertEquals(local.getOutput(),calibrated.getOutput());
    }
    @Test public void OriginalAudioFallbackOnlyHandlesAudioFailures() {
        assertTrue(LocalPlayback.audioFailure(new androidx.media3.common.PlaybackException("test",null,androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED)));
        assertFalse(LocalPlayback.audioFailure(new androidx.media3.common.PlaybackException("test",null,androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED)));
    }
}
