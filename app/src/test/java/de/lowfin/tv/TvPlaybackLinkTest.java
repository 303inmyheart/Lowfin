package de.lowfin.tv;
import android.content.Intent;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class TvPlaybackLinkTest {
    private final Account account=new Account("http://server","user","token","name","device");
    @Test public void OnlyOwnPlaybackCardsTakeTheFastPath() {
        Intent resume=HomeChannels.link(RuntimeEnvironment.getApplication(),account,"episode-id","resume"); assertTrue(TvPlaybackLink.parse(resume,account).resume);
        assertFalse(TvPlaybackLink.parse(HomeChannels.link(RuntimeEnvironment.getApplication(),account,"episode-id","play"),account).resume);
        assertNull(TvPlaybackLink.parse(resume,new Account("http://server","other-user","token","other","device")));
        assertNull(TvPlaybackLink.parse(HomeChannels.link(RuntimeEnvironment.getApplication(),account,"series-id","open"),account));
    }
    @Test public void NativeResumeIsFreshButRestartsAndRestoredPositionsArePreserved() {
        JSONObject current=Json.obj("UserData",Json.obj("PlaybackPositionTicks",123000000));
        assertEquals(12300,PlaybackPolicy.startPosition(current,0,true,false,false));
        assertEquals(0,PlaybackPolicy.startPosition(current,0,false,false,false));
        assertEquals(0,PlaybackPolicy.startPosition(current,999,true,true,false));
        assertEquals(999,PlaybackPolicy.startPosition(current,999,true,false,true));
    }
    @Test public void SingularAndPluralMatchSelectedLanguage() {
        I18n.init(RuntimeEnvironment.getApplication()); I18n.select("de"); assertEquals("1 Wiedergabe",I18n.quantity(R.plurals.completed_plays,1,1)); assertEquals("0 Wiedergaben",I18n.quantity(R.plurals.completed_plays,0,0)); assertEquals("2 Wiedergaben",I18n.quantity(R.plurals.completed_plays,2,2));
        I18n.select("en"); assertEquals("1 play",I18n.quantity(R.plurals.completed_plays,1,1)); assertEquals("2 plays",I18n.quantity(R.plurals.completed_plays,2,2));
    }
}
