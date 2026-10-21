package de.lowfin.tv;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.view.Display;
import android.view.WindowManager;
import org.json.*;
import java.util.*;

/** Native HDR only when explicitly enabled and both display and decoder support it. */
final class HdrPolicy {
    static String ranges(boolean enabled,int[] displayTypes,boolean tenBit) {
        List<String> ranges=new ArrayList<>(Arrays.asList("Unknown","SDR"));
        if(!enabled||!tenBit) return String.join("|",ranges);
        for(int type:displayTypes) {
            if(type==Display.HdrCapabilities.HDR_TYPE_HDR10&&!ranges.contains("HDR10")) ranges.add("HDR10");
            if(type==Display.HdrCapabilities.HDR_TYPE_HLG&&!ranges.contains("HLG")) ranges.add("HLG");
            if(type==Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS) { if(!ranges.contains("HDR10")) ranges.add("HDR10"); ranges.add("HDR10Plus"); }
        }
        // Dolby Vision profiles differ substantially; do not claim unverified DV support.
        return String.join("|",ranges);
    }
    private static boolean tenBit(String mime) {
        try { for(MediaCodecInfo codec:DeviceCodecs.infos()) {
            if(codec.isEncoder()) continue;
            for(String type:codec.getSupportedTypes()) if(type.equalsIgnoreCase(mime)) {
                for(MediaCodecInfo.CodecProfileLevel p:codec.getCapabilitiesForType(type).profileLevels) {
                    if(mime.equals("video/hevc")&&(p.profile==MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10||p.profile==MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10||p.profile==MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)) return true;
                    if(mime.equals("video/x-vnd.on2.vp9")&&(p.profile==MediaCodecInfo.CodecProfileLevel.VP9Profile2||p.profile==MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR||p.profile==MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus)) return true;
                    if(mime.equals("video/av01")&&(p.profile==MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10||p.profile==MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10||p.profile==MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus)) return true;
                }
            }
        } } catch(RuntimeException ignored) { }
        return false;
    }
    static void configure(Context context,JSONObject request,boolean enabled) throws JSONException {
        int[] types=new int[0];
        if(enabled) try { WindowManager wm=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE); if(wm!=null) types=wm.getDefaultDisplay().getHdrCapabilities().getSupportedHdrTypes(); } catch(RuntimeException ignored) { }
        Map<String,String> allowed=new LinkedHashMap<>();
        for(String[] codec:new String[][]{{"hevc","video/hevc"},{"vp9","video/x-vnd.on2.vp9"},{"av1","video/av01"}}) allowed.put(codec[0],ranges(enabled,types,enabled&&tenBit(codec[1])));
        apply(request,allowed);
    }
    static void apply(JSONObject request,Map<String,String> allowed) throws JSONException {
        JSONObject profile=request.getJSONObject("DeviceProfile"); JSONArray codecs=profile.optJSONArray("CodecProfiles"); if(codecs==null) codecs=new JSONArray();
        for(Map.Entry<String,String> entry:allowed.entrySet()) codecs.put(Json.obj("Type","Video","Codec",entry.getKey(),"Conditions",Json.arr(Json.obj("Property","VideoRangeType","Condition","EqualsAny","Value",entry.getValue(),"IsRequired",true))));
        codecs.put(Json.obj("Type","Video","Codec","h264,mpeg2video,mpeg4,vp8","Conditions",Json.arr(Json.obj("Property","VideoRangeType","Condition","EqualsAny","Value","Unknown|SDR","IsRequired",true))));
        profile.put("CodecProfiles",codecs);
    }
}
