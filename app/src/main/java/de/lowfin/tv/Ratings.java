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
    private Supplier<CompanionApi> companion=()->null;
    private boolean cacheEnabled() { return settings.getBoolean("ratingCache",true); }
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
    Ratings(Context c,Library db,Api api) { this(c.getSharedPreferences("settings",0),db,api,new RatingKeys(c)::get,"https://www.omdbapi.com/","https://api.mdblist.com/","https://api.themoviedb.org/3/"); companion=()->c.getApplicationContext() instanceof LowfinApp?((LowfinApp)c.getApplicationContext()).companion:null; }
    Ratings(SharedPreferences settings,Library db,Api api,Function<String,String> key,String omdb,String mdb,String tmdb) { this.settings=settings;library=db;jellyfin=api;this.key=key;this.omdb=omdb;this.mdb=mdb;this.tmdb=tmdb; }
    static boolean enabled(SharedPreferences settings,String source) { return settings.getBoolean("rating-show-"+source,true); }
    void clearMemory() { synchronized(pending) { memory.clear(); } blocked.clear(); }
    void close() { worker.shutdownNow(); http.dispatcher().cancelAll(); }
    /** UI-only fast path: never reads disk or starts provider work. */
    JSONObject peek(JSONObject item) {
        if(item.optString("Type").equals("Episode")||!cacheEnabled()) return null;
        synchronized(pending) {
            JSONObject cached=memory.get(item.optString("Id"));
            return cached!=null&&System.currentTimeMillis()-cached.optLong("time")<cached.optLong("ttl",TTL)?Json.object(cached,"scores"):null;
        }
    }
    void request(JSONObject item,Consumer<JSONObject> callback) {
        if(!item.optString("Type").equals("Movie")&&!item.optString("Type").equals("Series")&&!item.optString("Type").equals("Episode")) { callback.accept(new JSONObject()); return; }
        String id=item.optString("Id");
        synchronized(pending) {
            JSONObject cached=memory.get(id); if(!item.optString("Type").equals("Episode")&&cacheEnabled()&&cached!=null&&System.currentTimeMillis()-cached.optLong("time")<cached.optLong("ttl",TTL)) { callback.accept(Json.object(cached,"scores")); return; }
            if(pending.containsKey(id)) { pending.get(id).add(callback); return; }
            pending.put(id,new ArrayList<>(Collections.singletonList(callback)));
        }
        try { worker.execute(()-> { if(item.optString("Type").equals("Episode")) loadEpisode(item,id);else load(item,id); }); } catch(RejectedExecutionException ignored) { complete(id,new JSONObject(),false,!item.optString("Type").equals("Episode")); }
    }
    private void loadEpisode(JSONObject original,String id) {
        JSONObject scores=new JSONObject();boolean failed=false;
        try {
            CompanionApi bridge=companion.get();
            if(bridge!=null&&bridge.serverProviders()) {
                List<String> selected=new ArrayList<>();if(settings.getBoolean("rating-provider-omdb",true)&&needed("imdb")) selected.add("omdb");if(settings.getBoolean("rating-provider-tmdb",true)&&needed("tmdb")) selected.add("tmdb");
                if(!selected.isEmpty()) { JSONObject response=jellyfin.get(CompanionApi.ROOT+"/items/"+id+"/ratings","sources",String.join(",",selected));merge(scores,Json.object(response,"scores")); }
            } else {
                String omdbKey=settings.getBoolean("rating-provider-omdb",true)&&needed("imdb")?key.apply("omdb"):"",tmdbKey=settings.getBoolean("rating-provider-tmdb",true)&&needed("tmdb")?key.apply("tmdb"):"";
                if(!omdbKey.isEmpty()||!tmdbKey.isEmpty()) {
                    JSONObject item=original;if(!item.has("ProviderIds")||(providerId(Json.object(item,"ProviderIds"),"Imdb").isEmpty()&&(!item.has("ParentIndexNumber")||!item.has("IndexNumber")||item.optString("SeriesId").isEmpty()))) item=jellyfin.item(id);
                    String imdb=providerId(Json.object(item,"ProviderIds"),"Imdb");if(!imdb.matches("tt[0-9]+")) imdb="";
                    JSONObject series=new JSONObject();int season=item.optInt("ParentIndexNumber",-1),episode=item.optInt("IndexNumber",-1);
                    if(imdb.isEmpty()&&!item.optString("SeriesId").isEmpty()&&season>=0&&episode>0) series=jellyfin.item(item.optString("SeriesId"));
                    String seriesImdb=providerId(Json.object(series,"ProviderIds"),"Imdb"),seriesTmdb=providerId(Json.object(series,"ProviderIds"),"Tmdb");
                    if(!omdbKey.isEmpty()&&(!imdb.isEmpty()||seriesImdb.matches("tt[0-9]+"))) try {
                        HttpUrl.Builder url=HttpUrl.get(omdb).newBuilder().addQueryParameter("apikey",omdbKey).addQueryParameter("i",imdb.isEmpty()?seriesImdb:imdb).addQueryParameter("type","episode");
                        if(imdb.isEmpty()) url.addQueryParameter("Season",""+season).addQueryParameter("Episode",""+episode);
                        JSONObject result=fetch("omdb",url.build(),null);if("episode".equalsIgnoreCase(result.optString("Type"))) put(scores,"imdb",result.optDouble("imdbRating",Double.NaN));
                    } catch(Exception error) { failed=true; }
                    if(!tmdbKey.isEmpty()) try {
                        JSONObject result=null;
                        if(!imdb.isEmpty()) result=Json.array(tmdbFetch("find/"+imdb+"?external_source=imdb_id",tmdbKey),"tv_episode_results").optJSONObject(0);
                        else if(seriesTmdb.matches("[0-9]+")&&season>=0&&episode>0) result=tmdbFetch("tv/"+seriesTmdb+"/season/"+season+"/episode/"+episode,tmdbKey);
                        if(result!=null&&result.optInt("vote_count")>0) put(scores,"tmdb",result.optDouble("vote_average",Double.NaN));
                    } catch(Exception error) { failed=true; }
                }
            }
        } catch(Exception error) { failed=true; }
        complete(id,scores,failed,false);
    }
    private void load(JSONObject original,String id) {
        CompanionApi bridge=companion.get(); if(bridge!=null&&bridge.serverProviders()) { loadCompanion(original,id,bridge); return; }
        JSONObject scores=new JSONObject(); boolean failed=false;
        try {
            Map<String,String> keys=new HashMap<>(); for(String p:new String[]{"omdb","mdblist","tmdb"}) keys.put(p,settings.getBoolean("rating-provider-"+p,true)?key.apply(p):"");
            String cache="ratings-"+id+"-"+fingerprint(keys.toString()+selection()); String saved=cacheEnabled()?library.state(cache):"";
            String existing=cacheEnabled()?library.state("ratings-values-"+id):""; JSONObject permanent=new JSONObject(existing.isEmpty()?"{}":existing); merge(scores,Json.object(permanent,"scores")); JSONObject providers=Json.object(permanent,"providers");
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
            if(configured) status=!cacheEnabled()?I18n.resource(R.string.rating_cache_disabled):failed?I18n.text("Ein Anbieter war nicht erreichbar oder hat den API-Key abgelehnt. Gespeicherte Werte bleiben verfügbar."):I18n.text("Bewertungen dauerhaft lokal gespeichert");
            if(cacheEnabled()&&scores.length()>0) library.state("ratings-values-"+id,Json.obj("scores",scores,"providers",providers).toString());
            JSONObject entry=Json.obj("time",System.currentTimeMillis(),"ttl",failed?180000:(scores.length()>0?TTL:180000),"failed",failed,"scores",scores); if(cacheEnabled()) library.state(cache,entry.toString());
        } catch(Exception ignored) { failed=true; }
        complete(id,scores,failed);
    }
    private void loadCompanion(JSONObject item,String id,CompanionApi bridge) {
        JSONObject scores=new JSONObject(); boolean failed=false;
        try {
            List<String> selected=new ArrayList<>(); for(String provider:new String[]{"omdb","mdblist","tmdb"}) if(settings.getBoolean("rating-provider-"+provider,true)&&(provider.equals("omdb")?needed("imdb","tomatoes","metacritic"):provider.equals("tmdb")?needed("tmdb"):needed(IDS))) selected.add(provider);
            if(selected.isEmpty()) { complete(id,scores,false); return; }
            String cache="companion-ratings-"+id+"-"+fingerprint(selected.toString()+selection()); String saved=cacheEnabled()?library.state(cache):"";
            if(!saved.isEmpty()) { JSONObject entry=new JSONObject(saved); if((!entry.optBoolean("failed")&&Json.object(entry,"scores").length()>0)||System.currentTimeMillis()-entry.optLong("time")<180000) { complete(id,Json.object(entry,"scores"),entry.optBoolean("failed")); return; } }
            if(cacheEnabled()&&settings.getBoolean("rating-provider-omdb",true)&&settings.getBoolean("rating-provider-mdblist",true)&&settings.getBoolean("rating-provider-tmdb",true)) {
                String existing=library.state("ratings-values-"+id); if(!existing.isEmpty()) merge(scores,Json.object(new JSONObject(existing),"scores"));
            }
            JSONObject response=jellyfin.get(CompanionApi.ROOT+"/items/"+id+"/ratings","sources",String.join(",",selected)); merge(scores,Json.object(response,"scores")); failed=Json.array(response,"errors").length()>0;
            if(cacheEnabled()) {
                library.state(cache,Json.obj("scores",scores,"failed",failed,"time",System.currentTimeMillis()).toString());
                if(scores.length()>0&&settings.getBoolean("rating-provider-omdb",true)&&settings.getBoolean("rating-provider-mdblist",true)&&settings.getBoolean("rating-provider-tmdb",true)) {
                    String previous=library.state("ratings-values-"+id); JSONObject savedProviders=previous.isEmpty()?new JSONObject():Json.object(new JSONObject(previous),"providers");
                    library.state("ratings-values-"+id,Json.obj("scores",scores,"providers",savedProviders).toString());
                }
            }
            status=I18n.resource(R.string.companion_providers_hint);
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
    private void complete(String id,JSONObject scores,boolean failed) { complete(id,scores,failed,true); }
    private void complete(String id,JSONObject scores,boolean failed,boolean allowCache) {
        List<Consumer<JSONObject>> callbacks;
        synchronized(pending) { if(allowCache&&cacheEnabled()) memory.put(id,Json.obj("time",System.currentTimeMillis(),"ttl",failed?180000:(scores.length()>0?TTL:180000),"scores",scores)); while(memory.size()>64) memory.remove(memory.keySet().iterator().next()); callbacks=pending.remove(id); }
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
    static String format(String source,double value) {
        boolean decimal=source.equals("imdb")||source.equals("tmdb")||source.equals("letterboxd");
        String suffix=decimal?(source.equals("letterboxd")?"/5":"/10"):source.equals("metacritic")||source.equals("mdblist")?"/100":"%";
        // Provider scores are bounded nonnegative numbers. Avoid Formatter/ICU startup
        // and per-badge format parsing; preserve exact decimal rounding at boundaries.
        if(Double.isFinite(value)&&value>=0&&value<=100&&Double.doubleToRawLongBits(value)>=0)
            return java.math.BigDecimal.valueOf(value).setScale(decimal?1:0,java.math.RoundingMode.HALF_UP).toPlainString().replace('.',',')+suffix;
        return String.format(Locale.GERMAN,decimal?"%.1f%s":"%.0f%s",value,suffix);
    }
}
