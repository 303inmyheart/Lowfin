package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.view.*;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class ConfirmPressTest {
    private ActivityController<Activity> activity;
    private Button card;
    private int clicks,holds;
    @Before public void setup() {
        activity=Robolectric.buildActivity(Activity.class).setup(); card=new Button(activity.get()); card.setFocusableInTouchMode(true); activity.get().setContentView(card); card.requestFocus();
        card.setOnClickListener(v->clicks++); ConfirmPress.bind(card,null,()->holds++);
    }
    @After public void close() { activity.pause().stop().destroy(); }
    private void key(int action,int code) { card.dispatchKeyEvent(new KeyEvent(action,code)); }
    @Test public void shortConfirmClicksOnlyOnRelease() {
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(0,clicks);
        key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1,clicks);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(0,holds);
    }
    @Test public void heldConfirmWithoutRemoteRepeatOpensMenuOnceAndNeverClicksOnRelease() {
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+20));
        assertEquals(1,holds); key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(0,clicks);
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER); assertEquals(1,clicks);
    }
    @Test public void remoteRepeatDoesNotDuplicateMenuOrConsumeNextShortGesture() {
        card.dispatchKeyEvent(new KeyEvent(0,1,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0));
        card.dispatchKeyEvent(new KeyEvent(0,600,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,1)); assertEquals(1,holds);
        card.dispatchKeyEvent(new KeyEvent(0,900,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,2)); assertEquals(1,holds);
        // Dialog received the release. A fresh DOWN still resets the old gesture.
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER); key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER); assertEquals(1,clicks);
    }
    @Test public void rebindingAndDetachingCancelOldItemTimeout() {
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER); ConfirmPress.bind(card,null,()->holds+=10);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(0,holds);
        key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER); activity.get().setContentView(new Button(activity.get()));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(0,holds);
    }
}
