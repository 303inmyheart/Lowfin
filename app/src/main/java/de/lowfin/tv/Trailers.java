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
    static String html(String id,String origin) {
        if(id==null||!id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException("Invalid YouTube ID");
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='strict-origin-when-cross-origin'>"
            +"<style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style></head><body><div id='player'></div>"
            +"<script>var player,lf={ready:false,state:-1,error:0,blocked:false};function onYouTubeIframeAPIReady(){player=new YT.Player('player',{width:'100%',height:'100%',videoId:"
            +JSONObject.quote(id)+",playerVars:{autoplay:1,playsinline:1,controls:1,fs:0,origin:"+JSONObject.quote(origin)+"},events:{onReady:function(e){lf.ready=true;e.target.playVideo();},onStateChange:function(e){lf.state=e.data;},onError:function(e){lf.error=e.data;},onAutoplayBlocked:function(){lf.blocked=true;}}});}"
            +"function lowfinToggle(){if(lf.ready){if(player.getPlayerState()===1)player.pauseVideo();else player.playVideo();}}"
            +"function lowfinSeek(delta){if(lf.ready)player.seekTo(Math.max(0,player.getCurrentTime()+delta),true);}"
            +"</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
    }
}
