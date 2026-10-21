package de.lowfin.tv;

import android.content.*;
import android.os.SystemClock;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Optional, account-scoped Companion v1; absence never replaces ordinary Jellyfin playback. */
final class CompanionApi {
    static final String ROOT="/Lowfin/Companion/v1";
    static final class Session {
        final String item,lease=UUID.randomUUID().toString();
        final long revision;
        final AtomicLong sequence=new AtomicLong();
        volatile JSONObject state;
        Session(String item,JSONObject state) { this.item=item; this.state=state; revision=state.optLong("revision"); }
    }
    static final class Prepared {
        final Session session; final long position;
        Prepared(Session session,long position) { this.session=session; this.position=position; }
    }
    final Api api; final Library library;
    private final OkHttpClient http;
    private final SharedPreferences outbox;
    private volatile boolean available;
    private volatile boolean scopedResume,resumeVisibility,incrementalCache,serverProviders;
    private volatile int coverTagFilter;
    private volatile boolean coverTagsSupported,extendedTags;
    private volatile Set<String> coverLabelKeys=Collections.emptySet();
    volatile Runnable coverTagsChanged;
    private long probeAt;
    CompanionApi(Context context,Api api,Library library) {
        this.api=api; this.library=library;
        outbox=context.getSharedPreferences("companion-outbox-"+api.account.scope(),0);
        available=outbox.getBoolean("available",false); library.companionAvailable=available;
        scopedResume=outbox.getBoolean("scopedResume",false); resumeVisibility=outbox.getBoolean("resumeVisibility",false);
        incrementalCache=outbox.getBoolean("incrementalCache",false); serverProviders=outbox.getBoolean("serverProviders",false);
        coverTagFilter=outbox.getInt("coverTagFilter",0);coverTagsSupported=outbox.getBoolean("coverTagsSupported",false);
        extendedTags=outbox.getBoolean("extendedTags",false);try {JSONArray saved=new JSONArray(outbox.getString("coverLabelKeys","[]"));Set<String> keys=new HashSet<>();for(int i=0;i<saved.length();i++) if(CoverTagLabels.KEYS.contains(saved.optString(i))) keys.add(saved.optString(i));coverLabelKeys=Collections.unmodifiableSet(keys);}catch(Exception ignored){}
        http=api.http.newBuilder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS).callTimeout(5,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    }
    boolean available() { return available; }
    synchronized boolean refreshDiscovery() { probeAt=0; return discover(); }
    synchronized boolean discover() {
        long now=SystemClock.elapsedRealtime(); if(probeAt!=0&&now-probeAt<(available?900000:30000)) return available;
        probeAt=Math.max(1,now);
        try { JSONObject response=call("GET","/capabilities",null); available=response.optBoolean("available")&&response.optInt("apiVersion")==1;
            JSONObject tags=Json.object(response,"imageBadges");coverTagsSupported=tags.optInt("version")==1;coverTagFilter=tags.optBoolean("enabled")&&tags.optInt("version")==1?((tags.optBoolean("dvd")?1:0)|(tags.optBoolean("surround")?2:0)|(tags.optBoolean("hdr")?4:0)):0;
            extendedTags=tags.optInt("labelVersion")==1;Set<String> keys=new HashSet<>();JSONArray configured=Json.array(tags,"tags");if(tags.optBoolean("enabled")) for(int i=0;i<configured.length();i++) if(CoverTagLabels.KEYS.contains(configured.optString(i))) keys.add(configured.optString(i));coverLabelKeys=Collections.unmodifiableSet(keys);
            JSONArray features=Json.array(response,"features"); scopedResume=false; resumeVisibility=false; incrementalCache=false; serverProviders=false;
            for(int index=0;index<features.length();index++) { scopedResume|="scopedResume".equals(features.optString(index)); resumeVisibility|="resumeVisibility".equals(features.optString(index)); incrementalCache|="incrementalCache".equals(features.optString(index)); serverProviders|="serverProviders".equals(features.optString(index)); }
        }
        catch(Exception e) { if(e instanceof Api.Failure&&(((Api.Failure)e).status==404||((Api.Failure)e).status==401||((Api.Failure)e).status==403)) available=false; }
        library.companionAvailable=available; Runnable changed=coverTagsChanged;if(changed!=null) changed.run();
        outbox.edit().putBoolean("extendedTags",extendedTags).putString("coverLabelKeys",new JSONArray(coverLabelKeys).toString()).putBoolean("coverTagsSupported",coverTagsSupported).putInt("coverTagFilter",coverTagFilter).putBoolean("available",available).putBoolean("scopedResume",scopedResume).putBoolean("resumeVisibility",resumeVisibility).putBoolean("incrementalCache",incrementalCache).putBoolean("serverProviders",serverProviders).apply(); return available;
    }
    boolean coverTagsSupported() { return available&&coverTagsSupported; }
    int coverTagFilter() { return available?coverTagFilter:0; }
    boolean extendedTags() {return available&&extendedTags;}
    Set<String> coverLabelKeys() {return coverLabelKeys;}
    boolean serverProviders() { return available&&serverProviders; }
    boolean incrementalCache() { return available&&incrementalCache; }
    JSONObject call(String method,String path,JSONObject body,Object... pairs) throws Exception {
        Request.Builder request=api.request(api.url(ROOT+path,pairs));
        if(method.equals("GET")) request.get(); else request.method(method,RequestBody.create(body==null?"{}":body.toString(),Api.JSON));
        try(Response response=http.newCall(request.build()).execute()) {
            if(!response.isSuccessful()) throw new Api.Failure(response.code());
            if(response.body()==null) throw new IOException("Missing Companion response");
            java.io.ByteArrayOutputStream value=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int count;
            java.io.InputStream input=response.body().byteStream();
            while((count=input.read(buffer))!=-1) { if(value.size()+count>262144) throw new IOException("Oversized Companion response"); value.write(buffer,0,count); }
            return new JSONObject(new String(value.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    JSONObject state(String item) throws Exception {
        if(!discover()) return null;
        JSONObject state=call("GET","/items/"+item,null); library.companionState(item,state); return state;
    }
    JSONObject rule(String item,JSONObject previous,JSONObject rule) throws Exception {
        JSONObject state=call("PUT","/items/"+item+"/rule",Json.obj("expectedRevision",previous.optLong("revision"),"rule",rule==null?JSONObject.NULL:rule));
        library.companionState(item,state); return state;
    }
    JSONObject action(String item,JSONObject previous,String action) throws Exception {
        JSONObject state=call("POST","/items/"+item+"/actions",Json.obj("expectedRevision",previous.optLong("revision"),"action",action));
        library.companionState(item,state); return state;
    }
    JSONObject global() throws Exception { return discover()?call("GET","/long-videos",null):null; }
    void global(JSONObject previous,JSONObject rule) throws Exception { call("PUT","/long-videos",Json.obj("expectedRevision",previous.optLong("revision"),"rule",rule)); }
    Prepared prepare(JSONObject item,long requested,boolean restart,boolean keepPosition) throws Exception {
        if(!Arrays.asList("Movie","Episode").contains(item.optString("Type"))||!discover()) return new Prepared(null,requested);
        flushItem(item.optString("Id"));
        JSONObject state;
        try { state=state(item.optString("Id")); }
        catch(Api.Failure e) { if(e.status!=404) throw e; available=false; library.companionAvailable=false; return new Prepared(null,requested); }
        if(!CompanionPolicy.active(state)) return new Prepared(null,requested);
        long position=restart?0:keepPosition?requested:state.optBoolean("hasResume")?Math.max(0,state.optLong("resumeTicks"))/10000:0;
        return new Prepared(new Session(item.optString("Id"),state),position);
    }
    private JSONObject frame(Session session,String event,JSONObject body) throws JSONException {
        return Json.obj("leaseId",session.lease,"expectedRevision",session.revision,"sequence",event.equals("Start")?0:session.sequence.incrementAndGet(),"event",event,"playback",new JSONObject(body.toString()));
    }
    // Called only by the existing serial reports executor, never on the main/UI thread.
    void report(Session session,String event,JSONObject body) throws Exception {
        JSONObject frame=frame(session,event,body); String key="lease-"+session.lease;
        JSONObject pending=new JSONObject(outbox.getString(key,"{}")); pending.put("item",session.item);
        pending.put(event.equals("Start")?"start":"latest",frame);
        if(!outbox.edit().putString(key,pending.toString()).commit()) throw new IOException("Could not persist Companion outbox");
        checkpoint(session,body,frame.optLong("sequence")); flush(key);
        JSONObject latest=library.companionState(session.item); if(latest!=null) session.state=latest;
    }
    private void checkpoint(Session session,JSONObject body,long sequence) throws JSONException {
        if(!session.lease.equals(session.state.optString("leaseId"))) return;
        JSONObject state=new JSONObject(session.state.toString()); long position=Math.max(0,body.optLong("PositionTicks")),duration=state.optLong("runtimeTicks");
        // A manual action/newer lease in the local database is a fence, even while offline.
        JSONObject current=library.companionState(session.item); if(current!=null&&current.optLong("revision")>state.optLong("revision")) return;
        JSONObject rule=Json.object(state,"effectiveRule");
        state.put("leaseId",session.lease); state.put("sequence",sequence);
        state.put("updatedAt",java.time.Instant.now().toString());
        if(rule.optBoolean("persistentResume",true)&&!state.optBoolean("manualFence")) {
            state.put("resumeTicks",position); state.put("hasResume",position>0&&(duration<=0||position<duration-10000000));
            if(!rule.optString("mode","Default").equals("Default")) state.put("played",state.optBoolean("played")||CompanionPolicy.completes(rule,position,duration));
        }
        library.companionState(session.item,state);
    }
    private boolean send(String item,JSONObject frame) throws Exception {
        JSONObject response;
        try { response=call("POST","/items/"+item+"/playback",frame); }
        catch(Api.Failure e) { if(e.status==409) return false; throw e; }
        JSONObject state=response.optJSONObject("state"); if(state!=null) library.companionState(item,state);
        String reason=response.optString("reason",""); return !reason.equals("superseded")&&!reason.equals("stopped")&&!reason.equals("disabled");
    }
    private void flush(String key) throws Exception {
        String raw=outbox.getString(key,null); if(raw==null) return;
        JSONObject pending=new JSONObject(raw); String item=pending.optString("item");
        if(pending.optJSONObject("start")!=null) {
            if(!send(item,pending.getJSONObject("start"))) { outbox.edit().remove(key).commit(); return; }
            pending.remove("start"); if(!outbox.edit().putString(key,pending.toString()).commit()) throw new IOException("Outbox update failed");
        }
        if(pending.optJSONObject("latest")!=null) { send(item,pending.getJSONObject("latest")); pending.remove("latest"); }
        if(!outbox.edit().remove(key).commit()) throw new IOException("Outbox cleanup failed");
    }
    void flushItem(String item) throws Exception {
        for(Map.Entry<String,?> entry:outbox.getAll().entrySet()) if(entry.getKey().startsWith("lease-")) {
            JSONObject pending=new JSONObject(String.valueOf(entry.getValue())); if(item.equals(pending.optString("item"))) flush(entry.getKey());
        }
    }
    void replay() {
        List<String> leases=new ArrayList<>();for(String key:outbox.getAll().keySet()) if(key.startsWith("lease-")) leases.add(key);
        // Session checks/playback keep normal discovery. An empty outbox has no work
        // to replay and must not introduce a competing startup capabilities request.
        if(leases.isEmpty()||!discover()) return;
        for(String key:leases) try { flush(key); } catch(Exception ignored) { return; }
    }
    List<JSONObject> mergeResume(List<JSONObject> nativeItems,int limit) throws Exception {
        return mergeResume(nativeItems,limit,null,null);
    }
    List<JSONObject> mergeResume(List<JSONObject> nativeItems,int limit,String parent,String type) throws Exception {
        if(!discover()) return nativeItems;
        // Older plugins cannot prove scope or exclusions; never substitute their global list for a selected library.
        if(parent!=null&&!scopedResume||parent==null&&!resumeVisibility) return nativeItems;
        LinkedHashSet<String> known=new LinkedHashSet<>(); for(JSONObject item:nativeItems) if(CompanionUi.media(item)) known.add(item.optString("Id"));
        List<JSONObject> cached=library.resumable(null,null,100,0);
        for(JSONObject item:cached) if(CompanionUi.media(item)&&known.size()<100) known.add(item.optString("Id"));
        JSONObject response=call("GET","/resume",null,"limit",limit,"knownItems",known.isEmpty()?null:String.join(",",known),"parentId",parent);
        if(parent!=null&&!parent.replace("-","").equalsIgnoreCase(response.optString("parentId").replace("-",""))) throw new IOException("Companion returned a different library scope");
        JSONArray states=Json.array(response,"states");
        library.mergeCompanionStates(states);
        JSONArray rows=Json.array(response,"items");
        LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i); if(row==null) continue; String id=row.optString("itemId"); JSONObject state=row.optJSONObject("state"); if(id.isEmpty()||state==null) continue;
            library.companionState(id,state); JSONObject item=library.item(id); if(item==null) { item=api.item(id); library.put(item); }
            merged.put(id,CompanionPolicy.decorate(item,state));
        }
        Set<String> ids=new LinkedHashSet<>();for(JSONObject item:nativeItems) ids.add(item.optString("Id"));if(parent==null) for(JSONObject item:cached) ids.add(item.optString("Id"));
        Map<String,JSONObject> current=library.itemsById(ids);
        List<JSONObject> missing=new ArrayList<>();for(JSONObject item:nativeItems) if(!current.containsKey(item.optString("Id"))) missing.add(item);
        Map<String,JSONObject> fallback=new HashMap<>();for(JSONObject item:library.withCompanionItems(missing)) fallback.putIfAbsent(item.optString("Id"),item);
        for(JSONObject item:nativeItems) { String id=item.optString("Id");merged.putIfAbsent(id,current.containsKey(id)?current.get(id):fallback.get(id)); }
        if(parent==null) for(JSONObject item:cached) { JSONObject fresh=current.get(item.optString("Id")); if(fresh!=null&&Json.inContinue(fresh)) merged.putIfAbsent(item.optString("Id"),fresh); }
        List<JSONObject> result=new ArrayList<>(merged.values()); result.removeIf(item->type!=null&&!type.equals(item.optString("Type"))||!(parent==null?Json.inContinue(item):Json.canResume(item)));
        return result.subList(0,Math.min(limit,result.size()));
    }
}
