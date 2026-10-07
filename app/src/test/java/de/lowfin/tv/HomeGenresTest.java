package de.lowfin.tv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class HomeGenresTest {
    @Test public void pinsAreOrderedPersistentAndScopedToAnAccountIncludingAnEmptyList() {
        android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences(UUID.randomUUID().toString(),0);
        Account a=new Account("http://server","a","token","A","device"),b=new Account("http://server","b","token","B","device");
        assertEquals(Arrays.asList("Horror","Thriller"),HomeGenres.read(prefs,a));
        HomeGenres.toggle(prefs,a,"Drama"); assertEquals(Arrays.asList("Horror","Thriller","Drama"),HomeGenres.read(prefs,a));
        assertEquals(Arrays.asList("Horror","Thriller"),HomeGenres.read(prefs,b));
        HomeGenres.toggle(prefs,a,"Horror"); HomeGenres.toggle(prefs,a,"Thriller"); HomeGenres.toggle(prefs,a,"Drama"); assertTrue(HomeGenres.read(prefs,a).isEmpty());
    }
}
