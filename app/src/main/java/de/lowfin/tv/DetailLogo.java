package de.lowfin.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

/** A fixed logo frame: contain the entire image and retain the text fallback until it loads. */
final class DetailLogo extends ImageView {
    static final int WIDTH_DP=228,HEIGHT_DP=80;
    private final TextView fallback;
    DetailLogo(Context context,TextView fallback) {
        super(context); this.fallback=fallback;
        setScaleType(ScaleType.FIT_CENTER); setAdjustViewBounds(false); setFocusable(false); setVisibility(GONE);
    }
    @Override public void setImageDrawable(Drawable drawable) { super.setImageDrawable(drawable); syncFallback(drawable!=null); }
    @Override public void setImageBitmap(Bitmap bitmap) { super.setImageBitmap(bitmap); syncFallback(bitmap!=null); }
    private void syncFallback(boolean ready) { if(fallback!=null) { fallback.setVisibility(ready?GONE:VISIBLE); setVisibility(ready?VISIBLE:GONE); } }
}
