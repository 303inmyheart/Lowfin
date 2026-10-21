package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class IdleScreensaverTest {
    private LowfinApp app;private ActivityController<Activity> activity;private IdleScreensaver saver;
    @Before public void setup() {
        app=(LowfinApp)RuntimeEnvironment.getApplication();app.settings.edit().clear().putBoolean("animations",false).commit();
        app.account=new Account("http://127.0.0.1:1","user","token","name","device");app.api=new Api(app.account);
        // Do not create network work for controller lifecycle tests.
        activity=Robolectric.buildActivity(Activity.class).setup().visible();
        activity.get().setContentView(new FrameLayout(activity.get()));saver=new IdleScreensaver(activity.get(),app);
    }
    @After public void close() { saver.pause();activity.pause().stop().destroy();app.account=null;app.api=null; }
    @Test public void defaultOffAndPlaybackDoNotActivateIt() {
        saver.resume();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(4));assertFalse(saver.active());
        app.settings.edit().putBoolean("screensaver",true).putInt("screensaverMinutes",1).commit();app.playing=true;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));assertFalse(saver.active());app.playing=false;
    }
    @Test public void idleTimeoutActivatesAndPauseRemovesTheSaver() throws Exception {
        try(okhttp3.mockwebserver.MockWebServer server=new okhttp3.mockwebserver.MockWebServer()) {
            server.enqueue(new okhttp3.mockwebserver.MockResponse().setBody(Json.obj("Items",Json.arr(Json.obj("Id","movie","Type","Movie","Name","Movie"))).toString()));server.start();
            app.account=new Account(server.url("/").toString(),"user","token","name","device");app.api=new Api(app.account);
            app.settings.edit().putBoolean("screensaver",true).putInt("screensaverMinutes",1).commit();
            activity.windowFocusChanged(true);saver.resume();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59));assertFalse(saver.active());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertTrue(saver.active());
            FrameLayout content=activity.get().findViewById(android.R.id.content);assertTrue(content.getChildAt(content.getChildCount()-1).getKeepScreenOn());
            saver.pause();assertFalse(saver.active());assertEquals(1,content.getChildCount());assertEquals(View.VISIBLE,content.getChildAt(0).getVisibility());
            okhttp3.mockwebserver.RecordedRequest request=server.takeRequest(5,java.util.concurrent.TimeUnit.SECONDS);assertNotNull(request);assertEquals("Backdrop",request.getRequestUrl().queryParameter("ImageTypes"));
        }
    }
    @Test public void librarySelectionsAreIndependentAccountScopedAndEmptyMeansNone() {
        assertNull(AmbientLibraries.selection(app,"screensaver"));
        app.settings.edit().putString(AmbientLibraries.key(app.account,"screensaver"),"[\"horror\",\"anime\"]").putString(AmbientLibraries.key(app.account,"banner"),"[]").commit();
        assertEquals(java.util.Arrays.asList("horror","anime"),AmbientLibraries.selection(app,"screensaver"));assertEquals("",AmbientLibraries.parent(AmbientLibraries.selection(app,"banner")));
        for(int i=0;i<20;i++) assertTrue(AmbientLibraries.selection(app,"screensaver").contains(AmbientLibraries.parent(AmbientLibraries.selection(app,"screensaver"))));
        app.account=new Account("http://127.0.0.1:1","other","token","other","device");assertNull(AmbientLibraries.selection(app,"screensaver"));
    }
    @Test public void DisabledSaverHasNoIdleTimerAndSettingsChangesRescheduleItWithoutRestarting() {
        android.os.Handler handler=org.robolectric.util.ReflectionHelpers.getField(saver,"main");Runnable tick=org.robolectric.util.ReflectionHelpers.getField(saver,"tick");
        saver.resume();shadowOf(Looper.getMainLooper()).idle();assertFalse(handler.hasCallbacks(tick));
        app.settings.edit().putBoolean("screensaver",true).commit();shadowOf(Looper.getMainLooper()).idle();assertTrue(handler.hasCallbacks(tick));
        app.settings.edit().putBoolean("screensaver",false).commit();shadowOf(Looper.getMainLooper()).idle();assertFalse(handler.hasCallbacks(tick));
        saver.pause();assertFalse(handler.hasCallbacks(tick));
    }
    @Test public void everyMinuteBetweenOneAndTwentyIsSupportedAndClamped() {
        for(int i=1;i<=20;i++) {app.settings.edit().putInt("screensaverMinutes",i).commit();assertEquals(i*60000L,saver.idleMillis());}
        app.settings.edit().putInt("screensaverMinutes",99).commit();assertEquals(1200000,saver.idleMillis());
        app.settings.edit().putInt("screensaverMinutes",0).commit();assertEquals(60000,saver.idleMillis());
    }
    @Test public void dismissConsumesTheWholeRemotePressRestoresFocusAndReleasesKeepAwake() {
        Button button=Ui.button(activity.get(),"Underlying action",()->fail("Dismiss must not click the underlying action"));activity.get().setContentView(button);button.requestFocus();
        saver.resume();
        // Inject the view to isolate dismissal from backend availability.
        ScreensaverView overlay=new ScreensaverView(activity.get(),app);overlay.setKeepScreenOn(true);
        FrameLayout content=activity.get().findViewById(android.R.id.content);content.addView(overlay);
        org.robolectric.util.ReflectionHelpers.setField(saver,"overlay",overlay);org.robolectric.util.ReflectionHelpers.setField(saver,"previousFocus",button);
        assertTrue(saver.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)));assertFalse(saver.active());assertFalse(overlay.getKeepScreenOn());
        assertTrue(saver.key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)));assertTrue(button.hasFocus());
        assertFalse(saver.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));
    }
    @Test public void screensaverHasRatingsAndNoTagCapableImageViews() {
        ScreensaverView view=new ScreensaverView(activity.get(),app);
        view.bind(Json.obj("Id","film","Type","Movie","Name","A movie","Overview","Description","CommunityRating",8.2,"CriticRating",90));
        assertFalse(contains(view,v->v instanceof ArtworkView));assertTrue(contains(view,v->v instanceof RatingsRow));
        assertFalse(contains(view,v->v instanceof TextView&&((TextView)v).getText().toString().contains("8.2")));
        RatingsRow row=org.robolectric.util.ReflectionHelpers.getField(view,"ratings");row.show(app,Json.obj("imdb",7.9));
        assertTrue(contains(view,v->v instanceof TextView&&((TextView)v).getText().toString().contains(Ratings.format("imdb",7.9))));
        view.stop();
    }
    @Test public void saverLayoutKeepsLogoRatingsAndOverviewWithinHdViewport() throws Exception {
        ScreensaverView view=new ScreensaverView(activity.get(),app);activity.get().setContentView(view);
        view.bind(Json.obj("Id","film","Type","Movie","Name","The Lord of the Rings: The Fellowship of the Ring","Overview","A quiet journey through a distant landscape. An unexpected adventure brings a group of friends together, testing their courage and changing their world forever.","CommunityRating",8.8,"CriticRating",91));
        int width=activity.get().getResources().getDisplayMetrics().widthPixels,height=activity.get().getResources().getDisplayMetrics().heightPixels;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
        android.graphics.Bitmap background=android.graphics.Bitmap.createBitmap(32,18,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas bg=new android.graphics.Canvas(background);android.graphics.Paint paint=new android.graphics.Paint();paint.setShader(new android.graphics.LinearGradient(0,0,32,18,0xff253b48,0xff8d7562,android.graphics.Shader.TileMode.CLAMP));bg.drawRect(0,0,32,18,paint);
        for(int i=0;i<view.getChildCount();i++) if(view.getChildAt(i) instanceof ImageView) {((ImageView)view.getChildAt(i)).setImageBitmap(background);break;}
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));java.io.File folder=new java.io.File("build/qa");folder.mkdirs();try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"screensaver-hd.png"))) {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        for(int i=0;i<view.getChildCount();i++) if(view.getChildAt(i) instanceof LinearLayout) {View panel=view.getChildAt(i);assertTrue(panel.getTop()>=0);assertTrue(panel.getBottom()<=height);}
        view.stop();
    }
    @Test public void settingsRowsHaveConsistentGapsAndIgnoreDuplicateLegacySpacers() {
        SettingsColumn column=new SettingsColumn(activity.get());Button first=Ui.button(activity.get(),"First",()->{}),second=Ui.button(activity.get(),"Second",()->{});
        column.addView(first);Ui.space(column,8);Ui.space(column,20);column.addView(second);
        assertEquals(2,column.getChildCount());assertEquals(Ui.dp(activity.get(),8),((LinearLayout.LayoutParams)second.getLayoutParams()).topMargin);
        TextView hint=Ui.text(activity.get(),"Hint",12,Ui.MUTED);column.addView(hint);assertEquals(Ui.dp(activity.get(),4),((LinearLayout.LayoutParams)hint.getLayoutParams()).topMargin);
    }
    private boolean contains(View view,java.util.function.Predicate<View> match) { if(match.test(view)) return true;if(view instanceof android.view.ViewGroup) {android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++) if(contains(group.getChildAt(i),match)) return true;}return false; }
}
