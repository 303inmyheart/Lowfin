package de.lowfin.tv;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Short compositor-only transitions; no extra image loads or continuous render loop. */
final class Motion {
    static boolean enabled(View view) { android.content.SharedPreferences p=view.getContext().getSharedPreferences("settings",0); return p.getBoolean("animations",true)&&p.getBoolean("smoothNavigation",true); }
    static void focus(View view,boolean focused,boolean card) {
        float scale=enabled(view)&&focused?boundedScale(view,card?1.035f:1.02f):1f;
        if(enabled(view)) view.animate().scaleX(scale).scaleY(scale).setInterpolator(new DecelerateInterpolator()).setDuration(180).start();
        else { view.setScaleX(1); view.setScaleY(1); }
    }
    /** Keep the animated outline inside every ancestor that clips its children/padding. */
    static float boundedScale(View view,float wanted) {
        int width=view.getWidth(),height=view.getHeight();if(width<=0||height<=0) return 1f;
        float scale=wanted,x=0,y=0;View child=view;
        while(child.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent=(android.view.ViewGroup)child.getParent();x+=child.getLeft()-parent.getScrollX();y+=child.getTop()-parent.getScrollY();
            if(parent.getWidth()>0&&parent.getHeight()>0&&(parent.getClipChildren()||parent.getClipToPadding())) {
                float left=parent.getClipToPadding()?parent.getPaddingLeft():0,top=parent.getClipToPadding()?parent.getPaddingTop():0;
                float right=parent.getWidth()-(parent.getClipToPadding()?parent.getPaddingRight():0),bottom=parent.getHeight()-(parent.getClipToPadding()?parent.getPaddingBottom():0);
                scale=Math.min(scale,2*Math.min(x+width/2f-left,right-x-width/2f)/width);
                scale=Math.min(scale,2*Math.min(y+height/2f-top,bottom-y-height/2f)/height);
            }
            child=parent;
        }
        return Math.max(1f,scale);
    }
    static void enter(View view,boolean forward) {
        view.animate().withEndAction(null).cancel();
        if(!enabled(view)) { view.setAlpha(1); view.setTranslationX(0); return; }
        view.setAlpha(.72f); view.setTranslationX(Ui.dp(view.getContext(),forward?18:-18));
        view.animate().alpha(1).translationX(0).setInterpolator(new DecelerateInterpolator()).setDuration(230).start();
    }
    /** Animate only the information panel, without changing layout or focus geometry. */
    static void enterFromBottom(View view) {
        view.animate().withEndAction(null).cancel();
        view.setTranslationX(0);
        if(!enabled(view)) { view.setAlpha(1); view.setTranslationY(0); return; }
        view.setAlpha(.72f); view.setTranslationY(Ui.dp(view.getContext(),18));
        view.animate().alpha(1).translationY(0).setInterpolator(new DecelerateInterpolator()).setDuration(230).withLayer().start();
    }
    static void feedback(View view) {
        if(view==null||!enabled(view)||!view.isAttachedToWindow()) return;
        view.animate().withEndAction(null).cancel();
        float feedback=boundedScale(view,1.065f);
        view.animate().scaleX(feedback).scaleY(feedback).setDuration(100).withEndAction(()-> {
            if(view.isAttachedToWindow()) view.animate().scaleX(view.hasFocus()?boundedScale(view,1.035f):1).scaleY(view.hasFocus()?boundedScale(view,1.035f):1).setDuration(170).withEndAction(null).start();
        }).start();
    }
}
