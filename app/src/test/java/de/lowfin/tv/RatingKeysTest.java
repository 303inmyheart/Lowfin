package de.lowfin.tv;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class RatingKeysTest {
    @Test public void keysRoundTripEncryptedAndCanBeRemovedWithoutAffectingOtherProviders() throws Exception {
        android.content.Context c=RuntimeEnvironment.getApplication(); RatingKeys vault=new RatingKeys(c,()->new javax.crypto.spec.SecretKeySpec(new byte[32],"AES"));
        vault.put("omdb","private-omdb-key"); vault.put("tmdb","private-tmdb-key");
        assertEquals("private-omdb-key",vault.get("omdb")); assertFalse(c.getSharedPreferences("rating-keys",0).getAll().toString().contains("private-omdb-key"));
        vault.put("omdb",""); assertEquals("",vault.get("omdb")); assertEquals("private-tmdb-key",vault.get("tmdb"));
    }
}
