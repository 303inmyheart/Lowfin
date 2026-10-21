package de.lowfin.tv;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Resolves public trailer media separately from the authenticated Jellyfin connection. */
final class YouTubeStreams {
    static final String USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";
    private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    private static volatile String visitor;
    private final OkHttpClient client;
    YouTubeStreams() { this(new OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS).callTimeout(5,TimeUnit.SECONDS).build()); }
    YouTubeStreams(OkHttpClient client) { this.client=client; }
    String resolve(String id) throws IOException {return resolve(id,720);}
    String resolve(String id,int height) throws IOException {
        if(id==null||!id.matches("[A-Za-z0-9_-]{11}")) throw new IOException("Invalid trailer ID");
        for(boolean vision:new boolean[]{true,false}) {
            try {
                String sent=vision?visitor:null; JSONObject response=request(id,vision,sent);
                String fresh=Json.object(response,"responseContext").optString("visitorData","");
                if(vision&&!"OK".equals(Json.object(response,"playabilityStatus").optString("status"))&&!fresh.isEmpty()&&!fresh.equals(sent)) { visitor=fresh; response=request(id,true,fresh); }
                String url=mediaUrl(response,height); if(url!=null) return url;
            } catch(IOException|JSONException ignored) { }
        }
        throw new IOException("Trailer stream unavailable");
    }
    private JSONObject request(String id,boolean vision,String visitorId) throws IOException,JSONException {
        String name=vision?"VISIONOS":"ANDROID",version=vision?"1.02":"20.10.41";
        JSONObject identity=Json.obj("clientName",name,"clientVersion",version,"hl","en","gl","US","deviceMake",vision?"Apple":"Google","deviceModel",vision?"RealityDevice17,1":"Pixel 5","osName",vision?"visionOS":"Android","osVersion",vision?"26.5.23O471":"11");
        if(vision&&visitorId!=null) identity.put("visitorData",visitorId);
        if(!vision) { identity.put("platform","MOBILE"); identity.put("androidSdkVersion",30); }
        JSONObject body=Json.obj("videoId",id,"context",Json.obj("client",identity),"contentCheckOk",true,"racyCheckOk",true);
        Request.Builder request=new Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent",vision?"Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15":"com.google.android.youtube/20.10.41 (Linux; U; Android 11) gzip")
            .header("Origin","https://www.youtube.com").header("Referer","https://www.youtube.com/")
            .header("X-YouTube-Client-Name",vision?"101":"3").header("X-YouTube-Client-Version",version).post(RequestBody.create(body.toString(),JSON));
        if(vision&&visitorId!=null) request.header("X-Goog-Visitor-Id",visitorId);
        try(Response response=client.newCall(request.build()).execute()) { if(!response.isSuccessful()||response.body()==null) throw new IOException("Trailer lookup failed"); return new JSONObject(response.body().string()); }
    }
    static String mediaUrl(JSONObject response) {return mediaUrl(response,720);}
    static String mediaUrl(JSONObject response,int height) {
        JSONObject status=Json.object(response,"playabilityStatus"); if(!"OK".equals(status.optString("status"))) return null;
        JSONObject data=Json.object(response,"streamingData"); String manifest=data.optString("hlsManifestUrl"); if(allowed(manifest)) return manifest;
        JSONArray formats=Json.array(data,"formats"); String chosen=null; int best=Integer.MIN_VALUE;
        for(int i=0;i<formats.length();i++) { JSONObject format=formats.optJSONObject(i); if(format==null) continue;
            String url=format.optString("url"),mime=format.optString("mimeType"); if(!allowed(url)||!mime.contains("video/")||!(mime.contains("mp4a")||mime.contains("opus")||mime.contains("vorbis"))) continue;
            if(height>0&&format.optInt("height",480)>height) continue;
            int score=(mime.contains("avc1")?4000:0)+(mime.contains("video/mp4")?2000:0)-Math.abs(format.optInt("height",480)-(height==0?2160:height));
            if(score>best) { best=score; chosen=url; }
        } return chosen;
    }
    static boolean allowed(String address) { HttpUrl url=HttpUrl.parse(address); return url!=null&&url.isHttps()&&url.username().isEmpty()&&url.password().isEmpty()&&(url.host().endsWith(".googlevideo.com")||url.host().endsWith(".youtube.com")); }
}
