package de.lowfin.tv;
import android.content.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class ChannelLibrariesTest {
    private Library db; private SharedPreferences prefs; private Account account;
    @Before public void setup() { Context c=RuntimeEnvironment.getApplication(); db=new Library(c,"channel-choice-test"); prefs=c.getSharedPreferences("channel-choice",0); prefs.edit().clear().commit(); account=new Account("https://test.example","user","token","name","device"); db.state("views",new JSONArray().put(Json.obj("Id","anime","Name","Anime")).put(Json.obj("Id","shows","Name","Serien")).put(Json.obj("Id","live","CollectionType","livetv")).toString());
        db.state("tv-library-Series-anime",new JSONArray().put(Json.obj("Id","a1","Type","Series")).put(Json.obj("Id","a2","Type","Series")).toString()); db.state("tv-library-Series-shows",new JSONArray().put(Json.obj("Id","s1","Type","Series")).put(Json.obj("Id","s2","Type","Series")).toString()); }
    @After public void close() { db.close(); }
    @Test public void defaultMixesAllLibrariesInsteadOfOnlyLastIndexedLibrary() { List<JSONObject> items=ChannelLibraries.cached(db,prefs,account,"Series"); assertEquals(4,items.size()); assertEquals("a1",items.get(0).optString("Id")); assertEquals("s1",items.get(1).optString("Id")); }
    @Test public void chosenLibraryIsIndependentOfFilmChoicesAndOtherAccounts() { prefs.edit().putString(ChannelLibraries.key(account,"Series"),"[\"shows\"]").commit(); assertEquals("s1",ChannelLibraries.cached(db,prefs,account,"Series").get(0).optString("Id")); assertNull(ChannelLibraries.selection(prefs,account,"Movie")); assertNull(ChannelLibraries.selection(prefs,new Account("https://other.example","user","token","name","device"),"Series")); }
    @Test public void ExplicitEmptySelectionPublishesNoCards() { prefs.edit().putString(ChannelLibraries.key(account,"Series"),"[]").commit(); assertTrue(ChannelLibraries.cached(db,prefs,account,"Series").isEmpty()); }
    @Test public void mixingDeduplicatesAndHonorsLimit() { JSONObject movie=Json.obj("Id","same"); assertEquals(1,ChannelLibraries.mix(Arrays.asList(Arrays.asList(movie,movie),Arrays.asList(movie)),24).size()); assertEquals(2,ChannelLibraries.mix(Arrays.asList(Arrays.asList(movie,Json.obj("Id","b")),Arrays.asList(Json.obj("Id","c"))),2).size()); }
}
