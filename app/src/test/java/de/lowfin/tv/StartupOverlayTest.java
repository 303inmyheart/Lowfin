package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class StartupOverlayTest {
    @Test public void waitsForReadinessAndHasBoundedFailureFallbackWithoutStealingFocus() {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings",0).edit().putBoolean("animations",false).apply();
        try(org.robolectric.android.controller.ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout host=new FrameLayout(activity.get()),content=new FrameLayout(activity.get());host.addView(content);activity.get().setContentView(host);
            boolean[] fresh={false,false};StartupOverlay overlay=new StartupOverlay(activity.get(),content,()->fresh[0]&&fresh[1]);host.addView(overlay);assertFalse(overlay.isFocusable());assertEquals(1,overlay.getChildCount());assertTrue(overlay.getChildAt(0) instanceof android.widget.ImageView);assertNotNull(((android.widget.ImageView)overlay.getChildAt(0)).getDrawable());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertTrue(overlay.showing());fresh[0]=true;
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertTrue(overlay.showing());fresh[1]=true;
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));assertFalse(overlay.showing());assertNull(overlay.getParent());
            StartupOverlay unavailable=new StartupOverlay(activity.get(),content,()->false);host.addView(unavailable);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StartupOverlay.MAX_WAIT_MS+100));assertFalse(unavailable.showing());assertNull(unavailable.getParent());
        }
    }
}
