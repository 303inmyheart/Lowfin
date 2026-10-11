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
        if(app.account==null) { stopSelf(); return START_NOT_STICKY; }
        if(!app.syncing) { app.syncSummary=I18n.text("Bibliothekscache wird geprüft …"); app.syncing=true; worker.execute(this::sync); }
        return START_NOT_STICKY;
    }
    private void status(String s) { app.syncStatus=s; if(!cancelled) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1,notification(s)); }
    private void waitForPlayback() throws InterruptedException {
        while(app.playing&&!cancelled) Thread.sleep(1000);
        if(cancelled||Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
    private void sync() {
        final Api api=app.api; final Library library=app.library; final Images images=app.images;
        long stamp=System.currentTimeMillis(); int offset=0,total=0;
        try {
            app.syncSummary=I18n.text(library.count()>0?"Bibliothek wird aktualisiert · Cache bleibt verfügbar":"Bibliothekscache wird erstmals erstellt …");
            while(!cancelled) {
                waitForPlayback();
                JSONObject response=api.items(offset,150,"Movie,Series,Season,Episode,BoxSet,Video",null,null);
                JSONArray items=Json.array(response,"Items"); total=response.optInt("TotalRecordCount",offset+items.length());
                library.put(items,stamp); offset+=items.length(); status(I18n.text("Metadaten: ")+offset+" / "+total);
                if(items.length()==0||offset>=total) break;
            }
            if(cancelled) return;
            library.completeSync(stamp);
            JSONArray views=Json.array(api.get("/Users/"+api.account.user+"/Views"),"Items"); library.state("views",views.toString());
            for(int v=0;v<views.length();v++) {
                JSONObject view=views.optJSONObject(v); if(view==null||"livetv".equals(view.optString("CollectionType"))) continue;
                String id=view.optString("Id"); int start=0;
                while(!cancelled) {
                    waitForPlayback(); JSONObject response=api.items(start,150,MainActivity.viewTypes(view),id,null); JSONArray items=Json.array(response,"Items");
                    library.put(items,stamp); library.link(id,items,stamp); start+=items.length(); status(I18n.text("Bibliothek: ")+view.optString("Name")+" · "+start+I18n.text(" Titel"));
                    if(items.length()==0||start>=response.optInt("TotalRecordCount",start)) break;
                }
                if(cancelled) return; library.completeLinks(id,stamp);
            }
            int covers=0,missing=0; offset=0;
            while(!cancelled) {
                java.util.List<JSONObject> page=library.query(null,null,"id",100,offset); if(page.isEmpty()) break;
                for(JSONObject item:page) {
                    waitForPlayback();
                    if(api.hasImage(item,true)) { try { if(api.hasImage(item)) { if(images.fetch(item)!=null) covers++; else missing++; } if(!Artwork.type(item,true).equals("Primary")) { if(images.fetch(item,true)!=null) covers++; else missing++; } } catch(Exception e) { missing++; } }
                    offset++;
                    if(offset%20==0) status(I18n.text("Cover: ")+offset+" / "+library.count()+" · "+images.bytes()/1048576+" MB");
                }
            }
            if(!cancelled) {
                String result=total+I18n.text(" Metadaten · ")+covers+I18n.text(" Cover")+(missing>0?" · "+missing+I18n.text(" Cover offen (Speicher/Verbindung)"):I18n.text(" · vollständig gespeichert"));
                library.state("cacheStatus",result); app.syncStatus=result;
                app.channels.publish();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); app.syncStatus=I18n.text("Cache pausiert; vorhandene Daten bleiben verfügbar"); }
        catch(Exception e) { app.syncStatus=I18n.text("Cache unvollständig: ")+e.getMessage(); library.state("cacheStatus",app.syncStatus); }
        finally { app.syncing=false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    }
    @Override public void onDestroy() { cancelled=true; worker.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
