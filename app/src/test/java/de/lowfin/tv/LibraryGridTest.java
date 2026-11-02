package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class LibraryGridTest {
    @Test public void rapidUpStopsAtTopAndDeliberateShortcutReturnsToOriginalColumn() {
        try(org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup()) {
            LinearLayout host=new LinearLayout(activity.get());host.setOrientation(LinearLayout.VERTICAL);
            Button jump=new Button(activity.get());jump.setText("Jump to letter");LinearLayout.LayoutParams header=new LinearLayout.LayoutParams(180,60);header.gravity=Gravity.RIGHT;host.addView(jump,header);
            LibraryGrid grid=new LibraryGrid(activity.get());grid.setLayoutManager(new GridLayoutManager(activity.get(),3));grid.setItemAnimator(null);grid.letterShortcut(jump);
            grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) {TextView card=new TextView(parent.getContext());card.setFocusable(true);card.setFocusableInTouchMode(true);card.setLayoutParams(new RecyclerView.LayoutParams(-1,100));return new RecyclerView.ViewHolder(card){};}
                public void onBindViewHolder(RecyclerView.ViewHolder h,int p) {((TextView)h.itemView).setText("Item "+p);}
                public int getItemCount() {return 15;}
            });host.addView(grid,new LinearLayout.LayoutParams(-1,0,1));activity.get().setContentView(host);
            View decor=activity.get().getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));decor.layout(0,0,900,600);Shadows.shadowOf(Looper.getMainLooper()).idle();
            View top=grid.findViewHolderForAdapterPosition(1).itemView;grid.findViewHolderForAdapterPosition(4).itemView.requestFocus();
            assertFalse("Header must not participate in global focus recovery",jump.isFocusable());
            key(activity.get(),1000,KeyEvent.KEYCODE_DPAD_UP,0);assertTrue("Focus: "+activity.get().getWindow().getDecorView().findFocus(),top.hasFocus());
            key(activity.get(),1080,KeyEvent.KEYCODE_DPAD_UP,0);assertTrue("Focus: "+activity.get().getWindow().getDecorView().findFocus(),top.hasFocus());assertFalse(jump.hasFocus());
            key(activity.get(),1400,KeyEvent.KEYCODE_DPAD_UP,0);assertTrue(jump.hasFocus());
            assertTrue(jump.isFocusable());
            key(activity.get(),1500,KeyEvent.KEYCODE_DPAD_DOWN,0);assertTrue("Focus: "+activity.get().getWindow().getDecorView().findFocus(),top.hasFocus());
            assertFalse("Returning to cards must exclude the shortcut again",jump.isFocusable());
            assertNotSame(jump,grid.focusSearch(top,View.FOCUS_RIGHT));
            key(activity.get(),1800,KeyEvent.KEYCODE_DPAD_UP,2);assertTrue("Focus: "+activity.get().getWindow().getDecorView().findFocus(),top.hasFocus());assertFalse(jump.hasFocus());
        }
    }
    @Test public void fastSeriesNavigationCannotEscapeToTabsButDeliberateUpAndReturnStillWork() {
        try(org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup()) {
            LinearLayout host=new LinearLayout(activity.get());host.setOrientation(LinearLayout.VERTICAL);
            Button jump=new Button(activity.get());jump.setText("Jump to letter");host.addView(jump,new LinearLayout.LayoutParams(-1,60));
            LinearLayout tabs=new LinearLayout(activity.get());Button all=new Button(activity.get()),resume=new Button(activity.get());all.setText("All series");resume.setText("Started episodes");tabs.addView(all,new LinearLayout.LayoutParams(0,60,1));tabs.addView(resume,new LinearLayout.LayoutParams(0,60,1));host.addView(tabs);
            LibraryGrid grid=new LibraryGrid(activity.get());grid.setLayoutManager(new GridLayoutManager(activity.get(),3));grid.setItemAnimator(null);grid.letterShortcut(jump);grid.topControls(all,resume);
            grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) {TextView card=new TextView(parent.getContext());card.setFocusable(true);card.setFocusableInTouchMode(true);card.setLayoutParams(new RecyclerView.LayoutParams(-1,100));return new RecyclerView.ViewHolder(card){};}
                public void onBindViewHolder(RecyclerView.ViewHolder holder,int position) {((TextView)holder.itemView).setText("Series "+position);}
                public int getItemCount() {return 30;}
            });host.addView(grid,new LinearLayout.LayoutParams(-1,0,1));activity.get().setContentView(host);
            View decor=activity.get().getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));decor.layout(0,0,900,600);Shadows.shadowOf(Looper.getMainLooper()).idle();
            View top=grid.findViewHolderForAdapterPosition(1).itemView;grid.findViewHolderForAdapterPosition(4).itemView.requestFocus();
            assertFalse(all.isFocusable());assertFalse(resume.isFocusable());assertFalse(jump.isFocusable());
            key(activity.get(),1000,KeyEvent.KEYCODE_DPAD_UP,0);assertTrue(top.hasFocus());
            for(int i=1;i<=8;i++) {key(activity.get(),1000+40*i,KeyEvent.KEYCODE_DPAD_UP,i);assertTrue(top.hasFocus());assertFalse(all.hasFocus());assertFalse(resume.hasFocus());}
            key(activity.get(),1700,KeyEvent.KEYCODE_DPAD_UP,0);assertTrue(all.hasFocus()||resume.hasFocus());
            key(activity.get(),1800,KeyEvent.KEYCODE_DPAD_DOWN,0);assertTrue(top.hasFocus());assertFalse(all.isFocusable());assertFalse(resume.isFocusable());
            assertTrue(grid.focusSearch(top,View.FOCUS_DOWN)==null||grid.findContainingItemView(grid.focusSearch(top,View.FOCUS_DOWN))!=null);
        }
    }
    private void key(Activity activity,long time,int key,int repeat) {
        View before=activity.getWindow().getDecorView().findFocus();
        activity.dispatchKeyEvent(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,key,repeat));
        // Robolectric does not run ViewRoot's final unhandled-key focus stage.
        if(before!=null&&before.hasFocus()) {
            View next=before.focusSearch(key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN);
            if(next!=null) next.requestFocus();
        }
        activity.dispatchKeyEvent(new KeyEvent(time,time+1,KeyEvent.ACTION_UP,key,0));
    }
}
