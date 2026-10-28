package de.lowfin.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Read-only on-device check of this package's actual stored TV channel titles. */
public final class ChannelDiagnostics extends Instrumentation {
    private boolean genres,cache,state,pending,denoise,startup,tags;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);tags=arguments!=null&&"true".equals(arguments.getString("tags"));genres=arguments!=null&&"true".equals(arguments.getString("genres"));startup=arguments!=null&&"true".equals(arguments.getString("startup"));denoise=arguments!=null&&"true".equals(arguments.getString("denoise")); pending=arguments!=null&&"true".equals(arguments.getString("pending")); state=arguments!=null&&"true".equals(arguments.getString("state")); cache=arguments!=null&&"true".equals(arguments.getString("cache")); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); Context target=getTargetContext(); StringBuilder rows=new StringBuilder();
        if(tags) {try {result.putString("tags",TagDiagnostics.inspect(target));finish(0,result);}catch(Exception error){result.putString("error",error.getClass().getSimpleName());finish(1,result);}return;}
        if(genres) { try {result.putString("genres",GenreDiagnostics.inspect(target));finish(0,result);}catch(Exception error) {result.putString("error",error.getClass().getSimpleName());finish(1,result);}return;}
        if(startup) { try { result.putString("startup_diagnostics",StartupDiagnostics.inspect(this));finish(0,result); } catch(Exception e) { result.putString("error",e.toString());finish(1,result); }return; }
        if(denoise) { try { result.putString("denoise_diagnostics",DenoiseDiagnostics.inspect(target));finish(0,result); } catch(Exception e) { result.putString("error",e.toString());finish(1,result); }return; }
        if(cache||state||pending) { try { result.putString("cache_diagnostics",CacheDiagnostics.inspect(target,state,pending)); finish(0,result); } catch(Exception e) { result.putString("error",e.toString()); finish(1,result); } return; }
        try(Cursor c=target.getContentResolver().query(Uri.parse("content://android.media.tv/channel"),new String[]{"_id","package_name","internal_provider_id","display_name","browsable"},null,null,null)) {
            if(c!=null) while(c.moveToNext()) if(target.getPackageName().equals(c.getString(1))) rows.append(c.getLong(0)).append(" | ").append(c.getString(2)).append(" | ").append(c.getString(3)).append(" | browsable=").append(c.getInt(4)).append('\n');
            result.putString("channel_titles",rows.toString()); finish(0,result);
        } catch(Exception e) { result.putString("error",e.getClass().getSimpleName()+": "+e.getMessage()); finish(1,result); }
    }
}
