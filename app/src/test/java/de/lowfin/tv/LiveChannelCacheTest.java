package de.lowfin.tv;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class LiveChannelCacheTest {
    @Test public void ReconciliationWaitsForAllPagesAndFailureKeepsOldChannels() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); Api api=new Api(new Account(server.url("/").toString(),"user","token","name","device"));
            server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","first","Type","TvChannel")),"TotalRecordCount",2).toString()));
            server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","second","Type","TvChannel")),"TotalRecordCount",2).toString()));
            JSONArray all=api.liveChannels(); assertEquals(2,all.length()); server.takeRequest(); assertEquals("1",server.takeRequest().getRequestUrl().queryParameter("StartIndex"));
            Library db=new Library(RuntimeEnvironment.getApplication(),"live-paging-test"); db.put(Json.obj("Id","old","Type","TvChannel"));
            server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","first","Type","TvChannel")),"TotalRecordCount",2).toString())); server.enqueue(new MockResponse().setResponseCode(503));
            assertThrows(Api.Failure.class,()->db.replaceChannels(api.liveChannels())); assertNotNull(db.item("old")); db.close();
        }
    }
    @Test public void EmptyPartialPageDoesNotPretendToBeACompleteList() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); Api api=new Api(new Account(server.url("/").toString(),"user","token","name","device")); server.enqueue(new MockResponse().setBody(Json.obj("Items",new JSONArray(),"TotalRecordCount",4).toString())); assertThrows(java.io.IOException.class,api::liveChannels); }
    }
}
