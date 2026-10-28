package de.lowfin.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/** Static GPU gradients with subtle spatial dithering, without animation or full-screen bitmaps. */
final class SmoothGradient extends Drawable {
    private static final Bitmap NOISE=noise();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Paint grainPaint=new Paint();
    private Shader baseShader,combinedShader;
    private final GradientDrawable.Orientation direction;
    private final int[] colors;
    private final int accent,variant;
    private int alpha=255;
    private SmoothGradient(GradientDrawable.Orientation direction,int[] colors,int accent,int variant) { this.direction=direction; this.colors=colors; this.accent=accent; this.variant=variant; }
    static SmoothGradient linear(GradientDrawable.Orientation direction,int... colors) { return new SmoothGradient(direction,colors.clone(),0,-1); }
    static SmoothGradient ambient(int accent,int variant) { return new SmoothGradient(null,null,accent,Math.floorMod(variant,8)); }
    private static Bitmap noise() {
        Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888); int[] pixels=new int[4096]; java.util.Random random=new java.util.Random(0x10f1L);
        for(int i=0;i<pixels.length;i++) { int v=random.nextInt(256); pixels[i]=Color.argb(3,v,v,v); }
        bitmap.setPixels(pixels,0,64,0,0,64,64); return bitmap;
    }
    private static int tint(int accent,float amount) { return Color.rgb(Math.round(Color.red(Ui.BG)+(Color.red(accent)-Color.red(Ui.BG))*amount),Math.round(Color.green(Ui.BG)+(Color.green(accent)-Color.green(Ui.BG))*amount),Math.round(Color.blue(Ui.BG)+(Color.blue(accent)-Color.blue(Ui.BG))*amount)); }
    @Override protected void onBoundsChange(Rect bounds) {
        float w=Math.max(1,bounds.width()),h=Math.max(1,bounds.height()); Shader shader;
        if(variant>=0) {
            // Eight new soft pools of light, replacing the old three-band directional patterns.
            float[][] centers={{.12f,.12f},{.88f,.18f},{.16f,.86f},{.86f,.88f},{.5f,-.18f},{.5f,1.18f},{-.18f,.5f},{1.18f,.5f}};
            float[] center=centers[variant];
            Shader base=new LinearGradient(0,0,w,h,tint(accent,.025f),tint(accent,.05f),Shader.TileMode.CLAMP);
            int rgb=accent&0x00ffffff;
            Shader pool=new RadialGradient(center[0]*w,center[1]*h,Math.max(w,h)*.9f,new int[]{0x20000000|rgb,0x1b000000|rgb,0x0e000000|rgb,rgb},new float[]{0,.28f,.65f,1},Shader.TileMode.CLAMP);
            shader=new ComposeShader(base,pool,PorterDuff.Mode.SRC_OVER);
        } else {
            float x0=0,y0=0,x1=w,y1=h;
            switch(direction) {
                case TR_BL:x0=w;x1=0;break; case BL_TR:y0=h;y1=0;break;case BR_TL:x0=w;y0=h;x1=0;y1=0;break;
                case LEFT_RIGHT:y1=0;break;case RIGHT_LEFT:x0=w;x1=0;y1=0;break;case TOP_BOTTOM:x1=0;break;case BOTTOM_TOP:y0=h;x1=0;y1=0;break; default:break;
            }
            shader=new LinearGradient(x0,y0,x1,y1,colors,null,Shader.TileMode.CLAMP);
        }
        Shader grain=new BitmapShader(NOISE,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);
        baseShader=shader;combinedShader=new ComposeShader(shader,grain,PorterDuff.Mode.SRC_ATOP);
        grainPaint.setShader(grain);
        paint.setShader(combinedShader);
    }
    @Override public void draw(Canvas canvas) {
        if(baseShader==null) onBoundsChange(getBounds());
        // Ambient backgrounds are opaque: grain SRC_ATOP is then equivalent to
        // a separate SRC_OVER pass, without a third nested shader stage. Keep
        // the original combined path for alpha fades and color filters.
        boolean opaqueAmbient=variant>=0&&alpha==255&&paint.getColorFilter()==null;
        paint.setShader(opaqueAmbient?baseShader:combinedShader);
        canvas.save();canvas.translate(getBounds().left,getBounds().top);
        canvas.drawRect(0,0,getBounds().width(),getBounds().height(),paint);
        if(opaqueAmbient) canvas.drawRect(0,0,getBounds().width(),getBounds().height(),grainPaint);
        canvas.restore();
    }
    @Override public void setAlpha(int value) { alpha=value; paint.setAlpha(value); invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
