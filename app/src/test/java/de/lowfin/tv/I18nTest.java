package de.lowfin.tv;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30,qualifiers="de")
public class I18nTest {
    @Before public void setup() { RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().clear().commit(); I18n.init(RuntimeEnvironment.getApplication()); }
    @Test public void defaultsToEnglishRegardlessOfSystemLocale() { assertEquals("en",I18n.language()); assertEquals("Cache not created yet",((LowfinApp)RuntimeEnvironment.getApplication()).syncStatus); assertEquals("Settings",I18n.text("Einstellungen")); assertEquals("Subtitles",I18n.text("Untertitel")); assertEquals("Sign in",I18n.text("Anmelden")); }
    @Test public void choicePersistsAndCanSwitchBothWaysWithoutChangingMetadata() { I18n.select("de"); assertEquals("Einstellungen",I18n.text("Einstellungen")); I18n.init(RuntimeEnvironment.getApplication()); assertEquals("de",I18n.language()); assertEquals("The German Film Title",I18n.text("The German Film Title")); I18n.select("en"); assertEquals("Genre rows",I18n.text("Genre-Reihen")); assertEquals("Buffering …",I18n.text("Puffer wird gefüllt …")); }
}
