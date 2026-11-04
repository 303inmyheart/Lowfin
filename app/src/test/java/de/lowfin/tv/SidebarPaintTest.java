package de.lowfin.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,application=android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SidebarPaintTest {
    private static class Row extends Button {
        boolean focused;
        Row(android.content.Context context) {super(context);}
        @Override public boolean isFocused() {return focused;}
    }
    @Test public void legacyFocusInvalidatesTheWholeWindowToEraseRemoteStrokeRemnants() {
        class Root extends FrameLayout {int redraws;Root(android.content.Context context){super(context);}@Override public void invalidate(){redraws++;super.invalidate();}}
        Root root=new Root(RuntimeEnvironment.getApplication());Row card=new Row(root.getContext());root.addView(card);Ui.cardFocus(card);int before=root.redraws;
        card.focused=true;card.getOnFocusChangeListener().onFocusChange(card,true);assertTrue(root.redraws>before);before=root.redraws;
        card.focused=false;card.getOnFocusChangeListener().onFocusChange(card,false);assertTrue(root.redraws>before);
        before=root.redraws;Ui.navigation(card,false);card.focused=true;card.getOnFocusChangeListener().onFocusChange(card,true);assertTrue(root.redraws>before);
    }
    @Test public void rapidSidebarFocusChangesRetireOldStrokeAndClearEveryUnfocusedPixel() {
        Row row=new Row(RuntimeEnvironment.getApplication());LinearLayout parent=new LinearLayout(row.getContext());parent.addView(row);Ui.navigation(row,false);
        assertNull(row.getStateListAnimator());assertEquals(0,row.getElevation(),0);
        for(int i=0;i<10;i++) {
            row.focused=true;row.getOnFocusChangeListener().onFocusChange(row,true);Drawable focused=row.getBackground();
            row.focused=false;row.getOnFocusChangeListener().onFocusChange(row,false);assertNotSame(focused,row.getBackground());
            Bitmap bitmap=Bitmap.createBitmap(180,44,Bitmap.Config.ARGB_8888);row.getBackground().setBounds(0,0,180,44);row.getBackground().draw(new Canvas(bitmap));
            int[] pixels=new int[180*44];bitmap.getPixels(pixels,0,180,0,0,180,44);for(int pixel:pixels)assertEquals(0,Color.alpha(pixel));bitmap.recycle();
        }
    }
    @Test public void homeCardsEraseOldFocusShapeOnLegacyRenderers() {
        Row row=new Row(RuntimeEnvironment.getApplication());LinearLayout parent=new LinearLayout(row.getContext());parent.addView(row);Ui.cardFocus(row);assertFalse(row.getDefaultFocusHighlightEnabled());
        row.focused=true;row.getOnFocusChangeListener().onFocusChange(row,true);Drawable previous=row.getBackground();
        row.focused=false;row.getOnFocusChangeListener().onFocusChange(row,false);assertNotSame(previous,row.getBackground());
        Bitmap bitmap=Bitmap.createBitmap(180,120,Bitmap.Config.ARGB_8888);row.getBackground().setBounds(0,0,180,120);row.getBackground().draw(new Canvas(bitmap));
        int[] pixels=new int[180*120];bitmap.getPixels(pixels,0,180,0,0,180,120);for(int pixel:pixels)assertEquals(0,Color.alpha(pixel));bitmap.recycle();
    }
}
