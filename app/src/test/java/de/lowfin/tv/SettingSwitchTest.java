package de.lowfin.tv;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SettingSwitchTest {
    @Test public void RemoteOkChangesStateBothWaysAndKeepsFocus() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get(); AtomicInteger events=new AtomicInteger();
        SettingSwitch toggle=Ui.toggle(activity,"Animation",true,on->events.incrementAndGet()); activity.setContentView(toggle); toggle.requestFocus();
        assertEquals(0,events.get());
        for(int i=0;i<2;i++) { toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)); toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)); }
        assertEquals(1f,toggle.getScaleX(),0); assertEquals(1f,toggle.getScaleY(),0);
        assertTrue(toggle.isChecked()); assertEquals(2,events.get()); assertTrue(toggle.hasFocus()); assertEquals("android.widget.Switch",toggle.getAccessibilityClassName());
    }
    private int trackPixel(SettingSwitch toggle) {
        int width=Ui.dp(toggle.getContext(),300),height=Ui.dp(toggle.getContext(),50);
        toggle.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));toggle.layout(0,0,width,height);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);toggle.draw(new android.graphics.Canvas(bitmap));
        int pixel=bitmap.getPixel(width-Ui.dp(toggle.getContext(),37),height/2);bitmap.recycle();return pixel;
    }
    @Test public void AccentUpdatesTrackAndOffStateRemainsDistinct() {
        SettingSwitch toggle=Ui.toggle(RuntimeEnvironment.getApplication(),"Option",true,on->{});int previous=Accent.color;
        try {
            Accent.color=0xffa4bbdd;Accent.apply(toggle);assertEquals(Accent.color,trackPixel(toggle));
            toggle.setChecked(false);assertEquals(0xff414148,trackPixel(toggle));
        } finally {Accent.color=previous;}
    }
    @Test public void dialogKeepsSwitchLabelsStartAlignedAndIndicatorVisibleOnFireOsSdk() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();SettingsColumn content=new SettingsColumn(activity);
        SettingSwitch toggle=Ui.toggle(activity,"Library",true,on->{});content.addView(toggle);
        android.app.AlertDialog dialog=LowfinDialog.builder(activity).setTitle("Libraries").setView(content).setPositiveButton("Save",null).show();
        View decor=dialog.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.AT_MOST));decor.layout(0,0,900,decor.getMeasuredHeight());
        try {assertEquals(View.TEXT_ALIGNMENT_VIEW_START,toggle.getTextAlignment());assertEquals(Gravity.START,toggle.getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);assertEquals(Accent.color,trackPixel(toggle));
            toggle.performClick();assertFalse(toggle.isChecked());assertEquals(0xff414148,trackPixel(toggle));
        } finally {dialog.dismiss();}
    }
}
