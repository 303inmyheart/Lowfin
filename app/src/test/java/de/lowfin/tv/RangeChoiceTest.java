package de.lowfin.tv;

import android.app.*;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class RangeChoiceTest {
    private org.robolectric.android.controller.ActivityController<Activity> activity;
    @Before public void setup(){activity=Robolectric.buildActivity(Activity.class).setup();}
    @After public void stop(){activity.pause().stop().destroy();}
    private RangeChoice.Slider slider(int min,int max,int current){RangeChoice.Slider slider=new RangeChoice.Slider(activity.get());slider.setMin(min);slider.setMax(max);slider.setProgress(current);return slider;}
    private void down(RangeChoice.Slider slider,int key,long time,int repeat){slider.onKeyDown(key,new KeyEvent(1000,time,KeyEvent.ACTION_DOWN,key,repeat));}
    @Test public void separatePressesAreExactlyOneUnitAndCannotExceedBounds(){RangeChoice.Slider slider=slider(10,50,25);down(slider,22,1000,0);assertEquals(26,slider.getProgress());slider.onKeyUp(22,new KeyEvent(KeyEvent.ACTION_UP,22));down(slider,22,5000,0);assertEquals(27,slider.getProgress());down(slider,21,7000,0);assertEquals(26,slider.getProgress());slider.setProgress(10);down(slider,21,8000,0);assertEquals(10,slider.getProgress());slider.setProgress(50);down(slider,22,9000,0);assertEquals(50,slider.getProgress());}
    @Test public void sustainedInputIsRateLimitedAndAcceleratesInStages(){RangeChoice.Slider slider=slider(50,150,50);down(slider,22,1000,0);assertEquals(51,slider.getProgress());down(slider,22,1040,1);assertEquals(51,slider.getProgress());down(slider,22,1120,2);assertEquals(52,slider.getProgress());down(slider,22,1800,5);assertEquals(54,slider.getProgress());down(slider,22,2800,10);assertEquals(59,slider.getProgress());down(slider,22,4200,20);assertEquals(69,slider.getProgress());}
    @Test public void releaseOrDirectionChangeRestartsSlowAndLongHoldClampsAtTheLimit(){RangeChoice.Slider slider=slider(10,50,25);down(slider,22,1000,0);down(slider,22,4500,10);assertEquals(31,slider.getProgress());down(slider,21,4700,11);assertEquals(30,slider.getProgress());slider.onKeyUp(21,new KeyEvent(KeyEvent.ACTION_UP,21));down(slider,22,6000,0);assertEquals(31,slider.getProgress());slider.setProgress(49);down(slider,22,9900,30);assertEquals(50,slider.getProgress());}
    @Test public void cancelDiscardsDraftAndSaveCommitsActualValueOnce(){AtomicInteger stored=new AtomicInteger(25);AlertDialog dialog=RangeChoice.show(activity.get(),"Channels",10,50,25,stored::set);RangeChoice.Slider slider=find(dialog.getWindow().getDecorView());assertNotNull(slider);assertEquals(10,slider.getMin());assertEquals(50,slider.getMax());slider.setProgress(37);assertEquals(25,stored.get());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(25,stored.get());dialog=RangeChoice.show(activity.get(),"Channels",10,50,25,stored::set);find(dialog.getWindow().getDecorView()).setProgress(42);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(42,stored.get());}
    @Test public void pageSizeDefaultsAndPersistedBoundsAreIndependent(){android.content.SharedPreferences prefs=activity.get().getSharedPreferences("range-test",0);assertEquals(25,GuideWindow.pageSize(prefs));assertEquals(60,LibraryPageSize.read(prefs));prefs.edit().putInt("guideChannelsPerPage",-2).putInt("libraryPageSize",200).apply();assertEquals(10,GuideWindow.pageSize(prefs));assertEquals(150,LibraryPageSize.read(prefs));prefs.edit().putInt("guideChannelsPerPage",100).putInt("libraryPageSize",2).apply();assertEquals(50,GuideWindow.pageSize(prefs));assertEquals(50,LibraryPageSize.read(prefs));}
    private RangeChoice.Slider find(View view){if(view instanceof RangeChoice.Slider)return (RangeChoice.Slider)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){RangeChoice.Slider found=find(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
}
