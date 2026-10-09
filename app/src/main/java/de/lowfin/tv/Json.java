package de.lowfin.tv;

import org.json.*;

final class Json {
    static JSONObject obj(Object... pairs) {
        JSONObject out = new JSONObject();
        try { for (int i=0; i<pairs.length; i+=2) out.put((String)pairs[i], pairs[i+1]); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
        return out;
    }
    static JSONArray arr(Object... values) { JSONArray a = new JSONArray(); for (Object v:values) a.put(v); return a; }
    static JSONObject object(JSONObject j,String key) { JSONObject o=j.optJSONObject(key); return o==null?new JSONObject():o; }
    static JSONArray array(JSONObject j,String key) { JSONArray a=j.optJSONArray(key); return a==null?new JSONArray():a; }
    static String str(JSONObject j,String key) { return j.isNull(key)?"":j.optString(key,""); }
    static long position(JSONObject item) { return object(item,"UserData").optLong("PlaybackPositionTicks")/10000; }
    static String label(JSONObject item) {
        String name=item.optString("Name", I18n.text("Unbenannt"));
        if ("Episode".equals(item.optString("Type"))) return "S"+item.optInt("ParentIndexNumber")+" · E"+item.optInt("IndexNumber")+"  "+name;
        return name;
    }
    static String genres(JSONObject item) {
        java.util.Set<String> names=new java.util.LinkedHashSet<>(); JSONArray genres=array(item,"Genres");
        for(int i=0;i<genres.length()&&names.size()<3;i++) { Object value=genres.opt(i); if(value instanceof String&&!((String)value).trim().isEmpty()) names.add(((String)value).trim()); }
        return String.join(" · ",names);
    }
}
