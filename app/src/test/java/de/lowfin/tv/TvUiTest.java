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
    private int viewportWidth=1280,viewportHeight=720;
    private JSONObject series,movie;
    private byte[] posterImage,thumbImage;
    private volatile JSONArray recommendations=new JSONArray(),firstEpisodes=new JSONArray();
    private volatile boolean recommendationsOffline;
    private volatile boolean companionInstalled;
    private volatile String blockedHomePath="";
    private final java.util.concurrent.CountDownLatch homeBlocked=new java.util.concurrent.CountDownLatch(1),homeRelease=new java.util.concurrent.CountDownLatch(1);
    private volatile JSONObject companionState;
    @Before public void setup() throws Exception { RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putString("language","de").putBoolean("startupLogo",false).commit(); I18n.init(RuntimeEnvironment.getApplication());
        app=(LowfinApp)RuntimeEnvironment.getApplication(); series=Json.obj("Id","show","Type","Series","Name","Die lange Serie","Overview","45 Staffeln. Direkt zu deiner Lieblingsfolge.","ImageTags",Json.obj("Primary","poster"),"PrimaryImageAspectRatio",2.0/3);
        movie=Json.obj("Id","movie","Type","Movie","Name","Zwischen den Sternen","Overview","Eine Crew folgt einem rätselhaften Signal durch die Nacht. Als ihr Schiff die vertrauten Routen verlässt, wird aus der Suche nach Antworten eine Reise nach Hause.","ProductionYear",2025,"OfficialRating","FSK 12","RunTimeTicks",67200000000L,"Genres",Json.arr("Science-Fiction","Drama"),"ImageTags",Json.obj("Primary","poster"),"BackdropImageTags",Json.arr("backdrop"),"RemoteTrailers",Json.arr(Json.obj("Url","https://www.youtube.com/watch?v=M7lc1UVf-VE")));
        posterImage=fixture(240,360,"LOWFIN","AFTER HOURS"); thumbImage=fixture(480,270,"STAFFEL 40","Ein neuer Anfang");
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path=request.getRequestUrl().encodedPath();
                if(path.equals(blockedHomePath)) {homeBlocked.countDown();try {homeRelease.await();} catch(InterruptedException interrupted) {Thread.currentThread().interrupt();return new MockResponse().setResponseCode(503);}}
                if(path.startsWith(CompanionApi.ROOT)) {
                    if(!companionInstalled) return new MockResponse().setResponseCode(404);
                    if(path.endsWith("/capabilities")) return new MockResponse().setBody(Json.obj("available",true,"apiVersion",1).toString());
                    if(path.endsWith("/long-videos")) return new MockResponse().setBody(Json.obj("revision",0).toString());
                    if(path.endsWith("/rule")) try { JSONObject requestBody=new JSONObject(request.getBody().readUtf8()); companionState=Json.obj("revision",2,"sequence",-1,"effectiveRule",requestBody.optJSONObject("rule"),"rule",requestBody.optJSONObject("rule")); } catch(Exception e) { return new MockResponse().setResponseCode(400); }
                    if(path.contains("/items/movie")) return new MockResponse().setBody(companionState.toString());
                    return new MockResponse().setBody(Json.obj("items",new JSONArray()).toString());
                }
                if(path.equals("/Movies/Recommendations")) return recommendationsOffline?new MockResponse().setResponseCode(503):new MockResponse().setBody(recommendations.toString());
                if(recommendationsOffline&&path.equals("/Users/user/Items")&&request.getRequestUrl().queryParameter("IsPlayed")!=null) return new MockResponse().setResponseCode(503);
                if(path.contains("/Images/")) return new MockResponse().setHeader("Content-Type","image/png").setBody(new okio.Buffer().write(path.contains("ep")||path.endsWith("Thumb")||path.endsWith("Backdrop")?thumbImage:posterImage));
                if(path.equals("/Users/user/Items/ep5")) return new MockResponse().setBody(episode("ep5",5,6000000000L).toString());
                if(path.equals("/Users/user/Items/ep6")) return new MockResponse().setBody(episode("ep6",6,0).toString());
                if(path.equals("/Users/user/Items/actor")) return new MockResponse().setBody(Json.obj("Id","actor","Type","Person","Name","Test Actor","Overview","Biography from Jellyfin","ImageTags",Json.obj("Primary","portrait")).toString());
                if(path.equals("/Users/user/Items")&&request.getRequestUrl().queryParameter("PersonIds")!=null) return new MockResponse().setBody(Json.obj("Items",Json.arr(movie,series),"TotalRecordCount",2).toString());
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
                if(path.endsWith("/Episodes")) return new MockResponse().setBody(Json.obj("Items",firstEpisodes,"TotalRecordCount",firstEpisodes.length()).toString());
                return new MockResponse().setBody("{}");
            }
        }); server.start();
    }
    @After public void stop() throws Exception {
        homeRelease.countDown();
        if(activity!=null) activity.pause().stop().destroy();
        if(app.images!=null) app.images.close(); if(app.ratings!=null) app.ratings.close(); app.io.shutdownNow(); app.cacheIo.shutdownNow(); app.navigationIo.shutdownNow(); app.reports.shutdownNow(); app.homeIo.shutdownNow(); server.shutdown();
    }

    @Test public void startupLogoWaitsForBothFreshRowsAndDoesNotReplayWhenReturningHome() throws Exception {
        app.settings.edit().putBoolean("startupLogo",true).putBoolean("animations",false).apply();app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));
        app.library.state("home-0",Json.arr(episode("cached0",4,6000000000L)).toString());blockedHomePath="/Users/user/Items/Resume";
        try {
            activity=Robolectric.buildActivity(MainActivity.class).setup();assertTrue(homeBlocked.await(2,java.util.concurrent.TimeUnit.SECONDS));
            await(()->!((HomeAdapter)recycler().getAdapter()).rows.get(1).isEmpty());StartupOverlay splash=org.robolectric.util.ReflectionHelpers.getField(activity.get(),"startupOverlay");assertNotNull(splash);assertTrue(splash.showing());
            homeRelease.countDown();await(()->!splash.showing());
            java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",JSONObject.class);open.setAccessible(true);open.invoke(activity.get(),movie);layout();activity.get().onBackPressed();layout();assertNull(org.robolectric.util.ReflectionHelpers.getField(activity.get(),"startupOverlay"));
        } finally {homeRelease.countDown();}
    }
    @Test public void optionalDiscoveryBannerHasNativeControlsAndRestoresItsFilmAfterDetails() throws Exception {
        app.settings.edit().putBoolean("discoveryBanner",true).putBoolean("animations",false).apply();app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));java.util.Map<String,JSONObject> ratingMemory=org.robolectric.util.ReflectionHelpers.getField(app.ratings,"memory");ratingMemory.put("movie",Json.obj("time",System.currentTimeMillis(),"ttl",Long.MAX_VALUE,"scores",Json.obj("imdb",7.8,"tmdb",8.1,"tomatoes",91)));activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->{HomeAdapter h=(HomeAdapter)recycler().getAdapter();return !h.rows.get(0).isEmpty()&&!h.rows.get(1).isEmpty();});HomeAdapter home=(HomeAdapter)recycler().getAdapter();
        assertEquals(0,home.firstRow());assertEquals(1,home.position(0));assertEquals(1,home.getItemViewType(0));
        await(()->recycler().findViewHolderForAdapterPosition(0)!=null);HomeAdapter.Shelf holder=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);assertNotNull(holder.banner);
        HomeAdapter.Shelf resume=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1);assertNotNull(resume);await(()->resume.list.hasFocus());
        home.requestRow(home.logical(0));await(()->holder.banner.hasFocus());holder.banner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));holder.banner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));
        await(()->holder.banner.ready());assertNotNull(findText(holder.banner,movie.optString("Name")));android.widget.ImageView poster=org.robolectric.util.ReflectionHelpers.getField(holder.banner,"poster");assertEquals(2.0/3,(double)poster.getWidth()/poster.getHeight(),.01);
        await(()->poster.getDrawable()!=null);assertFalse("Poster drawable has empty bounds: "+poster.getDrawable().getBounds(),poster.getDrawable().getBounds().isEmpty());android.graphics.RectF imageRect=new android.graphics.RectF(poster.getDrawable().getBounds());poster.getImageMatrix().mapRect(imageRect);assertTrue("Poster transform: "+imageRect+" / "+poster.getWidth()+"x"+poster.getHeight(),imageRect.width()>0&&imageRect.height()>0&&imageRect.left<poster.getWidth()&&imageRect.top<poster.getHeight());Bitmap posterProof=Bitmap.createBitmap(poster.getWidth(),poster.getHeight(),Bitmap.Config.ARGB_8888);poster.draw(new Canvas(posterProof));assertEquals("Poster drawable must remain fully opaque",255,poster.getDrawable().getAlpha());assertNotEquals("Poster must draw actual artwork",0xff191919,posterProof.getPixel(posterProof.getWidth()/2,posterProof.getHeight()/2));TextView facts=org.robolectric.util.ReflectionHelpers.getField(holder.banner,"meta");assertEquals("2025 · 112 min · Science-Fiction · Drama",facts.getText().toString());assertNull(findText(holder.banner,I18n.resource(R.string.discovery_hint)));RatingsRow bannerRatings=(RatingsRow)find(holder.banner,v->v instanceof RatingsRow);assertNotNull(bannerRatings);assertTrue(((TextView)bannerRatings.getChildAt(0)).getText().toString().contains("7,8"));assertBannerGeometry(holder.banner,bannerRatings);assertEquals(resume.itemView.getLeft(),holder.banner.getLeft());assertEquals(resume.itemView.getRight(),holder.banner.getRight());int ratingHeight=bannerRatings.getHeight();bannerRatings.show(app,Json.obj("imdb",10,"tmdb",10,"tomatoes",100));layout();assertEquals(ratingHeight,bannerRatings.getHeight());for(View entry:new View[]{facts,bannerRatings,findText(holder.banner,movie.optString("Overview"))}) {android.graphics.Rect box=new android.graphics.Rect();entry.getDrawingRect(box);holder.banner.offsetDescendantRectToMyCoords(entry,box);assertTrue(box.top>=0&&box.bottom<=holder.banner.getHeight());assertTrue(entry instanceof RatingsRow?box.right<=holder.banner.getWidth():box.right<poster.getLeft());}assertTrue(findText(holder.banner,movie.optString("Overview")).getHeight()>0);screenshot("home-discovery-banner-hd.png");holder.banner.performClick();layout();assertNotNull(findButton(activity.get().findViewById(android.R.id.content),"Abspielen"));activity.get().onBackPressed();
        await(()->{HomeAdapter restored=(HomeAdapter)recycler().getAdapter();HomeAdapter.Shelf current=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(restored.position(restored.rows.size()-1));return current!=null&&current.banner!=null&&current.banner.hasFocus()&&current.banner.ready();});
        HomeAdapter restored=(HomeAdapter)recycler().getAdapter();assertEquals("movie",restored.rows.get(restored.rows.size()-1).get(0).optString("Id"));
        remote(KeyEvent.KEYCODE_DPAD_DOWN,1);await(()->{HomeAdapter.Shelf row=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(restored.position(0));return row!=null&&row.list.hasFocus();});
    }
    @Test public void discoveryBannerHidesMissingFactsAndDisabledRatingSources() throws Exception {
        app.settings.edit().putBoolean("discoveryBanner",true).putBoolean("animations",false).apply();for(String id:Ratings.IDS)app.settings.edit().putBoolean("rating-show-"+id,false).apply();movie.remove("RunTimeTicks");movie.remove("Genres");movie.remove("ProductionYear");app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->{HomeAdapter h=(HomeAdapter)recycler().getAdapter();return !h.rows.get(0).isEmpty()&&!h.rows.get(1).isEmpty();});await(()->recycler().findViewHolderForAdapterPosition(0)!=null);HomeAdapter.Shelf holder=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);HomeAdapter bannerHome=(HomeAdapter)recycler().getAdapter();bannerHome.requestRow(bannerHome.logical(0));await(()->holder.banner.hasFocus());holder.banner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));holder.banner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));await(()->holder.banner.ready());
        TextView facts=org.robolectric.util.ReflectionHelpers.getField(holder.banner,"meta");assertEquals(View.GONE,facts.getVisibility());assertEquals("",facts.getText().toString());RatingsRow scores=(RatingsRow)find(holder.banner,v->v instanceof RatingsRow);assertEquals(0,scores.getChildCount());assertEquals(View.GONE,scores.getVisibility());assertNull(findText(holder.banner,I18n.resource(R.string.discovery_hint)));assertTrue(findText(holder.banner,movie.optString("Overview")).getHeight()>0);
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-xxxhdpi") public void discoveryBannerScalesItsFactsRatingsAndPosterTo4k() throws Exception {
        viewportWidth=3840;viewportHeight=2160;app.settings.edit().putBoolean("discoveryBanner",true).putBoolean("animations",false).apply();app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));java.util.Map<String,JSONObject> memory=org.robolectric.util.ReflectionHelpers.getField(app.ratings,"memory");memory.put("movie",Json.obj("time",System.currentTimeMillis(),"ttl",Long.MAX_VALUE,"scores",Json.obj("imdb",7.8,"tmdb",8.1)));activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->{HomeAdapter h=(HomeAdapter)recycler().getAdapter();return !h.rows.get(0).isEmpty()&&!h.rows.get(1).isEmpty();});await(()->recycler().findViewHolderForAdapterPosition(0)!=null);HomeAdapter.Shelf holder=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);HomeAdapter bannerHome=(HomeAdapter)recycler().getAdapter();bannerHome.requestRow(bannerHome.logical(0));await(()->holder.banner.hasFocus());holder.banner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));holder.banner.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));await(()->holder.banner.ready());
        ImageView poster=org.robolectric.util.ReflectionHelpers.getField(holder.banner,"poster");await(()->poster.getDrawable()!=null);assertEquals(504,poster.getWidth());assertEquals(756,poster.getHeight());assertEquals(ImageView.ScaleType.FIT_CENTER,poster.getScaleType());TextView facts=org.robolectric.util.ReflectionHelpers.getField(holder.banner,"meta");assertEquals("2025 · 112 min · Science-Fiction · Drama",facts.getText().toString());assertEquals(44f,facts.getTextSize(),.1f);RatingsRow ratings=(RatingsRow)find(holder.banner,v->v instanceof RatingsRow);assertBannerGeometry(holder.banner,ratings);assertEquals(40f,((TextView)ratings.getChildAt(0)).getTextSize(),.1f);assertTrue(((TextView)ratings.getChildAt(0)).getText().toString().contains("7,8"));assertNull(findText(holder.banner,I18n.resource(R.string.discovery_hint)));
        for(View entry:new View[]{facts,ratings,findText(holder.banner,movie.optString("Overview"))}){android.graphics.Rect bounds=new android.graphics.Rect();entry.getDrawingRect(bounds);holder.banner.offsetDescendantRectToMyCoords(entry,bounds);assertTrue(bounds.top>=0&&bounds.bottom<=holder.banner.getHeight());assertTrue(entry instanceof RatingsRow?bounds.right<=holder.banner.getWidth():bounds.right<poster.getLeft());}assertTrue(findText(holder.banner,movie.optString("Overview")).getHeight()>0);screenshot("home-discovery-banner-4k.png");
    }
    private void assertBannerGeometry(DiscoveryBanner banner,RatingsRow ratings) {
        TextView description=org.robolectric.util.ReflectionHelpers.getField(banner,"description");assertEquals(4,description.getMaxLines());
        for(int i=0;i<ratings.getChildCount();i++) {View chip=ratings.getChildAt(i);assertEquals(0,chip.getTop());assertTrue(chip.getRight()<=ratings.getWidth());assertEquals(1f,chip.getScaleX(),0);assertEquals(1f,chip.getScaleY(),0);}
        android.graphics.Rect text=new android.graphics.Rect();description.getDrawingRect(text);banner.offsetDescendantRectToMyCoords(description,text);assertTrue(text.bottom<=ratings.getTop());
    }
    @Test public void nextUpRefreshDoesNotWaitForSlowResumeOrBusyGeneralWorkers() throws Exception { independentHomeRefresh(0); }
    @Test public void resumeRefreshDoesNotWaitForSlowNextUpOrBusyGeneralWorkers() throws Exception { independentHomeRefresh(1); }
    private void independentHomeRefresh(int blockedRow) throws Exception {
        app.settings.edit().putBoolean("homeChannels",false).putBoolean("autoPrecache",false).apply();
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));
        app.library.state("home-0",Json.arr(episode("cached0",4,6000000000L)).toString());app.library.state("home-1",Json.arr(episode("cached1",5,0)).toString());
        blockedHomePath=blockedRow==0?"/Users/user/Items/Resume":"/Shows/NextUp";
        java.util.concurrent.CountDownLatch busy=new java.util.concurrent.CountDownLatch(4),release=new java.util.concurrent.CountDownLatch(1);
        for(int i=0;i<4;i++) app.io.execute(()->{busy.countDown();try {release.await();} catch(InterruptedException interrupted) {Thread.currentThread().interrupt();}});
        assertTrue(busy.await(2,java.util.concurrent.TimeUnit.SECONDS));
        try {
            activity=Robolectric.buildActivity(MainActivity.class).setup();assertTrue(homeBlocked.await(2,java.util.concurrent.TimeUnit.SECONDS));
            int readyRow=1-blockedRow;String freshId=readyRow==0?"ep5":"ep6";
            await(()->{HomeAdapter home=(HomeAdapter)recycler().getAdapter();return !home.rows.get(readyRow).isEmpty()&&freshId.equals(home.rows.get(readyRow).get(0).optString("Id"))&&!home.rows.get(blockedRow).isEmpty();});
            HomeAdapter home=(HomeAdapter)recycler().getAdapter();assertEquals("cached"+blockedRow,home.rows.get(blockedRow).get(0).optString("Id"));assertEquals(1,homeRelease.getCount());
            homeRelease.countDown();String blockedFresh=blockedRow==0?"ep5":"ep6";
            await(()->blockedFresh.equals(home.rows.get(blockedRow).get(0).optString("Id")));
        } finally {homeRelease.countDown();release.countDown();}
    }
    @Test public void televisionPlaybackLaunchDoesNotWaitForBusyHomeWorkersOrFetchMetadataTwice() throws Exception {
        Account account=new Account(server.url("/").toString(),"user","token","Rico","device"); app.setAccount(account);
        java.util.concurrent.CountDownLatch occupied=new java.util.concurrent.CountDownLatch(4),release=new java.util.concurrent.CountDownLatch(1);
        for(int i=0;i<4;i++) app.io.execute(()-> { occupied.countDown(); try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(occupied.await(2,java.util.concurrent.TimeUnit.SECONDS));
        try {
            activity=Robolectric.buildActivity(MainActivity.class,HomeChannels.link(app,account,"ep5","resume")).setup();
            android.content.Intent player=shadowOf(activity.get()).getNextStartedActivity(); assertNotNull(player); assertEquals(PlayerActivity.class.getName(),player.getComponent().getClassName()); assertTrue(player.getBooleanExtra("resumeFromHome",false));
            assertEquals("ep5",new JSONObject(player.getStringExtra("item")).getString("Id"));
            RecordedRequest request; while((request=server.takeRequest(20,java.util.concurrent.TimeUnit.MILLISECONDS))!=null) assertEquals(CompanionApi.ROOT+"/capabilities",request.getRequestUrl().encodedPath());
            activity.pause().resume(); assertNotNull(recycler()); assertTrue(recycler().getAdapter() instanceof HomeAdapter);
        } finally { release.countDown(); }
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
    @Test public void detailMetadataLoadsWhileGeneralContentWorkersAreOccupied() throws Exception {
        movie.put("MediaSources",Json.arr(Json.obj("Size",12500000000L,"MediaStreams",Json.arr(Json.obj("Type","Video","Width",1920,"Height",1080)))));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        java.util.concurrent.CountDownLatch occupied=new java.util.concurrent.CountDownLatch(4),release=new java.util.concurrent.CountDownLatch(1);
        for(int i=0;i<4;i++) app.io.execute(()-> { occupied.countDown(); try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(occupied.await(2,java.util.concurrent.TimeUnit.SECONDS));
        try {
            activity=Robolectric.buildActivity(MainActivity.class).setup();
            java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",JSONObject.class); open.setAccessible(true);
            open.invoke(activity.get(),Json.obj("Id","movie","Type","Movie","Name","Minimal"));
            await(()->findText(activity.get().findViewById(android.R.id.content),"1920 × 1080  ·  12,5 GB")!=null);
            screenshot("details-video-info-hd.png");
        } finally { release.countDown(); }
    }
    @Test public void castOpensPersonAndClickableLibraryTitlesWithBackNavigation() throws Exception {
        movie.put("People",Json.arr(Json.obj("Id","actor","Type","Actor","Name","Test Actor","Role","Captain Example","PrimaryImageTag","portrait"),Json.obj("Id","director","Type","Director","Name","Director")));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",JSONObject.class); open.setAccessible(true); open.invoke(activity.get(),movie); layout();
        RecyclerView cast=(RecyclerView)find(activity.get().findViewById(android.R.id.content),v->v instanceof RecyclerView&&((RecyclerView)v).getAdapter() instanceof CastAdapter);
        assertNotNull(cast); assertEquals(1,cast.getAdapter().getItemCount());
        assertNotNull(findText(cast,"Captain Example"));
        View person=cast.getLayoutManager().findViewByPosition(0); assertNotNull(person); person.requestFocus(); person.performClick();
        await(()->findText(activity.get().findViewById(android.R.id.content),"Biography from Jellyfin")!=null&&recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&recycler().getAdapter().getItemCount()==2);
        layout(); Button biographyMore=findButton(activity.get().findViewById(android.R.id.content),I18n.resource(R.string.person_more)); assertTrue(biographyMore.getWidth()<((View)biographyMore.getParent()).getWidth()/2);
        screenshot("person-library-hd.png");
        recycler().getLayoutManager().findViewByPosition(0).performClick(); layout(); assertNotNull(findButton(activity.get().findViewById(android.R.id.content),"Abspielen"));
        activity.get().onBackPressed(); await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&recycler().getAdapter().getItemCount()==2);
        activity.get().onBackPressed(); layout(); assertNotNull(findText(activity.get().findViewById(android.R.id.content),"Besetzung"));
        await(()-> { RecyclerView castBack=(RecyclerView)find(activity.get().findViewById(android.R.id.content),v->v instanceof RecyclerView&&((RecyclerView)v).getAdapter() instanceof CastAdapter); return castBack!=null&&castBack.hasFocus(); });
        screenshot("details-cast-hd.png");
    }
    @Test public void RecycledCastCardsDoNotKeepAnotherActorsRole() {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));
        CastAdapter adapter=new CastAdapter(app.images,person->{}); android.widget.FrameLayout parent=new android.widget.FrameLayout(app);
        CastAdapter.Person card=adapter.onCreateViewHolder(parent,0);
        adapter.set(java.util.Arrays.asList(Json.obj("Id","a","Type","Person","Name","Actor A","Role","A very long character name that should remain inside the card"),Json.obj("Id","b","Type","Person","Name","Actor B")));
        adapter.onBindViewHolder(card,0);assertEquals(View.VISIBLE,card.role.getVisibility());assertEquals(2,card.role.getMaxLines());assertTrue(card.itemView.getContentDescription().toString().contains("character"));
        adapter.onBindViewHolder(card,1);assertEquals(View.GONE,card.role.getVisibility());assertEquals("",card.role.getText().toString());assertEquals("Actor B",card.itemView.getContentDescription());
    }
    @Test public void playAndResumeFocusIsHighContrastAndSurvivesAccentRefresh() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());activity=Robolectric.buildActivity(MainActivity.class).setup();
        java.lang.reflect.Method action=MainActivity.class.getDeclaredMethod("detailAction",String.class,int.class,boolean.class,boolean.class,Runnable.class);action.setAccessible(true);
        LinearLayout row=Ui.row(activity.get());activity.get().setContentView(row);
        for(String label:new String[]{"Abspielen","Fortsetzen"}) {
            Button play=(Button)action.invoke(activity.get(),label,R.drawable.detail_play,false,true,(Runnable)()->{});Button other=Ui.button(activity.get(),"Other",()->{});row.addView(play);row.addView(other);layout();
            android.graphics.drawable.GradientDrawable shape=(android.graphics.drawable.GradientDrawable)play.getBackground();
            other.requestFocus();int unfocused=shape.getColor().getDefaultColor();play.requestFocus();assertTrue(play.hasFocus());assertEquals(0xfff5f5f7,shape.getColor().getDefaultColor());assertNotEquals(unfocused,shape.getColor().getDefaultColor());assertEquals(0xff111113,play.getCurrentTextColor());
            Accent.apply(play);assertEquals(0xfff5f5f7,shape.getColor().getDefaultColor());assertSame(shape,play.getBackground());other.requestFocus();assertEquals(Accent.color,shape.getColor().getDefaultColor());row.removeAllViews();
        }
    }
    @Test public void detailActionsFillTheContentGridWithFewActionsAndAtLargerViewports() throws Exception {
        movie.remove("RemoteTrailers");app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",JSONObject.class);open.setAccessible(true);open.invoke(activity.get(),movie);
        View root=activity.get().findViewById(android.R.id.content);await(()->findButton(root,"Als gesehen")!=null);layout();
        LinearLayout actions=(LinearLayout)findButton(root,"Als gesehen").getParent();assertEquals(4,actions.getChildCount());
        for(int viewport:new int[]{1280,1920,3840}) {
            View decor=activity.get().getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(viewport,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(viewport*9/16,View.MeasureSpec.EXACTLY));decor.layout(0,0,viewport,viewport*9/16);
            assertEquals(((View)actions.getParent()).getWidth()-((View)actions.getParent()).getPaddingLeft()-((View)actions.getParent()).getPaddingRight(),actions.getWidth());
            int width=actions.getChildAt(0).getWidth();assertTrue(width>Ui.dp(activity.get(),104));
            for(int i=0;i<actions.getChildCount();i++) {assertTrue(Math.abs(width-actions.getChildAt(i).getWidth())<=1);assertEquals(Ui.dp(activity.get(),38),actions.getChildAt(i).getHeight());}
            assertEquals(actions.getWidth(),actions.getChildAt(3).getRight());
        }
        layout();screenshot("detail-toolbar-fullwidth-hd.png");
    }
    @Test public void detailToolbarRightEdgeStaysInToolbarWithCompanionAndDownStillReachesCast() throws Exception { detailToolbarRightEdge(true); }
    @Test public void detailToolbarRightEdgeStaysInToolbarWithoutCompanion() throws Exception { detailToolbarRightEdge(false); }
    private void detailToolbarRightEdge(boolean withCompanion) throws Exception {
        companionInstalled=withCompanion;companionState=Json.obj("revision",1,"sequence",-1,"effectiveRule",Json.obj("mode","Default","persistentResume",true));
        JSONArray actors=new JSONArray();for(int i=0;i<30;i++) actors.put(Json.obj("Id","actor"+i,"Type","Actor","Name","Actor "+i));movie.put("People",actors);
        movie.put("MediaSources",Json.arr(Json.obj("Id","file","MediaStreams",Json.arr(Json.obj("Type","Audio","Index",1,"DisplayTitle","Deutsch")))));
        movie.put("UserData",Json.obj("PlaybackPositionTicks",600000000L));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());app.companion.discover();
        activity=Robolectric.buildActivity(MainActivity.class).setup();java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",JSONObject.class);open.setAccessible(true);open.invoke(activity.get(),movie);
        View root=activity.get().findViewById(android.R.id.content);String last=withCompanion?"Smart Completion":"Als gesehen";
        await(()->findButton(root,last)!=null);layout();
        LinearLayout actions=(LinearLayout)findButton(root,last).getParent();assertEquals(withCompanion?8:7,actions.getChildCount());assertTrue(actions.getWidth()<=((View)actions.getParent()).getWidth());
        int buttonWidth=actions.getChildAt(0).getWidth();
        for(int i=0;i<actions.getChildCount();i++) {View button=actions.getChildAt(i);assertTrue(Math.abs(buttonWidth-button.getWidth())<=1);assertEquals(Ui.dp(activity.get(),38),button.getHeight());assertTrue(button.getWidth()>=Ui.dp(activity.get(),104));assertTrue(button.getRight()<=actions.getWidth());}
        assertEquals(actions.getWidth(),actions.getChildAt(actions.getChildCount()-1).getRight());
        findButton(root,"Fortsetzen").requestFocus();
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,30);layout();assertTrue(findButton(root,last).hasFocus());
        long now=android.os.SystemClock.uptimeMillis();activity.get().dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,12));activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT));layout();assertTrue(findButton(root,last).hasFocus());
        remote(KeyEvent.KEYCODE_DPAD_LEFT,1);layout();assertFalse(findButton(root,last).hasFocus());remote(KeyEvent.KEYCODE_DPAD_RIGHT,1);layout();assertTrue(findButton(root,last).hasFocus());
        CastList cast=(CastList)find(root,v->v instanceof CastList);assertNotNull(cast);remote(KeyEvent.KEYCODE_DPAD_DOWN,1);await(cast::hasFocus);
        remote(KeyEvent.KEYCODE_DPAD_LEFT,1);layout();assertTrue(cast.hasFocus());assertFalse(findButton(root,"Start").isShown());
        View parent=cast;while(parent!=null&&!(parent instanceof ScrollView)) parent=parent.getParent() instanceof View?(View)parent.getParent():null;
        ScrollView page=(ScrollView)parent;assertNotNull(page);page.scrollTo(0,cast.getTop());layout();assertTrue(page.getScrollY()>0);
        remote(KeyEvent.KEYCODE_DPAD_UP,1);await(()->page.getScrollY()==0);assertTrue(((ViewGroup)findButton(root,last).getParent()).hasFocus());
    }
    @Test public void castRemoteNavigationScrollsAcrossEveryActorAndNeverOpensDrawer() throws Exception {
        JSONArray actors=new JSONArray(); for(int i=0;i<40;i++) actors.put(Json.obj("Id","actor"+i,"Type","Actor","Name","Actor "+i)); movie.put("People",actors);
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",JSONObject.class); open.setAccessible(true); open.invoke(activity.get(),movie); layout();
        CastList cast=(CastList)find(activity.get().findViewById(android.R.id.content),v->v instanceof CastList); assertNotNull(cast); cast.getLayoutManager().findViewByPosition(0).requestFocus();
        remote(KeyEvent.KEYCODE_DPAD_LEFT,3); layout(); assertTrue(cast.hasFocus()); assertFalse(findButton(activity.get().findViewById(android.R.id.content),"Start").isShown());
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,45); await(()->cast.getLayoutManager().findViewByPosition(39)!=null&&cast.getLayoutManager().findViewByPosition(39).hasFocus());
        remote(KeyEvent.KEYCODE_DPAD_LEFT,45); await(()->cast.getLayoutManager().findViewByPosition(0)!=null&&cast.getLayoutManager().findViewByPosition(0).hasFocus());
        assertFalse(findButton(activity.get().findViewById(android.R.id.content),"Start").isShown()); screenshot("details-cast-focus-hd.png");
    }
    @Test public void detailLogoUsesFixedTopRightFrameAndPreservesImageAspect() throws Exception {
        movie.getJSONObject("ImageTags").put("Logo","logo");
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",JSONObject.class); open.setAccessible(true); open.invoke(activity.get(),movie);
        await(()-> { DetailLogo logo=(DetailLogo)find(activity.get().findViewById(android.R.id.content),v->v instanceof DetailLogo); return logo!=null&&logo.getDrawable()!=null; });
        DetailLogo logo=(DetailLogo)find(activity.get().findViewById(android.R.id.content),v->v instanceof DetailLogo);
        assertEquals(Ui.dp(activity.get(),DetailLogo.WIDTH_DP),logo.getWidth()); assertEquals(Ui.dp(activity.get(),DetailLogo.HEIGHT_DP),logo.getHeight());
        android.widget.FrameLayout.LayoutParams frame=(android.widget.FrameLayout.LayoutParams)logo.getLayoutParams(); assertEquals(Gravity.TOP|Gravity.END,frame.gravity);
        float[] matrix=new float[9]; logo.getImageMatrix().getValues(matrix); assertEquals(matrix[android.graphics.Matrix.MSCALE_X],matrix[android.graphics.Matrix.MSCALE_Y],.001f);
        assertFalse(findText(activity.get().findViewById(android.R.id.content),movie.optString("Name")).isShown()); screenshot("details-fixed-logo-hd.png");
    }
    @Test public void settingsStartOnHomeAndReturnToTheChosenCategory() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick(); layout();
        assertTrue(findButton(activity.get().findViewById(android.R.id.content),"Startseite").hasFocus());
        Button playback=findButton(activity.get().findViewById(android.R.id.content),"Wiedergabe"); playback.requestFocus(); playback.performClick(); layout();
        activity.get().onBackPressed(); layout(); assertTrue(findButton(activity.get().findViewById(android.R.id.content),"Wiedergabe").hasFocus());
        Button api=findButton(activity.get().findViewById(android.R.id.content),"API"); api.requestFocus(); api.performClick(); layout();
        activity.get().onBackPressed(); layout(); assertTrue(findButton(activity.get().findViewById(android.R.id.content),"API").hasFocus());
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
            assertNotNull(findButton(activity.get().findViewById(android.R.id.content),"Wiedergabe"));
            findButton(activity.get().findViewById(android.R.id.content),"Wiedergabe").performClick();
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
    @Test public void holdingOkOnSeasonTogglesWatchedForSeasonAndCachedEpisodes() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); app.library.put(series);
        app.library.put(Json.obj("Id","season-episode","Type","Episode","ParentId","s1","UserData",Json.obj("Played",false,"PlaybackPositionTicks",9000000)));
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Serien")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Serien").performClick(); await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1); layout(); recycler().getChildAt(0).performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==45); layout(); View card=recycler().getChildAt(0); card.requestFocus();
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+20));
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertTrue(dialog.isShowing()); assertEquals(1,dialog.getListView().getAdapter().getCount()); assertEquals("Als gesehen markieren",dialog.getListView().getAdapter().getItem(0));
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)); assertTrue(dialog.isShowing()); assertNull(shadowOf(activity.get()).getNextStartedActivity());
        dialog.getListView().performItemClick(null,0,0);
        await(()->Json.object(app.library.item("season-episode"),"UserData").optBoolean("Played"));
        // Persistence precedes the UI callback; wait for the user-visible completion before opening the next menu.
        await(()->"Gespeichert".equals(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()));
        layout(); assertEquals(0,Json.position(app.library.item("season-episode")));
        card.performLongClick(); dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertEquals("Als ungesehen markieren",dialog.getListView().getAdapter().getItem(0)); dialog.getListView().performItemClick(null,0,0);
        await(()->!Json.object(app.library.item("s1"),"UserData").optBoolean("Played")&&!Json.object(app.library.item("season-episode"),"UserData").optBoolean("Played"));
        boolean post=false,delete=false; RecordedRequest request;
        while((request=server.takeRequest(100,java.util.concurrent.TimeUnit.MILLISECONDS))!=null) if(request.getRequestUrl().encodedPath().equals("/Users/user/PlayedItems/s1")) { post|="POST".equals(request.getMethod()); delete|="DELETE".equals(request.getMethod()); }
        assertTrue(post); assertTrue(delete);
    }
    @Test public void holdingRemoteConfirmOnHomeOpensActionsWithoutLaunchingPlayback() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter&&((HomeAdapter)recycler().getAdapter()).rows.get(0).size()==1);
        layout(); HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0); View card=shelf.list.getChildAt(0); card.requestFocus();
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+20));
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(dialog); assertTrue(dialog.isShowing());
        assertEquals(7,dialog.getListView().getAdapter().getCount()); assertEquals("Zur Serienseite",dialog.getListView().getAdapter().getItem(5)); assertEquals("Zur Folge gehen",dialog.getListView().getAdapter().getItem(6));
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(dialog.isShowing()); assertNull(shadowOf(activity.get()).getNextStartedActivity());
        dialog.getListView().performItemClick(null,3,3); layout();
        assertTrue(new WatchLater(activity.get(),app.account).contains("ep5")); assertNull(shadowOf(activity.get()).getNextStartedActivity());
        assertTrue(card.hasFocus());
        card.performLongClick(); dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertEquals("Aus Später ansehen entfernen",dialog.getListView().getAdapter().getItem(3));
        dialog.dismiss();
    }
    @Test public void episodeDetailPlaybackReturnsLatestEpisodeButHomeStaysHome() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter&&((HomeAdapter)recycler().getAdapter()).rows.get(0).size()==1);
        layout(); HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);
        shelf.list.getChildAt(0).performLongClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null,6,6); layout();
        assertNotNull(findText(activity.get().findViewById(android.R.id.content),"Der Rückweg"));
        findButton(activity.get().findViewById(android.R.id.content),"Fortsetzen").performClick();
        org.robolectric.shadows.ShadowActivity.IntentForResult launch=shadowOf(activity.get()).getNextStartedActivityForResult();
        assertTrue(launch.intent.getBooleanExtra("returnEpisodeDetail",false));
        android.content.Intent result=new android.content.Intent().putExtra("item",episode("ep6",6,0).toString()).putExtra("accountScope",app.account.scope());
        activity.get().onActivityResult(launch.requestCode,android.app.Activity.RESULT_OK,result); layout();
        assertNotNull(findText(activity.get().findViewById(android.R.id.content),"Ein neuer Anfang"));
        activity.get().onBackPressed(); layout(); assertTrue(recycler().getAdapter() instanceof HomeAdapter);
        activity.get().onActivityResult(launch.requestCode,android.app.Activity.RESULT_OK,result); layout(); assertTrue(recycler().getAdapter() instanceof HomeAdapter);
    }
    @Test public void libraryActionsResumeRestartAndSynchronizeWatchedAndFavoriteState() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        movie.put("UserData",Json.obj("PlaybackPositionTicks",600000000L,"Played",false,"IsFavorite",false));
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null); findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&recycler().getAdapter().getItemCount()==1); layout(); View card=recycler().getChildAt(0); card.requestFocus();
        card.performLongClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertEquals(5,dialog.getListView().getAdapter().getCount());
        dialog.getListView().performItemClick(null,0,0); android.content.Intent intent=shadowOf(activity.get()).getNextStartedActivity(); assertEquals(60000,intent.getLongExtra("position",-1));
        card.performLongClick(); dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); dialog.getListView().performItemClick(null,1,1);
        assertEquals(0,shadowOf(activity.get()).getNextStartedActivity().getLongExtra("position",-1));
        card.performLongClick(); dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); dialog.getListView().performItemClick(null,2,2);
        await(()->Json.object(((MediaAdapter)recycler().getAdapter()).items.get(0),"UserData").optBoolean("Played"));
        card.performLongClick(); dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertEquals("Als ungesehen markieren",dialog.getListView().getAdapter().getItem(2)); dialog.getListView().performItemClick(null,4,4);
        await(()->Json.object(((MediaAdapter)recycler().getAdapter()).items.get(0),"UserData").optBoolean("IsFavorite"));
        boolean playedRequest=false,favoriteRequest=false; RecordedRequest request;
        while((request=server.takeRequest(100,java.util.concurrent.TimeUnit.MILLISECONDS))!=null) { if("POST".equals(request.getMethod())&&"/Users/user/PlayedItems/movie".equals(request.getRequestUrl().encodedPath())) playedRequest=true; if("POST".equals(request.getMethod())&&"/Users/user/FavoriteItems/movie".equals(request.getRequestUrl().encodedPath())) favoriteRequest=true; }
        assertTrue(playedRequest); assertTrue(favoriteRequest);
    }
    @Test public void showActionsResolveNextUpFirstEpisodeAndSeriesDetails() throws Exception {
        firstEpisodes=Json.arr(episode("first",1,0));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); await(()->findButton(activity.get().findViewById(android.R.id.content),"Serien")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Serien").performClick(); await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&recycler().getAdapter().getItemCount()==1); layout(); View card=recycler().getChildAt(0); card.requestFocus();
        card.performLongClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); assertEquals(6,dialog.getListView().getAdapter().getCount()); dialog.getListView().performItemClick(null,0,0);
        final android.content.Intent[] intent={null}; await(()-> { intent[0]=shadowOf(activity.get()).getNextStartedActivity(); return intent[0]!=null; }); assertEquals("ep6",new JSONObject(intent[0].getStringExtra("item")).optString("Id"));
        card.performLongClick(); dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); dialog.getListView().performItemClick(null,1,1);
        await(()-> { intent[0]=shadowOf(activity.get()).getNextStartedActivity(); return intent[0]!=null; }); assertEquals("first",new JSONObject(intent[0].getStringExtra("item")).optString("Id")); assertEquals(0,intent[0].getLongExtra("position",-1));
        card.performLongClick(); dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); dialog.getListView().performItemClick(null,5,5);
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Staffeln ansehen")!=null);
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
        assertEquals(600000L,intent.getLongExtra("position",-1)); assertEquals("ep5",new JSONObject(intent.getStringExtra("item")).optString("Id")); assertFalse(intent.getBooleanExtra("returnEpisodeDetail",false));
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
        String cached=app.library.state("seriesResume-v2-shows-0"); assertTrue(cached.contains("ep5"));
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
        assertFalse(findButton(root,"Zufälliger Film").isShown()); assertNull(findButton(root,"Genres anheften"));
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); adapter.requestRow(1); layout();
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1);
        assertEquals(2,shelf.list.getAdapter().getItemCount()); View more=shelf.list.getLayoutManager().findViewByPosition(1); assertNotNull(more);
        ArtworkView image=(ArtworkView)find(more,v->v instanceof ArtworkView); assertEquals(16.0/9,(double)image.getMeasuredWidth()/image.getMeasuredHeight(),.01);
        assertEquals("Mehr anzeigen",more.getContentDescription().toString()); more.performClick(); layout();
        assertNotNull(findText(root,"Als Nächstes")); assertTrue(recycler().getAdapter() instanceof MediaAdapter);
    }
    @Test public void SidebarSpacingBetweenGenresAndLiveMatchesOtherEntries() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        remote(KeyEvent.KEYCODE_MENU,1); layout(); View root=activity.get().findViewById(android.R.id.content);
        Button search=findButton(root,"Suche"),genres=findButton(root,"Genres"),live=findButton(root,"Live TV");
        assertEquals(genres.getTop()-search.getBottom(),live.getTop()-genres.getBottom());
        screenshot("sidebar-even-spacing-hd.png");
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
    @Test public void detailWatchLaterAndFavoritesAreIndependentReversibleAndKeepTheirFocus() throws Exception {
        Dispatcher original=server.getDispatcher();java.util.concurrent.atomic.AtomicBoolean favorite=new java.util.concurrent.atomic.AtomicBoolean(),played=new java.util.concurrent.atomic.AtomicBoolean();java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            String path=request.getRequestUrl().encodedPath();
            if(path.equals("/Users/user/PlayedItems/movie")) { played.set(request.getMethod().equals("POST"));return new MockResponse().setBody("{}"); }
            if(path.equals("/Users/user/FavoriteItems/movie")) { favorite.set(request.getMethod().equals("POST"));writes.incrementAndGet();return new MockResponse().setBody("{}"); }
            if(path.equals("/Users/user/Items/movie")) return new MockResponse().setBody(Json.obj("Id","movie","Type","Movie","Name","Film","UserData",Json.obj("IsFavorite",favorite.get(),"Played",played.get())).toString());
            return original.dispatch(request);
        }});
        Account account=new Account(server.url("/").toString(),"user","token","Rico","device");app.setAccount(account);app.library.state("lastSync",""+System.currentTimeMillis());activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();await(()->recycler()!=null&&recycler().getChildCount()>0);recycler().getChildAt(0).performClick();
        View root=activity.get().findViewById(android.R.id.content);await(()->findText(root,"Film")!=null);layout();
        Button add=findButton(root,"Später ansehen");assertNotNull(add);add.requestFocus();add.performClick();
        Button saved=findButton(root,"Vorgemerkt");assertTrue(saved.isSelected());assertTrue(saved.hasFocus());assertEquals("Aus Später ansehen entfernen",saved.getContentDescription());assertEquals(0,writes.get());assertTrue(new WatchLater(app,account).contains("movie"));
        Button fav=findButton(root,"Favorit hinzufügen");fav.requestFocus();fav.performClick();await(()->findButton(root,"Favorisiert")!=null);
        assertTrue(findButton(root,"Favorisiert").isSelected());assertTrue(findButton(root,"Favorisiert").hasFocus());assertTrue(findButton(root,"Vorgemerkt").isSelected());
        findButton(root,"Favorisiert").performClick();await(()->findButton(root,"Favorit hinzufügen")!=null);assertEquals(2,writes.get());assertTrue(new WatchLater(app,account).contains("movie"));
        findButton(root,"Vorgemerkt").performClick();assertNotNull(findButton(root,"Später ansehen"));assertFalse(new WatchLater(app,account).contains("movie"));assertEquals(2,writes.get());
        findButton(root,"Als gesehen").performClick();await(()->findButton(root,"Als ungesehen")!=null);assertTrue(findButton(root,"Als ungesehen").isSelected());assertEquals("Als ungesehen markieren",findButton(root,"Als ungesehen").getContentDescription());
        findButton(root,"Als ungesehen").performClick();await(()->findButton(root,"Als gesehen")!=null);assertFalse(findButton(root,"Als gesehen").isSelected());
    }
    @Test public void movieDetailUsesPortraitPosterAndCompactActions() throws Exception {
        movie.put("MediaSources",Json.arr(Json.obj("Id","file-de","MediaStreams",Json.arr(Json.obj("Type","Audio","Index",3,"DisplayTitle","Deutsch 5.1")))));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); layout();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1); recycler().getChildAt(0).performClick();
        View root=activity.get().findViewById(android.R.id.content);
        await(()->findButton(root,"Abspielen")!=null); layout();
        ArtworkView cover=(ArtworkView)find(root,v->v instanceof ArtworkView);
        assertEquals(2.0/3,(double)cover.getMeasuredWidth()/cover.getMeasuredHeight(),.01);
        assertTrue(findButton(root,"Abspielen").getHeight()<65); assertNotNull(findButton(root,"Als gesehen"));
        BackdropView backdrop=(BackdropView)find(root,v->v instanceof BackdropView); assertNotNull(backdrop);
        await(()->cover.getDrawable()!=null&&backdrop.getDrawable()!=null); screenshot("movie-detail-hd.png");
        Button play=findButton(root,"Abspielen"),audio=findButton(root,"Tonspur"),watched=findButton(root,"Als gesehen");
        assertNotNull(audio);
        ViewGroup actions=(ViewGroup)play.getParent();for(int n=0;n<actions.getChildCount();n++) assertNotNull(((Button)actions.getChildAt(n)).getCompoundDrawablesRelative()[0]);
        watched.requestFocus(); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT)); activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT));
        assertTrue(findButton(root,"Favorit hinzufügen").hasFocus());
        assertEquals(0,((View)backdrop.getParent()).getLeft()); assertEquals(0,((View)backdrop.getParent()).getTop());
        assertSame(play.getParent(),audio.getParent()); assertSame(play.getParent(),watched.getParent());
        audio.performClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(dialog.getListView().getChildAt(1),1,1);
        assertNotNull(findButton(root,"Tonspur"));
        findButton(root,"Abspielen").performClick(); android.content.Intent intent=shadowOf(activity.get()).getNextStartedActivity();
        assertEquals(3,intent.getIntExtra("audioStreamIndex",-1)); assertEquals("file-de",intent.getStringExtra("mediaSourceId"));
    }
    @Test public void resumedMoviesUseLandscapeThumbsWithoutChangingLibraryPosters() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter&&((HomeAdapter)recycler().getAdapter()).rows.get(0).size()==1);
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter();
        JSONObject movie=Json.obj("Id","resume-film","Type","Movie","Name","Film","ImageTags",Json.obj("Primary","poster","Thumb","thumb"));
        adapter.rows.get(0).clear(); adapter.rows.get(0).add(movie); adapter.notifyItemChanged(0); layout();
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);
        ArtworkView cover=(ArtworkView)find(shelf.list.getChildAt(0),v->v instanceof ArtworkView); assertNotNull(cover);
        assertEquals(16.0/9,(double)cover.getMeasuredWidth()/cover.getMeasuredHeight(),.015);
        ImageView expected=new ImageView(activity.get()); app.images.show(expected,movie,true); assertEquals(expected.getTag(),cover.getTag());
        assertEquals(2.0/3,Artwork.ratio(movie,false),.001);
        assertEquals(1,shelf.list.getAdapter().getItemViewType(0));
        assertEquals(3,shelf.list.getAdapter().getItemViewType(1));
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
        android.app.AlertDialog choices=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("Alle Filme",choices.getListView().getAdapter().getItem(0)); assertEquals("Genre wählen…",choices.getListView().getAdapter().getItem(1)); choices.getListView().performItemClick(null,0,0);
        long end=System.currentTimeMillis()+5000; RecordedRequest random=null;
        while(System.currentTimeMillis()<end) { RecordedRequest request=server.takeRequest(100,java.util.concurrent.TimeUnit.MILLISECONDS); if(request!=null&&"Random".equals(request.getRequestUrl().queryParameter("SortBy"))&&"80".equals(request.getRequestUrl().queryParameter("Limit"))) { random=request; break; } }
        assertNotNull(random); assertEquals("films",random.getRequestUrl().queryParameter("ParentId")); assertEquals("80",random.getRequestUrl().queryParameter("Limit"));
        assertEquals("Movie",random.getRequestUrl().queryParameter("IncludeItemTypes"));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3)); layout();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Abspielen")!=null);
    }
    @Test public void RandomGenreSubmenuSendsMovieOnlyGenreAndLibraryFilters() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1);
        findButton(activity.get().findViewById(android.R.id.content),"Zufälliger Film").performClick();
        android.app.AlertDialog choices=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); choices.getListView().performItemClick(null,1,1);
        await(()-> { android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); return d!=choices&&d.isShowing()&&d.getListView()!=null&&d.getListView().getAdapter().getCount()==1; });
        android.app.AlertDialog genres=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals("Drama",genres.getListView().getAdapter().getItem(0));genres.getListView().performItemClick(null,0,0);
        long end=System.currentTimeMillis()+5000; RecordedRequest random=null;
        while(System.currentTimeMillis()<end) { RecordedRequest r=server.takeRequest(100,java.util.concurrent.TimeUnit.MILLISECONDS); if(r!=null&&"Random".equals(r.getRequestUrl().queryParameter("SortBy"))&&"80".equals(r.getRequestUrl().queryParameter("Limit"))) { random=r; break; } }
        assertNotNull(random); assertEquals("films",random.getRequestUrl().queryParameter("ParentId")); assertEquals("Drama",random.getRequestUrl().queryParameter("Genres")); assertEquals("Movie",random.getRequestUrl().queryParameter("IncludeItemTypes"));
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
        adapter.requestRow(1); layout();
        await(()-> { HomeAdapter.Shelf h=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1); return h!=null&&h.list.findFocus()!=null; });
        HomeAdapter.Shelf next=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1);
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
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Abspielen")!=null); assertNull(shadowOf(activity.get()).getNextStartedActivity());
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
        recycler().getChildAt(0).performClick(); await(()->findButton(activity.get().findViewById(android.R.id.content),"Trailer")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Trailer").performClick(); android.content.Intent intent=shadowOf(activity.get()).getNextStartedActivity();
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
        assertSame(findButton(activity.get().findViewById(android.R.id.content),"Zufälliger Film"),activity.get().findViewById(android.R.id.content).findFocus());
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
    @Test public void optionalCompanionDetailControlsSaveDecimalRulesAndKeepFocus() throws Exception {
        companionInstalled=true; companionState=Json.obj("revision",1,"sequence",-1,"effectiveRule",Json.obj("mode","Default","persistentResume",true,"remainingSeconds",120,"percentage",99.5));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick(); await(()->recycler()!=null&&recycler().getChildCount()>0); recycler().getChildAt(0).performClick();
        View root=activity.get().findViewById(android.R.id.content); await(()->findButton(root,"Smart Completion")!=null);
        Button settings=findButton(root,"Smart Completion"); settings.requestFocus(); settings.performClick();
        android.app.AlertDialog dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); findButton(dialog.getWindow().getDecorView(),I18n.resource(R.string.companion_mode)).performClick();
        dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); dialog.getListView().performItemClick(dialog.getListView().getChildAt(1),1,1);
        dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); shadowOf(Looper.getMainLooper()).idle(); EditText value=(EditText)find(dialog.getWindow().getDecorView(),v->v instanceof EditText); value.setText("99,5"); assertEquals("99,5",value.getText().toString()); dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        await(()->app.library.companionState("movie").optLong("revision")==2);
        await(()->findButton(root,"Smart Completion").hasFocus()); assertEquals("CustomPercentage",Json.object(app.library.companionState("movie"),"effectiveRule").optString("mode"));
        assertEquals(99.5,Json.object(app.library.companionState("movie"),"effectiveRule").optDouble("percentage"),.0001);
    }
    @Test public void optionalCompanionPlaybackSettingsAppearOnlyWhenDiscovered() throws Exception {
        companionInstalled=true; companionState=Json.obj("revision",1,"sequence",-1,"effectiveRule",Json.obj("mode","Default"));
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); View root=activity.get().findViewById(android.R.id.content);
        findButton(root,"Einstellungen").performClick(); findButton(root,"Wiedergabe").performClick();
        await(()->findButton(root,"Lowfin Companion · lange Videos")!=null);
        findButton(root,"Lowfin Companion · lange Videos").performClick(); android.app.AlertDialog dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); assertTrue(dialog.isShowing()); assertNotNull(find(dialog.getWindow().getDecorView(),v->v instanceof SettingSwitch));
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

    @Test public void metadataRefreshBeforeShelfReacquiresFocusRetainsRememberedCard() throws Exception {
        app.settings.edit().putBoolean("animations",false).putBoolean("scrollAnimations",false).apply();HomeAdapter adapter=stressHome();adapter.requestRow(2);awaitShelf(2,0);remote(KeyEvent.KEYCODE_DPAD_RIGHT,12);awaitShelf(2,12);
        android.widget.FrameLayout decor=(android.widget.FrameLayout)activity.get().getWindow().getDecorView();Button temporary=Ui.button(activity.get(),"Temporary focus during restore",()->{});decor.addView(temporary,new android.widget.FrameLayout.LayoutParams(100,50));assertTrue(temporary.requestFocus());
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2);assertFalse(shelf.list.hasFocus());
        java.util.List<JSONObject> changed=stressFilms(2,40);changed.get(12).put("Name","Fresh metadata before focus");adapter.update(2,changed);decor.removeView(temporary);awaitShelf(2,12);
    }
    @Test public void recommendationDetailsReturnToTheSameHomeCardAndHorizontalPosition() throws Exception { homeCardReturn(2,12,false); }
    @Test public void animatedGenreDetailsReturnToTheSameHomeCardAndHorizontalPosition() throws Exception { homeCardReturn(7,12,true); }
    @Test public void recommendationMoreReturnsToItsOwnCardOnHome() throws Exception { homeCardReturn(2,40,false); }
    private void homeCardReturn(int row,int column,boolean animated) throws Exception {
        app.settings.edit().putBoolean("scrollAnimations",animated).putBoolean("animations",animated).apply();HomeAdapter adapter=stressHome();adapter.requestRow(row);
        if(animated) awaitAnimatedShelf(row,0);else awaitShelf(row,0);
        if(animated) {HomeAdapter.Shelf target=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(row);((HomeAdapter.ShelfList)target.list).requestCard(column);}
        else remote(KeyEvent.KEYCODE_DPAD_RIGHT,column);
        if(animated) awaitAnimatedShelf(row,column);else awaitShelf(row,column);
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(row);View selected=shelf.list.getLayoutManager().findViewByPosition(column);int left=shelf.list.getLayoutManager().getDecoratedLeft(selected);
        String label=adapter.labels.get(row);selected.performClick();layout();
        if(column<40) {JSONObject changed=new JSONObject(adapter.rows.get(row).get(column).toString());changed.put("Name","Updated while in details");app.library.put(changed);}
        activity.get().onBackPressed();
        if(animated) awaitAnimatedShelf(row,column);else awaitShelf(row,column);
        HomeAdapter restored=(HomeAdapter)recycler().getAdapter();if(column<40) await(()->"Updated while in details".equals(restored.rows.get(row).get(column).optString("Name")));assertNotSame(adapter,restored);assertEquals(label,restored.labels.get(row));assertEquals(40,restored.rows.get(row).size());
        HomeAdapter.Shelf returned=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(row);View card=returned.list.getLayoutManager().findViewByPosition(column);
        assertTrue(card.hasFocus());assertTrue("Returned viewport moved too far",Math.abs(left-returned.list.getLayoutManager().getDecoratedLeft(card))<=(animated?Ui.dp(activity.get(),12):0));
        if(column<40) {
            String id="row"+row+"film"+column;assertEquals(id,restored.rows.get(row).get(column).optString("Id"));
            java.util.List<JSONObject> reordered=new java.util.ArrayList<>(restored.rows.get(row));java.util.Collections.swap(reordered,0,column);restored.update(row,reordered);
            if(animated) awaitAnimatedShelf(row,0);else awaitShelf(row,0);assertEquals(id,restored.rows.get(row).get(0).optString("Id"));
        }
    }
    @Test public void precacheKeepAwakeSwitchOnlyKeepsTheScreenOnDuringRealCacheWork() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());app.settings.edit().putBoolean("autoPrecache",false).remove("keepAwakeDuringPrecache").apply();
        activity=Robolectric.buildActivity(MainActivity.class).setup();View root=activity.get().findViewById(android.R.id.content);findButton(root,"Einstellungen").performClick();findButton(activity.get().findViewById(android.R.id.content),"Bibliothek und Cache").performClick();layout();
        SettingSwitch setting=(SettingSwitch)findButton(activity.get().findViewById(android.R.id.content),I18n.resource(R.string.precache_keep_awake));assertNotNull(setting);assertFalse(setting.isChecked());
        View stage=org.robolectric.util.ReflectionHelpers.getField(activity.get(),"root");
        try {
            app.syncing=true;org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertFalse(stage.getKeepScreenOn());
            setting.performClick();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertTrue(stage.getKeepScreenOn());
            app.syncing=false;org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertFalse(stage.getKeepScreenOn());
            org.robolectric.util.ReflectionHelpers.setField(app.cacheChanges,"artworkActive",true);org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertTrue(stage.getKeepScreenOn());
            setting.performClick();org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertFalse(stage.getKeepScreenOn());
            assertEquals(0,activity.get().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } finally {app.syncing=false;org.robolectric.util.ReflectionHelpers.setField(app.cacheChanges,"artworkActive",false);}
    }
    private HomeAdapter stressHome() throws Exception {
        recommendationFixture(); app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        app.settings.edit().putString("homeGenres-"+app.account.scope(),"[\"Horror\",\"Thriller\",\"Drama\",\"Fantasy\",\"Action\",\"Animation\"]").apply();
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()-> { if(recycler()==null) return false; HomeAdapter adapter=(HomeAdapter)recycler().getAdapter(); if(adapter.rows.size()!=13) return false; for(java.util.List<JSONObject> row:adapter.rows) if(row.isEmpty()) return false; return true; });
        // Await initial network workers before replacing real rows with synthetic 40-card rows.
        // Include the independent Resume/Next Up and disk hydration executors.
        // Cached nonempty rows do not imply that their fresh network writes have finished.
        java.util.concurrent.CountDownLatch drained=new java.util.concurrent.CountDownLatch(7),release=new java.util.concurrent.CountDownLatch(1);
        Runnable barrier=()->{drained.countDown();try {release.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}};
        for(int i=0;i<4;i++) app.io.execute(barrier);
        for(int i=0;i<2;i++) app.homeIo.execute(barrier);
        app.cacheIo.execute(barrier);
        try {assertTrue("Initial home workers did not drain",drained.await(4,java.util.concurrent.TimeUnit.SECONDS));layout();} finally {release.countDown();}
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
    private void awaitAnimatedShelf(int row,int column) throws Exception {
        await(()-> { HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(row);if(shelf==null) return false;View card=shelf.list.getLayoutManager().findViewByPosition(column);return card!=null&&card.hasFocus()&&!recycler().getLayoutManager().isSmoothScrolling()&&recycler().getScrollState()==RecyclerView.SCROLL_STATE_IDLE&&!shelf.list.getLayoutManager().isSmoothScrolling()&&shelf.list.getScrollState()==RecyclerView.SCROLL_STATE_IDLE; });layout();awaitShelf(row,column);
    }
    @Test public void recommendationHeadingAppearsWhenFullyVisibleBeforeItsRowIsSelected() throws Exception {
        HomeAdapter adapter=stressHome();adapter.requestRow(0);layout();
        HomeAdapter.Shelf first=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2);
        if(first!=null) assertEquals(View.INVISIBLE,((View)first.label.getParent()).getVisibility());
        adapter.requestRow(1);layout();
        await(()-> { HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2);return shelf!=null&&((View)shelf.label.getParent()).getVisibility()==View.VISIBLE; });
        HomeAdapter.Shelf next=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(1);assertTrue(next.list.hasFocus());
        adapter.requestRow(2);layout();awaitShelf(2,0);first=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2);assertEquals(View.VISIBLE,((View)first.label.getParent()).getVisibility());
        adapter.requestRow(0);layout();first=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(2);if(first!=null) assertEquals(View.INVISIBLE,((View)first.label.getParent()).getVisibility());
    }
    @Test public void unchangedHomeSnapshotKeepsPendingNavigationButRefreshesActionMetadata() throws Exception {
        app.settings.edit().putBoolean("scrollAnimations",true).commit();HomeAdapter adapter=stressHome();adapter.requestRow(7);awaitAnimatedShelf(7,0);
        java.util.concurrent.atomic.AtomicInteger changes=new java.util.concurrent.atomic.AtomicInteger();adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver(){ @Override public void onItemRangeChanged(int start,int count,Object payload) { changes.incrementAndGet(); } });
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,20);java.util.List<JSONObject> fresh=stressFilms(7,40);fresh.get(20).put("MediaSources",Json.arr(Json.obj("Id","fresh-source")));adapter.update(7,fresh);awaitAnimatedShelf(7,20);assertEquals(0,changes.get());
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(7);java.util.List<JSONObject> bound=org.robolectric.util.ReflectionHelpers.getField(shelf.list.getAdapter(),"data");assertSame(fresh.get(20),bound.get(20));
        adapter.labels.set(7,"Neuer Reihentitel");adapter.update(7,fresh);layout();assertTrue(changes.get()>0);assertEquals("Neuer Reihentitel",shelf.label.getText().toString());
        int previous=changes.get();java.util.List<JSONObject> watched=stressFilms(7,40);watched.get(20).put("UserData",Json.obj("Played",true));adapter.update(7,watched);layout();assertTrue(changes.get()>previous);
    }
    @Test public void hiddenSidebarStatusDoesNoTextWorkAndOpeningItShowsTheLatestValueImmediately() throws Exception {
        HomeAdapter adapter=stressHome();TextView status=org.robolectric.util.ReflectionHelpers.getField(activity.get(),"status");java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();status.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){writes.incrementAndGet();}public void afterTextChanged(android.text.Editable value){}});
        app.syncing=true;app.syncSummary="Aktueller Status";org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertEquals(0,writes.get());
        activity.get().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU));layout();assertEquals("Aktueller Status",status.getText().toString());int current=writes.get();assertTrue(current>0);
        for(int i=0;i<5;i++) org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertEquals(current,writes.get());app.syncSummary="Neuer Status";org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity.get(),"updateStatus");assertEquals("Neuer Status",status.getText().toString());assertEquals(current+1,writes.get());app.syncing=false;
    }
    @Test public void OptionalScrollKeepsRapidHomeNavigationAndSingleShelfSettledCorrectly() throws Exception {
        app.settings.edit().putBoolean("scrollAnimations",true).putBoolean("animations",true).apply();HomeAdapter adapter=stressHome();adapter.requestRow(7);awaitAnimatedShelf(7,0);
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,20);awaitAnimatedShelf(7,20);
        remote(KeyEvent.KEYCODE_DPAD_DOWN,20);awaitAnimatedShelf(12,20);
        remote(KeyEvent.KEYCODE_DPAD_UP,5);awaitAnimatedShelf(7,20);
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,30);awaitAnimatedShelf(7,40);remote(KeyEvent.KEYCODE_DPAD_RIGHT,5);awaitAnimatedShelf(7,40);
    }
    @Test public void AnimatedShelfContentUpdatesClampPendingFocusToNewCards() throws Exception {
        app.settings.edit().putBoolean("scrollAnimations",true).putBoolean("animations",true).apply();HomeAdapter adapter=stressHome();adapter.requestRow(7);awaitAnimatedShelf(7,0);
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,35);adapter.update(7,stressFilms(7,3));awaitAnimatedShelf(7,3);
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
        assertNull(findButton(root,"TV-Kanal · Fortsetzen"));
        findButton(root,"Android-TV-Hauptmenü").performClick(); layout(); root=activity.get().findViewById(android.R.id.content);
        assertNotNull(findButton(root,"TV-Kanal · Fortsetzen"));
        for(int i=1;i<=5;i++) assertNotNull(findButton(root,"TV-Kanal · Film-Empfehlungen "+i));
    }
    @Test public void dialogTransparencyPreviewsWithoutSavingUntilConfirmedAndRestoresSettingFocus() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());activity=Robolectric.buildActivity(MainActivity.class).setup();
        View root=activity.get().findViewById(android.R.id.content);findButton(root,"Einstellungen").performClick();layout();findButton(activity.get().findViewById(android.R.id.content),"Darstellung").performClick();layout();
        String label="Dialog-Hintergrundtransparenz";Button entry=findButton(activity.get().findViewById(android.R.id.content),label+": 0 %");entry.requestFocus();entry.performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();SeekBar slider=(SeekBar)find(dialog.getWindow().getDecorView(),v->v instanceof SeekBar);shadowOf(Looper.getMainLooper()).idle();assertTrue(slider.hasFocus());slider.setProgress(45);assertEquals(0,app.settings.getInt("dialogTransparency",0));dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(0,app.settings.getInt("dialogTransparency",0));
        findButton(activity.get().findViewById(android.R.id.content),label+": 0 %").performClick();dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();slider=(SeekBar)find(dialog.getWindow().getDecorView(),v->v instanceof SeekBar);slider.setProgress(35);screenshotDialog(dialog,"compact-dialog-transparency-hd.png");dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();layout();assertEquals(35,app.settings.getInt("dialogTransparency",0));Button setting=findButton(activity.get().findViewById(android.R.id.content),label+": 35 %");assertNotNull(setting);assertTrue(setting.hasFocus());
    }
    @Test public void reorderedGenreAndRecommendationShelvesKeepSingleRowNavigationAndFreshLabels() throws Exception {
        Account account=new Account(server.url("/").toString(),"user","token","Rico","device");java.util.List<String> keys=HomeLayout.keys(java.util.Arrays.asList("Horror","Thriller","Drama","Fantasy","Action","Animation"),false);java.util.Collections.reverse(keys);app.settings.edit().putString("homeOrder-"+account.scope(),new JSONArray(keys).toString()).apply();
        HomeAdapter adapter=stressHome();adapter.requestRow(7);layout();awaitShelf(adapter.position(7),0);
        remote(KeyEvent.KEYCODE_DPAD_RIGHT,5);layout();awaitShelf(adapter.position(7),5);
        remote(KeyEvent.KEYCODE_DPAD_DOWN,1);layout();awaitShelf(adapter.position(6),5);
        adapter.labels.set(6,"Dynamischer neuer Empfehlungstitel");adapter.update(6,stressFilms(6,40));layout();awaitShelf(adapter.position(6),5);
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(adapter.position(6));assertEquals("Dynamischer neuer Empfehlungstitel",shelf.label.getText().toString());
        remote(KeyEvent.KEYCODE_DPAD_UP,1);layout();awaitShelf(adapter.position(7),5);
    }
    @Test public void animatedDetailReturnRestoresTheSameLibraryCard() throws Exception {
        app.settings.edit().putBoolean("animations",true).putBoolean("scrollAnimations",true).apply();returningFromFilmDetailsRestoresTheSelectedLibraryCardAndViewport();
    }
    @Test public void returningFromFilmDetailsRestoresTheSelectedLibraryCardAndViewport() throws Exception {
        Dispatcher original=server.getDispatcher();java.util.List<JSONObject> films=new java.util.ArrayList<>();for(int i=0;i<60;i++) films.add(Json.obj("Id","focus-"+i,"Type","Movie","Name",String.format("Film %02d",i),"ParentId","films","ImageTags",Json.obj("Primary","poster")));
        server.setDispatcher(new Dispatcher(){ @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            String path=request.getRequestUrl().encodedPath();if(path.equals("/Users/user/Items")&&"films".equals(request.getRequestUrl().queryParameter("ParentId"))) return new MockResponse().setBody(Json.obj("Items",new JSONArray(films),"TotalRecordCount",films.size()).toString());
            if(path.startsWith("/Users/user/Items/focus-")) return new MockResponse().setBody(films.get(Integer.parseInt(path.substring(path.lastIndexOf('-')+1))).toString());return original.dispatch(request);
        }});
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());activity=Robolectric.buildActivity(MainActivity.class).setup();await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&recycler().getAdapter().getItemCount()==60&&((MediaAdapter)recycler().getAdapter()).fresh);
        layout();recycler().clearFocus();((GridLayoutManager)recycler().getLayoutManager()).scrollToPositionWithOffset(40,17);layout();
        await(()->recycler().findViewHolderForAdapterPosition(41)!=null);
        View selected=recycler().findViewHolderForAdapterPosition(41).itemView;selected.requestFocus();int top=selected.getTop();selected.performClick();
        await(()->findButton(activity.get().findViewById(android.R.id.content),"Abspielen")!=null);
        activity.get().onBackPressed();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&recycler().findViewHolderForAdapterPosition(41)!=null&&recycler().findViewHolderForAdapterPosition(41).itemView.hasFocus());for(int settle=0;settle<5;settle++) { layout();shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20)); }assertEquals(top,recycler().findViewHolderForAdapterPosition(41).itemView.getTop());
        await(()->((MediaAdapter)recycler().getAdapter()).fresh);layout();assertTrue(recycler().findViewHolderForAdapterPosition(41).itemView.hasFocus());
    }
    @Test public void homeLibrariesUseThumbsWideFramesAndOpenTheScopedLibrary() throws Exception {
        app.settings.edit().putBoolean("homeLibraries",true).putBoolean("animations",false).apply();Account account=new Account(server.url("/").toString(),"user","token","Rico","device");app.setAccount(account);app.library.state("lastSync",""+System.currentTimeMillis());
        java.util.List<String> keys=HomeLayout.keys(HomeGenres.read(app.settings,account),true);for(int i=1;i<keys.size();i++) HomeLayout.move(app.settings,account,keys,"libraries",-1);
        activity=Robolectric.buildActivity(MainActivity.class).setup();
        await(()-> { if(recycler()==null||!(recycler().getAdapter() instanceof HomeAdapter)) return false;HomeAdapter adapter=(HomeAdapter)recycler().getAdapter();return adapter.libraries(adapter.logical(0))&&adapter.rows.get(adapter.logical(0)).size()==2; });
        HomeAdapter adapter=(HomeAdapter)recycler().getAdapter();java.util.List<JSONObject> libraries=adapter.rows.get(adapter.logical(0));
        libraries.get(0).put("ImageTags",Json.obj("Primary","poster","Thumb","library-thumb"));adapter.libraries(libraries);adapter.requestRow(adapter.logical(0));
        await(()-> { HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);return shelf!=null&&shelf.list.findViewHolderForAdapterPosition(0)!=null&&shelf.list.findViewHolderForAdapterPosition(0).itemView.hasFocus(); });
        HomeAdapter.Shelf shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);View card=shelf.list.findViewHolderForAdapterPosition(0).itemView;ArtworkView image=(ArtworkView)find(card,v->v instanceof ArtworkView);
        assertEquals(16.0/9,(double)image.getWidth()/image.getHeight(),0.02);assertTrue(app.api.image(libraries.get(0),240,true).contains("/Images/Thumb"));assertEquals(2,shelf.list.getAdapter().getItemCount());
        remote(KeyEvent.KEYCODE_DPAD_DOWN,1);await(()-> { HomeAdapter.Shelf row=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(adapter.position(0));return row!=null&&row.list.hasFocus(); });
        remote(KeyEvent.KEYCODE_DPAD_UP,1);await(()-> { HomeAdapter.Shelf row=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);return row!=null&&row.list.hasFocus(); });
        shelf=(HomeAdapter.Shelf)recycler().findViewHolderForAdapterPosition(0);shelf.list.findViewHolderForAdapterPosition(0).itemView.performClick();await(()->recycler().getAdapter() instanceof MediaAdapter&&((MediaAdapter)recycler().getAdapter()).fresh&&recycler().getAdapter().getItemCount()>0);
        assertEquals("Meine Filme",findText(activity.get().findViewById(android.R.id.content),"Meine Filme").getText().toString());assertEquals("Movie",((MediaAdapter)recycler().getAdapter()).items.get(0).optString("Type"));
        activity.get().onBackPressed();await(()->recycler()!=null&&recycler().getAdapter() instanceof HomeAdapter);assertTrue(((HomeAdapter)recycler().getAdapter()).libraries(((HomeAdapter)recycler().getAdapter()).logical(0)));
    }
    @Test public void homeOrderingDialogMovesRowsAndRetainsTheSelectedControl() throws Exception {
        Account account=new Account(server.url("/").toString(),"user","token","Rico","device");app.setAccount(account);app.library.state("lastSync",""+System.currentTimeMillis());activity=Robolectric.buildActivity(MainActivity.class).setup();
        findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick();layout();findButton(activity.get().findViewById(android.R.id.content),"Startseite").performClick();layout();
        findButton(activity.get().findViewById(android.R.id.content),"Reihenfolge der Startseitenzeilen").performClick();android.app.AlertDialog order=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();shadowOf(Looper.getMainLooper()).idle();
        Button next=findButton(order.getWindow().getDecorView(),"2 · Als Nächstes");assertNotNull(next);next.performClick();android.app.AlertDialog direction=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        direction.getListView().performItemClick(direction.getListView().getChildAt(0),0,0);shadowOf(Looper.getMainLooper()).idle();
        assertEquals("next",HomeLayout.read(app.settings,account,HomeLayout.keys(HomeGenres.read(app.settings,account),true)).get(0));assertNotNull(findButton(order.getWindow().getDecorView(),"1 · Als Nächstes"));
    }
    @Test public void manualPreloadIsDisabledAfterCompletionAndEnabledAfterClearing() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());app.library.state("precacheComplete","true");
        activity=Robolectric.buildActivity(MainActivity.class).setup();View root=activity.get().findViewById(android.R.id.content);findButton(root,"Einstellungen").performClick();layout();findButton(root,"Bibliothek und Cache").performClick();layout();
        Button preload=findButton(root,"Bibliothek und Cover vollständig vorladen");assertNotNull(preload);
        await(()->preload.getAlpha()==.4f);assertFalse(preload.isEnabled());
        app.library.resetPrecacheCompletion();java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updatePreloadButton");update.setAccessible(true);update.invoke(activity.get());
        await(preload::isEnabled);assertEquals(1f,preload.getAlpha(),0);
    }
    @Test public void entirePrecacheDeletionRequiresConfirmationAndCancelKeepsTheLibrary() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());app.library.put(movie);activity=Robolectric.buildActivity(MainActivity.class).setup();View root=activity.get().findViewById(android.R.id.content);findButton(root,"Einstellungen").performClick();layout();findButton(activity.get().findViewById(android.R.id.content),"Bibliothek und Cache").performClick();layout();
        Button clear=findButton(activity.get().findViewById(android.R.id.content),"Gesamten Bibliotheks-Precache löschen");assertNotNull(clear);clear.requestFocus();clear.performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(app.library.item("movie"));dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();assertNotNull(app.library.item("movie"));assertFalse(app.clearingCache);
    }
    @Test public void fullPrecacheResetReplacesCacheWorkersAndKeepsAccountRatingsRulesAndSettings() throws Exception {
        app.settings.edit().putBoolean("autoPrecache",false).putBoolean("hdrPlayback",true).apply();Account account=new Account(server.url("/").toString(),"user","token","Rico","device");app.setAccount(account);Library old=app.library;Images images=app.images;old.put(movie);old.state("home-0",Json.arr(movie).toString());old.state("ratings-values-movie","saved-rating");old.companionState("movie",Json.obj("revision",1,"resumeTicks",1234));
        java.io.File cover=new java.io.File(images.directory,"test.jpg");try(java.io.OutputStream out=new java.io.FileOutputStream(cover)) {out.write(posterImage);}
        java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();java.util.concurrent.atomic.AtomicReference<Exception> error=new java.util.concurrent.atomic.AtomicReference<>();app.clearPrecache(()->done.set(true),error::set);long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(!done.get()&&error.get()==null&&System.nanoTime()<until) { shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10); }assertNull(error.get());assertTrue(done.get());assertSame(account,app.account);assertNotSame(old,app.library);assertNotSame(images,app.images);assertEquals(0,app.library.count());assertEquals("saved-rating",app.library.state("ratings-values-movie"));assertEquals(1234,app.library.companionState("movie").optLong("resumeTicks"));assertTrue(app.settings.getBoolean("hdrPlayback",false));assertFalse(cover.exists());assertFalse(app.clearingCache);old.put(movie);assertNull(app.library.item("movie"));
    }
    @Test public void LibraryEntryFocusStaysOnFirstMediumAndLetterJumpReachesLaterPages() throws Exception {
        Dispatcher original=server.getDispatcher();
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            if(request.getRequestUrl().encodedPath().equals("/Users/user/Items")&&"films".equals(request.getRequestUrl().queryParameter("ParentId"))) {
                if("0".equals(request.getRequestUrl().queryParameter("Limit"))) return new MockResponse().setBody(Json.obj("Items",new JSONArray(),"TotalRecordCount",request.getRequestUrl().queryParameter("NameStartsWithOrGreater")==null?162:89).toString());
                int start=Integer.parseInt(request.getRequestUrl().queryParameter("StartIndex"));JSONArray values=new JSONArray();for(int n=start;n<Math.min(162,start+61);n++) values.put(Json.obj("Id","letter-"+n,"Type","Movie","Name",n<73?"Alpha "+n:"Mango "+n));
                return new MockResponse().setBody(Json.obj("Items",values,"TotalRecordCount",162).toString());
            }return original.dispatch(request);
        } });
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device"));app.library.state("lastSync",""+System.currentTimeMillis());activity=Robolectric.buildActivity(MainActivity.class).setup();await(()->findButton(activity.get().findViewById(android.R.id.content),"Meine Filme")!=null);
        findButton(activity.get().findViewById(android.R.id.content),"Meine Filme").performClick();await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&((MediaAdapter)recycler().getAdapter()).fresh&&recycler().getAdapter().getItemCount()==60);layout();assertTrue(recycler().findViewHolderForAdapterPosition(0).itemView.hasFocus());
        Button letters=findButton(activity.get().findViewById(android.R.id.content),"Zu Buchstaben springen");assertNotNull(letters);assertFalse(letters.hasFocus());screenshot("library-letter-header-hd.png");letters.performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        View dialogRoot=dialog.getWindow().getDecorView();shadowOf(Looper.getMainLooper()).idle();dialogRoot.measure(View.MeasureSpec.makeMeasureSpec(dialog.getWindow().getAttributes().width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));dialogRoot.layout(0,0,dialogRoot.getMeasuredWidth(),dialogRoot.getMeasuredHeight());Bitmap dialogImage=Bitmap.createBitmap(dialogRoot.getWidth(),dialogRoot.getHeight(),Bitmap.Config.ARGB_8888);dialogRoot.draw(new Canvas(dialogImage));try(java.io.OutputStream out=new java.io.FileOutputStream("build/qa/library-letter-menu-hd.png")) { dialogImage.compress(Bitmap.CompressFormat.PNG,100,out); }dialogImage.recycle();findButton(dialogRoot,"M").performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&((MediaAdapter)recycler().getAdapter()).fresh&&((MediaAdapter)recycler().getAdapter()).items.get(0).optString("Id").equals("letter-60"));await(()->recycler().findViewHolderForAdapterPosition(13)!=null&&recycler().findViewHolderForAdapterPosition(13).itemView.hasFocus());assertEquals("Mango 73",((MediaAdapter)recycler().getAdapter()).items.get(13).optString("Name"));
    }
    @Test public void AnimatedLibraryPagingKeepsFooterAndReturnFocus() throws Exception {
        app.settings.edit().putBoolean("scrollAnimations",true).putBoolean("animations",true).apply();PagingLookaheadDoesNotDuplicateItemsOrOfferAnEmptyFinalPage();
    }
    @Test public void PagingLookaheadDoesNotDuplicateItemsOrOfferAnEmptyFinalPage() throws Exception {
        Dispatcher original=server.getDispatcher(); java.util.concurrent.atomic.AtomicInteger available=new java.util.concurrent.atomic.AtomicInteger(61);
        server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            if(request.getRequestUrl().encodedPath().equals("/Users/user/Items")&&"films".equals(request.getRequestUrl().queryParameter("ParentId"))) {
                String offset=request.getRequestUrl().queryParameter("StartIndex"),size=request.getRequestUrl().queryParameter("Limit"); int start=offset==null?0:Integer.parseInt(offset),limit=size==null?60:Integer.parseInt(size); JSONArray page=new JSONArray();
                for(int index=start;index<Math.min(available.get(),start+limit);index++) page.put(Json.obj("Id","paged-"+index,"Type","Movie","Name",String.format("Film %03d",index)));
                return new MockResponse().setBody(Json.obj("Items",page,"TotalRecordCount",available.get()).toString());
            }
            return original.dispatch(request);
        }});
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); View root=activity.get().findViewById(android.R.id.content); await(()->findButton(root,"Meine Filme")!=null); findButton(root,"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&((MediaAdapter)recycler().getAdapter()).fresh&&recycler().getAdapter().getItemCount()==60); layout();
        assertFalse(findButton(root,"← Vorherige Seite").isShown()); assertTrue(findButton(root,"Nächste Seite →").isShown());
        layout(); recycler().clearFocus(); ((GridLayoutManager)recycler().getLayoutManager()).scrollToPositionWithOffset(59,0); layout(); await(()->recycler().findViewHolderForAdapterPosition(59)!=null); recycler().findViewHolderForAdapterPosition(59).itemView.requestFocus();
        remote(KeyEvent.KEYCODE_DPAD_DOWN,1); layout(); assertTrue(findButton(root,"Nächste Seite →").hasFocus());
        remote(KeyEvent.KEYCODE_DPAD_UP,1); layout(); assertTrue(recycler().findViewHolderForAdapterPosition(59).itemView.hasFocus());
        findButton(root,"Nächste Seite →").performClick(); await(()->recycler()!=null&&recycler().getAdapter().getItemCount()==1); layout();
        assertEquals("paged-60",((MediaAdapter)recycler().getAdapter()).items.get(0).optString("Id")); assertTrue(findButton(root,"← Vorherige Seite").isShown()); assertFalse(findButton(root,"Nächste Seite →").isShown());
        recycler().findViewHolderForAdapterPosition(0).itemView.requestFocus(); remote(KeyEvent.KEYCODE_DPAD_DOWN,1); layout();
        Button previous=findButton(root,"← Vorherige Seite"); assertTrue(previous.hasFocus()); java.lang.reflect.Method restore=MainActivity.class.getDeclaredMethod("restoreFocus"); restore.setAccessible(true); restore.invoke(activity.get()); layout(); assertTrue(previous.hasFocus()); assertEquals(1f,previous.getScaleX(),0); assertEquals(1f,previous.getScaleY(),0); screenshot("paging-previous-focus-hd.png");
        remote(KeyEvent.KEYCODE_DPAD_UP,1); layout(); assertTrue(recycler().findViewHolderForAdapterPosition(0).itemView.hasFocus());
        available.set(60); findButton(root,"← Vorherige Seite").performClick(); await(()->recycler()!=null&&recycler().getAdapter() instanceof MediaAdapter&&((MediaAdapter)recycler().getAdapter()).fresh&&recycler().getAdapter().getItemCount()==60); layout();
        assertFalse(findButton(root,"← Vorherige Seite").isShown()); assertFalse(findButton(root,"Nächste Seite →").isShown());
    }
    @Test public void SinglePageListsHidePagingAndSkipSettingsContainOnlySeparateControls() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        activity=Robolectric.buildActivity(MainActivity.class).setup(); View root=activity.get().findViewById(android.R.id.content);
        await(()->findButton(root,"Meine Filme")!=null); findButton(root,"Meine Filme").performClick();
        await(()->recycler()!=null&&recycler().getAdapter().getItemCount()>0); layout();
        assertFalse(findButton(root,"← Vorherige Seite").isShown()); assertFalse(findButton(root,"Nächste Seite →").isShown());
        findButton(root,"Einstellungen").performClick(); findButton(root,"Wiedergabe").performClick(); layout();
        assertNull(findButton(root,"Intros und Outros automatisch überspringen"));
        assertTrue(findButton(root,"Intros automatisch überspringen") instanceof SettingSwitch); assertTrue(findButton(root,"Outros automatisch überspringen") instanceof SettingSwitch);
        findButton(root,"Auflösung: 720p").performClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog.getListView()); assertNotNull(dialog.getWindow().getDecorView());
        screenshotDialog(dialog,"lowfin-dialog-resolution-hd.png");
    }
    private void screenshotDialog(android.app.AlertDialog dialog,String filename) throws Exception {
        shadowOf(Looper.getMainLooper()).idle(); View decor=dialog.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(dialog.getWindow().getAttributes().width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.AT_MOST)); decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
        Bitmap image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888); decor.draw(new Canvas(image)); File output=new File("build/qa/"+filename); output.getParentFile().mkdirs(); try(FileOutputStream stream=new FileOutputStream(output)) { image.compress(Bitmap.CompressFormat.PNG,100,stream); }
    }
    @Test public void PlaybackSwitchesToggleBothWaysWithoutRebuildingFocusAndPreserveVideoBudget() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis());
        app.settings.edit().putInt("videoCacheMb",1024).apply();
        activity=Robolectric.buildActivity(MainActivity.class).setup(); View root=activity.get().findViewById(android.R.id.content);
        findButton(root,"Einstellungen").performClick(); findButton(root,"Wiedergabe").performClick(); layout();
        SettingSwitch stereo=(SettingSwitch)findButton(root,"Audio auf Stereo (2.0) heruntermischen"); stereo.requestFocus();
        stereo.performClick(); assertFalse(app.settings.getBoolean("stereo",true)); assertTrue(stereo.hasFocus());
        stereo.performClick(); assertTrue(app.settings.getBoolean("stereo",false)); assertTrue(stereo.hasFocus());
        SettingSwitch cache=(SettingSwitch)findButton(root,"Videocache auf dem Gerätespeicher"); cache.performClick();
        assertEquals(0,app.settings.getInt("videoCacheMb",-1)); assertFalse(findButton(root,"Videocache: 1024 MB").isEnabled());
        cache.performClick(); assertEquals(1024,app.settings.getInt("videoCacheMb",-1)); assertTrue(findButton(root,"Videocache: 1024 MB").isEnabled()); screenshot("playback-switches-hd.png");
    }
    @Test public void ratingSettingsShowAllThreeKeyFieldsAndSourceToggles() throws Exception {
        app.setAccount(new Account(server.url("/").toString(),"user","token","Rico","device")); app.library.state("lastSync",""+System.currentTimeMillis()); activity=Robolectric.buildActivity(MainActivity.class).setup();
        findButton(activity.get().findViewById(android.R.id.content),"Einstellungen").performClick(); findButton(activity.get().findViewById(android.R.id.content),"API").performClick(); layout();
        View root=activity.get().findViewById(android.R.id.content); assertNotNull(findButton(root,"OMDb · API-Key bearbeiten")); assertNotNull(findButton(root,"MDBList · API-Key bearbeiten")); assertNotNull(findButton(root,"TMDB · API-Key bearbeiten"));
        SettingSwitch imdb=(SettingSwitch)findButton(root,"IMDb"); assertTrue(imdb.isChecked()); imdb.performClick(); assertFalse(imdb.isChecked()); imdb.performClick(); assertTrue(imdb.isChecked()); screenshot("rating-settings-hd.png");
        findButton(root,"OMDb · API-Key bearbeiten").performClick(); android.app.AlertDialog dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); assertNotNull(find(dialog.getWindow().getDecorView(),v->v instanceof EditText));
        assertNotNull(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)); dialog.dismiss();
        root=activity.get().findViewById(android.R.id.content); assertNotNull(findText(root,"Tracearr"));
        assertEquals(1,countButtons(root,"Tracearr-Verbindung bearbeiten")); assertEquals(1,countButtons(root,"TXT-Datei auswählen")); assertEquals(1,countButtons(root,"API-Key-Datei neu erstellen"));
        assertNull(findButton(root,"Tracearr · Wiedergabestatistiken"));
        findButton(root,"Tracearr-Verbindung bearbeiten").performClick(); dialog=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog(); assertNotNull(find(dialog.getWindow().getDecorView(),v->v instanceof EditText)); dialog.dismiss();
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
        root.measure(View.MeasureSpec.makeMeasureSpec(viewportWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(viewportHeight,View.MeasureSpec.EXACTLY)); root.layout(0,0,viewportWidth,viewportHeight); shadowOf(Looper.getMainLooper()).idle();
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
        findButton(activity.get().findViewById(android.R.id.content),"Konto und Sprache").performClick(); layout();
        findButton(activity.get().findViewById(android.R.id.content),"Sprache: Deutsch").performClick(); android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(); dialog.getListView().performItemClick(null,0,0); layout();
        assertEquals("en",I18n.language()); activity.get().onBackPressed(); layout(); View root=activity.get().findViewById(android.R.id.content); assertNotNull(findButton(root,"Genre rows")); screenshot("settings-english-hd.png");
        findButton(root,"Genre rows").performClick(); layout();
        SettingSwitch horror=(SettingSwitch)find(activity.get().findViewById(android.R.id.content),v->v instanceof SettingSwitch&&"Horror".contentEquals(((SettingSwitch)v).getText())); assertNotNull(horror); assertTrue(horror.isChecked()); horror.setChecked(false); assertFalse(HomeGenres.read(app.settings,app.account).contains("Horror")); screenshot("genre-settings-english-hd.png");
        activity.get().onBackPressed(); layout(); assertNotNull(findButton(activity.get().findViewById(android.R.id.content),"Genre rows"));
    }
    private void screenshot(String name) throws Exception {
        layout(); View root=activity.get().getWindow().getDecorView(); Bitmap bitmap=Bitmap.createBitmap(viewportWidth,viewportHeight,Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        File dir=new File("build/qa"); dir.mkdirs(); try(OutputStream out=new FileOutputStream(new File(dir,name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle();
    }
    private RecyclerView recycler() { return (RecyclerView)find(activity.get().findViewById(android.R.id.content),v->v instanceof RecyclerView); }
    private int countButtons(View view,String text) {
        int count=view instanceof Button&&text.equals(((Button)view).getText().toString())?1:0;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) count+=countButtons(((ViewGroup)view).getChildAt(i),text);
        return count;
    }
    private Button findButton(View root,String text) { return (Button)find(root,v->v instanceof Button&&text.equals(((Button)v).getText().toString())); }
    private TextView findText(View root,String text) { return (TextView)find(root,v->v instanceof TextView&&text.equals(((TextView)v).getText().toString())); }
    private View find(View root,java.util.function.Predicate<View> match) { if(match.test(root)) return root; if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View r=find(((ViewGroup)root).getChildAt(i),match); if(r!=null) return r; } return null; }
}
