package de.lowfin.tv;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

final class Account {
    final String server,user,token,name,device;
    Account(String server,String user,String token,String name,String device) {
        this.server=server.replaceAll("/+$", ""); this.user=user; this.token=token; this.name=name; this.device=device;
    }
    static String device(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences("account",0);
        String id=p.getString("device",null);
        if(id==null) { id=java.util.UUID.randomUUID().toString(); p.edit().putString("device",id).apply(); }
        return id;
    }
    static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(!ks.containsAlias("lowfin-session")) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("lowfin-session",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); g.generateKey();
        }
        return (SecretKey)ks.getKey("lowfin-session",null);
    }
    void save(Context c) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] data=cipher.doFinal(Json.obj("server",server,"user",user,"token",token,"name",name,"device",device).toString().getBytes(StandardCharsets.UTF_8));
        c.getSharedPreferences("account",0).edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("session",Base64.encodeToString(data,Base64.NO_WRAP)).apply();
    }
    static Account load(Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences("account",0);
        try {
            if(!p.contains("session")) return null;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
            JSONObject j=new JSONObject(new String(cipher.doFinal(Base64.decode(p.getString("session",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
            return new Account(j.getString("server"),j.getString("user"),j.getString("token"),j.getString("name"),j.getString("device"));
        } catch(Exception e) { clear(c); return null; }
    }
    static void clear(Context c) { c.getSharedPreferences("account",0).edit().remove("session").remove("iv").apply(); }
    String scope() {
        try { byte[] b=MessageDigest.getInstance("SHA-256").digest((server+"|"+user).getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder(); for(byte x:b) s.append(String.format("%02x",x)); return s.substring(0,24);
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
