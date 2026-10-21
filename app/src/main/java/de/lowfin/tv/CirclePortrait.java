package de.lowfin.tv;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;

/** Hardware outline clipping, with the same circular mask for software rendering. */
final class CirclePortrait extends ImageView {
    private final Path mask=new Path();
    CirclePortrait(Context context) {
        super(context); setScaleType(ScaleType.CENTER_CROP); setBackgroundColor(0xff242428);
        setOutlineProvider(new ViewOutlineProvider() { @Override public void getOutline(View view,Outline outline) { outline.setOval(0,0,view.getWidth(),view.getHeight()); } }); setClipToOutline(true);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) { super.onSizeChanged(w,h,oldW,oldH); mask.reset(); mask.addOval(0,0,w,h,Path.Direction.CW); }
    @Override public void draw(Canvas canvas) { if(canvas.isHardwareAccelerated()) { super.draw(canvas); return; } int saved=canvas.save(); canvas.clipPath(mask); super.draw(canvas); canvas.restoreToCount(saved); }
}
