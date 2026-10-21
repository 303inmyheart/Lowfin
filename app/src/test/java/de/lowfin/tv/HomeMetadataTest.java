package de.lowfin.tv;

import androidx.recyclerview.widget.RecyclerView;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class HomeMetadataTest {
    private HomeAdapter adapter;private int updates;
    @Before public void setup() {
        I18n.init(RuntimeEnvironment.getApplication());adapter=new HomeAdapter(null,Arrays.asList("Horror","Thriller"),(item,row)->{},row->{},(item,row)->{},row->{});
        adapter.update(0,new ArrayList<>(Arrays.asList(film("a"))));adapter.update(7,new ArrayList<>(Arrays.asList(film("a"),film("c"),film("a"))));adapter.update(8,new ArrayList<>(Arrays.asList(film("b"))));
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver(){@Override public void onItemRangeChanged(int start,int count,Object payload){updates+=count;}});
    }
    private JSONObject film(String id) {return Json.obj("Id",id,"Type","Movie","Name",id);}
    @Test public void emptyAndUnrelatedChangesLeaveShelfListsAndNotificationsUntouched() {
        List<List<JSONObject>> previous=new ArrayList<>(adapter.rows);adapter.metadata(Json.obj("changes",new JSONArray()));adapter.metadata(Json.obj("changes",Json.arr(Json.obj("item",film("not-visible")))));
        for(int i=0;i<previous.size();i++) assertSame(previous.get(i),adapter.rows.get(i));assertEquals(0,updates);
    }
    @Test public void matchingChangesRefreshAllOccurrencesAndActionDataWithoutMutatingOldSnapshots() throws Exception {
        List<JSONObject> first=adapter.rows.get(0),genre=adapter.rows.get(7),unrelated=adapter.rows.get(8);JSONObject fresh=film("a");fresh.put("MediaSources",Json.arr(Json.obj("Id","new-file")));
        adapter.metadata(Json.obj("changes",Json.arr(Json.obj("item",fresh))));
        assertSame(fresh,adapter.rows.get(0).get(0));assertSame(fresh,adapter.rows.get(7).get(0));assertSame(fresh,adapter.rows.get(7).get(2));assertSame(unrelated,adapter.rows.get(8));assertFalse(first.get(0).has("MediaSources"));assertFalse(genre.get(0).has("MediaSources"));assertEquals(0,updates);
        JSONObject renamed=film("a");renamed.put("Name","Updated name");adapter.metadata(Json.obj("changes",Json.arr(Json.obj("item",renamed))));assertEquals(2,updates);assertEquals("Updated name",adapter.rows.get(7).get(2).optString("Name"));assertSame(unrelated,adapter.rows.get(8));
    }
}
