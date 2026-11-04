package de.lowfin.tv;

import android.content.Context;
import android.media.*;
import android.os.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class NoticeSoundTest {
    private Context context;private AudioManager audio;private NoticeSound sound;private int started;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);audio.setStreamVolume(AudioManager.STREAM_MUSIC,7,0);
        sound=new NoticeSound(context,new Handler(Looper.getMainLooper()),()->started++);
    }
    @After public void cleanup() {sound.close();}
    private SoundPool pool() {return ReflectionHelpers.getField(sound,"pool");}
    @Test public void waitsForSampleLoadAndLeavesTimeForHdmiOutputWithoutTakingFocusOrChangingVolume() {
        sound.play();SoundPool pool=pool();assertNotNull(pool);assertFalse(shadowOf(pool).wasResourcePlayed(R.raw.dashboard_notice));
        shadowOf(pool).notifyResourceLoaded(R.raw.dashboard_notice,true);shadowOf(Looper.getMainLooper()).idle();
        assertTrue(shadowOf(pool).wasResourcePlayed(R.raw.dashboard_notice));assertEquals(1,started);
        org.robolectric.shadows.ShadowSoundPool.Playback played=shadowOf(pool).getResourcePlaybacks(R.raw.dashboard_notice).get(0);
        assertEquals(.28f,played.leftVolume,0);assertEquals(0,played.loop);assertNull(shadowOf(audio).getLastAudioFocusRequest());assertEquals(7,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        shadowOf(Looper.getMainLooper()).idleFor(250,TimeUnit.MILLISECONDS);assertNotNull(pool());
        shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);assertNull(pool());
    }
    @Test public void mutedMediaOrSilentModePreventsPlaybackWithoutOverridingEither() {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);sound.play();assertNull(pool());assertEquals(0,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,7,0);audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);sound.play();assertNull(pool());
    }
    @Test public void closingBeforeLoadDiscardsLateCallback() {
        sound.play();SoundPool pool=pool();sound.close();shadowOf(pool).notifyResourceLoaded(R.raw.dashboard_notice,true);shadowOf(Looper.getMainLooper()).idle();assertFalse(shadowOf(pool).wasResourcePlayed(R.raw.dashboard_notice));assertNull(pool());
    }
    @Test public void failedLoadIsReleasedAndNeverPlayed() {
        sound.play();SoundPool pool=pool();shadowOf(pool).notifyResourceLoaded(R.raw.dashboard_notice,false);shadowOf(Looper.getMainLooper()).idle();assertNull(pool());assertFalse(shadowOf(pool).wasResourcePlayed(R.raw.dashboard_notice));assertEquals(0,started);
    }
}
