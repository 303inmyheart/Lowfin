package de.lowfin.tv;

import android.content.*;
import org.json.*;
import java.util.*;

/** A device-local watch list, separated from server favorites and scoped to each account. */
final class WatchLater {
    private final SharedPreferences prefs;
    WatchLater(Context context,Account account) { prefs=context.getSharedPreferences("watch-later-"+account.scope(),0); }
    boolean contains(String id) { return prefs.contains(id); }
    void set(JSONObject item,boolean added) {
        SharedPreferences.Editor editor=prefs.edit(); String id=item.optString("Id"); if(id.isEmpty()) return;
        if(added) editor.putString(id,item.toString()); else editor.remove(id); editor.apply();
    }
    List<JSONObject> items() {
        List<JSONObject> result=new ArrayList<>();
        for(Object value:prefs.getAll().values()) if(value instanceof String) try { result.add(new JSONObject((String)value)); } catch(JSONException ignored) { }
        result.sort(Comparator.comparing(j->j.optString("SortName",j.optString("Name")),String.CASE_INSENSITIVE_ORDER)); return result;
    }
}
