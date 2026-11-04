package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.widget.ScrollView;

/** Keep complete message content in a bounded, non-focusable reading viewport. */
final class NoticeViewport extends ScrollView {
    NoticeViewport(Context context) {
        super(context);setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setVerticalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);
    }
    @Override protected void onMeasure(int width,int height) {
        int maximum=Math.max(1,Math.min(Math.round(getResources().getDisplayMetrics().heightPixels*.42f),getResources().getDisplayMetrics().heightPixels-Ui.dp(getContext(),96)));
        if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)maximum=Math.min(maximum,MeasureSpec.getSize(height));
        super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(maximum,View.MeasureSpec.AT_MOST));
    }
}
