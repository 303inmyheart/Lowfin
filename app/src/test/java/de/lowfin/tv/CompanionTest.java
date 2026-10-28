package de.lowfin.tv;

import android.content.Context;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class CompanionTest {
    private Context context;
    private MockWebServer server;
    private Account account;
    private Library library;
    private CompanionApi client;
    private volatile JSONObject backend;
    private volatile boolean installed=true,failProgress,failStartAfterApply,independent=true,legacyPlugin;
    private volatile String returnedScope;
    private int starts,nativeReports;
    private final long duration=432000000000L;
    @Before public void setup() throws Exception {
        context=RuntimeEnvironment.getApplication(); I18n.init(context);
        backend=state(1,-1,0,false);
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                try {
                    String path=request.getRequestUrl().encodedPath();
                    if(path.startsWith("/Sessions/Playing")) { nativeReports++; return new MockResponse().setResponseCode(204); }
                    if(path.equals("/Users/user/Items/movie")) return ok(movie());
                    if(!installed) return new MockResponse().setResponseCode(404);
                    if(path.equals(CompanionApi.ROOT+"/capabilities")) return ok(Json.obj("available",true,"apiVersion",1,"features",legacyPlugin?new JSONArray():Json.arr("scopedResume","resumeVisibility","explicitOptIn")));
                    if(path.equals(CompanionApi.ROOT+"/resume")) { JSONObject row=Json.obj("itemId","movie","state",backend); String scope=request.getRequestUrl().queryParameter("parentId"); return ok(Json.obj("items",independent&&backend.optBoolean("hasResume")?Json.arr(row):new JSONArray(),"states",Json.arr(row),"parentId",returnedScope==null?scope:returnedScope)); }
                    if(path.endsWith("/playback")) {
                        JSONObject frame=new JSONObject(request.getBody().clone().readUtf8()); long sequence=frame.optLong("sequence"); String event=frame.optString("event");
                        if(event.equals("Progress")&&failProgress) { failProgress=false; return new MockResponse().setResponseCode(503); }
                        boolean duplicate=sequence<=backend.optLong("sequence",-1);
                        if(!duplicate) {
                            JSONObject value=new JSONObject(backend.toString());
                            if(event.equals("Start")) { starts++; value.put("revision",value.optLong("revision")+1); value.put("leaseId",frame.optString("leaseId")); }
                            value.put("sequence",sequence);
                            if(!value.optBoolean("manualFence")) { long position=Json.object(frame,"playback").optLong("PositionTicks"); value.put("resumeTicks",position); value.put("hasResume",position>0&&position<duration-10000000); }
                            backend=value;
                        }
                        if(event.equals("Start")&&failStartAfterApply) { failStartAfterApply=false; return new MockResponse().setResponseCode(503); }
                        return ok(Json.obj("accepted",!duplicate,"duplicate",duplicate,"state",backend));
                    }
                    if(path.equals(CompanionApi.ROOT+"/items/movie")) return ok(backend);
                    return new MockResponse().setResponseCode(404);
                } catch(Exception exception) { return new MockResponse().setResponseCode(500); }
            }
        }); server.start();
        account=new Account(server.url("/").toString(),"user","test-token","Test","tv-test");
        library=new Library(context,account.scope()); client=new CompanionApi(context,new Api(account),library);
    }
    @Test public void emptyOutboxDoesNotProbeButNormalDiscoveryRemainsAvailable() {
        context.getSharedPreferences("companion-outbox-"+account.scope(),0).edit().putBoolean("available",true).apply();
        client.replay();assertEquals(0,server.getRequestCount());assertTrue(client.discover());assertEquals(1,server.getRequestCount());
    }
    @Test public void simultaneousStartupDiscoverySharesProbeAndExplicitRefreshStillReloads() throws Exception {
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Future<Boolean> session=workers.submit(()-> { start.await();return client.discover(); });
            java.util.concurrent.Future<Boolean> resume=workers.submit(()-> { start.await();return client.discover(); });
            start.countDown();
            assertTrue(session.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(resume.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,server.getRequestCount());
            installed=false;
            assertFalse(client.refreshDiscovery());
            assertEquals(2,server.getRequestCount());
            assertFalse(client.discover());
            assertEquals(2,server.getRequestCount());
        } finally { workers.shutdownNow(); }
    }
    @After public void cleanup() throws Exception { library.close(); server.shutdown(); LowfinApp app=(LowfinApp)context; app.io.shutdownNow(); app.cacheIo.shutdownNow(); app.navigationIo.shutdownNow(); app.reports.shutdownNow(); }
    private MockResponse ok(JSONObject value) { return new MockResponse().setHeader("Content-Type","application/json").setBody(value.toString()); }
    private JSONObject state(long revision,long sequence,long position,boolean played) { return Json.obj("revision",revision,"sequence",sequence,"resumeTicks",position,"runtimeTicks",duration,"hasResume",position>0,"played",played,"manualFence",false,"active",true,"resumeVisible",true,"effectiveRule",Json.obj("mode","RemainingTime","remainingSeconds",120,"percentage",99.5,"persistentResume",true)); }
    private JSONObject movie() { return Json.obj("Id","movie","Type","Movie","Name","Twelve hours","RunTimeTicks",duration,"UserData",Json.obj("Played",true,"PlaybackPositionTicks",0)); }
    private JSONObject progress(CompanionApi.Session session,long position) { return Json.obj("ItemId","movie","PlaySessionId",session.lease,"PositionTicks",position,"IsPaused",false); }

    @Test public void absentPluginKeepsNativePositionAndDoesNotCreateAManagedSession() throws Exception {
        installed=false;
        CompanionApi.Prepared prepared=client.prepare(movie(),345000,false,false);
        assertNull(prepared.session); assertEquals(345000,prepared.position); assertFalse(library.companionAvailable);
        assertEquals(1,server.getRequestCount());
    }
    @Test public void independentResumeRemainsVisibleForAWatchedMovieAndExplicitRestartStartsAtZero() throws Exception {
        backend=state(1,-1,396000000000L,true); library.put(movie());
        CompanionApi.Prepared prepared=client.prepare(movie(),0,false,false);
        assertEquals(39600000L,prepared.position); assertNotNull(prepared.session);
        List<JSONObject> resumed=library.resumable(null,null,18,0); assertEquals(1,resumed.size()); assertTrue(Json.played(resumed.get(0))); assertTrue(Json.canResume(resumed.get(0)));
        assertEquals(0,client.prepare(movie(),39600000L,true,false).position);
        assertEquals(1234,client.prepare(movie(),1234,false,true).position);
    }
    @Test public void offlineProgressOutboxSurvivesClientRecreationAndNeverUsesNativePlaybackRoutes() throws Exception {
        CompanionApi.Session session=client.prepare(movie(),0,false,false).session;
        client.report(session,"Start",progress(session,0)); failProgress=true;
        try { client.report(session,"Progress",progress(session,288000000000L)); fail("Expected temporary server error"); } catch(Api.Failure expected) { assertEquals(503,expected.status); }
        assertEquals(288000000000L,library.companionState("movie").optLong("resumeTicks"));
        new CompanionApi(context,new Api(account),library).replay();
        assertEquals(288000000000L,backend.optLong("resumeTicks")); assertEquals(1,starts); assertEquals(0,nativeReports);
        for(String key:context.getSharedPreferences("companion-outbox-"+account.scope(),0).getAll().keySet()) assertFalse(key.startsWith("lease-"));
    }
    @Test public void ambiguousStartRetryIsDeduplicatedBeforeSendingLatestProgress() throws Exception {
        CompanionApi.Session session=client.prepare(movie(),0,false,false).session; failStartAfterApply=true;
        try { client.report(session,"Start",progress(session,0)); fail("Expected ambiguous delivery"); } catch(Api.Failure expected) { assertEquals(503,expected.status); }
        client.report(session,"Progress",progress(session,396000000000L));
        assertEquals(1,starts); assertEquals(396000000000L,backend.optLong("resumeTicks")); assertEquals(0,nativeReports);
    }
    @Test public void newerManualRevisionWinsOverAnOlderSessionCheckpoint() throws Exception {
        CompanionApi.Session session=client.prepare(movie(),0,false,false).session; client.report(session,"Start",progress(session,0));
        backend=state(3,0,0,true); backend.put("manualFence",true); backend.put("leaseId",session.lease); library.companionState("movie",backend);
        client.report(session,"Progress",progress(session,396000000000L));
        assertEquals(0,library.companionState("movie").optLong("resumeTicks")); assertTrue(library.companionState("movie").optBoolean("played"));
    }
    @Test public void newerSequenceAllowsBackwardSeekAndOldSequenceCannotOverwriteIt() throws Exception {
        library.put(movie()); library.companionState("movie",state(2,3,288000000000L,false));
        library.companionState("movie",state(2,4,144000000000L,false)); library.companionState("movie",state(2,2,396000000000L,true));
        assertEquals(144000000000L,library.companionState("movie").optLong("resumeTicks"));
        assertFalse(library.companionState("movie").optBoolean("played"));
    }
    @Test public void resumeMergeIncludesCompanionOnlyMoviesUsingExistingJellyfinAuthentication() throws Exception {
        backend=state(1,-1,396000000000L,true);
        List<JSONObject> merged=client.mergeResume(Collections.emptyList(),18);
        assertEquals(1,merged.size()); assertEquals("movie",merged.get(0).optString("Id")); assertTrue(Json.canResume(merged.get(0)));
        RecordedRequest discovery=server.takeRequest(); assertTrue(discovery.getHeader("Authorization").contains("Client=\"Lowfin\"")); assertTrue(discovery.getHeader("Authorization").contains("Token=\"test-token\""));
    }
    @Test public void batchedResumeKeepsNativeOrderFreshMetadataMissingFallbackAndScope() throws Exception {
        independent=false;backend=state(1,-1,0,false);
        JSONArray saved=new JSONArray();for(int n=0;n<120;n++)saved.put(Json.obj("Id","cached-"+n,"Type","Episode","Name","Cached "+n,"UserData",Json.obj("PlaybackPositionTicks",600000000L,"Played",false)));library.put(saved,1);
        JSONObject nativeItem=Json.obj("Id","native","Type","Episode","Name","Fresh native","UserData",Json.obj("PlaybackPositionTicks",700000000L,"Played",false));library.put(nativeItem);
        JSONObject staleNative=Json.obj("Id","native","Type","Episode","Name","Stale native","UserData",Json.obj("PlaybackPositionTicks",100000000L,"Played",false));
        JSONObject missing=Json.obj("Id","uncached","Type","Episode","Name","Native fallback","UserData",Json.obj("PlaybackPositionTicks",800000000L,"Played",false));
        library.companionState("uncached",state(2,-1,900000000L,false));
        JSONObject repeatedMissing=new JSONObject(missing.toString());repeatedMissing.put("Name","Second duplicate must not replace first");
        List<JSONObject> values=client.mergeResume(Arrays.asList(missing,staleNative,staleNative,repeatedMissing),200);assertTrue(values.size()>90);assertEquals("uncached",values.get(0).getString("Id"));assertEquals("Native fallback",values.get(0).getString("Name"));assertEquals(90000,Json.position(values.get(0)));assertEquals("native",values.get(1).getString("Id"));assertEquals("Fresh native",values.get(1).getString("Name"));assertEquals(70000,Json.position(values.get(1)));Set<String> ids=new HashSet<>();for(JSONObject item:values)assertTrue(ids.add(item.getString("Id")));
        assertEquals("Stale native",staleNative.getString("Name"));assertFalse(missing.has("LowfinCompanion"));assertEquals(2,server.getRequestCount());
        List<JSONObject> scoped=client.mergeResume(Arrays.asList(missing,staleNative),18,"selected-library","Episode");assertEquals(2,scoped.size());assertEquals("uncached",scoped.get(0).getString("Id"));assertEquals("native",scoped.get(1).getString("Id"));assertEquals(3,server.getRequestCount());
    }
    @Test public void exactDecimalCompletionHandlesTwelveHourVideosAndNeverMode() {
        assertFalse(CompanionPolicy.completes(Json.obj("mode","CustomPercentage","percentage",99.5),429800000000L,duration));
        assertTrue(CompanionPolicy.completes(Json.obj("mode","CustomPercentage","percentage",99.5),429840000000L,duration));
        assertFalse(CompanionPolicy.completes(Json.obj("mode","NeverAutoComplete"),duration,duration));
        assertFalse(CompanionPolicy.completes(Json.obj("mode","RemainingTime","remainingSeconds",120),duration,0));
        assertTrue(CompanionPolicy.completes(Json.obj("mode","RemainingTime","remainingSeconds",120),duration-1200000000L,duration));
    }
    @Test public void disabledPluginStripsSavedShelfDecorationsAndKeepsNativeBehavior() {
        JSONObject decorated=CompanionPolicy.decorate(movie(),state(1,-1,396000000000L,false)); library.companionAvailable=false;
        assertEquals(0,Json.position(library.withCompanion(decorated))); assertTrue(Json.played(library.withCompanion(decorated)));
    }
    @Test public void accountScopesDoNotShareTheIndependentResumeTable() {
        library.companionState("movie",state(1,-1,396000000000L,false));
        Account other=new Account(account.server,"another-user","other-token","Other",account.device); Library second=new Library(context,other.scope());
        try { assertNull(second.companionState("movie")); } finally { second.close(); }
    }
    @Test public void manualResetReconcilesCachedCardsInTheSingleResumeRequest() throws Exception {
        library.put(movie()); library.companionState("movie",state(2,4,396000000000L,false));
        backend=state(3,4,0,true); backend.put("manualFence",true);
        assertTrue(client.mergeResume(Collections.emptyList(),18).isEmpty());
        assertEquals(0,library.companionState("movie").optLong("resumeTicks")); assertEquals(2,server.getRequestCount());
    }
    @Test public void otherClientResumeUsesFreshStateInsteadOfAnOldOwnedPosition() throws Exception {
        library.put(movie()); library.companionState("movie",state(2,4,396000000000L,false));
        backend=state(3,-1,600000000L,false); independent=false;
        JSONObject nativeMovie=movie(); nativeMovie.put("UserData",Json.obj("Played",false,"PlaybackPositionTicks",600000000L));
        List<JSONObject> merged=client.mergeResume(Collections.singletonList(nativeMovie),18);
        assertEquals(1,merged.size()); assertEquals(60000,Json.position(merged.get(0))); assertEquals(2,server.getRequestCount());
    }
    @Test public void existingPlayerReportsRouteThroughCompanionWithOriginalPlaybackFields() throws Exception {
        CompanionApi.Session session=client.prepare(movie(),0,false,false).session;
        PlayerActivity player=reportingPlayer(session);
        java.lang.reflect.Method report=PlayerActivity.class.getDeclaredMethod("report",String.class,long.class); report.setAccessible(true);
        report.invoke(player,"",0L); report.invoke(player,"/Progress",39600000L); report.invoke(player,"/Stopped",39600100L);
        ((LowfinApp)context).reports.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1,starts); assertEquals(396001000000L,backend.optLong("resumeTicks")); assertEquals(0,nativeReports);
        boolean sawProgress=false;
        for(int index=0,count=server.getRequestCount();index<count;index++) {
            RecordedRequest request=server.takeRequest(); if(!request.getPath().endsWith("/playback")) continue;
            JSONObject frame=new JSONObject(request.getBody().readUtf8()); if(!frame.optString("event").equals("Progress")) continue;
            JSONObject playback=Json.object(frame,"playback"); assertEquals("source-file",playback.optString("MediaSourceId")); assertEquals("jellyfin-play-session",playback.optString("PlaySessionId")); assertEquals(2,playback.optInt("AudioStreamIndex")); assertEquals(-1,playback.optInt("SubtitleStreamIndex")); sawProgress=true;
        }
        assertTrue(sawProgress);
    }
    @Test public void existingPlayerKeepsNativePlaybackReportsWithoutAManagedSession() throws Exception {
        PlayerActivity player=reportingPlayer(null);
        java.lang.reflect.Method report=PlayerActivity.class.getDeclaredMethod("report",String.class,long.class); report.setAccessible(true);
        report.invoke(player,"",0L); report.invoke(player,"/Progress",1234L); report.invoke(player,"/Stopped",2345L);
        ((LowfinApp)context).reports.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(3,nativeReports); assertEquals(0,starts);
    }
    private PlayerActivity reportingPlayer(CompanionApi.Session session) throws Exception {
        PlayerActivity player=new PlayerActivity();
        for(Object[] field:new Object[][]{{"app",context},{"api",new Api(account)},{"library",library},{"companion",client},{"companionSession",session},{"item",movie()},{"sessionId","jellyfin-play-session"},{"sourceId","source-file"},{"audio",2}}) {
            java.lang.reflect.Field target=PlayerActivity.class.getDeclaredField((String)field[0]); target.setAccessible(true); target.set(player,field[1]);
        }
        return player;
    }
    @Test public void unconfiguredMediaKeepsNativePlaybackAndDoesNotCreateAResumeLease() throws Exception {
        backend=state(1,-1,396000000000L,false); backend.put("active",false); backend.put("effectiveRule",Json.obj("mode","Default","persistentResume",false));
        CompanionApi.Prepared prepared=client.prepare(movie(),1234,false,false); assertNull(prepared.session); assertEquals(1234,prepared.position);
        assertFalse(CompanionPolicy.active(backend)); assertEquals(0,Json.position(CompanionPolicy.decorate(movie(),backend)));
        assertEquals(0,starts);
    }
    @Test public void scopedResumeDoesNotIncludeGlobalCachedCardsOrTrustAnOldPlugin() throws Exception {
        JSONObject unrelated=Json.obj("Id","other-episode","Type","Episode","Name","Other library","UserData",Json.obj("PlaybackPositionTicks",600000000L)); library.put(unrelated); library.link("selected-library",Json.arr(unrelated),1); // Deliberately corrupted legacy membership.
        backend=state(1,-1,0,false); independent=false;
        assertTrue(client.mergeResume(Collections.emptyList(),18,"selected-library","Episode").isEmpty());
        boolean scoped=false; for(int i=0,count=server.getRequestCount();i<count;i++) { RecordedRequest request=server.takeRequest(); if(request.getPath().contains("/resume")) { assertEquals("selected-library",request.getRequestUrl().queryParameter("parentId")); scoped=true; } } assertTrue(scoped);
    }
    @Test public void wrongScopeResponseIsRejectedRatherThanShowingAnotherLibrary() throws Exception {
        returnedScope="wrong-library";
        try { client.mergeResume(Collections.emptyList(),18,"selected-library","Episode"); fail("Different scope must be rejected"); } catch(java.io.IOException expected) { assertTrue(expected.getMessage().contains("scope")); }
    }
    @Test public void legacyPluginCannotAddItsGlobalRowsToAScopedLibrary() throws Exception {
        legacyPlugin=true; backend=state(1,-1,396000000000L,false);
        assertTrue(client.mergeResume(Collections.emptyList(),18,"selected-library","Episode").isEmpty()); assertEquals(1,server.getRequestCount());
    }
    @Test public void excludedResumeStaysAvailableInDetailsButNotInContinueOrItsCache() throws Exception {
        backend=state(1,-1,396000000000L,false); backend.put("resumeVisible",false); library.put(movie());
        assertTrue(client.mergeResume(Collections.emptyList(),18).isEmpty()); assertTrue(library.resumable(null,null,18,0).isEmpty());
        JSONObject detail=library.withCompanion(movie()); assertTrue(Json.canResume(detail)); assertFalse(Json.inContinue(detail)); assertEquals(39600000L,Json.position(detail));
    }
    @Test public void schemaFiveUpgradePreservesMetadataAndAddsResumeVisibility() throws Exception {
        String scope="upgrade-"+UUID.randomUUID(); android.database.sqlite.SQLiteDatabase old=context.openOrCreateDatabase("library-"+scope+".db",0,null);
        Library schema=new Library(context,scope); schema.onCreate(old); old.execSQL("DROP TABLE companion"); old.execSQL("CREATE TABLE companion (companion_item TEXT PRIMARY KEY,companion_json TEXT,companion_resume INTEGER,companion_has_resume INTEGER,companion_stamp INTEGER)"); old.execSQL("INSERT INTO state VALUES ('existing','kept')"); old.setVersion(5); old.close(); schema.close();
        Library upgraded=new Library(context,scope); try { assertEquals("kept",upgraded.state("existing")); upgraded.companionState("movie",state(1,0,396000000000L,false)); assertNotNull(upgraded.companionState("movie")); } finally { upgraded.close(); }
    }
    @Test public void nativeClearDoesNotReappearFromOldMetadataWhenCompanionIsInactive() throws Exception {
        JSONObject stale=movie(); stale.put("UserData",Json.obj("Played",false,"PlaybackPositionTicks",72000000000L)); library.put(stale);
        backend=state(1,-1,0,true); backend.put("active",false); independent=false;
        assertTrue(client.mergeResume(Collections.emptyList(),18).isEmpty());
        assertTrue(library.resumable(null,null,18,0).isEmpty()); assertEquals(0,Json.position(library.item("movie"))); assertTrue(Json.played(library.item("movie")));
    }
}
