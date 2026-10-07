package de.lowfin.tv;

import android.content.Context;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

/** API keys share the Android Keystore protection used for the session token. */
final class RatingKeys {
    interface KeySource { javax.crypto.SecretKey get() throws Exception; }
    private final KeySource keys;
    private final android.content.SharedPreferences saved;
    RatingKeys(Context context) { this(context,Account::key); }
    RatingKeys(Context context,KeySource keys) { this.keys=keys; saved=context.getSharedPreferences("rating-keys",0); }
    String get(String provider) {
        try {
            if(!saved.contains(provider)) return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,keys.get(),new GCMParameterSpec(128,Base64.decode(saved.getString(provider+"-iv",""),Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(saved.getString(provider,""),Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch(Exception ignored) { return ""; }
    }
    void put(String provider,String value) throws Exception {
        if(value.trim().isEmpty()) { saved.edit().remove(provider).remove(provider+"-iv").commit(); return; }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,keys.get());
        saved.edit().putString(provider,Base64.encodeToString(cipher.doFinal(value.trim().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP))
            .putString(provider+"-iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit();
    }
}
