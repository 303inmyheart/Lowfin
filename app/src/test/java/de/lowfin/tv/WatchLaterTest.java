package de.lowfin.tv;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.UUID;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class WatchLaterTest {
    @Test public void watchListPersistsAndDoesNotMixAccountsOrFavorites() {
        String id=UUID.randomUUID().toString(); Account a=new Account("http://server",id,"token","A","device"),b=new Account("http://server",id+"b","token","B","device");
        WatchLater list=new WatchLater(RuntimeEnvironment.getApplication(),a);
        JSONObject movie=Json.obj("Id","film","Type","Movie","Name","Film","UserData",Json.obj("IsFavorite",false));
        list.set(movie,true); assertTrue(new WatchLater(RuntimeEnvironment.getApplication(),a).contains("film"));
        assertFalse(new WatchLater(RuntimeEnvironment.getApplication(),b).contains("film"));
        assertFalse(Json.object(list.items().get(0),"UserData").optBoolean("IsFavorite"));
        list.set(movie,false); assertTrue(list.items().isEmpty());
    }
}
