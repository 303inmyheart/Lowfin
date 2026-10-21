package de.lowfin.tv;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import okhttp3.OkHttpClient;
import java.util.*;

/** Full-screen trailer playback using the same native transport as Jellyfin media. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class TrailerActivity extends Activity {
    private LowfinApp app;
    private ExoPlayer player;
    private PlayerChrome chrome;
    private final PlayerMenuPress menuPress=new PlayerMenuPress();
    private BufferBar buffer;
    private TextView status;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean destroyed,active;
    private final Runnable hide=()-> { if(player!=null&&player.isPlaying()) showChrome(false); };
    private final Runnable poll=new Runnable() { @Override public void run() {
        if(destroyed||!active||player==null) return;
        chrome.update(player.getCurrentPosition(),player.getBufferedPosition(),Math.max(0,player.getDuration()),player.isPlaying(),false);
        handler.postDelayed(this,500);
    }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); app=(LowfinApp)getApplication(); Ui.immersive(this);
        String id=getIntent().getStringExtra("videoId"); if(id==null||!id.matches("[A-Za-z0-9_-]{11}")) { finish(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(android.graphics.Color.BLACK);
        PlayerView video=new PlayerView(this); video.setUseController(false); root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        chrome=new PlayerChrome(this,false,false,new PlayerChrome.Actions() {
            public void toggle() { if(player!=null) { if(player.getPlayWhenReady()) player.pause(); else player.play(); showChrome(true); } }
            public void seek(long millis) { if(player!=null) seekTo(player.getCurrentPosition()+millis); }
            public void seekTo(long millis) { if(player!=null) { player.seekTo(Math.max(0,Math.min(Math.max(0,player.getDuration()),millis))); showChrome(true); } }
            public void options() {} public void next() {} public void channels() {}
        });
        chrome.options.setVisibility(View.GONE); chrome.title.setText(getIntent().getStringExtra("title")); chrome.logo.setVisibility(View.GONE);
        root.addView(chrome.identity,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP)); root.addView(chrome,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        LinearLayout loading=Ui.column(this); loading.setGravity(Gravity.CENTER); status=Ui.text(this,I18n.text("Trailer wird geladen …"),13,Ui.MUTED); loading.addView(status);
        buffer=new BufferBar(this); loading.addView(buffer,new LinearLayout.LayoutParams(Ui.dp(this,160),Ui.dp(this,22))); root.addView(loading,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        setContentView(root); chrome.play.requestFocus();
        OkHttpDataSource.Factory http=new OkHttpDataSource.Factory(new OkHttpClient()).setUserAgent(YouTubeStreams.USER_AGENT)
            .setDefaultRequestProperties(Collections.singletonMap("Referer","https://www.youtube.com/"));
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build(); video.setPlayer(player);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setMaxVideoSize(1280,720).setPreferredVideoMimeType(MimeTypes.VIDEO_H264).build());
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) { if(state==Player.STATE_ENDED) { finish(); return; } buffer.setVisibility(state==Player.STATE_BUFFERING?View.VISIBLE:View.GONE); status.setText(state==Player.STATE_BUFFERING?I18n.text("Trailer wird gepuffert …"):state==Player.STATE_ENDED?I18n.text("Trailer beendet"):""); if(state==Player.STATE_READY) showChrome(true); }
            @Override public void onPlayerError(PlaybackException error) { buffer.setVisibility(View.GONE); status.setText(I18n.text("YouTube konnte nicht geladen werden")); showChrome(true); }
        });
        app.io.execute(()-> { try { String url=new YouTubeStreams().resolve(id); runOnUiThread(()-> { if(destroyed) return; player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.setPlayWhenReady(active); }); }
            catch(Exception e) { runOnUiThread(()-> { if(destroyed) return; buffer.setVisibility(View.GONE); status.setText(I18n.text("YouTube konnte nicht geladen werden")); }); } });
    }
    private void showChrome(boolean visible) { chrome.setVisibility(visible?View.VISIBLE:View.GONE); chrome.identity.setVisibility(visible?View.VISIBLE:View.GONE); handler.removeCallbacks(hide); if(visible) handler.postDelayed(hide,5000); }
    @Override protected void onResume() { super.onResume(); Ui.immersive(this); active=true; app.playing=true; handler.post(poll); }
    @Override protected void onPause() { active=false; handler.removeCallbacksAndMessages(null); if(player!=null) player.pause(); app.playing=false; super.onPause(); }
    @Override protected void onDestroy() { destroyed=true; handler.removeCallbacksAndMessages(null); if(player!=null) { player.release(); player=null; } super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode(); boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER;
        boolean direction=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
        if(chrome!=null&&menuPress.handle(event,chrome.getVisibility()==View.VISIBLE,()-> { if(player!=null) player.pause(); },openingKey-> { showChrome(true); if(openingKey==KeyEvent.KEYCODE_DPAD_LEFT||openingKey==KeyEvent.KEYCODE_DPAD_RIGHT) chrome.timeline.requestFocus(); else chrome.play.requestFocus(); })) return true;
        if(chrome!=null&&confirm&&(chrome.play.hasFocus()||chrome.timeline.hasFocus())) return menuPress.activate(event,()->chrome.play.performClick());
        if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND) {
            if(event.getAction()==KeyEvent.ACTION_DOWN&&player!=null) { if(key==KeyEvent.KEYCODE_MEDIA_PLAY) player.play(); else if(key==KeyEvent.KEYCODE_MEDIA_PAUSE) player.pause(); else if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if(player.getPlayWhenReady()) player.pause(); else player.play(); } else player.seekTo(Math.max(0,player.getCurrentPosition()+(key==KeyEvent.KEYCODE_MEDIA_REWIND?-10000:10000))); showChrome(true); } return true;
        }
        if(direction||confirm) { handler.removeCallbacks(hide); handler.postDelayed(hide,5000); }
        return super.dispatchKeyEvent(event);
    }
}
