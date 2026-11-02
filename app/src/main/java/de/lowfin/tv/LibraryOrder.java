package de.lowfin.tv;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** Native Jellyfin per-user order; never writes a partial/default user configuration. */
final class LibraryOrder {
    static final class Conflict extends IOException {final int label;Conflict(int label) {super("Library order conflict");this.label=label;}}
    static final class Snapshot {
        final JSONObject configuration;final List<JSONObject> views;final List<OrderEditor.Item> items;final List<String> order;
        Snapshot(JSONObject configuration,List<JSONObject> views) {
            this.configuration=configuration;this.views=views;order=ids(Json.array(configuration,"OrderedViews"));items=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(JSONObject view:views) {String id=normalize(view.optString("Id"));if(!id.isEmpty()&&!"livetv".equals(view.optString("CollectionType"))&&seen.add(id))items.add(new OrderEditor.Item(id,view.optString("Name")));}
        }
        List<String> visible() {List<String> result=new ArrayList<>();for(OrderEditor.Item item:items)result.add(item.id);return result;}
    }
    static Snapshot load(Api api) throws Exception {
        api=bounded(api);
        JSONObject user=api.get("/Users/"+api.account.user);if(!normalize(user.optString("Id")).equals(normalize(api.account.user)))throw new IOException("Unexpected user");
        JSONObject configuration=user.optJSONObject("Configuration");if(configuration==null||!(configuration.opt("OrderedViews") instanceof JSONArray))throw new IOException("Missing user configuration");
        JSONArray data=Json.array(api.get("/Users/"+api.account.user+"/Views"),"Items");List<JSONObject> views=new ArrayList<>();for(int i=0;i<data.length();i++)if(data.optJSONObject(i)!=null)views.add(data.optJSONObject(i));
        return new Snapshot(new JSONObject(configuration.toString()),views);
    }
    static Snapshot save(Api api,Snapshot baseline,List<String> desired) throws Exception {
        OrderSequence.validate(baseline.visible(),desired);Snapshot current=load(api);
        if(!new HashSet<>(current.visible()).equals(new HashSet<>(baseline.visible())))throw new Conflict(R.string.order_libraries_changed);
        if(current.visible().equals(desired))return current;
        if(!current.order.equals(baseline.order))throw new Conflict(R.string.order_conflict);
        List<String> merged=OrderSequence.merge(current.order,current.visible(),desired);JSONArray wire=new JSONArray();for(String id:merged)wire.put(wireId(id));
        JSONObject configuration=new JSONObject(current.configuration.toString());configuration.put("OrderedViews",wire);
        Api transport=bounded(api);
        try {transport.post("/Users/Configuration",configuration,"userId",api.account.user);}
        catch(Api.Failure failure) {if(failure.status!=404&&failure.status!=405)throw failure;transport.post("/Users/"+api.account.user+"/Configuration",configuration);}
        Snapshot verified=load(api);if(!verified.order.equals(merged)||!verified.visible().equals(desired))throw new Conflict(R.string.order_conflict);return verified;
    }
    static String normalize(String id) {String value=id.toLowerCase(Locale.ROOT);return value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")?value.replace("-",""):value;}
    static String wireId(String id) {return id.matches("[a-f0-9]{32}")?id.substring(0,8)+"-"+id.substring(8,12)+"-"+id.substring(12,16)+"-"+id.substring(16,20)+"-"+id.substring(20):id;}
    private static List<String> ids(JSONArray data) {List<String> result=new ArrayList<>();for(int i=0;i<data.length();i++)if(!data.optString(i).isEmpty())result.add(normalize(data.optString(i)));return result;}
    private static Api bounded(Api source) {Api api=new Api(source.account,source.http.newBuilder().readTimeout(15,java.util.concurrent.TimeUnit.SECONDS).callTimeout(20,java.util.concurrent.TimeUnit.SECONDS).build());api.diagnostics=source.diagnostics;return api;}
}
