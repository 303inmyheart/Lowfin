package de.lowfin.tv;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
public class LiveSessionTest {
    @Test public void missingAutoOpenedSessionIsExplicitlyOpenedWithTheSameCopyAndEncodingPermissions() throws Exception {
        MockWebServer server=new MockWebServer(); server.enqueue(new MockResponse().setBody("{\"MediaSource\":{\"Id\":\"opened-source\",\"LiveStreamId\":\"live-session\",\"Path\":\"http://127.0.0.1/LiveTv/LiveStreamFiles/live/stream.ts\"}}")); server.start();
        try {
            Api api=new Api(new Account(server.url("/").toString().replaceAll("/+$",""),"user","token","Rico","device"));
            JSONObject request=Json.obj("DeviceProfile",Json.obj("Name","Lowfin"),"EnableDirectPlay",true,"EnableDirectStream",true,"EnableTranscoding",false);
            JSONObject opened=LiveStreams.openIfNeeded(api,Json.obj("RequiresOpening",true,"OpenToken","open-token"),request,"play-session"); assertEquals("live-session",opened.optString("LiveStreamId"));
            RecordedRequest call=server.takeRequest(); assertEquals("/LiveStreams/Open",call.getPath()); JSONObject body=new JSONObject(call.getBody().readUtf8());
            assertEquals("open-token",body.optString("OpenToken")); assertEquals("play-session",body.optString("PlaySessionId")); assertFalse(body.optBoolean("EnableTranscoding")); assertTrue(body.optBoolean("AllowAudioStreamCopy")); assertTrue(call.getHeader("Authorization").contains("token"));
        } finally { server.shutdown(); }
    }
    @Test public void existingLiveSessionIsNeverOpenedASecondTime() throws Exception {
        JSONObject source=Json.obj("RequiresOpening",true,"LiveStreamId","already-open"); assertSame(source,LiveStreams.openIfNeeded(null,source,new JSONObject(),"session"));
    }
}
