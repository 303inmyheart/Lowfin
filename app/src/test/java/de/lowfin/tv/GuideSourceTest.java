package de.lowfin.tv;

import org.junit.*;
import org.json.*;
import okhttp3.mockwebserver.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class GuideSourceTest {
    private MockWebServer server;private LiveTv tv;
    @Before public void setup() throws Exception {server=new MockWebServer();server.start();tv=new LiveTv(new Api(new Account(server.url("/").toString(),"user","token","Name","device")));}
    @After public void stop() throws Exception {server.shutdown();}
    private JSONObject programme(String channel){Instant now=Instant.now();return Json.obj("ChannelId",channel,"StartDate",now.plusSeconds(86400).toString(),"EndDate",now.plusSeconds(90000).toString());}
    @Test public void filteringFillsPagesWithoutSkippingChannelsOrTreatingFutureOnlyDataAsEmpty() throws Exception {
        server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest request){String path=request.getRequestUrl().encodedPath();JSONArray items=new JSONArray();if(path.endsWith("Channels")){int start=Integer.parseInt(request.getRequestUrl().queryParameter("StartIndex")),limit=Integer.parseInt(request.getRequestUrl().queryParameter("Limit"));for(int i=start;i<Math.min(100,start+limit);i++)items.put(Json.obj("Id","c"+i));}else for(String id:request.getRequestUrl().queryParameter("ChannelIds").split(","))if(Integer.parseInt(id.substring(1))%3==0)items.put(programme(id));return new MockResponse().setBody(Json.obj("Items",items).toString());}});
        GuideSource source=new GuideSource(tv);GuideSource.Page first=source.page(0,10,true,1,()->true),second=source.page(1,10,true,1,()->true);assertEquals(10,first.channels.size());assertEquals(10,second.channels.size());assertEquals("c0",first.channels.get(0).optString("Id"));assertEquals("c27",first.channels.get(9).optString("Id"));assertEquals("c30",second.channels.get(0).optString("Id"));assertEquals("c57",second.channels.get(9).optString("Id"));assertTrue(first.more);assertTrue(second.more);assertEquals(4,server.getRequestCount());source.page(0,10,true,1,()->true);assertEquals(4,server.getRequestCount());
    }
    @Test public void disablingTheGuideFilterUsesOrdinaryPagedChannelsWithoutEpgProbes() throws Exception {
        JSONArray channels=new JSONArray();for(int i=0;i<11;i++)channels.put(Json.obj("Id","c"+i));server.enqueue(new MockResponse().setBody(Json.obj("Items",channels).toString()));GuideSource.Page page=new GuideSource(tv).page(2,10,false,0,()->true);assertEquals(10,page.channels.size());assertTrue(page.more);RecordedRequest request=server.takeRequest();assertEquals("/LiveTv/Channels",request.getRequestUrl().encodedPath());assertEquals("20",request.getRequestUrl().queryParameter("StartIndex"));assertEquals("11",request.getRequestUrl().queryParameter("Limit"));assertEquals(1,server.getRequestCount());
    }
    @Test public void aFailedPresenceCheckCannotEraseChannelsOrAdvanceTheCursor() throws Exception {
        server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","a"))).toString()));server.enqueue(new MockResponse().setResponseCode(503));GuideSource source=new GuideSource(tv);assertThrows(Api.Failure.class,()->source.page(0,10,true,1,()->true));server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","a"))).toString()));server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(programme("a"))).toString()));GuideSource.Page page=source.page(0,10,true,1,()->true);assertEquals(1,page.channels.size());assertFalse(page.more);server.takeRequest();server.takeRequest();assertEquals("0",server.takeRequest().getRequestUrl().queryParameter("StartIndex"));
    }
    @Test public void emptyGuideBatchNeedsOnlyOneProbeAndCancellationMakesNoRequests() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));Map<String,Boolean> presence=tv.guidePresence(Arrays.asList(Json.obj("Id","a"),Json.obj("Id","b")),()->true);assertEquals(Boolean.FALSE,presence.get("a"));assertEquals(Boolean.FALSE,presence.get("b"));RecordedRequest request=server.takeRequest();assertNull(request.getRequestUrl().queryParameter("MaxStartDate"));assertEquals("false",request.getRequestUrl().queryParameter("EnableImages"));assertEquals("user",request.getRequestUrl().queryParameter("UserId"));assertThrows(java.util.concurrent.CancellationException.class,()->new GuideSource(tv).page(0,25,true,0,()->false));assertEquals(1,server.getRequestCount());
    }
    @Test public void truncatedPresenceResponseChecksOnlyUnknownChannelsInsteadOfHidingThem() throws Exception {
        JSONArray dense=new JSONArray();for(int i=0;i<513;i++)dense.put(programme("a"));server.enqueue(new MockResponse().setBody(Json.obj("Items",dense).toString()));server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(programme("b"))).toString()));Map<String,Boolean> data=tv.guidePresence(Arrays.asList(Json.obj("Id","a"),Json.obj("Id","b"),Json.obj("Id","c")),()->true);assertEquals(Boolean.TRUE,data.get("a"));assertEquals(Boolean.TRUE,data.get("b"));assertEquals(Boolean.FALSE,data.get("c"));server.takeRequest();RecordedRequest fallback=server.takeRequest();assertEquals("b,c",fallback.getRequestUrl().queryParameter("ChannelIds"));assertEquals("17",fallback.getRequestUrl().queryParameter("Limit"));assertEquals(2,server.getRequestCount());
    }
    @Test public void manyUnknownChannelsAreResolvedTogetherAfterADenseFirstResponse() throws Exception {
        JSONArray dense=new JSONArray();for(int i=0;i<513;i++)dense.put(programme("c0"));server.enqueue(new MockResponse().setBody(Json.obj("Items",dense).toString()));server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));List<JSONObject> channels=new ArrayList<>();for(int i=0;i<50;i++)channels.add(Json.obj("Id","c"+i));Map<String,Boolean> data=tv.guidePresence(channels,()->true);assertTrue(data.get("c0"));for(int i=1;i<50;i++)assertFalse(data.get("c"+i));assertEquals(2,server.getRequestCount());server.takeRequest();RecordedRequest remaining=server.takeRequest();assertEquals(49,remaining.getRequestUrl().queryParameter("ChannelIds").split(",").length);assertEquals("17",remaining.getRequestUrl().queryParameter("Limit"));
    }
    @Test public void multipleTruncatedBatchesKeepRemovingKnownPositivesUntilAbsenceIsProven() throws Exception {
        JSONArray first=new JSONArray(),second=new JSONArray();for(int i=0;i<513;i++)first.put(programme("a"));for(int i=0;i<17;i++)second.put(programme("b"));server.enqueue(new MockResponse().setBody(Json.obj("Items",first).toString()));server.enqueue(new MockResponse().setBody(Json.obj("Items",second).toString()));server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));Map<String,Boolean> data=tv.guidePresence(Arrays.asList(Json.obj("Id","a"),Json.obj("Id","b"),Json.obj("Id","c"),Json.obj("Id","d")),()->true);assertTrue(data.get("a"));assertTrue(data.get("b"));assertFalse(data.get("c"));assertFalse(data.get("d"));server.takeRequest();assertEquals("b,c,d",server.takeRequest().getRequestUrl().queryParameter("ChannelIds"));assertEquals("c,d",server.takeRequest().getRequestUrl().queryParameter("ChannelIds"));assertEquals(3,server.getRequestCount());
    }
    @Test public void noProgressInATruncatedMalformedResponseFailsWithoutHidingChannels() throws Exception {
        JSONArray foreign=new JSONArray();for(int i=0;i<513;i++)foreign.put(programme("foreign"));server.enqueue(new MockResponse().setBody(Json.obj("Items",foreign).toString()));assertThrows(java.io.IOException.class,()->tv.guidePresence(Collections.singletonList(Json.obj("Id","a")),()->true));assertEquals(1,server.getRequestCount());
    }
    @Test public void cancellationBetweenPresenceBatchesStopsFurtherRequests() throws Exception {
        JSONArray first=new JSONArray();for(int i=0;i<513;i++)first.put(programme("a"));server.enqueue(new MockResponse().setBody(Json.obj("Items",first).toString()));java.util.concurrent.atomic.AtomicInteger checks=new java.util.concurrent.atomic.AtomicInteger();assertThrows(java.util.concurrent.CancellationException.class,()->tv.guidePresence(Arrays.asList(Json.obj("Id","a"),Json.obj("Id","b")),()->checks.incrementAndGet()==1));assertEquals(1,server.getRequestCount());
    }
}
