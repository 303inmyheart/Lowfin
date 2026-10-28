package de.lowfin.tv;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Account-scoped choices; an absent preference means all libraries, [] means none. */
final class ChannelLibraries {
    static String key(Account a,String type) { return "channel-libraries-"+a.scope()+"-"+type; }
    static List<String> selection(SharedPreferences prefs,Account a,String type) {
        if(!prefs.contains(key(a,type))) return null;
        List<String> ids=new ArrayList<>(); try { JSONArray data=new JSONArray(prefs.getString(key(a,type),"[]")); for(int i=0;i<data.length();i++) if(!ids.contains(data.optString(i))) ids.add(data.optString(i)); } catch(Exception ignored) {}
        return ids;
    }
    static List<String> libraries(Library db,SharedPreferences prefs,Account a,String type) {
        List<String> ids=selection(prefs,a,type); if(ids!=null) return ids;
        ids=new ArrayList<>(); try { JSONArray views=new JSONArray(db.state("views")); for(int i=0;i<views.length();i++) { JSONObject v=views.optJSONObject(i); if(v!=null&&!v.optString("CollectionType").equals("livetv")) ids.add(v.optString("Id")); } } catch(Exception ignored) {}
        return ids;
    }
    static List<JSONObject> cached(Library db,SharedPreferences prefs,Account a,String type) {
        List<String> ids=libraries(db,prefs,a,type); List<List<JSONObject>> groups=new ArrayList<>();
        if(ids.isEmpty()&&selection(prefs,a,type)==null) return db.query("type=?",new String[]{type},"stamp DESC,name",24,0);
        List<String> keys=new ArrayList<>();for(String id:ids) keys.add("tv-library-"+type+"-"+id);
        Map<String,String> snapshots=db.states(keys);
        for(String id:ids) { try { String saved=snapshots.getOrDefault("tv-library-"+type+"-"+id,""); List<JSONObject> items=new ArrayList<>(); if(!saved.isEmpty()) { JSONArray data=new JSONArray(saved); for(int i=0;i<data.length();i++) { JSONObject item=data.optJSONObject(i); if(item!=null&&type.equals(item.optString("Type"))) items.add(item); } } else items=db.browse(type,id,null,0,24); groups.add(items); } catch(Exception ignored) {} }
        return mix(groups,24);
    }
    static List<JSONObject> mix(List<List<JSONObject>> groups,int limit) {
        List<JSONObject> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(int position=0;result.size()<limit;position++) { boolean found=false; for(List<JSONObject> group:groups) if(position<group.size()) { found=true; JSONObject item=group.get(position); if(seen.add(item.optString("Id"))) result.add(item); if(result.size()==limit) break; } if(!found) break; }
        return result;
    }
}
