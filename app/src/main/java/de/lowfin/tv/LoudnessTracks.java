package de.lowfin.tv;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import org.json.*;
import java.util.*;

/** Do not substitute audio ordinals for Jellyfin's file stream indices. Ambiguity uses local leveling. */
@androidx.media3.common.util.UnstableApi
final class LoudnessTracks {
    static final String NATIVE="native-v1",STEREO="lowfin-stereo-v1";
    private static String language(String value) {
        value=value==null?"":value.toLowerCase(Locale.ROOT).replace('_','-');
        if(value.equals("und")) return "";
        if(value.equals("ger")) return "deu";if(value.equals("fre")) return "fra";if(value.equals("chi")) return "zho";
        try { return Locale.forLanguageTag(value).getISO3Language(); } catch(MissingResourceException e) { return value; }
    }
    private static String mime(String codec) {
        switch(codec.toLowerCase(Locale.ROOT)) {
            case "aac": return MimeTypes.AUDIO_AAC;case "ac3": return MimeTypes.AUDIO_AC3;case "eac3": return MimeTypes.AUDIO_E_AC3;
            case "dts": return MimeTypes.AUDIO_DTS;case "truehd": return MimeTypes.AUDIO_TRUEHD;case "flac": return MimeTypes.AUDIO_FLAC;
            case "mp3": return MimeTypes.AUDIO_MPEG;case "opus": return MimeTypes.AUDIO_OPUS;case "vorbis": return MimeTypes.AUDIO_VORBIS;
            default: return "";
        }
    }
    static int stream(JSONArray streams,Format selected) {
        List<JSONObject> candidates=new ArrayList<>();
        for(int i=0;i<streams.length();i++) {
            JSONObject s=streams.optJSONObject(i);if(s==null||!s.optString("Type").equals("Audio")||s.optBoolean("IsExternal")||s.optInt("Index",-1)<0) continue;
            if(!language(s.optString("Language")).equals(language(selected.language))) continue;
            if(selected.channelCount>0&&s.optInt("Channels",0)>0&&selected.channelCount!=s.optInt("Channels")) continue;
            if(selected.sampleRate>0&&s.optInt("SampleRate",0)>0&&selected.sampleRate!=s.optInt("SampleRate")) continue;
            String expected=mime(s.optString("Codec"));
            if(!expected.isEmpty()&&selected.sampleMimeType!=null&&!expected.equals(selected.sampleMimeType)&&!(expected.equals(MimeTypes.AUDIO_E_AC3)&&selected.sampleMimeType.equals(MimeTypes.AUDIO_E_AC3_JOC))&&!(expected.equals(MimeTypes.AUDIO_DTS)&&selected.sampleMimeType.equals(MimeTypes.AUDIO_DTS_HD))) continue;
            candidates.add(s);
        }
        if(candidates.size()>1&&selected.label!=null&&!selected.label.isEmpty()) {
            List<JSONObject> labeled=new ArrayList<>();for(JSONObject s:candidates) if(selected.label.equals(s.optString("Title"))) labeled.add(s);
            if(labeled.size()==1) return labeled.get(0).optInt("Index",-1);
        }
        return candidates.size()==1?candidates.get(0).optInt("Index",-1):-1;
    }
    static JSONObject measurement(JSONArray values,int index,String profile) {
        if(index<0) return null;
        for(int i=0;i<values.length();i++) { JSONObject row=values.optJSONObject(i);if(row!=null&&row.optInt("streamIndex",-1)==index&&profile.equals(row.optString("profile"))) return row; }
        return null;
    }
}
