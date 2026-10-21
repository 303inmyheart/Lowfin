package de.lowfin.tv;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CoverTagsTest {
    private static final String FIRST="11111111111111111111111111111111",SECOND="22222222222222222222222222222222";
    private LowfinApp app;private MockWebServer server;private Library db;private CompanionCoverTags tags;
    private final AtomicInteger batches=new AtomicInteger();
    private volatile boolean feature=true,expanded;
    private volatile int firstMask=3;
    private final AtomicInteger indexPending=new AtomicInteger();
    private volatile CountDownLatch entered,release;
    @Before public void setup() throws Exception {
        app=(LowfinApp)RuntimeEnvironment.getApplication();app.settings.edit().clear().commit();
        server=new MockWebServer();server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) {
            if(request.getPath().contains("capabilities")) return new MockResponse().setBody(Json.obj("available",true,"apiVersion",1,"imageBadges",Json.obj("enabled",feature,"version",1,"dvd",true,"surround",true,"hdr",true,"labelVersion",expanded?1:0,"tags",expanded?Json.arr("dvd","5.1","hdr","4k","dv","hevc","languages","subtitles"):new JSONArray())).toString());
            if(request.getPath().contains("/badges?")) { batches.incrementAndGet();if(indexPending.getAndUpdate(n->Math.max(0,n-1))>0) return new MockResponse().setBody(Json.obj("items",new JSONArray(),"pending",true).toString());int current=firstMask;CountDownLatch wait=release;if(wait!=null) { entered.countDown();try { wait.await(4,TimeUnit.SECONDS); } catch(InterruptedException error) { Thread.currentThread().interrupt(); } }return new MockResponse().setBody(Json.obj("items",Json.arr(Json.obj("id",FIRST,"mask",current,"tags",Json.arr("4k","dv","hevc","5.1","stereo","lang:en","vost:de")),Json.obj("id",SECOND,"mask",1,"tags",Json.arr("dvd")))).toString()); }
            return new MockResponse().setResponseCode(404);
        }});server.start();app.api=new Api(new Account(server.url("/").toString(),"user","token","name","device"));
        db=new Library(app,"cover-tag-test");app.library=db;app.companion=new CompanionApi(app,app.api,db);assertTrue(app.companion.refreshDiscovery());tags=new CompanionCoverTags(app,app.api);
    }
    @After public void close() throws Exception { if(release!=null) release.countDown();tags.close();server.shutdown();db.close();app.companion=null;app.api=null;app.library=null; }
    private JSONObject episode(String id) { return Json.obj("Id",id,"Type","Episode","ImageTags",Json.obj("Primary","image")); }
    private int mask(ArtworkView view) { return org.robolectric.util.ReflectionHelpers.getField(view,"badgeMask"); }
    private void await(java.util.function.BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        do { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));if(condition.getAsBoolean()) return;Thread.sleep(5); } while(System.nanoTime()<until);fail("Tag result did not arrive");
    }
    @Test public void VisibleItemsShareBatchPersistAndDoNotChangeCoverCacheKeys() throws Exception {
        ArtworkView one=new ArtworkView(app),two=new ArtworkView(app);String before=app.api.image(episode(FIRST),320,true);
        tags.bind(one,episode(FIRST),true);tags.bind(two,episode(SECOND),true);await(()->mask(one)==3&&mask(two)==1);assertEquals(1,batches.get());
        assertEquals(before,app.api.image(episode(FIRST),320,true));assertEquals("3",db.state("cover-tags-v2-"+FIRST));
        tags.close();tags=new CompanionCoverTags(app,app.api);ArtworkView restored=new ArtworkView(app);tags.bind(restored,episode(FIRST),true);await(()->mask(restored)==3);assertEquals(1,batches.get());
    }
    @Test public void MainThreadBindingAndBatchDispatchDoNotWaitForThePersistenceLock() throws Exception {
        Object gate=org.robolectric.util.ReflectionHelpers.getField(tags,"gate");CountDownLatch locked=new CountDownLatch(1),unlock=new CountDownLatch(1);
        Thread writer=new Thread(()-> {synchronized(gate) {locked.countDown();try {unlock.await(4,TimeUnit.SECONDS);}catch(InterruptedException error) {Thread.currentThread().interrupt();}}});writer.start();assertTrue(locked.await(1,TimeUnit.SECONDS));
        try {
            long began=System.nanoTime();tags.bind(new ArtworkView(app),episode(FIRST),true);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
            assertTrue("UI binding waited for the database writer",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)<500);
        } finally {unlock.countDown();writer.join(2000);}
    }
    @Test public void PersistentFactsAreReadWithoutWritingThemBackAgain() throws Exception {
        db.state("cover-tags-v2-"+FIRST,"3");
        db.getWritableDatabase().execSQL("CREATE TABLE tag_write_audit (value INTEGER)");
        db.getWritableDatabase().execSQL("CREATE TRIGGER audit_tag_insert AFTER INSERT ON state WHEN NEW.key LIKE 'cover-tags-v2-%' BEGIN INSERT INTO tag_write_audit VALUES (1); END");
        ArtworkView view=new ArtworkView(app);tags.bind(view,episode(FIRST),true);await(()->mask(view)==3);
        try(android.database.Cursor rows=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tag_write_audit",null)) {assertTrue(rows.moveToFirst());assertEquals(0,rows.getInt(0));}
        assertEquals(0,batches.get());
    }
    @Test public void ExpandedTagsRespectServerSelectionLocalHdrAndPersistWithoutAnotherRequest() throws Exception {
        expanded=true;app.companion.refreshDiscovery();ArtworkView view=new ArtworkView(app);tags.bind(view,episode(FIRST),true);
        await(()->((String[])org.robolectric.util.ReflectionHelpers.getField(view,"badgeLabels")).length>0);
        java.util.List<String> labels=java.util.Arrays.asList((String[])org.robolectric.util.ReflectionHelpers.getField(view,"badgeLabels"));
        assertTrue(labels.contains("4k"));assertTrue(labels.contains("lang:en"));assertFalse(labels.contains("dv"));assertFalse(labels.contains("stereo"));assertTrue(db.state("cover-tag-labels-v1-"+FIRST).contains("hevc"));
        app.settings.edit().putBoolean("hdrPlayback",true).commit();await(()->java.util.Arrays.asList((String[])org.robolectric.util.ReflectionHelpers.getField(view,"badgeLabels")).contains("dv"));assertEquals(1,batches.get());
        tags.close();tags=new CompanionCoverTags(app,app.api);ArtworkView restored=new ArtworkView(app);tags.bind(restored,episode(FIRST),true);
        await(()->((String[])org.robolectric.util.ReflectionHelpers.getField(restored,"badgeLabels")).length>0);assertEquals(1,batches.get());
        JSONArray changes=Json.arr(Json.obj("item",episode(FIRST),"libraries",new JSONArray()));tags.invalidate(changes);db.applyCacheChanges(Json.obj("cursor","new-labels","changes",changes));assertEquals("",db.state("cover-tag-labels-v1-"+FIRST));tags.changesApplied();
    }
    @Test public void DenseExpandedTagsFitCroppedAndFourKFramesWithoutCoveringTheWholeImage() {
        String[] labels={"4k","dv","hevc","atmos","truehd","dtshdma","7.1","5.1","lang:en","lang:de","lang:fr","lang:ja","vost:es","vost:pt"};
        for(int[] size:new int[][]{{160,90,1},{240,360,1},{640,360,4},{3840,2160,4}}) {
            CoverTags overlay=new CoverTags();overlay.layout(size[0],size[1],0,size[2],labels);assertFalse(overlay.extraBoxes.isEmpty());assertTrue(overlay.extraBoxes.size()<=12);
            for(RectF box:overlay.extraBoxes) {assertTrue(box.left>=0&&box.top>=0&&box.right<=size[0]&&box.bottom<=size[1]);assertTrue(box.top>=size[1]*.4f);}
        }
    }
    @Test public void LocalSwitchImmediatelyHidesAndRestoresTagsWithoutAnotherRequest() throws Exception {
        ArtworkView one=new ArtworkView(app);tags.bind(one,episode(FIRST),true);await(()->mask(one)==3);
        app.settings.edit().putBoolean("showCoverTags",false).commit();await(()->mask(one)==0);
        app.settings.edit().putBoolean("showCoverTags",true).commit();await(()->mask(one)==3);assertEquals(1,batches.get());
    }
    @Test public void AbsentFeatureAndNonMediaArtworkGenerateNoTagRequests() throws Exception {
        feature=false;app.companion.refreshDiscovery();ArtworkView view=new ArtworkView(app);tags.bind(view,episode(FIRST),true);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));assertEquals(0,mask(view));assertEquals(0,batches.get());
        feature=true;app.companion.refreshDiscovery();for(String type:new String[]{"Person","TvChannel","More"}) tags.bind(view,Json.obj("Id",FIRST,"Type",type),false);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));assertEquals(0,batches.get());
    }
    @Test public void RecycledViewCannotReceivePreviousItemsLateTagResult() throws Exception {
        ArtworkView view=new ArtworkView(app);tags.bind(view,episode(FIRST),true);view.badges("",0);await(()->batches.get()==1);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));assertEquals(0,mask(view));assertEquals("",view.badgeItem);
    }
    @Test public void PluginMetadataChangeInvalidatesPersistedFactsAndFencesInflightResults() throws Exception {
        entered=new CountDownLatch(1);release=new CountDownLatch(1);ArtworkView view=new ArtworkView(app);tags.bind(view,episode(FIRST),true);await(()->entered.getCount()==0);
        JSONArray changes=Json.arr(Json.obj("item",episode(FIRST),"libraries",new JSONArray()));
        tags.invalidate(changes);db.applyCacheChanges(Json.obj("cursor","updated","changes",changes));firstMask=1;CountDownLatch old=release;release=null;tags.changesApplied();old.countDown();
        await(()->mask(view)==1);assertEquals("1",db.state("cover-tags-v2-"+FIRST));assertEquals(2,batches.get());
    }
    @Test public void EpisodeUpdatesInvalidateOnlyTheirOwnSeriesAndSeasonFacts() throws Exception {
        String series="33333333333333333333333333333333",season="44444444444444444444444444444444",unrelated="55555555555555555555555555555555";
        for(String id:new String[]{FIRST,series,season,unrelated})db.state("cover-tags-v2-"+id,"1");
        JSONObject episode=episode(FIRST).put("SeriesId",series).put("SeasonId",season);db.applyCacheChanges(Json.obj("cursor","ancestor-update","changes",Json.arr(Json.obj("item",episode,"libraries",new JSONArray()))));
        for(String id:new String[]{FIRST,series,season})assertEquals("",db.state("cover-tags-v2-"+id));assertEquals("1",db.state("cover-tags-v2-"+unrelated));
    }
    @Test public void HdrTagRequiresLocalPlaybackSwitchAndUpdatesWithoutRefetch() throws Exception {
        firstMask=6;ArtworkView view=new ArtworkView(app);tags.bind(view,episode(FIRST),true);await(()->mask(view)==2);assertFalse(app.settings.getBoolean("hdrPlayback",false));
        app.settings.edit().putBoolean("hdrPlayback",true).commit();await(()->mask(view)==6);
        app.settings.edit().putBoolean("hdrPlayback",false).commit();await(()->mask(view)==2);assertEquals(1,batches.get());
    }
    @Test public void PendingServerIndexRetriesVisibleTagsWithoutCachingUnknownAsZero() throws Exception {
        indexPending.set(1);ArtworkView view=new ArtworkView(app);tags.bind(view,episode(FIRST),true);await(()->batches.get()==1);assertEquals("",db.state("cover-tags-v2-"+FIRST));
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6));await(()->mask(view)==3);assertEquals(2,batches.get());
    }
    @Test public void ThreeBadgesStayInsideNarrowFrameAndKeepTheirAspectRatio() {
        CoverTags overlay=new CoverTags();overlay.layout(100,56,7,1.33f);for(RectF box:new RectF[]{overlay.dvd,overlay.surround,overlay.hdr}) {assertFalse(box.isEmpty());assertTrue(box.left>=0&&box.right<=100&&box.top>=0&&box.bottom<=56);assertEquals(31f/16,box.width()/box.height(),.01f);}assertTrue(overlay.dvd.right<overlay.surround.left);assertTrue(overlay.surround.right<overlay.hdr.left);
    }
    @Test public void FourByThreeImageCroppedToWideFrameKeepsBothTagsVisible() throws Exception { verifyFrame("cover-tags-cropped-4x3.png"); }
    @Test @Config(qualifiers="w960dp-h540dp-land-xxxhdpi") public void FourKTagsScaleWithFrameAndUiDensity() throws Exception { verifyFrame("cover-tags-cropped-4x3-4k.png"); }
    private void verifyFrame(String filename) throws Exception {
        ArtworkView view=new ArtworkView(app);view.ratio(16.0/9);view.crop(true);int width=Ui.dp(app,240),height=(int)Math.round(width*9.0/16);
        Bitmap source=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888);source.eraseColor(Color.rgb(60,80,100));view.setImageDrawable(new BitmapDrawable(app.getResources(),source));view.badges(FIRST,3);
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(result));
        CoverTags overlay=org.robolectric.util.ReflectionHelpers.getField(view,"coverTags");
        for(RectF box:new RectF[]{overlay.dvd,overlay.surround}) { assertFalse(box.isEmpty());assertTrue(box.left>0&&box.top>0&&box.right<width&&box.bottom<height);assertNotEquals(source.getPixel(50,50),result.getPixel((int)box.centerX(),(int)(box.bottom-3))); }
        assertEquals(16*app.getResources().getDisplayMetrics().density,overlay.dvd.height(),.1f);
        assertEquals(Color.rgb(60,80,100),result.getPixel(width/2,height/2));assertFalse(view.isFocusable());
        File output=new File("build/qa/"+filename);output.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(output)) { result.compress(Bitmap.CompressFormat.PNG,100,out); }
    }
}
