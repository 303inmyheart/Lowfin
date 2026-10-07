package de.lowfin.tv;
import org.junit.Test;
import org.json.JSONObject;
import okhttp3.HttpUrl;
import java.util.List;
import static org.junit.Assert.*;
public class LiveStreamsTest {
    private final Api api=new Api(new Account("https://tv.example/jellyfin","u","secret","Rico","device"));
    @Test public void openedManagedSourcePathWinsEvenIfServerMarksItTranscodeOnly() {
        JSONObject source=Json.obj("Id","source","LiveStreamId","opened","Path","http://127.0.0.1:8096/LiveTv/LiveStreamFiles/opened/stream.ts","SupportsDirectPlay",false,"TranscodingUrl","/Videos/id/master.m3u8?VideoCodec=h264&AudioCodec=aac");
        List<String> urls=LiveStreams.candidates(api,"channel",source,"session",false,false);
        assertEquals(2,urls.size()); assertTrue(urls.get(0).startsWith("https://tv.example/jellyfin/LiveTv/LiveStreamFiles/")); assertTrue(urls.get(0).contains("LiveStreamId=opened"));
    }
    @Test public void upstreamCredentialsAndUrlStaySeparateFromJellyfinSession() {
        JSONObject source=Json.obj("LiveStreamId","opened","Path","https://iptv.example/channel?token=upstream");
        String url=LiveStreams.candidates(api,"channel",source,"session",false,false).get(0);
        assertEquals("https://iptv.example/channel?token=upstream",url); assertFalse(LiveStreams.server(api,url));
    }
    @Test public void directStreamUrlCarriesLiveSessionAndSurvivesReverseProxyPrefix() {
        JSONObject source=Json.obj("Id","static","LiveStreamId","actual","DirectStreamUrl","/Videos/channel/stream.ts?livestreamid=stale&AudioStreamIndex=2","SupportsDirectStream",true);
        HttpUrl url=HttpUrl.get(LiveStreams.candidates(api,"channel",source,"session",false,false).get(0));
        assertEquals("/jellyfin/Videos/channel/stream.ts",url.encodedPath()); assertNull(url.queryParameter("livestreamid")); assertEquals("actual",url.queryParameter("LiveStreamId")); assertEquals("2",url.queryParameter("AudioStreamIndex"));
    }
    @Test public void transcodingIsOnlySelectedWhenExplicitlyAllowedOrItIsCodecCopy() {
        JSONObject source=Json.obj("TranscodingUrl","/Videos/channel/master.m3u8?VideoCodec=h264&AudioCodec=aac");
        assertTrue(LiveStreams.candidates(api,"channel",source,"session",false,false).isEmpty()); assertEquals(1,LiveStreams.candidates(api,"channel",source,"session",true,false).size());
        source=Json.obj("TranscodingUrl","/Videos/channel/master.m3u8?VideoCodec=copy&AudioCodec=copy"); assertEquals(1,LiveStreams.candidates(api,"channel",source,"session",false,false).size());
    }
}
