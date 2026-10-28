package de.lowfin.tv;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.net.HttpURLConnection;
import java.util.*;
/** Same-signer inspection; never prints credentials or edits client cache/settings. */
final class TagDiagnostics {
    static String inspect(Context context) throws Exception {
        android.content.SharedPreferences saved=context.getSharedPreferences("account",0),settings=context.getSharedPreferences("settings",0);
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,store.getKey("lowfin-session",null),new GCMParameterSpec(128,Base64.decode(saved.getString("iv",""),Base64.NO_WRAP)));
        JSONObject account=new JSONObject(new String(cipher.doFinal(Base64.decode(saved.getString("session",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
        byte[] hash=MessageDigest.getInstance("SHA-256").digest((account.getString("server")+"|"+account.getString("user")).getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));String scope=hex.substring(0,24);
        android.content.SharedPreferences discovery=context.getSharedPreferences("companion-outbox-"+scope,0);
        StringBuilder out=new StringBuilder("showCoverTags="+settings.getBoolean("showCoverTags",true)+" hdrPlayback="+settings.getBoolean("hdrPlayback",false)+"\n");
        for(String key:new String[]{"available","coverTagsSupported","extendedTags"})out.append(key).append('=').append(discovery.getBoolean(key,false)).append('\n');
        out.append("coverTagFilter=").append(discovery.getInt("coverTagFilter",0)).append(" labels=").append(discovery.getString("coverLabelKeys","[]")).append('\n');
        List<String> ids=new ArrayList<>();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("library-"+scope+".db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
            try(Cursor c=db.rawQuery("SELECT COUNT(*),SUM(CASE WHEN value NOT IN ('0','[]','') THEN 1 ELSE 0 END) FROM state WHERE key LIKE 'cover-tags-v2-%' OR key LIKE 'cover-tag-labels-v1-%'",null)){if(c.moveToFirst())out.append("storedFacts=").append(c.getInt(0)).append(" nonempty=").append(c.getInt(1)).append('\n');}
            try(Cursor c=db.rawQuery("SELECT id FROM items WHERE type IN ('Movie','Series','Episode') ORDER BY stamp DESC LIMIT 5",null)){while(c.moveToNext())ids.add(c.getString(0));}
        }
        out.append("capabilities=").append(get(account,"/Lowfin/Companion/v1/capabilities")).append('\n');
        if(!ids.isEmpty())out.append("sampleBadges=").append(get(account,"/Lowfin/Companion/v1/badges?ids="+String.join(",",ids))).append('\n');
        return out.toString();
    }
    private static String get(JSONObject account,String path) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new java.net.URL(account.getString("server")+path).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(10000);connection.setRequestProperty("Authorization","MediaBrowser Client=\"Lowfin\", Device=\"Diagnostics\", DeviceId=\"tag-diagnostics\", Token=\""+account.getString("token")+"\"");
        try {int status=connection.getResponseCode();if(status!=200)return "HTTP "+status;try(java.io.InputStream input=connection.getInputStream();java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){byte[] block=new byte[4096];int n;while((n=input.read(block))!=-1){if(bytes.size()+n>65536)throw new java.io.IOException("Oversized response");bytes.write(block,0,n);}return bytes.toString("UTF-8");}}finally{connection.disconnect();}
    }
}
