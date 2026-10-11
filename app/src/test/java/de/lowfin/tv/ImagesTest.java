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
    @Test public void cacheClearDuringDownloadDoesNotRestoreDeletedCache() throws Exception {
        Future<File> slow=calls.submit(()->images.fetch(item("slow"))); assertTrue(blocked.await(2,TimeUnit.SECONDS)); images.clear(); release.countDown(); assertNull(slow.get(2,TimeUnit.SECONDS));
        assertEquals(0,images.bytes()); assertEquals(0,images.directory.listFiles(file->file.getName().endsWith(".jpg")).length);
    }
}
