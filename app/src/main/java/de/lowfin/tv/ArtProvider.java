package de.lowfin.tv;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.List;

/** Exposes only cached artwork of the current account to the TV launcher. */
public final class ArtProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!mode.equals("r")) throw new FileNotFoundException("Read only");
        LowfinApp app=(LowfinApp)getContext().getApplicationContext(); List<String> parts=uri.getPathSegments();
        if(app.account==null||parts.size()!=2||!parts.get(0).equals(app.account.scope())||!parts.get(1).matches("[a-f0-9]{64}\\.jpg")) throw new FileNotFoundException("Artwork unavailable");
        File target=new File(new File(getContext().getFilesDir(),"covers-"+parts.get(0)),parts.get(1));
        if(!target.isFile()) throw new FileNotFoundException("Artwork unavailable");
        return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) { return null; }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri,ContentValues values,String where,String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri,String where,String[] args) { throw new UnsupportedOperationException(); }
}
