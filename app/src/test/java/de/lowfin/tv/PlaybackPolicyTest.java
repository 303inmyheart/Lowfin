package de.lowfin.tv;

import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class PlaybackPolicyTest {
    @Test public void onlyIntroAndOutroAreAutomatic() {
        assertTrue(PlaybackPolicy.automatic("Intro")); assertTrue(PlaybackPolicy.automatic("Outro"));
        for(String type:new String[]{"Recap","Preview","Commercial","Unknown","Credits",""}) assertFalse(type,PlaybackPolicy.automatic(type));
    }
    @Test public void segmentBoundariesUseJellyfinTicksAndExclusiveEnd() {
        JSONObject s=Json.obj("StartTicks",600000000L,"EndTicks",900000000L);
        assertFalse(PlaybackPolicy.active(s,59999)); assertTrue(PlaybackPolicy.active(s,60000));
        assertTrue(PlaybackPolicy.active(s,89999)); assertFalse(PlaybackPolicy.active(s,90000));
    }
    @Test public void emptyAndReversedSegmentsCannotSeekLoop() {
        assertFalse(PlaybackPolicy.active(Json.obj("StartTicks",20L,"EndTicks",10L),0));
        assertFalse(PlaybackPolicy.active(new JSONObject(),0));
    }
    @Test public void liveAlwaysOpensTunerAndKeepsServerFallback() {
        JSONObject r=PlaybackPolicy.request("user",720,4000000,true,false,0,-1,-1,null);
        assertTrue(r.optBoolean("AutoOpenLiveStream")); assertFalse(r.optBoolean("EnableDirectPlay"));
        assertTrue(r.optBoolean("EnableDirectStream")); assertTrue(r.optBoolean("EnableTranscoding"));
    }
    @Test public void forceHlsDisablesBothDirectModes() {
        JSONObject r=PlaybackPolicy.request("user",720,4000000,false,true,60000,2,4,"version");
        assertFalse(r.optBoolean("EnableDirectPlay")); assertFalse(r.optBoolean("EnableDirectStream")); assertTrue(r.optBoolean("EnableTranscoding"));
        assertEquals(600000000L,r.optLong("StartTimeTicks")); assertEquals(2,r.optInt("AudioStreamIndex"));
        assertEquals(4,r.optInt("SubtitleStreamIndex")); assertEquals("version",r.optString("MediaSourceId"));
    }
    @Test public void hdProfileRestrictsExpensiveCodecsAndSurround() {
        JSONObject profile=PlaybackPolicy.profile(720,4000000);
        JSONObject direct=profile.optJSONArray("DirectPlayProfiles").optJSONObject(0);
        assertEquals("h264",direct.optString("VideoCodec")); assertEquals("aac,mp3",direct.optString("AudioCodec"));
        JSONObject hls=profile.optJSONArray("TranscodingProfiles").optJSONObject(0);
        assertEquals("hls",hls.optString("Protocol")); assertEquals("2",hls.optString("MaxAudioChannels"));
        JSONArray conditions=profile.optJSONArray("CodecProfiles").optJSONObject(0).optJSONArray("Conditions");
        assertEquals("1280",conditions.optJSONObject(0).optString("Value")); assertEquals("720",conditions.optJSONObject(1).optString("Value"));
        assertEquals("8",conditions.optJSONObject(2).optString("Value")); assertEquals("30",conditions.optJSONObject(3).optString("Value"));
    }
    @Test public void stereoSwitchControlsServerNegotiationAndTranscoding() {
        for(boolean stereo:new boolean[]{true,false}) {
            JSONObject r=PlaybackPolicy.request("user",720,4000000,false,false,0,-1,-1,null,stereo);
            assertEquals(stereo?2:6,r.optInt("MaxAudioChannels"));
            JSONObject p=r.optJSONObject("DeviceProfile");
            assertEquals(stereo?"2":"6",p.optJSONArray("TranscodingProfiles").optJSONObject(0).optString("MaxAudioChannels"));
            assertEquals(stereo?"2":"6",p.optJSONArray("CodecProfiles").optJSONObject(1).optJSONArray("Conditions").optJSONObject(0).optString("Value"));
        }
    }
    @Test public void resumePositionUsesMillisecondsWithoutIntegerOverflow() {
        JSONObject item=Json.obj("UserData",Json.obj("PlaybackPositionTicks",72000000000L));
        assertEquals(7200000L,Json.position(item));
    }
    @Test public void nullableServerFieldsDoNotBecomeLiteralNullStrings() {
        JSONObject response=Json.obj("ErrorCode",JSONObject.NULL,"LiveStreamId",JSONObject.NULL);
        assertEquals("",Json.str(response,"ErrorCode")); assertEquals("",Json.str(response,"LiveStreamId")); assertEquals("",Json.str(response,"Missing"));
    }
    @Test public void cacheScopesIsolateServerAndUser() {
        Account a=new Account("https://a.example/","user1","token","name","device");
        assertEquals(a.scope(),new Account("https://a.example","user1","other-token","new-name","device").scope());
        assertNotEquals(a.scope(),new Account("https://b.example","user1","token","name","device").scope());
        assertNotEquals(a.scope(),new Account("https://a.example","user2","token","name","device").scope());
    }
}
