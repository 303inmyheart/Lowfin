package de.lowfin.tv;

import org.json.*;
import java.text.NumberFormat;
import java.util.*;

/** Source dimensions and original file bytes; never the TV's output or a transcoded stream. */
final class VideoDetails {
    static String describe(JSONObject item,Locale locale) {
        if("Series".equals(item.optString("Type"))||"Season".equals(item.optString("Type"))) return "";
        JSONArray sources=Json.array(item,"MediaSources"); List<String> lines=new ArrayList<>();
        if(sources.length()==0) return dimensions(Json.array(item,"MediaStreams"));
        for(int i=0;i<sources.length();i++) {
            JSONObject source=sources.optJSONObject(i); if(source==null) continue;
            String resolution=dimensions(Json.array(source,"MediaStreams"));
            if(resolution.isEmpty()&&sources.length()==1) resolution=dimensions(Json.array(item,"MediaStreams"));
            String size=fileSize(source.optLong("Size"),locale); String facts=resolution+(resolution.isEmpty()||size.isEmpty()?"":"  ·  ")+size;
            if(facts.isEmpty()) continue;
            if(sources.length()>1) { String name=Json.str(source,"Name"); if(name.isEmpty()) name="Version "+(i+1); facts=name+"  ·  "+facts; }
            lines.add(facts);
        }
        return String.join("\n",lines);
    }
    private static String dimensions(JSONArray streams) {
        for(int i=0;i<streams.length();i++) {
            JSONObject stream=streams.optJSONObject(i); if(stream==null||!"Video".equals(stream.optString("Type"))||stream.optBoolean("IsExternal")) continue;
            int width=stream.optInt("Width"),height=stream.optInt("Height");
            if(width>0&&height>0) return width+" × "+height;
        }
        return "";
    }
    static String fileSize(long bytes,Locale locale) {
        if(bytes<=0) return "";
        String[] units={"B","kB","MB","GB","TB","PB"}; double value=bytes; int unit=0;
        while(value>=1000&&unit<units.length-1) { value/=1000; unit++; }
        NumberFormat format=NumberFormat.getNumberInstance(locale); format.setGroupingUsed(false); format.setMaximumFractionDigits(unit==0?0:1);
        return format.format(value)+" "+units[unit];
    }
}
