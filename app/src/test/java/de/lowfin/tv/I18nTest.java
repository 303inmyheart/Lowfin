package de.lowfin.tv;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="de")
public class I18nTest {
    @Before public void setup() { RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().clear().commit(); I18n.init(RuntimeEnvironment.getApplication()); }
    @Test public void defaultsToEnglishRegardlessOfSystemLocale() { assertEquals("en",I18n.language()); assertEquals("Cache not created yet",((LowfinApp)RuntimeEnvironment.getApplication()).syncStatus); assertEquals("Settings",I18n.text("Einstellungen")); assertEquals("Season no.",I18n.resource(R.string.season_number_hint)); assertEquals("Subtitles",I18n.text("Untertitel")); assertEquals("Sign in",I18n.text("Anmelden")); }
    @Test public void choicePersistsAndCanSwitchBothWaysWithoutChangingMetadata() { I18n.select("de"); assertEquals("Einstellungen",I18n.text("Einstellungen")); I18n.init(RuntimeEnvironment.getApplication()); assertEquals("de",I18n.language()); assertEquals("The German Film Title",I18n.text("The German Film Title")); I18n.select("en"); assertEquals("Genre rows",I18n.text("Genre-Reihen")); assertEquals("Buffering …",I18n.text("Puffer wird gefüllt …")); }
    @Test public void everyMappedUiLabelHasNonemptyEnglishTextAndFeatureSettingsUseEnglish() {
        I18n.select("en");
        java.util.Map<String,Integer> ids=org.robolectric.util.ReflectionHelpers.getStaticField(I18n.class,"IDS");
        for(java.util.Map.Entry<String,Integer> entry:ids.entrySet()) assertFalse(entry.getKey(),I18n.resource(entry.getValue()).trim().isEmpty());
        assertEquals("Screensaver",I18n.resource(R.string.screensaver_title));
        assertEquals("Season no.",I18n.resource(R.string.season_number_hint));
        assertEquals("Home banner · Libraries",I18n.resource(R.string.banner_libraries));
        assertEquals("Pulsing focus outline",I18n.resource(R.string.focus_pulse));
        assertEquals("Remove from Watch later",I18n.text("Aus Später ansehen entfernen"));
        assertEquals("Add to favorites",I18n.text("Zu Favoriten hinzufügen"));
        I18n.select("de");assertEquals("Staffel-Nr.",I18n.resource(R.string.season_number_hint));
    }
}
