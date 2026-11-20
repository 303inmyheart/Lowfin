package de.lowfin.tv;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Frame-linked PCM leveling. No lookahead, new threads, I/O or allocations in the sample loop. */
@androidx.media3.common.util.UnstableApi
final class LoudnessAudioProcessor extends BaseAudioProcessor {
    static final class Calibration {
        final double gain;
        final boolean measured;
        Calibration(double gain) { this(gain,false); }
        Calibration(double gain,boolean measured) { this.gain=gain;this.measured=measured; }
    }
    private volatile Calibration calibration=new Calibration(1);
    private volatile long processedFrames;
    boolean measured() { return calibration.measured; }
    double calibrationDb() { return 20*Math.log10(calibration.gain); }
    long processedFrames() { return processedFrames; }
    private final int strength;
    private byte[] partial=new byte[0];
    private short[] frame=new short[0];
    private int pending;
    private int controlFrame;
    private double targetGain=1;
    private double power,gain=1,limiter=1,powerRate,upRate,downRate,limiterRelease;
    LoudnessAudioProcessor(int strength) { this.strength=Math.max(1,Math.min(3,strength)); }
    void clearMeasurement() { calibration=new Calibration(1); }
    void measurement(double lufs,double peak) {
        if(!Double.isFinite(lufs)||!Double.isFinite(peak)||lufs<=-70||lufs>10||peak>20||peak< -120) { clearMeasurement();return; }
        // A conservative programme gain; local scene leveling works around this baseline.
        double db=Math.max(-24,Math.min(9,Math.min(-20-lufs,-1-peak)));
        calibration=new Calibration(Math.pow(10,db/20),true);
    }
    @Override protected AudioFormat onConfigure(AudioFormat format) {
        return format.encoding==C.ENCODING_PCM_16BIT&&format.channelCount>=1&&format.channelCount<=8&&format.sampleRate>0?format:AudioFormat.NOT_SET;
    }
    @Override protected void onFlush() {
        pending=0;power=0;gain=1;limiter=1;controlFrame=0;targetGain=1;
        int channels=inputAudioFormat.channelCount;
        if(channels>0&&channels<=8) { frame=new short[channels];partial=new byte[channels*2]; }
        double rate=Math.max(1,inputAudioFormat.sampleRate);
        powerRate=1-Math.exp(-1/(rate*.12));upRate=1-Math.exp(-1/(rate*1.5));downRate=1-Math.exp(-1/(rate*.035));limiterRelease=1-Math.exp(-1/(rate*.15));
    }
    @Override public void queueInput(ByteBuffer input) {
        int bytes=frame.length*2;if(bytes==0) return;
        ByteBuffer output=replaceOutputBuffer((pending+input.remaining())/bytes*bytes).order(ByteOrder.nativeOrder());
        // Snapshot once per input buffer. A delayed server response cannot mutate the audio loop.
        double base=calibration.gain,maxSceneGain=Math.pow(10,(strength*3d)/20),exponent=.2+strength*.15,targetRms=Math.pow(10,(-25+strength*2d)/20);
        while(input.hasRemaining()) {
            int count=Math.min(bytes-pending,input.remaining());input.get(partial,pending,count);pending+=count;
            if(pending!=bytes) break;pending=0;
            double sum=0,peak=0;
            for(int c=0;c<frame.length;c++) {
                int offset=c*2;
                short sample=ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN?(short)((partial[offset]&255)|(partial[offset+1]<<8)):(short)((partial[offset+1]&255)|(partial[offset]<<8));
                frame[c]=sample;double normalized=sample/32768d;sum+=normalized*normalized;peak=Math.max(peak,Math.abs(normalized));
            }
            power+=powerRate*(sum/frame.length-power);
            // Below the noise gate do not amplify silence/background noise or retain a prior boost.
            if(controlFrame++%64==0) {
                targetGain=Math.min(1,base);
                if(power>0.0000032) {
                    double rms=Math.sqrt(power)*base;
                    double scene=Math.pow(targetRms/Math.max(.000001,rms),exponent);
                    targetGain=base*Math.max(.125,Math.min(maxSceneGain,scene));
                }
            }
            gain+=(targetGain<gain?downRate:upRate)*(targetGain-gain);
            double ceiling=peak*gain>.95?.95/(peak*gain):1;
            if(ceiling<limiter) limiter=ceiling;else limiter+=limiterRelease*(ceiling-limiter);
            double applied=gain*limiter;
            for(short sample:frame) output.putShort((short)Math.max(-32768,Math.min(32767,Math.round(sample*applied))));
        }
        processedFrames+=output.position()/bytes;
        output.flip();
    }
    @Override protected void onQueueEndOfStream() { pending=0; }
    @Override protected void onReset() { partial=new byte[0];frame=new short[0];pending=0;power=0;gain=limiter=1;clearMeasurement(); }
}
