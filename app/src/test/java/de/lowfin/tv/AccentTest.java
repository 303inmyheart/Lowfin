package de.lowfin.tv;
import android.app.*;
import android.graphics.Color;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.time.Duration;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30) @LooperMode(LooperMode.Mode.PAUSED)
public class AccentTest {
    @Test public void allChoicesStayInMutedPoolAndAvoidImmediateRepeat() { for(int current:Accent.POOL) for(int choice=0;choice<Accent.POOL.length-1;choice++) { int next=Accent.next(current,choice); assertNotEquals(current,next); float[] hsv=new float[3]; Color.colorToHSV(next,hsv); assertTrue(hsv[1]<.4f); assertTrue(hsv[2]>.65f); } }
    @Test public void hourlyChangeCountsForegroundTimeAndUpdatesBoundViews() {
        Application app=RuntimeEnvironment.getApplication(); Accent accent=new Accent(app); Activity activity=Robolectric.buildActivity(Activity.class).create().get(); android.widget.TextView view=new android.widget.TextView(activity); activity.setContentView(view); int[] observed={0}; Accent.bind(view,(v,c)->observed[0]=c); accent.onActivityStarted(activity); int first=Accent.color;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(30)); assertEquals(first,Accent.color); accent.onActivityStopped(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofHours(2)); assertEquals(first,Accent.color); accent.onActivityStarted(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(30)); assertNotEquals(first,Accent.color); assertEquals(Accent.color,observed[0]); accent.onActivityStopped(activity); app.unregisterActivityLifecycleCallbacks(accent);
    }
}
