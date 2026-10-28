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
