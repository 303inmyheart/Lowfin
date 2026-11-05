package de.lowfin.tv;

import android.content.SharedPreferences;

/** One installation-wide choice; legacy accounts and explicit preferences keep their behavior. */
final class PrecacheSetup {
    private static final String CHOICE="precacheChoiceMade";
    static void prepare(SharedPreferences settings,boolean existingAccount) {
        if(settings.contains(CHOICE))return;
        settings.edit().putBoolean(CHOICE,existingAccount||settings.contains("autoPrecache")).apply();
    }
    static boolean pending(SharedPreferences settings) {return !settings.getBoolean(CHOICE,true)&&!settings.contains("autoPrecache");}
    static boolean enabled(SharedPreferences settings) {return !pending(settings)&&settings.getBoolean("autoPrecache",true);}
    static void choose(SharedPreferences settings,boolean enabled) {settings.edit().putBoolean(CHOICE,true).putBoolean("autoPrecache",enabled).apply();}
}
