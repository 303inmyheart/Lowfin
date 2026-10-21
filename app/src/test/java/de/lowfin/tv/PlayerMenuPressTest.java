package de.lowfin.tv;

import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class PlayerMenuPressTest {
    private KeyEvent key(int code,int action,int repeat) { return new KeyEvent(10,10+repeat*100L,action,code,repeat); }
    @Test public void confirmPausesBeforeOpeningAndSwallowsRepeatAndRelease() {
        for(int code:new int[]{KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER}) {
            PlayerMenuPress press=new PlayerMenuPress(); List<String> actions=new ArrayList<>();
            assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,0),false,()->actions.add("pause"),k->actions.add("open")));
            assertEquals(Arrays.asList("pause","open"),actions);
            assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,1),true,()->fail("Repeated pause"),k->fail("Repeated opening")));
            assertTrue(press.handle(key(code,KeyEvent.ACTION_UP,0),true,()->fail(),k->fail()));
            assertFalse(press.handle(key(code,KeyEvent.ACTION_DOWN,0),true,()->fail(),k->fail()));
        }
    }
    @Test public void arrowsRevealWithoutPausingAndEntireOpeningGestureCannotSeek() {
        for(int code:new int[]{KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN}) {
            PlayerMenuPress press=new PlayerMenuPress(); List<Integer> opened=new ArrayList<>();
            assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,0),false,()->fail("Arrow paused playback"),opened::add));
            for(int i=1;i<12;i++) assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,i),true,()->fail(),k->fail()));
            assertTrue(press.handle(key(code,KeyEvent.ACTION_UP,0),true,()->fail(),k->fail()));
            assertEquals(Collections.singletonList(code),opened);
            assertFalse(press.handle(key(code,KeyEvent.ACTION_DOWN,0),true,()->fail(),k->fail()));
        }
    }
    @Test public void dedicatedMediaKeysKeepTheirDirectBehavior() {
        PlayerMenuPress press=new PlayerMenuPress();
        for(int code:new int[]{KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,KeyEvent.KEYCODE_MEDIA_REWIND}) assertFalse(press.handle(key(code,KeyEvent.ACTION_DOWN,0),false,()->fail(),k->fail()));
    }
    @Test public void focusedPlayButtonActsOnDownWithoutTogglingAgainOnRelease() {
        PlayerMenuPress press=new PlayerMenuPress(); List<String> actions=new ArrayList<>(); int code=KeyEvent.KEYCODE_DPAD_CENTER;
        assertTrue(press.activate(key(code,KeyEvent.ACTION_DOWN,0),()->actions.add("toggle")));
        assertEquals(Collections.singletonList("toggle"),actions);
        assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,1),true,()->fail(),k->fail()));
        assertTrue(press.handle(key(code,KeyEvent.ACTION_UP,0),true,()->fail(),k->fail()));
        assertEquals(1,actions.size());
    }
}
