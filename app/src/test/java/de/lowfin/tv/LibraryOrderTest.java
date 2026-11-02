package de.lowfin.tv;

import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class LibraryOrderTest {
    private static final String A="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",B="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",C="cccccccccccccccccccccccccccccccc",H="dddddddddddddddddddddddddddddddd",L="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private MockWebServer server;private Api api;private volatile JSONObject configuration;private volatile boolean addLibrary,denyWrite,overwriteAfterWrite,wrongUser,missingConfiguration,legacyOnly;private volatile String writePath;private int writes;
    @Before public void setup() throws Exception {
        server=new MockWebServer();configuration=Json.obj("OrderedViews",Json.arr(LibraryOrder.wireId(H),LibraryOrder.wireId(A),LibraryOrder.wireId(B),LibraryOrder.wireId(C),LibraryOrder.wireId(L)),"MyMediaExcludes",Json.arr(LibraryOrder.wireId(H)),"GroupedFolders",Json.arr(LibraryOrder.wireId(H)),"SubtitleMode","Default","AudioLanguagePreference","de","FutureSetting",Json.obj("keep",true));
        server.setDispatcher(new Dispatcher() {public MockResponse dispatch(RecordedRequest request) {
            try {
                String path=request.getRequestUrl().encodedPath();
                if(path.equals("/Users/user"))return new MockResponse().setBody(Json.obj("Id",wrongUser?"other":"user","Configuration",missingConfiguration?JSONObject.NULL:new JSONObject(configuration.toString())).toString());
                if(path.equals("/Users/Configuration")||path.equals("/Users/user/Configuration")) {if(legacyOnly&&path.equals("/Users/Configuration"))return new MockResponse().setResponseCode(404);writes++;writePath=path;if(denyWrite)return new MockResponse().setResponseCode(403);configuration=new JSONObject(request.getBody().readUtf8());if(overwriteAfterWrite)configuration.put("OrderedViews",Json.arr(LibraryOrder.wireId(A),LibraryOrder.wireId(C),LibraryOrder.wireId(B)));return new MockResponse().setResponseCode(204);}
                if(path.equals("/Users/user/Views")) {
                    List<String> order=new ArrayList<>();JSONArray configured=configuration.getJSONArray("OrderedViews");for(int i=0;i<configured.length();i++)order.add(LibraryOrder.normalize(configured.getString(i)));
                    List<String> ids=new ArrayList<>(Arrays.asList(A,B,C,L));if(addLibrary)ids.add("ffffffffffffffffffffffffffffffff");ids.sort(Comparator.comparingInt(id->order.contains(id)?order.indexOf(id):999));JSONArray items=new JSONArray();
                    for(String id:ids)items.put(Json.obj("Id",id,"Name",id.equals(A)?"Alpha":id.equals(B)?"Beta":"Library "+id.charAt(0),"CollectionType",id.equals(L)?"livetv":"movies"));return new MockResponse().setBody(Json.obj("Items",items).toString());
                }
                return new MockResponse().setResponseCode(404);
            }catch(Exception error) {return new MockResponse().setResponseCode(500);}
        }});server.start();api=new Api(new Account(server.url("/").toString(),"user","test-token","Name","device"));
    }
    @After public void stop() throws Exception {server.shutdown();}
    @Test public void savesNativeOrderOnceAndRetainsFreshUnrelatedSettingsAndHiddenSlots() throws Exception {
        LibraryOrder.Snapshot baseline=LibraryOrder.load(api);assertEquals(Arrays.asList(A,B,C),baseline.visible());configuration.put("AudioLanguagePreference","en");
        LibraryOrder.Snapshot saved=LibraryOrder.save(api,baseline,Arrays.asList(C,A,B));assertEquals(Arrays.asList(C,A,B),saved.visible());assertEquals(Arrays.asList(H,C,A,B,L),saved.order);assertEquals(1,writes);
        assertEquals("en",configuration.getString("AudioLanguagePreference"));assertTrue(configuration.getJSONObject("FutureSetting").getBoolean("keep"));assertEquals(Json.arr(LibraryOrder.wireId(H)).toString(),configuration.getJSONArray("MyMediaExcludes").toString());assertEquals(Json.arr(LibraryOrder.wireId(H)).toString(),configuration.getJSONArray("GroupedFolders").toString());
        JSONArray wire=configuration.getJSONArray("OrderedViews");for(int i=0;i<wire.length();i++)assertEquals(36,wire.getString(i).length());
    }
    @Test public void aChangedServerOrderRequiresReviewAndCannotBeOverwritten() throws Exception {
        LibraryOrder.Snapshot baseline=LibraryOrder.load(api);configuration.put("OrderedViews",Json.arr(LibraryOrder.wireId(B),LibraryOrder.wireId(A),LibraryOrder.wireId(C)));
        try {LibraryOrder.save(api,baseline,Arrays.asList(C,A,B));fail("Stale order accepted");}catch(LibraryOrder.Conflict conflict) {assertEquals(R.string.order_conflict,conflict.label);}assertEquals(0,writes);
    }
    @Test public void changedLibraryMembershipRequiresReloadWithoutWriting() throws Exception {
        LibraryOrder.Snapshot baseline=LibraryOrder.load(api);addLibrary=true;try {LibraryOrder.save(api,baseline,Arrays.asList(C,A,B));fail("Stale libraries accepted");}catch(LibraryOrder.Conflict conflict) {assertEquals(R.string.order_libraries_changed,conflict.label);}assertEquals(0,writes);
    }
    @Test public void sameOrAlreadyCompletedOrderDoesNotWriteAgain() throws Exception {
        LibraryOrder.Snapshot baseline=LibraryOrder.load(api);LibraryOrder.save(api,baseline,baseline.visible());assertEquals(0,writes);
        LibraryOrder.save(api,baseline,Arrays.asList(C,A,B));assertEquals(1,writes);LibraryOrder.save(api,baseline,Arrays.asList(C,A,B));assertEquals(1,writes);
    }
    @Test public void invalidAndDuplicateIdsAreRejectedBeforeAnyNetworkWrite() throws Exception {
        LibraryOrder.Snapshot baseline=LibraryOrder.load(api);for(List<String> invalid:Arrays.asList(Arrays.asList(A,A,C),Arrays.asList(A,B),Arrays.asList(A,B,"other")))try {LibraryOrder.save(api,baseline,invalid);fail("Invalid permutation accepted");}catch(IllegalArgumentException expected) {}assertEquals(0,writes);assertEquals(2,server.getRequestCount());
    }
    @Test public void wrongUserOrMissingFullConfigurationCannotProduceDefaultWrites() throws Exception {
        wrongUser=true;try {LibraryOrder.load(api);fail("Wrong user accepted");}catch(java.io.IOException expected) {}wrongUser=false;missingConfiguration=true;try {LibraryOrder.load(api);fail("Missing configuration accepted");}catch(java.io.IOException expected) {}assertEquals(0,writes);
    }
    @Test public void aConcurrentWriteAfterOurPostIsReportedAndNeverRolledBack() throws Exception {
        LibraryOrder.Snapshot baseline=LibraryOrder.load(api);overwriteAfterWrite=true;try {LibraryOrder.save(api,baseline,Arrays.asList(C,A,B));fail("Concurrent overwrite accepted");}catch(LibraryOrder.Conflict expected) {}assertEquals(1,writes);assertEquals(Json.arr(LibraryOrder.wireId(A),LibraryOrder.wireId(C),LibraryOrder.wireId(B)).toString(),configuration.getJSONArray("OrderedViews").toString());
    }
    @Test public void normalizationHandlesGuidFormatsWithoutChangingOpaqueKeys() {
        assertEquals(A,LibraryOrder.normalize(LibraryOrder.wireId(A).toUpperCase(Locale.ROOT)));assertEquals("library-key",LibraryOrder.normalize("library-key"));assertEquals(LibraryOrder.wireId(A),LibraryOrder.wireId(LibraryOrder.normalize(A)));
    }
    @Test public void modernAndLegacyConfigurationRoutesBothWorkWithoutRetryingPermissionFailures() throws Exception {
        LibraryOrder.Snapshot baseline=LibraryOrder.load(api);LibraryOrder.save(api,baseline,Arrays.asList(C,A,B));assertEquals("/Users/Configuration",writePath);
        baseline=LibraryOrder.load(api);legacyOnly=true;LibraryOrder.save(api,baseline,Arrays.asList(A,C,B));assertEquals("/Users/user/Configuration",writePath);
        baseline=LibraryOrder.load(api);legacyOnly=false;denyWrite=true;int before=writes;try {LibraryOrder.save(api,baseline,Arrays.asList(B,C,A));fail("Forbidden save accepted");}catch(Api.Failure failure) {assertEquals(403,failure.status);}assertEquals(before+1,writes);assertEquals("/Users/Configuration",writePath);
    }
}
