package de.lowfin.tv;

import android.animation.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.function.BooleanSupplier;

/** One bounded startup overlay; data loading proceeds normally behind it. */
final class StartupOverlay extends FrameLayout {
    static final long MAX_WAIT_MS=15000;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BooleanSupplier ready;
    private final View content;
    private final long began=SystemClock.uptimeMillis();
    private final Rect bounds=new Rect();
    private ObjectAnimator pulse;
    private boolean finished;
    private final Runnable poll=()->check();
    StartupOverlay(android.content.Context context,View content,BooleanSupplier ready) {
        super(context);this.content=content;this.ready=ready;setClickable(true);setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);Accent.background(this);
        ImageView logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImageResource(R.drawable.launcher_foreground);addView(logo,new LayoutParams(Ui.dp(context,240),Ui.dp(context,160),Gravity.CENTER));
        setOnClickListener(v->dismiss());
        if(Motion.enabled(this)) {pulse=ObjectAnimator.ofPropertyValuesHolder(logo,PropertyValuesHolder.ofFloat("alpha",.55f,1f),PropertyValuesHolder.ofFloat("scaleX",.97f,1f),PropertyValuesHolder.ofFloat("scaleY",.97f,1f));pulse.setDuration(1100);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.setRepeatMode(ValueAnimator.REVERSE);pulse.start();}
    }
    boolean showing() {return !finished;}
    private void check() {
        if(finished||!isAttachedToWindow()) return;
        long elapsed=SystemClock.uptimeMillis()-began;
        if(elapsed>=MAX_WAIT_MS||elapsed>=400&&ready.getAsBoolean()&&!pendingImages(content)) {dismiss();return;}
        main.postDelayed(poll,100);
    }
    private boolean pendingImages(View view) {
        if(view==this||view.getVisibility()!=VISIBLE||!view.getGlobalVisibleRect(bounds)) return false;
        if(view instanceof ImageView&&view.getTag() instanceof String&&((ImageView)view).getDrawable()==null) return true;
        if(view instanceof ViewGroup) {ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++) if(pendingImages(group.getChildAt(i))) return true;}
        return false;
    }
    void dismiss() {
        if(finished) return;finished=true;main.removeCallbacks(poll);if(pulse!=null){pulse.cancel();pulse=null;}
        Runnable remove=()->{if(getParent() instanceof ViewGroup) ((ViewGroup)getParent()).removeView(this);};
        if(Motion.enabled(this)&&isAttachedToWindow()) animate().alpha(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).withEndAction(remove).start();else remove.run();
    }
    @Override protected void onAttachedToWindow() {super.onAttachedToWindow();main.post(poll);}
    @Override protected void onDetachedFromWindow() {main.removeCallbacks(poll);if(pulse!=null){pulse.cancel();pulse=null;}animate().cancel();super.onDetachedFromWindow();}
}
