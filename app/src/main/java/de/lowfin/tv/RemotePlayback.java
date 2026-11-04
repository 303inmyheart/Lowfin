package de.lowfin.tv;

import android.os.Handler;
import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Authenticated foreground-session socket. All state is UI-thread confined. */
final class RemotePlayback implements AutoCloseable {
    interface Actions {
        void command(String command,long positionMs);
        default void message(String header,String text,long timeoutMs) {}
    }
    private final Api api;
    private final Handler main;
    private final Actions actions;
    private final OkHttpClient http;
    private final boolean mediaControl;
    private WebSocket socket;
    private boolean closed;
    private int attempt,epoch;
    private final Runnable retry=this::connect;
    private final Runnable heartbeat=new Runnable() { public void run() {
        if(!closed&&socket!=null) { socket.send("{\"MessageType\":\"KeepAlive\"}");main.postDelayed(this,20000); }
    }};
    RemotePlayback(Api api,Handler main,Actions actions) {
        this(api,main,actions,true);
    }
    RemotePlayback(Api api,Handler main,Actions actions,boolean mediaControl) {
        this.api=api;this.main=main;this.actions=actions;
        this.mediaControl=mediaControl;
        http=api.http.newBuilder().readTimeout(0,TimeUnit.MILLISECONDS).callTimeout(0,TimeUnit.MILLISECONDS).pingInterval(30,TimeUnit.SECONDS).build();
    }
    void start() { if(!closed&&socket==null) connect(); }
    private void connect() {
        if(closed)return;
        main.removeCallbacks(retry);final int generation=++epoch;
        socket=http.newWebSocket(api.request(api.url("/socket","DeviceId",api.account.device)).build(),new WebSocketListener() {
            @Override public void onOpen(WebSocket ws,Response response) { main.post(()-> {
                if(closed||generation!=epoch) {ws.cancel();return;}
                attempt=0;main.removeCallbacks(heartbeat);heartbeat.run();
                // Keep device capabilities stable across menu/player handoff: Jellyfin also
                // uses SupportsMediaControl to authorize dashboard messages.
                Request request=api.request(api.url("/Sessions/Capabilities/Full")).post(RequestBody.create(
                    Json.obj("PlayableMediaTypes",new JSONArray().put("Video"),"SupportedCommands",new JSONArray().put("DisplayMessage"),"SupportsMediaControl",true,"SupportsPersistentIdentifier",true).toString(),Api.JSON)).build();
                http.newCall(request).enqueue(new Callback() {
                    @Override public void onFailure(Call call,IOException error) { /* The socket reconnect refreshes capabilities. */ }
                    @Override public void onResponse(Call call,Response response) { response.close(); }
                });
            }); }
            @Override public void onMessage(WebSocket ws,String text) { main.post(()-> {
                if(closed||generation!=epoch)return;
                try {
                    JSONObject message=new JSONObject(text);String type=message.optString("MessageType");
                    if("ForceKeepAlive".equals(type)) ws.send("{\"MessageType\":\"KeepAlive\"}");
                    else if("Playstate".equalsIgnoreCase(type)&&mediaControl) {
                        JSONObject data=message.optJSONObject("Data");String command=command(data);
                        if(command!=null) actions.command(command,Math.max(0,data.optLong("SeekPositionTicks",0)/10000));
                    }
                    else if("GeneralCommand".equals(type)) {
                        JSONObject data=message.optJSONObject("Data");
                        if(data!=null&&"DisplayMessage".equals(data.optString("Name"))) {
                            JSONObject args=data.optJSONObject("Arguments");String body=args==null?"":Json.str(args,"Text").trim();
                            if(!body.isEmpty())actions.message(Json.str(args,"Header"),body,args.optLong("TimeoutMs",0));
                        }
                    }
                } catch(JSONException ignored) { /* Ignore unrelated or malformed session messages. */ }
            }); }
            @Override public void onClosing(WebSocket ws,int code,String reason) { ws.close(code,null); }
            @Override public void onClosed(WebSocket ws,int code,String reason) { disconnected(generation); }
            @Override public void onFailure(WebSocket ws,Throwable error,Response response) { disconnected(generation); }
        });
    }
    static String command(JSONObject data) {
        if(data==null)return null;String command=data.optString("Command");
        switch(command) {case "Pause":case "Unpause":case "PlayPause":case "Stop":return command;
            case "Seek":return data.has("SeekPositionTicks")&&!data.isNull("SeekPositionTicks")&&data.optLong("SeekPositionTicks",-1)>=0?command:null;
            default:return null;}
    }
    private void disconnected(int generation) {main.post(()-> {
        if(closed||generation!=epoch)return;
        epoch++;socket=null;main.removeCallbacks(heartbeat);main.removeCallbacks(retry);
        main.postDelayed(retry,Math.min(30000,1000L<<Math.min(attempt++,5)));
    });}
    @Override public void close() {
        closed=true;epoch++;main.removeCallbacks(retry);main.removeCallbacks(heartbeat);
        if(socket!=null) {socket.close(1000,null);socket=null;}
    }
}
