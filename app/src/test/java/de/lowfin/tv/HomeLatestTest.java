package de.lowfin.tv;

import android.content.*;
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
public class HomeLatestTest {
    private MockWebServer server;private Api api;private Library db;private SharedPreferences prefs;private Account account;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();Context c=RuntimeEnvironment.getApplication();I18n.init(c);
        account=new Account(server.url("/").toString(),"user","token","User","device");api=new Api(account);db=new Library(c,account.scope());prefs=c.getSharedPreferences(UUID.randomUUID().toString(),0);
    }
    @After public void cleanup() throws Exception {db.close();server.shutdown();}
    private JSONObject view(String id,String type) {return Json.obj("Id",id,"Name",id,"CollectionType",type);}
    @Test public void videoLibrariesDefaultOnAndSwitchesAreAccountScoped() {
        List<JSONObject> views=Arrays.asList(view("films","movies"),view("shows","tvshows"),view("mixed",""),view("live","livetv"),view("audio","music"),view("books","books"),view("films","movies"));
        assertEquals(3,HomeLatest.selected(prefs,account,views).size());prefs.edit().putBoolean(HomeLatest.setting(account,"films"),false).commit();assertEquals(2,HomeLatest.selected(prefs,account,views).size());
        Account other=new Account(account.server,"other","token","Other","device");assertEquals(3,HomeLatest.selected(prefs,other,views).size());
        prefs.edit().putBoolean(HomeLatest.setting(account),false).commit();assertTrue(HomeLatest.selected(prefs,account,views).isEmpty());prefs.edit().putBoolean(HomeLatest.setting(account),true).commit();assertEquals(2,HomeLatest.selected(prefs,account,views).size());
    }
    @Test public void latestEndpointIsScopedAndCachedSeriesNormalizeEpisodesWithoutExtraRequests() throws Exception {
        db.put(Json.obj("Id","show","Type","Series","Name","Series","ProviderIds",Json.obj("Tmdb","42")));
        server.enqueue(new MockResponse().setBody(Json.arr(Json.obj("Id","film","Type","Movie"),Json.obj("Id","ep","Type","Episode","SeriesId","show"),Json.obj("Id","season","Type","Season","SeriesId","show"),Json.obj("Id","person","Type","Person")).toString()));
        List<JSONObject> rows=HomeLatest.load(api,db,view("selected","tvshows"));assertEquals(2,rows.size());assertEquals("film",rows.get(0).optString("Id"));assertEquals("show",rows.get(1).optString("Id"));assertEquals("42",rows.get(1).getJSONObject("ProviderIds").getString("Tmdb"));
        RecordedRequest request=server.takeRequest();assertEquals("/Users/user/Items/Latest",request.getPath().split("\\?")[0]);assertEquals("selected",request.getRequestUrl().queryParameter("ParentId"));assertEquals("true",request.getRequestUrl().queryParameter("GroupItems"));assertEquals("24",request.getRequestUrl().queryParameter("Limit"));assertEquals(1,server.getRequestCount());
    }
    @Test public void missingSeriesResolveInOneBatchAndKeepServerOrder() throws Exception {
        server.enqueue(new MockResponse().setBody(Json.arr(Json.obj("Id","a1","Type","Episode","SeriesId","a"),Json.obj("Id","b1","Type","Season","SeriesId","b"),Json.obj("Id","a2","Type","Episode","SeriesId","a")).toString()));
        server.enqueue(new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","b","Type","Series"),Json.obj("Id","a","Type","Series"))).toString()));
        List<JSONObject> rows=HomeLatest.load(api,db,view("selected","tvshows"));assertEquals("a",rows.get(0).optString("Id"));assertEquals("b",rows.get(1).optString("Id"));assertEquals(2,server.getRequestCount());server.takeRequest();RecordedRequest batch=server.takeRequest();assertEquals("a,b",batch.getRequestUrl().queryParameter("Ids"));assertEquals("Series",batch.getRequestUrl().queryParameter("IncludeItemTypes"));
    }
    @Test public void latestGroupCountsNeverOverwriteTheCachedSeriesTotal() throws Exception {
        db.put(Json.obj("Id","show","Type","Series","ChildCount",40));
        server.enqueue(new MockResponse().setBody(Json.arr(Json.obj("Id","show","Type","Series","ChildCount",2)).toString()));
        List<JSONObject> rows=HomeLatest.load(api,db,view("selected","tvshows"));db.put(new JSONArray(rows),System.currentTimeMillis());assertEquals(40,db.item("show").getInt("ChildCount"));
    }
    @Test public void rowOrderingSurvivesDisableRenameAndDynamicLibraryRemoval() {
        List<JSONObject> views=Arrays.asList(view("films","movies"),view("shows","tvshows"));List<String> all=HomeLayout.keys(Collections.emptyList(),false,false,views);
        HomeLayout.move(prefs,account,all,"latest:shows",-1);List<String> expected=HomeLayout.read(prefs,account,all);
        HomeAdapter adapter=new HomeAdapter(null,Collections.emptyList(),(j,r)->{},r->{},(j,r)->{},r->{});adapter.configure(prefs,account,Collections.emptyList(),false,views);
        int row=-1;for(Map.Entry<Integer,JSONObject> entry:adapter.latestLibraries().entrySet())if(entry.getValue().optString("Id").equals("shows"))row=entry.getKey();assertTrue(row>=0&&adapter.feature(row));assertEquals(expected.indexOf("latest:shows"),adapter.position(row));
        JSONObject renamed=Json.obj("Id","shows","Name","Renamed","CollectionType","tvshows");adapter.latestLibraries(prefs,account,Collections.singletonList(renamed));assertTrue(adapter.labels.get(row).contains("Renamed"));assertEquals(1,adapter.latestLibraries().size());
        adapter.latestLibraries(prefs,account,Collections.emptyList());adapter.update(row,Collections.singletonList(Json.obj("Id","show","Type","Series")));assertEquals(-1,adapter.position(row));
        assertEquals(expected,HomeLayout.read(prefs,account,all));
    }
}
