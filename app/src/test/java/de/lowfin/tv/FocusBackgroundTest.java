package de.lowfin.tv;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class FocusBackgroundTest {
    @Test public void controlsKeepTheirOwnFocusShapeAcrossRepeatedFocusAndBlur() {
        try(org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()) {
            Activity context=controller.get();LinearLayout root=Ui.column(context);context.setContentView(root);
            for(int style=0;style<6;style++) {
                Button first=Ui.button(context,"First",()->{}),second=Ui.button(context,"Second",()->{});
                if(style==1) { Ui.cardFocus(first);Ui.cardFocus(second); }
                if(style==2) { Ui.fixedFocus(first);Ui.fixedFocus(second); }
                if(style==3) { Ui.navigation(first,false);Ui.navigation(second,true); }
                if(style==4) { first=Ui.quiet(context,"First",()->{});second=Ui.quiet(context,"Second",()->{}); }
                if(style==5) { first=Ui.primary(context,"First",()->{});second=Ui.primary(context,"Second",()->{}); }
                root.addView(first);root.addView(second);GradientDrawable a=(GradientDrawable)first.getBackground(),b=(GradientDrawable)second.getBackground();assertNotSame(a,b);
                for(int i=0;i<10;i++) { first.requestFocus();assertTrue(first.hasFocus());assertSame(a,first.getBackground());second.requestFocus();assertTrue(second.hasFocus());assertSame(b,second.getBackground());assertSame(a,first.getBackground()); }
                if(style==0||style==2) { assertEquals(Ui.PANEL,a.getColor().getDefaultColor());assertEquals(0xff383838,b.getColor().getDefaultColor()); }
                if(style==1) { assertEquals(android.graphics.Color.TRANSPARENT,a.getColor().getDefaultColor());assertEquals(0xff242427,b.getColor().getDefaultColor()); }
                root.removeAllViews();
            }
        }
    }
}
