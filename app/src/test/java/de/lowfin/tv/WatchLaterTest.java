package de.lowfin.tv;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class WatchLaterTest {
    private WatchLater later;
    private JSONObject movie;
    @Before public void setup() {
        later=new WatchLater(RuntimeEnvironment.getApplication(),new Account("http://server","user-"+java.util.UUID.randomUUID(),"token","User","device"));
        movie=Json.obj("Id","movie","Type","Movie","UserData",Json.obj("Played",false,"IsFavorite",true));
    }
    @Test public void incompletePlaybackKeepsTheEntryButConfirmedCompletionRemovesIt() throws Exception {
        later.set(movie,true);String saved=later.snapshot("movie");assertFalse(later.completed(movie,false,saved));assertTrue(later.contains("movie"));
        movie.getJSONObject("UserData").put("Played",true);assertTrue(later.completed(movie,false,saved));assertFalse(later.contains("movie"));assertTrue(movie.getJSONObject("UserData").optBoolean("IsFavorite"));
    }
    @Test public void alreadyWatchedItemsCanBeQueuedAgainAndRemainUntilPlaybackEnds() throws Exception {
        movie.getJSONObject("UserData").put("Played",true);later.set(movie,true);String saved=later.snapshot("movie");assertFalse(later.completed(movie,false,saved));assertTrue(later.completed(movie,true,saved));
    }
    @Test public void staleCompletionCannotRemoveAnExplicitlyReaddedEntry() {
        later.set(movie,true);String old=later.snapshot("movie");later.set(movie,false);later.set(movie,true);
        assertFalse(later.completed(movie,true,old));assertTrue(later.contains("movie"));assertFalse(later.items().get(0).has("_LowfinWatchLaterEntry"));
    }
    @Test public void episodeAndSeriesEntriesAndOtherAccountsArePreserved() throws Exception {
        movie.put("Type","Episode");later.set(movie,true);assertFalse(later.completed(movie,true,later.snapshot("movie")));assertTrue(later.contains("movie"));
        WatchLater other=new WatchLater(RuntimeEnvironment.getApplication(),new Account("http://server","other","token","Other","device"));assertFalse(other.contains("movie"));
        movie.put("Type","Series");later.set(movie,true);assertFalse(later.completed(movie,true,later.snapshot("movie")));
    }
    @Test public void companionNeverCompleteOverridesNativePlayedAndUsesTheEffectiveStatus() throws Exception {
        later.set(movie,true);String saved=later.snapshot("movie");movie.getJSONObject("UserData").put("Played",true);
        JSONObject current=CompanionPolicy.decorate(movie,Json.obj("active",true,"played",false));assertFalse(later.completed(current,false,saved));
        current.getJSONObject("LowfinCompanion").put("played",true);assertTrue(later.completed(current,false,saved));
    }
}
