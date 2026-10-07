package de.lowfin.tv;

import org.json.JSONObject;

/** Choose real artwork before deciding its frame; never put a poster in a thumbnail frame. */
final class Artwork {
    static String type(JSONObject item,boolean wide) {
        JSONObject tags=Json.object(item,"ImageTags");
        if(wide&&!Json.str(tags,"Thumb").isEmpty()) return "Thumb";
        if(wide&&!"Episode".equals(item.optString("Type"))&&Json.array(item,"BackdropImageTags").length()>0) return "Backdrop";
        return "Primary";
    }
    static JSONObject backdrop(JSONObject item) {
        org.json.JSONArray tags=Json.array(item,"BackdropImageTags"); String id=item.optString("Id");
        if(tags.length()==0) { tags=Json.array(item,"ParentBackdropImageTags"); id=Json.str(item,"ParentBackdropItemId"); }
        return tags.length()==0||id.isEmpty()?null:Json.obj("Id",id,"Type","Movie","BackdropImageTags",tags);
    }
    static boolean crop(JSONObject item,boolean wide) { return !item.optString("Type").equals("TvChannel"); }
    static double ratio(JSONObject item,boolean wide) {
        String kind=item.optString("Type");
        return kind.equals("Episode")||kind.equals("Video")||kind.equals("TvChannel")?16.0/9:2.0/3;
    }
}
