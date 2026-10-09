package de.lowfin.tv;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.*;
/** Fixed frame, proportional logo and readable text fallback. */
final class PlayerLogo extends ImageView {
    private final TextView fallback;
    PlayerLogo(Context context,TextView fallback) { super(context); this.fallback=fallback; setScaleType(ScaleType.FIT_START); setFocusable(false); setVisibility(GONE); }
    @Override public void setImageDrawable(Drawable image) { super.setImageDrawable(image); if(fallback!=null) { setVisibility(image==null?GONE:VISIBLE); fallback.setVisibility(image==null?VISIBLE:GONE); } }
}
