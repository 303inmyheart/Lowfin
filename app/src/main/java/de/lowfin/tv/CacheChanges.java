package de.lowfin.tv;

import android.content.Intent;
import okhttp3.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

/** One waiting request while a Lowfin activity is in the foreground. No background cron or playback-thread work. */
final class CacheChanges implements AutoCloseable {
    private final LowfinApp app;
    private final CompanionApi companion;
    private final Library library;
    private final Images images;
    private final CacheNotice notices;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean active,closed,artworkActive;
    boolean artworkActive() { return artworkActive; }
    private volatile Call pending;
    private ScheduledFuture<?> scheduled;
    private long notBefore,fullNotBefore;
    private boolean channelsChecked;
    private final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean();
    CacheChanges(LowfinApp app,CompanionApi companion,Images images) { this.app=app; this.companion=companion; library=companion.library; this.images=images;
        notices=new CacheNotice(()->current()&&app.foreground()&&app.settings.getBoolean("companionUpdateToast",true),count->android.widget.Toast.makeText(app,I18n.quantity(R.plurals.companion_changes_received,count,count),android.widget.Toast.LENGTH_SHORT).show());
        notBefore=android.os.SystemClock.elapsedRealtime()+10000; }
    synchronized void resume() { if(closed||app.clearingCache) return; active=true; if(!running.get()&&(scheduled==null||scheduled.isDone())) schedule(0); }
    synchronized void pause() { active=false; if(scheduled!=null) scheduled.cancel(false); if(pending!=null) pending.cancel(); }
    private synchronized void schedule(long seconds) { if(active&&!closed) scheduled=worker.schedule(this::tick,seconds,TimeUnit.SECONDS); }
    private boolean current() { return active&&!closed&&!app.clearingCache&&app.library==library&&app.companion==companion; }
    static JSONObject request(CompanionApi companion,String cursor,int wait) throws Exception { return read(companion,null,cursor,wait); }
    private static JSONObject read(CompanionApi companion,CacheChanges owner,String cursor,int wait) throws Exception {
        OkHttpClient transport=companion.api.http.newBuilder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
        Call call=transport.newCall(companion.api.request(companion.api.url(CompanionApi.ROOT+"/cache/changes","cursor",cursor,"waitSeconds",wait,"limit",8)).get().build());
        if(owner!=null) owner.pending=call;
        try(Response response=call.execute()) {
            if(!response.isSuccessful()) throw new Api.Failure(response.code()); if(response.body()==null) throw new IOException("Missing cache changes");
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
            try(InputStream input=response.body().byteStream()) { while((count=input.read(buffer))!=-1) { if(out.size()+count>2097152) throw new IOException("Oversized cache changes"); out.write(buffer,0,count); } }
            return new JSONObject(out.toString("UTF-8"));
        } finally { if(owner!=null) owner.pending=null; }
    }
    private void tick() {
        if(!running.compareAndSet(false,true)) return;
        long retry=1;
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            if(!current()) return;
            if(!app.settings.getBoolean("autoPrecache",true)||app.syncing) { retry=10; return; }
            if(android.os.SystemClock.elapsedRealtime()<notBefore) { retry=3; return; }
            companion.discover();
            if(!companion.incrementalCache()) {
                if(!channelsChecked) {
                    try { JSONArray channels=companion.api.liveChannels(); if(!current()) return; library.replaceChannels(channels); app.syncStatus=library.state("cacheStatus"); channelsChecked=true; }
                    catch(Api.Failure error) { if(error.status==403) { library.replaceChannels(new JSONArray()); channelsChecked=true; } }
                    catch(Exception ignored) { /* Retry without deleting existing channels. */ }
                }
                nativeFallback(); retry=60; return;
            }
            String cursor=library.state("cacheCursor");
            if(needsBaseline(library)) { full(); retry=10; return; }
            if(cursor.isEmpty()) { JSONObject head=read(companion,this,null,0); if(!current()||app.syncing) return; library.subscribeCacheChanges(head); android.util.Log.i("LowfinCache","Existing metadata retained; subscribed to plugin changes (history gap)"); app.syncStatus=library.state("cacheStatus"); retry=1; return; }
            JSONObject response=read(companion,this,cursor,library.artworkPending(1).isEmpty()?20:0);
            if(!current()||app.syncing) return;
            if(response.optBoolean("reset")) { library.subscribeCacheChanges(response); android.util.Log.i("LowfinCache","Plugin journal reset; existing metadata retained (history gap)"); app.syncStatus=library.state("cacheStatus"); retry=1; return; }
            if(images!=null) images.coverTags.invalidate(Json.array(response,"changes"));
            try { library.applyCacheChanges(response); } finally { if(images!=null) images.coverTags.changesApplied(); }
            if(current()&&!cursor.equals(response.optString("cursor"))) notices.changed(Json.array(response,"changes").length());
            boolean artworkWork=library.artworkCount()>0;
            artworkActive=artworkWork;
            for(String id:library.artworkPending(app.playing?1:32)) {
                if(!current()||app.syncing) break;
                JSONObject item=library.item(id); boolean success=true;
                try { if(item!=null&&!"TvChannel".equals(item.optString("Type"))) { if(companion.api.hasImage(item)) success=images.fetch(item)!=null; if(!Artwork.type(item,true).equals("Primary")) success=images.fetch(item,true)!=null&&success; } }
                catch(Exception e) { success=false; }
                library.artworkFinished(id,success);
            }
            if(artworkWork&&library.artworkCount()==0&&!"true".equals(library.state("cacheNeedsReview"))) { library.finishPrecacheArtwork(); library.state("cacheStatus",I18n.resource(R.string.cache_artwork_ready)); app.syncStatus=library.state("cacheStatus"); }
            if(Json.array(response,"changes").length()>0) {
                android.util.Log.i("LowfinCache","Applied "+Json.array(response,"changes").length()+" changed items");
                JSONArray changes=Json.array(response,"changes"); for(int i=0;i<changes.length();i++) { JSONObject change=changes.optJSONObject(i); if(change!=null) change.put("item",library.withCompanion(change.getJSONObject("item"))); }
                java.util.function.Consumer<JSONObject> listener=app.cacheListener; if(current()&&listener!=null) listener.accept(response);
            }
            retry=response.optBoolean("more")?0:1;
        } catch(Exception error) {
            retry=60;
            if(current()) { android.util.Log.w("LowfinCache","Incremental cache postponed: "+error.getClass().getSimpleName()); if(error instanceof Api.Failure&&(((Api.Failure)error).status==404||((Api.Failure)error).status==503)) { companion.refreshDiscovery(); } }
        } finally { artworkActive=false; running.set(false); schedule(retry); }
    }
    static boolean needsBaseline(Library library) { return library.state("lastSync").isEmpty()||!library.hasItems(); }
    private void nativeFallback() { try { long last=Long.parseLong(library.state("lastSync").isEmpty()?"0":library.state("lastSync")); if(System.currentTimeMillis()-last>app.settings.getInt("cacheHours",24)*3600000L) full(); } catch(Exception ignored) { } }
    private void full() { if(current()&&!app.syncing&&android.os.SystemClock.elapsedRealtime()>=fullNotBefore) { fullNotBefore=android.os.SystemClock.elapsedRealtime()+300000; android.util.Log.i("LowfinCache","Starting full metadata baseline"); app.startForegroundService(new Intent(app,SyncService.class)); } }
    @Override public synchronized void close() { pause(); closed=true; notices.close(); worker.shutdownNow(); }
}
