package de.lowfin.tv;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.ui.PlayerView;
import okhttp3.HttpUrl;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private LowfinApp app;
    private Api api;
    private Library library;
    private JSONObject item,source;
    private JSONArray segments=new JSONArray();
    private final Set<String> skipped=new HashSet<>();
    private PlayerView view;
    private ExoPlayer player;
    private PlayerChrome chrome;
    private TextView message;
    private BufferBar bufferBar;
    private final SeekGesture seekGesture=new SeekGesture();
    private int seekDirection;
    private String logoRequest="";
    private JSONObject logoSeries;
    private Button skip;
    private long skipTarget,requestedPosition;
    private int audio=-1,subtitle=-1;
    private boolean initialAudioApplied;
    private volatile int generation;
    private String sourceId,sessionId,liveId,method="DirectPlay";
    private boolean live,started,forceHls,released,subtitleChosen;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastReport;
    private List<String> liveCandidates=Collections.emptyList();
    private int liveCandidate;
    private final LiveBufferWatchdog liveBuffer=new LiveBufferWatchdog();
    private final Runnable hideControls=()-> { if(player!=null&&player.getPlayWhenReady()&&!seekGesture.active()) hideChrome(); };
    private final Runnable tick=new Runnable() { @Override public void run() {
        if(released||player==null) return;
        if(chrome!=null&&chrome.getVisibility()==View.VISIBLE&&!seekGesture.active()) chrome.update(player.getCurrentPosition(),player.getBufferedPosition(),player.getDuration(),player.getPlayWhenReady(),live);
        if(live&&player.getPlaybackState()==Player.STATE_BUFFERING) {
            int action=liveBuffer.poll(SystemClock.elapsedRealtime());
            if(action==LiveBufferWatchdog.RECONNECT) { retryLiveTransport(); message.setText(I18n.text("Live-Stream wird erneut verbunden …")); }
            else if(action==LiveBufferWatchdog.FAIL) { player.stop(); message.setText(I18n.text("Der Sender liefert keine abspielbaren Daten.\nOptionen → Stream erneut verbinden oder einen anderen Sender wählen.")); bufferBar.setVisibility(View.GONE); message.setVisibility(View.VISIBLE); showChrome(true); }
        }
        if(player.getPlaybackState()==Player.STATE_READY) {
            long position=player.getCurrentPosition();
            if(!live) applySegments(position);
            if(started&&SystemClock.elapsedRealtime()-lastReport>=10000) { report("/Progress",position); lastReport=SystemClock.elapsedRealtime(); }
        }
        handler.removeCallbacks(this); handler.postDelayed(this,500);
    }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); app=(LowfinApp)getApplication(); library=app.library;
        if(app.account==null) { released=true; finish(); return; }
        api=new Api(app.account);
        try { item=new JSONObject(getIntent().getStringExtra("item")); } catch(Exception e) { finish(); return; }
        requestedPosition=saved!=null?saved.getLong("position"):getIntent().getLongExtra("position",0);
        audio=saved!=null?saved.getInt("audioStreamIndex",-1):getIntent().getIntExtra("audioStreamIndex",-1);
        sourceId=saved!=null?saved.getString("mediaSourceId",""):getIntent().getStringExtra("mediaSourceId");
        live="TvChannel".equals(item.optString("Type"));
        app.playing=true; getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); Ui.immersive(this);
        layout(); load(requestedPosition,false);
    }
    private void layout() {
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        view=new PlayerView(this); view.setUseController(false); view.setKeepContentOnPlayerReset(true); view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        chrome=new PlayerChrome(this,live,"Episode".equals(item.optString("Type")),new PlayerChrome.Actions() {
            @Override public void toggle() { if(player!=null) { if(player.getPlayWhenReady()) player.pause(); else player.play(); showChrome(false); } }
            @Override public void seek(long delta) { seekTo(position()+delta); }
            @Override public boolean seekHeld(int direction,KeyEvent event) { return heldSeek(direction,event); }
            @Override public void seekTo(long target) { PlayerActivity.this.seekTo(target); }
            @Override public void options() { optionsMenu(); }
            @Override public void next() { nextEpisode(false); }
            @Override public void channels() { PlayerActivity.this.channels(); }
        });
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM); root.addView(chrome,bottom); root.addView(chrome.identity,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.LEFT)); updateChromeTitle();
        message=Ui.text(this,I18n.text("Stream wird geöffnet …"),14,Color.WHITE); message.setGravity(Gravity.CENTER); message.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,20),Ui.dp(this,12)); message.setBackground(Ui.background(0xe619191c,0));
        FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(Ui.dp(this,420),-2,Gravity.CENTER); root.addView(message,center);
        bufferBar=new BufferBar(this); bufferBar.setVisibility(View.GONE); root.addView(bufferBar,new FrameLayout.LayoutParams(Ui.dp(this,240),Ui.dp(this,20),Gravity.CENTER));
        skip=Ui.quiet(this,I18n.text("Überspringen"),()-> { if(player!=null) { player.seekTo(skipTarget); skip.setVisibility(View.GONE); view.requestFocus(); } }); skip.setVisibility(View.GONE);
        FrameLayout.LayoutParams skipLayout=new FrameLayout.LayoutParams(-2,Ui.dp(this,36),Gravity.END|Gravity.BOTTOM); skipLayout.setMargins(0,0,Ui.dp(this,32),Ui.dp(this,192)); root.addView(skip,skipLayout);
        setContentView(root); view.setOnClickListener(v->{ if(chrome.getVisibility()==View.VISIBLE) hideChrome(); else showChrome(true); }); view.requestFocus();
    }
    private void updateChromeTitle() {
        chrome.title.setText(item.optString("Type").equals("Episode")?item.optString("SeriesName",item.optString("Name")):item.optString("Name"));
        JSONObject artwork=Artwork.logo(item)!=null?item:logoSeries!=null&&logoSeries.optString("Id").equals(item.optString("SeriesId"))?logoSeries:null;
        if(live&&app.images!=null) app.images.channelLogo(chrome.logo,item);
        else if(artwork!=null&&app.images!=null) app.images.logo(chrome.logo,artwork); else chrome.logo.setImageDrawable(null);
        String series=item.optString("SeriesId");
        if(!series.isEmpty()&&Artwork.logo(item)==null&&!series.equals(logoRequest)) {
            logoRequest=series; final Api logoApi=app.api;
            app.io.execute(()-> { try { JSONObject data=logoApi.item(series); runOnUiThread(()-> { if(!released&&series.equals(item.optString("SeriesId"))) { logoSeries=data; app.images.logo(chrome.logo,data); } }); } catch(Exception ignored) {} });
        }
        chrome.caption.setText(live?"Live TV":item.optString("Type").equals("Episode")?Json.label(item):item.optString("ProductionYear",""));
    }
    private void showChrome(boolean focus) {
        chrome.setVisibility(View.VISIBLE); chrome.identity.setVisibility(View.VISIBLE); if(player!=null&&!seekGesture.active()) chrome.update(position(),player.getBufferedPosition(),player.getDuration(),player.getPlayWhenReady(),live);
        if(focus) chrome.play.requestFocus(); handler.removeCallbacks(hideControls); handler.postDelayed(hideControls,app.settings.getInt("controlsSeconds",5)*1000L);
    }
    private void hideChrome() { chrome.setVisibility(View.GONE); chrome.identity.setVisibility(View.GONE); handler.removeCallbacks(hideControls); view.requestFocus(); }
    private void seekTo(long target) {
        if(player==null||live) return; long duration=player.getDuration(); player.seekTo(Math.max(0,duration>0?Math.min(target,duration):target)); showChrome(false);
    }
    private void optionsMenu() {
        handler.removeCallbacks(hideControls);
        String[] labels=live?new String[]{I18n.text("Tonspur"),I18n.text("Untertitel"),I18n.text("Medienversion"),I18n.text("Sender wechseln"),I18n.text("Aktuelles Programm"),I18n.text("Stream erneut verbinden"),I18n.text("Über Server abspielen")}:new String[]{I18n.text("Tonspur"),I18n.text("Untertitel"),I18n.text("Medienversion"),I18n.text("Kapitel"),I18n.text("Über Server abspielen")};
        new AlertDialog.Builder(this).setTitle(I18n.text("Wiedergabeoptionen")).setItems(labels,(d,index)-> {
            if(index==0) streams("Audio"); else if(index==1) streams("Subtitle"); else if(index==2) versions();
            else if(live&&index==3) channels(); else if(live&&index==4) guide(); else if(!live&&index==3) chapters();
            else if(live&&index==5) { if(player!=null) { liveBuffer.reset(); player.stop(); player.prepare(); player.play(); } else load(0,true); }
            else { forceHls=true; load(position(),true); }
        }).setNegativeButton(I18n.text("Schließen"),null).show();
    }
    private void load(long position,boolean keepSelections) {
        seekGesture.reset(); bufferBar.setVisibility(View.GONE); cleanupSession(); final int g=++generation; started=false; requestedPosition=position; segments=new JSONArray(); skipped.clear(); skip.setVisibility(View.GONE);
        if(player!=null) { view.setPlayer(null); player.release(); player=null; }
        message.setVisibility(View.GONE); bufferBar.setVisibility(View.VISIBLE); updateChromeTitle(); showChrome(false);
        final String id=item.optString("Id"); final boolean isLive=live;
        final String requestedSource=sourceId; final int requestedAudio=audio,requestedSubtitle=subtitle; final boolean force=forceHls;
        if(isLive) app.settings.edit().putString("lastChannel",id).apply();
        network.execute(()-> {
            JSONObject negotiated=null,chosen=null; String negotiatedLive=null,negotiatedSession=null;
            try {
                if(g!=generation||released) return;
                JSONObject detailed=api.item(id);
                int audioIndex=requestedAudio,subtitleIndex=keepSelections?requestedSubtitle:-1;
                if(!keepSelections) {
                    JSONArray sources=Json.array(detailed,"MediaSources");
                    JSONObject first=sources.optJSONObject(0);
                    for(int i=0;i<sources.length();i++) { JSONObject s=sources.optJSONObject(i); if(s!=null&&Json.str(s,"Id").equals(requestedSource)) first=s; }
                    if(first!=null) { if(requestedAudio<0) audioIndex=first.optInt("DefaultAudioStreamIndex",-1); subtitleIndex=-1; }
                }
                if(g!=generation||released) return;
                JSONObject request=PlaybackPolicy.request(api.account.user,app.settings.getInt("height",720),app.settings.getInt("bitrate",4000000),isLive,force,position,audioIndex,subtitleIndex,requestedSource,app.settings.getBoolean("stereo",true));
                if(!force) { LocalPlayback.negotiate(request); request.put("EnableTranscoding",app.settings.getBoolean("serverFallback",false)); }
                if(isLive) { LiveStreams.configure(request,force); }
                negotiated=api.post("/Items/"+id+"/PlaybackInfo",request,"UserId",api.account.user);
                if(!Json.str(negotiated,"ErrorCode").isEmpty()) throw new IllegalStateException(I18n.text("Wiedergabe nicht möglich: ")+Json.str(negotiated,"ErrorCode"));
                JSONArray sources=Json.array(negotiated,"MediaSources"); chosen=sources.optJSONObject(0);
                if(chosen==null) throw new IllegalStateException(I18n.text("Keine abspielbare Medienquelle"));
                negotiatedLive=Json.str(chosen,"LiveStreamId"); negotiatedSession=Json.str(negotiated,"PlaySessionId");
                if(isLive) { chosen=LiveStreams.openIfNeeded(api,chosen,request,negotiatedSession); negotiatedLive=chosen.optString("LiveStreamId"); }
                String playMethod="DirectPlay",stream;
                boolean hls=false;
                List<String> liveUrls=isLive?LiveStreams.candidates(api,id,chosen,negotiatedSession,force,app.settings.getBoolean("serverFallback",false)):Collections.emptyList();
                if(isLive&&!liveUrls.isEmpty()) { stream=liveUrls.get(0); hls=stream.toLowerCase(Locale.ROOT).contains(".m3u8"); playMethod=LiveStreams.server(api,stream)?"DirectStream":"DirectPlay"; if(force) playMethod="Transcode"; }
                else if(isLive) throw new IllegalStateException(I18n.text("Keine direkte Live-Stream-Adresse verfügbar. Optional Server-Transcoding in den Einstellungen zulassen."));
                else if((force||!chosen.optBoolean("SupportsDirectPlay"))&&!Json.str(chosen,"TranscodingUrl").isEmpty()) {
                    stream=resolve(Json.str(chosen,"TranscodingUrl")); playMethod=chosen.optString("TranscodingSubProtocol").equals("hls")||stream.contains("m3u8")?"Transcode":"DirectStream"; hls=stream.contains("m3u8");
                    HttpUrl.Builder url=HttpUrl.get(stream).newBuilder();
                    if(!isLive) { url.removeAllQueryParameters("startTimeTicks"); url.setQueryParameter("StartTimeTicks","0"); }
                    if(force||app.settings.getBoolean("serverFallback",false)) { int height=app.settings.getInt("height",720); url.setQueryParameter("MaxWidth",height==720?"1280":"1920"); url.setQueryParameter("MaxHeight",""+height); }
                    if(force&&app.settings.getBoolean("stereo",true)) { url.setQueryParameter("MaxAudioChannels","2"); url.setQueryParameter("AudioChannels","2"); }
                    stream=url.build().toString();
                } else if(!force&&(chosen.optBoolean("SupportsDirectPlay")||chosen.optBoolean("SupportsDirectStream"))) {
                    playMethod=chosen.optBoolean("SupportsDirectPlay")&&!isLive?"DirectPlay":"DirectStream";
                    stream=api.url("/Videos/"+id+"/stream","Static",true,"MediaSourceId",chosen.optString("Id"),"LiveStreamId",negotiatedLive.isEmpty()?null:negotiatedLive,
                        "DeviceId",api.account.device,"PlaySessionId",negotiatedSession).toString();
                } else throw new IllegalStateException(I18n.text("Kein direkt abspielbarer Stream. Optional ‚Über Server abspielen‘ wählen."));
                JSONObject full=detailed,src=chosen; String url=stream,playSession=negotiatedSession,liveStream=negotiatedLive,mode=playMethod; boolean useHls=hls;
                final int selectedAudio=audioIndex,selectedSubtitle=subtitleIndex;
                library.put(full);
                DataSource.Factory data=app.videoCache.factory(api,isLive,useHls);
                runOnUiThread(()-> {
                    if(g!=generation||released) { closeNegotiated(liveStream,playSession); return; }
                    item=full; source=src; sourceId=src.optString("Id"); sessionId=playSession; liveId=liveStream; method=mode;
                    liveCandidates=liveUrls; liveCandidate=0; audio=selectedAudio; subtitle=selectedSubtitle; prepare(url,useHls,isLive?0:position,g,data); updateChromeTitle();
                    app.channels.engaged(api.account,id);
                });
                if(!isLive) { try {
                    JSONArray found=Json.array(api.get("/MediaSegments/"+id),"Items"); library.state("segments-"+id,found.toString());
                    runOnUiThread(()-> { if(g==generation&&!released) segments=found; });
                } catch(Exception e) {
                    String cached=library.state("segments-"+id); if(!cached.isEmpty()) { JSONArray found=new JSONArray(cached); runOnUiThread(()-> { if(g==generation&&!released) segments=found; }); }
                }}
            } catch(Exception e) {
                closeNegotiated(negotiatedLive,negotiatedSession);
                runOnUiThread(()-> { if(g==generation&&!released) { bufferBar.setVisibility(View.GONE); message.setVisibility(View.VISIBLE); message.setText(I18n.text("Stream konnte nicht gestartet werden.\n")+friendly(e)+I18n.text("\nZurück oder ‚Über Server abspielen‘ wählen.")); showChrome(true); } });
            }
        });
    }
    private String resolve(String value) {
        HttpUrl parsed=HttpUrl.get(api.account.server+"/").resolve(value);
        HttpUrl base=HttpUrl.get(api.account.server);
        if(parsed==null||!parsed.host().equals(base.host())||parsed.port()!=base.port()||!parsed.scheme().equals(base.scheme())) throw new IllegalArgumentException(I18n.text("Streamadresse außerhalb des Jellyfin-Servers"));
        // Jellyfin returns /Videos/... even when hosted below /jellyfin.
        if(value.startsWith("/")&&!base.encodedPath().equals("/")&&!parsed.encodedPath().startsWith(base.encodedPath()+"/")) return api.account.server+value;
        return parsed.toString();
    }
    private String friendly(Exception e) { return e instanceof Api.Failure?e.getMessage():e instanceof java.io.IOException?I18n.text("Verbindung zum Server prüfen"):e.getMessage(); }
    private void prepare(String url,boolean hls,long position,int g,DataSource.Factory data) {
        liveBuffer.reset();
        AdaptiveLoadControl control=new AdaptiveLoadControl(this,live);
        androidx.media3.extractor.DefaultExtractorsFactory extractors=new androidx.media3.extractor.DefaultExtractorsFactory();
        if(live) extractors.setTsExtractorFlags(androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES);
        player=new ExoPlayer.Builder(this,LocalPlayback.renderers(this,app.settings.getBoolean("stereo",true)).setExtensionRendererMode(live?DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON:DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)).setMediaSourceFactory(new DefaultMediaSourceFactory(data,extractors).setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6))).setLoadControl(control).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setMaxVideoSize(app.settings.getInt("height",720)==720?1280:1920,app.settings.getInt("height",720))
            .setMaxVideoFrameRate(30).setMaxAudioChannelCount(6).setPreferredAudioLanguages(app.settings.getString("audioLanguage",I18n.language())).setPreferredTextLanguages(I18n.language()).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,subtitle<0&&(subtitleChosen||!app.settings.getBoolean("defaultSubtitles",false))).setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT|C.SELECTION_FLAG_FORCED).build());
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true); view.setPlayer(player);
        MediaItem.Builder media=new MediaItem.Builder().setUri(url); if(hls) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        if(subtitle>=0) {
            JSONArray streams=Json.array(source,"MediaStreams");
            for(int i=0;i<streams.length();i++) { JSONObject s=streams.optJSONObject(i); if(s==null||s.optInt("Index",-2)!=subtitle||!s.optString("Type").equals("Subtitle")) continue;
                if(s.optString("DeliveryMethod").equals("External")&&!s.optString("DeliveryUrl").isEmpty()) {
                    String codec=s.optString("Codec"); String mime=codec.equals("vtt")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP;
                    try { media.setSubtitleConfigurations(Collections.singletonList(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(resolve(s.optString("DeliveryUrl"))))
                        .setMimeType(mime).setLanguage(s.optString("Language")).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build())); } catch(Exception ignored) {}
                }
            }
        }
        initialAudioApplied=false;
        player.addListener(new Player.Listener() {
            @Override public void onTracksChanged(Tracks tracks) {
                if(g!=generation||released||initialAudioApplied||audio<0||!method.equals("DirectPlay")) return;
                initialAudioApplied=true;
                if(!selectLocalAudio(audio)) initialAudioApplied=false;
            }
            @Override public void onPlaybackStateChanged(int state) {
                if(g!=generation||released) return;
                if(state==Player.STATE_BUFFERING) { if(live) liveBuffer.begin(SystemClock.elapsedRealtime()); message.setVisibility(View.GONE); bufferBar.setVisibility(View.VISIBLE); }
                if(state==Player.STATE_READY) {
                    liveBuffer.reset();
                    bufferBar.setVisibility(View.GONE); message.setVisibility(View.GONE);
                    showChrome(false);
                    if(!started) { if(live&&lastReport==0) chrome.resetLiveClock(); started=true; report("",player.getCurrentPosition()); lastReport=SystemClock.elapsedRealtime(); }
                }
                if(state==Player.STATE_ENDED&&!live) {
                    if(app.settings.getBoolean("nextEpisode",true)&&"Episode".equals(item.optString("Type"))) nextEpisode(true);
                    else { message.setText(I18n.text("Wiedergabe beendet")); bufferBar.setVisibility(View.GONE); message.setVisibility(View.VISIBLE); showChrome(true); }
                }
            }
            @Override public void onPlayerError(PlaybackException error) {
                bufferBar.setVisibility(View.GONE);
                if(g!=generation||released) return;
                if(live&&liveCandidate+1<liveCandidates.size()) { retryLiveTransport(); return; }
                if(!forceHls&&app.settings.getBoolean("serverFallback",false)) { long p=Math.max(requestedPosition,position()); forceHls=true; message.setText(I18n.text("Kompatibler Serverstream wird versucht …")); load(p,true); }
                else { message.setText(I18n.text("Wiedergabefehler: ")+error.getErrorCodeName()+I18n.text("\nZurück oder erneut ‚Über Server abspielen‘ wählen.")); bufferBar.setVisibility(View.GONE); message.setVisibility(View.VISIBLE); showChrome(true); }
            }
        });
        player.setMediaItem(media.build(),position); player.prepare(); player.setPlayWhenReady(true);
        handler.removeCallbacks(tick); handler.post(tick);
    }
    private void retryLiveTransport() {
        if(player==null) return;
        if(liveCandidate+1<liveCandidates.size()) {
            String next=liveCandidates.get(++liveCandidate); view.setPlayer(null); player.release(); player=null;
            method=LiveStreams.server(api,next)?"DirectStream":"DirectPlay"; boolean hls=next.toLowerCase(Locale.ROOT).contains(".m3u8");
            prepare(next,hls,0,generation,app.videoCache.factory(api,true,hls));
        } else { player.stop(); player.prepare(); player.play(); }
    }
    private long position() { return player==null?requestedPosition:Math.max(0,player.getCurrentPosition()); }
    private void applySegments(long position) {
        boolean offer=false;
        for(int i=0;i<segments.length();i++) {
            JSONObject s=segments.optJSONObject(i); if(s==null||!PlaybackPolicy.active(s,position)) continue;
            String type=s.optString("Type"),key=s.optString("Id",type+":"+s.optLong("StartTicks")); long end=s.optLong("EndTicks")/10000;
            if(PlaybackPolicy.automatic(type)&&app.settings.getBoolean("autoSkip",true)&&app.settings.getBoolean("Intro".equalsIgnoreCase(type)?"skipIntro":"skipOutro",true)) {
                if(skipped.add(key)) { long duration=player.getDuration(); player.seekTo(duration>0?Math.min(end,duration):end); }
                continue;
            }
            offer=true; skipTarget=end; String label=type.equals("Recap")?I18n.text("Rückblick"):type.equals("Preview")?I18n.text("Vorschau"):type.equals("Commercial")?I18n.text("Werbung"):type.equals("Intro")?"Intro":type.equals("Outro")?"Outro":type;
            skip.setText(I18n.language().equals("en")?I18n.text("Überspringen")+" "+label:label+I18n.text(" überspringen")); skip.setVisibility(View.VISIBLE); break;
        }
        if(!offer) { if(skip.hasFocus()) view.requestFocus(); skip.setVisibility(View.GONE); }
    }
    private JSONObject reportBody(long pos) {
        return Json.obj("ItemId",item.optString("Id"),"MediaSourceId",sourceId,"PlaySessionId",sessionId,"PositionTicks",pos*10000,
            "IsPaused",player==null||!player.isPlaying(),"IsMuted",player!=null&&player.getVolume()==0,"CanSeek",!live,
            "PlayMethod",method,"AudioStreamIndex",audio,"SubtitleStreamIndex",subtitle,"EventName","TimeUpdate","PlaybackStartTimeTicks",0);
    }
    private void report(String endpoint,long pos) {
        if(sessionId==null||sessionId.isEmpty()) return; final JSONObject body=reportBody(pos);
        app.reports.execute(()-> { try { api.report(endpoint,body); } catch(Exception ignored) {} });
    }
    private void cleanupSession() {
        if(sessionId!=null) {
            report("/Stopped",position());
            JSONObject cached=item; long pos=position(),duration=player==null?0:player.getDuration();
            try { JSONObject user=Json.object(cached,"UserData"); user.put("PlaybackPositionTicks",pos*10000); if(duration>0&&pos>duration*.95) { user.put("Played",true); user.put("PlaybackPositionTicks",0); } cached.put("UserData",user); if(!live) { final Library db=library; final JSONObject saved=new JSONObject(cached.toString()); app.cacheIo.execute(()->db.put(saved)); } } catch(Exception ignored) {}
            closeNegotiated(liveId,sessionId);
        }
        sessionId=null; liveId=null;
    }
    private void closeNegotiated(String liveStream,String session) {
        if((liveStream==null||liveStream.isEmpty())&&(session==null||session.isEmpty())) return;
        app.reports.execute(()-> {
            if(liveStream!=null&&!liveStream.isEmpty()) try { api.post("/LiveStreams/Close",null,"LiveStreamId",liveStream); } catch(Exception ignored) {}
            if(session!=null&&!session.isEmpty()) try { api.call("DELETE","/Videos/ActiveEncodings",null,"DeviceId",api.account.device,"PlaySessionId",session); } catch(Exception ignored) {}
        });
    }
    private void streams(String type) {
        if(live&&player!=null) { liveTracks(type); return; }
        if(source==null) return; List<String> names=new ArrayList<>(); List<Integer> indices=new ArrayList<>();
        if(type.equals("Subtitle")) { names.add(I18n.text("Aus")); indices.add(-1); }
        JSONArray streams=Json.array(source,"MediaStreams");
        for(int i=0;i<streams.length();i++) { JSONObject s=streams.optJSONObject(i); if(s==null||!type.equals(s.optString("Type"))) continue;
            names.add(s.optString("DisplayTitle",s.optString("Language",I18n.text("Spur ")+s.optInt("Index")))); indices.add(s.optInt("Index"));
        }
        if(names.isEmpty()) { toast(I18n.text("Keine weiteren Spuren")); return; }
        new AlertDialog.Builder(this).setTitle(type.equals("Audio")?I18n.text("Tonspur"):I18n.text("Untertitel")).setItems(names.toArray(new String[0]),(d,w)-> { if(type.equals("Subtitle")) subtitleChosen=true; long p=position(); if(type.equals("Subtitle")&&indices.get(w)==-1&&player!=null) { subtitle=-1; player.setTrackSelectionParameters(SubtitlePolicy.off(player.getTrackSelectionParameters())); return; } if(type.equals("Audio")&&method.equals("DirectPlay")&&selectLocalAudio(indices.get(w))) { audio=indices.get(w); return; } if(type.equals("Audio")) audio=indices.get(w); else subtitle=indices.get(w); forceHls=app.settings.getBoolean("serverFallback",false); load(p,true); }).setNegativeButton(I18n.text("Abbrechen"),null).show();
    }
    private void liveTracks(String type) {
        boolean text=type.equals("Subtitle"); int trackType=text?C.TRACK_TYPE_TEXT:C.TRACK_TYPE_AUDIO;
        ExoPlayer current=player; List<String> labels=new ArrayList<>(); List<TrackSelectionOverride> selections=new ArrayList<>();
        if(text) { labels.add(I18n.text("Aus")); selections.add(null); }
        for(Tracks.Group group:current.getCurrentTracks().getGroups()) if(group.getType()==trackType) for(int track=0;track<group.length;track++) {
            if(!group.isTrackSupported(track)) continue; Format format=group.getTrackFormat(track);
            String label=format.label!=null?format.label:format.language!=null?format.language:I18n.text("Spur ")+(selections.size()+1);
            labels.add(label+(format.sampleMimeType==null?"":" · "+format.sampleMimeType.replace("audio/","").replace("application/",""))); selections.add(new TrackSelectionOverride(group.getMediaTrackGroup(),track));
        }
        if(labels.isEmpty()) { toast(I18n.text("Keine weiteren Spuren")); return; }
        new AlertDialog.Builder(this).setTitle(text?I18n.text("Untertitel"):I18n.text("Tonspur")).setItems(labels.toArray(new String[0]),(dialog,which)-> {
            if(player!=current) return; TrackSelectionOverride selection=selections.get(which);
            if(text) subtitleChosen=true;
            if(text&&selection==null) { subtitle=-1; current.setTrackSelectionParameters(SubtitlePolicy.off(current.getTrackSelectionParameters())); }
            else { current.setTrackSelectionParameters(current.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(trackType,false).clearOverridesOfType(trackType).setOverrideForType(selection).build()); if(text) subtitle=which-1; else audio=which; }
        }).setNegativeButton(I18n.text("Abbrechen"),null).show();
    }
    private boolean selectLocalAudio(int streamIndex) {
        if(player==null||source==null) return false;
        int ordinal=0,target=-1; JSONArray streams=Json.array(source,"MediaStreams");
        for(int i=0;i<streams.length();i++) { JSONObject stream=streams.optJSONObject(i); if(stream==null||!"Audio".equals(stream.optString("Type"))||stream.optBoolean("IsExternal")) continue; if(stream.optInt("Index",-1)==streamIndex) target=ordinal; ordinal++; }
        if(target<0) return false; ordinal=0;
        for(Tracks.Group group:player.getCurrentTracks().getGroups()) if(group.getType()==C.TRACK_TYPE_AUDIO) for(int track=0;track<group.length;track++) {
            if(ordinal++==target) { if(!group.isTrackSupported(track)) return false;
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),track)).build()); return true;
            }
        }
        return false;
    }
    private void versions() {
        JSONArray sources=Json.array(item,"MediaSources"); if(sources.length()<2) { toast(I18n.text("Eine Medienversion vorhanden")); return; }
        String[] labels=new String[sources.length()]; for(int i=0;i<sources.length();i++) labels[i]=sources.optJSONObject(i).optString("Name",I18n.text("Version ")+(i+1));
        new AlertDialog.Builder(this).setTitle(I18n.text("Medienversion")).setItems(labels,(d,w)-> { sourceId=sources.optJSONObject(w).optString("Id"); audio=-1; forceHls=false; load(position(),false); }).show();
    }
    private void chapters() {
        JSONArray chapters=Json.array(item,"Chapters"); if(chapters.length()==0) { toast(I18n.text("Keine Kapitel")); return; }
        String[] labels=new String[chapters.length()]; for(int i=0;i<chapters.length();i++) labels[i]=chapters.optJSONObject(i).optString("Name",I18n.text("Kapitel ")+(i+1));
        new AlertDialog.Builder(this).setTitle(I18n.text("Kapitel")).setItems(labels,(d,w)-> { if(player!=null) player.seekTo(chapters.optJSONObject(w).optLong("StartPositionTicks")/10000); }).show();
    }
    private void nextEpisode(boolean automatic) {
        if(!"Episode".equals(item.optString("Type"))) return;
        final String id=item.optString("Id"),series=item.optString("SeriesId"); int g=generation;
        network.execute(()-> { try {
            JSONObject next=library.next(item);
            // Resolve from server so an incompletely cached library cannot skip an episode.
            JSONArray episodes=Json.array(api.get("/Shows/"+series+"/Episodes","UserId",api.account.user,"StartItemId",id,"Limit",2,"Fields","Overview,ParentId"),"Items");
            if(episodes.length()>1&&id.equals(episodes.optJSONObject(0).optString("Id"))) next=episodes.optJSONObject(1);
            else if(episodes.length()==1&&id.equals(episodes.optJSONObject(0).optString("Id"))) next=null;
            JSONObject chosen=next;
            runOnUiThread(()-> { if(g!=generation||released) return; if(chosen==null) { toast(I18n.text("Keine weitere Folge")); if(automatic) finish(); return; }
                cleanupSession(); item=chosen; audio=-1; sourceId=null; forceHls=false; load(0,false);
            });
        } catch(Exception e) { runOnUiThread(()-> { if(g==generation) toast(I18n.text("Nächste Folge konnte nicht geladen werden")); }); } });
    }
    private void channels() {
        int g=generation;
        network.execute(()-> { try { List<JSONObject> list=new ArrayList<>(); JSONArray channels=Json.array(api.get("/LiveTv/Channels","UserId",api.account.user,"SortBy","SortName"),"Items");
            for(int i=0;i<channels.length();i++) { JSONObject c=channels.optJSONObject(i); if(c!=null) list.add(c); }
            runOnUiThread(()-> { if(g!=generation||released) return; new AlertDialog.Builder(this).setTitle(I18n.text("Sender wechseln")).setAdapter(new ChannelPicker(this,app.images,list),(d,w)->switchChannel(list.get(w))).setNegativeButton(I18n.text("Abbrechen"),null).show(); });
        } catch(Exception e) { runOnUiThread(()->toast(I18n.text("Senderliste nicht verfügbar"))); } });
    }
    private void switchChannel(JSONObject channel) { cleanupSession(); item=channel; chrome.resetLiveClock(); live=true; audio=-1; sourceId=null; forceHls=false; load(0,false); }
    private void zap(int direction) {
        int g=generation; String id=item.optString("Id");
        network.execute(()-> { try {
            JSONArray channels=Json.array(api.get("/LiveTv/Channels","UserId",api.account.user,"SortBy","SortName"),"Items");
            for(int i=0;i<channels.length();i++) if(id.equals(channels.optJSONObject(i).optString("Id"))) { JSONObject c=channels.optJSONObject((i+direction+channels.length())%channels.length()); runOnUiThread(()-> { if(g==generation&&!released) switchChannel(c); }); return; }
        } catch(Exception ignored) {} });
    }
    private void guide() {
        int g=generation; final String id=item.optString("Id");
        network.execute(()-> { try {
            JSONArray programs=Json.array(api.get("/LiveTv/Programs","UserId",api.account.user,"ChannelIds",id,"MinEndDate",java.time.Instant.now().toString(),"Limit",12,"SortBy","StartDate","SortOrder","Ascending","Fields","Overview"),"Items");
            String[] labels=new String[programs.length()];
            for(int i=0;i<programs.length();i++) { JSONObject p=programs.optJSONObject(i); String time=p.optString("StartDate"); try { time=java.time.Instant.parse(time).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString(); } catch(Exception ignored) {} labels[i]=time+"  "+p.optString("Name"); }
            runOnUiThread(()-> { if(g!=generation||released) return; new AlertDialog.Builder(this).setTitle(I18n.text("Programm · ")+item.optString("Name")).setItems(labels,(d,w)->program(programs.optJSONObject(w))).setNegativeButton(I18n.text("Schließen"),null).show(); });
        } catch(Exception e) { runOnUiThread(()->toast(I18n.text("Programm nicht verfügbar"))); } });
    }
    private void program(JSONObject p) {
        new AlertDialog.Builder(this).setTitle(p.optString("Name")).setMessage(p.optString("Overview",I18n.text("Keine Beschreibung")))
            .setPositiveButton(I18n.text("Aufnehmen"),(d,w)-> { network.execute(()-> { try { JSONObject timer=api.get("/LiveTv/Timers/Defaults","ProgramId",p.optString("Id")); timer.put("ProgramId",p.optString("Id")); api.post("/LiveTv/Timers",timer); runOnUiThread(()->toast(I18n.text("Aufnahme geplant"))); } catch(Exception e) { runOnUiThread(()->toast(I18n.text("Aufnahme konnte nicht geplant werden"))); } }); }).setNegativeButton(I18n.text("Schließen"),null).show();
    }
    private void toast(String s) { if(!released) Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private boolean heldSeek(int direction,KeyEvent event) {
        if(player==null||live) return true;
        if(event.getAction()==KeyEvent.ACTION_DOWN) {
            long now=SystemClock.elapsedRealtime(),length=player.getDuration();
            if(!seekGesture.active()) { seekDirection=direction; seekGesture.begin(event.getKeyCode(),direction,position(),now,length); }
            else seekGesture.repeat(now,length);
            showChrome(false); handler.removeCallbacks(hideControls);
            chrome.preview(seekGesture.target(),length,SeekGesture.step(Math.max(0,event.getEventTime()-event.getDownTime())),player.getPlayWhenReady());
        } else if(event.getAction()==KeyEvent.ACTION_UP&&seekGesture.active()) {
            long target=seekGesture.target(); seekGesture.reset();
            if(!event.isCanceled()) seekTo(target); else showChrome(false);
        }
        return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int remote=e.getKeyCode();
        if(seekGesture.active()&&remote==seekGesture.key()) return heldSeek(seekDirection,e);
        boolean arrow=remote==KeyEvent.KEYCODE_DPAD_LEFT||remote==KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean mediaSeek=remote==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||remote==KeyEvent.KEYCODE_MEDIA_REWIND;
        if(!live&&(mediaSeek||arrow&&(chrome.getVisibility()!=View.VISIBLE||chrome.timeline.hasFocus()||view.hasFocus()))) {
            if(e.getAction()==KeyEvent.ACTION_DOWN) { heldSeek(remote==KeyEvent.KEYCODE_DPAD_RIGHT||remote==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD?1:-1,e); chrome.timeline.requestFocus(); }
            return true;
        }
        if(e.getAction()==KeyEvent.ACTION_DOWN) {
            int key=e.getKeyCode();
            if(live&&(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN)) { zap(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1); return true; }
            if(key==KeyEvent.KEYCODE_MENU) { if(e.getRepeatCount()==0) { showChrome(false); chrome.options.requestFocus(); } return true; }
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN) {
                if(chrome.getVisibility()!=View.VISIBLE||view.hasFocus()) { showChrome(true); if(key==KeyEvent.KEYCODE_DPAD_UP&&skip.getVisibility()==View.VISIBLE) skip.requestFocus(); return true; }
                if(!live&&key==KeyEvent.KEYCODE_DPAD_UP&&!chrome.timeline.hasFocus()&&!skip.hasFocus()) { chrome.timeline.requestFocus(); return true; }
                if(key==KeyEvent.KEYCODE_DPAD_UP&&chrome.timeline.hasFocus()) { if(skip.getVisibility()==View.VISIBLE) skip.requestFocus(); return true; }
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&chrome.timeline.hasFocus()) { chrome.play.requestFocus(); return true; }
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&chrome.hasFocus()) { hideChrome(); return true; }
            }
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) { if(chrome.getVisibility()!=View.VISIBLE) { showChrome(true); return true; } if(view.hasFocus()||chrome.timeline.hasFocus()) { if(e.getRepeatCount()==0) { chrome.play.requestFocus(); chrome.play.performClick(); } return true; } }
            if((key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE)&&player!=null) {
                if(key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&player.getPlayWhenReady()) player.pause(); else player.play(); showChrome(false); return true;
            }
            if(chrome.getVisibility()==View.VISIBLE&&key!=KeyEvent.KEYCODE_BACK) { handler.removeCallbacks(hideControls); handler.postDelayed(hideControls,app.settings.getInt("controlsSeconds",5)*1000L); }
        }
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed() { if(seekGesture.active()) { seekGesture.reset(); showChrome(true); } else if(chrome!=null&&chrome.getVisibility()==View.VISIBLE) hideChrome(); else finish(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putLong("position",position()); state.putInt("audioStreamIndex",audio); state.putString("mediaSourceId",sourceId); super.onSaveInstanceState(state); }
    @Override protected void onStop() { super.onStop(); release(); finish(); }
    private void release() {
        if(released) return; released=true; if(bufferBar!=null) bufferBar.setVisibility(View.GONE); generation++; handler.removeCallbacksAndMessages(null); cleanupSession();
        if(player!=null) { view.setPlayer(null); player.release(); player=null; }
        api.http.dispatcher().cancelAll(); network.shutdownNow(); app.playing=false; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        app.reports.execute(()->app.channels.refresh(null));
    }
    @Override protected void onDestroy() { release(); super.onDestroy(); }
}
