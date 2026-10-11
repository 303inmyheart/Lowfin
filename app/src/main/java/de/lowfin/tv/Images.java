package de.lowfin.tv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.util.Log;
import android.widget.ImageView;
import okhttp3.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Disk hits and decoding never wait behind network/precache downloads. */
final class Images {
    final File directory;
    final Api api;
    final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=pool();
    private final ExecutorService downloads=pool();
    private static ExecutorService pool() { return new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),new ThreadPoolExecutor.DiscardOldestPolicy()); }
    private final ConcurrentHashMap<String,FutureTask<File>> pending=new ConcurrentHashMap<>();
    private final CountDownLatch scanned=new CountDownLatch(1);
    private final OkHttpClient http;
    private volatile boolean closed;
    private long diskBytes,epoch;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(6*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getAllocationByteCount(); }
    };
    Images(Context context,Api api,String scope) {
        this.context=context; this.api=api; directory=new File(context.getFilesDir(),"covers-"+scope); directory.mkdirs();
        http=api.http.newBuilder().dispatcher(new Dispatcher()).connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build();
        workers.execute(()-> {
            long generation; synchronized(this) { generation=epoch; }
            long size=0; int count=0; File[] files=directory.listFiles();
            if(files!=null) for(File file:files) if(file.getName().endsWith(".jpg")) { size+=file.length(); count++; }
            synchronized(this) { if(epoch==generation) diskBytes=size; }
            scanned.countDown(); Log.i("LowfinImages","Persistent artwork cache: "+count+" files, "+size/1048576+" MB");
            final int stored=count; final long storedBytes=size;
            main.post(()-> { if(context.getApplicationContext() instanceof LowfinApp) { LowfinApp app=(LowfinApp)context.getApplicationContext(); if(app.images==this&&!app.syncing&&stored>0&&app.syncStatus.equals(I18n.text("Cache noch nicht erstellt"))) app.syncStatus=I18n.text("Gespeicherte Cover: ")+stored+" · "+storedBytes/1048576+" MB"; } });
        });
    }
    String key(JSONObject item) { return key(item,false,displayWidth(240)); }
    String key(JSONObject item,boolean wide) { return key(item,wide,displayWidth(240)); }
    private String key(JSONObject item,boolean wide,int width) {
        try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(api.image(item,width,wide).getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64); final char[] hex="0123456789abcdef".toCharArray();
            for(byte value:hash) { result.append(hex[(value&255)>>>4]); result.append(hex[value&15]); } return result.toString();
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
        view.setTag(key); view.setImageDrawable(null); if(closed||!api.hasImage(item,wide)) return;
        Bitmap ready=memory.get(key); if(ready!=null) { view.setImageBitmap(ready); return; }
        workers.execute(()-> {
            if(closed||!key.equals(view.getTag())) return;
            File cached=cached(item,wide,width); if(cached!=null) { decode(view,key,cached,width); return; }
            downloads.execute(()-> {
                if(closed||!key.equals(view.getTag())) return;
                try { File file=fetch(item,wide,width,true); if(file!=null&&!closed) workers.execute(()->decode(view,key,file,width)); }
                catch(Exception e) { if(!closed) Log.w("LowfinImages","Artwork download unavailable: "+e.getClass().getSimpleName()); }
            });
        });
    }
    private void decode(ImageView view,String key,File file,int width) {
        if(closed||!key.equals(view.getTag())) return;
        Bitmap bitmap=memory.get(key);
        if(bitmap==null) {
            BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true; BitmapFactory.decodeFile(file.getPath(),options);
            options.inJustDecodeBounds=false; options.inSampleSize=1; options.inPreferredConfig=Bitmap.Config.RGB_565;
            while(options.outWidth/options.inSampleSize>width*2) options.inSampleSize*=2;
            bitmap=BitmapFactory.decodeFile(file.getPath(),options); if(bitmap==null||closed) return; memory.put(key,bitmap);
        }
        final Bitmap ready=bitmap; main.post(()-> { if(!closed&&key.equals(view.getTag())) view.setImageBitmap(ready); });
    }
    private File cached(JSONObject item,boolean wide,int width) {
        File target=new File(directory,key(item,wide,width)+".jpg");
        // Existing HD precache files are already larger than the small library cards.
        if(!target.isFile()&&width>240&&width<=320) target=new File(directory,key(item,wide,240)+".jpg");
        if(!target.isFile()||target.length()==0) return null;
        target.setLastModified(System.currentTimeMillis()); return target;
    }
    File fetch(JSONObject item) throws Exception { return fetch(item,false,displayWidth(240),false); }
    File fetch(JSONObject item,boolean wide) throws Exception { return fetch(item,wide,displayWidth(240),false); }
    File fetchBackdrop(JSONObject item) throws Exception { JSONObject backdrop=Artwork.backdrop(item); return backdrop==null?null:fetch(backdrop,true,displayWidth(720),false); }
    private File fetch(JSONObject item,boolean wide,int width,boolean foreground) throws Exception {
        if(closed||!api.hasImage(item,wide)) return null;
        File cached=cached(item,wide,width); if(cached!=null) return cached;
        String key=key(item,wide,width); FutureTask<File> task=new FutureTask<>(()->download(item,wide,width,foreground));
        FutureTask<File> existing=pending.putIfAbsent(key,task);
        if(existing!=null) return existing.get();
        try { task.run(); return task.get(); } finally { pending.remove(key,task); }
    }
    private File download(JSONObject item,boolean wide,int width,boolean foreground) throws Exception {
        scanned.await(); if(closed) return null;
        File cached=cached(item,wide,width); if(cached!=null) return cached;
        long max=(long)context.getSharedPreferences("settings",0).getInt("cacheMb",1024)*1024*1024;
        long generation; synchronized(this) { generation=epoch; if(!foreground&&max>0&&diskBytes>=max) return null; }
        if(directory.getUsableSpace()<64L*1024*1024) return null;
        File target=new File(directory,key(item,wide,width)+".jpg"),temp=File.createTempFile("artwork-",".part",directory);
        long began=SystemClock.elapsedRealtime();
        try {
            try(Response response=http.newCall(api.request(HttpUrl.get(api.image(item,width,wide))).build()).execute()) {
                if(!response.isSuccessful()||response.body()==null) throw new IOException("Artwork HTTP failure");
                try(InputStream input=response.body().byteStream(); OutputStream output=new FileOutputStream(temp)) {
                    byte[] block=new byte[8192]; int n; long size=0;
                    while((n=input.read(block))!=-1) { size+=n; if(size>2*1024*1024) throw new IOException("Artwork too large"); output.write(block,0,n); }
                }
            }
            synchronized(this) {
                if(closed||epoch!=generation) return null;
                if(target.isFile()&&target.length()>0) return target;
                if(max>0&&diskBytes+temp.length()>max) {
                    if(!foreground) return null;
                    File[] files=directory.listFiles(file->file.getName().endsWith(".jpg"));
                    if(files!=null) { Arrays.sort(files,Comparator.comparingLong(File::lastModified)); for(File file:files) { long size=file.length(); if(file.delete()) diskBytes=Math.max(0,diskBytes-size); if(diskBytes+temp.length()<=max) break; } }
                    if(diskBytes+temp.length()>max) return null;
                }
                long size=temp.length(); if(size==0||!temp.renameTo(target)) return null; diskBytes+=size;
            }
            long elapsed=SystemClock.elapsedRealtime()-began; if(elapsed>1000) Log.i("LowfinImages","Artwork download: "+elapsed+" ms");
            return target;
        } finally { temp.delete(); }
    }
    synchronized long bytes() { return diskBytes; }
    synchronized void clear() { epoch++; memory.evictAll(); File[] files=directory.listFiles(); if(files!=null) for(File file:files) file.delete(); diskBytes=0; }
    void close() { closed=true; http.dispatcher().cancelAll(); downloads.shutdownNow(); workers.shutdownNow(); memory.evictAll(); scanned.countDown(); }
}
