package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

/** Width-driven fixed-ratio frame; limited parent height must not squash artwork. */
final class ArtworkView extends ImageView {
    private double ratio=2.0/3;
    ArtworkView(Context context) {
        super(context); setScaleType(ScaleType.CENTER_CROP); setBackground(Ui.background(0xff191919,0)); setClipToOutline(true);
    }
    void crop(boolean crop) { setScaleType(crop?ScaleType.CENTER_CROP:ScaleType.FIT_CENTER); }
    void ratio(double value) { if(ratio!=value) { ratio=value; requestLayout(); } }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=resolveSize(View.MeasureSpec.getSize(widthSpec),widthSpec);
        setMeasuredDimension(width,(int)Math.round(width/ratio));
    }
}
