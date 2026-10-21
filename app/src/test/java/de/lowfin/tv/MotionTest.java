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
    @Test public void focusGrowthIsLimitedByClippingAncestorsAndKeepsZoomWhereThereIsRoom() {
        android.content.Context c=RuntimeEnvironment.getApplication();android.widget.FrameLayout outer=new android.widget.FrameLayout(c),inner=new android.widget.FrameLayout(c);View view=new View(c);
        outer.addView(inner);inner.addView(view);outer.layout(0,0,500,500);inner.layout(0,0,500,500);view.layout(0,100,500,200);
        assertEquals(1f,Motion.boundedScale(view,1.035f),0.0001f);
        view.layout(100,100,200,200);assertEquals(1.035f,Motion.boundedScale(view,1.035f),0.0001f);
        inner.setClipChildren(false);inner.setClipToPadding(false);inner.layout(0,0,600,500);view.layout(400,100,500,200);assertEquals(1f,Motion.boundedScale(view,1.035f),0.0001f);
        inner.layout(0,0,500,500);inner.setClipChildren(true);inner.setClipToPadding(true);inner.setPadding(20,20,20,20);view.layout(20,100,120,200);assertEquals(1f,Motion.boundedScale(view,1.065f),0.0001f);
    }
    @Test public void bottomEntryUsesOnlyVerticalTranslationAndDisabledMotionClearsBothAxes() {
        android.content.Context c=RuntimeEnvironment.getApplication(); android.content.SharedPreferences p=c.getSharedPreferences("settings",0);
        View view=new View(c);p.edit().putBoolean("animations",true).putBoolean("smoothNavigation",true).commit();
        view.setTranslationX(90);Motion.enterFromBottom(view);assertEquals(0,view.getTranslationX(),0);assertTrue(view.getTranslationY()>0);
        Motion.enterFromBottom(view);assertEquals(Ui.dp(c,18),view.getTranslationY(),0);
        p.edit().putBoolean("animations",false).commit();Motion.enterFromBottom(view);assertEquals(0,view.getTranslationX(),0);assertEquals(0,view.getTranslationY(),0);assertEquals(1,view.getAlpha(),0);
    }
}
