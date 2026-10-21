package de.lowfin.tv;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SmoothGradientTest {
    @Test public void EightNewPatternsRenderWithoutHardColorEdgesAndRemainStable() throws Exception {
        Set<Integer> distinct=new HashSet<>();Bitmap sheet=Bitmap.createBitmap(1280,360,Bitmap.Config.ARGB_8888);Canvas preview=new Canvas(sheet);
        for(int variant=0;variant<8;variant++) {
            SmoothGradient background=AccentBackground.drawable(Accent.POOL[0],variant); background.setBounds(0,0,960,540);
            Bitmap first=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);background.draw(new Canvas(first));
            Bitmap again=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);background.draw(new Canvas(again));assertTrue(first.sameAs(again));
            int[] pixels=new int[960*540];first.getPixels(pixels,0,960,0,0,960,540);distinct.add(Arrays.hashCode(pixels));
            for(int x=1;x<960;x++) { int a=first.getPixel(x-1,270),b=first.getPixel(x,270);assertEquals(255,Color.alpha(b));assertTrue(Math.abs(Color.red(a)-Color.red(b))<=4);assertTrue(Math.abs(Color.green(a)-Color.green(b))<=4);assertTrue(Math.abs(Color.blue(a)-Color.blue(b))<=4); }
            int x=variant%4*320,y=variant/4*180;preview.drawBitmap(first,null,new Rect(x,y,x+320,y+180),new Paint(Paint.FILTER_BITMAP_FLAG));first.recycle();again.recycle();
        }
        assertEquals(8,distinct.size());File file=new File("build/qa/smooth-backgrounds.png");file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)) { sheet.compress(Bitmap.CompressFormat.PNG,100,out); }sheet.recycle();
    }
    @Test public void DitheringPreservesTransparencyAndDrawableOpacity() {
        SmoothGradient background=SmoothGradient.linear(GradientDrawable.Orientation.TOP_BOTTOM,0x00112233,0xff112233);background.setBounds(0,0,320,180);
        Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);background.draw(new Canvas(bitmap));assertTrue(Color.alpha(bitmap.getPixel(160,0))<=1);assertTrue(Color.alpha(bitmap.getPixel(160,179))>=253);
        background.setAlpha(128);bitmap.eraseColor(Color.TRANSPARENT);background.draw(new Canvas(bitmap));assertTrue(Color.alpha(bitmap.getPixel(160,179))<=128);bitmap.recycle();
    }
}
