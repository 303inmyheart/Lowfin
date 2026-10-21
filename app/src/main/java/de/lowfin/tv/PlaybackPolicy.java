package de.lowfin.tv;

import org.json.*;

/** Pure playback decisions, shared by the player and unit tests. */
final class PlaybackPolicy {
    static void migrateSkipSettings(android.content.SharedPreferences settings) {
        if(!settings.contains("autoSkip")) return;
        android.content.SharedPreferences.Editor edit=settings.edit();
        if(!settings.getBoolean("autoSkip",true)) edit.putBoolean("skipIntro",false).putBoolean("skipOutro",false);
        edit.remove("autoSkip").apply();
    }
    static boolean autoSkip(String type,org.json.JSONObject item,android.content.SharedPreferences settings) {
        return automatic(type)&&settings.getBoolean("Intro".equalsIgnoreCase(type)?"skipIntro":"skipOutro",true)
            &&!keepSeasonPremiereIntro(type,item,settings.getBoolean("keepSeasonPremiereIntro",false));
    }

    static long startPosition(JSONObject item,long requested,boolean homeResume,boolean restart,boolean keepPosition) { return restart?0:homeResume&&!keepPosition?Json.position(item):requested; }
    static int width(int height) { return height>=2160?3840:height>=1080?1920:1280; }
    static boolean automatic(String type) { return "Intro".equalsIgnoreCase(type)||"Outro".equalsIgnoreCase(type); }
    static boolean keepSeasonPremiereIntro(String type,JSONObject item,boolean enabled) {
        return enabled&&"Intro".equalsIgnoreCase(type)&&item!=null&&"Episode".equalsIgnoreCase(item.optString("Type"))&&item.optInt("IndexNumber",-1)==1;
    }
    static boolean active(JSONObject segment,long positionMs) {
        long start=segment.optLong("StartTicks")/10000, end=segment.optLong("EndTicks")/10000;
        return end>start&&positionMs>=start&&positionMs<end;
    }
    static JSONObject profile(int height,int bitrate) { return profile(height,bitrate,true); }
    static JSONObject profile(int height,int bitrate,boolean stereo) {
        return Json.obj("Name","Lowfin HD","MaxStreamingBitrate",bitrate,"MaxStaticBitrate",bitrate,
            "DirectPlayProfiles",Json.arr(Json.obj("Type","Video","Container","mp4,mkv,mov","VideoCodec","h264","AudioCodec","aac,mp3")),
            "TranscodingProfiles",Json.arr(Json.obj("Type","Video","Container","ts","Protocol","hls","VideoCodec","h264","AudioCodec","aac",
                "MaxAudioChannels",stereo?"2":"6","MinSegments",2,"SegmentLength",4,"BreakOnNonKeyFrames",false,"Context","Streaming")),
            "CodecProfiles",Json.arr(
                Json.obj("Type","Video","Codec","h264","Conditions",Json.arr(
                    condition("Width","LessThanEqual",String.valueOf(width(height))), condition("Height","LessThanEqual",""+height),
                    condition("VideoBitDepth","LessThanEqual","8"),condition("VideoFramerate","LessThanEqual","30"))),
                Json.obj("Type","VideoAudio","Conditions",Json.arr(condition("AudioChannels","LessThanEqual",stereo?"2":"6")))),
            "SubtitleProfiles",Json.arr(Json.obj("Format","srt","Method","External"),Json.obj("Format","vtt","Method","External"),
                Json.obj("Format","ass","Method","Encode"),Json.obj("Format","ssa","Method","Encode"),
                Json.obj("Format","pgssub","Method","Encode"),Json.obj("Format","dvdsub","Method","Encode")));
    }
    private static JSONObject condition(String property,String condition,String value) { return Json.obj("Property",property,"Condition",condition,"Value",value,"IsRequired",true); }
    static JSONObject request(String user,int height,int bitrate,boolean live,boolean forceHls,long position,int audio,int subtitle,String source) {
        return request(user,height,bitrate,live,forceHls,position,audio,subtitle,source,true);
    }
    static JSONObject request(String user,int height,int bitrate,boolean live,boolean forceHls,long position,int audio,int subtitle,String source,boolean stereo) {
        JSONObject r=Json.obj("UserId",user,"DeviceProfile",profile(height,bitrate,stereo),"MaxStreamingBitrate",bitrate,
            "StartTimeTicks",position*10000,"IsPlayback",true,"AutoOpenLiveStream",true,
            "EnableDirectPlay",!live&&!forceHls,"EnableDirectStream",!forceHls,"EnableTranscoding",true,
            "MaxAudioChannels",stereo?2:6,"SubtitleStreamIndex",subtitle);
        try { if(audio>=0) r.put("AudioStreamIndex",audio); if(source!=null) r.put("MediaSourceId",source); } catch(JSONException e) { throw new IllegalArgumentException(e); }
        return r;
    }
}
