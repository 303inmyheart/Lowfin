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
    @Test public void allPatternsKeepTheirColorsAndTransparencyAtFourK() {
        for(int variant=0;variant<8;variant++) {
            SmoothGradient gradient=AccentBackground.drawable(Accent.POOL[0],variant);gradient.setBounds(0,0,160,90);
            Bitmap small=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);gradient.draw(new Canvas(small));gradient.setBounds(0,0,3840,2160);
            Bitmap large=Bitmap.createBitmap(3840,2160,Bitmap.Config.ARGB_8888);gradient.draw(new Canvas(large));
            for(int y=9;y<90;y+=18) for(int x=16;x<160;x+=32) {
                int a=averageColor(small,x,y),b=averageColor(large,x*24+12,y*24+12);assertEquals(255,Color.alpha(b));
                assertTrue(Math.abs(Color.red(a)-Color.red(b))<=2);assertTrue(Math.abs(Color.green(a)-Color.green(b))<=2);assertTrue(Math.abs(Color.blue(a)-Color.blue(b))<=2);
            }
            small.recycle();large.recycle();
        }
    }
    private static int averageColor(Bitmap image,int x,int y) {
        int r=0,g=0,b=0;for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){int c=image.getPixel(x+dx,y+dy);r+=Color.red(c);g+=Color.green(c);b+=Color.blue(c);assertEquals(255,Color.alpha(c));}
        return Color.rgb(r/25,g/25,b/25);
    }
    @Test public void ambientFastPathMatchesTheOriginalComposedShaderAndKeepsFades() {
        for(int variant=0;variant<8;variant++) {
            SmoothGradient gradient=AccentBackground.drawable(Accent.POOL[0],variant);gradient.setBounds(0,0,320,180);
            for(int alpha:new int[]{255,128}) {
                gradient.setAlpha(alpha);Bitmap actual=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);gradient.draw(new Canvas(actual));
                Shader original=org.robolectric.util.ReflectionHelpers.getField(gradient,"combinedShader");Paint referencePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);referencePaint.setShader(original);referencePaint.setAlpha(alpha);
                Bitmap expected=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);new Canvas(expected).drawRect(0,0,320,180,referencePaint);
                for(int y=0;y<180;y+=3) for(int x=0;x<320;x+=3) {int a=actual.getPixel(x,y),b=expected.getPixel(x,y);assertEquals(Color.alpha(b),Color.alpha(a));assertTrue(Math.abs(Color.red(a)-Color.red(b))<=1);assertTrue(Math.abs(Color.green(a)-Color.green(b))<=1);assertTrue(Math.abs(Color.blue(a)-Color.blue(b))<=1);}
                actual.recycle();expected.recycle();
            }
        }
    }
    @Test public void DitheringPreservesTransparencyAndDrawableOpacity() {
        SmoothGradient background=SmoothGradient.linear(GradientDrawable.Orientation.TOP_BOTTOM,0x00112233,0xff112233);background.setBounds(0,0,320,180);
        Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);background.draw(new Canvas(bitmap));assertTrue(Color.alpha(bitmap.getPixel(160,0))<=1);assertTrue(Color.alpha(bitmap.getPixel(160,179))>=253);
        background.setAlpha(128);bitmap.eraseColor(Color.TRANSPARENT);background.draw(new Canvas(bitmap));assertTrue(Color.alpha(bitmap.getPixel(160,179))<=128);bitmap.recycle();
    }
}
