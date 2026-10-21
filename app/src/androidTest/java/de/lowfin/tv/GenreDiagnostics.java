package de.lowfin.tv;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/** Same-signer read-only inspection: return genre names only, never account credentials. */
final class GenreDiagnostics {
    static String inspect(Context context) throws Exception {
        android.content.SharedPreferences prefs=context.getSharedPreferences("account",0);
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,store.getKey("lowfin-session",null),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        JSONObject account=new JSONObject(new String(cipher.doFinal(Base64.decode(prefs.getString("session",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
        byte[] hash=MessageDigest.getInstance("SHA-256").digest((account.getString("server")+"|"+account.getString("user")).getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte value:hash) hex.append(String.format(Locale.ROOT,"%02x",value&255));
        TreeSet<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context.getDatabasePath("library-"+hex.substring(0,24)+".db").getPath(),null,SQLiteDatabase.OPEN_READONLY)) {
            try(Cursor rows=db.rawQuery("SELECT DISTINCT g.name FROM genres g JOIN items i ON i.id=g.item WHERE i.type IN ('Movie','Series') ORDER BY g.name COLLATE NOCASE",null)) {while(rows.moveToNext()) names.add(rows.getString(0));}
            try(Cursor rows=db.rawQuery("SELECT value FROM state WHERE key='genres'",null)) {if(rows.moveToFirst()) {JSONArray genres=new JSONArray(rows.getString(0));for(int i=0;i<genres.length();i++) {JSONObject genre=genres.optJSONObject(i);if(genre!=null&&!genre.optString("Name").isEmpty()) names.add(genre.optString("Name"));}}}
        }
        return new JSONArray(names).toString();
    }
}
