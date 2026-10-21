package de.lowfin.tv;

import android.app.Application;
import android.content.SharedPreferences;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class LowfinApp extends Application {
    final ExecutorService io=Executors.newFixedThreadPool(4);
    final ExecutorService cacheIo=Executors.newSingleThreadExecutor();
    final ExecutorService navigationIo=Executors.newFixedThreadPool(2);
    final ExecutorService homeIo=Executors.newFixedThreadPool(2);
    final ExecutorService reports=Executors.newSingleThreadExecutor();
    Account account; Api api; Library library; Images images;
    HomeChannels channels;
    Accent accent;
    Ratings ratings;
    Tracearr tracearr;
    CompanionApi companion;
    CacheChanges cacheChanges;
    volatile java.util.function.Consumer<org.json.JSONObject> cacheListener;
    RatingKeyImport keyImport;
    VideoCache videoCache;
    SharedPreferences settings;
    volatile String syncStatus=I18n.text("Cache noch nicht erstellt");
    volatile String syncSummary="";
    volatile boolean syncing=false,playing=false,clearingCache=false;
    volatile CountDownLatch syncIdle=new CountDownLatch(0);
    private final java.util.Set<android.app.Activity> resumedActivities=new java.util.HashSet<>();
    private final android.os.Handler lifecycleHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pauseCache=()-> { if(resumedActivities.isEmpty()&&cacheChanges!=null) cacheChanges.pause(); };
    boolean foreground() { return !resumedActivities.isEmpty(); }
    private void trackCacheLifecycle() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityResumed(android.app.Activity a) { resumedActivities.add(a); lifecycleHandler.removeCallbacks(pauseCache); if(cacheChanges!=null&&!clearingCache) cacheChanges.resume(); }
            public void onActivityPaused(android.app.Activity a) { resumedActivities.remove(a); lifecycleHandler.postDelayed(pauseCache,250); }
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b) {}
            public void onActivityStarted(android.app.Activity a) {}
            public void onActivityStopped(android.app.Activity a) {}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b) {}
            public void onActivityDestroyed(android.app.Activity a) { resumedActivities.remove(a); }
        });
    }
    @Override public void onCreate() { super.onCreate(); trackCacheLifecycle(); I18n.init(this); syncStatus=I18n.text("Cache noch nicht erstellt"); accent=new Accent(this); settings=getSharedPreferences("settings",0); PlaybackPolicy.migrateSkipSettings(settings); keyImport=new RatingKeyImport(this); channels=new HomeChannels(this); videoCache=new VideoCache(this); setAccount(Account.load(this)); }
    synchronized void setAccount(Account a) {
        if(cacheChanges!=null) { cacheChanges.close(); cacheChanges=null; }
        if(images!=null) images.close();
        if(ratings!=null) ratings.close();
        account=a;
        tracearr=a==null?null:createTracearr(a);
        if(a!=null) { api=new Api(a); library=new Library(this,a.scope()); companion=new CompanionApi(this,api,library); images=new Images(this,api,a.scope()); ratings=new Ratings(this,library,api); cacheChanges=new CacheChanges(this,companion,images); }
        else { api=null; library=null; images=null; ratings=null; companion=null; }
        if(cacheChanges!=null&&!resumedActivities.isEmpty()) cacheChanges.resume();
        if(a!=null&&channels!=null) channels.schedule();
        if(a!=null) { final CompanionApi current=companion; reports.execute(current::replay); }
        if(a!=null) { final Library db=library; cacheIo.execute(()-> { try { String saved=db.state("cacheStatus"); if(library==db&&!syncing&&!saved.isEmpty()) syncStatus=saved; } catch(Exception ignored) {} backfill(db); }); }
    }
    Tracearr createTracearr(Account account) {
        final RatingKeys keys=new RatingKeys(this);
        Tracearr stats=new Tracearr(new Api(account),settings,name->{ if(!name.equals("token")) { String scoped=keys.get("tracearr-"+account.scope()+"-"+name); return scoped.isEmpty()?keys.get("tracearr_"+name):scoped; } String scoped=keys.get("tracearr-"+account.scope()); return scoped.isEmpty()&&!settings.getBoolean("tracearr-"+account.scope()+"-localToken",false)?keys.get("tracearr"):scoped; }); stats.companion=()->companion; return stats;
    }
    private void backfill(Library db) {
        if(library!=db) return;
        try { if(db.backfillGenres()) cacheIo.execute(()->backfill(db)); } catch(Exception ignored) { /* Retry on the next launch. */ }
    }
    void clearPrecache(Runnable done,java.util.function.Consumer<Exception> failed) {
        if(clearingCache||playing||account==null) { failed.accept(new IllegalStateException("Cache busy"));return; }
        clearingCache=true;final Account selected=account;final Library oldLibrary=library;final Images oldImages=images;final CountDownLatch idle=syncIdle;
        if(cacheChanges!=null) cacheChanges.close();
        stopService(new android.content.Intent(this,SyncService.class));api.http.dispatcher().cancelAll();
        io.execute(()-> {
            boolean retired=false;
            try {
                if(!idle.await(20,TimeUnit.SECONDS)) throw new java.io.IOException("Precache did not stop");
                synchronized(this) {
                    if(account!=selected||library!=oldLibrary) throw new IllegalStateException("Account changed");
                }
                oldLibrary.clearAndRetirePrecache();retired=true;oldImages.close();oldImages.clear();
                lifecycleHandler.post(()-> {
                    if(account!=selected||library!=oldLibrary) { clearingCache=false;failed.accept(new IllegalStateException("Account changed"));return; }
                    syncing=false;syncSummary="";syncStatus=I18n.resource(R.string.precache_cleared);setAccount(selected);clearingCache=false;
                    if(cacheChanges!=null&&!resumedActivities.isEmpty()) cacheChanges.resume();done.run();
                });
            } catch(Exception error) { final boolean rebuild=retired;lifecycleHandler.post(()-> {
                if(rebuild&&account==selected&&library==oldLibrary) setAccount(selected);
                clearingCache=false;
                if(library==oldLibrary&&companion!=null) { cacheChanges=new CacheChanges(this,companion,images);if(!resumedActivities.isEmpty()) cacheChanges.resume(); }
                failed.accept(error);
            }); }
        });
    }
    void logout() {
        stopService(new android.content.Intent(this,SyncService.class));
        channels.cancelAndClear();
        if(api!=null) { Api previous=api; previous.http.dispatcher().cancelAll(); reports.execute(()-> { try { previous.post("/Sessions/Logout",null); } catch(Exception ignored) {} }); }
        Account.clear(this); setAccount(null); io.execute(()-> { try { videoCache.clear(); } catch(Exception ignored) {} });
    }
}
