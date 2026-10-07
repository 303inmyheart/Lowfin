package de.lowfin.tv;

import android.os.Looper;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class RatingsNetworkTest {
    private MockWebServer server;
    private Ratings ratings;
    private Library library;
    private Api api;
    private AtomicInteger calls;
    @Before public void setup() throws Exception {
        server=new MockWebServer(); calls=new AtomicInteger(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            calls.incrementAndGet(); if(request.getHeader("Authorization")!=null&&request.getHeader("Authorization").contains("jellyfin-secret")) return new MockResponse().setResponseCode(403);
            String path=request.getRequestUrl().encodedPath();
            if(path.equals("/omdb/")) return new MockResponse().setBody("{\"imdbRating\":\"7.8\",\"Metascore\":\"82\",\"Ratings\":[{\"Source\":\"Rotten Tomatoes\",\"Value\":\"91%\"}]}");
            if(path.equals("/mdb/imdb/movie/tt12345/")) return new MockResponse().setBody("{\"score\":87,\"ratings\":[{\"source\":\"trakt\",\"value\":88}]}");
            if(path.equals("/tmdb/movie/123")) return new MockResponse().setBody("{\"vote_average\":8.2,\"vote_count\":500}");
            return new MockResponse().setResponseCode(404);
        } }); server.start(); android.content.Context c=RuntimeEnvironment.getApplication();
        library=new Library(c,"ratings-test"); api=new Api(new Account(server.url("/").toString(),"user","jellyfin-secret","Rico","device")); ratings=create();
    }
    private Ratings create() { return new Ratings(RuntimeEnvironment.getApplication().getSharedPreferences("settings",0),library,api,p->p+"-key",server.url("/omdb/").toString(),server.url("/mdb/").toString(),server.url("/tmdb/").toString()); }
    @After public void close() throws Exception { ratings.close(); library.close(); server.shutdown(); }
    private JSONObject movie() { return Json.obj("Id","movie","Type","Movie","ProviderIds",Json.obj("Imdb","tt12345","Tmdb","123")); }
    private void waitFor(AtomicInteger callbacks,int expected) throws Exception {
        long end=System.currentTimeMillis()+5000; while(callbacks.get()<expected&&System.currentTimeMillis()<end) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10); } assertEquals(expected,callbacks.get());
    }
    @Test public void allThreeProvidersUseDocumentedIdsAndNeverReceiveJellyfinCredentials() throws Exception {
        AtomicInteger done=new AtomicInteger(); AtomicReference<JSONObject> result=new AtomicReference<>();
        ratings.request(movie(),scores->{ result.set(scores); done.incrementAndGet(); }); waitFor(done,1);
        assertEquals(7.8,result.get().optDouble("imdb"),.001); assertEquals(8.2,result.get().optDouble("tmdb"),.001); assertEquals(88,result.get().optDouble("trakt"),.001);
        assertEquals(3,calls.get()); for(int i=0;i<3;i++) { RecordedRequest request=server.takeRequest(); assertNull(request.getHeader("Authorization")); assertTrue(request.getRequestUrl().queryParameterNames().contains(request.getRequestUrl().encodedPath().startsWith("/tmdb")?"api_key":"apikey")); }
    }
    @Test public void concurrentLookupsDeduplicateAndDiskCacheSurvivesRepositoryRecreation() throws Exception {
        AtomicInteger done=new AtomicInteger(); ratings.request(movie(),scores->done.incrementAndGet()); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,2); assertEquals(3,calls.get());
        ratings.close(); ratings=create(); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,3); assertEquals(3,calls.get());
    }
    @Test public void successfulRatingsSurviveExpiredLegacyCacheAcrossRestarts() throws Exception {
        AtomicInteger done=new AtomicInteger(); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,1);
        try(android.database.Cursor c=library.getReadableDatabase().rawQuery("SELECT key,value FROM state WHERE key LIKE 'ratings-%' AND key NOT LIKE 'ratings-values-%'",null)) {
            assertTrue(c.moveToFirst()); JSONObject entry=new JSONObject(c.getString(1)); entry.put("time",1); entry.put("ttl",604800000L); library.state(c.getString(0),entry.toString());
        }
        ratings.close(); ratings=create(); ratings.request(movie(),scores->{ assertEquals(7.8,scores.optDouble("imdb"),.001); done.incrementAndGet(); }); waitFor(done,2); assertEquals(3,calls.get());
    }
    @Test public void displaySelectionChangesReusePreviouslyFetchedProviderValues() throws Exception {
        AtomicInteger done=new AtomicInteger(); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,1); assertEquals(3,calls.get());
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("rating-show-imdb",false).commit();
        ratings.close(); ratings=create(); ratings.request(movie(),scores->{ assertEquals(8.2,scores.optDouble("tmdb"),.001); done.incrementAndGet(); }); waitFor(done,2); assertEquals(3,calls.get());
    }
    @Test public void missingApiKeysNeverStartExternalRequests() throws Exception {
        ratings.close(); ratings=new Ratings(RuntimeEnvironment.getApplication().getSharedPreferences("settings",0),library,api,p->"",server.url("/omdb/").toString(),server.url("/mdb/").toString(),server.url("/tmdb/").toString());
        AtomicInteger done=new AtomicInteger(); ratings.request(movie(),scores->{ assertEquals(0,scores.length()); done.incrementAndGet(); }); waitFor(done,1); assertEquals(0,calls.get());
    }
}
