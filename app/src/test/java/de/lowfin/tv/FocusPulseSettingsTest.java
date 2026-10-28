package de.lowfin.tv;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class FocusPulseSettingsTest {
    @Test public void appearanceUsesTheSharedSwitchSpacingAndUpdatesWithoutRebuildingFocus() {
        LowfinApp app=(LowfinApp)RuntimeEnvironment.getApplication();app.settings.edit().remove("focusPulse").commit();
        try(org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup()) {
            LinearLayout column=Ui.column(activity.get());activity.get().setContentView(column);java.util.concurrent.atomic.AtomicInteger reloads=new java.util.concurrent.atomic.AtomicInteger();new Options(activity.get(),app,column,reloads::incrementAndGet,true).appearance();
            SettingSwitch pulse=null;int index=-1;for(int i=0;i<column.getChildCount();i++) {View view=column.getChildAt(i);if(view instanceof SettingSwitch&&((SettingSwitch)view).getText().toString().equals(I18n.resource(R.string.focus_pulse))) {pulse=(SettingSwitch)view;index=i;break;}}
            assertNotNull(pulse);assertFalse(pulse.isChecked());assertTrue(index>0);assertEquals(Ui.dp(activity.get(),8),column.getChildAt(index-1).getLayoutParams().height);assertTrue(pulse.getMinHeight()>=Ui.dp(activity.get(),44));
            pulse.performClick();assertTrue(app.settings.getBoolean("focusPulse",false));assertSame(pulse,column.getChildAt(index));assertEquals(0,reloads.get());pulse.performClick();assertFalse(app.settings.getBoolean("focusPulse",true));
        }
    }
}
