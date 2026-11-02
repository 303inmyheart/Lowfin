package de.lowfin.tv;

import android.app.Activity;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Looper;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class DiagnosticsTest {
    private LowfinApp app;
    private Diagnostics diagnostics;
    @Before public void setup() {app=(LowfinApp)RuntimeEnvironment.getApplication();diagnostics=app.diagnostics;diagnostics.setEnabled(false);shadowOf(Looper.getMainLooper()).idle();}
    @After public void cleanup() {diagnostics.setEnabled(false);shadowOf(Looper.getMainLooper()).idle();}
    @Test public void buildDefaultAndExplicitUserChoiceAreRespected() {
        app.settings.edit().remove(Diagnostics.SETTING).commit();assertEquals(BuildConfig.DIAGNOSTICS_DEFAULT,diagnostics.enabled());
        diagnostics.setEnabled(!BuildConfig.DIAGNOSTICS_DEFAULT);assertEquals(!BuildConfig.DIAGNOSTICS_DEFAULT,diagnostics.enabled());
    }
    @Test public void disabledHasNoHistoryOrFrameWorkerAndIgnoresSamples() throws Exception {
        for(Diagnostics.Event event:Diagnostics.Event.values())diagnostics.record(event,1,2,3,4);
        diagnostics.frame(diagnostics.epoch(),15000000,0);
        JSONObject report=diagnostics.snapshot();assertFalse(report.getBoolean("enabled"));assertFalse(report.has("events"));assertFalse(report.has("uiFrames"));
        assertNull(ReflectionHelpers.getField(diagnostics,"frameThread"));
    }
    @Test public void boundedHistoryRetainsNewestNumericEventsAndDisablePurgesEverything() throws Exception {
        diagnostics.setEnabled(true);
        for(int i=0;i<Diagnostics.CAPACITY+17;i++)diagnostics.record(Diagnostics.Event.NAV_SERVER,i,2,3,4);
        JSONObject report=diagnostics.snapshot();JSONArray events=report.getJSONArray("events");
        assertEquals(Diagnostics.CAPACITY,events.length());assertEquals(17,report.getLong("evictedEvents"));assertEquals(17,events.getJSONObject(0).getLong("a"));
        assertEquals(Diagnostics.CAPACITY+16,events.getJSONObject(events.length()-1).getLong("a"));
        Set<String> expected=new HashSet<>(Arrays.asList("event","atMs","a","b","c","d"));
        Set<String> keys=new HashSet<>();events.getJSONObject(0).keys().forEachRemaining(keys::add);assertEquals(expected,keys);
        diagnostics.setEnabled(false);assertFalse(diagnostics.snapshot().has("events"));diagnostics.setEnabled(true);
        assertEquals(0,diagnostics.snapshot().getJSONArray("events").length());
    }
    @Test public void frameBucketsAndResetRejectCallbacksFromThePreviousSession() throws Exception {
        diagnostics.setEnabled(true);long generation=diagnostics.epoch();
        diagnostics.frame(generation,8000000,0);diagnostics.frame(generation,16000001,2);diagnostics.frame(generation,251000000,0);diagnostics.frame(generation,-1,10);
        JSONObject frame=diagnostics.snapshot().getJSONObject("uiFrames");assertEquals(3,frame.getLong("count"));assertEquals(2,frame.getLong("reportDrops"));
        JSONArray buckets=frame.getJSONArray("buckets");assertEquals(1,buckets.getLong(0));assertEquals(1,buckets.getLong(2));assertEquals(1,buckets.getLong(7));assertEquals(251000000,frame.getLong("maxNs"));
        diagnostics.clear();diagnostics.frame(generation,90000000,9);assertEquals(0,diagnostics.snapshot().getJSONObject("uiFrames").getLong("count"));
        diagnostics.frame(diagnostics.epoch(),10000000,0);assertEquals(1,diagnostics.snapshot().getJSONObject("uiFrames").getLong("count"));
    }
    @Test public void frameMonitoringExistsOnlyWhileEnabledAndAnActivityIsResumed() {
        ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup();
        try {
            assertNull(ReflectionHelpers.getField(diagnostics,"frameThread"));diagnostics.setEnabled(true);shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(ReflectionHelpers.getField(diagnostics,"frameThread"));
            activity.pause();assertNull(ReflectionHelpers.getField(diagnostics,"frameThread"));activity.resume();
            assertNotNull(ReflectionHelpers.getField(diagnostics,"frameThread"));diagnostics.setEnabled(false);shadowOf(Looper.getMainLooper()).idle();
            assertNull(ReflectionHelpers.getField(diagnostics,"frameThread"));
        } finally {activity.pause().stop().destroy();}
    }
    @Test public void privateDumpSupportsEnableClearDisableWithoutCrudAccess() throws Exception {
        ProviderInfo info=app.getPackageManager().getProviderInfo(new android.content.ComponentName(app,DiagnosticsProvider.class),0);
        assertFalse(info.exported);assertFalse(info.grantUriPermissions);
        DiagnosticsProvider provider=Robolectric.buildContentProvider(DiagnosticsProvider.class).create().get();
        StringWriter output=new StringWriter();provider.dump(null,new PrintWriter(output),new String[]{"--enable"});assertTrue(new JSONObject(output.toString()).getBoolean("enabled"));
        diagnostics.record(Diagnostics.Event.FIRST_FRAME,500,0,0,0);provider.dump(null,new PrintWriter(new StringWriter()),new String[]{"--clear"});assertEquals(0,diagnostics.snapshot().getJSONArray("events").length());
        provider.dump(null,new PrintWriter(new StringWriter()),new String[]{"--disable"});assertFalse(diagnostics.enabled());
        try {provider.query(Uri.parse("content://de.lowfin.tv.diagnostics"),null,null,null,null);fail();}catch(UnsupportedOperationException expected) {}
    }
    @Test public void httpSamplesExcludeCredentialsUrlsBodiesAndErrorMessages() throws Exception {
        diagnostics.setEnabled(true);MockWebServer server=new MockWebServer();server.start();
        try {
            server.enqueue(new MockResponse().setBody("{\"Title\":\"PRIVATE_MEDIA_TITLE\"}"));
            Api api=new Api(new Account(server.url("/").toString(),"SECRET_USER","SECRET_TOKEN","PRIVATE_NAME","SECRET_DEVICE"));api.diagnostics=diagnostics;
            api.get("/private-item-id","api_key","SECRET_QUERY");
            server.enqueue(new MockResponse().setResponseCode(403).setBody("SECRET_ERROR"));
            try {api.post("/private-write",Json.obj("secret","PRIVATE_BODY"));fail();}catch(Api.Failure expected) {}
            JSONArray events=diagnostics.snapshot().getJSONArray("events");assertEquals(2,events.length());assertEquals("HTTP_READ",events.getJSONObject(0).getString("event"));assertEquals(200,events.getJSONObject(0).getInt("b"));assertEquals("HTTP_WRITE",events.getJSONObject(1).getString("event"));assertEquals(403,events.getJSONObject(1).getInt("b"));
            String report=diagnostics.snapshot().toString();for(String secret:new String[]{"SECRET_","PRIVATE_","private-item-id","private-write","localhost","127.0.0.1"})assertFalse(secret,report.contains(secret));
        } finally {server.shutdown();}
    }
}
