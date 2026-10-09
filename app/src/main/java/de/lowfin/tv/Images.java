package de.lowfin.tv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import okhttp3.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

final class Images {
    final File directory;
    final Api api;
    final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(40),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(6*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getAllocationByteCount(); }
    };
    private long diskBytes;
    Images(Context context,Api api,String scope) {
        this.context=context; this.api=api; directory=new File(context.getFilesDir(),"covers-"+scope); directory.mkdirs();
        workers.execute(()-> { synchronized(this) { File[] files=directory.listFiles(); if(files!=null) for(File f:files) diskBytes+=f.length(); } });
    }
    String key(JSONObject item) { return key(item,false,240); }
    String key(JSONObject item,boolean wide) { return key(item,wide,240); }
    private String key(JSONObject item,boolean wide,int width) {
        try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(api.image(item,width,wide).getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder(); for(byte v:hash) b.append(String.format("%02x",v)); return b.toString();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private int displayWidth(int dp) { return Math.min(1440,Math.max(dp,Ui.dp(context,dp))); }
    void show(ImageView view,JSONObject item) { show(view,item,false,displayWidth(240)); }
    void show(ImageView view,JSONObject item,boolean wide) { show(view,item,wide,displayWidth(240)); }
    void channelLogo(ImageView view,JSONObject item) {
        if(Artwork.logo(item)!=null) logo(view,item);
        else if(api.hasImage(item)) show(view,item,false,320);
        else { view.setTag(null); view.setImageDrawable(null); }
    }
    void logo(ImageView view,JSONObject item) { JSONObject logo=Artwork.logo(item); if(logo==null) { view.setTag(null); view.setImageDrawable(null); } else show(view,logo,false,320); }
    void backdrop(ImageView view,JSONObject item) { JSONObject backdrop=Artwork.backdrop(item); if(backdrop==null) { view.setTag(null); view.setImageDrawable(null); } else show(view,backdrop,true,displayWidth(720)); }
    private void show(ImageView view,JSONObject item,boolean wide,int width) {
        String key=key(item,wide,width); if(key.equals(view.getTag())&&view.getDrawable()!=null) return;
        view.setTag(key); view.setImageDrawable(null); if(!api.hasImage(item,wide)) return;
        Bitmap ready=memory.get(key); if(ready!=null) { view.setImageBitmap(ready); return; }
        workers.execute(()-> {
            if(!key.equals(view.getTag())) return;
            try {
                File f=fetch(item,wide,width); if(f==null) return;
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inPreferredConfig=Bitmap.Config.RGB_565;
                Bitmap bitmap=BitmapFactory.decodeFile(f.getPath(),options); if(bitmap==null) return; memory.put(key,bitmap);
                main.post(()-> { if(key.equals(view.getTag())) view.setImageBitmap(bitmap); });
            } catch(Exception ignored) { }
        });
    }
    synchronized File fetch(JSONObject item) throws Exception { return fetch(item,false,240); }
    synchronized File fetch(JSONObject item,boolean wide) throws Exception { return fetch(item,wide,240); }
    synchronized File fetchBackdrop(JSONObject item) throws Exception { JSONObject backdrop=Artwork.backdrop(item); return backdrop==null?null:fetch(backdrop,true,720); }
    private synchronized File fetch(JSONObject item,boolean wide,int width) throws Exception {
        if(!api.hasImage(item,wide)) return null;
        File target=new File(directory,key(item,wide,width)+".jpg"); if(target.exists()) return target;
        long max=(long)context.getSharedPreferences("settings",0).getInt("cacheMb",1024)*1024*1024;
        if((max>0&&diskBytes>=max)||directory.getUsableSpace()<64L*1024*1024) return null;
        try(Response response=api.http.newCall(api.request(HttpUrl.get(api.image(item,width,wide))).build()).execute()) {
            if(!response.isSuccessful()||response.body()==null) throw new IOException(I18n.text("Cover nicht verfügbar"));
            File temp=new File(directory,key(item,wide,width)+".part");
            try(InputStream in=response.body().byteStream(); OutputStream out=new FileOutputStream(temp)) {
                byte[] buffer=new byte[8192]; int n; long size=0;
                while((n=in.read(buffer))!=-1) { size+=n; if(size>2*1024*1024) throw new IOException(I18n.text("Cover zu groß")); out.write(buffer,0,n); }
            } catch(Exception e) { temp.delete(); throw e; }
            if((max>0&&diskBytes+temp.length()>max)||!temp.renameTo(target)) { temp.delete(); return null; }
            diskBytes+=target.length(); return target;
        }
    }
    synchronized long bytes() { return diskBytes; }
    synchronized void clear() { memory.evictAll(); File[] files=directory.listFiles(); if(files!=null) for(File f:files) f.delete(); diskBytes=0; }
    void close() { workers.shutdownNow(); memory.evictAll(); }
}
