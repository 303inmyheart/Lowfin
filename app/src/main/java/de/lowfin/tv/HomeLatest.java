package de.lowfin.tv;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Account-scoped recently-added shelves, using Jellyfin's own library grouping. */
final class HomeLatest {
    static final String PREFIX="latest:";
    static final int LIMIT=24;
    static String setting(Account account) {return "homeLatest-"+account.scope();}
    static String setting(Account account,String id) {return setting(account)+"-"+id;}
    static String cache(String id) {return "homeLatest-"+id;}
    static String label(JSONObject library) {return String.format(Locale.ROOT,I18n.resource(R.string.home_latest_row),library.optString("Name"));}
    static List<JSONObject> libraries(List<JSONObject> views) {
        LinkedHashMap<String,JSONObject> result=new LinkedHashMap<>();
        for(JSONObject view:views) {
            String id=view.optString("Id"),type=view.optString("CollectionType");
            if(id.isEmpty()||Arrays.asList("livetv","music","books","photos","playlists","boxsets","folders").contains(type))continue;
            result.putIfAbsent(id,view);
        }
        return new ArrayList<>(result.values());
    }
    static List<JSONObject> selected(SharedPreferences prefs,Account account,List<JSONObject> views) {
        if(!prefs.getBoolean(setting(account),true))return Collections.emptyList();
        List<JSONObject> result=new ArrayList<>();
        for(JSONObject view:libraries(views))if(prefs.getBoolean(setting(account,view.optString("Id")),true))result.add(view);
        return result;
    }
    static List<JSONObject> load(Api api,Library library,JSONObject view) throws Exception {
        Api limited=new Api(api.account,api.http.newBuilder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build());
        JSONArray rows=limited.getArray("/Users/"+api.account.user+"/Items/Latest","ParentId",view.getString("Id"),"Limit",LIMIT,"GroupItems",true,"IncludeItemTypes","Movie,Episode,Video","Fields",HomeRecommendations.FIELDS);
        Set<String> seriesIds=new LinkedHashSet<>();
        for(int i=0;i<rows.length();i++) {JSONObject item=rows.optJSONObject(i);if(item!=null&&(item.optString("Type").equals("Episode")||item.optString("Type").equals("Season"))&&!item.optString("SeriesId").isEmpty())seriesIds.add(item.optString("SeriesId"));}
        Map<String,JSONObject> parents=library.itemsById(seriesIds);List<String> missing=new ArrayList<>();
        for(String id:seriesIds)if(!parents.containsKey(id)||!parents.get(id).optString("Type").equals("Series"))missing.add(id);
        if(!missing.isEmpty())try {
            JSONArray fetched=Json.array(limited.get("/Users/"+api.account.user+"/Items","Ids",String.join(",",missing),"IncludeItemTypes","Series","Fields",HomeRecommendations.FIELDS),"Items");
            for(int i=0;i<fetched.length();i++) {JSONObject item=fetched.optJSONObject(i);if(item!=null&&item.optString("Type").equals("Series")&&seriesIds.contains(item.optString("Id")))parents.put(item.optString("Id"),item);}
        } catch(Exception unavailable) { /* Keep locally available series and movie entries. */ }
        LinkedHashMap<String,JSONObject> result=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject item=rows.optJSONObject(i);if(item==null)continue;
            String type=item.optString("Type");
            // Latest grouping uses ChildCount for the number of newly added
            // children, not the series' actual library total.
            if(type.equals("Series")) {item=new JSONObject(item.toString());item.remove("ChildCount");}
            // A single added episode may remain ungrouped in Jellyfin's response.
            // Present its series so the shelf retains posters and series ratings.
            if(type.equals("Episode")||type.equals("Season")) {
                String series=item.optString("SeriesId");if(series.isEmpty()||result.containsKey(series))continue;
                JSONObject parent=parents.get(series);if(parent==null||!parent.optString("Type").equals("Series"))continue;
                item=parent;type=item.optString("Type");
            }
            String id=item.optString("Id");
            if(!id.isEmpty()&&(type.equals("Movie")||type.equals("Series")||type.equals("Video")))result.putIfAbsent(id,item);
        }
        return new ArrayList<>(result.values());
    }
}
