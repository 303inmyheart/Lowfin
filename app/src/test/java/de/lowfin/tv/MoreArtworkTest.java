package de.lowfin.tv;
import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MoreArtworkTest {
    @Test public void ArrowUsesAccentAndUpdatesOnColorChange() throws Exception {
        HomeAdapter.MoreArtwork arrow=new HomeAdapter.MoreArtwork(); arrow.setBounds(0,0,256,256);
        Bitmap bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap);
        arrow.color(0xffa0cb9c); arrow.draw(canvas); assertEquals(0xffa0cb9c,bitmap.getPixel(128,128));
        arrow.color(0xffaca1dc); arrow.draw(canvas); assertEquals(0xffaca1dc,bitmap.getPixel(128,128));
        assertEquals(0xff202023,bitmap.getPixel(10,10));
        java.io.File output=new java.io.File("build/qa/more-arrow.png"); output.getParentFile().mkdirs();
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,stream); }
    }
}
