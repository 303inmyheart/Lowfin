package de.lowfin.tv;

import java.util.List;
import org.json.JSONObject;

/** Only labels, ordering and collection routing affect sidebar buttons. Artwork remains independently refreshable. */
final class SidebarViews {
    private static final String[] BUTTON_FIELDS={"Id","Name","CollectionType"};
    static boolean sameButtons(List<JSONObject> before,List<JSONObject> after) {
        if(before.size()!=after.size())return false;
        for(int i=0;i<before.size();i++)for(String field:BUTTON_FIELDS)if(!before.get(i).optString(field).equals(after.get(i).optString(field)))return false;
        return true;
    }
    static boolean sameData(List<JSONObject> before,List<JSONObject> after) {
        if(before.size()!=after.size())return false;for(int i=0;i<before.size();i++)if(!MediaDiff.jsonEquals(before.get(i),after.get(i)))return false;return true;
    }
}
