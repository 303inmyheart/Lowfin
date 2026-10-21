package de.lowfin.tv;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class DetailLogoTest {
    @Test public void fixedFrameContainsWideAndTallLogosAndKeepsFallbackForMissingImages() {
        TextView text=new TextView(RuntimeEnvironment.getApplication()); DetailLogo logo=new DetailLogo(RuntimeEnvironment.getApplication(),text);
        logo.setLayoutParams(new android.widget.FrameLayout.LayoutParams(228,80));
        assertEquals(ImageView.ScaleType.FIT_CENTER,logo.getScaleType()); assertFalse(logo.getAdjustViewBounds()); assertEquals(View.VISIBLE,text.getVisibility());
        for(int[] size:new int[][]{{600,60},{60,600},{200,200}}) {
            logo.setImageBitmap(Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888));
            assertEquals(228,logo.getLayoutParams().width); assertEquals(80,logo.getLayoutParams().height); assertEquals(View.GONE,text.getVisibility()); assertEquals(View.VISIBLE,logo.getVisibility());
        }
        logo.setImageDrawable(null); assertEquals(View.VISIBLE,text.getVisibility()); assertEquals(View.GONE,logo.getVisibility());
    }
}
