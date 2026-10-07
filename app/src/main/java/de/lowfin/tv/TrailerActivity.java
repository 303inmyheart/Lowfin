package de.lowfin.tv;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;

/** The official YouTube player lives inside Lowfin; WebView exists only for a trailer. */
public final class TrailerActivity extends Activity {
    private WebView web;
    private LowfinApp app;
    private TextView status;
    private Button toggle;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean destroyed,active;
    private long started;
    private final Runnable poll=new Runnable() { @Override public void run() {
        if(web==null||destroyed||!active) return;
        web.evaluateJavascript("JSON.stringify(typeof lf==='undefined'?{}:lf)",value-> {
            if(destroyed||!active) return;
            try {
                Object decoded=new JSONTokener(value).nextValue(); JSONObject state=new JSONObject(String.valueOf(decoded));
                int error=state.optInt("error");
                if(error!=0) status.setText(errorText(error));
                else if(state.optBoolean("blocked")) status.setText(I18n.text("Zum Starten Wiedergabe drücken"));
                else if(state.optBoolean("ready")) { int playback=state.optInt("state",-1); toggle.setText(playback==1?I18n.text("Ⅱ Pause"):I18n.text("▶ Wiedergabe")); status.setText(playback==3?I18n.text("Trailer wird gepuffert …"):playback==0?I18n.text("Trailer beendet"):""); }
                else if(SystemClock.elapsedRealtime()-started>20000) status.setText(I18n.text("YouTube lädt nicht · Verbindung und Android System WebView prüfen"));
            } catch(Exception ignored) { }
        }); handler.postDelayed(this,500);
    }};
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); app=(LowfinApp)getApplication(); Ui.immersive(this);
        String id=getIntent().getStringExtra("videoId"); if(id==null||!id.matches("[A-Za-z0-9_-]{11}")) { finish(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=Ui.column(this); root.setBackgroundColor(android.graphics.Color.BLACK);
        LinearLayout head=Ui.row(this); head.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,8));
        TextView title=Ui.text(this,getIntent().getStringExtra("title"),15,android.graphics.Color.WHITE); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Ui.addButton(head,Ui.quiet(this,I18n.text("Zurück"),this::finish)); root.addView(head);
        FrameLayout stage=new FrameLayout(this); root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=Ui.row(this); controls.setPadding(Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,8));
        toggle=Ui.primary(this,I18n.text("▶ Wiedergabe"),()->js("lowfinToggle()")); Ui.addButton(controls,toggle);
        Ui.addButton(controls,Ui.quiet(this,"−10 s",()->js("lowfinSeek(-10)"))); Ui.addButton(controls,Ui.quiet(this,"+10 s",()->js("lowfinSeek(10)")));
        Ui.addButton(controls,Ui.quiet(this,"YouTube-Steuerung",()-> { if(web!=null) { web.setFocusable(true); web.setFocusableInTouchMode(true); web.requestFocus(); } }));
        status=Ui.text(this,I18n.text("Trailer wird geladen …"),12,Ui.MUTED); controls.addView(status,new LinearLayout.LayoutParams(0,-2,1)); root.addView(controls); setContentView(root); toggle.requestFocus();
        try {
            web=new WebView(this); web.setBackgroundColor(android.graphics.Color.BLACK); web.setFocusable(false);
            WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            web.setWebChromeClient(new WebChromeClient()); web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                    String host=request.getUrl().getHost(); return host==null||!(host.equals("de.lowfin.tv")||host.equals("youtube.com")||host.endsWith(".youtube.com")||host.equals("youtube-nocookie.com")||host.endsWith(".youtube-nocookie.com"));
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) { if(request.isForMainFrame()) status.setText(I18n.text("YouTube konnte nicht geladen werden")); }
            });
            stage.addView(web,new FrameLayout.LayoutParams(-1,-1)); String origin="https://"+getPackageName();
            web.loadDataWithBaseURL(origin+"/",Trailers.html(id,origin),"text/html","UTF-8",null); started=SystemClock.elapsedRealtime();
        } catch(Exception e) { status.setText(I18n.text("Android System WebView fehlt oder kann nicht gestartet werden")); }
    }
    private void js(String code) { if(web!=null&&!destroyed) web.evaluateJavascript("if(typeof player!=='undefined'&&player){"+code+";}",null); }
    static String errorText(int error) {
        if(error==100) return I18n.text("Dieser Trailer ist nicht mehr verfügbar");
        if(error==101||error==150) return I18n.text("Dieser Trailer erlaubt keine Wiedergabe in Apps");
        if(error==153) return I18n.text("YouTube konnte den App-Player nicht verifizieren");
        return I18n.text("YouTube-Wiedergabe fehlgeschlagen (")+error+")";
    }
    @Override protected void onResume() { super.onResume(); Ui.immersive(this); active=true; app.playing=true; if(web!=null) web.onResume(); handler.post(poll); }
    @Override protected void onPause() { active=false; handler.removeCallbacksAndMessages(null); js("if(lf.ready)player.pauseVideo()"); if(web!=null) web.onPause(); app.playing=false; super.onPause(); }
    @Override protected void onDestroy() { destroyed=true; handler.removeCallbacksAndMessages(null); if(web!=null) { web.stopLoading(); if(web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)web.getParent()).removeView(web); web.destroy(); web=null; } super.onDestroy(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode(); String command=key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE?"lowfinToggle()":key==KeyEvent.KEYCODE_MEDIA_PLAY?"player.playVideo()":key==KeyEvent.KEYCODE_MEDIA_PAUSE?"player.pauseVideo()":key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?"lowfinSeek(10)":key==KeyEvent.KEYCODE_MEDIA_REWIND?"lowfinSeek(-10)":null;
        if(command!=null) { if(event.getAction()==KeyEvent.ACTION_DOWN) js(command); return true; }
        return super.dispatchKeyEvent(event);
    }
}
