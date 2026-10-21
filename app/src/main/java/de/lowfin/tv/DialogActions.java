package de.lowfin.tv;

import android.content.Context;
import android.view.*;
import android.widget.LinearLayout;

/** Short actions share a row; long translated labels receive full-width rows. */
final class DialogActions extends LinearLayout {
    private int actionHeight;
    @Override public int getMinimumHeight() { return actionHeight; }
    DialogActions(Context context) { super(context); setGravity(Gravity.CENTER); int inset=Ui.dp(context,8); setPadding(inset,inset,inset,inset); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight(),gap=Ui.dp(getContext(),8),desired=0;
        for(int i=0;i<getChildCount();i++) { View child=getChildAt(i); child.measure(MeasureSpec.makeMeasureSpec(Math.max(0,available),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED)); desired+=child.getMeasuredWidth()+(i==0?0:gap); }
        boolean stacked=desired>available;
        if(getOrientation()!=(stacked?VERTICAL:HORIZONTAL)) setOrientation(stacked?VERTICAL:HORIZONTAL);
        for(int i=0;i<getChildCount();i++) { LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)getChildAt(i).getLayoutParams(); size.width=stacked?LayoutParams.MATCH_PARENT:LayoutParams.WRAP_CONTENT; size.height=LayoutParams.WRAP_CONTENT; size.weight=0; size.leftMargin=!stacked&&i>0?gap:0; size.topMargin=stacked&&i>0?gap:0; }
        super.onMeasure(widthSpec,heightSpec);
        actionHeight=getMeasuredHeight();
    }
}
