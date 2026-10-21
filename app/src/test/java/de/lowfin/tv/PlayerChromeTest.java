package de.lowfin.tv;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayerChromeTest {
    private ActivityController<Activity> activity;
    private PlayerChrome chrome;
    private int toggles,options;
    private long seek;
    @After public void close() { if(activity!=null) activity.pause().stop().destroy(); }
    private FrameLayout setup(boolean live) { return setup(live,System::currentTimeMillis); }
    private FrameLayout setup(boolean live,java.util.function.LongSupplier clock) {
        activity=Robolectric.buildActivity(Activity.class).setup(); Activity context=activity.get(); Ui.immersive(context);
        FrameLayout root=new FrameLayout(context); root.setBackgroundColor(0xff303a43);
        chrome=new PlayerChrome(context,live,!live,new PlayerChrome.Actions() {
            public void toggle() { toggles++; } public void seek(long value) { seek=value; } public void seekTo(long value) { seek=value; }
            public void options() { options++; } public void next() {} public void channels() {}
        },clock); root.addView(chrome,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)); root.addView(chrome.identity,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP)); context.setContentView(root);
        chrome.title.setText(live?"Sender Eins":"Die lange Serie"); chrome.caption.setText(live?"Live TV":"Staffel 40 · Folge 5 · Der Rückweg");
        chrome.update(615000,640000,2700000,true,live); return root;
    }
    private void layout() {
        View root=activity.get().getWindow().getDecorView(); root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY)); root.layout(0,0,1280,720);
    }
    @Test public void unchangedTicksDoNotRewriteLabelsButPauseAndTimezoneChangesRemainLive() {
        java.util.TimeZone original=java.util.TimeZone.getDefault();java.util.concurrent.atomic.AtomicLong now=new java.util.concurrent.atomic.AtomicLong(0);
        try { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));setup(false,now::get);chrome.update(60000,120000,3600000,false,false);
            java.util.concurrent.atomic.AtomicInteger edits=new java.util.concurrent.atomic.AtomicInteger();android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){edits.incrementAndGet();}public void afterTextChanged(android.text.Editable text){}};
            chrome.current.addTextChangedListener(watcher);chrome.remaining.addTextChangedListener(watcher);chrome.play.addTextChangedListener(watcher);
            chrome.update(60000,120000,3600000,false,false);assertEquals(0,edits.get());assertTrue(chrome.remaining.getText().toString().endsWith("00:59"));
            now.set(60000);chrome.update(60000,120000,3600000,false,false);assertEquals(1,edits.get());assertTrue(chrome.remaining.getText().toString().endsWith("01:00"));
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT+02:00"));chrome.update(60000,120000,3600000,false,false);assertTrue(chrome.remaining.getText().toString().endsWith("03:00"));
        } finally { java.util.TimeZone.setDefault(original); }
    }
    @Test public void elapsedTimeFormattingHandlesLongVideosAndBoundaries() {
        assertEquals("0:00",PlayerChrome.time(-1));assertEquals("0:59",PlayerChrome.time(59999));assertEquals("1:00",PlayerChrome.time(60000));assertEquals("59:59",PlayerChrome.time(3599999));assertEquals("1:00:00",PlayerChrome.time(3600000));assertEquals("12:03:04",PlayerChrome.time(43384000));assertEquals("100:00:00",PlayerChrome.time(360000000));
    }
    @Test public void hdTransportIsCompactAndButtonsInvokeTheirActions() throws Exception {
        setup(false); layout(); assertTrue(chrome.getMeasuredHeight()<230);
        assertEquals("10:15 / 45:00",chrome.current.getText().toString()); assertTrue(chrome.remaining.getText().toString().contains(":"));
        assertTrue(chrome.timeline.getSecondaryProgress()>chrome.timeline.getProgress());
        assertTrue(chrome.options.getTop()+((View)chrome.options.getParent()).getTop()>chrome.timeline.getTop());
        chrome.play.performClick(); chrome.options.performClick(); assertEquals(1,toggles); assertEquals(1,options);
        screenshot("player-hd.png");
    }
    @Test public void seekFocusHasClearFrameAndLargerMarkerWithoutSeeking() throws Exception {
        setup(false); layout(); chrome.play.requestFocus(); int small=chrome.timeline.getThumb().getIntrinsicWidth(),position=chrome.timeline.getProgress();
        assertTrue(chrome.timeline.requestFocus()); layout();
        assertTrue(chrome.timeline.getThumb().getIntrinsicWidth()>small); assertEquals(Ui.dp(activity.get(),20),chrome.timeline.getThumb().getBounds().width());
        assertTrue(Color.alpha(((android.graphics.drawable.GradientDrawable)chrome.timeline.getBackground()).getColor().getDefaultColor())>0);
        assertEquals(Ui.dp(activity.get(),5),((android.graphics.drawable.LayerDrawable)chrome.timeline.getProgressDrawable()).getLayerHeight(0));
        assertEquals(position,chrome.timeline.getProgress()); assertEquals(0,seek); screenshot("player-seek-focused-hd.png");
        chrome.play.requestFocus(); assertEquals(small,chrome.timeline.getThumb().getIntrinsicWidth());
        assertEquals(Color.TRANSPARENT,((android.graphics.drawable.GradientDrawable)chrome.timeline.getBackground()).getColor().getDefaultColor());
    }
    @Test public void PausedEndTimeStaysVisibleAndMovesWithWallClock() {
        java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong(1792022400000L);
        setup(false,clock::get); layout(); long position=615000,length=2700000;
        chrome.update(position,position,length,false,false); String paused=chrome.remaining.getText().toString(); assertFalse(paused.isEmpty());
        clock.addAndGet(120000);
        chrome.update(position,position,length,false,false); assertNotEquals(paused,chrome.remaining.getText().toString());
        String expected=I18n.text("Ende um ")+new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(clock.get()+length-position));
        assertEquals(expected,chrome.remaining.getText().toString());
        String afterPause=chrome.remaining.getText().toString(); chrome.update(position,position,length,true,false); assertEquals(afterPause,chrome.remaining.getText().toString());
    }
    @Test public void FocusedSeekBarAndLabelsHaveClearanceAboveTransport() throws Exception {
        setup(false); layout(); chrome.timeline.requestFocus(); layout();
        View transport=(View)chrome.play.getParent(); assertTrue(transport.getTop()-chrome.timeline.getBottom()>=Ui.dp(activity.get(),12));
        assertTrue(((View)chrome.current.getParent()).getBottom()<chrome.timeline.getTop()); screenshot("player-seek-clearance-hd.png");
        chrome.play.requestFocus(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(250)); layout();
        assertTrue(transport.getTop()+chrome.play.getTop()-(chrome.play.getHeight()*(chrome.play.getScaleY()-1)/2)>chrome.timeline.getBottom());
    }
    @Test public void liveTransportHasNoFakeSeekBar() throws Exception {
        setup(true); layout(); assertEquals(View.GONE,chrome.timeline.getVisibility()); assertEquals("0:00",chrome.current.getText().toString()); screenshot("player-live-hd.png");
    }
    @Test public void logoUsesFixedFrameWithoutStretchingAndFallsBackToTitle() {
        setup(false); chrome.logo.setImageBitmap(Bitmap.createBitmap(400,80,Bitmap.Config.ARGB_8888)); layout();
        assertEquals(View.VISIBLE,chrome.logo.getVisibility()); assertEquals(View.GONE,chrome.title.getVisibility());
        int width=chrome.logo.getWidth(),height=chrome.logo.getHeight();
        assertEquals(ImageView.ScaleType.FIT_CENTER,chrome.logo.getScaleType());
        assertEquals(chrome.logo.getLeft()+chrome.logo.getWidth()/2,chrome.caption.getLeft()+chrome.caption.getWidth()/2);
        assertTrue(chrome.caption.getTop()>=chrome.logo.getBottom());
        assertEquals(Gravity.CENTER,chrome.caption.getGravity());
        chrome.logo.setImageBitmap(Bitmap.createBitmap(80,400,Bitmap.Config.ARGB_8888)); layout();
        assertEquals(width,chrome.logo.getWidth()); assertEquals(height,chrome.logo.getHeight());
        float[] matrix=new float[9]; chrome.logo.getImageMatrix().getValues(matrix); assertEquals(matrix[Matrix.MSCALE_X],matrix[Matrix.MSCALE_Y],.001f);
        chrome.logo.setImageDrawable(null); assertEquals(View.VISIBLE,chrome.title.getVisibility());layout();
        assertEquals(chrome.title.getWidth(),chrome.caption.getWidth());
    }
    @Test public void backdropRetainsColorWhileReducingSaturation() {
        setup(false); BackdropView view=new BackdropView(activity.get()); Bitmap original=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); original.eraseColor(Color.rgb(30,80,160)); view.setImageBitmap(original);
        view.measure(View.MeasureSpec.makeMeasureSpec(8,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(8,View.MeasureSpec.EXACTLY)); view.layout(0,0,8,8);
        Bitmap output=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888); view.draw(new Canvas(output)); int pixel=output.getPixel(4,4);
        assertTrue(Color.blue(pixel)>Color.green(pixel)); assertTrue(Color.green(pixel)>Color.red(pixel)); assertTrue(Color.blue(pixel)-Color.red(pixel)<130);
    }
    private void screenshot(String name) throws Exception {
        View root=activity.get().getWindow().getDecorView(); Bitmap bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        File folder=new File("build/qa"); folder.mkdirs(); try(OutputStream out=new FileOutputStream(new File(folder,name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle();
    }
}
