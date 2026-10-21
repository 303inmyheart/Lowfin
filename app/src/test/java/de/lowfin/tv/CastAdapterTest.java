package de.lowfin.tv;

import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class CastAdapterTest {
    private JSONObject actor(String id,String role,String tag) { return Json.obj("Id",id,"Type","Person","Name",id,"Role",role,"ImageTags",Json.obj("Primary",tag)); }
    @Test public void unchangedActorsDoNotRebindButRoleImageIdentityAndOrderChangesDo() {
        CastAdapter adapter=new CastAdapter(null,j->{});int[] changes={0};adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver(){ @Override public void onChanged() { changes[0]++; } });
        adapter.set(Arrays.asList(actor("a","Lead","old"),actor("b","Guest","b")));assertEquals(1,changes[0]);
        adapter.set(Arrays.asList(actor("a","Lead","old"),actor("b","Guest","b")));assertEquals(1,changes[0]);
        adapter.set(Arrays.asList(actor("a","New role","old"),actor("b","Guest","b")));assertEquals(2,changes[0]);
        adapter.set(Arrays.asList(actor("a","New role","new"),actor("b","Guest","b")));assertEquals(3,changes[0]);
        adapter.set(Arrays.asList(actor("b","Guest","b"),actor("a","New role","new")));assertEquals(4,changes[0]);
        adapter.set(Collections.singletonList(actor("other","Lead","other")));assertEquals(5,changes[0]);assertEquals(1,adapter.getItemCount());
        adapter.set(Collections.emptyList());assertEquals(6,changes[0]);assertEquals(0,adapter.getItemCount());
    }
}
