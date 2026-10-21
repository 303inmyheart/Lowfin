package de.lowfin.tv;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.net.HttpURLConnection;
import java.util.*;

/** Same-signer, read-only release-cache inspection. Never prints credentials or modifies cache files. */
final class CacheDiagnostics {
    private static String hash(String value) throws Exception { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder(64); char[] hex="0123456789abcdef".toCharArray(); for(byte b:bytes) out.append(hex[(b&255)>>>4]).append(hex[b&15]); return out.toString(); }
    private static String recentArtwork(Context context,SQLiteDatabase db,String server,String scope) throws Exception {
        Map<String,String> labels=new HashMap<>(); Map<String,File> files=new HashMap<>();
        File[] all=new File(context.getFilesDir(),"covers-"+scope).listFiles(file->file.getName().endsWith(".jpg"));
        if(all==null) return "recentArtworkFiles=0\n";
        Arrays.sort(all,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        Set<String> wanted=new HashSet<>();for(int i=0;i<Math.min(20,all.length);i++) { wanted.add(all[i].getName());files.put(all[i].getName(),all[i]); }
        int densityWidth=Math.min(1440,Math.max(240,Math.round(240*context.getResources().getDisplayMetrics().density)));
        int backdropWidth=Math.min(1440,Math.max(720,Math.round(720*context.getResources().getDisplayMetrics().density)));
        File dir=new File(context.getFilesDir(),"covers-"+scope);
        try(Cursor rows=db.rawQuery("SELECT json FROM items WHERE type IN ('Movie','Series','Season','Episode') ORDER BY stamp DESC LIMIT 5000",null)) { while(rows.moveToNext()) {
            JSONObject item=new JSONObject(rows.getString(0)),tags=item.optJSONObject("ImageTags"); if(tags==null) tags=new JSONObject();
            for(String kind:new String[]{"Primary","Thumb","Backdrop","Logo"}) {
                String id=item.optString("Id"),tag=tags.optString(kind);
                if(kind.equals("Primary")&&tag.isEmpty()) { id=item.optString("SeriesId"); tag=item.optString("SeriesPrimaryImageTag"); }
                if(kind.equals("Logo")&&tag.isEmpty()) { id=item.optString("ParentLogoItemId",item.optString("SeriesId")); tag=item.optString("ParentLogoImageTag",item.optString("SeriesLogoImageTag")); }
                if(kind.equals("Backdrop")) { JSONArray a=item.optJSONArray("BackdropImageTags"); if(a!=null&&a.length()>0) tag=a.optString(0); else { a=item.optJSONArray("ParentBackdropImageTags"); id=item.optString("ParentBackdropItemId"); if(a!=null) tag=a.optString(0); } }
                if(id.isEmpty()||tag.isEmpty()) continue;
                for(int width:new int[]{240,320,densityWidth,720,backdropWidth,1440}) {
                    String url=Uri.parse(server+"/Items/"+id+"/Images/"+kind).buildUpon().appendQueryParameter("MaxWidth",""+width).appendQueryParameter("Quality","78").appendQueryParameter("Tag",tag).build().toString();
                    File file=new File(dir,hash(url)+".jpg"); if(!wanted.contains(file.getName())) continue;
                    files.put(file.getName(),file); labels.put(file.getName(),item.optString("Name")+" | series="+item.optString("SeriesName")+" | "+kind+" width="+width);
                }
            }
        } }
        List<File> sorted=new ArrayList<>(files.values()); sorted.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));
        StringBuilder out=new StringBuilder("recentArtworkFiles="+sorted.size()+" (matched against latest 5000 media rows; timestamps can reflect use)\n");
        for(int i=0;i<Math.min(20,sorted.size());i++) { File f=sorted.get(i); out.append("recentImage=").append(labels.getOrDefault(f.getName(),"unmatched "+f.getName())).append(" | bytes=").append(f.length()).append(" | filesystemModifiedAt=").append(java.time.Instant.ofEpochMilli(f.lastModified())).append('\n'); }
        return out.toString();
    }
    static String inspect(Context context) throws Exception { return inspect(context,false); }
    static String inspect(Context context,boolean stateOnly) throws Exception { return inspect(context,stateOnly,false); }
    static String inspect(Context context,boolean stateOnly,boolean pendingOnly) throws Exception {
        android.content.SharedPreferences prefs=context.getSharedPreferences("account",0);
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,store.getKey("lowfin-session",null),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        JSONObject account=new JSONObject(new String(cipher.doFinal(Base64.decode(prefs.getString("session",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
        String server=account.getString("server"),scope=hash(server+"|"+account.getString("user")).substring(0,24);
        if(pendingOnly) {
            StringBuilder out=new StringBuilder();String auth="MediaBrowser Client=\"Lowfin\", Device=\"Android TV\", DeviceId=\"diagnostics\", Token=\""+account.getString("token")+"\"";
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("library-"+scope+".db").getPath(),null,SQLiteDatabase.OPEN_READONLY);
                Cursor c=db.rawQuery("SELECT item,retry FROM cache_artwork LIMIT 10",null)) {
                while(c.moveToNext()) {
                    String id=c.getString(0);HttpURLConnection connection=(HttpURLConnection)new java.net.URL(server+"/Users/"+account.getString("user")+"/Items/"+id).openConnection();
                    connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setRequestProperty("Authorization",auth);
                    try { int status=connection.getResponseCode();out.append("pending=").append(id).append(" metadataHTTP=").append(status);
                        if(status==200) { byte[] data;try(java.io.InputStream stream=connection.getInputStream()) { java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=stream.read(block))!=-1) { if(bytes.size()+n>2097152) throw new java.io.IOException("Metadata too large");bytes.write(block,0,n); }data=bytes.toByteArray(); }JSONObject item=new JSONObject(new String(data,StandardCharsets.UTF_8));
                            out.append(" | name=").append(item.optString("Name")).append(" | seriesId=").append(item.optString("SeriesId")).append(" | tags=").append(item.optJSONObject("ImageTags")).append(" | seriesPrimary=").append(item.optString("SeriesPrimaryImageTag"));
                        }out.append('\n');
                    } finally { connection.disconnect(); }
                }
            }return out.toString();
        }
        if(stateOnly) {
            StringBuilder result=new StringBuilder();
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("library-"+scope+".db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
                try(Cursor c=db.rawQuery("SELECT key,value FROM state WHERE key IN ('cacheStatus','lastSync','cacheCursor','cacheChangeAudit')",null)) { while(c.moveToNext()) result.append(c.getString(0)).append('=').append(c.getString(0).equals("cacheCursor")?(c.getString(1).isEmpty()?"EMPTY":"present"):c.getString(1)).append('\n'); }
                try(Cursor c=db.rawQuery("SELECT type,name,stamp,json FROM items WHERE type IN ('Movie','Series','Episode','Season') ORDER BY stamp DESC LIMIT 20",null)) { while(c.moveToNext()) { JSONObject item=new JSONObject(c.getString(3)); result.append("recentMetadata=").append(c.getString(0)).append(" | ").append(c.getString(1)).append(" | series=").append(item.optString("SeriesName")).append(" | season=").append(item.optInt("ParentIndexNumber",-1)).append(" episode=").append(item.optInt("IndexNumber",-1)).append(" | localSavedAt=").append(java.time.Instant.ofEpochMilli(c.getLong(2))).append(" | serverDateLastSaved=").append(item.optString("DateLastSaved")).append('\n'); } }
                try(Cursor c=db.rawQuery("SELECT q.item,q.retry,i.json FROM cache_artwork q LEFT JOIN items i ON i.id=q.item ORDER BY q.retry LIMIT 20",null)) { while(c.moveToNext()) {
                    JSONObject item=c.isNull(2)?new JSONObject():new JSONObject(c.getString(2));
                    result.append("pendingArtwork=").append(c.getString(0)).append(" | name=").append(item.optString("Name")).append(" | type=").append(item.optString("Type")).append(" | series=").append(item.optString("SeriesName")).append(" | retryAt=").append(java.time.Instant.ofEpochMilli(c.getLong(1))).append(" | tags=").append(item.optJSONObject("ImageTags")).append(" | backdrop=").append(item.optJSONArray("BackdropImageTags")).append(" | seriesId=").append(item.optString("SeriesId")).append(" | seriesPrimary=").append(item.optString("SeriesPrimaryImageTag")).append('\n');
                } }
                result.append(recentArtwork(context,db,server,scope));
                for(String table:new String[]{"items","cache_artwork"}) try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)) { if(c.moveToFirst()) result.append(table).append('=').append(c.getLong(0)).append('\n'); }
            }
            android.content.SharedPreferences discovery=context.getSharedPreferences("companion-outbox-"+scope,0);
            result.append("companionAvailable=").append(discovery.getBoolean("available",false)).append(" incrementalCache=").append(discovery.getBoolean("incrementalCache",false)).append("\n");
            result.append("autoPrecache=").append(context.getSharedPreferences("settings",0).getBoolean("autoPrecache",true)).append("\n");
            File[] artwork=new File(context.getFilesDir(),"covers-"+scope).listFiles(); long imageFiles=0,imageBytes=0;
            if(artwork!=null) for(File file:artwork) if(file.getName().endsWith(".jpg")&&file.isFile()) { imageFiles++; imageBytes+=file.length(); }
            result.append("imageFiles=").append(imageFiles).append(" imageBytes=").append(imageBytes).append("\n");
            return result.toString();
        }
        File directory=new File(context.getFilesDir(),"covers-"+scope); int missing=0,checked=0; Map<String,Integer> reasons=new TreeMap<>(); StringBuilder examples=new StringBuilder();
        String auth="MediaBrowser Client=\"Lowfin\", Device=\"Android TV\", DeviceId=\""+account.getString("device")+"\", Version=\"0.8.1\", Token=\""+account.getString("token")+"\"";
        Map<String,JSONObject> liveIds=new HashMap<>(),liveNames=new HashMap<>();
        HttpURLConnection live=(HttpURLConnection)new java.net.URL(server+"/LiveTv/Channels?UserId="+account.getString("user")+"&Fields=ChannelInfo").openConnection();
        try { live.setConnectTimeout(5000); live.setReadTimeout(10000); live.setRequestProperty("Authorization",auth); if(live.getResponseCode()!=200) throw new java.io.IOException("Live TV list HTTP "+live.getResponseCode()); try(java.io.InputStream input=live.getInputStream()) { java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] block=new byte[8192]; int n; while((n=input.read(block))!=-1) { if(out.size()+n>2097152) throw new java.io.IOException("Live list too large"); out.write(block,0,n); } JSONArray list=new JSONObject(out.toString("UTF-8")).optJSONArray("Items"); if(list!=null) for(int i=0;i<list.length();i++) { JSONObject channel=list.getJSONObject(i); liveIds.put(channel.optString("Id"),channel); liveNames.put(channel.optString("Name"),channel); } } } finally { live.disconnect(); }
        int obsolete=0,changedTags=0;
        String saved=""; int width=Math.min(1440,Math.max(240,Math.round(240*context.getResources().getDisplayMetrics().density)));
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("library-"+scope+".db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
            try(Cursor c=db.rawQuery("SELECT value FROM state WHERE key='cacheStatus'",null)) { if(c.moveToFirst()) saved=c.getString(0); }
            try(Cursor c=db.rawQuery("SELECT json FROM items WHERE type='TvChannel'",null)) { while(c.moveToNext()) {
                JSONObject item=new JSONObject(c.getString(0)),tags=item.optJSONObject("ImageTags"); if(tags==null) tags=new JSONObject();
                JSONArray backdrops=item.optJSONArray("BackdropImageTags"); String primary=tags.optString("Primary"); if(primary.isEmpty()) primary=item.optString("SeriesPrimaryImageTag");
                for(int wide=0;wide<2;wide++) {
                    String type="Primary",tag=primary,id=item.optString("Id");
                    if(wide==1) { if(!tags.optString("Thumb").isEmpty()) { type="Thumb"; tag=tags.optString("Thumb"); } else if(!item.optString("Type").equals("Episode")&&backdrops!=null&&backdrops.length()>0) { type="Backdrop"; tag=backdrops.optString(0); } else continue; }
                    if(type.equals("Primary")) { if(primary.isEmpty()) continue; if(tags.optString("Primary").isEmpty()&&!item.optString("SeriesId").isEmpty()) id=item.optString("SeriesId"); }
                    checked++; if(checked%2000==0) android.util.Log.i("LowfinCacheDiagnostic","Checked "+checked+" artwork references; missing "+missing); Uri.Builder url=Uri.parse(server+"/Items/"+id+"/Images/"+type).buildUpon().appendQueryParameter("MaxWidth",String.valueOf(width)).appendQueryParameter("Quality","78"); if(!tag.isEmpty()) url.appendQueryParameter("Tag",tag);
                    File file=new File(directory,hash(url.build().toString())+".jpg"); if(!file.isFile()&&width>240&&width<=320) { Uri legacy=url.build().buildUpon().clearQuery().appendQueryParameter("MaxWidth","240").appendQueryParameter("Quality","78").appendQueryParameter("Tag",tag).build(); file=new File(directory,hash(legacy.toString())+".jpg"); }
                    if(file.isFile()&&file.length()>0) continue;
                    missing++; if(!liveIds.containsKey(id)) obsolete++; else { JSONObject fresh=liveIds.get(id).optJSONObject("ImageTags"); if(fresh==null||!tag.equals(fresh.optString(type))) changedTags++; } if(missing>100) continue;
                    String reason; HttpURLConnection connection=null;
                    try { connection=(HttpURLConnection)new java.net.URL(url.build().toString()).openConnection(); connection.setConnectTimeout(4000); connection.setReadTimeout(6000); connection.setInstanceFollowRedirects(false); connection.setRequestProperty("Authorization",auth); int status=connection.getResponseCode(); reason="HTTP "+status; if(status==404) { HttpURLConnection metadata=(HttpURLConnection)new java.net.URL(server+"/Users/"+account.getString("user")+"/Items/"+id).openConnection(); try { metadata.setConnectTimeout(4000); metadata.setReadTimeout(6000); metadata.setRequestProperty("Authorization",auth); reason+=" / item HTTP "+metadata.getResponseCode(); } finally { metadata.disconnect(); } } }
                    catch(Exception e) { reason=e.getClass().getSimpleName(); } finally { if(connection!=null) connection.disconnect(); }
                    reasons.put(reason,reasons.getOrDefault(reason,0)+1); if(examples.length()<2500) examples.append(item.optString("Name")).append(" · ").append(type).append(" · ").append(reason).append('\n');
                }
            } }
        }
        return "savedStatus="+saved+"\ncacheBudgetMB="+context.getSharedPreferences("settings",0).getInt("cacheMb",1024)+"\nfreeMB="+directory.getUsableSpace()/1048576+"\nwidth="+width+"\nexpectedArt="+checked+"\nmissingArt="+missing+"\ncurrentChannels="+liveIds.size()+"\nmissingIdsAbsentFromCurrentList="+obsolete+"\nmissingIdsWithChangedImageTags="+changedTags+"\nprobeResults="+reasons+"\n"+examples;
    }
}
