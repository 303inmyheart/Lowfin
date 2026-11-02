package de.lowfin.tv;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Consistent gaps for settings controls and their helper text, including dynamically added rows. */
final class SettingsColumn extends LinearLayout {
    private final String title;
    SettingsColumn(Context context) {this(context,"");}
    SettingsColumn(Context context,String title) { super(context);this.title=title;setOrientation(VERTICAL);int inset=Ui.dp(context,6);setPadding(inset,inset,inset,inset);setClipChildren(false);setClipToPadding(false); }
    @Override public void addView(View child,int index,ViewGroup.LayoutParams params) {
        // Old pages supplied hand-written spacer Views, sometimes twice and sometimes not at all.
        if(child.getClass()==View.class) return;
        LinearLayout.LayoutParams size=params instanceof LinearLayout.LayoutParams?new LinearLayout.LayoutParams((LinearLayout.LayoutParams)params):new LinearLayout.LayoutParams(params);
        boolean heading=child instanceof TextView&&!(child instanceof Button)&&((TextView)child).getTextSize()>=16*getResources().getDisplayMetrics().scaledDensity;
        if(heading&&getChildCount()==0&&title.contentEquals(((TextView)child).getText()))return;
        if(heading) {TextView text=(TextView)child;text.setTextSize(13);text.setLetterSpacing(.06f);text.setPadding(Ui.dp(getContext(),14),Ui.dp(getContext(),8),Ui.dp(getContext(),14),Ui.dp(getContext(),4));Accent.bind(text,(view,color)->((TextView)view).setTextColor(color));}
        else if(child instanceof Button)SettingsRow.style((Button)child);
        else if(child instanceof TextView)child.setPadding(Ui.dp(getContext(),14),Ui.dp(getContext(),2),Ui.dp(getContext(),14),Ui.dp(getContext(),4));
        int gap=getChildCount()==0?0:heading?18:child.isFocusable()||child instanceof ViewGroup?4:2;
        size.topMargin=Ui.dp(getContext(),gap);super.addView(child,index,size);
    }
}
