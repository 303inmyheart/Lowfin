package de.lowfin.tv;

import okhttp3.mockwebserver.*;
import org.junit.*;
import org.json.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class ApiTest {
    private MockWebServer server; private Api api;
    @Before public void setup() throws Exception { server=new MockWebServer(); server.start(); api=new Api(new Account(server.url("/jellyfin").toString(),"user","session-token","name","device")); }
    @After public void stop() throws Exception { server.shutdown(); }
    @Test public void LetterJumpUsesScopedCountQueriesWithoutDownloadingWholeLibrary() throws Exception {
        server.enqueue(new MockResponse().setBody(Json.obj("Items",new org.json.JSONArray(),"TotalRecordCount",162).toString()));server.enqueue(new MockResponse().setBody(Json.obj("Items",new org.json.JSONArray(),"TotalRecordCount",89).toString()));
        assertEquals(73,api.letterOffset("Movie","films","M"));
        RecordedRequest all=server.takeRequest(),suffix=server.takeRequest();for(RecordedRequest request:new RecordedRequest[]{all,suffix}) { assertEquals("films",request.getRequestUrl().queryParameter("ParentId"));assertEquals("Movie",request.getRequestUrl().queryParameter("IncludeItemTypes"));assertEquals("0",request.getRequestUrl().queryParameter("Limit"));assertEquals("false",request.getRequestUrl().queryParameter("EnableImages")); }assertEquals("M",suffix.getRequestUrl().queryParameter("NameStartsWithOrGreater"));
    }
    @Test public void MissingLetterAndInconsistentCountsCannotProduceWrongPageOffsets() throws Exception {
        server.enqueue(new MockResponse().setBody(Json.obj("TotalRecordCount",3).toString()));server.enqueue(new MockResponse().setBody(Json.obj("TotalRecordCount",0).toString()));assertEquals(-1,api.letterOffset("Series","shows","Z"));
        server.enqueue(new MockResponse().setBody(Json.obj("TotalRecordCount",3).toString()));server.enqueue(new MockResponse().setBody(Json.obj("TotalRecordCount",4).toString()));assertThrows(java.io.IOException.class,()->api.letterOffset("Series","shows","M"));
    }
    @Test public void loginUsesUsernameAndPasswordWithoutPuttingSecretsInUrl() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"AccessToken\":\"token\"}"));
        api.post("/Users/AuthenticateByName",Json.obj("Username","Rico","Pw","secret"));
        RecordedRequest r=server.takeRequest(2,TimeUnit.SECONDS); assertNotNull(r);
        assertEquals("/jellyfin/Users/AuthenticateByName",r.getPath()); assertEquals("POST",r.getMethod());
        assertEquals("secret",new JSONObject(r.getBody().readUtf8()).optString("Pw"));
        assertFalse(r.getPath().contains("secret"));
    }
    @Test public void authUsesHeaderAndPreservesReverseProxyPath() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}")); api.items(60,60,"Series",null,null);
        RecordedRequest r=server.takeRequest(2,TimeUnit.SECONDS); assertNotNull(r);
        assertTrue(r.getPath().startsWith("/jellyfin/Users/user/Items?"));
        assertEquals("60",r.getRequestUrl().queryParameter("StartIndex"));
        assertTrue(r.getHeader("Authorization").contains("Token=\"session-token\""));
        assertFalse(r.getPath().contains("session-token"));
    }
    @Test public void libraryRequestOnlyUsesValidItemFields() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}")); api.items(0,150,"Movie,Series,Season,Episode",null,null);
        String fields=server.takeRequest(2,TimeUnit.SECONDS).getRequestUrl().queryParameter("Fields");
        for(String bad:new String[]{"UserData","SeriesId","SeasonId","ImageTags"}) assertFalse(fields.contains(bad));
        assertTrue(fields.contains("ParentId")); assertTrue(fields.contains("Overview"));
    }
    @Test public void emptySuccessBodiesAreAcceptedForReportsAndClose() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204)); api.report("/Stopped",Json.obj("ItemId","item","PositionTicks",600000000L));
        RecordedRequest r=server.takeRequest(2,TimeUnit.SECONDS); assertEquals("/jellyfin/Sessions/Playing/Stopped",r.getPath());
        assertEquals(600000000L,new JSONObject(r.getBody().readUtf8()).optLong("PositionTicks"));
    }
    @Test public void expiredSessionReturnsTypedFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        try { api.get("/Users/Me"); fail("Expected failure"); } catch(Api.Failure e) { assertEquals(401,e.status); }
    }
    @Test public void LibraryListsOmitHeavyPlaybackFieldsWhileDetailRequestsStayComplete() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"Items\":[]}")); api.items(0,60,"Movie",null,null);
        String fields=server.takeRequest(2,TimeUnit.SECONDS).getRequestUrl().queryParameter("Fields");
        for(String heavy:new String[]{"MediaSources","MediaStreams","Chapters"}) assertFalse(fields.contains(heavy));
        for(String needed:new String[]{"Overview","Genres","ProviderIds","ParentId"}) assertTrue(fields.contains(needed));
        server.enqueue(new MockResponse().setBody(Json.obj("Id","movie","MediaSources",Json.arr(Json.obj("Id","source")),"Chapters",Json.arr(Json.obj("StartPositionTicks",1000))).toString()));
        JSONObject full=api.item("movie"); assertEquals(1,full.getJSONArray("MediaSources").length()); assertEquals(1,full.getJSONArray("Chapters").length());
        assertNull(server.takeRequest(2,TimeUnit.SECONDS).getRequestUrl().queryParameter("Fields"));
    }
    @Test public void AccountScopeRemainsCompatibleAndIndependentOfSessionToken() throws Exception {
        Account first=new Account("http://server/","user","one","Name","device");
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest("http://server|user".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder legacy=new StringBuilder(); for(byte b:digest) legacy.append(String.format("%02x",b));
        assertEquals(legacy.substring(0,24),first.scope()); assertSame(first.scope(),first.scope());
        assertEquals(first.scope(),new Account("http://server","user","two","Name","other-device").scope());
        assertNotEquals(first.scope(),new Account("http://server","another-user","one","Name","device").scope());
    }
}
