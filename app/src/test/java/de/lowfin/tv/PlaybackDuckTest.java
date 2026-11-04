package de.lowfin.tv;

import android.os.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackDuckTest {
    static class Volume {double value=.8;boolean retired;}
    private Volume player;private PlaybackDuck<Volume> duck;
    @Before public void setup() {
        player=new Volume();duck=new PlaybackDuck<>(new Handler(Looper.getMainLooper()),()->player,p->{assertFalse(p.retired);return p.value;},(p,v)->{assertFalse(p.retired);p.value=v;});
    }
    @After public void cleanup() {duck.close();}
    @Test public void restoresExactPreviousVolumeAfterThreeSeconds() {
        duck.lower();assertEquals(.2,player.value,1e-6);shadowOf(Looper.getMainLooper()).idleFor(2999,TimeUnit.MILLISECONDS);assertEquals(.2,player.value,1e-6);
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.MILLISECONDS);assertEquals(.8,player.value,1e-6);
    }
    @Test public void repeatedCueExtendsWindowWithoutCompoundingReduction() {
        duck.lower();shadowOf(Looper.getMainLooper()).idleFor(2,TimeUnit.SECONDS);duck.lower();assertEquals(.2,player.value,1e-6);
        shadowOf(Looper.getMainLooper()).idleFor(2,TimeUnit.SECONDS);assertEquals(.2,player.value,1e-6);
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);assertEquals(.8,player.value,1e-6);
    }
    @Test public void respectsMuteAndManualVolumeChanges() {
        duck.lower();player.value=0;shadowOf(Looper.getMainLooper()).idleFor(3,TimeUnit.SECONDS);assertEquals(0,player.value,0);
        player.value=.6;duck.lower();player.value=.5;shadowOf(Looper.getMainLooper()).idleFor(3,TimeUnit.SECONDS);assertEquals(.5,player.value,0);
        player.value=0;duck.lower();shadowOf(Looper.getMainLooper()).idleFor(3,TimeUnit.SECONDS);assertEquals(0,player.value,0);
    }
    @Test public void replacementNeverTouchesRetiredPlayerOrNewPlayersVolume() {
        duck.lower();player.retired=true;player=new Volume();player.value=.6;
        shadowOf(Looper.getMainLooper()).idleFor(3,TimeUnit.SECONDS);assertEquals(.6,player.value,0);
        duck.lower();assertEquals(.15,player.value,1e-6);duck.close();assertEquals(.6,player.value,0);
    }
    @Test public void closeRestoresImmediatelyAndCancelsPendingWork() {
        duck.lower();duck.close();assertEquals(.8,player.value,1e-6);player.value=.4;
        shadowOf(Looper.getMainLooper()).idleFor(10,TimeUnit.SECONDS);assertEquals(.4,player.value,0);
        player=null;duck.lower();duck.close();
    }
}
