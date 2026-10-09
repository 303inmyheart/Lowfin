package de.lowfin.tv;

import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class TvUiTest {
    private ActivityController<MainActivity> activity;
    private MockWebServer server;
    private LowfinApp app;
    private JSONObject series,movie;
    private byte[] posterImage,thumbImage;
    private volatile JSONArray recommendations=new JSONArray();
    private volatile boolean recommendationsOffline;
    @Before public void setup() throws Exception { RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putString("language","de").commit(); I18n.init(RuntimeEnvironment.getApplication());
        app=(LowfinApp)RuntimeEnvironment.getApplication(); series=Json.obj("Id","show","Type","Series","Name","Die lange Serie","Overview","45 Staffeln. Direkt zu deiner Lieblingsfolge.","ImageTags",Json.obj("Primary","poster"),"PrimaryImageAspectRatio",2.0/3);
        movie=Json.obj("Id","movie","Type","Movie","Name","Zwischen den Sternen","Overview","Eine Crew folgt einem rätselhaften Signal durch die Nacht. Als ihr Schiff die vertrauten Routen verlässt, wird aus der Suche nach Antworten eine Reise nach Hause.","ProductionYear",2025,"OfficialRating","FSK 12","RunTimeTicks",67200000000L,"Genres",Json.arr("Science-Fiction","Drama"),"ImageTags",Json.obj("Primary","poster"),"BackdropImageTags",Json.arr("backdrop"),"RemoteTrailers",Json.arr(Json.obj("Url","https://www.youtube.com/watch?v=M7lc1UVf-VE")));
        posterImage=fixture(240,360,"LOWFIN","AFTER HOURS"); thumbImage=fixture(480,270,"STAFFEL 40","Ein neuer Anfang");
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path=request.getRequestUrl().encodedPath();
                if(path.equals("/Movies/Recommendations")) return recommendationsOffline?new MockResponse().setResponseCode(503):new MockResponse().setBody(recommendations.toString());
                if(recommendationsOffline&&path.equals("/Users/user/Items")&&request.getRequestUrl().queryParameter("IsPlayed")!=null) return new MockResponse().setResponseCode(503);
                if(path.contains("/Images/")) return new MockResponse().setHeader("Content-Type","image/png").setBody(new okio.Buffer().write(path.contains("ep")||path.endsWith("Thumb")||path.endsWith("Backdrop")?thumbImage:posterImage));
                if(path.equals("/Users/user/Items/movie")) return new MockResponse().setBody(movie.toString());
                if(path.equals("/Genres")) return new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","genre","Name","Drama","MovieCount",1)),"TotalRecordCount",1).toString());
                if(path.equals("/Users/user/Views")) return new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","films","Name","Meine Filme","CollectionType","movies"),Json.obj("Id","shows","Name","Serien","CollectionType","tvshows"))).toString());
                if(path.equals("/LiveTv/Channels")) return new MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","channel","Name","Sender Eins","Type","TvChannel")),"TotalRecordCount",1).toString());
                if(path.equals("/Shows/show/Seasons")) {
                    JSONArray seasons=new JSONArray(); for(int i=1;i<=45;i++) seasons.put(Json.obj("Id","s"+i,"ParentId","show","SeriesId","show","Type","Season","IndexNumber",i,"Name","Staffel "+i,"RecursiveItemCount",24,"ImageTags",Json.obj("Primary","season-poster"),"PrimaryImageAspectRatio",2.0/3));
                    return new MockResponse().setBody(Json.obj("Items",seasons,"TotalRecordCount",45).toString());
                }
                if(path.equals("/Users/user/Items")&&"IsResumable".equals(request.getRequestUrl().queryParameter("Filters"))) return new MockResponse().setBody(Json.obj("Items",Json.arr(episode("ep5",5,6000000000L)),"TotalRecordCount",1).toString());
                if(path.equals("/Users/user/Items")) { boolean movies="Movie".equals(request.getRequestUrl().queryParameter("IncludeItemTypes"))||request.getRequestUrl().queryParameter("Genres")!=null; return new MockResponse().setBody(Json.obj("Items",Json.arr(movies?movie:series),"TotalRecordCount",1).toString()); }
                if(path.equals("/Users/user/Items/show")) return new MockResponse().setBody(series.toString());
                if(path.endsWith("/Items/Resume")) return new MockResponse().setBody(Json.obj("Items",Json.arr(episode("ep5",5,6000000000L)),"TotalRecordCount",1).toString());
                if(path.equals("/Shows/NextUp")) return new MockResponse().setBody(Json.obj("Items",Json.arr(episode("ep6",6,0)),"TotalRecordCount",1).toString());
                if(path.endsWith("/Episodes")) return new MockResponse().setBody("{\"Items\":[],\"TotalRecordCount\":0}");
                return new MockResponse().setBody("{}");
            }
        }); server.start();
    }
    @After public void stop() throws Exception {
        if(activity!=null) activity.pause().stop().destroy();
        if(app.images!=null) app.images.close(); if(app.ratings!=null) app.ratings.close(); app.io.shutdownNow(); app.cacheIo.shutdownNow(); app.navigationIo.shutdownNow(); app.reports.shutdownNow(); server.shutdown();
    }

    @Test public void navigationLoadsLibrariesEvenWhenContentWorkersAreBusy() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));
        app.library.state("lastSync",""+System.currentTimeMillis());
        java.util.concurrent.CountDownLatch occupied=new java.util.concurrent.CountDownLatch(4), release=new java.util.concurrent.CountDownLatch(1);
        for(int i=0;i<4;i++) app.io.execute(()-> { occupied.countDown(); try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(occupied.await(2,java.util.concurrent.TimeUnit.SECONDS));
        try {
            activity=Robolectric.buildActivity(MainActivity.class).setup();
            await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        } finally { release.countDown(); }
    }
    @Test public void settingsOpenWithoutWaitingForTheDatabaseOpeningLock() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        java.util.concurrent.CountDownLatch locked=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        Thread holder=new Thread(()-> { synchronized(app.library) { locked.countDown(); try { release.await(3,java.util.concurrent.TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } } }); holder.start();
        assertTrue(locked.await(2,java.util.concurrent.TimeUnit.SECONDS));
        try {
            long start=System.nanoTime(); findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick();
            assertTrue("Settings waited for SQLite",System.nanoTime()-start<2_000_000_000L);
            assertNotNull(findButton(activity.get().findViewById(android.R.id.content),"Videocache: 500 MB"));
        } finally { release.countDown(); holder.join(2000); }
    }
    @Test public void loginFitsAnHdScreenAndHasFocusableInputs() throws Exception {
        activity=Robolectric.buildActivity(MainActivity.class).setup(); layout();
        View root=activity.get().findViewById(android.R.id.content);
        Button login=findButton(root,"Anmelden"); assertNotNull(login); assertTrue(login.isFocusable());
        assertTrue(login.getBottom()<720); screenshot("login-hd.png");
    }
    @Test public void fortyFiveSeasonsAreVirtualizedAndStaffelFortyCanOpen() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); app.library.put(series);
        activity=Robolectric.buildActivity(MainActivity.class).setup(); layout();
        findButton(activity.get().findViewById(android.R.id.content),"Serien").performClick();
        await(()-> { RecyclerView r=recycler(); return r!=null&&r.getAdapter().getItemCount()==1; }); layout();
        RecyclerView grid=recycler(); assertTrue(grid.getChildCount()>0); grid.getChildAt(0).performClick();
        await(()-> { RecyclerView r=recycler(); return r!=null&&r.getAdapter().getItemCount()==45; });
        layout(); RecyclerView seasons=recycler(); assertEquals(45,seasons.getAdapter().getItemCount()); assertEquals(8,((GridLayoutManager)seasons.getLayoutManager()).getSpanCount()); assertTrue(seasons.getChildCount()<45);
        LinearLayoutManager manager=(LinearLayoutManager)seasons.getLayoutManager();
        EditText jump=(EditText)find(activity.get().findViewById(android.R.id.content),v->v instanceof EditText); jump.setText("40");
        findButton(activity.get().findViewById(android.R.id.content),"Zur Staffel").performClick(); layout();
        View row=manager.findViewByPosition(39); assertNotNull(row); assertNotNull(findText(row,"Staffel 40"));
        ArtworkView cover=(ArtworkView)find(row,v->v instanceof ArtworkView); assertNotNull(cover); assertEquals(2.0/3,(double)cover.getMeasuredWidth()/cover.getMeasuredHeight(),.01); assertTrue(row.isFocusable()); row.requestFocus();
        await(()->cover.getDrawable()!=null); screenshot("seasons-40-hd.png"); row.performClick(); layout();
        assertNotNull(findText(activity.get().findViewById(android.R.id.content),"Staffel 40"));
    }
    @Test public void homeShowsResumeAndNextUpCardsAndResumesAtSavedPosition() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()-> { RecyclerView r=recycler(); return r!=null&&r.getAdapter() instanceof HomeAdapter&&((HomeAdapter)r.getAdapter()).rows.get(0).size()==1&&((HomeAdapter)r.getAdapter()).rows.get(1).size()==1; });
        layout(); RecyclerView home=recycler(); HomeAdapter adapter=(HomeAdapter)home.getAdapter();
        assertEquals("ep5",adapter.rows.get(0).get(0).optString("Id")); assertEquals("ep6",adapter.rows.get(1).get(0).optString("Id"));
        assertNotNull(findText(home,"Weiterschauen"));
        await(()-> { ArtworkView image=(ArtworkView)find(home,v->v instanceof ArtworkView); return image!=null&&image.getDrawable()!=null; }); screenshot("home-hd.png");
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)home.findViewHolderForAdapterPosition(0); assertNotNull(shelf); assertTrue(shelf.list.getChildCount()>0);
        await(()->shelf.list.getChildAt(0).hasFocus());
        shelf.list.getChildAt(0).performClick(); android.content.Intent intent=shadowOf(activity.get()).getNextStartedActivity(); assertNotNull(intent);
        assertEquals(600000L,intent.getLongExtra("position",-1)); assertEquals("ep5",new JSONObject(intent.getStringExtra("item")).optString("Id"));
    }
    @Test public void seriesLibraryHasScopedResumeTabAndLaunchesAtSavedPosition() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        View root=activity.get().findViewById(android.R.id.content);
        findButton(root,"Meine Filme").performClick(); assertNull(findButton(root,"Begonnene Folgen"));
        findButton(root,"Serien").performClick(); assertNotNull(findButton(root,"Alle Serien")); findButton(root,"Begonnene Folgen").performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1);
        recycler().getChildAt(0).performClick(); android.content.Intent intent=shadowOf(activity.get()).getNextStartedActivity();
        assertEquals(PlayerActivity.class.getName(),intent.getComponent().getClassName()); assertEquals(600000L,intent.getLongExtra("position",-1));
        String cached=app.library.state("seriesResume-shows-0"); assertTrue(cached.contains("ep5"));
        boolean scoped=false; okhttp3.mockwebserver.RecordedRequest request;
        while((request=server.takeRequest(100,java.util.concurrent.TimeUnit.MILLISECONDS))!=null) if("IsResumable".equals(request.getRequestUrl().queryParameter("Filters"))) { assertEquals("shows",request.getRequestUrl().queryParameter("ParentId")); assertEquals("Episode",request.getRequestUrl().queryParameter("IncludeItemTypes")); scoped=true; }
        assertTrue(scoped);
    }
    @Test public void moreIsAPosterAtRowEndAndOpensTheCorrectListWithoutHomeActionButtons() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter&&((HomeAdapter)recycler().getAdapter()).rows.get(1).size()==1);
        layout(); View root=activity.get().findViewById(android.R.id.content);
        assertNull(findText(recycler(),"Neu in deiner Bibliothek"));
        assertFalse(findButton(root,"Zufälliger Film").isShown()); assertFalse(findButton(root,"Genres anheften").isShown());
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); adapter.requestRow(1); layout();
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1);
        assertEquals(2,shelf.list.getAdapter().getItemCount()); View more=shelf.list.getLayoutManager().findViewByPosition(1); assertNotNull(more);
        ArtworkView image=(ArtworkView)find(more,v->v instanceof ArtworkView); assertEquals(16.0/9,(double)image.getMeasuredWidth()/image.getMeasuredHeight(),.01);
        assertEquals("Mehr anzeigen",more.getContentDescription().toString()); more.performClick(); layout();
        assertNotNull(findText(root,"Als Nächstes")); assertTrue(recycler().getAdapter() instanceof MediaAdapter);
    }
    @Test public void sidebarUsesActualLibraryNamesAndLiveTvOnlyShowsChannels() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        View root=activity.get().findViewById(android.R.id.content);
        assertNotNull(findButton(root,"Suche")); findButton(root,"Live TV").performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1);
        assertNull(findButton(root,"Programm")); assertNull(findButton(root,"Aufnahmen"));
        screenshot("live-channels-hd.png");
    }
    @Test public void movieDetailUsesPortraitPosterAndCompactActions() throws Exception {
        movie.put("MediaSources",Json.arr(Json.obj("Id","file-de","MediaStreams",Json.arr(Json.obj("Type","Audio","Index",3,"DisplayTitle","Deutsch 5.1")))));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); layout();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1); recycler().getChildAt(0).performClick();
        View root=activity.get().findViewById(android.R.id.content);
        await(()->findButton(root,"▶ Abspielen")!=null); layout();
        ArtworkView cover=(ArtworkView)find(root,v->v instanceof ArtworkView);
        assertEquals(2.0/3,(double)cover.getMeasuredWidth()/cover.getMeasuredHeight(),.01);
        assertTrue(findButton(root,"▶ Abspielen").getHeight()<65); assertNotNull(findButton(root,"Als gesehen"));
        BackdropView backdrop=(BackdropView)find(root,v->v instanceof BackdropView); assertNotNull(backdrop);
        await(()->cover.getDrawable()!=null&&backdrop.getDrawable()!=null); screenshot("movie-detail-hd.png");
        Button play=findButton(root,"▶ Abspielen"),audio=findButton(root,"Tonspur"),watched=findButton(root,"Als gesehen");
        assertNotNull(audio);
        watched.requestFocus(); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT)); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT));
        assertTrue(findButton(root,"☆ Merken").hasFocus());
        assertEquals(0,((View)backdrop.getParent()).getLeft()); assertEquals(0,((View)backdrop.getParent()).getTop());
        assertSame(play.getParent(),audio.getParent()); assertSame(play.getParent(),watched.getParent());
        audio.performClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(dialog.getListView().getChildAt(1),1,1);
        assertNotNull(findButton(root,"Tonspur · Deutsch 5.1"));
        findButton(root,"▶ Abspielen").performClick(); android.content.Intent intent=shadowOf(activity.get()).getNextStartedActivity();
        assertEquals(3,intent.getIntExtra("audioStreamIndex",-1)); assertEquals("file-de",intent.getStringExtra("mediaSourceId"));
    }
    @Test public void episodeThumbnailsKeepTheirLandscapeRatio() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter&&((HomeAdapter)recycler().getAdapter()).rows.get(0).size()==1);
        layout(); HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);
        ArtworkView cover=(ArtworkView)find(shelf.list,v->v instanceof ArtworkView); assertNotNull(cover);
        assertEquals(16.0/9,(double)cover.getMeasuredWidth()/cover.getMeasuredHeight(),.015); assertEquals(ImageView.ScaleType.CENTER_CROP,cover.getScaleType());
    }
    @Test public void randomButtonUsesEntireSelectedLibraryAndThenOpensFilmDetail() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1);
        findButton(activity.get().findViewById(android.R.id.content),"Zufälliger Film").performClick();
        long end=System.currentTimeMillis()+5000; RecordedRequest random=null;
        while(System.currentTimeMillis()<end) { RecordedRequest request=server.takeRequest(100,java.util.concurrent.TimeUnit.MILLISECONDS); if(request!=null&&"Random".equals(request.getRequestUrl().queryParameter("SortBy"))&&"80".equals(request.getRequestUrl().queryParameter("Limit"))) { random=request; break; } }
        assertNotNull(random); assertEquals("films",random.getRequestUrl().queryParameter("ParentId")); assertEquals("80",random.getRequestUrl().queryParameter("Limit"));
        assertEquals("Movie",random.getRequestUrl().queryParameter("IncludeItemTypes"));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3)); layout();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"▶ Abspielen")!=null);
    }
    @Test public void genreSidebarFiltersFilmsAndReturnsToGenreList() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); layout();
        findButton(activity.get().findViewById(android.R.id.content),"Genres").performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof GenreAdapter&&recycler().getAdapter().getItemCount()==1);
        screenshot("genres-hd.png"); recycler().getChildAt(0).performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&recycler().getAdapter().getItemCount()==1);
        long end=System.currentTimeMillis()+3000; RecordedRequest filtered=null;
        while(System.currentTimeMillis()<end) { RecordedRequest request=server.takeRequest(50,java.util.concurrent.TimeUnit.MILLISECONDS); if(request!=null&&"Drama".equals(request.getRequestUrl().queryParameter("Genres"))) { filtered=request; break; } }
        assertNotNull(filtered); assertEquals("Drama",filtered.getRequestUrl().queryParameter("Genres")); assertEquals("Movie,Series",filtered.getRequestUrl().queryParameter("IncludeItemTypes"));
        activity.get().onBackPressed(); await(()->recycler()!=null&&recycler().getAdapter() instanceof GenreAdapter);
    }

    private void recommendationFixture() throws Exception {
        JSONArray suggestions=new JSONArray();
        for(int i=0;i<24;i++) { JSONObject suggestion=new JSONObject(movie.toString()); suggestion.put("Id","suggestion"+i); suggestions.put(suggestion); }
        recommendations=Json.arr(Json.obj("BaselineItemName","Alien","RecommendationType","SimilarToRecentlyPlayed","Items",suggestions),Json.obj("BaselineItemName","The Thing","RecommendationType","SimilarToRecentlyPlayed","Items",Json.arr(movie)),Json.obj("BaselineItemName","Halloween","RecommendationType","SimilarToRecentlyPlayed","Items",Json.arr(movie)),Json.obj("BaselineItemName","Scream","RecommendationType","SimilarToRecentlyPlayed","Items",Json.arr(movie)),Json.obj("BaselineItemName","The Shining","RecommendationType","SimilarToRecentlyPlayed","Items",Json.arr(movie)));
    }
    @Test public void fiveRecommendationShelvesSitAfterNextUpBeforeGenresAndOpenDetailsAndMore() throws Exception {
        recommendationFixture(); app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&((HomeAdapter)recycler().getAdapter()).rows.get(2).size()==24&&((HomeAdapter)recycler().getAdapter()).rows.get(3).size()==1);
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); assertEquals("Als Nächstes",adapter.labels.get(1)); assertEquals("Weil du „Alien“ gesehen hast",adapter.labels.get(2)); assertEquals("Weil du „The Thing“ gesehen hast",adapter.labels.get(3)); assertEquals("Horror",adapter.labels.get(7)); assertEquals("Weil du „The Shining“ gesehen hast",adapter.labels.get(6)); assertEquals(1,adapter.rows.get(6).size());
        adapter.requestRow(1); layout(); HomeAdapter.Shelf next=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1);
        next.list.findFocus().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2); return h!=null&&h.itemView.isShown()&&h.list.hasFocus(); });
        HomeAdapter.Shelf first=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2); ArtworkView cover=(ArtworkView)find(first.list.getChildAt(0),v->v instanceof ArtworkView);
        assertEquals(2.0/3,(double)cover.getMeasuredWidth()/cover.getMeasuredHeight(),.01); await(()->cover.getDrawable()!=null);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300)); layout();
        TextView overview=findText(activity.get().findViewById(android.R.id.content),movie.optString("Overview")); assertNotNull(overview); assertTrue(overview.isShown());
        RatingsRow badges=(RatingsRow)find(activity.get().findViewById(android.R.id.content),v->v instanceof RatingsRow); badges.clear(); badges.show(app,Json.obj("imdb",7.8,"tomatoes",91,"tomatoesaudience",94,"metacritic",82,"tmdb",8.2,"trakt",88,"letterboxd",4.2,"mdblist",87)); layout(); assertEquals(8,badges.getChildCount());
        for(int i=0;i<recycler().getChildCount();i++) { View other=recycler().getChildAt(i); if(recycler().getChildAdapterPosition(other)!=2) assertEquals(View.INVISIBLE,other.getVisibility()); }
        int[] xy=new int[2]; overview.getLocationOnScreen(xy); assertTrue(xy[1]+overview.getHeight()<=720);
        BackdropView backdrop=(BackdropView)find(activity.get().findViewById(android.R.id.content),v->v instanceof BackdropView); await(()->backdrop.getDrawable()!=null);
        screenshot("home-recommendations-hd.png");
        first.list.findFocus().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(3); return h!=null&&h.itemView.isShown()&&h.list.hasFocus(); });
        adapter.requestRow(6); layout();
        HomeAdapter.Shelf second=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(6); second.list.findFocus().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7); return h!=null&&h.itemView.isShown()&&h.list.hasFocus(); });
        HomeAdapter.Shelf genre=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7); genre.list.findFocus().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(6); return h!=null&&h.itemView.isShown()&&h.list.hasFocus(); });
        second=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(6); second.list.getChildAt(0).performClick();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"▶ Abspielen")!=null); assertNull(shadowOf(activity.get()).getNextStartedActivity());
        activity.get().onBackPressed(); await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter&&((HomeAdapter)recycler().getAdapter()).rows.get(2).size()==24);
        adapter=(HomeAdapter)recycler().getAdapter(); adapter.requestRow(2); layout(); first=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2); ((HomeAdapter.ShelfList)first.list).requestCard(24);
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2); View more=h.list.getLayoutManager().findViewByPosition(24); return more!=null&&more.hasFocus(); });
        first=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2); first.list.findFocus().performClick(); layout(); assertTrue(recycler().getAdapter() instanceof MediaAdapter); assertEquals(24,recycler().getAdapter().getItemCount());
    }
    @Test public void emptyRecommendationShelvesAreSkippedBetweenNextUpAndGenres() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&((HomeAdapter)recycler().getAdapter()).rows.get(1).size()==1&&((HomeAdapter)recycler().getAdapter()).rows.get(7).size()==1);
        ((HomeAdapter)recycler().getAdapter()).requestRow(1); layout(); HomeAdapter.Shelf next=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1); next.list.findFocus().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7); return h!=null&&h.itemView.isShown()&&h.list.hasFocus(); });
        HomeAdapter.Shelf genre=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7); genre.list.findFocus().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1); return h!=null&&h.itemView.isShown()&&h.list.hasFocus(); });
    }
    @Test public void recommendationsSurviveActivityRestartWhenServerIsUnavailable() throws Exception {
        recommendationFixture(); app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&((HomeAdapter)recycler().getAdapter()).rows.get(3).size()==1); assertFalse(app.library.state("homeRecommendations").isEmpty());
        activity.pause().stop().destroy(); recommendationsOffline=true;
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&((HomeAdapter)recycler().getAdapter()).rows.get(2).size()==24&&((HomeAdapter)recycler().getAdapter()).rows.get(3).size()==1);
        assertEquals("Weil du „Alien“ gesehen hast",((HomeAdapter)recycler().getAdapter()).labels.get(2));
        // Reopen the on-disk database to check that no in-memory adapter cache is required.
        Library reopened=new Library(app,app.account.scope()); assertEquals(5,new JSONArray(reopened.state("homeRecommendations")).length()); reopened.close();
    }

    @Test public void pinnedGenreHasOneVisibleShelfDescriptionAndBackdropAndRightStopsAtEnd() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()-> { RecyclerView r=recycler(); return r!=null&&r.getAdapter() instanceof HomeAdapter&&((HomeAdapter)r.getAdapter()).rows.get(7).size()==1&&((HomeAdapter)r.getAdapter()).rows.get(8).size()==1; });
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); assertEquals(9,adapter.getItemCount()); assertEquals("Horror",adapter.labels.get(7)); assertEquals("Thriller",adapter.labels.get(8));
        adapter.requestRow(7); layout();
        await(()->findText(activity.get().findViewById(android.R.id.content),movie.optString("Overview"))!=null&&findText(activity.get().findViewById(android.R.id.content),movie.optString("Overview")).isShown());
        layout(); HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7); View card=shelf.list.getChildAt(0); assertNotNull(card); assertTrue(card.hasFocus()); assertTrue("Shelf clips card metadata",card.getBottom()<=shelf.list.getHeight());
        View more=shelf.list.focusSearch(card,View.FOCUS_RIGHT); assertNotSame(card,more); assertEquals("Mehr anzeigen",more.getContentDescription().toString()); assertSame(more,shelf.list.focusSearch(more,View.FOCUS_RIGHT));
        ArtworkView image=(ArtworkView)find(card,v->v instanceof ArtworkView); assertEquals(2.0/3,(double)image.getMeasuredWidth()/image.getMeasuredHeight(),.01);
        for(int i=0;i<recycler().getChildCount();i++) { View row=recycler().getChildAt(i); if(recycler().getChildAdapterPosition(row)!=7) assertEquals(View.INVISIBLE,row.getVisibility()); }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250));
        await(()-> { BackdropView backdrop=(BackdropView)find(activity.get().findViewById(android.R.id.content),v->v instanceof BackdropView); return backdrop!=null&&backdrop.getDrawable()!=null; });
        screenshot("home-genre-hd.png");
        card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)); layout();
        await(()-> { HomeAdapter.Shelf next=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(8); return next!=null&&next.itemView.isShown()&&next.list.hasFocus(); });
        for(int i=0;i<recycler().getChildCount();i++) { View row=recycler().getChildAt(i); if(recycler().getChildAdapterPosition(row)!=8) assertEquals(View.INVISIBLE,row.getVisibility()); }
    }
    @Test public void genrePinsPersistAndCanBeRemovedFromTheHomePage() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        findButton(activity.get().findViewById(android.R.id.content),"Genres").performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof GenreAdapter&&recycler().getAdapter().getItemCount()==1);
        recycler().getChildAt(0).performClick(); layout();
        findButton(activity.get().findViewById(android.R.id.content),"Auf Startseite anheften").performClick();
        assertTrue(HomeGenres.read(app.settings,app.account).contains("Drama"));
        findButton(activity.get().findViewById(android.R.id.content),"Start").performClick();
        assertEquals(10,((HomeAdapter)recycler().getAdapter()).getItemCount());
        findButton(activity.get().findViewById(android.R.id.content),"Genres").performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof GenreAdapter&&recycler().getAdapter().getItemCount()==1); recycler().getChildAt(0).performClick(); layout();
        findButton(activity.get().findViewById(android.R.id.content),"Von Startseite lösen").performClick(); assertFalse(HomeGenres.read(app.settings,app.account).contains("Drama"));
    }
    @Test public void drawerStartsHiddenOpensWithButtonAndClosesOnRightReturningToContent() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()-> { RecyclerView r=recycler(); return r!=null&&((HomeAdapter)r.getAdapter()).rows.get(0).size()==1; });
        View root=activity.get().findViewById(android.R.id.content); assertFalse(findButton(root,"Start").isShown());
        int fullWidth=recycler().getWidth(); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU)); layout(); assertTrue(findButton(root,"Start").isShown());
        screenshot("drawer-hd.png"); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200)); layout();
        assertFalse("Drawer still visible",findButton(root,"Start").isShown()); assertTrue(Math.abs(fullWidth-recycler().getWidth())<=Ui.dp(activity.get(),2)); assertTrue("Content did not regain focus",recycler().hasFocus());
    }
    @Test public void filmDetailTrailerStartsInsideLowfinWithoutAJellyfinToken() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick(); await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1);
        recycler().getChildAt(0).performClick(); await(()->findButton(activity.get().findViewById(android.R.id.content),"▶ Trailer")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"▶ Trailer").performClick(); android.content.Intent intent=shadowOf(activity.get()).getNextStartedActivity();
        assertEquals(TrailerActivity.class.getName(),intent.getComponent().getClassName()); assertEquals("M7lc1UVf-VE",intent.getStringExtra("videoId")); assertFalse(intent.getExtras().containsKey("token"));
    }

    @Test public void compactPosterGridOpensMenuOnLeftAndReturnsToTheSameCard() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick(); await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1); layout();
        assertEquals(8,((GridLayoutManager)recycler().getLayoutManager()).getSpanCount());
        View card=recycler().getChildAt(0); card.requestFocus(); ArtworkView cover=(ArtworkView)find(card,v->v instanceof ArtworkView);
        assertTrue(cover.getMeasuredWidth()<=Ui.dp(activity.get(),125)); assertEquals(2.0/3,(double)cover.getMeasuredWidth()/cover.getMeasuredHeight(),.01); assertEquals(ImageView.ScaleType.CENTER_CROP,cover.getScaleType());
        screenshot("films-compact-hd.png");
        card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT)); layout();
        assertSame(findButton(activity.get().findViewById(android.R.id.content),"Meine Filme"),activity.get().findViewById(android.R.id.content).findFocus());
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));
        assertSame(findButton(activity.get().findViewById(android.R.id.content),"Genres anheften"),activity.get().findViewById(android.R.id.content).findFocus());
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        assertSame(findButton(activity.get().findViewById(android.R.id.content),"Meine Filme"),activity.get().findViewById(android.R.id.content).findFocus());
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));
        assertSame(card,activity.get().findViewById(android.R.id.content).findFocus());
    }
    @Test public void movingBetweenHomeRowsRetainsTheHorizontalSelection() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&((HomeAdapter)recycler().getAdapter()).rows.get(7).size()==1&&((HomeAdapter)recycler().getAdapter()).rows.get(0).size()==1&&((HomeAdapter)recycler().getAdapter()).rows.get(1).size()==1);
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter();
        adapter.update(0,java.util.Arrays.asList(episode("a0",1,0),episode("a1",2,0),episode("a2",3,0)));
        adapter.update(1,java.util.Arrays.asList(episode("b0",1,0),episode("b1",2,0),episode("b2",3,0))); layout();
        HomeAdapter.Shelf first=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0); View selected=first.list.getLayoutManager().findViewByPosition(2); assertNotNull(selected); selected.requestFocus();
        selected.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        await(()-> { HomeAdapter.Shelf next=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1); return next!=null&&next.list.getLayoutManager().findViewByPosition(2)!=null&&next.list.getLayoutManager().findViewByPosition(2).hasFocus(); });
        HomeAdapter.Shelf next=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1); next.list.getLayoutManager().findViewByPosition(2).dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));
        await(()-> { HomeAdapter.Shelf back=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0); return back!=null&&back.list.getLayoutManager().findViewByPosition(2)!=null&&back.list.getLayoutManager().findViewByPosition(2).hasFocus(); });
    }

    @Test public void backFromSettingsReturnsHomeBeforeExitingAndBackClosesAnOpenMenuFirst() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick(); activity.get().onBackPressed(); layout();
        assertFalse(activity.get().isFinishing()); assertTrue(recycler().getAdapter() instanceof HomeAdapter);
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU)); activity.get().onBackPressed();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200)); layout();
        assertFalse(activity.get().isFinishing()); assertFalse(findButton(activity.get().findViewById(android.R.id.content),"Start").isShown());
        activity.get().onBackPressed(); assertFalse(activity.get().isFinishing()); android.app.AlertDialog exit=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); assertTrue(exit.isShowing()); exit.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); assertTrue(activity.get().isFinishing());
    }
    @Test public void genreAndDetailsRatingsWrapInsideHdContentAndRespectSourceSelection() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&((HomeAdapter)recycler().getAdapter()).rows.get(7).size()==1);
        ((HomeAdapter)recycler().getAdapter()).requestRow(7); layout(); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300)); layout();
        JSONObject scores=Json.obj("imdb",7.8,"tomatoes",91,"tomatoesaudience",94,"metacritic",82,"tmdb",8.2,"trakt",88,"letterboxd",4.2,"mdblist",87);
        RatingsRow badges=(RatingsRow)find(activity.get().findViewById(android.R.id.content),v->v instanceof RatingsRow); badges.show(app,scores); layout(); assertEquals(8,badges.getChildCount());
        for(int i=0;i<badges.getChildCount();i++) { View child=badges.getChildAt(i); assertTrue(child.getRight()<=badges.getWidth()); assertTrue(child.getBottom()<=badges.getHeight()); }
        screenshot("home-genre-ratings-hd.png");
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7); shelf.list.getChildAt(0).performClick(); layout();
        badges=(RatingsRow)find(activity.get().findViewById(android.R.id.content),v->v instanceof RatingsRow); badges.show(app,scores); layout(); screenshot("details-ratings-hd.png");
        app.settings.edit().putBoolean("rating-show-tmdb",false).apply(); badges.show(app,scores); assertEquals(7,badges.getChildCount());
    }
    @Test public void genreDescriptionFitsHdAndNavigationReachesOffscreenPostersAndLastRow() throws Exception {
        movie.put("Overview", "Eine sehr lange Beschreibung. ".repeat(150));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&((HomeAdapter)recycler().getAdapter()).rows.get(7).size()==1);
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); java.util.List<JSONObject> films=new java.util.ArrayList<>();
        for(int i=0;i<30;i++) { JSONObject film=new JSONObject(movie.toString()); film.put("Id","random"+i); films.add(film); }
        adapter.update(7,films); adapter.requestRow(7); layout();
        for(int column=1;column<30;column++) {
            HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7);
            View focused=shelf.list.findFocus(); assertNotNull(focused); focused.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
            final int target=column; await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7); View v=h.list.getLayoutManager().findViewByPosition(target); return v!=null&&v.hasFocus(); });
        }
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7);
        shelf.list.findFocus().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(8); return h!=null&&h.list.hasFocus(); });
        HomeAdapter.Shelf last=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(8); assertTrue(last.itemView.isShown()); assertEquals(0,last.itemView.getTop());
        TextView overview=findText(activity.get().findViewById(android.R.id.content),movie.optString("Overview")); assertNotNull(overview);
        RatingsRow badges=(RatingsRow)find(activity.get().findViewById(android.R.id.content),v->v instanceof RatingsRow);
        badges.clear(); badges.show(app,Json.obj("imdb",7.8,"tomatoes",91,"tomatoesaudience",94,"metacritic",82,"tmdb",8.2,"trakt",88,"letterboxd",4.2,"mdblist",87)); layout();
        int[] xy=new int[2]; overview.getLocationOnScreen(xy); assertTrue(overview.getHeight()>overview.getLineHeight()); assertTrue(xy[1]+overview.getHeight()<=720);
        assertTrue(overview.getMaxLines()*overview.getLineHeight()<=overview.getHeight()); assertEquals(android.text.TextUtils.TruncateAt.END,overview.getEllipsize());
        screenshot("home-genre-description-hd.png");
    }

    private HomeAdapter stressHome() throws Exception {
        recommendationFixture(); app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        app.settings.edit().putString("homeGenres-"+app.account.scope(),"[\"Horror\",\"Thriller\",\"Drama\",\"Fantasy\",\"Action\",\"Animation\"]").apply();
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()-> { if(recycler()==null) return false; HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); if(adapter.rows.size()!=13) return false; for(java.util.List<JSONObject> row:adapter.rows) if(row.isEmpty()) return false; return true; });
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter();
        for(int row=2;row<13;row++) adapter.update(row,stressFilms(row,40)); layout(); return adapter;
    }
    private java.util.List<JSONObject> stressFilms(int row,int count) throws Exception {
        java.util.List<JSONObject> films=new java.util.ArrayList<>();
        for(int column=0;column<count;column++) { JSONObject film=new JSONObject(movie.toString()); film.put("Id","row"+row+"film"+column); film.put("Name","Reihe "+row+" · Film "+column); film.put("Overview","Beschreibung für Reihe "+row+" und Film "+column); films.add(film); }
        return films;
    }
    private void awaitShelf(int row,int column) throws Exception {
        await(()-> { HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(row); if(shelf==null||!shelf.itemView.isShown()) return false; View card=shelf.list.getLayoutManager().findViewByPosition(column); return card!=null&&card.hasFocus(); });
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(row); assertEquals("Focused shelf drifted vertically",0,shelf.itemView.getTop());
        for(int i=0;i<recycler().getChildCount();i++) { View other=recycler().getChildAt(i); if(recycler().getChildAdapterPosition(other)!=row) assertEquals(View.INVISIBLE,other.getVisibility()); }
    }
    private void remote(int key,int count) {
        for(int i=0;i<count;i++) { activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key)); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key)); }
    }
    @Test public void rapidRemoteNavigationKeepsAllRecommendationsAndGenresAlignedAndRestoresDrawerFocus() throws Exception {
        HomeAdapter adapter=stressHome(); adapter.requestRow(7); layout(); awaitShelf(7,0);
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,20); awaitShelf(7,20);
        remote(KeyEvent.KEYCODE_DPAD_DOWN,20); awaitShelf(12,20);
        for(int cycle=0;cycle<3;cycle++) {
            remote(KeyEvent.KEYCODE_DPAD_UP,10); awaitShelf(2,20);
            remote(KeyEvent.KEYCODE_DPAD_DOWN,10); awaitShelf(12,20);
        }
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,25); awaitShelf(12,40); remote(KeyEvent.KEYCODE_DPAD_RIGHT,5); awaitShelf(12,40);
        remote(KeyEvent.KEYCODE_DPAD_LEFT,40); awaitShelf(12,0); remote(KeyEvent.KEYCODE_DPAD_LEFT,1); layout(); assertTrue(findButton(activity.get().findViewById(android.R.id.content),"Start").isShown());
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,1); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200)); layout(); awaitShelf(12,0);
        activity.get().onBackPressed(); await(()-> { HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0); return shelf!=null&&shelf.list.hasFocus(); }); assertFalse(activity.get().isFinishing());
    }
    @Test public void changingAFocusedGenreDuringPendingHorizontalScrollClampsFocusToTheNewRow() throws Exception {
        HomeAdapter adapter=stressHome(); adapter.requestRow(7); layout(); awaitShelf(7,0);
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,35); adapter.update(7,stressFilms(7,3)); awaitShelf(7,3);
        remote(KeyEvent.KEYCODE_DPAD_LEFT,1); awaitShelf(7,2); assertTrue(findText(activity.get().findViewById(android.R.id.content),"Beschreibung für Reihe 7 und Film 2").isShown());
        remote(KeyEvent.KEYCODE_DPAD_DOWN,1); awaitShelf(8,2); remote(KeyEvent.KEYCODE_DPAD_UP,1); awaitShelf(7,2);
    }

    @Test public void exitCanBeCanceledAndMenuHasLiveAndRandomAboveLibrariesWithoutHeadingButton() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null); layout();
        View root=activity.get().findViewById(android.R.id.content); assertNull(findButton(root,"☰ Menü"));
        activity.get().onBackPressed(); android.app.AlertDialog dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        shadowOf(Looper.getMainLooper()).idle(); assertTrue("Cancel should have focus, actual: "+dialog.getWindow().getDecorView().findFocus(),dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).hasFocus()); dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle(); assertFalse(activity.get().isFinishing());
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU)); layout();
        Button film=findButton(root,"Meine Filme"),live=findButton(root,"Live TV"),random=findButton(root,"Zufälliger Film"); assertTrue(live.getTop()<film.getTop()); assertTrue(random.getTop()<film.getTop());
    }
    @Test public void settingsExposeAllFiveRecommendationChannelActivationButtons() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick(); layout(); View root=activity.get().findViewById(android.R.id.content);
        assertNotNull(findButton(root,"TV-Kanal aktivieren · Fortsetzen"));
        for(int i=1;i<=5;i++) assertNotNull(findButton(root,"TV-Kanal aktivieren · Film-Empfehlungen "+i));
    }
    @Test public void ratingSettingsShowAllThreeKeyFieldsAndSourceToggles() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick(); findButton(activity.get().findViewById(android.R.id.content),"Bewertungen · Anbieter und API-Keys").performClick(); layout();
        View root=activity.get().findViewById(android.R.id.content); assertNotNull(findButton(root,"OMDb · API-Key bearbeiten")); assertNotNull(findButton(root,"MDBList · API-Key bearbeiten")); assertNotNull(findButton(root,"TMDB · API-Key bearbeiten"));
        findButton(root,"IMDb: Ein").performClick(); assertNotNull(findButton(root,"IMDb: Aus")); screenshot("rating-settings-hd.png");
        findButton(root,"OMDb · API-Key bearbeiten").performClick(); android.app.AlertDialog dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); assertNotNull(find(dialog.getWindow().getDecorView(),v->v instanceof EditText));
        assertNotNull(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)); dialog.dismiss();
    }
    private byte[] fixture(int width,int height,String label,String title) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap); Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0,0,width,height,new int[]{0xff3a414b,0xff15161a},null,Shader.TileMode.CLAMP)); canvas.drawRect(0,0,width,height,paint); paint.setShader(null);
        paint.setColor(0xff8995a4); paint.setStrokeWidth(2); paint.setStyle(Paint.Style.STROKE); canvas.drawRect(4,4,width-4,height-4,paint); canvas.drawCircle(width*.68f,height*.34f,width*.2f,paint); paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); paint.setTextSize(width*.065f); canvas.drawText(label,width*.08f,height*.13f,paint); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextSize(width*.09f); canvas.drawText(title,width*.08f,height*.82f,paint);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes); bitmap.recycle(); return bytes.toByteArray();
    }
    private JSONObject episode(String id,int index,long position) { return Json.obj("Id",id,"Type","Episode","Name",index==5?"Der Rückweg":"Ein neuer Anfang","SeriesName","Die lange Serie","SeriesId","show","ParentIndexNumber",40,"IndexNumber",index,"RunTimeTicks",27000000000L,"ImageTags",Json.obj("Primary","episode-thumb"),"PrimaryImageAspectRatio",4.0/3,"UserData",Json.obj("PlaybackPositionTicks",position)); }
    @Test public void launcherSeriesLinkOpensItsSeasonsDirectly() throws Exception {
        Account account=new Account(server.url("/").toString(),"user","token","Rico","device"); app.setAccount(account); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class,HomeChannels.link(app,account,"show","open")).setup();
        await(()-> { RecyclerView r=recycler(); return r!=null&&r.getAdapter().getItemCount()==45; });
        assertNotNull(findText(activity.get().findViewById(android.R.id.content),"Die lange Serie"));
    }
    @Test public void launcherLinkFromAnotherAccountCannotStartPlayback() throws Exception {
        Account account=new Account(server.url("/").toString(),"user","token","Rico","device"); app.setAccount(account); app.library.state("lastSync",""+System.currentTimeMillis());
        Account other=new Account(server.url("/").toString(),"different-user","token","Other","device");
        activity=Robolectric.buildActivity(MainActivity.class,HomeChannels.link(app,other,"show","resume")).setup(); layout();
        assertNull(shadowOf(activity.get()).getNextStartedActivity());
    }
    private void await(BooleanSupplier condition) throws Exception {
        long end=System.currentTimeMillis()+8000;
        while(System.currentTimeMillis()<end) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20)); layout(); if(condition.getAsBoolean()) return; Thread.sleep(20); }
        fail("UI data did not arrive");
    }
    private void layout() {
        shadowOf(Looper.getMainLooper()).idle(); View root=activity.get().getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY)); root.layout(0,0,1280,720); shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void disabledHomeRowsAreSkippedAndRecommendationCountBoundsNavigation() throws Exception {
        recommendationFixture(); app.settings.edit().putBoolean("homeResume",false).putBoolean("homeNext",false).putBoolean("genreRows",false).putInt("recommendationRows",2).apply();
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter&&!((HomeAdapter)recycler().getAdapter()).rows.get(2).isEmpty()); HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); assertEquals(7,adapter.getItemCount()); adapter.requestRow(0);
        await(()-> { layout(); HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2); return shelf!=null&&shelf.list.hasFocus(); });
        adapter.moveRow(2,1); await(()-> { layout(); HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(3); return shelf!=null&&shelf.list.hasFocus(); });
        adapter.moveRow(3,1); layout(); assertTrue(((HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(3)).list.hasFocus());
    }
    @Test public void switchingToEnglishUpdatesMenusAndGenreCheckboxesPersistPerAccount() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Einstellungen")!=null); findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick(); layout();
        findButton(activity.get().findViewById(android.R.id.content),"Sprache: Deutsch").performClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); dialog.getListView().performItemClick(null,0,0); layout();
        assertEquals("en",I18n.language()); View root=activity.get().findViewById(android.R.id.content); assertNotNull(findButton(root,"Genre rows")); screenshot("settings-english-hd.png");
        findButton(root,"Genre rows").performClick(); layout();
        android.widget.CheckBox horror=(android.widget.CheckBox)find(activity.get().findViewById(android.R.id.content),v->v instanceof android.widget.CheckBox&&"Horror".contentEquals(((android.widget.CheckBox)v).getText())); assertNotNull(horror); assertTrue(horror.isChecked()); horror.setChecked(false); assertFalse(HomeGenres.read(app.settings,app.account).contains("Horror")); screenshot("genre-settings-english-hd.png");
        activity.get().onBackPressed(); layout(); assertNotNull(findButton(activity.get().findViewById(android.R.id.content),"Genre rows"));
    }
    private void screenshot(String name) throws Exception {
        layout(); View root=activity.get().getWindow().getDecorView(); Bitmap bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        File dir=new File("build/qa"); dir.mkdirs(); try(OutputStream out=new FileOutputStream(new File(dir,name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle();
    }
    private RecyclerView recycler() { return (RecyclerView)find(activity.get().findViewById(android.R.id.content),v->v instanceof RecyclerView); }
    private Button findButton(View root,String text) { return (Button)find(root,v->v instanceof Button&&text.equals(((Button)v).getText().toString())); }
    private TextView findText(View root,String text) { return (TextView)find(root,v->v instanceof TextView&&text.equals(((TextView)v).getText().toString())); }
    private View find(View root,java.util.function.Predicate<View> match) { if(match.test(root)) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View r=find(((ViewGroup)root).getChildAt(i),match); if(r!=null) return r; } return null; }
}
