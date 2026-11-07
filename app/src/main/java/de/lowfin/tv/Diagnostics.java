package de.lowfin.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import android.view.FrameMetrics;
import android.view.Window;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in, bounded process-local observations. Never accepts titles, URLs, IDs or exception text. */
final class Diagnostics {
    static final String SETTING="diagnostics";
    static final int CAPACITY=192;
    enum Event { SCREEN_BUILD, NAV_CACHE, NAV_SERVER, NAV_DISCARDED, NAV_ERROR, HTTP_READ, HTTP_WRITE, LIBRARY_STORE,
        HOME_NEXT, HOME_RESUME, GRID_KEY, GRID_TARGET, GRID_FOCUS, PLAYER_STATE, FIRST_FRAME, VIDEO_DROPPED, PLAYER_ERROR, CACHE_CHANGES, CACHE_RESET, STARTUP_READY, HOME_LATEST }
    private static final long[] FRAME_LIMITS={8,16,24,33,50,100,250};
    private final LowfinApp app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Activity> resumed=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Activity,Window.OnFrameMetricsAvailableListener> listeners=new IdentityHashMap<>();
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    private final long[] frameBuckets=new long[FRAME_LIMITS.length+1];
    private volatile boolean enabled;
    private long epoch,started,evicted,frames,frameTotalNs,frameMaxNs,frameReportsDropped;
    private HandlerThread frameThread;
    private Handler frameHandler;
    private static final class Sample {
        final Event event;final long at,a,b,c,d;
        Sample(Event event,long at,long a,long b,long c,long d) {this.event=event;this.at=at;this.a=a;this.b=b;this.c=c;this.d=d;}
    }
    Diagnostics(LowfinApp app) {
        this.app=app;enabled=app.settings.getBoolean(SETTING,BuildConfig.DIAGNOSTICS_DEFAULT);started=SystemClock.elapsedRealtime();
        app.settings.registerOnSharedPreferenceChangeListener(preferences);
    }
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences=(prefs,key)-> {
        if(SETTING.equals(key)) configure(prefs.getBoolean(SETTING,BuildConfig.DIAGNOSTICS_DEFAULT));
    };
    boolean enabled() {return enabled;}
    void setEnabled(boolean value) {app.settings.edit().putBoolean(SETTING,value).apply();configure(value);}
    private void configure(boolean value) {
        synchronized(this) {if(enabled==value) return;enabled=value;clearLocked();}
        main.post(this::restartFrames);
    }
    void clear() {synchronized(this) {clearLocked();}main.post(this::restartFrames);}
    private void clearLocked() {
        epoch++;samples.clear();evicted=frames=frameTotalNs=frameMaxNs=frameReportsDropped=0;
        Arrays.fill(frameBuckets,0);started=SystemClock.elapsedRealtime();
    }
    void record(Event event,long a,long b,long c,long d) {
        if(!enabled) return;
        synchronized(this) {
            if(!enabled) return;
            if(samples.size()==CAPACITY) {samples.removeFirst();evicted++;}
            samples.addLast(new Sample(event,SystemClock.elapsedRealtime()-started,a,b,c,d));
        }
    }
    synchronized long epoch() {return epoch;}
    synchronized void frame(long generation,long durationNs,int dropped) {
        if(!enabled||generation!=epoch||durationNs<0) return;
        frames++;frameTotalNs+=durationNs;frameMaxNs=Math.max(frameMaxNs,durationNs);frameReportsDropped+=Math.max(0,dropped);
        int bucket=0;while(bucket<FRAME_LIMITS.length&&durationNs>FRAME_LIMITS[bucket]*1000000L) bucket++;
        frameBuckets[bucket]++;
    }
    void resumed(Activity activity) {resumed.add(activity);updateFrames();}
    void paused(Activity activity) {resumed.remove(activity);removeFrames(activity);updateFrames();}
    private void removeFrames(Activity activity) {
        Window.OnFrameMetricsAvailableListener listener=listeners.remove(activity);
        if(listener!=null) activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
    }
    private void restartFrames() {for(Activity activity:new ArrayList<>(listeners.keySet()))removeFrames(activity);updateFrames();}
    private void updateFrames() {
        for(Activity activity:new ArrayList<>(listeners.keySet())) if(!enabled||!resumed.contains(activity)) removeFrames(activity);
        if(enabled&&!resumed.isEmpty()) {
            if(frameThread==null) {frameThread=new HandlerThread("LowfinDiagnostics",android.os.Process.THREAD_PRIORITY_BACKGROUND);frameThread.start();frameHandler=new Handler(frameThread.getLooper());}
            for(Activity activity:resumed) if(!listeners.containsKey(activity)) {
                final long generation=epoch();
                Window.OnFrameMetricsAvailableListener listener=(window,metrics,dropped)->frame(generation,metrics.getMetric(FrameMetrics.TOTAL_DURATION),dropped);
                activity.getWindow().addOnFrameMetricsAvailableListener(listener,frameHandler);listeners.put(activity,listener);
            }
        } else if(frameThread!=null) {frameThread.quitSafely();frameThread=null;frameHandler=null;}
    }
    synchronized JSONObject snapshot() {
        JSONObject result=Json.obj("schema",1,"version",BuildConfig.VERSION_NAME,"versionCode",BuildConfig.VERSION_CODE,"enabled",enabled);
        if(!enabled) return result;
        Runtime runtime=Runtime.getRuntime();
        put(result,"sessionMs",SystemClock.elapsedRealtime()-started);put(result,"playing",app.playing);
        put(result,"javaUsedBytes",runtime.totalMemory()-runtime.freeMemory());put(result,"javaMaxBytes",runtime.maxMemory());
        put(result,"nativeAllocatedBytes",Debug.getNativeHeapAllocatedSize());put(result,"evictedEvents",evicted);
        JSONObject pools=new JSONObject();
        for(Object[] pool:new Object[][]{{"navigation",app.navigationIo},{"home",app.homeIo},{"latest",app.latestIo},{"cache",app.cacheIo},{"general",app.io},{"reports",app.reports}}) {
            if(pool[1] instanceof ThreadPoolExecutor) {ThreadPoolExecutor p=(ThreadPoolExecutor)pool[1];put(pools,(String)pool[0],Json.obj("active",p.getActiveCount(),"queued",p.getQueue().size(),"completed",p.getCompletedTaskCount()));}
        }
        put(result,"executors",pools);
        if(app.api!=null) put(result,"http",Json.obj("running",app.api.http.dispatcher().runningCallsCount(),"queued",app.api.http.dispatcher().queuedCallsCount()));
        if(app.companion!=null) put(result,"companion",Json.obj("available",app.companion.available(),"incrementalCache",app.companion.incrementalCache()));
        JSONArray buckets=new JSONArray();for(long count:frameBuckets) buckets.put(count);
        put(result,"uiFrames",Json.obj("count",frames,"totalNs",frameTotalNs,"maxNs",frameMaxNs,"reportDrops",frameReportsDropped,"bucketUpperMs",new JSONArray(Arrays.asList(8,16,24,33,50,100,250)),"buckets",buckets));
        JSONArray events=new JSONArray();for(Sample sample:samples) events.put(Json.obj("event",sample.event.name(),"atMs",sample.at,"a",sample.a,"b",sample.b,"c",sample.c,"d",sample.d));
        put(result,"events",events);return result;
    }
    private static void put(JSONObject object,String key,Object value) {try {object.put(key,value);}catch(JSONException impossible) {throw new IllegalStateException(impossible);}}
}
