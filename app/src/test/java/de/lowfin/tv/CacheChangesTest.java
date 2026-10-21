package de.lowfin.tv;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class CacheChangesTest {
    private Library library;
    @Before public void setup() { library=new Library(RuntimeEnvironment.getApplication(),"delta-"+UUID.randomUUID()); }
    @After public void close() { library.close(); }
    private JSONObject movie(String tag) { return Json.obj("Id","movie","Type","Movie","Name","Movie","ImageTags",Json.obj("Primary",tag),"Genres",Json.arr("Horror")); }
    private JSONObject change(JSONObject item,String... roots) { return Json.obj("item",item,"libraries",new JSONArray(Arrays.asList(roots))); }
    private JSONObject batch(String cursor,JSONObject... changes) { return Json.obj("cursor",cursor,"reset",false,"changes",new JSONArray(Arrays.asList(changes))); }
    @Test public void committedPluginDeliveryShowsNoticeButEmptyPollDoesNot() throws Exception {
        LowfinApp app=(LowfinApp)RuntimeEnvironment.getApplication();
        try(okhttp3.mockwebserver.MockWebServer server=new okhttp3.mockwebserver.MockWebServer();org.robolectric.android.controller.ActivityController<android.app.Activity> activity=Robolectric.buildActivity(android.app.Activity.class).setup()) {
            final int[] polls={0};server.setDispatcher(new okhttp3.mockwebserver.Dispatcher(){public okhttp3.mockwebserver.MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                if(request.getRequestUrl().encodedPath().endsWith("/capabilities")) return new okhttp3.mockwebserver.MockResponse().setBody(Json.obj("available",true,"apiVersion",1,"features",Json.arr("incrementalCache")).toString());
                return new okhttp3.mockwebserver.MockResponse().setBody((polls[0]++==0?batch("after",change(Json.obj("Id","notice","Type","Movie","Name","New title"),"root")):batch("after")).toString());
            }});server.start();library.put(Json.obj("Id","seed","Type","Movie"));library.state("lastSync","1");library.state("cacheCursor","before");
            CompanionApi companion=new CompanionApi(app,new Api(new Account(server.url("/").toString(),"user","token","User","device")),library);
            app.library=library;app.companion=companion;app.settings.edit().putBoolean("autoPrecache",true).putBoolean("companionUpdateToast",true).apply();
            CacheChanges changes=new CacheChanges(app,companion,null);
            try {
                org.robolectric.util.ReflectionHelpers.setField(changes,"active",true);org.robolectric.util.ReflectionHelpers.setField(changes,"notBefore",0L);
                java.lang.reflect.Method tick=CacheChanges.class.getDeclaredMethod("tick");tick.setAccessible(true);
                tick.invoke(changes);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500));
                assertEquals(I18n.quantity(R.plurals.companion_changes_received,1,1),org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
                int shown=org.robolectric.shadows.ShadowToast.shownToastCount();tick.invoke(changes);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1500));assertEquals(shown,org.robolectric.shadows.ShadowToast.shownToastCount());
            } finally {changes.close();}
        }
    }
    @Test public void MetadataMembershipAndCursorCommitTogetherAndSurviveRestart() throws Exception {
        library.applyCacheChanges(batch("cursor-1",change(movie("old"),"a"))); assertEquals(1,library.browse("Movie","a",null,0,20).size()); assertEquals("cursor-1",library.state("cacheCursor"));
        library.applyCacheChanges(batch("cursor-2",change(movie("new"),"b"))); assertTrue(library.browse("Movie","a",null,0,20).isEmpty()); assertEquals(1,library.browse("Movie","b",null,0,20).size()); assertEquals("new",Json.object(library.item("movie"),"ImageTags").getString("Primary")); assertEquals(1,library.artworkPending(10).size());
    }
    @Test public void PluginAuditIsBoundedAndUnaffectedByNormalReadsAndEmptyPolls() throws Exception {
        for(int i=0;i<55;i++) library.applyCacheChanges(batch("cursor-"+i,change(Json.obj("Id","episode-"+i,"Type","Episode","Name","Episode "+i,"SeriesName","Example series","ParentIndexNumber",2,"IndexNumber",i),"a")));
        JSONArray audit=new JSONArray(library.state("cacheChangeAudit")); assertEquals(50,audit.length());
        assertEquals("episode-54",audit.getJSONObject(0).getString("id")); assertEquals("Example series",audit.getJSONObject(0).getString("series")); assertEquals("companion",audit.getJSONObject(0).getString("source")); assertTrue(audit.getJSONObject(0).getLong("receivedAt")>0);
        String saved=audit.toString(); library.put(movie("normal-navigation")); library.applyCacheChanges(batch("empty-poll")); assertEquals(saved,library.state("cacheChangeAudit"));
        assertThrows(JSONException.class,()->library.applyCacheChanges(batch("invalid",change(Json.obj("Id","bad"),"a")))); assertEquals(saved,library.state("cacheChangeAudit"));
    }
    @Test public void PersistedHomeAndRecommendationSnapshotsReceiveMetadataChanges() throws Exception {
        library.state("home-0",Json.arr(movie("old")).toString()); library.state("homeRecommendations",Json.arr(Json.obj("Items",Json.arr(movie("old")))).toString());
        library.applyCacheChanges(batch("new",change(movie("new"),"a")));
        assertEquals("new",new JSONArray(library.state("home-0")).getJSONObject(0).getJSONObject("ImageTags").getString("Primary"));
        assertEquals("new",new JSONArray(library.state("homeRecommendations")).getJSONObject(0).getJSONArray("Items").getJSONObject(0).getJSONObject("ImageTags").getString("Primary"));
    }
    @Test public void MalformedBatchNeverAdvancesCursorOrPartiallyWritesItems() throws Exception {
        library.applyCacheChanges(batch("before")); JSONObject broken=Json.obj("item",Json.obj("Id","invalid"),"libraries",new JSONArray());
        assertThrows(JSONException.class,()->library.applyCacheChanges(batch("after",change(movie("new"),"a"),broken))); assertEquals("before",library.state("cacheCursor")); assertNull(library.item("movie"));
    }
    @Test public void UnchangedArtworkDoesNotRequeueAndFailuresBackOff() throws Exception {
        library.applyCacheChanges(batch("1",change(movie("old"),"a"))); library.artworkFinished("movie",true);
        library.applyCacheChanges(batch("2",change(movie("old"),"a"))); assertTrue(library.artworkPending(10).isEmpty());
        library.applyCacheChanges(batch("3",change(movie("new"),"a"))); library.artworkFinished("movie",false); assertTrue(library.artworkPending(10).isEmpty());
        library.applyCacheChanges(batch("4",change(movie("newer"),"a"))); assertEquals(Collections.singletonList("movie"),library.artworkPending(10));
    }
    @Test public void MissingImageTagsRemoveStaleReferences() throws Exception {
        library.put(movie("old")); library.applyCacheChanges(batch("1",change(Json.obj("Id","movie","Type","Movie","Name","Movie"),"a"))); assertEquals(0,Json.object(library.item("movie"),"ImageTags").length());
    }
    @Test public void CompleteChannelListRemovesOnlyObsoleteChannelsAndPreservesFilms() {
        library.put(movie("old")); library.put(Json.obj("Id","stale-channel","Type","TvChannel","Name","Old")); library.replaceChannels(Json.arr(Json.obj("Id","new-channel","Type","TvChannel","Name","Current"))); assertNull(library.item("stale-channel")); assertNotNull(library.item("movie")); assertNotNull(library.item("new-channel"));
    }
    @Test public void InterruptedArtworkPassRetainsMetadataBaselineAndRemainingWork() throws Exception {
        library.put(movie("old")); library.checkpointSync(0,"baseline",false);
        assertEquals("baseline",library.state("cacheCursor")); assertEquals("0",library.state("lastSync"));
        assertEquals(Collections.singletonList("movie"),library.artworkPending(10));
        library.artworkFinished("movie",true); assertTrue(library.artworkPending(10).isEmpty());
        library.applyCacheChanges(batch("new",change(movie("new"),"a")));
        assertEquals(Collections.singletonList("movie"),library.artworkPending(10));
    }
    @Test public void ResetResponseMustNotBeAppliedAsAnEmptySuccessfulDelta() { assertThrows(JSONException.class,()->library.applyCacheChanges(Json.obj("cursor","reset","reset",true,"changes",new JSONArray()))); assertEquals("",library.state("cacheCursor")); }
    @Test public void ReattachingAfterMissingOrExpiredCursorPreservesExistingCache() throws Exception {
        library.put(movie("old")); library.checkpointSync(0,"old",false);
        library.artworkFinished("movie",true);
        library.subscribeCacheChanges(Json.obj("cursor","fresh","reset",true,"changes",new JSONArray()));
        assertEquals("old",library.item("movie").getJSONObject("ImageTags").getString("Primary"));
        assertEquals("0",library.state("lastSync")); assertEquals("fresh",library.state("cacheCursor"));
        assertEquals(0,library.artworkCount()); assertEquals("true",library.state("cacheNeedsReview"));
        assertFalse(CacheChanges.needsBaseline(library));
    }
    @Test public void EmptyCacheStillNeedsInitialBuildAndBadSubscriptionPreservesCursor() throws Exception {
        assertTrue(CacheChanges.needsBaseline(library));
        library.state("cacheCursor","before");
        assertThrows(JSONException.class,()->library.subscribeCacheChanges(Json.obj("cursor","","changes",new JSONArray())));
        assertEquals("before",library.state("cacheCursor"));
    }
    @Test public void RecognizedPluginResetDoesNotStartFullService() throws Exception { runCacheTick(true,false); }
    @Test public void MissingPluginRetainsNativePeriodicFullRefresh() throws Exception { runCacheTick(false,false); }
    @Test public void RecognizedPluginMissingCursorDoesNotRebuildExistingLibrary() throws Exception { runCacheTick(true,true); }
    private void runCacheTick(boolean installed,boolean missing) throws Exception {
        okhttp3.mockwebserver.MockWebServer server=new okhttp3.mockwebserver.MockWebServer();
        server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(installed?200:404).setBody(Json.obj("available",true,"apiVersion",1,"features",Json.arr("incrementalCache")).toString()));
        if(!installed) server.enqueue(new okhttp3.mockwebserver.MockResponse().setResponseCode(403));
        if(installed) server.enqueue(new okhttp3.mockwebserver.MockResponse().setBody(Json.obj("cursor","new-head","reset",true,"changes",new JSONArray()).toString()));
        server.start();
        LowfinApp app=(LowfinApp)RuntimeEnvironment.getApplication();
        library.put(movie("old")); library.checkpointSync(0,"old-head",false); library.artworkFinished("movie",true); if(missing) library.state("cacheCursor","");
        Account account=new Account(server.url("/").toString(),"user","test-token","Test","tv-test");
        CompanionApi companion=new CompanionApi(app,new Api(account),library);
        app.library=library; app.companion=companion; app.settings.edit().putBoolean("autoPrecache",true).apply(); app.playing=true;
        CacheChanges changes=new CacheChanges(app,companion,null);
        try {
            java.lang.reflect.Field active=CacheChanges.class.getDeclaredField("active"); active.setAccessible(true); active.setBoolean(changes,true);
            java.lang.reflect.Field delay=CacheChanges.class.getDeclaredField("notBefore"); delay.setAccessible(true); delay.setLong(changes,0);
            java.lang.reflect.Method tick=CacheChanges.class.getDeclaredMethod("tick"); tick.setAccessible(true); tick.invoke(changes);
            android.content.Intent service=Shadows.shadowOf(app).getNextStartedService();
            if(installed) { assertNull(service); assertEquals("new-head",library.state("cacheCursor")); assertEquals(0,library.artworkCount()); assertEquals("0",library.state("lastSync")); }
            else { assertNotNull(service); assertEquals(SyncService.class.getName(),service.getComponent().getClassName()); assertEquals("old-head",library.state("cacheCursor")); }
        } finally { changes.close(); server.shutdown(); app.playing=false; }
    }
}
