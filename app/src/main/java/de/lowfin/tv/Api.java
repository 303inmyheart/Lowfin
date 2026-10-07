package de.lowfin.tv;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class Api {
    static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
    final Account account;
    final OkHttpClient http;
    Api(Account a) {
        account=a;
        http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(180,TimeUnit.SECONDS)
            .callTimeout(200,TimeUnit.SECONDS).followSslRedirects(false).build();
    }
    static class Failure extends IOException {
        final int status;
        Failure(int status) { super(status==401?I18n.text("Anmeldung abgelaufen"):status==403?I18n.text("Keine Berechtigung"): I18n.text("Serverfehler HTTP ")+status); this.status=status; }
    }
    String authorization() {
        return "MediaBrowser Client=\"Lowfin\", Device=\"Android TV\", DeviceId=\""+account.device+"\", Version=\"0.7.0\""+
            (account.token.isEmpty()?"":", Token=\""+account.token+"\"");
    }
    HttpUrl url(String path,Object... pairs) {
        HttpUrl.Builder b=HttpUrl.get(account.server+path).newBuilder();
        for(int i=0;i<pairs.length;i+=2) if(pairs[i+1]!=null) b.addQueryParameter((String)pairs[i],String.valueOf(pairs[i+1]));
        return b.build();
    }
    Request.Builder request(HttpUrl url) { return new Request.Builder().url(url).header("Authorization",authorization()); }
    JSONObject call(String method,String path,JSONObject body,Object... pairs) throws Exception {
        RequestBody data=body==null?RequestBody.create("",JSON):RequestBody.create(body.toString(),JSON);
        Request.Builder r=request(url(path,pairs));
        if(method.equals("GET")) r.get(); else r.method(method,data);
        try(Response response=http.newCall(r.build()).execute()) {
            if(!response.isSuccessful()) throw new Failure(response.code());
            String text=response.body()==null?"":response.body().string();
            return text.isEmpty()?new JSONObject():new JSONObject(text);
        }
    }
    JSONObject get(String path,Object... pairs) throws Exception { return call("GET",path,null,pairs); }
    JSONArray getArray(String path,Object... pairs) throws Exception {
        try(Response response=http.newCall(request(url(path,pairs)).get().build()).execute()) {
            if(!response.isSuccessful()) throw new Failure(response.code());
            String text=response.body()==null?"":response.body().string();
            return text.isEmpty()?new JSONArray():new JSONArray(text);
        }
    }
    JSONObject post(String path,JSONObject body,Object... pairs) throws Exception { return call("POST",path,body,pairs); }
    JSONObject item(String id) throws Exception { return get("/Users/"+account.user+"/Items/"+id); }
    JSONObject items(int start,int limit,String type,String parent,String search) throws Exception {
        return get("/Users/"+account.user+"/Items","Recursive",true,"ParentId",parent,"IncludeItemTypes",type,
            "StartIndex",start,"Limit",limit,"SortBy","SortName","SortOrder","Ascending","SearchTerm",search,
            "Fields","Overview,Genres,ProviderIds,ParentId,DateCreated,MediaSources,MediaStreams,PrimaryImageAspectRatio,SortName,RecursiveItemCount,Chapters",
            "EnableImages",true,"EnableUserData",true);
    }
    String image(JSONObject item,int width) { return image(item,width,false); }
    String image(JSONObject item,int width,boolean wide) {
        String type=Artwork.type(item,wide);
        if(!type.equals("Primary")) { String tag=type.equals("Backdrop")?Json.array(item,"BackdropImageTags").optString(0):Json.str(Json.object(item,"ImageTags"),type); return url("/Items/"+item.optString("Id")+"/Images/"+type,"MaxWidth",width,"Quality",78,"Tag",tag).toString(); }
        JSONObject tags=Json.object(item,"ImageTags"); String id=item.optString("Id"); String tag=Json.str(tags,"Primary");
        if(tag.isEmpty()) { if(!Json.str(item,"SeriesId").isEmpty()) id=Json.str(item,"SeriesId"); tag=Json.str(item,"SeriesPrimaryImageTag"); }
        return url("/Items/"+id+"/Images/Primary","MaxWidth",width,"Quality",78,"Tag",tag.isEmpty()?null:tag).toString();
    }
    boolean hasImage(JSONObject item,boolean wide) { return !Artwork.type(item,wide).equals("Primary")||hasImage(item); }
    boolean hasImage(JSONObject item) { return !Json.str(Json.object(item,"ImageTags"),"Primary").isEmpty()||!Json.str(item,"SeriesPrimaryImageTag").isEmpty(); }
    void report(String endpoint,JSONObject body) throws Exception { post("/Sessions/Playing"+endpoint,body); }
}
