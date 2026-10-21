package de.lowfin.tv;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CastTest {
    @Test public void actorsAndGuestsAreDeduplicatedAndHaveImageTags() {
        JSONObject item=Json.obj("People",Json.arr(Json.obj("Id","actor","Type","Actor","Name","A","PrimaryImageTag","portrait"),Json.obj("Id","actor","Type","GuestStar"),Json.obj("Id","director","Type","Director"),Json.obj("Id","guest","Type","GuestStar","Name","B")));
        assertEquals(2,Cast.actors(item).size()); assertEquals("portrait",Json.object(Cast.actors(item).get(0),"ImageTags").optString("Primary")); assertEquals("Person",Cast.actors(item).get(0).optString("Type"));
    }
    @Test public void filmographyExcludesEpisodesAndPeople() { assertEquals(2,Cast.titles(Json.arr(Json.obj("Type","Movie"),Json.obj("Type","Series"),Json.obj("Type","Episode"),Json.obj("Type","Person"))).size()); }
}
