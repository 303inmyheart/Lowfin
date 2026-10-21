package de.lowfin.tv;

import androidx.recyclerview.widget.DiffUtil;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Iterator;

/** AndroidX runs nontrivial snapshot comparisons on its shared background executor. */
final class MediaDiff {
    private static final String[] TEXT_FIELDS={"Name","Type","SeriesName","ProductionYear","RunTimeTicks","ParentIndexNumber","IndexNumber","Overview","Wide","ChannelNumber","PrimaryImageAspectRatio","SeriesId","SeriesPrimaryImageTag","ParentBackdropItemId"};
    private static final String[] JSON_FIELDS={"ImageTags","BackdropImageTags","ParentBackdropImageTags","Genres","ProviderIds"};
    /** Home shelves compare only visible/focus metadata, without serializing chapters and streams. */
    static boolean presentationEquals(JSONObject before,JSONObject after) {
        if(before==after) return true;
        for(String field:TEXT_FIELDS)
            if(!Json.str(before,field).equals(Json.str(after,field))) return false;
        for(String field:JSON_FIELDS)
            if(!jsonEquals(before.opt(field),after.opt(field))) return false;
        return Json.position(before)==Json.position(after)&&Json.played(before)==Json.played(after)&&Json.object(before,"UserData").optBoolean("IsFavorite")==Json.object(after,"UserData").optBoolean("IsFavorite");
    }
    /** Compare complete snapshots without materializing large serialized DTO strings. */
    static boolean jsonEquals(Object before,Object after) {
        if(before==after) return true;
        if(before==null||after==null) return false;
        if(before instanceof JSONObject&&after instanceof JSONObject) {
            JSONObject left=(JSONObject)before,right=(JSONObject)after;
            if(left.length()!=right.length()) return false;
            Iterator<String> keys=left.keys();
            while(keys.hasNext()) { String key=keys.next(); if(!right.has(key)||!jsonEquals(left.opt(key),right.opt(key))) return false; }
            return true;
        }
        if(before instanceof JSONArray&&after instanceof JSONArray) {
            JSONArray left=(JSONArray)before,right=(JSONArray)after;
            if(left.length()!=right.length()) return false;
            for(int i=0;i<left.length();i++) if(!jsonEquals(left.opt(i),right.opt(i))) return false;
            return true;
        }
        if(before instanceof Number&&after instanceof Number) {
            try { return JSONObject.numberToString((Number)before).equals(JSONObject.numberToString((Number)after)); }
            catch(org.json.JSONException invalid) { return false; }
        }
        return before.equals(after);
    }
    static final DiffUtil.ItemCallback<JSONObject> CALLBACK=new DiffUtil.ItemCallback<JSONObject>() {
        @Override public boolean areItemsTheSame(JSONObject before,JSONObject after) { return before.optString("Id").equals(after.optString("Id")); }
        @Override public boolean areContentsTheSame(JSONObject before,JSONObject after) { return jsonEquals(before,after); }
    };
}
