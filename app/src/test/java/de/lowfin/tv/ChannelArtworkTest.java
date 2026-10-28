package de.lowfin.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import okhttp3.mockwebserver.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class ChannelArtworkTest {
    private MockWebServer server;
    private Account account;
    private Images images;
    private byte[] picture;
    @Before public void setup() throws Exception {
        Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("settings",0).edit().putInt("cacheMb",1024).commit();
        server=new MockWebServer();server.start();account=new Account(server.url("/").toString(),"user","token","User","device");
        images=new Images(context,new Api(account),UUID.randomUUID().toString());
        Bitmap image=Bitmap.createBitmap(16,24,Bitmap.Config.ARGB_8888);ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);image.recycle();picture=bytes.toByteArray();
    }
    @After public void close() throws Exception { images.close();server.shutdown(); }
    private JSONObject item(String tag) {return Json.obj("Id","film","Type","Movie","ImageTags",Json.obj("Primary",tag));}
    private MockResponse picture() {return new MockResponse().setBody(new okio.Buffer().write(picture));}
    @Test public void duplicatesShareFailuresOnlyWithinTheirPublicationAndNextPublicationRetries() {
        server.enqueue(new MockResponse().setResponseCode(404));server.enqueue(picture());
        ChannelArtwork first=new ChannelArtwork(account,images);Uri fallback=first.get(item("v1"));assertEquals("android.resource",fallback.getScheme());
        for(int i=0;i<30;i++) assertEquals(fallback,first.get(item("v1")));
        assertEquals(1,server.getRequestCount());
        ChannelArtwork next=new ChannelArtwork(account,images);Uri loaded=next.get(item("v1"));assertEquals("content",loaded.getScheme());assertEquals(account.scope(),loaded.getPathSegments().get(0));
        assertEquals(2,server.getRequestCount());assertEquals(loaded,next.get(item("v1")));
        assertEquals(loaded,new ChannelArtwork(account,images).get(item("v1")));assertEquals(2,server.getRequestCount());
    }
    @Test public void changedImageTagsAndCacheDeletionAreObservedWithinTheSamePublication() throws Exception {
        server.enqueue(picture());server.enqueue(picture());server.enqueue(picture());
        ChannelArtwork batch=new ChannelArtwork(account,images);Uri original=batch.get(item("v1")),changed=batch.get(item("v2"));assertNotEquals(original,changed);assertEquals(2,server.getRequestCount());
        File file=new File(images.directory,changed.getLastPathSegment());assertTrue(file.isFile());images.clear();assertFalse(file.exists());
        assertEquals(changed,batch.get(item("v2")));assertEquals(3,server.getRequestCount());assertTrue(file.isFile());
    }
    @Test public void missingArtworkNeverStartsAnImageRequest() {
        ChannelArtwork batch=new ChannelArtwork(account,images);
        for(int i=0;i<10;i++) assertEquals("android.resource",batch.get(Json.obj("Id","no-cover","Type","Movie")).getScheme());
        assertEquals(0,server.getRequestCount());
    }
}
