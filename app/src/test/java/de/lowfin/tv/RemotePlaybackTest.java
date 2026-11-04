package de.lowfin.tv;

import android.os.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30,application=android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class RemotePlaybackTest {
    private MockWebServer server;private RemotePlayback remote;private Api api;
    private final AtomicReference<WebSocket> peer=new AtomicReference<>();
    private final List<String> received=new ArrayList<>();
    private final List<String> messages=new ArrayList<>();
    private final BlockingQueue<String> heartbeats=new LinkedBlockingQueue<>();
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();api=new Api(new Account(server.url("/").toString().replaceAll("/+$",""),"user","test-token","Demo User","test-device"));
        remote=new RemotePlayback(api,new Handler(Looper.getMainLooper()),new RemotePlayback.Actions() {
            public void command(String command,long position) {received.add(command+":"+position);}
            public void message(String header,String text,long timeout) {messages.add(header+"|"+text+"|"+timeout);}
        });
    }
    @After public void cleanup() throws Exception {remote.close();if(peer.get()!=null)peer.get().close(1000,null);server.shutdown();api.http.dispatcher().executorService().shutdown();}
    private void enqueueSocket() {
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket socket,Response response) {peer.set(socket);}
            @Override public void onMessage(WebSocket socket,String text) {heartbeats.add(text);}
            @Override public void onClosing(WebSocket socket,int code,String reason) {socket.close(code,null);}
        }));server.enqueue(new MockResponse().setResponseCode(204));
    }
    private void await(BooleanSupplier ready) throws Exception {
        long end=System.currentTimeMillis()+5000;
        while(!ready.getAsBoolean()&&System.currentTimeMillis()<end) {shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
        shadowOf(Looper.getMainLooper()).idle();assertTrue("Timed out",ready.getAsBoolean());
    }
    @Test public void authenticatedSocketAdvertisesControlAndReceivesDashboardCommandsAndKeepAlive() throws Exception {
        enqueueSocket();remote.start();await(()->server.getRequestCount()>=2);
        RecordedRequest socket=server.takeRequest(2,TimeUnit.SECONDS),capabilities=server.takeRequest(2,TimeUnit.SECONDS);
        assertEquals("/socket?DeviceId=test-device",socket.getPath());assertTrue(socket.getHeader("Authorization").contains("test-token"));assertFalse(socket.getPath().contains("test-token"));
        assertEquals("/Sessions/Capabilities/Full",capabilities.getPath());assertTrue(new JSONObject(capabilities.getBody().readUtf8()).getBoolean("SupportsMediaControl"));
        for(String c:new String[]{"Pause","Unpause","PlayPause","Stop"})peer.get().send(Json.obj("MessageType","Playstate","Data",Json.obj("Command",c)).toString());
        peer.get().send("{\"MessageType\":\"PlayState\",\"Data\":{\"Command\":\"Seek\",\"SeekPositionTicks\":43210000}}");
        await(()->received.size()==5);assertEquals(Arrays.asList("Pause:0","Unpause:0","PlayPause:0","Stop:0","Seek:4321"),received);
        heartbeats.clear();peer.get().send("{\"MessageType\":\"ForceKeepAlive\",\"Data\":60}");await(()->!heartbeats.isEmpty());assertEquals("KeepAlive",new JSONObject(heartbeats.poll()).optString("MessageType"));
    }
    @Test public void malformedAndUnrelatedMessagesNeverBecomeCommands() throws Exception {
        enqueueSocket();remote.start();await(()->server.getRequestCount()>=2);
        peer.get().send("broken");peer.get().send("{\"MessageType\":\"Play\",\"Data\":{\"Command\":\"Stop\"}}");peer.get().send("{\"MessageType\":\"Playstate\"}");
        peer.get().send("{\"MessageType\":\"Playstate\",\"Data\":{\"Command\":\"Pause\"}}");await(()->received.size()==1);assertEquals("Pause:0",received.get(0));
    }
    @Test public void closingPlayerDiscardsQueuedCommandsAndDoesNotReconnect() throws Exception {
        enqueueSocket();remote.start();await(()->server.getRequestCount()>=2);
        peer.get().send("{\"MessageType\":\"Playstate\",\"Data\":{\"Command\":\"Stop\"}}");remote.close();
        shadowOf(Looper.getMainLooper()).idleFor(2,TimeUnit.MINUTES);assertTrue(received.isEmpty());assertEquals(2,server.getRequestCount());
    }
    @Test public void reconnectsAfterServerDisconnect() throws Exception {
        enqueueSocket();remote.start();await(()->server.getRequestCount()>=2);server.takeRequest();server.takeRequest();
        WebSocket old=peer.get();enqueueSocket();old.close(1000,null);await(()->peer.get()==old&&server.getRequestCount()==2&&heartbeats.size()>0);
        // Drain the close callback before advancing the bounded reconnect timer.
        long end=System.currentTimeMillis()+3000;while(server.getRequestCount()<3&&System.currentTimeMillis()<end) {shadowOf(Looper.getMainLooper()).idleFor(1,TimeUnit.SECONDS);Thread.sleep(10);}
        await(()->server.getRequestCount()>=4);assertNotSame(old,peer.get());
    }
    @Test public void dashboardMessagesUseGeneralCommandAndKeepRemoteControlsSeparate() throws Exception {
        enqueueSocket();remote.start();await(()->server.getRequestCount()>=2);server.takeRequest();
        JSONObject capabilities=new JSONObject(server.takeRequest().getBody().readUtf8());assertEquals("DisplayMessage",capabilities.getJSONArray("SupportedCommands").getString(0));
        peer.get().send("{\"MessageType\":\"GeneralCommand\",\"Data\":{\"Name\":\"DisplayMessage\",\"Arguments\":{\"Header\":\"Notice\",\"Text\":\"A server message\",\"TimeoutMs\":\"5000\"}}}");
        peer.get().send("{\"MessageType\":\"GeneralCommand\",\"Data\":{\"Name\":\"DisplayMessage\",\"Arguments\":{\"Text\":\" \"}}}");
        peer.get().send("{\"MessageType\":\"GeneralCommand\",\"Data\":{\"Name\":\"RunCommand\",\"Arguments\":{\"Text\":\"ignored\"}}}");
        peer.get().send("{\"MessageType\":\"Playstate\",\"Data\":{\"Command\":\"Pause\"}}");
        await(()->received.size()==1);assertEquals(Collections.singletonList("Notice|A server message|5000"),messages);assertEquals("Pause:0",received.get(0));
    }
    @Test public void browsingSocketRetainsDashboardAccessAndCompleteIncomingText() throws Exception {
        remote.close();remote=new RemotePlayback(api,new Handler(Looper.getMainLooper()),new RemotePlayback.Actions() {
            public void command(String command,long position) {received.add(command);}
            public void message(String header,String text,long timeout) {messages.add(header+"|"+text);}
        },false);
        enqueueSocket();remote.start();await(()->server.getRequestCount()>=2);server.takeRequest();
        assertTrue(new JSONObject(server.takeRequest().getBody().readUtf8()).getBoolean("SupportsMediaControl"));
        peer.get().send(Json.obj("MessageType","GeneralCommand","Data",Json.obj("Name","DisplayMessage","Arguments",Json.obj("Header","h".repeat(200),"Text","t".repeat(2000)))).toString());
        peer.get().send("{\"MessageType\":\"Playstate\",\"Data\":{\"Command\":\"Stop\"}}");
        await(()->messages.size()==1);assertEquals(200+1+2000,messages.get(0).length());assertTrue(received.isEmpty());
    }
    @Test public void invalidSeekAndUnknownCommandAreRejected() {
        assertNull(RemotePlayback.command(null));assertNull(RemotePlayback.command(Json.obj("Command","Seek")));assertNull(RemotePlayback.command(Json.obj("Command","Seek","SeekPositionTicks",-1)));
        assertNull(RemotePlayback.command(Json.obj("Command","RunArbitraryCommand")));assertEquals("Seek",RemotePlayback.command(Json.obj("Command","Seek","SeekPositionTicks",0)));
    }
}
