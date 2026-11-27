package de.lowfin.tv;

import android.view.View;
import okhttp3.mockwebserver.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class RatingsVisibilityTest {
    private LowfinApp app;private MockWebServer server;private Library db;private Api api;private Ratings ratings;
    private final Map<String,String> keys=new HashMap<>();
    @Before public void setup() throws Exception {
        app=(LowfinApp)RuntimeEnvironment.getApplication();app.settings.edit().clear().putBoolean("animations",false).commit();
        server=new MockWebServer();server.start();api=new Api(new Account(server.url("/").toString(),"user","test-token","Test","device"));db=new Library(app,"rating-visibility-"+UUID.randomUUID());
        ratings=new Ratings(app.settings,db,api,p->keys.getOrDefault(p,""),server.url("/omdb/").toString(),server.url("/mdb/").toString(),server.url("/tmdb/").toString());app.ratings=ratings;
    }
    @After public void close() throws Exception {ratings.close();app.ratings=null;db.close();server.shutdown();}
    private int measure(RatingsRow row) {row.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,700,row.getMeasuredHeight());return row.getMeasuredHeight();}
    @Test public void removingKeysHidesCachedScoresAndPlaceholdersWithoutMovingAnySlots() {
        keys.put("mdblist","test-key");RatingsRow row=new RatingsRow(app).singleLine();row.reserve(app);row.show(app,Json.obj("imdb",8.1,"mdblist",87));int height=measure(row);
        int[] right=new int[row.getChildCount()];for(int i=0;i<right.length;i++){right[i]=row.getChildAt(i).getRight();assertEquals(View.VISIBLE,row.getChildAt(i).getVisibility());}
        keys.clear();row.show(app,Json.obj("imdb",8.1,"mdblist",87));assertEquals(height,measure(row));
        for(int i=0;i<right.length;i++){assertEquals(View.INVISIBLE,row.getChildAt(i).getVisibility());assertEquals(right[i],row.getChildAt(i).getRight());}
        row.clear();assertEquals(height,measure(row));for(int i=0;i<right.length;i++)assertEquals(View.INVISIBLE,row.getChildAt(i).getVisibility());assertEquals(0,server.getRequestCount());
    }
    @Test public void providerKeysOnlyEnableTheScoresTheyCanSupplyAndRespectSwitches() {
        for(String id:Ratings.IDS)assertFalse(ratings.visible(id));keys.put("omdb","test-key");
        assertTrue(ratings.visible("imdb"));assertTrue(ratings.visible("tomatoes"));assertTrue(ratings.visible("metacritic"));assertFalse(ratings.visible("tmdb"));assertFalse(ratings.visible("letterboxd"));
        keys.put("tmdb","test-key");assertTrue(ratings.visible("tmdb"));app.settings.edit().putBoolean("rating-provider-tmdb",false).commit();assertFalse(ratings.visible("tmdb"));
        app.settings.edit().putBoolean("rating-show-imdb",false).commit();assertFalse(ratings.visible("imdb"));
    }
    @Test public void detailAndScreensaverKeepTheirHeightWhenNoKeysRemain() {
        keys.put("mdblist","test-key");
        for(RatingsRow row:new RatingsRow[]{RatingsRow.details(app),new RatingsRow(app,true)}) {
            row.show(app,Json.obj("imdb",8,"mdblist",87));int before=measure(row);keys.clear();row.show(app,Json.obj("imdb",8,"mdblist",87));assertEquals(before,measure(row));
            for(int i=0;i<row.getChildCount();i++)assertEquals(View.INVISIBLE,row.getChildAt(i).getVisibility());row.clear();assertEquals(before,measure(row));keys.put("mdblist","test-key");
        }
    }
    private void discovery(String providers) {
        server.enqueue(new MockResponse().setBody("{\"available\":true,\"apiVersion\":1,\"features\":[\"serverProviders\"]}"));server.enqueue(new MockResponse().setBody(providers));
    }
    @Test public void companionConfigurationHidesMissingAndRevokedServerKeysWithoutExposingSecrets() {
        CompanionApi bridge=new CompanionApi(app,api,db);ReflectionHelpers.setField(ratings,"companion",(Supplier<CompanionApi>)()->bridge);
        discovery("{\"omdb\":true,\"mdblist\":false,\"tmdb\":false}");assertTrue(bridge.discover());assertTrue(ratings.visible("imdb"));assertFalse(ratings.visible("tmdb"));assertFalse(ratings.visible("mdblist"));
        keys.put("mdblist","unused-local-key");assertFalse(ratings.visible("mdblist"));
        discovery("{\"omdb\":false,\"mdblist\":false,\"tmdb\":false}");assertTrue(bridge.refreshDiscovery());assertFalse(ratings.visible("imdb"));assertTrue(bridge.available());
    }
}
