package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Consistent gaps for settings controls and their helper text, including dynamically added rows. */
final class SettingsColumn extends LinearLayout {
    SettingsColumn(Context context) { super(context);setOrientation(VERTICAL);int inset=Ui.dp(context,6);setPadding(inset,inset,inset,inset);setClipChildren(false);setClipToPadding(false); }
    @Override public void addView(View child,int index,ViewGroup.LayoutParams params) {
        // Old pages supplied hand-written spacer Views, sometimes twice and sometimes not at all.
        if(child.getClass()==View.class) return;
        LinearLayout.LayoutParams size=params instanceof LinearLayout.LayoutParams?new LinearLayout.LayoutParams((LinearLayout.LayoutParams)params):new LinearLayout.LayoutParams(params);
        boolean heading=child instanceof TextView&&!(child instanceof Button)&&((TextView)child).getTextSize()>=16*getResources().getDisplayMetrics().scaledDensity;
        int gap=getChildCount()==0?0:heading?16:child.isFocusable()?8:4;
        size.topMargin=Ui.dp(getContext(),gap);super.addView(child,index,size);
    }
}
