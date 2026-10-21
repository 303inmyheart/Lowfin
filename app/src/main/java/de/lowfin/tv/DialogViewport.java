package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Wrap short dialogs; bound long native list/message/custom scroll containers to the TV viewport. */
final class DialogViewport extends FrameLayout {
    DialogViewport(Context context) { super(context); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int screen=getResources().getDisplayMetrics().heightPixels;
        int maximum=Math.max(1,Math.min(Math.round(screen*.70f)-Ui.dp(getContext(),12),screen-Ui.dp(getContext(),48)));
        int available=View.MeasureSpec.getMode(heightSpec)==View.MeasureSpec.UNSPECIFIED?maximum:Math.min(maximum,View.MeasureSpec.getSize(heightSpec));
        super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.AT_MOST));
        int minimum=Math.min(available,Ui.dp(getContext(),112));
        if(getMeasuredHeight()<minimum) setMeasuredDimension(getMeasuredWidth(),minimum);
    }
}
