package de.lowfin.tv;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import org.json.*;
import java.util.concurrent.TimeUnit;

/** Foreground-only idle controller. Playback activities never own this controller. */
final class IdleScreensaver {
    private final Activity activity;
    private final LowfinApp app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean resumed,loading,swallowTouch;
    private int generation,swallowKey=-1;
    private long lastActivity=SystemClock.elapsedRealtime();
    private ScreensaverView overlay;
    private View previousFocus,underneath;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferences=(prefs,key)-> {
        if(key!=null&&key.startsWith("screensaver")) main.post(()-> {if(resumed) {if(!enabled()) dismiss();schedule();}});
    };
    private boolean enabled() {return app.settings.getBoolean("screensaver",false)&&app.account!=null&&!app.playing;}
    private final Runnable tick=this::checkIdle;
    private void checkIdle() {
        if(!resumed) return;
        if(!enabled()) dismiss();
        else if(overlay==null&&activity.hasWindowFocus()&&SystemClock.elapsedRealtime()-lastActivity>=idleMillis()) show();
        schedule();
    }
    private void schedule() {
        main.removeCallbacks(tick);
        if(!resumed||!enabled()||active()) return;
        long remaining=idleMillis()-(SystemClock.elapsedRealtime()-lastActivity);
        main.postDelayed(tick,remaining>0?remaining:5000);
    }
    private final Runnable next=new Runnable() { public void run() { if(active()) { load();main.postDelayed(this,slideMillis()); } } };
    IdleScreensaver(Activity activity,LowfinApp app) { this.activity=activity;this.app=app; }
    long idleMillis() { return Math.max(1,Math.min(20,app.settings.getInt("screensaverMinutes",3)))*60000L; }
    private long slideMillis() { return Math.max(10,app.settings.getInt("screensaverSeconds",20))*1000L; }
    boolean active() { return overlay!=null; }
    void resume() { if(!resumed) app.settings.registerOnSharedPreferenceChangeListener(preferences);resumed=true;lastActivity=SystemClock.elapsedRealtime();schedule(); }
    void pause() { if(resumed) app.settings.unregisterOnSharedPreferenceChangeListener(preferences);resumed=false;main.removeCallbacksAndMessages(null);dismiss();swallowKey=-1;swallowTouch=false; }
    void activity() { lastActivity=SystemClock.elapsedRealtime();schedule(); }
    boolean key(KeyEvent event) {
        activity();
        if(active()) { swallowKey=event.getKeyCode();dismiss();if(event.getAction()==KeyEvent.ACTION_UP) swallowKey=-1;return true; }
        if(swallowKey==event.getKeyCode()) { if(event.getAction()==KeyEvent.ACTION_UP) swallowKey=-1;return true; }
        return false;
    }
    boolean touch(MotionEvent event) {
        activity();if(active()) { swallowTouch=true;dismiss(); }
        boolean consumed=swallowTouch;if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL) swallowTouch=false;return consumed;
    }
    boolean motion() { activity();if(!active()) return false;dismiss();return true; }
    void show() {
        if(active()||!resumed||app.api==null||app.playing) return;
        FrameLayout content=activity.findViewById(android.R.id.content);if(content==null) return;
        previousFocus=activity.getCurrentFocus();underneath=content.getChildCount()>0?content.getChildAt(0):null;
        overlay=new ScreensaverView(activity,app);overlay.setKeepScreenOn(true);content.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        if(underneath!=null) underneath.setVisibility(View.INVISIBLE);
        overlay.requestFocus();load();main.postDelayed(next,slideMillis());
    }
    void dismiss() {
        generation++;main.removeCallbacks(next);
        if(overlay!=null) { overlay.stop();overlay.setKeepScreenOn(false);if(overlay.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)overlay.getParent()).removeView(overlay);overlay=null; }
        if(underneath!=null) { underneath.setVisibility(View.VISIBLE);underneath=null; }
        if(previousFocus!=null&&previousFocus.isAttachedToWindow()) previousFocus.requestFocus();previousFocus=null;activity();
    }
    private void load() {
        if(loading||!active()) return;loading=true;
        java.util.List<String> libraries=AmbientLibraries.selection(app,"screensaver");
        if(libraries!=null&&libraries.isEmpty()) {loading=false;dismiss();return;}
        String parent=AmbientLibraries.parent(libraries);
        int request=generation;Api api=app.api;Library library=app.library;
        app.navigationIo.execute(()-> {
            JSONObject item=null;
            try {
                Api limited=new Api(api.account,api.http.newBuilder().callTimeout(10,TimeUnit.SECONDS).build());
                JSONArray items=Json.array(limited.get("/Users/"+api.account.user+"/Items","Recursive",true,"IncludeItemTypes","Movie,Series","ParentId",parent,"SortBy","Random","Limit",1,"ImageTypes","Backdrop","Fields","Overview,Genres,ProviderIds,ParentId"),"Items");
                JSONObject candidate=items.optJSONObject(0);if(candidate!=null&&(candidate.optString("Type").equals("Movie")||candidate.optString("Type").equals("Series"))) item=candidate;
            } catch(Exception ignored) { try { java.util.List<JSONObject> saved=library.randomMovies(parent,null,1);if(!saved.isEmpty()) item=saved.get(0); } catch(Exception unavailable) {} }
            JSONObject result=item;main.post(()-> { loading=false;if(request==generation&&active()&&api==app.api) { if(result!=null) overlay.bind(result);else dismiss(); } });
        });
    }
}
