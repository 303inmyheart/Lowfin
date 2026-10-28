package de.lowfin.tv;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;

/** Optional, slow border-only pulse. No animator, bitmap, layer or layout changes. */
final class FocusBackground extends GradientDrawable implements Runnable {
    private static final long FRAME_MS=50,PERIOD_MS=2400;
    private final Rect visible=new Rect();
    private WeakReference<View> focusOwner;
    private int width,color,appliedWidth=-1,appliedColor;
    private boolean running;
    void owner(View view) {focusOwner=new WeakReference<>(view);}
    private View host() {
        Drawable.Callback callback=getCallback();
        if(callback==null) return null;
        if(focusOwner!=null) return focusOwner.get();
        for(int i=0;i<8&&callback instanceof Drawable;i++) callback=((Drawable)callback).getCallback();
        return callback instanceof View?(View)callback:null;
    }
    private boolean enabled() {
        if(width<=0||Color.alpha(color)==0||!isVisible()||getAlpha()==0) return false;
        View view=host();
        if(view==null||!view.isFocused()||!view.isAttachedToWindow()||!view.hasWindowFocus()||!view.isShown()||!view.getLocalVisibleRect(visible)) return false;
        SharedPreferences settings=view.getContext().getSharedPreferences("settings",0);
        return settings.getBoolean("focusPulse",false)&&settings.getBoolean("animations",true);
    }
    @Override public void setStroke(int width,int color) {
        this.width=width;this.color=color;
        if(running&&enabled()) pulse();else stop();
    }
    private void apply(int value) {
        if(appliedWidth==width&&appliedColor==value) return;
        appliedWidth=width;appliedColor=value;super.setStroke(width,value);
    }
    private void pulse() {
        double phase=(SystemClock.uptimeMillis()%PERIOD_MS)*(2*Math.PI/PERIOD_MS);
        float light=.72f+.28f*(float)((1-Math.cos(phase))*.5);
        int accent=Accent.color;
        apply(Color.argb(Color.alpha(color),Math.round(Color.red(accent)*light),Math.round(Color.green(accent)*light),Math.round(Color.blue(accent)*light)));
    }
    private void stop() {running=false;unscheduleSelf(this);apply(color);}
    @Override public boolean isStateful() {return width>0||super.isStateful();}
    @Override protected boolean onStateChange(int[] state) {
        boolean changed=super.onStateChange(state),animate=enabled();
        if(running&&!animate) {stop();return true;}
        // Window-focus state changes must restart a stopped pulse after a dialog closes.
        if(animate) {invalidateSelf();return true;}
        return changed;
    }
    @Override public void draw(Canvas canvas) {
        if(enabled()) {
            if(!running) {running=true;pulse();scheduleSelf(this,SystemClock.uptimeMillis()+FRAME_MS);}
        } else if(running) stop();
        super.draw(canvas);
    }
    @Override public void run() {
        if(!enabled()) {stop();return;}
        pulse();scheduleSelf(this,SystemClock.uptimeMillis()+FRAME_MS);
    }
    @Override public boolean setVisible(boolean visible,boolean restart) {
        if(!visible) stop();
        return super.setVisible(visible,restart);
    }
}
