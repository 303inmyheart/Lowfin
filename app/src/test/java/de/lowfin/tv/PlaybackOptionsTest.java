package de.lowfin.tv;
import android.content.*;
import org.robolectric.RuntimeEnvironment;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class PlaybackOptionsTest {
    private SharedPreferences p;
    @Before public void setup() {p=RuntimeEnvironment.getApplication().getSharedPreferences("playback-options-test",0);p.edit().clear().commit();}
    private JSONObject request() {return PlaybackPolicy.request("user",720,4000000,false,true,0,-1,-1,null,false);}
    @Test public void defaultsAndStereoOverrideArePreserved() throws Exception {
        assertEquals(30,PlaybackOptions.fps(p));assertEquals(720,PlaybackOptions.trailerHeight(p));
        p.edit().putInt("audioChannels",8).commit();JSONObject r=request();PlaybackOptions.negotiate(r,p);assertEquals(2,r.getInt("MaxAudioChannels"));
        p.edit().putBoolean("stereo",false).putInt("videoFps",60).commit();r=request();PlaybackOptions.negotiate(r,p);assertEquals(8,r.getInt("MaxAudioChannels"));
        JSONObject profile=r.getJSONObject("DeviceProfile");assertEquals("8",profile.getJSONArray("TranscodingProfiles").getJSONObject(0).getString("MaxAudioChannels"));
        assertEquals("60",profile.getJSONArray("CodecProfiles").getJSONObject(0).getJSONArray("Conditions").getJSONObject(3).getString("Value"));
    }
    @Test public void automaticRemovesNegotiationCapsAndWorksAfterLocalNegotiation() throws Exception {
        p.edit().putBoolean("stereo",false).putInt("audioChannels",0).putInt("videoFps",0).commit();JSONObject r=request();PlaybackOptions.negotiate(r,p);
        assertFalse(r.has("MaxAudioChannels"));assertFalse(r.getJSONObject("DeviceProfile").getJSONArray("TranscodingProfiles").getJSONObject(0).has("MaxAudioChannels"));
        assertEquals(3,r.getJSONObject("DeviceProfile").getJSONArray("CodecProfiles").getJSONObject(0).getJSONArray("Conditions").length());
        assertEquals(0,r.getJSONObject("DeviceProfile").getJSONArray("CodecProfiles").getJSONObject(1).getJSONArray("Conditions").length());
        r=request();LocalPlayback.negotiate(r);PlaybackOptions.negotiate(r,p);assertFalse(r.has("MaxAudioChannels"));
    }
}
