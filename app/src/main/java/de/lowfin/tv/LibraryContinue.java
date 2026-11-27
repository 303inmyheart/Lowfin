package de.lowfin.tv;

import org.json.*;
import java.util.*;

/** Library-only progress shelves. Never reads or writes the home shelves' caches. */
final class LibraryContinue {
    private final Api api;
    private final Library library;
    private final CompanionApi companion;
    private final String parent;
    LibraryContinue(Api api,Library library,CompanionApi companion,String parent) {
        this.api=api;this.library=library;this.companion=companion;this.parent=parent;
    }
    private String key(boolean next,int offset,int limit) {
        return "library-continue-v1-"+(next?"next-":"resume-")+(parent==null?"all":parent)+"-"+offset+"-"+limit;
    }
    List<JSONObject> cached(boolean next,int offset,int limit) throws Exception {
        String saved=library.state(key(next,offset,limit));
        List<JSONObject> result=library.withCompanionItems(items(new JSONArray(saved.isEmpty()?"[]":saved)));
        if(!next) result.removeIf(item->!Json.canResume(item));
        return result;
    }
    List<JSONObject> load(boolean next,int offset,int limit) throws Exception {
        JSONObject response=next
            ?api.get("/Shows/NextUp","UserId",api.account.user,"ParentId",parent,"EnableResumable",false,"EnableTotalRecordCount",false,"StartIndex",offset,"Limit",limit,"Fields","Overview,ParentId,PrimaryImageAspectRatio")
            :api.get("/Users/"+api.account.user+"/Items","Recursive",true,"ParentId",parent,"IncludeItemTypes","Episode","Filters","IsResumable","SortBy","DatePlayed","SortOrder","Descending","StartIndex",offset,"Limit",limit,"Fields","Overview,ParentId,PrimaryImageAspectRatio");
        JSONArray values=Json.array(response,"Items");long stamp=System.currentTimeMillis();library.put(values,stamp);
        if(parent!=null) library.link(parent,values,stamp);
        List<JSONObject> result=items(values);
        if(!next&&offset==0&&companion!=null&&companion.discover()) result=companion.mergeResume(result,limit,parent,"Episode");
        else result=library.withCompanionItems(result);
        library.state(key(next,offset,limit),new JSONArray(result).toString());
        return result;
    }
    private static List<JSONObject> items(JSONArray values) {
        List<JSONObject> result=new ArrayList<>();
        for(int i=0;i<values.length();i++) {JSONObject item=values.optJSONObject(i);if(item!=null&&"Episode".equals(item.optString("Type")))result.add(item);}
        return result;
    }
}
