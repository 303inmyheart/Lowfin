package de.lowfin.tv;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class RatingKeysTest {
    @Test public void batchEncryptionFailureCannotSaveOnlyTheFirstProvider() throws Exception {
        android.content.Context c=RuntimeEnvironment.getApplication(); final int[] requests={0};
        RatingKeys vault=new RatingKeys(c,()-> { if(++requests[0]>1) throw new java.io.IOException("Keystore failure"); return new javax.crypto.spec.SecretKeySpec(new byte[32],"AES"); });
        java.util.Map<String,String> values=new java.util.LinkedHashMap<>(); values.put("omdb","first-private"); values.put("tmdb","second-private");
        try { vault.importIfEmpty(values); fail("Encryption should fail"); } catch(java.io.IOException expected) { }
        assertFalse(vault.hasAny()); assertTrue(c.getSharedPreferences("rating-keys",0).getAll().isEmpty());
    }
    @Test public void keysRoundTripEncryptedAndCanBeRemovedWithoutAffectingOtherProviders() throws Exception {
        android.content.Context c=RuntimeEnvironment.getApplication(); RatingKeys vault=new RatingKeys(c,()->new javax.crypto.spec.SecretKeySpec(new byte[32],"AES"));
        vault.put("omdb","private-omdb-key"); vault.put("tmdb","private-tmdb-key");
        assertEquals("private-omdb-key",vault.get("omdb")); assertFalse(c.getSharedPreferences("rating-keys",0).getAll().toString().contains("private-omdb-key"));
        vault.put("omdb",""); assertEquals("",vault.get("omdb")); assertEquals("private-tmdb-key",vault.get("tmdb"));
    }
    @Test public void RepeatedReadsAvoidKeystoreWorkAndExternalUpdatesInvalidateMemory() throws Exception {
        android.content.Context c=RuntimeEnvironment.getApplication(); final int[] requests={0};
        javax.crypto.SecretKey key=new javax.crypto.spec.SecretKeySpec(new byte[32],"AES");
        RatingKeys vault=new RatingKeys(c,()-> { requests[0]++; return key; });
        vault.put("omdb","first"); assertEquals("first",vault.get("omdb")); int afterFirst=requests[0];
        for(int i=0;i<100;i++) assertEquals("first",vault.get("omdb")); assertEquals(afterFirst,requests[0]);
        RatingKeys other=new RatingKeys(c,()->key); other.put("omdb","replacement");
        assertEquals("replacement",vault.get("omdb")); assertEquals(afterFirst+1,requests[0]);
        other.put("omdb",""); assertEquals("",vault.get("omdb"));
        assertFalse(c.getSharedPreferences("rating-keys",0).getAll().toString().contains("replacement"));
    }
}
