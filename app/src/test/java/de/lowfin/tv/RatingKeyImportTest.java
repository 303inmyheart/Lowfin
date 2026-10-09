package de.lowfin.tv;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class RatingKeyImportTest {
    private Context context; private RatingKeys keys; private Files files; private RatingKeyImport importer;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication(); context.getSharedPreferences("rating-keys",0).edit().clear().commit(); context.getSharedPreferences("rating-key-import",0).edit().clear().commit();
        keys=new RatingKeys(context,()->new javax.crypto.spec.SecretKeySpec(new byte[32],"AES")); files=new Files(); importer=new RatingKeyImport(context,keys,files);
    }
    private RatingKeyImport.Result importStable() { importer.check(); org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1)); return importer.check(); }
    private void fill(String text) { files.content=text.getBytes(StandardCharsets.UTF_8); }
    @Test public void startupCreatesOneFormattedTemplateAndImportsEncryptedKeysBeforeDeleting() throws Exception {
        assertEquals(RatingKeyImport.State.CREATED,importer.check().state); assertEquals(1,files.creates);
        assertTrue(RatingKeyText.parse(new ByteArrayInputStream(files.content)).isEmpty());
        assertEquals(RatingKeyImport.State.WAITING,importer.check().state); assertEquals(1,files.creates);
        fill("OMDB_API_KEY=omdb-private\nMDBLIST_API_KEY=mdb-private\nTMDB_API_KEY=token==\n");
        files.beforeDelete=()-> { assertEquals("omdb-private",keys.get("omdb")); assertEquals("mdb-private",keys.get("mdblist")); assertFalse(context.getSharedPreferences("rating-keys",0).getAll().toString().contains("private")); };
        RatingKeyImport.Result result=importStable(); assertEquals(RatingKeyImport.State.IMPORTED,result.state); assertTrue(result.changed); assertNull(files.content);
        assertEquals(RatingKeyImport.State.CONFIGURED,importer.check().state); assertEquals(1,files.creates);
        assertEquals("token==",new RatingKeys(context,()->new javax.crypto.spec.SecretKeySpec(new byte[32],"AES")).get("tmdb"));
    }
    @Test public void oneExistingProviderDisablesCreationAndDoesNotOverwriteAnyKeys() throws Exception {
        keys.put("omdb","existing"); assertEquals(RatingKeyImport.State.CONFIGURED,importer.check().state); assertEquals(0,files.creates);
        fill("TMDB_API_KEY=new\n"); assertEquals(RatingKeyImport.State.CONFIGURED,importer.selected("test:keys").state); assertEquals("existing",keys.get("omdb")); assertEquals("",keys.get("tmdb")); assertNotNull(files.content);
    }
    @Test public void invalidOrUnsavableInputIsRetainedAndNoPartialKeysAreStored() throws Exception {
        importer.check(); fill("OMDB_API_KEY=private\nINVALID=another\n"); assertEquals(RatingKeyImport.State.INVALID,importer.check().state); assertFalse(keys.hasAny()); assertEquals(0,files.deletes);
        RatingKeys failing=new RatingKeys(context,()->{ throw new IOException("Unavailable keystore"); }); importer=new RatingKeyImport(context,failing,files);
        fill("OMDB_API_KEY=private\nTMDB_API_KEY=other\n"); assertEquals(RatingKeyImport.State.ERROR,importStable().state); assertFalse(keys.hasAny()); assertNotNull(files.content); assertEquals(0,files.deletes);
    }
    @Test public void failedDeletionRetriesAfterRestartWithoutRecreatingOrImportingAgain() throws Exception {
        importer.check(); fill("OMDB_API_KEY=private\n"); files.allowDelete=false;
        RatingKeyImport.Result first=importStable(); assertEquals(RatingKeyImport.State.DELETE_PENDING,first.state); assertTrue(first.changed); assertNotNull(files.content);
        files.allowDelete=true; importer=new RatingKeyImport(context,keys,files); RatingKeyImport.Result retry=importer.check(); assertEquals(RatingKeyImport.State.IMPORTED,retry.state); assertFalse(retry.changed); assertEquals(1,files.creates);
    }
    @Test public void changingFileIsNeverSavedFromAnUnstableSnapshot() throws Exception {
        importer.check(); fill("OMDB_API_KEY=first\n"); files.changeOnSecondRead=true;
        assertEquals(RatingKeyImport.State.WAITING,importer.check().state);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
        assertEquals(RatingKeyImport.State.WAITING,importer.check().state);
        assertFalse(keys.hasAny()); assertNotNull(files.content); assertEquals(0,files.deletes);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
        assertEquals(RatingKeyImport.State.IMPORTED,importer.check().state); assertEquals("edited",keys.get("omdb"));
    }
    static final class Files implements RatingKeyImport.Documents {
        byte[] content; int creates,deletes,reads; boolean allowDelete=true,changeOnSecondRead; Runnable beforeDelete=()->{};
        @Override public String find() { return content==null?null:"test:keys"; }
        @Override public String create(byte[] template) { creates++; content=template; return "test:keys"; }
        @Override public InputStream open(String uri) throws IOException {
            if(content==null) throw new FileNotFoundException(); reads++; if(changeOnSecondRead&&reads==2) content="OMDB_API_KEY=edited\n".getBytes(StandardCharsets.UTF_8); return new ByteArrayInputStream(content);
        }
        @Override public boolean delete(String uri) { beforeDelete.run(); deletes++; if(!allowDelete) return false; content=null; return true; }
    }
}
