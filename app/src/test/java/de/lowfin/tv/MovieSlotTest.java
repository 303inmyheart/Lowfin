package de.lowfin.tv;

import android.app.Activity;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.time.Duration;
import java.util.Arrays;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MovieSlotTest {
    @Test public void rouletteWaitsFourSecondsAndOpensExactlyOnceAfterCelebration() {
        ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup();
        MovieSlot slot=new MovieSlot(activity.get(),Arrays.asList("Film A","Film B")); activity.get().setContentView(slot);
        int[] opened={0}; slot.finish("Film B",()->opened[0]++);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3999)); assertEquals(0,opened[0]);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1001)); assertEquals(1,opened[0]);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2)); assertEquals(1,opened[0]);
        activity.pause().stop().destroy();
    }
    @Test public void slotStartsEmptyAndShowsOnlyRealLibraryTitles() throws Exception {
        ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup(); MovieSlot slot=new MovieSlot(activity.get(),java.util.Collections.emptyList()); activity.get().setContentView(slot);
        assertEquals("",slot.rowTitle(0)); slot.setNames(Arrays.asList("Alien – Das unheimliche Wesen", "The Thing", "Blade Runner", "Halloween"));
        assertEquals("Blade Runner",slot.rowTitle(2)); slot.measure(android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(480,android.view.View.MeasureSpec.EXACTLY)); slot.layout(0,0,720,480);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(720,480,android.graphics.Bitmap.Config.ARGB_8888); slot.draw(new android.graphics.Canvas(bitmap)); java.io.File dir=new java.io.File("build/qa"); dir.mkdirs(); try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"movie-slot.png"))) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle(); slot.stop(); activity.pause().stop().destroy();
    }
    private String text(android.view.View view) { String result=view instanceof android.widget.TextView?((android.widget.TextView)view).getText().toString():""; if(view instanceof android.view.ViewGroup) { android.view.ViewGroup group=(android.view.ViewGroup)view; for(int i=0;i<group.getChildCount();i++) result+=" " +text(group.getChildAt(i)); } return result; }
    @Test public void canceledRouletteCannotOpenItsPendingFilm() {
        ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup();
        MovieSlot slot=new MovieSlot(activity.get(),Arrays.asList("Film A","Film B")); activity.get().setContentView(slot);
        int[] opened={0}; slot.finish("Film B",()->opened[0]++); slot.stop(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals(0,opened[0]); activity.pause().stop().destroy();
    }
}
