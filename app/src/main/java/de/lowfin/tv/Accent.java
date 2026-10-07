package de.lowfin.tv;

import android.app.*;
import android.os.*;
import android.view.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Muted, curated hues; only foreground time advances the hourly timer. */
final class Accent implements Application.ActivityLifecycleCallbacks {
    static final long HOUR=3600000;
    static final int[] POOL={0xffc9a4cf,0xff9bbbdc,0xff92cdc7,0xffa0cb9c,0xffd1ca92,0xffdcb98c,0xffd69f91,0xffd397ae,0xffaca1dc,0xff91b9d0,0xffa0cda2,0xffd0a6c8};
    static int color=POOL[0];
    private final android.content.SharedPreferences settings;
    private final Random random=new Random();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<Activity> started=Collections.newSetFromMap(new WeakHashMap<>());
    private long since,elapsed;
    private final Runnable change=()-> { elapsed=0; since=SystemClock.elapsedRealtime(); if(rotateEnabled()) choose(); if(!started.isEmpty()) handler.postDelayed(this::tick,period()); };
    private void tick() { change.run(); }
    Accent(Application app) { settings=app.getSharedPreferences("settings",0); chooseStart(); app.registerActivityLifecycleCallbacks(this); }
    private boolean rotateEnabled() { return settings.getBoolean("rotateAccent",true); }
    private long period() { return Math.max(15,settings.getInt("accentMinutes",60))*60000L; }
    private void chooseStart() { if(settings.getBoolean("randomAccent",true)) choose(); else { color=POOL[Math.floorMod(settings.getInt("accentIndex",0),POOL.length)]; for(Activity activity:new ArrayList<>(started)) apply(activity.getWindow().getDecorView()); } }
    void refresh() { handler.removeCallbacksAndMessages(null); elapsed=0; since=SystemClock.elapsedRealtime(); chooseStart(); if(!started.isEmpty()) handler.postDelayed(this::tick,period()); }
    static int next(int current,int choice) { int index=0; for(int i=0;i<POOL.length;i++) if(POOL[i]==current) index=i; return POOL[(index+1+Math.floorMod(choice,POOL.length-1))%POOL.length]; }
    private void choose() { color=next(color,random.nextInt(POOL.length-1)); for(Activity activity:new ArrayList<>(started)) apply(activity.getWindow().getDecorView()); }
    static void bind(View view,BiConsumer<View,Integer> style) { view.setTag(R.id.accent_style,style); style.accept(view,color); }
    @SuppressWarnings("unchecked") static void apply(View view) {
        Object style=view.getTag(R.id.accent_style); if(style instanceof BiConsumer) ((BiConsumer<View,Integer>)style).accept(view,color);
        if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) apply(group.getChildAt(i)); }
    }
    @Override public void onActivityStarted(Activity activity) {
        boolean first=started.isEmpty(); started.add(activity); if(first) { if(activity instanceof MainActivity) { elapsed=0; chooseStart(); } since=SystemClock.elapsedRealtime(); handler.postDelayed(this::tick,Math.max(1,period()-elapsed)); } apply(activity.getWindow().getDecorView());
    }
    @Override public void onActivityStopped(Activity activity) { started.remove(activity); if(started.isEmpty()) { elapsed+=Math.max(0,SystemClock.elapsedRealtime()-since); handler.removeCallbacksAndMessages(null); } }
    @Override public void onActivityCreated(Activity a,Bundle b) {} @Override public void onActivityResumed(Activity a) { apply(a.getWindow().getDecorView()); } @Override public void onActivityPaused(Activity a) {} @Override public void onActivitySaveInstanceState(Activity a,Bundle b) {} @Override public void onActivityDestroyed(Activity a) { started.remove(a); }
}
