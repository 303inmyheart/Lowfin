package de.lowfin.tv;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class LibrarySnapshotTest {
    @Test public void batchedSnapshotsPreserveOrderMissingEntriesDuplicateIdsAndCompanionState() throws Exception {
        String scope=UUID.randomUUID().toString();
        try(Library db=new Library(RuntimeEnvironment.getApplication(),scope);Library other=new Library(RuntimeEnvironment.getApplication(),scope+"-other")) {
            List<JSONObject> stale=new ArrayList<>();JSONArray stored=new JSONArray();
            for(int i=0;i<260;i++) {
                String id="film-"+i;stale.add(Json.obj("Id",id,"Name","Old"));
                stored.put(Json.obj("Id",id,"Type","Movie","Name","Current "+i,"MediaSources",Json.arr(Json.obj("Size",9000000000L+i))));
            }
            db.put(stored,1);other.put(Json.obj("Id","film-0","Name","Other account"));
            JSONObject missing=Json.obj("Id","missing","Name","Snapshot fallback");stale.add(5,missing);stale.add(stale.get(0));
            db.companionState("film-0",Json.obj("active",true,"revision",1,"hasResume",true,"resumeTicks",123400000L,"played",false));db.companionAvailable=true;
            List<JSONObject> result=db.latestItems(stale);assertEquals(stale.size(),result.size());assertSame(missing,result.get(5));
            for(int i=0;i<stale.size();i++) {
                assertEquals(stale.get(i).optString("Id"),result.get(i).optString("Id"));
                if(i!=5) {assertTrue(result.get(i).optString("Name").startsWith("Current "));assertTrue(result.get(i).getJSONArray("MediaSources").getJSONObject(0).getLong("Size")>=9000000000L);}
            }
            assertEquals(12340,Json.position(result.get(0)));assertEquals(12340,Json.position(result.get(result.size()-1)));assertEquals("Old",stale.get(0).optString("Name"));assertFalse(stale.get(0).has("LowfinCompanion"));
            assertEquals("Other account",other.latestItems(Collections.singletonList(stale.get(0))).get(0).optString("Name"));
            db.companionAvailable=false;assertFalse(db.latestItems(stale).get(0).has("LowfinCompanion"));assertTrue(db.latestItems(Collections.emptyList()).isEmpty());
        }
    }
}
