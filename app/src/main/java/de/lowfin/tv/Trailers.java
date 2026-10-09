package de.lowfin.tv;

import okhttp3.HttpUrl;
import org.json.*;
import java.util.*;

final class Trailers {
    static String youtubeId(String address) {
        HttpUrl url=HttpUrl.parse(address); if(url==null||!url.username().isEmpty()||!url.password().isEmpty()) return null;
        String host=url.host(),id=null; List<String> parts=url.pathSegments();
        if(host.equals("youtu.be")||host.equals("www.youtu.be")) id=parts.get(0);
        else if(Arrays.asList("youtube.com","www.youtube.com","m.youtube.com","music.youtube.com","youtube-nocookie.com","www.youtube-nocookie.com").contains(host)) {
            if(parts.get(0).equals("watch")) id=url.queryParameter("v");
            else if(parts.size()>1&&Arrays.asList("embed","shorts","live","v").contains(parts.get(0))) id=parts.get(1);
        }
        return id!=null&&id.matches("[A-Za-z0-9_-]{11}")?id:null;
    }
    static List<JSONObject> youtube(JSONObject item) {
        List<JSONObject> trailers=new ArrayList<>(); Set<String> seen=new HashSet<>(); JSONArray remote=Json.array(item,"RemoteTrailers");
        for(int i=0;i<remote.length();i++) { JSONObject link=remote.optJSONObject(i); if(link==null) continue; String id=youtubeId(link.optString("Url"));
            if(id!=null&&seen.add(id)) trailers.add(Json.obj("Id",id,"Name",link.optString("Name","Trailer "+(trailers.size()+1))));
        } return trailers;
    }
}
