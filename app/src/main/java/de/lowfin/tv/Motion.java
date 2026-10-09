package de.lowfin.tv;

import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Short compositor-only transitions; no extra image loads or continuous render loop. */
final class Motion {
    static boolean enabled(View view) { android.content.SharedPreferences p=view.getContext().getSharedPreferences("settings",0); return p.getBoolean("animations",true)&&p.getBoolean("smoothNavigation",true); }
    static void focus(View view,boolean focused,boolean card) {
        float scale=enabled(view)&&focused?(card?1.035f:1.02f):1f;
        if(enabled(view)) view.animate().scaleX(scale).scaleY(scale).setInterpolator(new DecelerateInterpolator()).setDuration(180).start();
        else { view.setScaleX(1); view.setScaleY(1); }
    }
    static void enter(View view,boolean forward) {
        view.animate().withEndAction(null).cancel();
        if(!enabled(view)) { view.setAlpha(1); view.setTranslationX(0); return; }
        view.setAlpha(.72f); view.setTranslationX(Ui.dp(view.getContext(),forward?18:-18));
        view.animate().alpha(1).translationX(0).setInterpolator(new DecelerateInterpolator()).setDuration(230).start();
    }
    static void feedback(View view) {
        if(view==null||!enabled(view)||!view.isAttachedToWindow()) return;
        view.animate().withEndAction(null).cancel();
        view.animate().scaleX(1.065f).scaleY(1.065f).setDuration(100).withEndAction(()-> {
            if(view.isAttachedToWindow()) view.animate().scaleX(view.hasFocus()?1.035f:1).scaleY(view.hasFocus()?1.035f:1).setDuration(170).withEndAction(null).start();
        }).start();
    }
}
