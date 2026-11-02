package de.lowfin.tv;

import android.content.SharedPreferences;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class SidebarLayoutTest {
    private final Account a=new Account("http://server","a","token","A","device"),b=new Account("http://server","b","token","B","device");
    @Test public void savesAllMenuEntriesSeparatelyFromLibraryAndHomeOrdersAndScopesThemToAccount() {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("layout-test",0);List<String> desired=new ArrayList<>(SidebarLayout.ALL);Collections.reverse(desired);SidebarLayout.save(prefs,a,SidebarLayout.ALL,desired);
        assertEquals(desired,SidebarLayout.read(prefs,a,SidebarLayout.ALL));assertEquals(SidebarLayout.ALL,SidebarLayout.read(prefs,b,SidebarLayout.ALL));assertEquals(1,prefs.getAll().size());
    }
    @Test public void temporarilyHiddenLiveEntryKeepsItsSlotWhenOtherEntriesMove() {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("layout-test",0);List<String> visible=new ArrayList<>(SidebarLayout.ALL);visible.remove("live");List<String> desired=new ArrayList<>(visible);Collections.swap(desired,0,5);SidebarLayout.save(prefs,a,visible,desired);
        List<String> all=SidebarLayout.read(prefs,a,SidebarLayout.ALL);assertEquals(3,all.indexOf("live"));assertEquals(desired,SidebarLayout.read(prefs,a,visible));assertEquals(7,new HashSet<>(all).size());
    }
    @Test public void corruptDuplicateAndUnknownKeysRecoverToValidNavigation() {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("layout-test",0);prefs.edit().putString("sidebarOrder-"+a.scope(),"[\"favorites\",\"favorites\",\"unknown\"]").apply();List<String> keys=SidebarLayout.read(prefs,a,SidebarLayout.ALL);assertEquals("favorites",keys.get(0));assertEquals(7,keys.size());assertFalse(keys.contains("unknown"));prefs.edit().putString("sidebarOrder-"+a.scope(),"broken").apply();assertEquals(SidebarLayout.ALL,SidebarLayout.read(prefs,a,SidebarLayout.ALL));
    }
    @Test public void invalidPermutationsCannotModifySavedPreferences() {
        SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("layout-test",0);try {SidebarLayout.save(prefs,a,SidebarLayout.ALL,Arrays.asList("home","home"));fail("Invalid order accepted");}catch(IllegalArgumentException expected) {}assertTrue(prefs.getAll().isEmpty());
    }
}
