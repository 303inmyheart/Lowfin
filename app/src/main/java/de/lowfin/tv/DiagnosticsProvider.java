package de.lowfin.tv;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;

/** Private provider. Android's permission-checked dumpsys path is the sole external interface. */
public final class DiagnosticsProvider extends ContentProvider {
    @Override public boolean onCreate() {return true;}
    @Override public void dump(FileDescriptor fd,PrintWriter writer,String[] args) {
        LowfinApp app=(LowfinApp)getContext().getApplicationContext();
        if(app.diagnostics==null) {writer.println("{\"ready\":false}");return;}
        // The provider is not exported. dumpsys activity performs the system DUMP permission check.
        if(args!=null) for(String arg:args) {
            if("--enable".equals(arg)) app.diagnostics.setEnabled(true);
            else if("--disable".equals(arg)) app.diagnostics.setEnabled(false);
            else if("--clear".equals(arg)) app.diagnostics.clear();
        }
        writer.println(app.diagnostics.snapshot().toString());
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri) {return null;}
    @Override public Uri insert(Uri uri,ContentValues values) {throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) {throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args) {throw new UnsupportedOperationException();}
}
