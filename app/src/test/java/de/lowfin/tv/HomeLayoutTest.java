package de.lowfin.tv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import android.content.SharedPreferences;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class HomeLayoutTest {
    private final Account a=new Account("http://server","a","token","A","device"),b=new Account("http://server","b","token","B","device");
    private SharedPreferences prefs() { return RuntimeEnvironment.getApplication().getSharedPreferences(UUID.randomUUID().toString(),0); }
    @Test public void everyRowMovesIndependentlyAndIsScopedToAccount() {
        SharedPreferences prefs=prefs();List<String> keys=HomeLayout.keys(Arrays.asList("Horror","Thriller"),true);
        for(int i=1;i<keys.size();i++) HomeLayout.move(prefs,a,keys,"libraries",-1);
        assertEquals("libraries",HomeLayout.read(prefs,a,keys).get(0));assertEquals(keys,HomeLayout.read(prefs,b,keys));
        HomeLayout.move(prefs,a,keys,"genre:Thriller",-1);
        List<String> ordered=HomeLayout.read(prefs,a,keys);assertTrue(ordered.indexOf("genre:Thriller")<ordered.indexOf("genre:Horror"));
        assertEquals(keys.size(),new HashSet<>(ordered).size());
    }
    @Test public void temporaryGenreRemovalAndLibrarySwitchDoNotDiscardTheirSavedOrder() {
        SharedPreferences prefs=prefs();List<String> all=HomeLayout.keys(Arrays.asList("Horror"),true);
        HomeLayout.move(prefs,a,all,"genre:Horror",-1);
        List<String> fewer=HomeLayout.keys(Collections.emptyList(),false);HomeLayout.move(prefs,a,fewer,"next",-1);
        List<String> restored=HomeLayout.read(prefs,a,all);
        assertEquals("next",restored.get(0));assertTrue(restored.indexOf("genre:Horror")<restored.indexOf("recommendation:4"));
    }
    @Test public void corruptDuplicateAndUnknownPreferencesAreNormalizedAndNewRowsAppended() {
        SharedPreferences prefs=prefs();String key="homeOrder-"+a.scope();List<String> all=HomeLayout.keys(Arrays.asList("New genre"),true);
        prefs.edit().putString(key,"[\"next\",\"next\",\"removed\"]").apply();List<String> order=HomeLayout.read(prefs,a,all);
        assertEquals("next",order.get(0));assertEquals(all.size(),order.size());assertFalse(order.contains("removed"));
        prefs.edit().putString(key,"broken").apply();assertEquals(all,HomeLayout.read(prefs,a,all));
    }
    @Test public void adapterNavigationUsesDisplayOrderWhileAsyncUpdatesKeepLogicalRows() {
        SharedPreferences prefs=I18n.preferences();List<String> genres=Arrays.asList("Horror");List<String> keys=HomeLayout.keys(genres,true);
        for(int i=1;i<keys.size();i++) HomeLayout.move(prefs,a,keys,"libraries",-1);
        int[] target={-1};HomeAdapter adapter=new HomeAdapter(null,genres,(j,r)->{},r->{},(j,r)->{},r->target[0]=r);adapter.configure(prefs,a,genres,true);
        int library=adapter.logical(0);assertTrue(adapter.libraries(library));assertFalse(adapter.feature(library));assertEquals(library,adapter.firstRow());
        adapter.moveRow(library,1);assertEquals(0,target[0]);adapter.moveRow(0,-1);assertEquals(library,target[0]);
        adapter.update(0,Arrays.asList(Json.obj("Id","resume")));assertEquals("resume",adapter.rows.get(0).get(0).optString("Id"));assertEquals(1,adapter.position(0));
        adapter.libraries(Arrays.asList(Json.obj("Id","movies","CollectionType","movies"),Json.obj("Id","tv","CollectionType","livetv")));
        assertEquals(1,adapter.rows.get(library).size());assertEquals("LowfinLibrary",adapter.rows.get(library).get(0).optString("Type"));
    }
}
