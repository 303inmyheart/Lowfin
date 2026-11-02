package de.lowfin.tv;

import android.app.*;
import android.content.Context;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Native integer slider: exact single presses, bounded acceleration for sustained remote input. */
final class RangeChoice {
    static AlertDialog show(Activity activity,String title,int minimum,int maximum,int current,IntConsumer save) {
        LinearLayout content=Ui.column(activity);content.setPadding(Ui.dp(activity,8),0,Ui.dp(activity,8),0);
        TextView value=Ui.title(activity,""+Math.max(minimum,Math.min(maximum,current)),22);value.setGravity(Gravity.CENTER);content.addView(value);
        Slider slider=new Slider(activity);slider.setMin(minimum);slider.setMax(maximum);slider.setProgress(current);slider.setKeyProgressIncrement(1);slider.setPadding(Ui.dp(activity,14),Ui.dp(activity,10),Ui.dp(activity,14),Ui.dp(activity,10));slider.setContentDescription(title+": "+slider.getProgress());
        Accent.bind(slider,(view,color)->{SeekBar seek=(SeekBar)view;seek.setProgressTintList(android.content.res.ColorStateList.valueOf(color));seek.setThumbTintList(android.content.res.ColorStateList.valueOf(color));});content.addView(slider,new LinearLayout.LayoutParams(-1,Ui.dp(activity,44)));
        LinearLayout limits=Ui.row(activity);limits.setPadding(Ui.dp(activity,14),0,Ui.dp(activity,14),0);TextView low=Ui.text(activity,""+minimum,12,Ui.MUTED),high=Ui.text(activity,""+maximum,12,Ui.MUTED);high.setGravity(Gravity.END);limits.addView(low,new LinearLayout.LayoutParams(0,-2,1));limits.addView(high,new LinearLayout.LayoutParams(0,-2,1));content.addView(limits);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar seek,int progress,boolean fromUser){value.setText(""+progress);seek.setContentDescription(title+": "+progress);}public void onStartTrackingTouch(SeekBar seek){}public void onStopTrackingTouch(SeekBar seek){}});
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle(title).setView(content).setPositiveButton(I18n.text("Speichern"),(d,w)->save.accept(slider.getProgress())).setNegativeButton(I18n.text("Abbrechen"),null).create();dialog.setOnShowListener(ignored->slider.requestFocus());dialog.show();return dialog;
    }
    static final class Slider extends SeekBar {
        private int heldKey;private long started,lastStep;
        Slider(Context context){super(context);}
        private void reset(){heldKey=0;started=lastStep=0;}
        @Override public boolean onKeyDown(int key,KeyEvent event){
            if(!isEnabled()||key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return super.onKeyDown(key,event);
            long now=event.getEventTime();boolean fresh=event.getRepeatCount()==0||heldKey!=key;
            if(fresh){heldKey=key;started=now;lastStep=now;}else if(now-lastStep<120)return true;
            long elapsed=now-started;int step=elapsed<700?1:elapsed<1600?2:elapsed<3000?5:Math.min(10,Math.max(5,(getMax()-getMin())/10));lastStep=now;
            setProgress(Math.max(getMin(),Math.min(getMax(),getProgress()+(key==KeyEvent.KEYCODE_DPAD_RIGHT?step:-step))));return true;
        }
        @Override public boolean onKeyUp(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){reset();return true;}return super.onKeyUp(key,event);}
        @Override protected void onFocusChanged(boolean focused,int direction,Rect previous){reset();super.onFocusChanged(focused,direction,previous);}
        @Override protected void onDetachedFromWindow(){reset();super.onDetachedFromWindow();}
    }
}
