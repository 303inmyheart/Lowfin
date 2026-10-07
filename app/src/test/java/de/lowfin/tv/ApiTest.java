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
}
