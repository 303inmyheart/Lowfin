package de.lowfin.tv;
import org.junit.*;
import org.json.*;
import okhttp3.mockwebserver.*;
import java.time.Instant;
import static org.junit.Assert.*;
public class LiveTvTest {
    private MockWebServer server;private Api api;
    @Before public void setup() throws Exception {server=new MockWebServer();server.start();api=new Api(new Account(server.url("/jellyfin").toString(),"user","token","name","device"));}
    @After public void stop() throws Exception {server.shutdown();}
    @Test public void availabilityIsUserScopedAndDoesNotDownloadAllChannels() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[{\"Id\":\"channel\"}]}"));assertTrue(new LiveTv(api).available());RecordedRequest r=server.takeRequest();assertEquals("1",r.getRequestUrl().queryParameter("Limit"));assertEquals("user",r.getRequestUrl().queryParameter("UserId"));assertEquals("false",r.getRequestUrl().queryParameter("EnableImages"));assertTrue(r.getHeader("Authorization").contains("token"));
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));assertFalse(new LiveTv(api).available());
    }
    @Test public void temporaryFailureIsNotTreatedAsNoConfiguration() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));assertThrows(Api.Failure.class,()->new LiveTv(api).available());
        server.enqueue(new MockResponse().setResponseCode(403));assertFalse(new LiveTv(api).available());server.enqueue(new MockResponse().setResponseCode(404));assertFalse(new LiveTv(api).available());
    }
    @Test public void malformedSuccessCannotEraseKnownAvailability() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));assertThrows(JSONException.class,()->new LiveTv(api).available());
    }
    @Test public void currentQueryIsBoundedToOneChannelAndCurrentTime() throws Exception {
        Instant now=Instant.now();JSONObject program=Json.obj("ChannelId","channel","Name","Now","StartDate",now.minusSeconds(60).toString(),"EndDate",now.plusSeconds(60).toString());server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(program)).toString()));assertEquals("Now",new LiveTv(api).current("channel").optString("Name"));RecordedRequest r=server.takeRequest();assertEquals("channel",r.getRequestUrl().queryParameter("ChannelIds"));assertEquals("1",r.getRequestUrl().queryParameter("Limit"));assertEquals(r.getRequestUrl().queryParameter("MinEndDate"),r.getRequestUrl().queryParameter("MaxStartDate"));assertEquals("Overview",r.getRequestUrl().queryParameter("Fields"));
    }
    @Test public void expiredFutureOrWrongChannelEntriesCannotAppearAsCurrent() {
        Instant now=Instant.parse("2026-10-21T12:00:00Z");JSONObject ended=Json.obj("ChannelId","a","StartDate","2026-10-21T11:00:00Z","EndDate",now.toString()),future=Json.obj("ChannelId","a","StartDate","2026-10-21T13:00:00Z","EndDate","2026-10-21T14:00:00Z"),other=Json.obj("ChannelId","b","StartDate","2026-10-21T11:00:00Z","EndDate","2026-10-21T14:00:00Z");assertNull(LiveTv.select(Json.arr(ended,future,other),"a",now));assertNull(LiveTv.select(Json.arr(Json.obj("ChannelId","a","StartDate","invalid")),"a",now));
    }
    @Test public void scheduleLoadsBoundedFuturePagesForOnlyTheSelectedChannel() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));Instant anchor=Instant.parse("2026-10-21T12:00:00Z");new LiveTv(api).schedule("channel",2,anchor);RecordedRequest r=server.takeRequest();assertEquals("channel",r.getRequestUrl().queryParameter("ChannelIds"));assertEquals("user",r.getRequestUrl().queryParameter("UserId"));assertEquals("24",r.getRequestUrl().queryParameter("StartIndex"));assertEquals("13",r.getRequestUrl().queryParameter("Limit"));assertEquals(anchor.toString(),r.getRequestUrl().queryParameter("MinEndDate"));assertNull(r.getRequestUrl().queryParameter("MaxStartDate"));assertEquals("Ascending",r.getRequestUrl().queryParameter("SortOrder"));
    }
    @Test public void scheduleIncludesLaterProgrammesButRejectsWrongOrInvalidRowsAndLookahead() {
        Instant anchor=Instant.parse("2026-10-21T12:00:00Z");JSONArray items=new JSONArray();for(int i=0;i<13;i++)items.put(Json.obj("ChannelId","a","Name","Programme "+i,"StartDate",anchor.plusSeconds(i*60-30).toString(),"EndDate",anchor.plusSeconds(i*60+30).toString()));
        java.util.List<JSONObject> page=LiveTv.scheduleItems(items,"a",anchor);assertEquals(12,page.size());assertEquals("Programme 11",page.get(11).optString("Name"));assertNull(LiveTv.select(Json.arr(page.get(1)),"a",anchor));
        items=Json.arr(Json.obj("ChannelId","b","StartDate",anchor.minusSeconds(30).toString(),"EndDate",anchor.plusSeconds(30).toString()),Json.obj("ChannelId","a","StartDate","invalid","EndDate",anchor.plusSeconds(30).toString()),Json.obj("ChannelId","a","StartDate",anchor.toString(),"EndDate",anchor.toString()));assertTrue(LiveTv.scheduleItems(items,"a",anchor).isEmpty());
    }
}
