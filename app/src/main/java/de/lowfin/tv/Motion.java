package de.lowfin.tv;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Short compositor-only transitions; no extra image loads or continuous render loop. */
final class Motion {
    private static final DecelerateInterpolator EASE_OUT=new DecelerateInterpolator();
    private static final View.OnLayoutChangeListener CENTER_PIVOT=(view,l,t,r,b,ol,ot,or,ob)-> {view.setPivotX((r-l)/2f);view.setPivotY((b-t)/2f);};
    static boolean enabled(View view) { android.content.SharedPreferences p=view.getContext().getSharedPreferences("settings",0); return p.getBoolean("animations",true)&&p.getBoolean("smoothNavigation",true); }
    static void focus(View view,boolean focused,boolean card) {
        boolean animate=enabled(view);
        float scale=animate&&focused?boundedScale(view,card?1.035f:1.02f):1f;
        if(animate) view.animate().scaleX(scale).scaleY(scale).setInterpolator(EASE_OUT).setDuration(180).start();
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
        view.animate().alpha(1).translationX(0).setInterpolator(EASE_OUT).setDuration(230).start();
    }
    /** Animate only the information panel, without changing layout or focus geometry. */
    static void enterFromBottom(View view) {
        view.animate().withEndAction(null).cancel();
        view.setTranslationX(0);
        if(!enabled(view)) { view.setAlpha(1); view.setTranslationY(0); return; }
        view.setAlpha(.72f); view.setTranslationY(Ui.dp(view.getContext(),18));
        view.animate().alpha(1).translationY(0).setInterpolator(EASE_OUT).setDuration(230).withLayer().start();
    }
    /** Symmetric cover reveal; the center pivot also follows later layout changes. */
    static void revealFromCenter(View view) {
        view.animate().withEndAction(null).cancel();
        if(view.getTag(R.id.center_reveal_pivot)!=Boolean.TRUE) {view.setTag(R.id.center_reveal_pivot,Boolean.TRUE);view.addOnLayoutChangeListener(CENTER_PIVOT);}
        view.setPivotX(view.getWidth()/2f);view.setPivotY(view.getHeight()/2f);
        view.setTranslationX(0);view.setTranslationY(0);
        if(!enabled(view)||!view.isAttachedToWindow()) {view.setAlpha(1);view.setScaleX(1);view.setScaleY(1);return;}
        view.setAlpha(.4f);view.setScaleX(.94f);view.setScaleY(.94f);
        view.animate().alpha(1).scaleX(1).scaleY(1).setInterpolator(EASE_OUT).setDuration(350).start();
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
