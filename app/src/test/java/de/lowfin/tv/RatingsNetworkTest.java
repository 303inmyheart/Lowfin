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
    @Test public void peekUsesOnlyWarmMemoryAndRespectsCacheSwitch() throws Exception {
        assertNull(ratings.peek(movie()));assertEquals(0,calls.get());
        AtomicInteger done=new AtomicInteger();ratings.request(movie(),scores->done.incrementAndGet());waitFor(done,1);int fetched=calls.get();
        assertEquals(7.8,ratings.peek(movie()).optDouble("imdb"),.001);assertEquals(fetched,calls.get());
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("ratingCache",false).commit();assertNull(ratings.peek(movie()));assertEquals(fetched,calls.get());
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
    @Test public void cacheOffBypassesMemoryAndDiskWithoutDeletingOldValues() throws Exception {
        AtomicInteger done=new AtomicInteger(); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,1); String saved=library.state("ratings-values-movie"); assertFalse(saved.isEmpty()); assertEquals(3,calls.get());
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("ratingCache",false).commit();
        ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,2); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,3); assertEquals(9,calls.get()); assertEquals(saved,library.state("ratings-values-movie"));
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("ratingCache",true).commit(); ratings.close(); ratings=create(); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,4); assertEquals(9,calls.get());
    }
    @Test public void cacheOffDoesNotWriteRatingsForPreviouslyUnknownMedia() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("ratingCache",false).commit(); AtomicInteger done=new AtomicInteger(); ratings.request(movie(),scores->done.incrementAndGet()); waitFor(done,1); assertEquals("",library.state("ratings-values-movie"));
        try(android.database.Cursor cursor=library.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM state WHERE key LIKE 'ratings-%'",null)) { cursor.moveToFirst(); assertEquals(0,cursor.getInt(0)); }
    }
    @Test public void EpisodeWithoutOwnIdUsesFreshSeriesCoordinatesIncludingSpecialSeasonZero() throws Exception {
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            calls.incrementAndGet();String path=request.getRequestUrl().encodedPath();
            if(path.equals("/Users/user/Items/episode")) return new MockResponse().setBody(Json.obj("Id","episode","Type","Episode","ProviderIds",new JSONObject(),"SeriesId","series","ParentIndexNumber",0,"IndexNumber",3).toString());
            if(path.equals("/Users/user/Items/series")) return new MockResponse().setBody(Json.obj("ProviderIds",Json.obj("Imdb","tt100","Tmdb","200")).toString());
            if(path.equals("/omdb/")) {assertEquals("tt100",request.getRequestUrl().queryParameter("i"));assertEquals("0",request.getRequestUrl().queryParameter("Season"));assertEquals("3",request.getRequestUrl().queryParameter("Episode"));return new MockResponse().setBody("{\"Type\":\"episode\",\"imdbRating\":\"7.5\"}");}
            if(path.equals("/tmdb/tv/200/season/0/episode/3")) return new MockResponse().setBody("{\"vote_count\":10,\"vote_average\":7.1}");return new MockResponse().setResponseCode(404);
        }});
        AtomicInteger done=new AtomicInteger();ratings.request(Json.obj("Id","episode","Type","Episode","ProviderIds",new JSONObject()),scores->{assertEquals(7.5,scores.optDouble("imdb"),.01);assertEquals(7.1,scores.optDouble("tmdb"),.01);done.incrementAndGet();});waitFor(done,1);assertEquals(4,calls.get());assertEquals("",library.state("ratings-values-episode"));
    }
    @Test public void EpisodeScoresBypassMemoryDiskAndProviderCachesAndRejectSeriesMatches() throws Exception {
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            calls.incrementAndGet();assertNull(request.getHeader("Authorization"));String path=request.getRequestUrl().encodedPath();
            if(path.equals("/omdb/")) { assertEquals("tt987",request.getRequestUrl().queryParameter("i"));assertEquals("episode",request.getRequestUrl().queryParameter("type"));return new MockResponse().setBody("{\"Type\":\"episode\",\"imdbRating\":\"8.6\"}"); }
            if(path.equals("/tmdb/find/tt987")) return new MockResponse().setBody("{\"tv_results\":[{\"vote_count\":5,\"vote_average\":9.9}],\"tv_episode_results\":[{\"vote_count\":10,\"vote_average\":8.1}]}");
            return new MockResponse().setResponseCode(404);
        }});
        JSONObject episode=Json.obj("Id","episode","Type","Episode","ProviderIds",Json.obj("Imdb","tt987","Tmdb","999"));AtomicInteger done=new AtomicInteger();
        library.state("ratings-values-episode",Json.obj("scores",Json.obj("imdb",1)).toString());String before=library.state("ratings-values-episode");
        for(int i=1;i<=2;i++) { ratings.request(episode,scores->{assertEquals(8.6,scores.optDouble("imdb"),.01);assertEquals(8.1,scores.optDouble("tmdb"),.01);assertEquals(2,scores.length());done.incrementAndGet();});waitFor(done,i);assertNull(ratings.peek(episode)); }
        assertEquals(4,calls.get());assertEquals(before,library.state("ratings-values-episode"));
        try(android.database.Cursor cursor=library.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM state WHERE key LIKE 'ratings-%' AND key != 'ratings-values-episode'",null)) {cursor.moveToFirst();assertEquals(0,cursor.getInt(0));}
    }
    @Test public void missingApiKeysNeverStartExternalRequests() throws Exception {
        ratings.close(); ratings=new Ratings(RuntimeEnvironment.getApplication().getSharedPreferences("settings",0),library,api,p->"",server.url("/omdb/").toString(),server.url("/mdb/").toString(),server.url("/tmdb/").toString());
        AtomicInteger done=new AtomicInteger(); ratings.request(movie(),scores->{ assertEquals(0,scores.length()); done.incrementAndGet(); }); waitFor(done,1); assertEquals(0,calls.get());
    }
}
