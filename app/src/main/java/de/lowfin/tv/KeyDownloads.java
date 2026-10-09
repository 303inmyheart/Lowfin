package de.lowfin.tv;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.nio.file.*;

/** Own Download entries on Android 10+, legacy storage only on Android 8/9. */
final class KeyDownloads implements RatingKeyImport.Documents {
    private final Context context;
    KeyDownloads(Context context) { this.context=context; }
    @androidx.annotation.RequiresApi(29)
    private Uri collection() { return MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY); }
    private File legacy() throws IOException {
        File file=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),RatingKeyText.FILE_NAME);
        if(Files.isSymbolicLink(file.toPath())) throw new IOException("Invalid import file"); return file;
    }
    @Override public String find() throws IOException {
        if(Build.VERSION.SDK_INT<29) { File file=legacy(); return file.isFile()?Uri.fromFile(file).toString():null; }
        try(Cursor cursor=context.getContentResolver().query(collection(),new String[]{MediaStore.MediaColumns._ID,MediaStore.MediaColumns.IS_PENDING,MediaStore.MediaColumns.SIZE},
            MediaStore.MediaColumns.DISPLAY_NAME+"=? AND "+MediaStore.MediaColumns.RELATIVE_PATH+"=? AND "+MediaStore.MediaColumns.OWNER_PACKAGE_NAME+"=?",
            new String[]{RatingKeyText.FILE_NAME,Environment.DIRECTORY_DOWNLOADS+"/",context.getPackageName()},null)) {
            if(cursor==null||!cursor.moveToFirst()) return null;
            Uri uri=ContentUris.withAppendedId(collection(),cursor.getLong(0));
            if(cursor.getInt(1)!=0) {
                // Finish a creation interrupted by process death before publishing the file.
                if(cursor.getLong(2)==0) try(InputStream template=context.getResources().openRawResource(I18n.language().equals("de")?R.raw.rating_keys_template_de:R.raw.rating_keys_template_en); OutputStream out=context.getContentResolver().openOutputStream(uri,"w")) {
                    if(out==null) throw new IOException("Cannot recover import template"); byte[] block=new byte[1024]; int n; while((n=template.read(block))!=-1) out.write(block,0,n);
                }
                ContentValues ready=new ContentValues(); ready.put(MediaStore.MediaColumns.IS_PENDING,0); if(context.getContentResolver().update(uri,ready,null,null)!=1) throw new IOException("Cannot publish recovered template");
            }
            return uri.toString();
        }
    }
    @Override public String create(byte[] template) throws IOException {
        if(Build.VERSION.SDK_INT<29) {
            File file=legacy(); File parent=file.getParentFile(); if(!parent.isDirectory()&&!parent.mkdirs()) throw new IOException("Downloads unavailable");
            try(OutputStream out=Files.newOutputStream(file.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) { out.write(template); }
            return Uri.fromFile(file).toString();
        }
        ContentResolver resolver=context.getContentResolver(); ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,RatingKeyText.FILE_NAME); values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/"); values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=resolver.insert(collection(),values); if(uri==null) throw new IOException("Cannot create import template");
        try {
            try(OutputStream out=resolver.openOutputStream(uri,"w")) { if(out==null) throw new IOException("Cannot write import template"); out.write(template); }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING,0); if(resolver.update(uri,values,null,null)!=1) throw new IOException("Cannot publish import template");
            return uri.toString();
        } catch(Exception e) { try { resolver.delete(uri,null,null); } catch(Exception ignored) { } throw new IOException("Cannot create import template"); }
    }
    @Override public InputStream open(String document) throws IOException {
        Uri uri=Uri.parse(document);
        if("file".equals(uri.getScheme())) { if(Build.VERSION.SDK_INT>=29||!legacy().equals(new File(uri.getPath()))) throw new IOException("Invalid import location"); return new FileInputStream(legacy()); }
        InputStream input=context.getContentResolver().openInputStream(uri); if(input==null) throw new FileNotFoundException("Import file unavailable"); return input;
    }
    @Override public boolean delete(String document) throws IOException {
        Uri uri=Uri.parse(document);
        if("file".equals(uri.getScheme())) { File file=legacy(); if(Build.VERSION.SDK_INT>=29||!file.equals(new File(uri.getPath()))) throw new IOException("Invalid import location"); return !file.exists()||file.delete(); }
        if(DocumentsContract.isDocumentUri(context,uri)) return DocumentsContract.deleteDocument(context.getContentResolver(),uri);
        return context.getContentResolver().delete(uri,null,null)>0;
    }
}
