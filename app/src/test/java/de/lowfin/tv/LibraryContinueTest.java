package de.lowfin.tv;

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
public class LibraryContinueTest {
    private MockWebServer server;
    private Library db;
    private Api api;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();
        api=new Api(new Account(server.url("/").toString(),"user","test-token","Test","device"));
        db=new Library(RuntimeEnvironment.getApplication(),"library-continue-"+UUID.randomUUID());
    }
    @After public void close() throws Exception {db.close();server.shutdown();}
    private MockResponse episode(String id) {
        return new MockResponse().setBody(Json.obj("Items",new JSONArray().put(Json.obj("Id",id,"Type","Episode","Name",id,"RunTimeTicks",6000000000L,"UserData",Json.obj("PlaybackPositionTicks",600000000L)))).toString());
    }
    @Test public void bothShelvesStayLibraryScopedAndDoNotUseHomeResumeFilteringOrCaches() throws Exception {
        db.state("home-0","keep-resume");db.state("home-1","keep-next");
        LibraryContinue first=new LibraryContinue(api,db,null,"library-a");
        server.enqueue(episode("resume-a"));assertEquals("resume-a",first.load(false,0,18).get(0).optString("Id"));
        RecordedRequest resume=server.takeRequest();assertEquals("/Users/user/Items",resume.getPath().split("\\?")[0]);
        assertEquals("library-a",resume.getRequestUrl().queryParameter("ParentId"));assertEquals("IsResumable",resume.getRequestUrl().queryParameter("Filters"));assertEquals("Episode",resume.getRequestUrl().queryParameter("IncludeItemTypes"));
        server.enqueue(episode("next-a"));assertEquals("next-a",first.load(true,0,18).get(0).optString("Id"));
        RecordedRequest next=server.takeRequest();assertEquals("/Shows/NextUp",next.getRequestUrl().encodedPath());assertEquals("library-a",next.getRequestUrl().queryParameter("ParentId"));assertEquals("false",next.getRequestUrl().queryParameter("EnableResumable"));
        assertEquals("resume-a",first.cached(false,0,18).get(0).optString("Id"));assertEquals("next-a",first.cached(true,0,18).get(0).optString("Id"));
        LibraryContinue second=new LibraryContinue(api,db,null,"library-b");assertTrue(second.cached(false,0,18).isEmpty());assertTrue(second.cached(true,0,18).isEmpty());
        assertEquals("keep-resume",db.state("home-0"));assertEquals("keep-next",db.state("home-1"));
    }
    @Test public void morePagesRetainLibraryFilterAndIndependentCachedOffsets() throws Exception {
        LibraryContinue source=new LibraryContinue(api,db,null,"library-a");server.enqueue(episode("later"));source.load(true,60,61);
        RecordedRequest request=server.takeRequest();assertEquals("library-a",request.getRequestUrl().queryParameter("ParentId"));assertEquals("60",request.getRequestUrl().queryParameter("StartIndex"));assertEquals("61",request.getRequestUrl().queryParameter("Limit"));
        assertTrue(source.cached(true,0,61).isEmpty());assertEquals("later",source.cached(true,60,61).get(0).optString("Id"));
    }
    @Test public void libraryShelvesRemainPresentWhenHomeRowsAreDisabledAndRestoreTheirOwnState() {
        LowfinApp app=(LowfinApp)RuntimeEnvironment.getApplication();app.settings.edit().putBoolean("homeResume",false).putBoolean("homeNext",false).commit();
        HomeAdapter adapter=new HomeAdapter(null,Collections.emptyList(),(item,row)->{},row->{},(item,row)->{},row->{});adapter.libraryContinue(()->{});
        assertEquals(2,adapter.getItemCount());assertEquals(0,adapter.firstRow());
        adapter.update(1,Collections.singletonList(Json.obj("Id","next-a","Type","Episode")));assertEquals(1,adapter.firstRow());
        HomeAdapter restored=new HomeAdapter(null,Collections.emptyList(),(item,row)->{},row->{},(item,row)->{},row->{});restored.libraryContinue(()->{});
        assertTrue(restored.restore(adapter.snapshot(1)));assertEquals("next-a",restored.rows.get(1).get(0).optString("Id"));
    }
}
