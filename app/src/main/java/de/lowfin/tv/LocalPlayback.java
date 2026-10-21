package de.lowfin.tv;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.audio.*;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.*;
import org.json.*;
import java.util.*;

/** Device decoders and PCM stereo mixing, without forcing server audio conversion. */
@androidx.media3.common.util.UnstableApi
final class LocalPlayback {
    static void negotiate(JSONObject request) throws JSONException {
        JSONObject profile=request.getJSONObject("DeviceProfile");
        Map<String,String> video=new LinkedHashMap<>(),audio=new LinkedHashMap<>();
        video.put("video/avc","h264"); video.put("video/hevc","hevc"); video.put("video/mpeg2","mpeg2video"); video.put("video/mp4v-es","mpeg4"); video.put("video/x-vnd.on2.vp9","vp9"); video.put("video/x-vnd.on2.vp8","vp8"); video.put("video/av01","av1");
        audio.put("audio/mp4a-latm","aac"); audio.put("audio/mpeg","mp3"); audio.put("audio/ac3","ac3"); audio.put("audio/eac3","eac3"); audio.put("audio/vnd.dts","dts"); audio.put("audio/flac","flac"); audio.put("audio/opus","opus"); audio.put("audio/vorbis","vorbis");
        Set<String> supported=new HashSet<>();
        try { supported.addAll(DeviceCodecs.types()); } catch(RuntimeException ignored) { }
        List<String> v=new ArrayList<>(),a=new ArrayList<>();
        for(Map.Entry<String,String> e:video.entrySet()) if(supported.contains(e.getKey())) v.add(e.getValue());
        for(Map.Entry<String,String> e:audio.entrySet()) if(supported.contains(e.getKey())) a.add(e.getValue());
        // AAC/H.264 are mandatory Android TV formats; preserve basic negotiation if OEM enumeration fails.
        if(v.isEmpty()) v.add("h264"); if(a.isEmpty()) { a.add("aac"); a.add("mp3"); }
        profile.put("DirectPlayProfiles",Json.arr(Json.obj("Type","Video","Container","mp4,mkv,mov,webm,ts,mpegts,avi","VideoCodec",String.join(",",v),"AudioCodec",String.join(",",a))));
        profile.put("CodecProfiles",new JSONArray());
        profile.put("MaxStaticBitrate",Integer.MAX_VALUE); profile.put("MaxStreamingBitrate",Integer.MAX_VALUE);
        request.put("MaxStreamingBitrate",Integer.MAX_VALUE); request.put("MaxAudioChannels",6); request.put("EnableTranscoding",false);
    }
    static ChannelMixingMatrix stereoMatrix(int channels) {
        float[] coefficients;
        switch(channels) {
            case 1: coefficients=new float[]{1,1}; break;
            case 2: coefficients=new float[]{1,0,0,1}; break;
            case 3: coefficients=new float[]{1,0,0,1,.7071f,.7071f}; break;
            case 4: coefficients=new float[]{1,0,0,1,.7071f,0,0,.7071f}; break;
            case 5: coefficients=new float[]{1,0,0,1,.7071f,.7071f,.7071f,0,0,.7071f}; break;
            case 6: coefficients=new float[]{1,0,0,1,.7071f,.7071f,.5f,.5f,.7071f,0,0,.7071f}; break;
            default: throw new IllegalArgumentException("Unsupported PCM channel count: "+channels);
        }
        ChannelMixingMatrix matrix=new ChannelMixingMatrix(channels,2,coefficients);
        // Reserve headroom for center/surround summation, keeping dialogue in both outputs.
        float peak=1;
        for(int output=0;output<2;output++) { float sum=0; for(int input=0;input<channels;input++) sum+=matrix.getMixingCoefficient(input,output); peak=Math.max(peak,sum); }
        return matrix.scaleBy(1/peak);
    }
    static DefaultRenderersFactory renderers(Context context,boolean stereo) {
        return new DefaultRenderersFactory(context) {
            @Override protected AudioSink buildAudioSink(Context c,boolean floats,boolean params) {
                if(!stereo) return super.buildAudioSink(c,floats,params);
                ChannelMixingAudioProcessor mixer=new ChannelMixingAudioProcessor();
                for(int channels=1;channels<=6;channels++) mixer.putChannelMixingMatrix(stereoMatrix(channels));
                return new DefaultAudioSink.Builder(c).setEnableFloatOutput(false)
                    .setAudioCapabilities(new AudioCapabilities(new int[]{C.ENCODING_PCM_16BIT},6))
                    .setAudioProcessors(new AudioProcessor[]{mixer}).build();
            }
        }.setEnableDecoderFallback(true);
    }
}
