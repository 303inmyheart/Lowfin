package de.lowfin.tv;

import android.view.Display;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class HdrPolicyTest {
    private int[] hdr() { return new int[]{Display.HdrCapabilities.HDR_TYPE_HDR10,Display.HdrCapabilities.HDR_TYPE_HLG}; }
    @Test public void OffAlwaysRestrictsToSdrEvenOnHdrHardware() { assertEquals("Unknown|SDR",HdrPolicy.ranges(false,hdr(),true)); }
    @Test public void HdrRequiresBothDisplayAndTenBitDecoder() {
        assertEquals("Unknown|SDR",HdrPolicy.ranges(true,hdr(),false));
        assertEquals("Unknown|SDR",HdrPolicy.ranges(true,new int[0],true));
        assertEquals("Unknown|SDR|HDR10|HLG",HdrPolicy.ranges(true,hdr(),true));
    }
    @Test public void DoesNotClaimDolbyVisionFromDisplayAlone() { assertEquals("Unknown|SDR",HdrPolicy.ranges(true,new int[]{Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION},true)); }
    @Test public void ProfileKeepsExistingLimitsAndDoesNotEnableServerTranscoding() throws Exception {
        JSONObject request=PlaybackPolicy.request("user",720,4000000,false,false,0,-1,-1,null,true); request.put("EnableTranscoding",false);
        int count=request.getJSONObject("DeviceProfile").getJSONArray("CodecProfiles").length();
        HdrPolicy.apply(request,Collections.singletonMap("hevc","Unknown|SDR|HDR10"));
        JSONArray codecs=request.getJSONObject("DeviceProfile").getJSONArray("CodecProfiles"); assertEquals(count+2,codecs.length());
        assertEquals("VideoRangeType",codecs.getJSONObject(count).getJSONArray("Conditions").getJSONObject(0).getString("Property"));
        assertEquals("Unknown|SDR|HDR10",codecs.getJSONObject(count).getJSONArray("Conditions").getJSONObject(0).getString("Value"));
        assertFalse(request.getBoolean("EnableTranscoding")); assertEquals(2,request.getInt("MaxAudioChannels"));
    }
    @Test public void DefaultSettingIsOffAndLocalNegotiationCannotEraseHdrPolicy() throws Exception {
        android.content.Context app=RuntimeEnvironment.getApplication(); assertFalse(app.getSharedPreferences("settings",0).getBoolean("hdrPlayback",false));
        JSONObject request=PlaybackPolicy.request("user",720,4000000,false,false,0,-1,-1,null,true);LocalPlayback.negotiate(request);HdrPolicy.configure(app,request,false);
        JSONArray codecs=request.getJSONObject("DeviceProfile").getJSONArray("CodecProfiles");assertEquals(4,codecs.length());
        for(int i=0;i<codecs.length();i++) assertEquals("Unknown|SDR",codecs.getJSONObject(i).getJSONArray("Conditions").getJSONObject(0).getString("Value"));
    }
}
