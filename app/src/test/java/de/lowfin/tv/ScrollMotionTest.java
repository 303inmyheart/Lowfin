package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class ScrollMotionTest {
    ActivityController<Activity> activity; RecyclerView list;
    @After public void close() { if(activity!=null) activity.pause().stop().destroy(); }
    private void setup(boolean enabled) {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("animations",true).putBoolean("scrollAnimations",enabled).commit();
        activity=Robolectric.buildActivity(Activity.class).setup();list=new RecyclerView(activity.get());list.setLayoutManager(new LinearLayoutManager(activity.get()));list.setItemAnimator(null);
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) { TextView v=new TextView(parent.getContext());v.setLayoutParams(new RecyclerView.LayoutParams(-1,100));v.setFocusable(true);return new RecyclerView.ViewHolder(v){}; }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int pos) { ((TextView)holder.itemView).setText("Item "+pos); }
            @Override public int getItemCount() { return 40; }
        });activity.get().setContentView(list);layout();
    }
    private void layout() { View root=activity.get().getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));root.layout(0,0,800,600);Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private void settle() { for(int i=0;i<150;i++) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));layout();if(!list.getLayoutManager().isSmoothScrolling()&&list.getScrollState()==RecyclerView.SCROLL_STATE_IDLE) return; }fail("Scroll did not settle"); }
    @Test public void SwitchIsOffByDefaultAndMasterAnimationSwitchWins() {
        android.content.Context context=RuntimeEnvironment.getApplication();android.content.SharedPreferences p=context.getSharedPreferences("settings",0);p.edit().clear().commit();View view=new View(context);assertFalse(ScrollMotion.enabled(view));p.edit().putBoolean("scrollAnimations",true).commit();assertTrue(ScrollMotion.enabled(view));p.edit().putBoolean("animations",false).commit();assertFalse(ScrollMotion.enabled(view));
    }
    @Test public void DisabledModeSnapsWithoutStartingAContinuousScroll() {
        setup(false);AtomicInteger callbacks=new AtomicInteger();ScrollMotion.position(list,20,true,callbacks::incrementAndGet);layout();assertFalse(list.getLayoutManager().isSmoothScrolling());assertEquals(1,callbacks.get());assertEquals(20,((LinearLayoutManager)list.getLayoutManager()).findFirstVisibleItemPosition());
    }
    @Test public void AnimatedScrollSettlesAtTargetAndCallsCompletionOnce() {
        setup(true);AtomicInteger callbacks=new AtomicInteger();ScrollMotion.position(list,10,true,callbacks::incrementAndGet);settle();assertEquals(10,((LinearLayoutManager)list.getLayoutManager()).findFirstVisibleItemPosition());assertEquals(1,callbacks.get());Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(1,callbacks.get());
    }
    @Test public void RapidRetargetingCancelsStaleCompletionAndClampsAtEdges() {
        setup(true);AtomicInteger old=new AtomicInteger(),last=new AtomicInteger();ScrollMotion.position(list,30,true,old::incrementAndGet);ScrollMotion.position(list,-3,true,last::incrementAndGet);settle();assertEquals(0,old.get());assertEquals(1,last.get());assertEquals(0,((LinearLayoutManager)list.getLayoutManager()).findFirstVisibleItemPosition());
    }
}
