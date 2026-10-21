package de.lowfin.tv;
import android.os.Looper;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class ServerProvidersTest {
    private MockWebServer server; private LowfinApp app; private Library library; private Ratings ratings; private CompanionApi companion; private Api api; private AtomicInteger requests=new AtomicInteger();
    @Before public void setup() throws Exception {
        app=(LowfinApp)RuntimeEnvironment.getApplication(); server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            String path=request.getRequestUrl().encodedPath();
            if(path.endsWith("/capabilities")) return new MockResponse().setBody(Json.obj("available",true,"apiVersion",1,"features",Json.arr("serverProviders")).toString());
            if(path.endsWith("/ratings")) { requests.incrementAndGet(); return new MockResponse().setBody(Json.obj("scores",Json.obj("imdb",8.5),"errors",new JSONArray()).toString()); }
            if(path.endsWith("/statistics")) return new MockResponse().setBody(Json.obj("count",3,"series",false).toString());
            return new MockResponse().setResponseCode(404);
        } }); server.start(); api=new Api(new Account(server.url("/").toString(),"user","session-token","name","device")); library=new Library(app,"server-provider-test"); companion=new CompanionApi(app,api,library); app.companion=companion; assertTrue(companion.refreshDiscovery()); ratings=new Ratings(app,library,api);
    }
    @After public void close() throws Exception { ratings.close(); library.close(); app.companion=null; server.shutdown(); }
    private JSONObject movie() { return Json.obj("Id","movie","Type","Movie"); }
    private JSONObject requestRating() throws Exception { AtomicReference<JSONObject> result=new AtomicReference<>(); ratings.request(movie(),result::set); long end=System.currentTimeMillis()+5000; while(result.get()==null&&System.currentTimeMillis()<end) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10); } assertNotNull(result.get()); return result.get(); }
    @Test public void ServerRatingsStayCachedLocallyByDefaultAndToggleBypassesBothCaches() throws Exception {
        assertEquals(8.5,requestRating().optDouble("imdb"),.01); assertEquals(1,requests.get()); assertFalse(library.state("ratings-values-movie").isEmpty()); requestRating(); assertEquals(1,requests.get());
        app.settings.edit().putBoolean("ratingCache",false).commit(); requestRating(); requestRating(); assertEquals(3,requests.get());
    }
    @Test public void ServerEpisodeScoresAreAlwaysFreshAndNeverWrittenToTheRatingCaches() throws Exception {
        JSONObject episode=Json.obj("Id","episode","Type","Episode");
        for(int i=0;i<2;i++) {AtomicReference<JSONObject> result=new AtomicReference<>();ratings.request(episode,result::set);long end=System.currentTimeMillis()+5000;while(result.get()==null&&System.currentTimeMillis()<end) {Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}assertNotNull(result.get());assertEquals(8.5,result.get().optDouble("imdb"),.01);assertNull(ratings.peek(episode));}
        assertEquals(2,requests.get());assertEquals("",library.state("ratings-values-episode"));try(android.database.Cursor c=library.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM state WHERE key LIKE '%ratings-%'",null)){c.moveToFirst();assertEquals(0,c.getInt(0));}
    }
    @Test public void ProviderChoicesReachServerAndTracearrNeverReadsClientSecrets() throws Exception {
        app.settings.edit().putBoolean("rating-provider-mdblist",false).commit(); requestRating(); server.takeRequest(); RecordedRequest request=server.takeRequest(); assertEquals("omdb,tmdb",request.getRequestUrl().queryParameter("sources")); assertFalse(request.getRequestUrl().queryParameterNames().contains("apikey"));
        Tracearr stats=new Tracearr(api,app.settings,name->{ throw new AssertionError("Client secret used for server statistics"); }); stats.companion=()->companion; assertEquals(3,stats.summary(movie()).getInt("count"));
    }
}
