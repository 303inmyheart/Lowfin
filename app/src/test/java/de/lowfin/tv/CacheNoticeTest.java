package de.lowfin.tv;

import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class CacheNoticeTest {
    @Test public void burstsCoalesceAndEmptyPollsDoNotNotify() {
        List<Integer> shown=new ArrayList<>();CacheNotice notices=new CacheNotice(()->true,shown::add);
        notices.changed(0);notices.changed(2);notices.changed(3);Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(shown.isEmpty());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));assertEquals(Collections.singletonList(5),shown);
        notices.changed(1);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));assertEquals(Collections.singletonList(5),shown);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(58500));assertEquals(Arrays.asList(5,1),shown);notices.close();
    }
    @Test public void continuousChangesNotifyAtMostOncePerMinuteWithoutLosingCounts() {
        List<Integer> shown=new ArrayList<>();CacheNotice notices=new CacheNotice(()->true,shown::add);
        notices.changed(1);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
        for(int i=0;i<59;i++) {notices.changed(8);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));}
        assertEquals(Collections.singletonList(1),shown);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(Arrays.asList(1,472),shown);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2));assertEquals(2,shown.size());notices.close();
    }
    @Test public void disablingDuringCooldownDropsTheSummary() {
        boolean[] enabled={true};List<Integer> shown=new ArrayList<>();CacheNotice notices=new CacheNotice(()->enabled[0],shown::add);
        notices.changed(1);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
        notices.changed(9);Shadows.shadowOf(Looper.getMainLooper()).idle();enabled[0]=false;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));assertEquals(Collections.singletonList(1),shown);notices.close();
    }
    @Test public void disablingOrLeavingForegroundDropsQueuedAndFutureNotices() {
        boolean[] allowed={true};List<Integer> shown=new ArrayList<>();CacheNotice notices=new CacheNotice(()->allowed[0],shown::add);
        notices.changed(4);Shadows.shadowOf(Looper.getMainLooper()).idle();allowed[0]=false;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertTrue(shown.isEmpty());
        notices.changed(8);Shadows.shadowOf(Looper.getMainLooper()).idle();allowed[0]=true;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertTrue(shown.isEmpty());
        notices.changed(1);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertEquals(Collections.singletonList(1),shown);notices.close();
    }
    @Test public void pluginCacheSettingUsesAReversibleSwitchAndHidesWithoutPlugin() {
        LowfinApp app=(LowfinApp)RuntimeEnvironment.getApplication();
        app.companion=new CompanionApi(app,new Api(new Account("http://127.0.0.1:1","user","token","User","device")),new Library(app,"notice-setting"));
        org.robolectric.util.ReflectionHelpers.setField(app.companion,"available",true);org.robolectric.util.ReflectionHelpers.setField(app.companion,"incrementalCache",true);
        app.settings.edit().remove("companionUpdateToast").apply();
        try(org.robolectric.android.controller.ActivityController<android.app.Activity> activity=Robolectric.buildActivity(android.app.Activity.class).setup()) {
            android.widget.LinearLayout rows=Ui.column(activity.get());new Options(activity.get(),app,rows,()->{},true).cache();SettingSwitch setting=null;
            for(int i=0;i<rows.getChildCount();i++) if(rows.getChildAt(i) instanceof SettingSwitch&&((SettingSwitch)rows.getChildAt(i)).getText().toString().equals(I18n.resource(R.string.companion_update_toast))) setting=(SettingSwitch)rows.getChildAt(i);
            assertNotNull(setting);assertTrue(setting.isChecked());setting.performClick();assertFalse(app.settings.getBoolean("companionUpdateToast",true));setting.performClick();assertTrue(app.settings.getBoolean("companionUpdateToast",false));
            app.companion=null;rows.removeAllViews();new Options(activity.get(),app,rows,()->{},true).cache();
            for(int i=0;i<rows.getChildCount();i++) if(rows.getChildAt(i) instanceof android.widget.Button) assertNotEquals(I18n.resource(R.string.companion_update_toast),((android.widget.Button)rows.getChildAt(i)).getText().toString());
        }
    }
    @Test public void accountRetirementCancelsAlreadyPostedWork() {
        List<Integer> shown=new ArrayList<>();CacheNotice notices=new CacheNotice(()->true,shown::add);
        notices.changed(2);Shadows.shadowOf(Looper.getMainLooper()).idle();notices.close();notices.changed(9);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertTrue(shown.isEmpty());
    }
}
