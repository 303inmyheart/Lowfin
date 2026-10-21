package de.lowfin.tv;

import android.content.SharedPreferences;
import org.json.*;

/** Device-local user limits shared by server negotiation and native track selection. */
final class PlaybackOptions {
    static int fps(SharedPreferences p) { int v=p.getInt("videoFps",30);return v==0||v==30||v==60?v:30; }
    static int channels(SharedPreferences p) { if(p.getBoolean("stereo",true)) return 2;int v=p.getInt("audioChannels",6);return v==0||v==2||v==6||v==8?v:6; }
    static int trailerHeight(SharedPreferences p) {int v=p.getInt("trailerHeight",720);return v==0||v==480||v==720||v==1080||v==2160?v:720;}
    static void negotiate(JSONObject request,SharedPreferences p) throws JSONException {
        int channels=channels(p),fps=fps(p);
        if(channels==0) request.remove("MaxAudioChannels");else request.put("MaxAudioChannels",channels);
        JSONObject profile=request.getJSONObject("DeviceProfile");
        JSONArray trans=Json.array(profile,"TranscodingProfiles");for(int i=0;i<trans.length();i++) {JSONObject t=trans.getJSONObject(i);if(channels==0)t.remove("MaxAudioChannels");else t.put("MaxAudioChannels",""+channels);}
        JSONArray codecs=Json.array(profile,"CodecProfiles");for(int i=0;i<codecs.length();i++) {JSONObject codec=codecs.getJSONObject(i);JSONArray conditions=Json.array(codec,"Conditions"),updated=new JSONArray();for(int n=0;n<conditions.length();n++) {JSONObject condition=conditions.getJSONObject(n);String key=condition.optString("Property");int limit="AudioChannels".equals(key)?channels:"VideoFramerate".equals(key)?fps:-1;if(limit==0)continue;if(limit>0)condition.put("Value",""+limit);updated.put(condition);}codec.put("Conditions",updated);}
    }
}
