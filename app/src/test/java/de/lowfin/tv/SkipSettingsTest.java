package de.lowfin.tv;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class SkipSettingsTest {
    @Test public void LegacyMasterMigrationPreservesAllEffectiveIntroOutroCombinations() {
        SharedPreferences preferences=RuntimeEnvironment.getApplication().getSharedPreferences("skip-migration",0);
        for(boolean master:new boolean[]{false,true}) for(boolean intro:new boolean[]{false,true}) for(boolean outro:new boolean[]{false,true}) {
            preferences.edit().clear().putBoolean("autoSkip",master).putBoolean("skipIntro",intro).putBoolean("skipOutro",outro).commit();
            PlaybackPolicy.migrateSkipSettings(preferences);
            assertFalse(preferences.contains("autoSkip"));
            assertEquals(master&&intro,PlaybackPolicy.autoSkip("Intro",Json.obj("Type","Movie"),preferences));
            assertEquals(master&&outro,PlaybackPolicy.autoSkip("Outro",Json.obj("Type","Movie"),preferences));
        }
    }
    @Test public void IndependentSwitchesAndPremiereExceptionKeepTheirMeaning() {
        SharedPreferences preferences=RuntimeEnvironment.getApplication().getSharedPreferences("skip-migration",0);
        preferences.edit().clear().putBoolean("skipIntro",true).putBoolean("skipOutro",false).putBoolean("keepSeasonPremiereIntro",true).commit();
        PlaybackPolicy.migrateSkipSettings(preferences);
        assertFalse(PlaybackPolicy.autoSkip("Intro",Json.obj("Type","Episode","IndexNumber",1),preferences));
        assertTrue(PlaybackPolicy.autoSkip("Intro",Json.obj("Type","Episode","IndexNumber",2),preferences));
        assertFalse(PlaybackPolicy.autoSkip("Outro",Json.obj("Type","Episode","IndexNumber",2),preferences));
        assertFalse(PlaybackPolicy.autoSkip("Recap",Json.obj("Type","Movie"),preferences));
        preferences.edit().putBoolean("skipOutro",true).commit(); PlaybackPolicy.migrateSkipSettings(preferences);
        assertTrue(PlaybackPolicy.autoSkip("Outro",Json.obj("Type","Episode","IndexNumber",1),preferences));
    }
}
