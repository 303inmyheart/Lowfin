package de.lowfin.tv;

import android.content.*;
import org.json.*;
import java.util.*;

/** A device-local watch list, separated from server favorites and scoped to each account. */
final class WatchLater {
    private final SharedPreferences prefs;
    WatchLater(Context context,Account account) { prefs=context.getSharedPreferences("watch-later-"+account.scope(),0); }
    String snapshot(String id) { return prefs.getString(id,""); }
    /** A saved entry is removed only for real playback completion, never just because it was added already watched. */
    boolean completed(JSONObject item,boolean ended,String expected) {
        if(!"Movie".equals(item.optString("Type"))||expected.isEmpty()) return false;
        synchronized(prefs) {
            String id=item.optString("Id");if(!expected.equals(snapshot(id))) return false;
            if(!ended) try { if(Json.played(new JSONObject(expected))||!Json.played(item)) return false; } catch(JSONException e) { return false; }
            prefs.edit().remove(id).apply();return true;
        }
    }
    boolean contains(String id) { return prefs.contains(id); }
    void set(JSONObject item,boolean added) {
        SharedPreferences.Editor editor=prefs.edit(); String id=item.optString("Id"); if(id.isEmpty()) return;
        synchronized(prefs) {
            if(added) try { JSONObject saved=new JSONObject(item.toString());saved.put("_LowfinWatchLaterEntry",UUID.randomUUID().toString());editor.putString(id,saved.toString()); } catch(JSONException e) { return; }
            else editor.remove(id); editor.apply();
        }
    }
    List<JSONObject> items() {
        List<JSONObject> result=new ArrayList<>();
        for(Object value:prefs.getAll().values()) if(value instanceof String) try { JSONObject item=new JSONObject((String)value);item.remove("_LowfinWatchLaterEntry");result.add(item); } catch(JSONException ignored) { }
        result.sort(Comparator.comparing(j->j.optString("SortName",j.optString("Name")),String.CASE_INSENSITIVE_ORDER)); return result;
    }
}
