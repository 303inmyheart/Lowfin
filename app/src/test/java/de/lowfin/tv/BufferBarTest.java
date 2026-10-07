package de.lowfin.tv;
import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
public class BufferBarTest {
    @Test public void motionRunsOnlyWhileVisibleAndAttached() {
        org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            FrameLayout root=new FrameLayout(activity.get()); activity.get().setContentView(root);
            BufferBar bar=new BufferBar(activity.get()); bar.setVisibility(View.GONE); root.addView(bar); activity.visible();
            Object attach=org.robolectric.util.ReflectionHelpers.getField(root,"mAttachInfo");
            org.robolectric.util.ReflectionHelpers.setField(attach,"mWindowVisibility",View.VISIBLE); root.dispatchWindowVisibilityChanged(View.VISIBLE); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(bar.animating()); bar.setVisibility(View.VISIBLE); assertTrue("visible bar must animate; attached="+bar.isAttachedToWindow()+", shown="+bar.isShown()+", window="+bar.getWindowVisibility(),bar.animating());
            root.setVisibility(View.GONE); assertFalse(bar.animating()); root.setVisibility(View.VISIBLE); assertTrue("visible bar must animate; attached="+bar.isAttachedToWindow()+", shown="+bar.isShown()+", window="+bar.getWindowVisibility(),bar.animating());
            root.removeView(bar); assertFalse(bar.animating());
        } finally { activity.pause().stop().destroy(); }
    }
}
