package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

/** Width-driven fixed-ratio frame; limited parent height must not squash artwork. */
final class ArtworkView extends ImageView {
    private final CoverTags coverTags=new CoverTags();
    String badgeItem="";
    private int badgeMask;
    private double ratio=2.0/3;
    private final android.graphics.drawable.GradientDrawable surface=Ui.background(0xff191919,0);
    private boolean transparent;
    ArtworkView(Context context) {
        super(context); setScaleType(ScaleType.CENTER_CROP); setBackground(surface); setClipToOutline(true);
    }
    void transparent(boolean value) { if(transparent!=value) { transparent=value;surface.setColor(value?android.graphics.Color.TRANSPARENT:0xff191919); } }
    void crop(boolean crop) { setScaleType(crop?ScaleType.CENTER_CROP:ScaleType.FIT_CENTER); }
    void ratio(double value) { if(ratio!=value) { ratio=value; requestLayout(); } }
    void badges(String item,int mask) { badgeItem=item;if(badgeMask!=mask) { badgeMask=mask;invalidate(); } }
    @Override protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        if(badgeMask!=0&&getDrawable()!=null) { coverTags.layout(getWidth(),getHeight(),badgeMask,getResources().getDisplayMetrics().density);coverTags.draw(canvas); }
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=resolveSize(View.MeasureSpec.getSize(widthSpec),widthSpec);
        setMeasuredDimension(width,(int)Math.round(width/ratio));
    }
}
