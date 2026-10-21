package de.lowfin.tv;

import org.json.*;

/** Keep optional GPU processing out of live, HDR, unknown and oversized video paths. */
final class DenoisePolicy {
    /** A short high-rate drop burst during settled playback, not a renderer's lifetime count. */
    static boolean overloaded(int dropped,long intervalMs,long stableReadyMs,boolean playing,boolean ready) {
        return playing&&ready&&stableReadyMs>=5000&&dropped>=12&&intervalMs>=1000&&intervalMs<=10000
            &&(long)dropped*1000>=intervalMs*3;
    }
    static boolean allowed(boolean enabled,boolean live,JSONObject source) {
        if(!enabled||live||source==null||source.optBoolean("IsEncrypted")) return false;
        for(int i=0;i<Json.array(source,"MediaStreams").length();i++) {
            JSONObject stream=Json.array(source,"MediaStreams").optJSONObject(i);if(stream==null||!"Video".equals(stream.optString("Type"))) continue;
            int width=stream.optInt("Width"),height=stream.optInt("Height");
            if(width<=0||height<=0||width>1920||height>1080) return false;
            String range=stream.optString("VideoRangeType",stream.optString("VideoRange",""));String transfer=stream.optString("ColorTransfer","").toLowerCase(java.util.Locale.ROOT);
            if(stream.optInt("BitDepth",8)>8||transfer.contains("2084")||transfer.contains("b67")||transfer.contains("pq")||transfer.contains("hlg")) return false;
            return "SDR".equalsIgnoreCase(range);
        }
        return false;
    }
}
