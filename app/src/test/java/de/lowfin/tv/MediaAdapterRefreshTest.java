package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.view.*;
import androidx.recyclerview.widget.*;
import okhttp3.mockwebserver.MockWebServer;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class MediaAdapterRefreshTest {
    private ActivityController<Activity> activity;
    private MockWebServer server;
    private Images images;
    private MediaAdapter adapter;
    private RecyclerView grid;
    private JSONObject clicked,held;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();
        activity=Robolectric.buildActivity(Activity.class).setup();
        Api api=new Api(new Account(server.url("/").toString(),"user","token","name","device"));
        images=new Images(activity.get(),api,"adapter-refresh-test");
        adapter=new MediaAdapter(images,false,item->clicked=item);adapter.onActions(item->held=item);
        grid=new RecyclerView(activity.get());grid.setLayoutManager(new GridLayoutManager(activity.get(),3));grid.setItemAnimator(null);grid.setAdapter(adapter);activity.get().setContentView(grid);
    }
    @After public void close() throws Exception {activity.pause().stop().destroy();images.close();server.shutdown();}
    private JSONObject item(int size) {return Json.obj("Id","movie","Name","Movie","Type","Movie","MediaSources",Json.arr(Json.obj("Size",size)));}
    private void submit(JSONObject item) throws Exception {
        boolean[] committed={false};adapter.set(Collections.singletonList(item),()->committed[0]=true);
        long until=System.nanoTime()+2_000_000_000L;
        while(!committed[0]&&System.nanoTime()<until) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
        assertTrue(committed[0]);
        grid.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));grid.layout(0,0,960,540);
        shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void metadataRefreshKeepsHeldConfirmAndUsesCurrentSnapshotForBothActions() throws Exception {
        submit(item(10));View card=grid.findViewHolderForAdapterPosition(0).itemView;card.requestFocus();assertTrue(card.hasFocus());
        card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        JSONObject fresh=item(20);submit(fresh);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+20));
        assertSame(fresh,held);card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));assertNull(clicked);
        card.performClick();assertSame(fresh,clicked);
    }
    @Test public void visibleRefreshRebindsAndCancelsOldHeldGesture() throws Exception {
        submit(item(10));View card=grid.findViewHolderForAdapterPosition(0).itemView;card.requestFocus();
        card.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
        JSONObject fresh=item(20).put("Name","Changed title");submit(fresh);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()+20));
        assertNull(held);assertEquals("Changed title",card.getContentDescription());card.performClick();assertSame(fresh,clicked);
    }
    @Test public void seasonCountsAndArtworkChangesNeverUseMetadataOnlyPayload() throws Exception {
        JSONObject before=item(10),after=item(20);
        assertSame(MediaDiff.METADATA_ONLY,MediaDiff.CALLBACK.getChangePayload(before,after));
        after.put("ChildCount",3);assertNull(MediaDiff.CALLBACK.getChangePayload(before,after));after.remove("ChildCount");
        after.put("RecursiveItemCount",12);assertNull(MediaDiff.CALLBACK.getChangePayload(before,after));after.remove("RecursiveItemCount");
        after.put("ImageTags",Json.obj("Primary","new"));assertNull(MediaDiff.CALLBACK.getChangePayload(before,after));
        after=item(20).put("UserData",Json.obj("Played",true));assertNull(MediaDiff.CALLBACK.getChangePayload(before,after));
    }
}
