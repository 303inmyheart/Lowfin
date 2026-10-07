package de.lowfin.tv;

import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class HomeRecommendationsTest {
    private MockWebServer server;
    private Api api;
    @Before public void setup() throws Exception {
        server=new MockWebServer(); server.start();
        api=new Api(new Account(server.url("/jellyfin").toString(),"user","secret","Rico","device"));
    }
    @After public void stop() throws Exception { server.shutdown(); }
    private JSONObject movie(String id) { return Json.obj("Id",id,"Type","Movie","Name",id); }
    private JSONObject bucket(String name,String type,Object... items) { return Json.obj("BaselineItemName",name,"RecommendationType",type,"Items",Json.arr(items)); }
    @Test public void choosesDistinctWatchedBaselinesAndPrefersDirectSimilarCategories() {
        List<JSONObject> rows=HomeRecommendations.select(Json.arr(
            bucket("A","HasActorFromRecentlyPlayed",movie("actor")),
            bucket("Favorite","SimilarToLikedItem",movie("liked")),
            bucket("A","SimilarToRecentlyPlayed",movie("a")),
            bucket("A","HasDirectorFromRecentlyPlayed",movie("duplicate")),
            bucket("B","SimilarToRecentlyPlayed",movie("b")),
            bucket("C","SimilarToRecentlyPlayed",movie("c"))));
        assertEquals(3,rows.size()); assertEquals("A",rows.get(0).optString("BaselineItemName")); assertEquals("a",Json.array(rows.get(0),"Items").optJSONObject(0).optString("Id"));
        assertEquals("B",rows.get(1).optString("BaselineItemName"));
    }
    @Test public void ignoresEmptyInvalidAndNonMovieBucketsAndDeduplicatesCards() {
        List<JSONObject> rows=HomeRecommendations.select(Json.arr(
            bucket("","SimilarToRecentlyPlayed",movie("x")),bucket("No items","SimilarToRecentlyPlayed"),
            bucket("Series","SimilarToRecentlyPlayed",Json.obj("Id","show","Type","Series")),
            bucket("Film","SimilarToRecentlyPlayed",movie("a"),movie("a"),movie("")),
            bucket(" film ","HasActorFromRecentlyPlayed",movie("duplicate-baseline")),
            bucket("Other","SimilarToRecentlyPlayed",movie("b"))));
        assertEquals(2,rows.size()); assertEquals(1,Json.array(rows.get(0),"Items").length());
        assertEquals("Weil du „Film“ gesehen hast",HomeRecommendations.title(rows.get(0)));
    }
    @Test public void excludesActorAndDirectorBaselinesEvenWhenTheirItemsAreMovies() {
        assertTrue(HomeRecommendations.select(Json.arr(
            bucket("Sigourney Weaver","HasActorFromRecentlyPlayed",movie("film")),
            bucket("Ridley Scott","HasDirectorFromRecentlyPlayed",movie("film")),
            bucket("Someone","HasLikedActor",movie("film")),
            bucket("Director","HasLikedDirector",movie("film")))).isEmpty());
    }
    @Test public void readsJellyfinArrayWithAccountAndMetadataAndNeedsNoFallbackForFiveRows() throws Exception {
        server.enqueue(new MockResponse().setBody(Json.arr(bucket("A","SimilarToRecentlyPlayed",movie("a")),bucket("B","SimilarToRecentlyPlayed",movie("b")),bucket("C","SimilarToRecentlyPlayed",movie("c")),bucket("D","SimilarToRecentlyPlayed",movie("d")),bucket("E","SimilarToRecentlyPlayed",movie("e"))).toString()));
        assertEquals(5,HomeRecommendations.load(api).size()); RecordedRequest request=server.takeRequest();
        assertEquals("/jellyfin/Movies/Recommendations",request.getRequestUrl().encodedPath()); assertEquals("user",request.getRequestUrl().queryParameter("UserId"));
        assertEquals("8",request.getRequestUrl().queryParameter("CategoryLimit")); assertEquals("24",request.getRequestUrl().queryParameter("ItemLimit"));
        assertTrue(request.getRequestUrl().queryParameter("Fields").contains("Overview")); assertTrue(request.getHeader("Authorization").contains("secret")); assertEquals(1,server.getRequestCount());
    }
    @Test public void fillsMissingRowUsingJellyfinSimilarItemsForAnotherPlayedFilm() throws Exception {
        server.enqueue(new MockResponse().setBody(Json.arr(bucket("A","SimilarToRecentlyPlayed",movie("a"))).toString()));
        server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(
            Json.obj("Id","a","Type","Movie","Name","A","UserData",Json.obj("Played",true)),
            Json.obj("Id","unwatched","Type","Movie","Name","Not watched","UserData",Json.obj("Played",false)),
            Json.obj("Id","b","Type","Movie","Name","B","UserData",Json.obj("Played",true)))).toString()));
        server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(movie("b"),movie("suggestion"))).toString()));
        List<JSONObject> rows=HomeRecommendations.load(api); assertEquals(2,rows.size()); assertEquals("B",rows.get(1).optString("BaselineItemName"));
        assertEquals("suggestion",Json.array(rows.get(1),"Items").optJSONObject(0).optString("Id"));
        server.takeRequest(); RecordedRequest history=server.takeRequest(); assertEquals("true",history.getRequestUrl().queryParameter("IsPlayed")); assertEquals("DatePlayed",history.getRequestUrl().queryParameter("SortBy"));
        assertEquals("/jellyfin/Items/b/Similar",server.takeRequest().getRequestUrl().encodedPath());
    }
    @Test public void missingHistoryDoesNotFabricateWatchedRecommendations() throws Exception {
        server.enqueue(new MockResponse().setBody("[]")); server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));
        assertTrue(HomeRecommendations.load(api).isEmpty()); assertEquals(2,server.getRequestCount());
    }
    @Test public void failedRecommendationRequestCannotReplaceCacheWithAnEmptyFallback() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503)); server.enqueue(new MockResponse().setBody("{\"Items\":[]}"));
        try { HomeRecommendations.load(api); fail("Expected failure so cached rows survive"); } catch(Api.Failure e) { assertEquals(503,e.status); }
    }
    @Test public void failureDoesNotEraseAValidFirstRecommendation() throws Exception {
        server.enqueue(new MockResponse().setBody(Json.arr(bucket("A","SimilarToRecentlyPlayed",movie("a"))).toString()));
        server.enqueue(new MockResponse().setResponseCode(503));
        assertEquals(1,HomeRecommendations.load(api).size());
    }
}
