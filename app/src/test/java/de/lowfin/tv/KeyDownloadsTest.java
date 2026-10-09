package de.lowfin.tv;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class KeyDownloadsTest {
    private android.content.Context context; private MediaProvider provider; private KeyDownloads downloads;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication(); provider=new MediaProvider(); ProviderInfo info=new ProviderInfo(); info.authority=MediaStore.AUTHORITY; info.name=MediaProvider.class.getName(); info.packageName="com.android.providers.media";
        provider.attachInfo(context,info); ShadowContentResolver.registerProviderInternal(info.authority,provider); shadowOf(context.getPackageManager()).addOrUpdateProvider(info); downloads=new KeyDownloads(context);
        context.getSharedPreferences("rating-key-import",0).edit().clear().commit(); context.getSharedPreferences("rating-keys",0).edit().clear().commit();
    }
    @Test public void ownedDownloadIsVisiblePlainTextEditableAndRemovedAfterEncryptedImport() throws Exception {
        RatingKeys keys=new RatingKeys(context,()->new javax.crypto.spec.SecretKeySpec(new byte[32],"AES")); RatingKeyImport importer=new RatingKeyImport(context,keys,downloads);
        assertEquals(RatingKeyImport.State.CREATED,importer.check().state); assertEquals(1,provider.rows.size()); ContentValues entry=provider.rows.values().iterator().next();
        assertEquals("Download/",entry.getAsString("relative_path")); assertEquals("text/plain",entry.getAsString("mime_type")); assertEquals(RatingKeyText.FILE_NAME,entry.getAsString("_display_name")); assertEquals(Integer.valueOf(0),entry.getAsInteger("is_pending"));
        String uri=downloads.find(); try(InputStream input=downloads.open(uri)) { assertTrue(RatingKeyText.parse(input).isEmpty()); }
        try(OutputStream out=context.getContentResolver().openOutputStream(Uri.parse(uri),"w")) { out.write("OMDB_API_KEY=private-key\n".getBytes(StandardCharsets.UTF_8)); }
        assertEquals(RatingKeyImport.State.WAITING,importer.check().state); org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1));
        assertEquals(RatingKeyImport.State.IMPORTED,importer.check().state); assertEquals("private-key",keys.get("omdb")); assertTrue(provider.rows.isEmpty()); assertNull(downloads.find());
    }
    @Test public void recoversHiddenTemplateAfterInterruptedCreation() throws Exception {
        ContentValues values=new ContentValues(); values.put("_display_name",RatingKeyText.FILE_NAME); values.put("relative_path","Download/"); values.put("mime_type","text/plain"); values.put("is_pending",1);
        Uri uri=context.getContentResolver().insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
        assertEquals(uri.toString(),downloads.find()); assertEquals(Integer.valueOf(0),provider.rows.values().iterator().next().getAsInteger("is_pending"));
        try(InputStream input=downloads.open(uri.toString())) { assertTrue(RatingKeyText.parse(input).isEmpty()); }
        assertTrue(provider.file(ContentUris.parseId(uri)).length()>0);
    }
    @Test public void doesNotAdoptAnotherAppsDownload() throws Exception {
        ContentValues values=new ContentValues(); values.put("_display_name",RatingKeyText.FILE_NAME); values.put("relative_path","Download/"); values.put("owner_package_name","another.app");
        context.getContentResolver().insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
        assertNull(downloads.find()); assertEquals(1,provider.rows.size());
    }
    public static final class MediaProvider extends ContentProvider {
        final Map<Long,ContentValues> rows=new LinkedHashMap<>(); long next=1;
        @Override public boolean onCreate() { return true; }
        File file(long id) { return new File(getContext().getCacheDir(),"import-media-"+id+".txt"); }
        @Override public Uri insert(Uri uri,ContentValues input) {
            long id=next++; ContentValues values=new ContentValues(input); values.put("_id",id); if(!values.containsKey("owner_package_name")) values.put("owner_package_name",getContext().getPackageName()); rows.put(id,values);
            try { new FileOutputStream(file(id)).close(); } catch(IOException e) { throw new IllegalStateException(e); }
            return ContentUris.withAppendedId(uri,id);
        }
        @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { ContentValues row=rows.get(ContentUris.parseId(uri)); if(row==null) return 0; row.putAll(values); return 1; }
        @Override public int delete(Uri uri,String selection,String[] args) { long id=ContentUris.parseId(uri); if(rows.remove(id)==null) return 0; file(id).delete(); return 1; }
        @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
            MatrixCursor cursor=new MatrixCursor(projection);
            for(ContentValues row:rows.values()) {
                if(args!=null&&(!args[0].equals(row.getAsString("_display_name"))||!args[1].equals(row.getAsString("relative_path"))||!args[2].equals(row.getAsString("owner_package_name")))) continue;
                Object[] values=new Object[projection.length]; for(int i=0;i<projection.length;i++) values[i]="_size".equals(projection[i])?file(row.getAsLong("_id")).length():row.get(projection[i]); cursor.addRow(values);
            } return cursor;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
            long id=ContentUris.parseId(uri); if(!rows.containsKey(id)) throw new FileNotFoundException(); return ParcelFileDescriptor.open(file(id),mode.contains("w")?ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_TRUNCATE:ParcelFileDescriptor.MODE_READ_ONLY);
        }
        @Override public String getType(Uri uri) { return "text/plain"; }
    }
}
