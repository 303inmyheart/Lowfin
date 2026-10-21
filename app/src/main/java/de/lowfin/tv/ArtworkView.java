package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

/** Width-driven fixed-ratio frame; limited parent height must not squash artwork. */
final class ArtworkView extends ImageView {
    private final CoverTags coverTags=new CoverTags();
    String badgeItem="";
    private int badgeMask;
    private String[] badgeLabels=CoverTagLabels.EMPTY;
    private double ratio=2.0/3;
    private final android.graphics.drawable.GradientDrawable surface=Ui.background(0xff191919,0);
    private boolean transparent;
    ArtworkView(Context context) {
        super(context); setScaleType(ScaleType.CENTER_CROP); setBackground(surface); setClipToOutline(true);
    }
    void transparent(boolean value) { if(transparent!=value) { transparent=value;surface.setColor(value?android.graphics.Color.TRANSPARENT:0xff191919); } }
    void crop(boolean crop) { setScaleType(crop?ScaleType.CENTER_CROP:ScaleType.FIT_CENTER); }
    void ratio(double value) { if(ratio!=value) { ratio=value; requestLayout(); } }
    void badges(String item,int mask) {badges(item,mask,CoverTagLabels.EMPTY);}
    void badges(String item,int mask,String[] labels) {badgeItem=item;if(badgeMask!=mask||!java.util.Arrays.equals(badgeLabels,labels)) {badgeMask=mask;badgeLabels=labels;invalidate();}}
    @Override protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        if((badgeMask!=0||badgeLabels.length>0)&&getDrawable()!=null) { coverTags.layout(getWidth(),getHeight(),badgeMask,getResources().getDisplayMetrics().density,badgeLabels);coverTags.draw(canvas); }
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=resolveSize(View.MeasureSpec.getSize(widthSpec),widthSpec);
        setMeasuredDimension(width,(int)Math.round(width/ratio));
    }
}
