package de.lowfin.tv;

import android.content.*;
import android.os.*;
import okhttp3.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Small on-demand cache: independent HTTPS clients, ID matching, deduplication and backoff. */
final class Ratings {
    static final String[] IDS={"imdb","tomatoes","tomatoesaudience","metacritic","tmdb","trakt","letterboxd","mdblist"};
    static final String[] NAMES={"IMDb","Rotten Tomatoes · Kritiker","Rotten Tomatoes · Publikum","Metacritic","TMDB","Trakt","Letterboxd","MDBList"};
    private static final long TTL=Long.MAX_VALUE;
    private final SharedPreferences settings;
    private final Library library;
    private final Api jellyfin;
    private final Function<String,String> key;
    private final String omdb,mdb,tmdb;
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).followRedirects(false).build();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(24));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,List<Consumer<JSONObject>>> pending=new HashMap<>();
    private final LinkedHashMap<String,JSONObject> memory=new LinkedHashMap<>(32,.75f,true);
    private final Map<String,Long> blocked=new ConcurrentHashMap<>();
    volatile String status=I18n.text("API-Keys hinterlegen, um externe Bewertungen zu laden.");
    Ratings(Context c,Library db,Api api) { this(c.getSharedPreferences("settings",0),db,api,new RatingKeys(c)::get,"https://www.omdbapi.com/","https://api.mdblist.com/","https://api.themoviedb.org/3/"); }
    Ratings(SharedPreferences settings,Library db,Api api,Function<String,String> key,String omdb,String mdb,String tmdb) { this.settings=settings;library=db;jellyfin=api;this.key=key;this.omdb=omdb;this.mdb=mdb;this.tmdb=tmdb; }
    static boolean enabled(SharedPreferences settings,String source) { return settings.getBoolean("rating-show-"+source,true); }
    void clearMemory() { synchronized(pending) { memory.clear(); } blocked.clear(); }
    void close() { worker.shutdownNow(); http.dispatcher().cancelAll(); }
    void request(JSONObject item,Consumer<JSONObject> callback) {
        if(!item.optString("Type").equals("Movie")&&!item.optString("Type").equals("Series")) { callback.accept(new JSONObject()); return; }
        String id=item.optString("Id");
        synchronized(pending) {
            JSONObject cached=memory.get(id); if(cached!=null&&System.currentTimeMillis()-cached.optLong("time")<cached.optLong("ttl",TTL)) { callback.accept(Json.object(cached,"scores")); return; }
            if(pending.containsKey(id)) { pending.get(id).add(callback); return; }
            pending.put(id,new ArrayList<>(Collections.singletonList(callback)));
        }
        try { worker.execute(()->load(item,id)); } catch(RejectedExecutionException ignored) { complete(id,new JSONObject(),false); }
    }
    private void load(JSONObject original,String id) {
        JSONObject scores=new JSONObject(); boolean failed=false;
        try {
            Map<String,String> keys=new HashMap<>(); for(String p:new String[]{"omdb","mdblist","tmdb"}) keys.put(p,key.apply(p));
            String cache="ratings-"+id+"-"+fingerprint(keys.toString()+selection()); String saved=library.state(cache);
            JSONObject permanent=new JSONObject(library.state("ratings-values-"+id).isEmpty()?"{}":library.state("ratings-values-"+id)); merge(scores,Json.object(permanent,"scores")); JSONObject providers=Json.object(permanent,"providers");
            if(!saved.isEmpty()) { JSONObject entry=new JSONObject(saved); if((Json.object(entry,"scores").length()>0&&!entry.optBoolean("failed"))||System.currentTimeMillis()-entry.optLong("time")<entry.optLong("ttl",TTL)) { merge(scores,Json.object(entry,"scores")); library.state("ratings-values-"+id,Json.obj("scores",scores,"providers",providers).toString()); complete(id,scores,entry.optBoolean("failed")); return; } merge(scores,Json.object(entry,"scores")); }
            boolean configured=keys.values().stream().anyMatch(value->!value.isEmpty());
            JSONObject item=original,ids=Json.object(item,"ProviderIds");
            if(configured&&ids.length()==0) { item=jellyfin.item(id); library.put(item); ids=Json.object(item,"ProviderIds"); }
            String imdb=providerId(ids,"Imdb"),tmdbId=providerId(ids,"Tmdb"); boolean movie=item.optString("Type").equals("Movie");
            if(!keys.get("omdb").isEmpty()&&!imdb.isEmpty()&&needed("imdb","tomatoes","metacritic")&&!providers.optBoolean("omdb")) try {
                merge(scores,parseOmdb(fetch("omdb",HttpUrl.get(omdb).newBuilder().addQueryParameter("apikey",keys.get("omdb")).addQueryParameter("i",imdb).build(),null))); providers.put("omdb",true);
            } catch(Exception e) { failed=true; }
            if(!keys.get("mdblist").isEmpty()&&(!imdb.isEmpty()||!tmdbId.isEmpty())&&needed(IDS)&&!providers.optBoolean("mdblist")) try {
                String provider=!imdb.isEmpty()?"imdb":"tmdb",external=!imdb.isEmpty()?imdb:tmdbId;
                merge(scores,parseMdb(fetch("mdblist",HttpUrl.get(mdb+provider+"/"+(movie?"movie":"show")+"/"+external+"/").newBuilder().addQueryParameter("apikey",keys.get("mdblist")).build(),null))); providers.put("mdblist",true);
            } catch(Exception e) { failed=true; }
            if(!keys.get("tmdb").isEmpty()&&needed("tmdb")&&!providers.optBoolean("tmdb")&&(!tmdbId.isEmpty()||!imdb.isEmpty())) try {
                String secret=keys.get("tmdb"); JSONObject result;
                if(!tmdbId.isEmpty()) result=tmdbFetch((movie?"movie/":"tv/")+tmdbId,secret);
                else { JSONObject found=tmdbFetch("find/"+imdb+"?external_source=imdb_id",secret); result=Json.array(found,movie?"movie_results":"tv_results").optJSONObject(0); }
                if(result!=null&&result.optInt("vote_count")>0) { put(scores,"tmdb",result.optDouble("vote_average")); providers.put("tmdb",true); }
            } catch(Exception e) { failed=true; }
            if(configured) status=failed?I18n.text("Ein Anbieter war nicht erreichbar oder hat den API-Key abgelehnt. Gespeicherte Werte bleiben verfügbar."):I18n.text("Bewertungen dauerhaft lokal gespeichert");
            if(scores.length()>0) library.state("ratings-values-"+id,Json.obj("scores",scores,"providers",providers).toString());
            JSONObject entry=Json.obj("time",System.currentTimeMillis(),"ttl",failed?180000:(scores.length()>0?TTL:180000),"failed",failed,"scores",scores); library.state(cache,entry.toString());
        } catch(Exception ignored) { failed=true; }
        complete(id,scores,failed);
    }
    private String selection() { StringBuilder mask=new StringBuilder(); for(String id:IDS) mask.append(enabled(settings,id)); return mask.toString(); }
    private boolean needed(String... sources) { for(String source:sources) if(enabled(settings,source)) return true; return false; }
    private JSONObject tmdbFetch(String path,String secret) throws Exception {
        HttpUrl.Builder url=HttpUrl.get(tmdb+path).newBuilder(); boolean bearer=secret.contains("."); if(!bearer) url.addQueryParameter("api_key",secret); return fetch("tmdb",url.build(),bearer?secret:null);
    }
    private JSONObject fetch(String provider,HttpUrl url,String bearer) throws Exception {
        if(blocked.getOrDefault(provider,0L)>System.currentTimeMillis()) throw new java.io.IOException(I18n.text("Anbieter pausiert"));
        Request.Builder request=new Request.Builder().url(url); if(bearer!=null) request.header("Authorization","Bearer "+bearer);
        try(Response response=http.newCall(request.build()).execute()) {
            if(!response.isSuccessful()) { long pause=180000; if(response.code()==429) try { pause=Math.max(pause,Long.parseLong(response.header("Retry-After","180"))*1000); } catch(NumberFormatException ignored) {}
                blocked.put(provider,System.currentTimeMillis()+pause); throw new java.io.IOException(I18n.text("Bewertungsanbieter HTTP ")+response.code()); }
            JSONObject data=new JSONObject(response.body().string()); if(data.has("Error")||data.has("error")||"False".equalsIgnoreCase(data.optString("Response"))) { blocked.put(provider,System.currentTimeMillis()+180000); throw new java.io.IOException(I18n.text("Anbieter antwortet mit Fehler")); } return data;
        }
    }
    private void complete(String id,JSONObject scores,boolean failed) {
        List<Consumer<JSONObject>> callbacks;
        synchronized(pending) { memory.put(id,Json.obj("time",System.currentTimeMillis(),"ttl",failed?180000:(scores.length()>0?TTL:180000),"scores",scores)); while(memory.size()>64) memory.remove(memory.keySet().iterator().next()); callbacks=pending.remove(id); }
        if(callbacks!=null) main.post(()-> { for(Consumer<JSONObject> callback:callbacks) callback.accept(scores); });
    }
    static String providerId(JSONObject ids,String name) { Iterator<String> keys=ids.keys(); while(keys.hasNext()) { String key=keys.next(); if(key.equalsIgnoreCase(name)) return ids.optString(key); } return ""; }
    private static String fingerprint(String input) { try { byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)); return android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP); } catch(Exception e) { throw new IllegalStateException(e); } }
    static JSONObject parseOmdb(JSONObject data) {
        JSONObject scores=new JSONObject(); put(scores,"imdb",data.optDouble("imdbRating",Double.NaN)); put(scores,"metacritic",data.optDouble("Metascore",Double.NaN));
        JSONArray ratings=Json.array(data,"Ratings"); for(int i=0;i<ratings.length();i++) { JSONObject r=ratings.optJSONObject(i); if(r==null) continue;
            String source=r.optString("Source"),value=r.optString("Value"); try { if(source.equals("Rotten Tomatoes")) put(scores,"tomatoes",Double.parseDouble(value.replace("%",""))); } catch(NumberFormatException ignored) {} }
        return scores;
    }
    static JSONObject parseMdb(JSONObject data) {
        JSONObject scores=new JSONObject(); put(scores,"mdblist",data.optDouble("score",Double.NaN)); JSONArray ratings=Json.array(data,"ratings");
        for(int i=0;i<ratings.length();i++) { JSONObject r=ratings.optJSONObject(i); if(r==null||r.isNull("value")) continue; String source=r.optString("source").toLowerCase(Locale.ROOT);
            if(source.equals("popcorn")) source="tomatoesaudience"; if(source.equals("tomatoes_audience")) source="tomatoesaudience"; if(source.equals("themoviedb")) source="tmdb";
            if(Arrays.asList(IDS).contains(source)) put(scores,source,r.optDouble("value",Double.NaN)); }
        return scores;
    }
    private static void put(JSONObject scores,String source,double value) { double max=source.equals("letterboxd")?5:source.equals("imdb")||source.equals("tmdb")?10:100; if(Double.isFinite(value)&&value>=0&&value<=max) try { scores.put(source,value); } catch(JSONException ignored) {} }
    private static void merge(JSONObject target,JSONObject source) throws JSONException { Iterator<String> keys=source.keys(); while(keys.hasNext()) { String key=keys.next(); target.put(key,source.get(key)); } }
    static String format(String source,double value) { if(source.equals("imdb")||source.equals("tmdb")||source.equals("letterboxd")) return String.format(Locale.GERMAN,"%.1f/%d",value,source.equals("letterboxd")?5:10); return String.format(Locale.GERMAN,"%.0f%s",value,source.equals("metacritic")||source.equals("mdblist")?"/100":"%"); }
}
