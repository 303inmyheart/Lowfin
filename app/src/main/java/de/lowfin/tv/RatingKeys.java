package de.lowfin.tv;

import android.content.Context;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** API keys share the Android Keystore protection used for the session token. */
final class RatingKeys {
    interface KeySource { javax.crypto.SecretKey get() throws Exception; }
    private final KeySource keys;
    private final android.content.SharedPreferences saved;
    RatingKeys(Context context) { this(context,Account::key); }
    RatingKeys(Context context,KeySource keys) { this.keys=keys; saved=context.getSharedPreferences("rating-keys",0); }
    private final Map<String,String[]> decoded=new LinkedHashMap<>();
    synchronized String get(String provider) {
        try {
            if(!saved.contains(provider)) { decoded.remove(provider); return ""; }
            String encrypted=saved.getString(provider,""),iv=saved.getString(provider+"-iv","");
            String[] cached=decoded.get(provider); if(cached!=null&&cached[0].equals(encrypted)&&cached[1].equals(iv)) return cached[2];
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,keys.get(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
            String value=new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
            decoded.put(provider,new String[]{encrypted,iv,value}); while(decoded.size()>64) decoded.remove(decoded.keySet().iterator().next()); return value;
        } catch(Exception ignored) { decoded.remove(provider); return ""; }
    }
    boolean hasAny() { for(String provider:new String[]{"omdb","mdblist","tmdb","tracearr"}) if(saved.contains(provider)) return true; for(String provider:saved.getAll().keySet()) if(provider.matches("tracearr-[a-zA-Z0-9]+")) return true; return false; }
    boolean matches(Map<String,String> values) { return !values.isEmpty()&&values.entrySet().stream().allMatch(e->e.getValue().equals(get(e.getKey()))); }
    void put(String provider,String value) throws Exception {
        synchronized(RatingKeys.class) { store(Collections.singletonMap(provider,value)); }
    }
    boolean importFilled(Map<String,String> values) throws Exception { synchronized(RatingKeys.class) { if(values.isEmpty()) return false; store(values); if(!matches(values)) throw new java.io.IOException("Key verification failed"); return true; } }
    boolean importIfEmpty(Map<String,String> values) throws Exception {
        synchronized(RatingKeys.class) { if(hasAny()||values.isEmpty()) return false; store(values); if(!matches(values)) throw new java.io.IOException("Key verification failed"); return true; }
    }
    private void store(Map<String,String> values) throws Exception {
        Map<String,?> before=new HashMap<>(saved.getAll());
        android.content.SharedPreferences.Editor edit=saved.edit();
        // Encrypt every value before mutating preferences: an encryption failure writes nothing.
        for(Map.Entry<String,String> entry:values.entrySet()) {
            String provider=entry.getKey(),value=entry.getValue().trim();
            if(!Arrays.asList("omdb","mdblist","tmdb","tracearr","tracearr_url","tracearr_server_id").contains(provider)&&!provider.startsWith("tracearr-")) throw new IllegalArgumentException("Unknown provider");
            if(value.isEmpty()) { edit.remove(provider).remove(provider+"-iv"); continue; }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,keys.get());
            edit.putString(provider,Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)).putString(provider+"-iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP));
        }
        if(!edit.commit()) {
            // SharedPreferences can update memory even when its disk commit fails.
            android.content.SharedPreferences.Editor rollback=saved.edit().clear();
            for(Map.Entry<String,?> entry:before.entrySet()) if(entry.getValue() instanceof String) rollback.putString(entry.getKey(),(String)entry.getValue());
            rollback.commit(); throw new java.io.IOException("Key storage failed");
        }
    }
}
