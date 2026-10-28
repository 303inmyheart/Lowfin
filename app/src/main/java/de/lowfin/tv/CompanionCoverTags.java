package de.lowfin.tv;

import android.content.*;
import android.os.*;
import android.util.LruCache;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Small on-demand metadata batches; no bitmap changes, UI-thread disk access or library-wide scan. */
final class CompanionCoverTags implements AutoCloseable {
    private final LowfinApp app;
    private final Api api;
    private final CompanionApi companion;
    private final Library library;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Object gate=new Object(),pendingGate=new Object();
    private final LruCache<String,Integer> facts=new LruCache<>(256);
    private final LruCache<String,String[]> labelFacts=new LruCache<>(256);
    private static final class Retry { final long epoch,until; Retry(long epoch,long until) { this.epoch=epoch;this.until=until; } }
    private final LruCache<String,Retry> retry=new LruCache<>(256);
    private final Set<String> pending=new LinkedHashSet<>();
    private final WeakHashMap<ArtworkView,String> bound=new WeakHashMap<>();
    private final Runnable settingsChanged=()->main.post(this::refresh);
    private final Runnable drain=this::drain;
    private final Runnable indexRetry=this::refresh;
    private volatile boolean closed;
    private boolean busy;
    private volatile boolean updating;
    private volatile long epoch;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferences=(prefs,key)-> { if("showCoverTags".equals(key)||"hdrPlayback".equals(key)) main.post(this::refresh); };

    CompanionCoverTags(Context context,Api api) {
        this.api=api;Context application=context.getApplicationContext();app=application instanceof LowfinApp?(LowfinApp)application:null;
        companion=app!=null&&app.api==api?app.companion:null;library=companion==null?null:companion.library;
        if(companion!=null) { companion.coverTagsChanged=settingsChanged;app.settings.registerOnSharedPreferenceChangeListener(preferences); }
    }
    private int filter() { return !closed&&companion!=null&&app.api==api&&app.settings.getBoolean("showCoverTags",true)?((companion.coverTagFilter()&(app.settings.getBoolean("hdrPlayback",false)?7:3))|(companion.extendedTags()&&!companion.coverLabelKeys().isEmpty()?8:0)):0; }
    static String id(String value) { return Json.normalizedId(value); }
    void bind(ArtworkView view,JSONObject item,boolean wide) {
        String type=item.optString("Type"),image=Artwork.type(item,wide);
        String id=(type.equals("Movie")||type.equals("Episode")||type.equals("Video")||type.equals("Series")||type.equals("Season"))&&(image.equals("Primary")||image.equals("Thumb"))?id(item.optString("Id")):"";
        if(id.isEmpty()) { bound.remove(view);view.badges("",0);return; }
        bound.put(view,id);if(!id.equals(view.badgeItem)||updating) view.badges(id,0);apply(view,id);
    }
    private void apply(ArtworkView view,String id) {
        if(!id.equals(view.badgeItem)) return;
        int filter=filter();if(filter==0) { view.badges(id,0);return; }
        if(updating) return;
        // LruCache protects its own short operations. Never wait for the writer's SQLite lock here.
        Integer mask=facts.get(id);String[] labels=labelFacts.get(id);if(mask!=null&&(!companion.extendedTags()||labels!=null)) { if(companion.extendedTags()) view.badges(id,0,CoverTagLabels.filter(labels,companion.coverLabelKeys(),app.settings.getBoolean("hdrPlayback",false)));else view.badges(id,mask&filter);return; }
        view.badges(id,0);
        Retry delay=retry.get(id);if(delay!=null&&delay.epoch==epoch&&SystemClock.elapsedRealtime()<delay.until) return;
        synchronized(pendingGate) { if(pending.size()<256) pending.add(id); }
        if(!busy) { main.removeCallbacks(drain);main.postDelayed(drain,80); }
    }
    private void refresh() {
        if(closed) return;
        for(Map.Entry<ArtworkView,String> entry:new ArrayList<>(bound.entrySet())) {
            ArtworkView view=entry.getKey();if(view!=null) apply(view,entry.getValue());
        }
    }
    private void drain() {
        if(closed||busy||updating||filter()==0||library==null) return;
        List<String> ids=new ArrayList<>();final long generation;
        synchronized(pendingGate) { generation=epoch;Iterator<String> it=pending.iterator();while(it.hasNext()&&ids.size()<64) { ids.add(it.next());it.remove(); } }
        if(ids.isEmpty()) return;busy=true;
        worker.execute(()-> {
            Map<String,Integer> loaded=new HashMap<>();Map<String,String[]> loadedLabels=new HashMap<>();Set<String> received=new HashSet<>();final boolean extended=companion.extendedTags();List<String> missing=new ArrayList<>();boolean indexPending=false;
            try {
                List<String> keys=new ArrayList<>();for(String id:ids) {keys.add("cover-tags-v2-"+id);if(extended) keys.add("cover-tag-labels-v1-"+id);}
                Map<String,String> storedFacts=library.states(keys);
                for(String id:ids) {
                    String stored=storedFacts.getOrDefault("cover-tags-v2-"+id,"");
                    if(stored.length()==1&&stored.charAt(0)>='0'&&stored.charAt(0)<='7') loaded.put(id,stored.charAt(0)-'0');
                    if(extended) {String[] labels=CoverTagLabels.decode(storedFacts.getOrDefault("cover-tag-labels-v1-"+id,""));if(labels!=null) loadedLabels.put(id,labels);}
                    if(!loaded.containsKey(id)||extended&&!loadedLabels.containsKey(id)) missing.add(id);
                }
                if(!missing.isEmpty()&&!closed&&filter()!=0) {
                    JSONObject response=companion.call("GET","/badges",null,"ids",String.join(",",missing));
                    indexPending=response.optBoolean("pending");JSONArray entries=response.getJSONArray("items");if(entries.length()>64) throw new java.io.IOException("Oversized cover tag response");
                    for(int i=0;i<entries.length();i++) {
                        JSONObject entry=entries.getJSONObject(i);String id=id(entry.optString("id"));int mask=entry.optInt("mask",-1);
                        if(missing.contains(id)&&mask>=0&&mask<=7) {received.add(id);loaded.put(id,mask);String[] labels=CoverTagLabels.parse(entry.optJSONArray("tags"));if(labels!=null) loadedLabels.put(id,labels);}
                    }
                }
                synchronized(gate) {
                    if(!closed&&generation==epoch) {
                        Map<String,String> fresh=new LinkedHashMap<>();
                        for(String id:received) {if(loaded.containsKey(id)) fresh.put("cover-tags-v2-"+id,""+loaded.get(id));if(extended&&loadedLabels.containsKey(id)) fresh.put("cover-tag-labels-v1-"+id,new JSONArray(Arrays.asList(loadedLabels.get(id))).toString());}
                        // Disk hits are already durable; only new server facts need one shared transaction.
                        library.stateBatch(fresh);
                        for(Map.Entry<String,Integer> entry:loaded.entrySet()) facts.put(entry.getKey(),entry.getValue());
                        for(Map.Entry<String,String[]> entry:loadedLabels.entrySet()) labelFacts.put(entry.getKey(),entry.getValue());
                    }
                }
            } catch(Exception ignored) { /* Artwork remains available when the optional service fails. */ }
            final boolean waitingForIndex=indexPending;
            main.post(()->completeBatch(generation,ids,extended,waitingForIndex));
        });
    }
    // UI callback: never acquire gate, which protects background SQLite writes.
    // epoch is volatile and each cache operation has its own short internal lock.
    private void completeBatch(long generation,List<String> ids,boolean extended,boolean waitingForIndex) {
        busy=false;if(closed) return;
        if(generation==epoch) for(String id:ids) if(facts.get(id)==null||extended&&labelFacts.get(id)==null) retry.put(id,new Retry(generation,SystemClock.elapsedRealtime()+(waitingForIndex?5000:30000)));
        if(waitingForIndex) { main.removeCallbacks(indexRetry);main.postDelayed(indexRetry,5100); }
        refresh();synchronized(pendingGate) { if(!pending.isEmpty()&&filter()!=0) main.postDelayed(drain,80); }
    }
    void invalidate(JSONArray changes) {
        if(changes.length()==0) return;
        updating=true;
        synchronized(gate) {
            epoch++;for(int i=0;i<changes.length();i++) { JSONObject change=changes.optJSONObject(i);if(change==null) continue;for(String id:Json.coverTagIds(Json.object(change,"item"))) { facts.remove(id);labelFacts.remove(id);retry.remove(id); } }
        }
    }
    void changesApplied() { updating=false;main.post(this::refresh); }
    @Override public void close() {
        closed=true;main.removeCallbacks(drain);main.removeCallbacks(indexRetry);worker.shutdownNow();
        if(companion!=null) { if(companion.coverTagsChanged==settingsChanged) companion.coverTagsChanged=null;app.settings.unregisterOnSharedPreferenceChangeListener(preferences); }
        synchronized(pendingGate) { pending.clear(); }
        synchronized(gate) { facts.evictAll();labelFacts.evictAll();retry.evictAll(); }
    }
}
