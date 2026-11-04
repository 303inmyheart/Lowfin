package de.lowfin.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.ContextWrapper;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import android.view.*;
import android.widget.*;

/** A passive native reading window; OK dismisses it without forwarding the press. */
final class ServerNotice implements AutoCloseable {
    private final Activity activity;
    private final Handler main;
    private final Runnable soundStarted;
    private PopupWindow popup;
    private NoticeSound sound;
    private ObjectAnimator readingScroll;
    private Runnable readingPage,readingSetup;
    private int serial;
    private int consumedKey=KeyEvent.KEYCODE_UNKNOWN;
    private static final WeakHashMap<Activity,WeakReference<ServerNotice>> active=new WeakHashMap<>();
    private final Runnable hide=this::close;
    ServerNotice(Activity activity,Handler main) {this(activity,main,()->{});}
    ServerNotice(Activity activity,Handler main,Runnable soundStarted) {this.activity=activity;this.main=main;this.soundStarted=soundStarted;}
    static long duration(String header,String message,long requested) {
        String text=(header==null?"":header.trim())+" "+(message==null?"":message.trim());
        int words=0;boolean word=false;
        for(int i=0;i<text.length();) {int cp=text.codePointAt(i);boolean next=!Character.isWhitespace(cp);if(next&&!word)words++;word=next;i+=Character.charCount(cp);}
        long reading=1800+Math.max(words*300L,text.codePointCount(0,text.length())*55L);
        return Math.min(60000,Math.max(Math.max(4000,reading),Math.max(0,requested)));
    }
    void show(String header,String message,long timeoutMs) {
        if(activity.isFinishing()||activity.isDestroyed()||message==null||message.trim().isEmpty())return;
        View anchor=activity.getWindow().getDecorView();if(!anchor.isAttachedToWindow())return;
        close();final int id=++serial;
        active.put(activity,new WeakReference<>(this));
        View focused=activity.getCurrentFocus();if(focused!=null) {ConfirmPress.cancel(focused);focused.setPressed(false);}
        long displayTime=duration(header,message,timeoutMs);
        LinearLayout panel=Ui.column(activity);int pad=Ui.dp(activity,16);panel.setPadding(pad,Ui.dp(activity,12),pad,Ui.dp(activity,12));
        panel.setBackground(Ui.background(0xf5222226,Accent.color));
        NoticeViewport viewport=new NoticeViewport(activity);LinearLayout content=Ui.column(activity);viewport.addView(content);panel.addView(viewport);
        if(header!=null&&!header.trim().isEmpty()) {
            TextView title=Ui.text(activity,header.trim(),12,Accent.color);content.addView(title);Ui.space(content,5);
        }
        TextView text=Ui.text(activity,message.trim(),14,Color.WHITE);content.addView(text);
        long shownAt=android.os.SystemClock.uptimeMillis();
        Runnable setup=()-> {
            if(serial!=id||popup==null)return;long elapsed=android.os.SystemClock.uptimeMillis()-shownAt;
            read(viewport,content,Math.max(1,displayTime-elapsed),Math.max(0,1200-elapsed),id);
        };readingSetup=setup;
        View.OnLayoutChangeListener reflow=(v,l,t,r,b,ol,ot,or,ob)-> {
            if(serial==id&&b>t) {main.removeCallbacks(setup);main.post(setup);}
        };viewport.addOnLayoutChangeListener(reflow);content.addOnLayoutChangeListener(reflow);
        int width=Math.min(Ui.dp(activity,360),activity.getResources().getDisplayMetrics().widthPixels-Ui.dp(activity,48));
        popup=new PopupWindow(panel,width,ViewGroup.LayoutParams.WRAP_CONTENT,false);
        popup.setTouchable(false);popup.setOutsideTouchable(false);popup.setFocusable(false);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);popup.setElevation(Ui.dp(activity,6));
        popup.showAtLocation(anchor,Gravity.TOP|Gravity.END,Ui.dp(activity,24),Ui.dp(activity,24));
        playSound();
        if(activity.getSharedPreferences("settings",0).getBoolean("animations",true)) {panel.setAlpha(0);panel.animate().alpha(1).setDuration(160).start();}
        main.postDelayed(hide,displayTime);
    }
    private void read(NoticeViewport viewport,View content,long remaining,long openingHold,int id) {
        if(readingScroll!=null) {readingScroll.cancel();readingScroll=null;}
        if(readingPage!=null) {main.removeCallbacks(readingPage);readingPage=null;}
        int range=Math.max(0,content.getHeight()-viewport.getHeight());if(range<=viewport.getScrollY())return;
        long readingTime=Math.max(1,remaining-openingHold-1400);
        if(activity.getSharedPreferences("settings",0).getBoolean("animations",true)) {
            readingScroll=ObjectAnimator.ofInt(viewport,"scrollY",viewport.getScrollY(),range);readingScroll.setStartDelay(openingHold);readingScroll.setDuration(readingTime);readingScroll.setInterpolator(new android.view.animation.LinearInterpolator());readingScroll.start();
        } else {
            int step=Math.max(1,viewport.getHeight()-Ui.dp(activity,24)),pages=(range-viewport.getScrollY()+step-1)/step;
            long interval=Math.max(1,readingTime/(pages+1));
            readingPage=new Runnable() {public void run() {
                if(serial!=id||popup==null)return;viewport.scrollTo(0,Math.min(range,viewport.getScrollY()+step));
                if(viewport.getScrollY()<range)main.postDelayed(this,interval);
            }};main.postDelayed(readingPage,openingHold+interval);
        }
    }
    static boolean key(Context context,KeyEvent event) {
        while(!(context instanceof Activity)&&context instanceof ContextWrapper) {Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}
        WeakReference<ServerNotice> reference=context instanceof Activity?active.get((Activity)context):null;
        ServerNotice notice=reference==null?null:reference.get();return notice!=null&&notice.key(event);
    }
    private boolean key(KeyEvent event) {
        int key=event.getKeyCode();boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A;
        if(!confirm||consumedKey!=key&&(popup==null||!popup.isShowing()))return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN) {consumedKey=key;close();}
        else if(event.getAction()==KeyEvent.ACTION_UP) {consumedKey=KeyEvent.KEYCODE_UNKNOWN;close();}
        return true;
    }
    private void unregister() {
        WeakReference<ServerNotice> reference=active.get(activity);
        if(consumedKey==KeyEvent.KEYCODE_UNKNOWN&&reference!=null&&reference.get()==this)active.remove(activity);
    }
    private void playSound() {
        if(!activity.getSharedPreferences("settings",0).getBoolean("dashboardNoticeSound",false))return;
        sound=new NoticeSound(activity,main,soundStarted);sound.play();
    }
    private void stopSound() {if(sound!=null) {sound.close();sound=null;}}
    @Override public void close() {
        serial++;main.removeCallbacks(hide);if(readingSetup!=null) {main.removeCallbacks(readingSetup);readingSetup=null;}if(readingScroll!=null) {readingScroll.cancel();readingScroll=null;}
        if(readingPage!=null) {main.removeCallbacks(readingPage);readingPage=null;}
        stopSound();if(popup!=null) {popup.dismiss();popup=null;}unregister();
    }
}
