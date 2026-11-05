package de.lowfin.tv;

import android.content.SharedPreferences;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class PrecacheSetupTest {
    private SharedPreferences settings;
    @Before public void setup(){settings=RuntimeEnvironment.getApplication().getSharedPreferences("precache-setup-test",0);settings.edit().clear().commit();}
    @Test public void newInstallationWaitsForAChoiceEvenAfterRestartWithAnAccount(){
        PrecacheSetup.prepare(settings,false);assertTrue(PrecacheSetup.pending(settings));assertFalse(PrecacheSetup.enabled(settings));
        PrecacheSetup.prepare(settings,true);assertTrue(PrecacheSetup.pending(settings));assertFalse(PrecacheSetup.enabled(settings));
    }
    @Test public void declinePersistsAndNormalSettingsCanEnableItLater(){
        PrecacheSetup.prepare(settings,false);PrecacheSetup.choose(settings,false);PrecacheSetup.prepare(settings,true);
        assertFalse(PrecacheSetup.pending(settings));assertFalse(PrecacheSetup.enabled(settings));settings.edit().putBoolean("autoPrecache",true).commit();assertTrue(PrecacheSetup.enabled(settings));
    }
    @Test public void acceptancePersistsAndDoesNotOverrideLaterDisable(){
        PrecacheSetup.prepare(settings,false);PrecacheSetup.choose(settings,true);assertTrue(PrecacheSetup.enabled(settings));
        settings.edit().putBoolean("autoPrecache",false).commit();PrecacheSetup.prepare(settings,true);assertFalse(PrecacheSetup.enabled(settings));
    }
    @Test public void upgradingExistingAccountsKeepsImplicitOrExplicitPreferences(){
        PrecacheSetup.prepare(settings,true);assertFalse(PrecacheSetup.pending(settings));assertTrue(PrecacheSetup.enabled(settings));
        settings.edit().clear().putBoolean("autoPrecache",false).commit();PrecacheSetup.prepare(settings,false);assertFalse(PrecacheSetup.pending(settings));assertFalse(PrecacheSetup.enabled(settings));
    }
}
