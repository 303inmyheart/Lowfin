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
    @Test public void offeredIntroSkipsWithoutPauseOrMenuAndConsumesReleaseAfterButtonDisappears() {
        for(int code:new int[]{KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER}) {
            PlayerMenuPress press=new PlayerMenuPress();List<String> actions=new ArrayList<>();
            assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,0),false,true,()->actions.add("skip"),()->fail("Intro confirmation paused"),k->fail("Intro confirmation opened menu")));
            for(int repeat=1;repeat<5;repeat++) assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,repeat),false,false,()->fail("Repeated skip"),()->fail("Repeated pause"),k->fail("Repeated menu")));
            assertTrue(press.handle(key(code,KeyEvent.ACTION_UP,0),false,false,()->fail(),()->fail(),k->fail()));
            assertEquals(Collections.singletonList("skip"),actions);
            assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,0),false,false,()->fail(),()->actions.add("pause"),k->actions.add("menu")));
            assertEquals(Arrays.asList("skip","pause","menu"),actions);
        }
    }
    @Test public void introDoesNotStealConfirmationFromOpenPlayerControls() {
        PlayerMenuPress press=new PlayerMenuPress();
        assertFalse(press.handle(key(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.ACTION_DOWN,0),true,true,()->fail("Focused controls must keep OK"),()->fail(),k->fail()));
    }
    @Test public void introAppearingDuringAnExistingPressCannotTriggerAnExtraSkip() {
        PlayerMenuPress press=new PlayerMenuPress();int code=KeyEvent.KEYCODE_DPAD_CENTER;
        assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,0),false,false,()->fail(),()->{},k->{}));
        assertTrue(press.handle(key(code,KeyEvent.ACTION_DOWN,1),false,true,()->fail("Held confirm must not skip new intro"),()->fail(),k->fail()));
        assertTrue(press.handle(key(code,KeyEvent.ACTION_UP,0),false,true,()->fail(),()->fail(),k->fail()));
    }
}
