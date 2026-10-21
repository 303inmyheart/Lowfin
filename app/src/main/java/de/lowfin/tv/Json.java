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
    static java.util.List<String> coverTagIds(JSONObject item) {
        java.util.LinkedHashSet<String> ids=new java.util.LinkedHashSet<>();
        for(String field:new String[]{"Id","SeriesId","SeasonId","ParentId"}) {
            if(!field.equals("Id")&&!item.optString("Type").equals("Episode")&&!item.optString("Type").equals("Season")) continue;
            String id=item.optString(field).replace("-","").toLowerCase(java.util.Locale.ROOT);if(id.matches("[0-9a-f]{32}")) ids.add(id);
        }
        return new java.util.ArrayList<>(ids);
    }
    static long position(JSONObject item) { JSONObject companion=item.optJSONObject("LowfinCompanion"); return CompanionPolicy.active(companion)?(companion.optBoolean("hasResume")?Math.max(0,companion.optLong("resumeTicks"))/10000:0):object(item,"UserData").optLong("PlaybackPositionTicks")/10000; }
    static boolean played(JSONObject item) { JSONObject companion=item.optJSONObject("LowfinCompanion"); return CompanionPolicy.active(companion)?companion.optBoolean("played"):object(item,"UserData").optBoolean("Played"); }
    static boolean canResume(JSONObject item) { return position(item)>0&&(CompanionPolicy.active(item.optJSONObject("LowfinCompanion"))||!played(item)); }
    static boolean inContinue(JSONObject item) { JSONObject state=item.optJSONObject("LowfinCompanion"); return canResume(item)&&(state==null||state.optBoolean("resumeVisible",true)); }
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
