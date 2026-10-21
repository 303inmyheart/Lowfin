package de.lowfin.tv;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DenoisePolicyTest {
    private JSONObject source(int width,int height,String range) { return Json.obj("MediaStreams",Json.arr(Json.obj("Type","Audio"),Json.obj("Type","Video","Width",width,"Height",height,"VideoRangeType",range,"BitDepth",8))); }
    @Test public void lifetimeCountsAndEpisodeTransitionsDoNotTriggerFallback() {
        assertFalse(DenoisePolicy.overloaded(42,554940,550000,true,true));
        assertFalse(DenoisePolicy.overloaded(42,554940,0,false,false));
        assertFalse(DenoisePolicy.overloaded(50,4000,4000,true,true));
        assertFalse(DenoisePolicy.overloaded(50,4000,10000,false,true));
        assertFalse(DenoisePolicy.overloaded(50,4000,10000,true,false));
        assertFalse(DenoisePolicy.overloaded(12,9000,10000,true,true));
        assertFalse(DenoisePolicy.overloaded(50,0,10000,true,true));
        assertFalse(DenoisePolicy.overloaded(50,-1,10000,true,true));
    }
    @Test public void sustainedHighRateDropsStillTriggerTheProtection() {
        assertTrue(DenoisePolicy.overloaded(12,4000,5000,true,true));
        assertTrue(DenoisePolicy.overloaded(50,5000,10000,true,true));
        assertFalse(DenoisePolicy.overloaded(11,1000,10000,true,true));
    }
    @Test public void disabledLiveUnknownHdrAndLargeSourcesNeverUseTheFilter() throws Exception {
        JSONObject sdr=source(1280,720,"SDR");assertFalse(DenoisePolicy.allowed(false,false,sdr));assertFalse(DenoisePolicy.allowed(true,true,sdr));assertTrue(DenoisePolicy.allowed(true,false,sdr));
        assertTrue(DenoisePolicy.allowed(true,false,source(1920,1080,"SDR")));assertFalse(DenoisePolicy.allowed(true,false,source(3840,2160,"SDR")));assertFalse(DenoisePolicy.allowed(true,false,source(0,0,"SDR")));
        assertFalse(DenoisePolicy.allowed(true,false,null));assertFalse(DenoisePolicy.allowed(true,false,new JSONObject()));assertFalse(DenoisePolicy.allowed(true,false,source(1280,720,"Unknown")));
        assertFalse(DenoisePolicy.allowed(true,false,source(1280,720,"HDR10")));sdr.getJSONArray("MediaStreams").getJSONObject(1).put("ColorTransfer","smpte2084");assertFalse(DenoisePolicy.allowed(true,false,sdr));
        sdr=source(1280,720,"SDR");sdr.put("IsEncrypted",true);assertFalse(DenoisePolicy.allowed(true,false,sdr));
        sdr=source(1280,720,"SDR");sdr.getJSONArray("MediaStreams").getJSONObject(1).put("BitDepth",10);assertFalse(DenoisePolicy.allowed(true,false,sdr));
    }
}
