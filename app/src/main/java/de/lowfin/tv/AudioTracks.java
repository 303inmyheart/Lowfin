package de.lowfin.tv;

import org.json.*;
import java.util.*;

/** Jellyfin stream indices are file indices, not audio-track ordinals. */
final class AudioTracks {
    static final class Choice {
        final String sourceId,label; final int index;
        Choice(String sourceId,int index,String label) { this.sourceId=sourceId; this.index=index; this.label=label; }
    }
    static List<Choice> embedded(JSONObject item) {
        List<Choice> result=new ArrayList<>(); JSONArray sources=Json.array(item,"MediaSources");
        for(int n=0;n<sources.length();n++) {
            JSONObject source=sources.optJSONObject(n); if(source==null) continue;
            JSONArray streams=Json.array(source,"MediaStreams");
            for(int i=0;i<streams.length();i++) {
                JSONObject stream=streams.optJSONObject(i);
                if(stream==null||!"Audio".equals(stream.optString("Type"))||stream.optBoolean("IsExternal")||stream.optInt("Index",-1)<0) continue;
                String label=stream.optString("DisplayTitle");
                if(label.isEmpty()) label=stream.optString("Language","und")+" · "+stream.optString("Codec","audio")+" · "+stream.optInt("Channels",2)+" ch";
                if(sources.length()>1) label=source.optString("Name",Integer.toString(n+1))+" · "+label;
                result.add(new Choice(source.optString("Id"),stream.optInt("Index"),label));
            }
        }
        return result;
    }
}
