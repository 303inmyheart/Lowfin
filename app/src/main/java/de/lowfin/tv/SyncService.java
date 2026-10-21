package de.lowfin.tv;

import android.app.*;
import android.content.Intent;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;

public final class SyncService extends Service {
    private volatile boolean cancelled;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LowfinApp app;
    private CountDownLatch completion;
    @Override public void onCreate() {
        super.onCreate(); app=(LowfinApp)getApplication();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("sync",I18n.text("Bibliothekscache"),NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String message) {
        return new Notification.Builder(this,"sync").setSmallIcon(R.drawable.icon).setContentTitle(I18n.text("Lowfin · Bibliothek speichern"))
            .setContentText(message).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        startForeground(1,notification(I18n.text("Metadaten werden geladen")));
        if(app.account==null||app.clearingCache) { stopSelf(); return START_NOT_STICKY; }
        if(!app.syncing) { app.syncSummary=I18n.text("Bibliothekscache wird geprüft …"); app.syncing=true; completion=new CountDownLatch(1);app.syncIdle=completion;worker.execute(this::sync); }
        return START_NOT_STICKY;
    }
    private void status(String s) { app.syncStatus=s; if(!cancelled) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification(s)); }
    private void waitForPlayback() throws InterruptedException {
        if(app.playing&&!cancelled&&app.companion!=null&&app.companion.incrementalCache()) Thread.sleep(100);
        else while(app.playing&&!cancelled) Thread.sleep(1000);
        if(cancelled||Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
    private void sync() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        final Api api=app.api; final Library library=app.library; final Images images=app.images;
        long stamp=System.currentTimeMillis(); int offset=0,total=0; String anchor=""; boolean currentChannels=false;
        final CompanionApi companion=app.companion;
        try {
            if(companion!=null&&companion.incrementalCache()) { anchor=CacheChanges.request(companion,null,0).getString("cursor"); if(anchor.isEmpty()) throw new java.io.IOException("Missing cache baseline"); }
            app.syncSummary=I18n.text(library.count()>0?"Bibliothek wird aktualisiert · Cache bleibt verfügbar":"Bibliothekscache wird erstmals erstellt …");
            while(!cancelled) {
                waitForPlayback();
                JSONObject response=api.items(offset,150,"Movie,Series,Season,Episode,BoxSet,Video",null,null);
                JSONArray items=Json.array(response,"Items"); total=response.optInt("TotalRecordCount",offset+items.length());
                if(items.length()==0&&offset<total) throw new java.io.IOException("Incomplete metadata page");
                library.put(items,stamp); offset+=items.length(); status(I18n.text("Metadaten: ")+offset+" / "+total);
                if(items.length()==0||offset>=total) break;
            }
            if(cancelled) return;
            JSONArray views=Json.array(api.get("/Users/"+api.account.user+"/Views"),"Items"); library.state("views",views.toString());
            for(int v=0;v<views.length();v++) {
                JSONObject view=views.optJSONObject(v); if(view==null||"livetv".equals(view.optString("CollectionType"))) continue;
                String id=view.optString("Id"); int start=0;
                while(!cancelled) {
                    waitForPlayback(); JSONObject response=api.items(start,150,MainActivity.viewTypes(view),id,null); JSONArray items=Json.array(response,"Items");
                    if(items.length()==0&&start<response.optInt("TotalRecordCount",start)) throw new java.io.IOException("Incomplete library page");
                    library.put(items,stamp); library.link(id,items,stamp); start+=items.length(); status(I18n.text("Bibliothek: ")+view.optString("Name")+" · "+start+I18n.text(" Titel"));
                    if(items.length()==0||start>=response.optInt("TotalRecordCount",start)) break;
                }
                if(cancelled) return; library.completeLinks(id,stamp);
            }
            try { JSONArray channels=api.liveChannels(); library.replaceChannels(channels); currentChannels=true; }
            catch(Api.Failure error) { if(error.status==403) library.replaceChannels(new JSONArray()); }
            catch(Exception ignored) { /* An unavailable live-TV list must never prune cached channels. */ }
            library.checkpointSync(stamp,anchor,currentChannels);
            library.state("precacheBaselinePending","true");
            android.util.Log.i("LowfinCache","Metadata baseline committed; persistent artwork pass begins");
            int covers=0,missing=0; offset=0;
            while(!cancelled) {
                java.util.List<JSONObject> page=library.query("type!='TvChannel'",null,"id",100,offset); if(page.isEmpty()) break;
                for(JSONObject item:page) {
                    waitForPlayback();
                    boolean artworkSuccess=true;
                    if(api.hasImage(item,true)) { try { if(api.hasImage(item)) { if(images.fetch(item)!=null) covers++; else { missing++; artworkSuccess=false; } } if(!Artwork.type(item,true).equals("Primary")) { if(images.fetch(item,true)!=null) covers++; else { missing++; artworkSuccess=false; } } } catch(Exception e) { missing++; artworkSuccess=false; } }
                    if(!anchor.isEmpty()) library.artworkFinished(item.optString("Id"),artworkSuccess);
                    offset++;
                    if(offset%20==0) status(I18n.text("Cover: ")+offset+" / "+library.count()+" · "+images.bytes()/1048576+" MB");
                }
            }
            if(!cancelled) {
                String result=total+I18n.text(" Metadaten · ")+covers+I18n.text(" Cover")+(missing>0?" · "+missing+I18n.text(" Cover offen (Speicher/Verbindung)"):I18n.text(" · vollständig gespeichert"));
                if(missing==0) library.finishPrecacheArtwork();
                library.state("cacheStatus",result); app.syncStatus=result;
                app.channels.publish();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); app.syncStatus=I18n.text("Cache pausiert; vorhandene Daten bleiben verfügbar"); }
        catch(Exception e) { app.syncStatus=I18n.text("Cache unvollständig: ")+e.getMessage(); library.state("cacheStatus",app.syncStatus); }
        finally { app.syncing=false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); if(completion!=null) completion.countDown(); }
    }
    @Override public void onDestroy() { cancelled=true; java.util.List<Runnable> queued=worker.shutdownNow();if(!queued.isEmpty()&&completion!=null) { app.syncing=false;completion.countDown(); } super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
