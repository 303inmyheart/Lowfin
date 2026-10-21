package de.lowfin.tv;

import android.content.SharedPreferences;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only public API v2 integration. Never substitutes starts for completed plays. */
final class Tracearr {
    interface Secret { String get(String name); }
    static final class Failure extends IOException {
        final int status;
        Failure(int status) { super("Tracearr HTTP "+status); this.status=status; }
    }
    final Api jellyfin;
    final SharedPreferences settings;
    final Secret secret;
    final OkHttpClient http;
    java.util.function.Supplier<CompanionApi> companion=()->null;
    private String identity="",identityServer="";
    private final Map<String,JSONObject> summaries=new LinkedHashMap<>();
    Tracearr(Api api,SharedPreferences settings,Secret secret) {
        jellyfin=api; this.settings=settings; this.secret=secret;
        http=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    }
    String key(String name) { return "tracearr-"+jellyfin.account.scope()+"-"+name; }
    String url() { String imported=secret.get("url"); return imported.isEmpty()?settings.getString(key("url"),""):imported; }
    String server() { String imported=secret.get("server_id"); return imported.isEmpty()?settings.getString(key("server"),""):imported; }
    boolean enabled() { return settings.getBoolean(key("enabled"),false); }
    static String validUrl(String value) {
        HttpUrl parsed=HttpUrl.parse(value.trim());
        if(parsed==null||parsed.host().isEmpty()||!parsed.username().isEmpty()||!parsed.password().isEmpty()||parsed.query()!=null||parsed.fragment()!=null) throw new IllegalArgumentException("Invalid URL");
        return parsed.toString().replaceAll("/+$","");
    }
    JSONObject get(String path,Object... pairs) throws Exception {
        String token=secret.get("token"); if(token.isEmpty()) throw new IOException("Missing token");
        HttpUrl.Builder builder=HttpUrl.get(validUrl(url())+"/api/v2/public"+path).newBuilder();
        for(int i=0;i<pairs.length;i+=2) if(pairs[i+1]!=null) builder.addQueryParameter((String)pairs[i],String.valueOf(pairs[i+1]));
        Request request=new Request.Builder().url(builder.build()).header("Authorization","Bearer "+token).build();
        try(Response response=http.newCall(request).execute()) {
            if(!response.isSuccessful()) throw new Failure(response.code());
            if(response.body()==null) throw new IOException("Missing response");
            return new JSONObject(response.body().string());
        }
    }
    JSONArray servers() throws Exception {
        JSONArray rows=get("/servers").optJSONArray("data"); if(rows==null) throw new IOException("Unsupported API");
        JSONArray found=new JSONArray(); for(int i=0;i<rows.length();i++) { JSONObject row=rows.optJSONObject(i); if(row!=null&&"jellyfin".equals(row.optString("server_type"))&&!row.optBoolean("historical")) found.put(row); }
        return found;
    }
    static String normalized(String id) { return id.replace("-","").toLowerCase(Locale.ROOT); }
    private String user() throws Exception {
        if(!server().isEmpty()&&server().equals(identityServer)&&!identity.isEmpty()) return identity;
        if(server().isEmpty()) throw new IOException("Select server");
        String cursor=null; Set<String> cursors=new HashSet<>(),matches=new HashSet<>();
        for(int page=0;page<100;page++) {
            JSONObject response=get("/users","pageSize",100,"cursor",cursor); JSONArray rows=response.optJSONArray("data"); if(rows==null) throw new IOException("Unsupported API");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i); if(row==null) continue; JSONArray accounts=Json.array(row,"accounts");
                for(int a=0;a<accounts.length();a++) { JSONObject account=accounts.optJSONObject(a);
                    if(account!=null&&server().equals(account.optString("server_id"))&&"jellyfin".equals(account.optString("server_type"))&&normalized(jellyfin.account.user).equals(normalized(account.optString("external_user_id")))) matches.add(row.optString("id"));
                }
            }
            cursor=cursor(response);
            if(cursor.isEmpty()) { if(matches.size()!=1||matches.contains("")) throw new IOException("Account mapping unavailable"); identity=matches.iterator().next(); identityServer=server(); return identity; }
            if(!cursors.add(cursor)) throw new IOException("Repeated cursor");
        }
        throw new IOException("Incomplete account list");
    }
    private static String cursor(JSONObject response) throws IOException { JSONObject meta=response.optJSONObject("meta"); if(meta==null||!meta.has("nextCursor")) throw new IOException("Missing pagination metadata"); return meta.isNull("nextCursor")?"":meta.optString("nextCursor",""); }
    void check() throws Exception { servers(); user(); }
    synchronized JSONObject summary(JSONObject item) throws Exception {
        CompanionApi bridge=companion.get(); if(bridge!=null&&bridge.serverProviders()) return jellyfin.get(CompanionApi.ROOT+"/items/"+item.optString("Id")+"/statistics");
        String id=item.optString("Id"),type=item.optString("Type");
        String cacheKey=server()+":"+id; JSONObject saved=summaries.get(cacheKey); if(saved!=null&&System.currentTimeMillis()-saved.optLong("stamp")<60000) return saved;
        boolean series="Series".equals(type); if(!series&&!"Episode".equals(type)&&!"Movie".equals(type)) throw new IOException("Unsupported item");
        String userId=user(),cursor=null; Set<String> cursors=new HashSet<>(),plays=new HashSet<>(); Map<String,Integer> episodes=new HashMap<>();
        Set<String> expected=new HashSet<>();
        if(series) {
            for(int start=0;start<100000;start+=200) {
                JSONObject response=jellyfin.get("/Shows/"+id+"/Episodes","UserId",jellyfin.account.user,"StartIndex",start,"Limit",200,"IsMissing",false);
                JSONArray rows=response.optJSONArray("Items"); if(rows==null) throw new IOException("Episodes unavailable");
                for(int i=0;i<rows.length();i++) { JSONObject episode=rows.optJSONObject(i); if(episode!=null&&!episode.optString("Id").isEmpty()) expected.add(normalized(episode.optString("Id"))); }
                if(rows.length()<200) break;
                if(start>=99800) throw new IOException("Incomplete episode list");
            }
            if(expected.isEmpty()) throw new IOException("No episodes");
        }
        long deadline=System.currentTimeMillis()+30000; boolean complete=false;
        for(int page=0;page<1000;page++) {
            if(System.currentTimeMillis()>deadline) throw new IOException("History timed out");
            JSONObject response=get("/history","server_id",server(),"user_id",userId,"rating_key",series?null:id,"media_type",series||"Episode".equals(type)?"episode":"movie","watched",true,"pageSize",100,"cursor",cursor);
            JSONArray rows=response.optJSONArray("data"); if(rows==null) throw new IOException("History unavailable");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i); if(row==null||!row.optBoolean("watched")||row.isNull("stopped_at")||row.optString("stopped_at").isEmpty()||!server().equals(row.optString("server_id"))) continue;
                String rating=normalized(row.optString("rating_key")),play=row.optString("id");
                if(play.isEmpty()||(!series&&!normalized(id).equals(rating))||(series&&!expected.contains(rating))||!plays.add(play)) continue;
                episodes.put(rating,episodes.getOrDefault(rating,0)+1);
            }
            cursor=cursor(response);
            if(cursor.isEmpty()) { complete=true; break; }
            if(!cursors.add(cursor)) throw new IOException("Repeated cursor");
        }
        if(!complete) throw new IOException("Incomplete history");
        int count=plays.size(); if(series) { count=Integer.MAX_VALUE; for(String episode:expected) count=Math.min(count,episodes.getOrDefault(episode,0)); }
        JSONObject result=Json.obj("count",count,"series",series,"episodes",expected.size(),"stamp",System.currentTimeMillis());
        summaries.put(cacheKey,result); if(summaries.size()>32) summaries.remove(summaries.keySet().iterator().next()); return result;
    }
}
