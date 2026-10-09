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
    private FrameLayout setup(boolean live) {
        activity=Robolectric.buildActivity(Activity.class).setup(); Activity context=activity.get(); Ui.immersive(context);
        FrameLayout root=new FrameLayout(context); root.setBackgroundColor(0xff303a43);
        chrome=new PlayerChrome(context,live,!live,new PlayerChrome.Actions() {
            public void toggle() { toggles++; } public void seek(long value) { seek=value; } public void seekTo(long value) { seek=value; }
            public void options() { options++; } public void next() {} public void channels() {}
        }); root.addView(chrome,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)); context.setContentView(root);
        chrome.title.setText(live?"Sender Eins":"Die lange Serie"); chrome.caption.setText(live?"Live TV":"Staffel 40 · Folge 5 · Der Rückweg");
        chrome.update(615000,640000,2700000,true,live); return root;
    }
    private void layout() {
        View root=activity.get().getWindow().getDecorView(); root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY)); root.layout(0,0,1280,720);
    }
    @Test public void hdTransportIsCompactAndButtonsInvokeTheirActions() throws Exception {
        setup(false); layout(); assertTrue(chrome.getMeasuredHeight()<230);
        assertEquals("10:15",chrome.current.getText().toString()); assertEquals("−34:45",chrome.remaining.getText().toString());
        assertTrue(chrome.timeline.getSecondaryProgress()>chrome.timeline.getProgress());
        assertTrue(chrome.options.getTop()+((View)chrome.options.getParent()).getTop()>chrome.timeline.getTop());
        chrome.play.performClick(); chrome.options.performClick(); assertEquals(1,toggles); assertEquals(1,options);
        screenshot("player-hd.png");
    }
    @Test public void liveTransportHasNoFakeSeekBar() throws Exception {
        setup(true); layout(); assertEquals(View.GONE,chrome.timeline.getVisibility()); assertEquals("● LIVE",chrome.current.getText().toString()); screenshot("player-live-hd.png");
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
