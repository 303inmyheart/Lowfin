package de.lowfin.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.widget.ImageView;
import okhttp3.mockwebserver.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,qualifiers="w960dp-h540dp-land-tvdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class ImagesTest {
    private MockWebServer server; private Api api; private Images images; private Context context;
    private final ExecutorService calls=Executors.newFixedThreadPool(2);
    private final CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
    private byte[] picture;
    private JSONObject item(String id) { return Json.obj("Id",id,"Type","Movie","ImageTags",Json.obj("Primary","tag")); }
    @Before public void setup() throws Exception {
        context=RuntimeEnvironment.getApplication(); context.getSharedPreferences("settings",0).edit().putInt("cacheMb",1024).commit();
        Bitmap bitmap=Bitmap.createBitmap(32,48,Bitmap.Config.RGB_565); bitmap.eraseColor(Color.BLUE); ByteArrayOutputStream out=new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,out); picture=out.toByteArray();
        server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            if(request.getPath().contains("/slow/")) { blocked.countDown(); release.await(); }
            return new MockResponse().setBody(new okio.Buffer().write(picture));
        }}); server.start(); api=new Api(new Account(server.url("/").toString(),"user","token","name","device")); images=new Images(context,api,"image-regression"); images.clear();
    }
    @After public void teardown() throws Exception { release.countDown(); images.close(); calls.shutdownNow(); server.shutdown(); }
    private void awaitImage(ImageView view) throws Exception { long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2); while(view.getDrawable()==null&&System.nanoTime()<until) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10); } assertTrue(view.getDrawable() instanceof BitmapDrawable); }
    @Test public void evictionSnapshotsEachTimestampOnceAndPreservesStableOldestFirstOrder() {
        class Counted extends File {final long age;int reads;Counted(String name,long age){super(name);this.age=age;}@Override public long lastModified(){reads++;return age;}}
        Counted a=new Counted("a",3),b=new Counted("b",1),c=new Counted("c",1),d=new Counted("d",2);File[] files={a,b,c,d};Images.oldestFirst(files);assertArrayEquals(new File[]{b,c,d,a},files);for(Counted f:new Counted[]{a,b,c,d})assertEquals(1,f.reads);
        File[] empty={};Images.oldestFirst(empty);assertEquals(0,empty.length);
    }
    @Test public void memoizedArtworkKeysKeepOriginalHashAndFollowTagChangesWithBoundedMemory() throws Exception {
        JSONObject image=item("key");String first=images.key(image);assertEquals(first,images.key(image));
        byte[] expected=MessageDigest.getInstance("SHA-256").digest(api.image(image,320,false).getBytes(StandardCharsets.UTF_8));StringBuilder encoded=new StringBuilder();for(byte value:expected) encoded.append(String.format("%02x",value));assertEquals(encoded.toString(),first);
        image.put("ImageTags",Json.obj("Primary","updated"));assertNotEquals(first,images.key(image));
        for(int i=0;i<400;i++) images.key(item("key-"+i));android.util.LruCache<?,?> hashes=org.robolectric.util.ReflectionHelpers.getField(images,"keyHashes");assertEquals(256,hashes.size());assertEquals(0,server.getRequestCount());assertEquals(0,images.directory.listFiles(f->f.getName().endsWith(".jpg")).length);
    }
    @Test @Config(qualifiers="w960dp-h540dp-land-xxxhdpi") public void logosRequestDensityScaledPixelsFor4kUi() throws Exception {
        JSONObject film=Json.obj("Id","film","Type","Movie","ImageTags",Json.obj("Logo","tag")); ImageView view=new ImageView(context);
        images.logo(view,film); awaitImage(view);
        RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS); assertNotNull(request);
        assertTrue(request.getRequestUrl().encodedPath().endsWith("/Images/Logo")); assertEquals("960",request.getRequestUrl().queryParameter("MaxWidth"));
    }
    @Test public void channelArtworkComesFromServerAndNeverCreatesPersistentFiles() throws Exception {
        JSONObject channel=Json.obj("Id","channel","Type","TvChannel","ImageTags",Json.obj("Primary","tag","Logo","logo"));
        assertNull(images.fetch(channel)); assertEquals(0,server.getRequestCount());
        ImageView first=new ImageView(context); images.channelLogo(first,channel); awaitImage(first);
        assertEquals(1,server.getRequestCount()); assertEquals(0,images.directory.listFiles(file->file.getName().endsWith(".jpg")).length);
        ImageView second=new ImageView(context); images.channelLogo(second,channel); awaitImage(second); assertEquals(1,server.getRequestCount());
        images.close(); images=new Images(context,api,"image-regression"); images.channelLogo(new ImageView(context),channel);
        assertNotNull(server.takeRequest(2,TimeUnit.SECONDS)); assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));
    }
    @Test public void preloadedFileIsUsedAtTvDensityAndSurvivesImageLoaderRestart() throws Exception {
        File file=images.fetch(item("cached")); assertTrue(file.isFile()); assertEquals("320",server.takeRequest().getRequestUrl().queryParameter("MaxWidth"));
        images.close(); images=new Images(context,api,"image-regression"); ImageView view=new ImageView(context); images.show(view,item("cached")); awaitImage(view);
        assertEquals(1,server.getRequestCount()); assertEquals(file,images.fetch(item("cached"))); assertEquals(1,server.getRequestCount());
    }
    @Test public void slowPrecacheDoesNotBlockDiskHitsOrAnotherDownload() throws Exception {
        images.fetch(item("cached")); Future<File> slow=calls.submit(()->images.fetch(item("slow"))); assertTrue(blocked.await(2,TimeUnit.SECONDS));
        ImageView cached=new ImageView(context); images.show(cached,item("cached")); awaitImage(cached);
        Future<File> fast=calls.submit(()->images.fetch(item("fast"))); assertTrue(fast.get(2,TimeUnit.SECONDS).isFile()); assertFalse(slow.isDone());
        release.countDown(); assertNotNull(slow.get(2,TimeUnit.SECONDS));
    }
    @Test public void sameArtworkDownloadIsSharedBetweenPrecacheAndVisibleCard() throws Exception {
        Future<File> slow=calls.submit(()->images.fetch(item("slow"))); assertTrue(blocked.await(2,TimeUnit.SECONDS));
        ImageView view=new ImageView(context); images.show(view,item("slow")); release.countDown(); assertNotNull(slow.get(2,TimeUnit.SECONDS)); awaitImage(view); assertEquals(1,server.getRequestCount());
    }
    @Test public void old240PixelHdCacheIsReusedInsteadOfRequestedAgain() throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(api.image(item("old"),240,false).getBytes(StandardCharsets.UTF_8)); StringBuilder key=new StringBuilder(); for(byte value:digest) key.append(String.format("%02x",value));
        File legacy=new File(images.directory,key+".jpg"); try(OutputStream output=new FileOutputStream(legacy)) { output.write(picture); }
        ImageView view=new ImageView(context); images.show(view,item("old")); awaitImage(view); assertEquals(legacy,images.fetch(item("old"))); assertEquals(0,server.getRequestCount());
    }
    @Test public void readingCacheSizeDoesNotWaitForMaintenanceHoldingTheImageLock() throws Exception {
        CountDownLatch held=new CountDownLatch(1),unlock=new CountDownLatch(1);Images repository=images;
        Future<?> maintenance=calls.submit(()-> { synchronized(repository) { held.countDown();try { unlock.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } } });
        assertTrue(held.await(2,TimeUnit.SECONDS));
        try { Future<Long> progress=calls.submit(repository::bytes);assertNotNull(progress.get(1,TimeUnit.SECONDS)); }
        finally { unlock.countDown();maintenance.get(2,TimeUnit.SECONDS); }
    }
    @Test public void cacheClearDuringDownloadDoesNotRestoreDeletedCache() throws Exception {
        Future<File> slow=calls.submit(()->images.fetch(item("slow"))); assertTrue(blocked.await(2,TimeUnit.SECONDS)); images.clear(); release.countDown(); assertNull(slow.get(2,TimeUnit.SECONDS));
        assertEquals(0,images.bytes()); assertEquals(0,images.directory.listFiles(file->file.getName().endsWith(".jpg")).length);
    }
    @Test public void SettledDownloadsSaveInventoryAndNextLoaderReusesIt() throws Exception {
        File cached=images.fetch(item("cached"));
        ArtworkInventory inventory=new ArtworkInventory(images.directory);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(inventory.read()==null&&System.nanoTime()<until) Thread.sleep(25);
        assertArrayEquals(new long[]{1,cached.length()},inventory.read());
        images.close(); images=new Images(context,api,"image-regression");
        ImageView view=new ImageView(context); images.show(view,item("cached")); awaitImage(view);
        assertEquals(cached.length(),images.bytes()); assertEquals(1,server.getRequestCount());
    }
    @Test public void RecycledImageNeverReceivesCompletedOldDownload() throws Exception {
        ImageView view=new ImageView(context); images.show(view,item("slow")); assertTrue(blocked.await(2,TimeUnit.SECONDS));
        images.show(view,item("fast")); awaitImage(view); Bitmap current=((BitmapDrawable)view.getDrawable()).getBitmap();
        release.countDown(); Thread.sleep(50); shadowOf(Looper.getMainLooper()).idle();
        assertSame(current,((BitmapDrawable)view.getDrawable()).getBitmap());
    }
    @Test public void memoryHitReplacesArtworkWithoutAnIntermediateEmptyDrawable() throws Exception {
        ImageView first=new ImageView(context);images.show(first,item("ready"));awaitImage(first);
        java.util.concurrent.atomic.AtomicInteger clears=new java.util.concurrent.atomic.AtomicInteger();
        ImageView reused=new ImageView(context) {
            @Override public void setImageDrawable(android.graphics.drawable.Drawable drawable) {
                if(drawable==null) clears.incrementAndGet();super.setImageDrawable(drawable);
            }
        };
        reused.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.RED));clears.set(0);
        images.show(reused,item("ready"));
        assertEquals(0,clears.get());assertSame(((BitmapDrawable)first.getDrawable()).getBitmap(),((BitmapDrawable)reused.getDrawable()).getBitmap());
        assertEquals(1,server.getRequestCount());
        images.show(reused,Json.obj("Id","missing","Type","Movie"));
        assertNull(reused.getDrawable());assertEquals(1,clears.get());
    }
}
