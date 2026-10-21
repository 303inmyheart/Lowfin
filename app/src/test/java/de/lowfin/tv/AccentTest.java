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
    @Test public void everyAccentHasEightDistinctDarkBackgroundPalettes() {
        for(int accent:Accent.POOL) {
            java.util.Set<String> unique=new java.util.HashSet<>();
            for(int variant=0;variant<AccentBackground.VARIANTS;variant++) {
                int[] colors=AccentBackground.colors(accent,variant); unique.add(java.util.Arrays.toString(colors));
                for(int color:colors) {
                    assertEquals(255,Color.alpha(color));
                    assertTrue(Color.red(color)<=50&&Color.green(color)<=50&&Color.blue(color)<=50);
                    assertNotEquals(Ui.BG,color);
                }
            }
            assertEquals(8,unique.size());
        }
    }
    @Test public void repeatedLifecycleStylingRetainsGradientsButColorVariantAndExternalChangesRefreshThem() {
        int color=Accent.color,variant=Accent.backgroundVariant;
        try {
            android.view.View view=new android.view.View(RuntimeEnvironment.getApplication());int[] builds={0};
            Accent.background(view,(c,v)-> {builds[0]++;return AccentBackground.drawable(c,v);});Object first=view.getBackground();
            for(int i=0;i<20;i++) Accent.apply(view);assertSame(first,view.getBackground());assertEquals(1,builds[0]);
            Accent.color=Accent.next(Accent.color,0);Accent.apply(view);assertNotSame(first,view.getBackground());assertEquals(2,builds[0]);
            Object next=view.getBackground();Accent.backgroundVariant=(Accent.backgroundVariant+1)%8;Accent.apply(view);assertNotSame(next,view.getBackground());assertEquals(3,builds[0]);
            view.setBackgroundColor(Color.BLACK);Accent.apply(view);assertEquals(4,builds[0]);assertTrue(view.getBackground() instanceof SmoothGradient);
        } finally {Accent.color=color;Accent.backgroundVariant=variant;}
    }
    @Test public void allChoicesStayInMutedPoolAndAvoidImmediateRepeat() { for(int current:Accent.POOL) for(int choice=0;choice<Accent.POOL.length-1;choice++) { int next=Accent.next(current,choice); assertNotEquals(current,next); float[] hsv=new float[3]; Color.colorToHSV(next,hsv); assertTrue(hsv[1]<.4f); assertTrue(hsv[2]>.65f); } }
    @Test public void hourlyChangeCountsForegroundTimeAndUpdatesBoundViews() {
        Application app=RuntimeEnvironment.getApplication(); Accent accent=new Accent(app); Activity activity=Robolectric.buildActivity(Activity.class).create().get(); android.widget.TextView view=new android.widget.TextView(activity); activity.setContentView(view); int[] observed={0}; Accent.bind(view,(v,c)->observed[0]=c); accent.onActivityStarted(activity); int first=Accent.color;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(30)); assertEquals(first,Accent.color); accent.onActivityStopped(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofHours(2)); assertEquals(first,Accent.color); accent.onActivityStarted(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(30)); assertNotEquals(first,Accent.color); assertEquals(Accent.color,observed[0]); accent.onActivityStopped(activity); app.unregisterActivityLifecycleCallbacks(accent);
    }
}
