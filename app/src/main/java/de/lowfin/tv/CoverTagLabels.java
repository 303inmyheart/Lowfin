package de.lowfin.tv;

import org.json.*;
import java.util.*;

/** Small validated wire labels, independent from bitmap keys and source image dimensions. */
final class CoverTagLabels {
    private static final java.util.regex.Pattern LANGUAGE=java.util.regex.Pattern.compile("(?:lang|vost):[a-z]{2,3}");
    static final String[] EMPTY=new String[0];
    static final Set<String> KEYS=new HashSet<>(Arrays.asList("dvd","5.1","hdr","4k","1080p","720p","hdr10","hdr10plus","dv","hlg","3d","h264","hevc","av1","vp9","atmos","dtsx","truehd","dtshdma","7.1","stereo","languages","subtitles"));
    static String[] decode(String stored) {try {return stored.isEmpty()?null:parse(new JSONArray(stored));}catch(Exception ignored){return null;}}
    static String[] parse(JSONArray data) {
        if(data==null||data.length()>32) return null;LinkedHashSet<String> values=new LinkedHashSet<>();
        for(int i=0;i<data.length();i++) {String value=data.optString(i);if(KEYS.contains(value)||LANGUAGE.matcher(value).matches()) values.add(value);}
        return values.toArray(EMPTY);
    }
    static boolean hdr(String key) {return "hdr".equals(key)||"hdr10".equals(key)||"hdr10plus".equals(key)||"dv".equals(key)||"hlg".equals(key);}
    static String[] filter(String[] labels,Set<String> enabled,boolean hdr) {
        List<String> visible=new ArrayList<>();for(String key:labels) {
            String group=key.startsWith("lang:")?"languages":key.startsWith("vost:")?"subtitles":key;
            if(enabled.contains(group)&&(hdr||!hdr(key))) visible.add(key);
        }return visible.toArray(EMPTY);
    }
    static String text(String key) {
        if(key.startsWith("lang:")) return key.substring(5).toUpperCase(Locale.ROOT);
        if(key.startsWith("vost:")) return "VOST"+key.substring(5).toUpperCase(Locale.ROOT);
        switch(key) {
            case "dvd":return "DVD";case "4k":return "4K";case "hdr10plus":return "HDR10+";case "dv":return "Dolby Vision";
            case "h264":return "H.264";case "hevc":return "HEVC";case "av1":return "AV1";case "vp9":return "VP9";
            case "atmos":return "Atmos";case "dtsx":return "DTS:X";case "dtshdma":return "DTS-HD MA";case "truehd":return "TrueHD";case "stereo":return "2.0";
            case "hdr":return "HDR";case "hdr10":return "HDR10";case "hlg":return "HLG";case "3d":return "3D";default:return key;
        }
    }
}
