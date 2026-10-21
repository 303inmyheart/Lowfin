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
@Config(sdk=30)
public class SettingSwitchTest {
    @Test public void RemoteOkChangesStateBothWaysAndKeepsFocus() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get(); AtomicInteger events=new AtomicInteger();
        SettingSwitch toggle=Ui.toggle(activity,"Animation",true,on->events.incrementAndGet()); activity.setContentView(toggle); toggle.requestFocus();
        assertEquals(0,events.get());
        for(int i=0;i<2;i++) { toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER)); toggle.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER)); }
        assertEquals(1f,toggle.getScaleX(),0); assertEquals(1f,toggle.getScaleY(),0);
        assertTrue(toggle.isChecked()); assertEquals(2,events.get()); assertTrue(toggle.hasFocus()); assertEquals("android.widget.Switch",toggle.getAccessibilityClassName());
    }
    @Test public void AccentUpdatesTrackAndOffStateRemainsDistinct() {
        SettingSwitch toggle=Ui.toggle(RuntimeEnvironment.getApplication(),"Option",true,on->{});
        int previous=Accent.color;
        try {
            Accent.color=0xffa4bbdd; Accent.apply(toggle);
            toggle.getTrackDrawable().setState(toggle.getDrawableState());
            int on=((GradientDrawable)toggle.getTrackDrawable().getCurrent()).getColor().getDefaultColor(); assertEquals(Accent.color,on);
            toggle.setChecked(false); toggle.getTrackDrawable().setState(toggle.getDrawableState());
            assertNotEquals(on,((GradientDrawable)toggle.getTrackDrawable().getCurrent()).getColor().getDefaultColor());
        } finally { Accent.color=previous; }
    }
}
