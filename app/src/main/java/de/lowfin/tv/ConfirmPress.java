package de.lowfin.tv;

import android.view.*;

/** Own the whole confirm gesture: release after a hold must never launch playback. */
final class ConfirmPress implements View.OnKeyListener, View.OnAttachStateChangeListener {
    private final View view;
    private View.OnKeyListener navigation;
    private Runnable hold;
    private boolean down,held;
    private final Runnable timeout=()->trigger();
    private ConfirmPress(View view) { this.view=view; view.addOnAttachStateChangeListener(this); }
    static void bind(View view,View.OnKeyListener navigation,Runnable hold) {
        ConfirmPress press=(ConfirmPress)view.getTag(R.id.confirm_press);
        if(press==null) { press=new ConfirmPress(view); view.setTag(R.id.confirm_press,press); }
        press.cancel(); press.navigation=navigation; press.hold=hold; view.setOnKeyListener(press);
        view.setOnLongClickListener(v->{ hold.run(); return true; });
    }
    static void cancel(View view) { ConfirmPress press=(ConfirmPress)view.getTag(R.id.confirm_press); if(press!=null) press.cancel(); }
    private void cancel() { view.removeCallbacks(timeout); down=false; held=false; }
    private void trigger() {
        if(!down||held||!view.isAttachedToWindow()||!view.hasFocus()) return;
        held=true; view.removeCallbacks(timeout); hold.run();
    }
    @Override public boolean onKey(View v,int key,KeyEvent event) {
        if(key!=KeyEvent.KEYCODE_DPAD_CENTER&&key!=KeyEvent.KEYCODE_ENTER&&key!=KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) cancel();
            return navigation!=null&&navigation.onKey(v,key,event);
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN) {
            if(event.getRepeatCount()==0) { cancel(); down=true; held=false; view.postDelayed(timeout,ViewConfiguration.getLongPressTimeout()); }
            if(event.isLongPress()||down&&event.getEventTime()-event.getDownTime()>=ViewConfiguration.getLongPressTimeout()) trigger();
        } else if(event.getAction()==KeyEvent.ACTION_UP) {
            boolean click=down&&!held&&!event.isCanceled()&&view.hasFocus(); cancel(); if(click) view.performClick();
        }
        return true;
    }
    @Override public void onViewDetachedFromWindow(View v) { cancel(); }
    @Override public void onViewAttachedToWindow(View v) { }
}
