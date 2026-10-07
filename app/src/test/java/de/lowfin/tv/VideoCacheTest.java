package de.lowfin.tv;

import androidx.media3.datasource.*;
import okhttp3.mockwebserver.*;
import okio.Buffer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class VideoCacheTest {
    private MockWebServer server;
    private VideoCache cache;
    private Api api;
    @Before public void setup() throws Exception {
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            byte[] content=new byte[600]; java.util.Arrays.fill(content,(byte)42);
            return new MockResponse().setBody(new Buffer().write(content));
        } }); server.start(); cache=new VideoCache(RuntimeEnvironment.getApplication(),1024);
        api=new Api(new Account(server.url("/").toString(),"user","token","User","device"));
    }
    @After public void stop() throws Exception { cache.close(); server.shutdown(); }
    private byte[] read(DataSource.Factory factory,String url) throws Exception {
        DataSource source=factory.createDataSource(); ByteArrayOutputStream result=new ByteArrayOutputStream();
        try { source.open(new DataSpec.Builder().setUri(url).build()); byte[] bytes=new byte[128]; int n; while((n=source.read(bytes,0,bytes.length))!=-1) result.write(bytes,0,n); }
        finally { source.close(); } return result.toByteArray();
    }
    @Test public void finiteVideoIsReusedAcrossPlaybackSessionsWithAuthOnNetworkOnly() throws Exception {
        DataSource.Factory factory=cache.factory(api,false,false);
        String url=server.url("/Videos/movie/stream?Static=true&MediaSourceId=version&PlaySessionId=one").toString();
        assertEquals(600,read(factory,url).length); assertEquals(600,read(factory,url.replace("one","two")).length);
        assertEquals(1,server.getRequestCount()); assertTrue(server.takeRequest().getHeader("Authorization").contains("token"));
    }
    @Test public void capacityEvictsOldVideoInsteadOfGrowingWithoutLimit() throws Exception {
        assertEquals(500L*1024*1024,VideoCache.MAX_BYTES);
        DataSource.Factory factory=cache.factory(api,false,false);
        String one=server.url("/Videos/one/stream?Static=true").toString(),two=server.url("/Videos/two/stream?Static=true").toString();
        read(factory,one); read(factory,two); read(factory,two); assertEquals(2,server.getRequestCount()); read(factory,one); assertEquals(3,server.getRequestCount());
    }
    @Test public void accountsCannotReuseEachOthersAuthorizedVideo() throws Exception {
        String url=server.url("/Videos/movie/stream?Static=true").toString(); read(cache.factory(api,false,false),url);
        Api other=new Api(new Account(api.account.server,"other","other-token","Other","device")); read(cache.factory(other,false,false),url); assertEquals(2,server.getRequestCount());
    }
    @Test public void livePlaylistsAlwaysReloadAndSegmentsNeverLeakIntoANewLiveSession() throws Exception {
        String manifest=server.url("/live/master.m3u8").toString(),segment=server.url("/live/segment.ts").toString();
        DataSource.Factory factory=cache.factory(api,true,true); read(factory,manifest); read(factory,manifest); assertEquals(2,server.getRequestCount());
        read(factory,segment); read(factory,segment); assertEquals(3,server.getRequestCount());
        read(cache.factory(api,true,true),segment); assertEquals(4,server.getRequestCount());
    }
    @Test public void continuousLiveNeverSeeksToAReportedFileEndAndAlwaysFetchesFresh() throws Exception {
        String url=server.url("/Videos/channel/stream?Static=true&LiveStreamId=live").toString();
        DataSource source=cache.factory(api,true,false).createDataSource();
        try { assertEquals(androidx.media3.common.C.LENGTH_UNSET,source.open(new DataSpec.Builder().setUri(url).build())); byte[] bytes=new byte[600]; assertTrue(source.read(bytes,0,bytes.length)>0); }
        finally { source.close(); }
        assertEquals(600,read(cache.factory(api,true,false),url).length); assertEquals(2,server.getRequestCount());
        assertTrue(server.takeRequest().getHeader("Authorization").contains("token"));
    }
    @Test public void remoteLiveUpstreamNeverReceivesTheJellyfinAuthorizationHeader() throws Exception {
        MockWebServer remote=new MockWebServer(); remote.enqueue(new MockResponse().setBody("remote-live")); remote.start();
        try { assertEquals(11,read(cache.factory(api,true,false),remote.url("/channel.ts").toString()).length); assertNull(remote.takeRequest().getHeader("Authorization")); } finally { remote.shutdown(); }
    }
    @Test public void clearingCacheMakesNextPlaybackFetchFreshData() throws Exception {
        String url=server.url("/Videos/movie/stream?Static=true").toString(); DataSource.Factory factory=cache.factory(api,false,false);
        read(factory,url); cache.clear(); read(factory,url); assertEquals(2,server.getRequestCount());
    }
    @Test public void savedBudgetCanDisableAndReenableVideoCaching() throws Exception {
        cache.close(); android.content.Context context=RuntimeEnvironment.getApplication(); cache=new VideoCache(context);
        context.getSharedPreferences("settings",0).edit().putInt("videoCacheMb",128).apply();
        String url=server.url("/Videos/movie/stream?Static=true").toString(); DataSource.Factory factory=cache.factory(api,false,false);
        read(factory,url); read(factory,url); assertEquals(1,server.getRequestCount());
        context.getSharedPreferences("settings",0).edit().putInt("videoCacheMb",0).apply(); factory=cache.factory(api,false,false);
        read(factory,url); read(factory,url); assertEquals(3,server.getRequestCount());
        context.getSharedPreferences("settings",0).edit().putInt("videoCacheMb",500).apply(); factory=cache.factory(api,false,false);
        read(factory,url); assertEquals(3,server.getRequestCount());
    }
}
