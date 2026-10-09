package de.lowfin.tv;

import android.app.Application;
import android.content.SharedPreferences;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class LowfinApp extends Application {
    final ExecutorService io=Executors.newFixedThreadPool(4);
    final ExecutorService cacheIo=Executors.newSingleThreadExecutor();
    final ExecutorService navigationIo=Executors.newSingleThreadExecutor();
    final ExecutorService reports=Executors.newSingleThreadExecutor();
    Account account; Api api; Library library; Images images;
    HomeChannels channels;
    Accent accent;
    Ratings ratings;
    RatingKeyImport keyImport;
    VideoCache videoCache;
    SharedPreferences settings;
    volatile String syncStatus=I18n.text("Cache noch nicht erstellt");
    volatile boolean syncing=false,playing=false;
    @Override public void onCreate() { super.onCreate(); I18n.init(this); syncStatus=I18n.text("Cache noch nicht erstellt"); accent=new Accent(this); settings=getSharedPreferences("settings",0); keyImport=new RatingKeyImport(this); channels=new HomeChannels(this); videoCache=new VideoCache(this); setAccount(Account.load(this)); }
    synchronized void setAccount(Account a) {
        if(images!=null) images.close();
        if(ratings!=null) ratings.close();
        account=a;
        if(a!=null) { api=new Api(a); library=new Library(this,a.scope()); images=new Images(this,api,a.scope()); ratings=new Ratings(this,library,api); }
        else { api=null; library=null; images=null; ratings=null; }
        if(a!=null&&channels!=null) channels.schedule();
        if(a!=null) { final Library db=library; cacheIo.execute(()->backfill(db)); }
    }
    private void backfill(Library db) {
        if(library!=db) return;
        try { if(db.backfillGenres()) cacheIo.execute(()->backfill(db)); } catch(Exception ignored) { /* Retry on the next launch. */ }
    }
    void logout() {
        stopService(new android.content.Intent(this,SyncService.class));
        channels.cancelAndClear();
        if(api!=null) { Api previous=api; previous.http.dispatcher().cancelAll(); reports.execute(()-> { try { previous.post("/Sessions/Logout",null); } catch(Exception ignored) {} }); }
        Account.clear(this); setAccount(null); io.execute(()-> { try { videoCache.clear(); } catch(Exception ignored) {} });
    }
}
