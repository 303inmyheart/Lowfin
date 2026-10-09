package de.lowfin.tv;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class MotionTest {
    @Test public void eitherAnimationSwitchDisablesMotionAndRestoresNeutralGeometry() {
        android.content.Context c=RuntimeEnvironment.getApplication(); android.content.SharedPreferences p=c.getSharedPreferences("settings",0);
        View view=new View(c); p.edit().putBoolean("animations",true).putBoolean("smoothNavigation",false).apply(); assertFalse(Motion.enabled(view));
        view.setScaleX(1.06f); view.setScaleY(1.06f); view.setAlpha(.6f); view.setTranslationX(18);
        Motion.focus(view,true,true); Motion.enter(view,true); assertEquals(1,view.getScaleX(),0); assertEquals(1,view.getScaleY(),0); assertEquals(1,view.getAlpha(),0); assertEquals(0,view.getTranslationX(),0);
        p.edit().putBoolean("animations",false).putBoolean("smoothNavigation",true).apply(); assertFalse(Motion.enabled(view));
        p.edit().putBoolean("animations",true).apply(); assertTrue(Motion.enabled(view)); p.edit().remove("animations").remove("smoothNavigation").apply();
    }
}
