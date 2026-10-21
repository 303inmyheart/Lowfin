package de.lowfin.tv;

import android.content.SharedPreferences;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class TracearrTest {
    private MockWebServer server;
    private Tracearr stats;
    private SharedPreferences settings;
    private final String sid="00000000-0000-0000-0000-000000000003",uid="00000000-0000-0000-0000-000000000002",external="00000000000000000000000000000001";
    private JSONArray history=new JSONArray();
    private String next="";
    @Before public void setup() throws Exception {
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() {
            public MockResponse dispatch(RecordedRequest request) {
                String path=request.getRequestUrl().encodedPath(); JSONObject response;
                if(path.endsWith("/servers")) response=Json.obj("data",Json.arr(Json.obj("server_id",sid,"server_name","Jellyfin","server_type","jellyfin")));
                else if(path.endsWith("/users")) response=Json.obj("data",Json.arr(Json.obj("id",uid,"accounts",Json.arr(Json.obj("server_id",sid,"server_type","jellyfin","external_user_id","00000000-0000-0000-0000-000000000001")))),"meta",Json.obj("nextCursor",JSONObject.NULL));
                else if(path.endsWith("/history")) response=Json.obj("data",history,"meta",Json.obj("nextCursor",next.isEmpty()?JSONObject.NULL:next));
                else if(path.equals("/Shows/show/Episodes")) response=Json.obj("Items",Json.arr(Json.obj("Id","e1"),Json.obj("Id","e2")));
                else return new MockResponse().setResponseCode(404);
                return new MockResponse().setBody(response.toString());
            }
        }); server.start();
        Account account=new Account(server.url("/").toString(),external,"jellyfin-token","User","device");
        settings=RuntimeEnvironment.getApplication().getSharedPreferences("tracearr-test",0); settings.edit().clear().commit();
        stats=new Tracearr(new Api(account),settings,name->name.equals("token")?"trr_pub_test":"");
        settings.edit().putString(stats.key("url"),server.url("/").toString()).putString(stats.key("server"),sid).apply();
    }
    @After public void teardown() throws Exception { stats.http.dispatcher().cancelAll(); stats.http.connectionPool().evictAll(); server.shutdown(); }
    private JSONObject play(String id,String item,boolean watched) { return Json.obj("id",id,"rating_key",item,"watched",watched,"server_id",sid,"stopped_at","2026-10-13T12:00:00Z"); }
    @Test public void onlyStoppedWatchedUniqueChainsCountAndQueriesUseCorrectIdentity() throws Exception {
        history=Json.arr(play("a","movie",true),play("a","movie",true),play("b","movie",false),play("c","other",true),play("d","movie",true).put("stopped_at",JSONObject.NULL),play("e","movie",true).put("server_id","other"));
        assertEquals(1,stats.summary(Json.obj("Id","movie","Type","Movie")).optInt("count"));
        RecordedRequest users=server.takeRequest(1,TimeUnit.SECONDS),request=server.takeRequest(1,TimeUnit.SECONDS);
        assertEquals("/api/v2/public/users",users.getRequestUrl().encodedPath());
        assertEquals("Bearer trr_pub_test",request.getHeader("Authorization"));
        assertEquals(uid,request.getRequestUrl().queryParameter("user_id")); assertEquals(sid,request.getRequestUrl().queryParameter("server_id"));
        assertEquals("movie",request.getRequestUrl().queryParameter("rating_key")); assertEquals("true",request.getRequestUrl().queryParameter("watched"));
    }
    @Test public void seriesCountsCompleteSetsIncludingUnplayedEpisodes() throws Exception {
        history=Json.arr(play("a","e1",true),play("b","e1",true)); assertEquals(0,stats.summary(Json.obj("Id","show","Type","Series")).optInt("count"));
        stats=new Tracearr(stats.jellyfin,settings,name->name.equals("token")?"trr_pub_test":"");
        history.put(play("c","e2",true)); assertEquals(1,stats.summary(Json.obj("Id","show","Type","Series")).optInt("count"));
    }
    @Test public void paginationDuplicatesNeverProducePartialCounts() throws Exception {
        next="same"; history=Json.arr(play("a","movie",true));
        try { stats.summary(Json.obj("Id","movie","Type","Movie")); fail("Incomplete history must not be shown"); } catch(IOException expected) { }
        next=""; history=new JSONArray(); assertEquals(0,stats.summary(Json.obj("Id","movie","Type","Movie")).optInt("count"));
    }
    @Test public void changedServerDoesNotReuseAnotherServersCachedCount() throws Exception {
        history=Json.arr(play("a","movie",true)); assertEquals(1,stats.summary(Json.obj("Id","movie","Type","Movie")).optInt("count"));
        settings.edit().putString(stats.key("server"),"other").apply();
        try { stats.summary(Json.obj("Id","movie","Type","Movie")); fail("Must remap the account"); } catch(IOException expected) { }
    }
    @Test public void urlsRejectEmbeddedCredentialsAndQueries() {
        for(String url:new String[]{"file:///tmp/key","https://user:key@example.com","https://example.com?token=key","https://example.com#key"}) try { Tracearr.validUrl(url); fail(url); } catch(IllegalArgumentException expected) { }
        assertEquals("https://example.com/tracearr",Tracearr.validUrl("https://example.com/tracearr/"));
    }
}
