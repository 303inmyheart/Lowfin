package de.lowfin.tv;

import android.view.*;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class RatingsRowTest {
    private LowfinApp app;private RatingsRow row;
    @Before public void setup() { app=(LowfinApp)RuntimeEnvironment.getApplication();app.settings.edit().clear().putBoolean("animations",false).commit();row=new RatingsRow(app); }
    private int height(int width) { row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));row.layout(0,0,width,row.getMeasuredHeight());return row.getMeasuredHeight(); }
    @Test public void compactBannerSlotsAreSmallerAndStayFixedWhileScoresChange() {
        row.reserve(app);int ordinary=height(900);RatingsRow full=row;row=new RatingsRow(app,true);row.reserve(app);int compact=height(900);assertTrue(compact<ordinary);assertEquals(full.getChildCount(),row.getChildCount());
        View first=row.getChildAt(0);row.show(app,Json.obj("imdb",10,"tomatoes",100,"tmdb",8.1,"mdblist",100));assertEquals(compact,height(900));assertSame(first,row.getChildAt(0));row.clear();assertEquals(compact,height(900));
        for(int i=0;i<row.getChildCount();i++)assertFalse(row.getChildAt(i).isFocusable());
    }
    @Test public void hiddenBannerCanRejectLateResultsWithoutDiscardingSharedCachedScores() throws Exception {
        Library db=new Library(app,"ratings-cancel-"+java.util.UUID.randomUUID());Ratings repository=new Ratings(app.settings,db,new Api(new Account("http://127.0.0.1:1","user","token","User","device")),provider->"","http://127.0.0.1:1/","http://127.0.0.1:1/","http://127.0.0.1:1/");app.ratings=repository;
        java.util.concurrent.ThreadPoolExecutor worker=org.robolectric.util.ReflectionHelpers.getField(repository,"worker");java.util.concurrent.CountDownLatch occupied=new java.util.concurrent.CountDownLatch(2),release=new java.util.concurrent.CountDownLatch(1);
        try {
            db.state("ratings-values-first",Json.obj("scores",Json.obj("imdb",7.8)).toString());org.json.JSONObject film=Json.obj("Id","first","Type","Movie");row=new RatingsRow(app,true);row.reserve(app);
            for(int i=0;i<2;i++)worker.execute(()->{occupied.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(occupied.await(2,java.util.concurrent.TimeUnit.SECONDS));row.load(app,film);row.cancelLoad();release.countDown();await(()->repository.peek(film)!=null);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(((TextView)row.getChildAt(0)).getText().toString().endsWith("—"));row.load(app,film);assertTrue(((TextView)row.getChildAt(0)).getText().toString().contains("7,8"));
        } finally {release.countDown();repository.close();worker.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS);app.ratings=null;db.close();}
    }
    @Test public void detailBadgesKeepTheirViewsForRepeatedScoresAndUpdateChangedValuesAndProviders() {
        row.show(app,Json.obj("imdb",7.8,"tmdb",8.1));height(400);TextView imdb=(TextView)row.getChildAt(0);View tmdb=row.getChildAt(1),shape=imdb;
        android.graphics.drawable.Drawable background=imdb.getBackground(),icon=imdb.getCompoundDrawables()[0];java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();
        imdb.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){writes.incrementAndGet();}public void afterTextChanged(android.text.Editable value){}});
        for(int n=0;n<20;n++) row.show(app,Json.obj("imdb",7.8,"tmdb",8.1));
        assertSame(imdb,row.getChildAt(0));assertSame(tmdb,row.getChildAt(1));assertSame(background,imdb.getBackground());assertSame(icon,imdb.getCompoundDrawables()[0]);assertEquals(0,writes.get());
        row.show(app,Json.obj("imdb",7.9,"tmdb",8.1));assertSame(imdb,row.getChildAt(0));assertEquals(1,writes.get());assertTrue(imdb.getText().toString().contains("7,9"));
        app.settings.edit().putBoolean("rating-show-imdb",false).apply();row.show(app,Json.obj("imdb",7.9,"tomatoes",91,"tmdb",8.1));assertEquals(2,row.getChildCount());assertEquals("tomatoes",row.getChildAt(0).getTag());assertEquals("tmdb",row.getChildAt(1).getTag());assertNotSame(shape,row.getChildAt(0));
        row.show(app,Json.obj());assertEquals(0,row.getChildCount());assertEquals(View.GONE,row.getVisibility());
    }
    @Test public void sameItemRefreshKeepsBadgesButItemSwitchClearsThemAndRejectsLateOldResults() throws Exception {
        Library db=new Library(app,"ratings-row-"+java.util.UUID.randomUUID());Ratings repository=new Ratings(app.settings,db,new Api(new Account("http://127.0.0.1:1","user","token","User","device")),provider->"","http://127.0.0.1:1/","http://127.0.0.1:1/","http://127.0.0.1:1/");app.ratings=repository;
        java.util.concurrent.ThreadPoolExecutor worker=org.robolectric.util.ReflectionHelpers.getField(repository,"worker");java.util.concurrent.CountDownLatch occupied=new java.util.concurrent.CountDownLatch(2),release=new java.util.concurrent.CountDownLatch(1);
        try {
            db.state("ratings-values-first",Json.obj("scores",Json.obj("imdb",7.8)).toString());db.state("ratings-values-second",Json.obj("scores",Json.obj("imdb",8.9)).toString());
            org.json.JSONObject first=Json.obj("Id","first","Type","Movie"),second=Json.obj("Id","second","Type","Movie");row.load(app,first);await(()->row.getChildCount()==1);View badge=row.getChildAt(0);repository.clearMemory();
            for(int i=0;i<2;i++) worker.execute(()-> {occupied.countDown();try {release.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}});assertTrue(occupied.await(2,java.util.concurrent.TimeUnit.SECONDS));
            row.load(app,first);assertSame(badge,row.getChildAt(0));assertEquals(View.VISIBLE,row.getVisibility());row.load(app,second);assertEquals(0,row.getChildCount());assertEquals(View.GONE,row.getVisibility());
            java.util.List<String> added=new java.util.ArrayList<>();row.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View parent,View child){added.add(((TextView)child).getText().toString());}public void onChildViewRemoved(View parent,View child){}});
            release.countDown();await(()->row.getChildCount()==1&&((TextView)row.getChildAt(0)).getText().toString().contains("8,9"));assertEquals(1,added.size());assertTrue(added.get(0).contains("8,9"));assertNotSame(badge,row.getChildAt(0));
        } finally {release.countDown();repository.close();worker.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS);app.ratings=null;db.close();}
    }
    private void await(java.util.function.BooleanSupplier ready) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until) {Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();if(ready.getAsBoolean()) return;Thread.sleep(5);}fail("Rating result did not arrive");
    }
    @Test public void repeatedScoresDoNotRewriteChipsButChangedAndMissingValuesStillUpdate() {
        row.reserve(app);row.show(app,Json.obj("imdb",7.8));TextView chip=(TextView)row.getChildAt(0);java.util.concurrent.atomic.AtomicInteger writes=new java.util.concurrent.atomic.AtomicInteger();chip.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){writes.incrementAndGet();}public void afterTextChanged(android.text.Editable value){}});
        row.show(app,Json.obj("imdb",7.8));assertEquals(0,writes.get());row.show(app,Json.obj("imdb",7.9));assertEquals(1,writes.get());row.show(app,Json.obj());assertEquals(2,writes.get());assertTrue(chip.getText().toString().endsWith("—"));assertEquals(.4f,chip.getAlpha(),0);
    }
    @Test public void fixedSlotsKeepTheirGeometryAcrossLoadingMissingScoresAndFilmChanges() {
        row.reserve(app);int before=height(900);assertEquals(8,row.getChildCount());View chip=row.getChildAt(0);int width=chip.getWidth(),top=chip.getTop();
        row.show(app,Json.obj("imdb",10,"tomatoes",100,"tmdb",8.1));assertEquals(before,height(900));assertSame(chip,row.getChildAt(0));assertEquals(width,chip.getWidth());assertEquals(top,chip.getTop());
        row.clear();assertEquals(before,height(900));assertEquals(View.VISIBLE,row.getVisibility());assertTrue(((TextView)chip).getText().toString().endsWith("—"));assertFalse(((TextView)chip).getText().toString().contains("10,0"));
        row.show(app,Json.obj());assertEquals(before,height(900));
        int narrow=height(400);row.show(app,Json.obj("imdb",7.4,"mdblist",100));assertEquals(narrow,height(400));
    }
    @Test public void sourceSwitchesDetermineReservedSlotsAndAllDisabledRemovesTheRow() {
        for(String id:Ratings.IDS) app.settings.edit().putBoolean("rating-show-"+id,false).commit();row.reserve(app);assertEquals(0,row.getChildCount());assertEquals(View.GONE,row.getVisibility());
        app.settings.edit().putBoolean("rating-show-imdb",true).commit();row.reserve(app);assertEquals(1,row.getChildCount());assertEquals(View.VISIBLE,row.getVisibility());
    }
    @Test public void ordinaryDetailBadgesStillCollapseWithoutScores() {
        row.show(app,Json.obj("imdb",7.8));assertEquals(View.VISIBLE,row.getVisibility());row.clear();assertEquals(View.GONE,row.getVisibility());assertEquals(0,row.getChildCount());
    }
}
