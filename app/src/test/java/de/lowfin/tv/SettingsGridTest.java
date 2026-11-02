package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi")
public class SettingsGridTest {
    private List<Button> tiles(SettingsGrid grid) {
        List<Button> result=new ArrayList<>();LinearLayout rows=(LinearLayout)grid.getChildAt(0);
        for(int row=0;row<rows.getChildCount();row++) {LinearLayout line=(LinearLayout)rows.getChildAt(row);for(int col=0;col<line.getChildCount();col++)if(line.getChildAt(col) instanceof Button)result.add((Button)line.getChildAt(col));}
        return result;
    }
    private void layout(Activity activity,int width,int height) {
        View decor=activity.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));decor.layout(0,0,width,height);Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private void key(Activity activity,int code,int repeat) {
        long time=android.os.SystemClock.uptimeMillis();activity.dispatchKeyEvent(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,code,repeat));activity.dispatchKeyEvent(new KeyEvent(time,time+1,KeyEvent.ACTION_UP,code,0));Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void everyDirectionHasAnExplicitNeighborAndEdgesNeverWrapOrLoseFocus() {
        try(var activity=Robolectric.buildActivity(Activity.class).setup()) {
            AtomicInteger menu=new AtomicInteger(),opened=new AtomicInteger();SettingsGrid grid=new SettingsGrid(activity.get(),menu::incrementAndGet);
            for(int i=0;i<12;i++)grid.add("category"+i,"Category "+i,R.drawable.settings_home,opened::incrementAndGet);
            activity.get().setContentView(grid);layout(activity.get(),1280,720);List<Button> cards=tiles(grid);assertEquals(12,cards.size());grid.focusFirst();assertTrue(cards.get(0).hasFocus());
            int[][] neighbors={{0,1,0,3},{0,2,1,4},{1,2,2,5},{3,4,0,6},{3,5,1,7},{4,5,2,8},{6,7,3,9},{6,8,4,10},{7,8,5,11},{9,10,6,9},{9,11,7,10},{10,11,8,11}};
            int[] codes={KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN};
            int[] directions={View.FOCUS_LEFT,View.FOCUS_RIGHT,View.FOCUS_UP,View.FOCUS_DOWN};
            for(int index=0;index<12;index++)for(int direction=0;direction<4;direction++) {
                cards.get(index).requestFocus();assertSame(cards.get(neighbors[index][direction]),grid.focusSearch(cards.get(index),directions[direction]));
                key(activity.get(),codes[direction],0);assertTrue("Wrong target from "+index+" in direction "+direction,cards.get(neighbors[index][direction]).hasFocus());
            }
            assertEquals(4,menu.get());assertEquals(0,opened.get());
            key(activity.get(),KeyEvent.KEYCODE_DPAD_CENTER,0);assertEquals(1,opened.get());
        }
    }
    @Test public void fastRepeatedNavigationKeepsItsColumnAndOnlyAFirstLeftOpensTheMenu() {
        try(var activity=Robolectric.buildActivity(Activity.class).setup()) {
            AtomicInteger menu=new AtomicInteger();SettingsGrid grid=new SettingsGrid(activity.get(),menu::incrementAndGet);for(int i=0;i<12;i++)grid.add("c"+i,"Category "+i,R.drawable.settings_home,()->{});
            activity.get().setContentView(grid);layout(activity.get(),1280,720);List<Button> cards=tiles(grid);cards.get(1).requestFocus();
            for(int i=0;i<100;i++)key(activity.get(),KeyEvent.KEYCODE_DPAD_DOWN,i);assertTrue(cards.get(10).hasFocus());
            for(int i=0;i<100;i++)key(activity.get(),KeyEvent.KEYCODE_DPAD_UP,i);assertTrue(cards.get(1).hasFocus());
            for(int i=0;i<100;i++)key(activity.get(),KeyEvent.KEYCODE_DPAD_RIGHT,i);assertTrue(cards.get(2).hasFocus());assertEquals(0,menu.get());
            cards.get(0).requestFocus();key(activity.get(),KeyEvent.KEYCODE_DPAD_LEFT,0);key(activity.get(),KeyEvent.KEYCODE_DPAD_LEFT,1);assertEquals(1,menu.get());assertTrue(cards.get(0).hasFocus());
        }
    }
    @Test public void anIncompleteLastRowKeepsItsWidthsAndCannotJumpToTheWrongColumn() {
        try(var activity=Robolectric.buildActivity(Activity.class).setup()) {
            SettingsGrid grid=new SettingsGrid(activity.get(),()->{});for(int i=0;i<5;i++)grid.add("c"+i,"Category "+i,R.drawable.settings_home,()->{});
            activity.get().setContentView(grid);layout(activity.get(),1280,720);List<Button> cards=tiles(grid);assertEquals(5,cards.size());
            for(Button card:cards)assertTrue(Math.abs(card.getWidth()-cards.get(0).getWidth())<=1);
            cards.get(2).requestFocus();key(activity.get(),KeyEvent.KEYCODE_DPAD_DOWN,0);assertTrue(cards.get(2).hasFocus());cards.get(4).requestFocus();key(activity.get(),KeyEvent.KEYCODE_DPAD_RIGHT,0);assertTrue(cards.get(4).hasFocus());key(activity.get(),KeyEvent.KEYCODE_DPAD_UP,0);assertTrue(cards.get(1).hasFocus());
        }
    }
    @Test public void categoriesExposeTheirLabelsIconsAndGridCoordinatesToAccessibility() {
        try(var activity=Robolectric.buildActivity(Activity.class).setup()) {
            SettingsGrid grid=new SettingsGrid(activity.get(),()->{});for(int i=0;i<12;i++)grid.add("c"+i,"Category "+i,R.drawable.settings_home,()->{});
            activity.get().setContentView(grid);layout(activity.get(),1280,720);
            AccessibilityNodeInfo list=grid.getChildAt(0).createAccessibilityNodeInfo();assertEquals(4,list.getCollectionInfo().getRowCount());assertEquals(3,list.getCollectionInfo().getColumnCount());
            List<Button> cards=tiles(grid);for(int i=0;i<cards.size();i++) {
                Button card=cards.get(i);AccessibilityNodeInfo item=card.createAccessibilityNodeInfo();assertEquals("Category "+i,item.getText().toString());assertEquals(i/3,item.getCollectionItemInfo().getRowIndex());assertEquals(i%3,item.getCollectionItemInfo().getColumnIndex());assertNotNull(card.getCompoundDrawables()[1]);
            }
        }
    }
}
