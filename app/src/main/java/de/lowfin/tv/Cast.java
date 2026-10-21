package de.lowfin.tv;
import org.json.*;
import java.util.*;
final class Cast {
    static List<JSONObject> actors(JSONObject item) {
        List<JSONObject> result=new ArrayList<>(); Set<String> seen=new HashSet<>(); JSONArray people=Json.array(item,"People");
        for(int i=0;i<people.length();i++) {
            JSONObject person=people.optJSONObject(i); if(person==null) continue; String type=person.optString("Type"),id=person.optString("Id");
            if(id.isEmpty()||!("Actor".equalsIgnoreCase(type)||"GuestStar".equalsIgnoreCase(type))||!seen.add(id)) continue;
            result.add(Json.obj("Id",id,"Type","Person","Name",person.optString("Name"),"Role",person.optString("Role"),"ImageTags",Json.obj("Primary",person.optString("PrimaryImageTag"))));
        }
        return result;
    }
    static List<JSONObject> titles(JSONArray items) { List<JSONObject> out=new ArrayList<>(); for(int i=0;i<items.length();i++) { JSONObject j=items.optJSONObject(i); if(j!=null&&("Movie".equals(j.optString("Type"))||"Series".equals(j.optString("Type")))) out.add(j); } return out; }
}
