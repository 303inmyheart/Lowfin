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
    private static final char[] HEX="0123456789abcdef".toCharArray();
    final File directory;
    final Api api;
    final Context context;
    private final LruCache<String,String> keyHashes=new LruCache<>(256);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=pool();
    private final ScheduledExecutorService inventory=Executors.newSingleThreadScheduledExecutor();
    private final ArtworkInventory inventoryIndex;
    private ScheduledFuture<?> inventorySave;
    private final ExecutorService downloads=pool();
    private static ExecutorService pool() { return new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),new ThreadPoolExecutor.DiscardOldestPolicy()); }
    private final ConcurrentHashMap<String,FutureTask<File>> pending=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,FutureTask<Bitmap>> channelPending=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,FutureTask<Bitmap>> decoding=new ConcurrentHashMap<>();
    private final CountDownLatch scanned=new CountDownLatch(1);
    private final OkHttpClient http;
    final CompanionCoverTags coverTags;
    private volatile boolean closed;
    private final long openedAt=SystemClock.elapsedRealtime();
    private final java.util.concurrent.atomic.AtomicBoolean firstReady=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile long diskBytes;
    private long diskFiles,epoch;
    private int activeWrites;
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(6*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getAllocationByteCount(); }
    };
    Images(Context context,Api api,String scope) {
        this.context=context; this.api=api;coverTags=new CompanionCoverTags(context,api); directory=new File(context.getFilesDir(),"covers-"+scope); directory.mkdirs(); inventoryIndex=new ArtworkInventory(directory);
        http=api.http.newBuilder().dispatcher(new Dispatcher()).connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build();
        inventory.execute(()-> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            long generation; synchronized(this) { generation=epoch; }
            long[] saved=inventoryIndex.read(); long size=0; int count=0;
            if(saved!=null) { count=(int)Math.min(Integer.MAX_VALUE,saved[0]); size=saved[1]; }
            else { File[] files=directory.listFiles(); if(files!=null) for(File file:files) if(file.getName().endsWith(".jpg")) { size+=file.length(); count++; } }
            synchronized(this) { if(!closed&&epoch==generation) { diskBytes=size; diskFiles=count; if(saved==null) saveInventory(); } }
            scanned.countDown(); Log.i("LowfinImages","Persistent artwork cache: "+count+" files, "+size/1048576+" MB · "+(saved==null?"recounted":"saved inventory"));
            final int stored=count; final long storedBytes=size;
            main.post(()-> { if(context.getApplicationContext() instanceof LowfinApp) { LowfinApp app=(LowfinApp)context.getApplicationContext(); if(app.images==this&&!app.syncing&&stored>0&&app.syncStatus.equals(I18n.text("Cache noch nicht erstellt"))) app.syncStatus=I18n.text("Gespeicherte Cover: ")+stored+" · "+storedBytes/1048576+" MB"; } });
        });
    }
    String key(JSONObject item) { return key(item,false,displayWidth(240)); }
    String key(JSONObject item,boolean wide) { return key(item,wide,displayWidth(240)); }
    private String key(JSONObject item,boolean wide,int width) {
        String url=api.image(item,width,wide);String cached=keyHashes.get(url);if(cached!=null) return cached;
        try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);
            for(byte value:hash) { result.append(HEX[(value&255)>>>4]); result.append(HEX[value&15]); } String value=result.toString();keyHashes.put(url,value);return value;
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
    void logo(ImageView view,JSONObject item) { JSONObject logo=Artwork.logo(item); if(logo==null) { view.setTag(null); view.setImageDrawable(null); } else show(view,logo,false,displayWidth(240)); }
    void backdrop(ImageView view,JSONObject item) { JSONObject backdrop=Artwork.backdrop(item); if(backdrop==null) { view.setTag(null); view.setImageDrawable(null); } else show(view,backdrop,true,displayWidth(720)); }
    private void show(ImageView view,JSONObject item,boolean wide,int width) {
        if(view instanceof ArtworkView) coverTags.bind((ArtworkView)view,item,wide);
        String key=key(item,wide,width); if(key.equals(view.getTag())&&view.getDrawable()!=null) return;
        view.setTag(key); if(closed||!api.hasImage(item,wide)) { view.setImageDrawable(null);return; }
        Bitmap ready=memory.get(key); if(ready!=null) { view.setImageBitmap(ready); firstShown(); return; }
        view.setImageDrawable(null);
        if("TvChannel".equals(item.optString("Type"))) {
            downloads.execute(()-> {
                if(closed||!key.equals(view.getTag())) return;
                try {
                    Bitmap bitmap=channelBitmap(item,wide,width,key);
                    if(bitmap!=null) main.post(()-> { if(!closed&&key.equals(view.getTag())) { view.setImageBitmap(bitmap); firstShown(); } });
                } catch(Exception e) { if(!closed) Log.w("LowfinImages","Channel logo unavailable: "+e.getClass().getSimpleName()); }
            });
            return;
        }
        workers.execute(()-> {
            if(closed||!key.equals(view.getTag())) return;
            File cached=cached(item,wide,width,true); if(cached!=null) { decode(view,key,cached,width); return; }
            downloads.execute(()-> {
                if(closed||!key.equals(view.getTag())) return;
                try { File file=fetch(item,wide,width,true); if(file!=null&&!closed) workers.execute(()->decode(view,key,file,width)); }
                catch(Exception e) { if(!closed) Log.w("LowfinImages","Artwork download unavailable: "+e.getClass().getSimpleName()); }
            });
        });
    }
    /** Sender artwork is fetched on demand and never written to the persistent cover directory. */
    private Bitmap channelBitmap(JSONObject item,boolean wide,int width,String key) throws Exception {
        Bitmap ready=memory.get(key); if(ready!=null) return ready;
        FutureTask<Bitmap> task=new FutureTask<>(()-> {
            try(Response response=http.newCall(api.request(HttpUrl.get(api.image(item,width,wide))).build()).execute()) {
                if(!response.isSuccessful()||response.body()==null) throw new IOException("Channel image HTTP "+response.code());
                ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] block=new byte[8192]; int count;
                try(InputStream input=response.body().byteStream()) { while((count=input.read(block))!=-1) { if(bytes.size()+count>2*1024*1024) throw new IOException("Channel image too large"); bytes.write(block,0,count); } }
                byte[] data=bytes.toByteArray(); BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,options); options.inJustDecodeBounds=false; options.inSampleSize=1; options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                while(options.outWidth/options.inSampleSize>width*2) options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options); if(bitmap!=null&&!closed) memory.put(key,bitmap); return bitmap;
            }
        });
        FutureTask<Bitmap> existing=channelPending.putIfAbsent(key,task); if(existing!=null) return existing.get();
        try { task.run(); return task.get(); } finally { channelPending.remove(key,task); }
    }
    private void decode(ImageView view,String key,File file,int width) {
        if(closed||!key.equals(view.getTag())) return;
        Bitmap bitmap=memory.get(key);
        if(bitmap==null) {
            FutureTask<Bitmap> task=new FutureTask<>(()-> {
                Bitmap ready=memory.get(key); if(ready!=null) return ready;
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true; BitmapFactory.decodeFile(file.getPath(),options);
                options.inJustDecodeBounds=false; options.inSampleSize=1; options.inPreferredConfig=Bitmap.Config.RGB_565;
                while(options.outWidth/options.inSampleSize>width*2) options.inSampleSize*=2;
                Bitmap decoded=BitmapFactory.decodeFile(file.getPath(),options); if(decoded!=null&&!closed) memory.put(key,decoded); return decoded;
            });
            FutureTask<Bitmap> existing=decoding.putIfAbsent(key,task);
            try { if(existing==null) { task.run(); bitmap=task.get(); } else bitmap=existing.get(); }
            catch(InterruptedException error) { Thread.currentThread().interrupt(); return; }
            catch(ExecutionException error) { return; }
            finally { if(existing==null) decoding.remove(key,task); }
            if(bitmap==null||closed) return;
        }
        final Bitmap ready=bitmap; main.post(()-> { if(!closed&&key.equals(view.getTag())) { view.setImageBitmap(ready); firstShown(); } });
    }
    private void firstShown() { if(firstReady.compareAndSet(false,true)) Log.i("LowfinImages","First artwork shown after "+(SystemClock.elapsedRealtime()-openedAt)+" ms"); }
    private File cached(JSONObject item,boolean wide,int width) { return cached(item,wide,width,false); }
    private File cached(JSONObject item,boolean wide,int width,boolean visible) {
        File target=new File(directory,key(item,wide,width)+".jpg");
        // Existing HD precache files are already larger than the small library cards.
        boolean exists=target.isFile();
        if(!exists&&width>240&&width<=320) {target=new File(directory,key(item,wide,240)+".jpg");exists=target.isFile();}
        if(!exists||target.length()==0) return null;
        long now=System.currentTimeMillis(); if(visible&&now-target.lastModified()>3600000) target.setLastModified(now); return target;
    }
    File fetch(JSONObject item) throws Exception { return fetch(item,false,displayWidth(240),false); }
    File fetch(JSONObject item,boolean wide) throws Exception { return fetch(item,wide,displayWidth(240),false); }
    File fetchBackdrop(JSONObject item) throws Exception { JSONObject backdrop=Artwork.backdrop(item); return backdrop==null?null:fetch(backdrop,true,displayWidth(720),false); }
    private File fetch(JSONObject item,boolean wide,int width,boolean foreground) throws Exception {
        if(closed||"TvChannel".equals(item.optString("Type"))||!api.hasImage(item,wide)) return null;
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
        synchronized(this) { inventoryIndex.beginMutation(); activeWrites++; }
        File target=new File(directory,key(item,wide,width)+".jpg"),temp=null;
        long began=SystemClock.elapsedRealtime();
        try {
            temp=File.createTempFile("artwork-",".part",directory);
            try(Response response=http.newCall(api.request(HttpUrl.get(api.image(item,width,wide))).build()).execute()) {
                if(!response.isSuccessful()||response.body()==null) throw new IOException("Artwork HTTP "+response.code());
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
                    if(files!=null) { oldestFirst(files); for(File file:files) { long size=file.length(); if(file.delete()) { diskBytes=Math.max(0,diskBytes-size); diskFiles=Math.max(0,diskFiles-1); } if(diskBytes+temp.length()<=max) break; } }
                    if(diskBytes+temp.length()>max) return null;
                }
                long size=temp.length(); if(size==0||!temp.renameTo(target)) return null; diskBytes+=size; diskFiles++;
            }
            long elapsed=SystemClock.elapsedRealtime()-began; if(elapsed>1000) Log.i("LowfinImages","Artwork download: "+elapsed+" ms");
            return target;
        } finally { synchronized(this) { if(temp!=null) temp.delete(); activeWrites--; queueInventorySave(); } }
    }
    private void saveInventory() { try { inventoryIndex.save(diskFiles,diskBytes); } catch(IOException error) { Log.w("LowfinImages","Inventory will be recounted on next launch"); } }
    private static final class FileAge {final File file;final long modified;FileAge(File file) {this.file=file;modified=file.lastModified();}}
    /** Snapshot each filesystem timestamp once, rather than stat() on every sort comparison. */
    static void oldestFirst(File[] files) {
        FileAge[] ages=new FileAge[files.length];for(int i=0;i<files.length;i++) ages[i]=new FileAge(files[i]);
        Arrays.sort(ages,Comparator.comparingLong(age->age.modified));for(int i=0;i<files.length;i++) files[i]=ages[i].file;
    }
    private synchronized void queueInventorySave() {
        if(closed||inventory.isShutdown()) return;
        if(inventorySave!=null) inventorySave.cancel(false);
        inventorySave=inventory.schedule(()-> { synchronized(this) { if(!closed&&scanned.getCount()==0&&activeWrites==0) saveInventory(); } },2,TimeUnit.SECONDS);
    }
    // Background publisher must observe a completed clear, not its half-deleted directory.
    synchronized long cacheGeneration() { return epoch; }
    long bytes() { return diskBytes; }
    synchronized void clear() { try { inventoryIndex.beginMutation(); } catch(IOException error) { Log.w("LowfinImages","Unable to mark inventory dirty"); return; } epoch++; memory.evictAll(); File[] files=directory.listFiles(); diskBytes=0; diskFiles=0; if(files!=null) for(File file:files) { if(!file.delete()&&file.isFile()&&file.getName().endsWith(".jpg")) { diskBytes+=file.length(); diskFiles++; } } queueInventorySave(); }
    synchronized void close() { closed=true;coverTags.close(); http.dispatcher().cancelAll(); downloads.shutdownNow(); workers.shutdownNow(); inventory.shutdown(); memory.evictAll(); keyHashes.evictAll(); scanned.countDown(); }
}
