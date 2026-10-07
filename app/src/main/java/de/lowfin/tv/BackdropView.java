package de.lowfin.tv;

import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;

/** Retain subdued, richer original colors; dark gradients are drawn separately over the artwork. */
final class BackdropView extends ImageView {
    static final float SATURATION=.65f;
    BackdropView(Context context) {
        super(context); setScaleType(ScaleType.CENTER_CROP); ColorMatrix matrix=new ColorMatrix(); matrix.setSaturation(SATURATION); setColorFilter(new ColorMatrixColorFilter(matrix));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); setFocusable(false);
    }
}
