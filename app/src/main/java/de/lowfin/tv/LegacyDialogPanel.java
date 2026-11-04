package de.lowfin.tv;

import android.content.Context;
import android.view.*;

/** Explicit custom-dialog bounds for older TV frameworks; native widgets/actions keep their behavior. */
final class LegacyDialogPanel extends ViewGroup {
    private final View title,body,actions;
    private final int inset,gap;
    LegacyDialogPanel(Context context,View title,View body,View actions) {
        super(context);this.title=title;this.body=body;this.actions=actions;inset=Ui.dp(context,10);gap=Ui.dp(context,10);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);setDefaultFocusHighlightEnabled(false);
        for(View child:new View[]{title,body,actions})if(child!=null)addView(child,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec),screen=getResources().getDisplayMetrics().heightPixels;
        int cap=Math.max(1,Math.min(Math.round(screen*.70f)-Ui.dp(getContext(),12),screen-Ui.dp(getContext(),48)));
        if(MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(heightSpec));
        int inner=Math.max(0,width-inset*2),used=inset*2;
        for(View child:new View[]{title,actions})if(child!=null&&child.getVisibility()!=GONE) {child.measure(MeasureSpec.makeMeasureSpec(inner,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));used+=child.getMeasuredHeight()+gap;}
        body.measure(MeasureSpec.makeMeasureSpec(inner,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,cap-used),MeasureSpec.AT_MOST));
        setMeasuredDimension(width,Math.min(cap,Math.max(Math.min(cap,Ui.dp(getContext(),112)),used+body.getMeasuredHeight())));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int y=inset;
        if(title!=null&&title.getVisibility()!=GONE) {title.layout(inset,y,getWidth()-inset,y+title.getMeasuredHeight());y+=title.getMeasuredHeight()+gap;}
        body.layout(inset,y,getWidth()-inset,y+body.getMeasuredHeight());y+=body.getMeasuredHeight()+gap;
        if(actions!=null&&actions.getVisibility()!=GONE)actions.layout(inset,y,getWidth()-inset,y+actions.getMeasuredHeight());
    }
}
