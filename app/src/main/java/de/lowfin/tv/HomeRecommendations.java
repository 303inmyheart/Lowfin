package de.lowfin.tv;

import org.json.*;
import java.util.*;

/** Five server recommendations based strictly on watched movies, never people or liked-only rows. */
final class HomeRecommendations {
    static final int ROWS=5;
    static final int LIMIT=24;
    static final String FIELDS="Overview,Genres,ProviderIds,ParentId,PrimaryImageAspectRatio";
    static List<JSONObject> select(JSONArray buckets) {
        List<JSONObject> result=new ArrayList<>(); Set<String> baselines=new HashSet<>();
        for(int i=0;i<buckets.length()&&result.size()<ROWS;i++) {
            JSONObject bucket=buckets.optJSONObject(i); if(bucket==null) continue;
            String type=bucket.optString("RecommendationType"),name=bucket.optString("BaselineItemName").trim();
            if(!type.equals("SimilarToRecentlyPlayed")) continue;
            String key=name.toLowerCase(Locale.ROOT); if(name.isEmpty()||baselines.contains(key)) continue;
            JSONArray items=movies(Json.array(bucket,"Items"),null); if(items.length()==0) continue;
            baselines.add(key); result.add(Json.obj("BaselineItemName",name,"Items",items,"RecommendationType",type));
        }
        return result;
    }
    static List<JSONObject> load(Api api) throws Exception {
        List<JSONObject> rows; Exception failure=null;
        try { rows=select(api.getArray("/Movies/Recommendations","UserId",api.account.user,"CategoryLimit",8,"ItemLimit",LIMIT,"Fields",FIELDS)); }
        catch(Exception e) { rows=new ArrayList<>(); failure=e; }
        if(rows.size()==ROWS) return rows;
        // Some servers expose fewer recently-played categories. Ask Jellyfin's similar-items
        // endpoint for other watched films rather than inventing local recommendations.
        try {
            JSONArray watched=Json.array(api.get("/Users/"+api.account.user+"/Items","Recursive",true,"IncludeItemTypes","Movie","IsPlayed",true,"SortBy","DatePlayed","SortOrder","Descending","Limit",12,"EnableUserData",true),"Items");
            Set<String> used=new HashSet<>(); for(JSONObject row:rows) used.add(row.optString("BaselineItemName").toLowerCase(Locale.ROOT));
            int attempts=0;
            for(int i=0;i<watched.length()&&rows.size()<ROWS;i++) {
                JSONObject film=watched.optJSONObject(i); if(film==null||!film.optString("Type").equals("Movie")||!Json.object(film,"UserData").optBoolean("Played")) continue;
                String name=film.optString("Name").trim(),id=film.optString("Id"); if(name.isEmpty()||id.isEmpty()||used.contains(name.toLowerCase(Locale.ROOT))) continue;
                if(attempts++>=8) break;
                try {
                    JSONArray items=movies(Json.array(api.get("/Items/"+id+"/Similar","UserId",api.account.user,"Limit",LIMIT,"Fields",FIELDS),"Items"),id);
                    if(items.length()>0) { rows.add(Json.obj("BaselineItemName",name,"Items",items,"RecommendationType","SimilarToRecentlyPlayed")); used.add(name.toLowerCase(Locale.ROOT)); }
                } catch(Exception e) { failure=e; }
            }
        } catch(Exception e) { if(rows.isEmpty()) throw e; }
        if(rows.isEmpty()&&failure!=null) throw failure;
        return rows;
    }
    private static JSONArray movies(JSONArray data,String baselineId) {
        JSONArray result=new JSONArray(); Set<String> seen=new HashSet<>();
        for(int i=0;i<data.length()&&result.length()<LIMIT;i++) {
            JSONObject item=data.optJSONObject(i); if(item==null||!item.optString("Type").equals("Movie")) continue;
            String id=item.optString("Id"); if(id.isEmpty()||id.equals(baselineId)||!seen.add(id)) continue;
            result.put(item);
        }
        return result;
    }
    static String title(JSONObject row) { return I18n.text("Weil du „")+row.optString("BaselineItemName")+I18n.text("“ gesehen hast"); }
}
