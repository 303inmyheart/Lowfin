package de.lowfin.tv;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import org.json.*;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends Activity {
    private LowfinApp app;
    private FrameLayout root;
    private LinearLayout body,drawer;
    private FrameLayout contentStage;
    private View drawerShade,drawerReturnFocus,lastContentFocus;
    private Button menuButton;
    private boolean drawerOpen,drawerRightKey;
    private TextView status;
    private RecyclerView grid;
    private final Deque<Screen> history=new ArrayDeque<>();
    private Screen screen;
    private int generation;
    private Runnable homeReload;
    private LinearLayout sidebar;
    private final List<JSONObject> views=new ArrayList<>();
    private boolean viewsLoading;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable statusTick=new Runnable() { public void run() {
        if(status!=null) status.setText(app.syncing?(screen!=null&&screen.kind.equals("settings")?app.syncStatus:app.syncSummary):screen!=null&&screen.kind.equals("settings")?app.syncStatus:app.account==null?"":app.account.name+"  ·  HD");
        handler.postDelayed(this,2000);
    }};
    private static final int PAGE=60,KEY_FILE_REQUEST=1201,KEY_STORAGE_REQUEST=1202;
    private boolean importActive,importBusy;
    private String lastImportNotice="",pendingKeyDocument;
    private TextView importStatus;
    private final Runnable importTick=new Runnable() { public void run() { if(!importActive) return; if(app.keyImport.needsCheck()) checkKeyFile(false,null); handler.postDelayed(this,5000); } };

    private static final class Screen {
        final String kind,title,type,parent; final JSONObject item;
        int page; Parcelable scroll;
        Screen(String kind,String title,String type,String parent,JSONObject item) { this.kind=kind; this.title=title; this.type=type; this.parent=parent; this.item=item; }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); app=(LowfinApp)getApplication(); Ui.immersive(this);
        if(app.account==null) login(""); else {
            show(new Screen("home",I18n.text("Deine Bibliothek"),null,null,null),false); checkSession();
            loadViews();
            String last=app.settings.getString("lastChannel","");
            if(hasHomeLink(getIntent())) handleHomeLink(getIntent());
            else if(savedNone(state)&&app.settings.getBoolean("liveOnLaunch",false)&&!last.isEmpty()) play(Json.obj("Id",last,"Type","TvChannel","Name","Live TV"),0);
        }
    }
    private boolean savedNone(Bundle state) { return state==null; }
    @Override protected void onResume() { super.onResume(); Ui.immersive(this); importActive=true; handler.removeCallbacks(importTick); handler.post(importTick); handler.post(statusTick); if(homeReload!=null&&screen!=null&&screen.kind.equals("home")) homeReload.run(); }
    @Override protected void onPause() { importActive=false; handler.removeCallbacks(importTick); handler.removeCallbacks(statusTick); super.onPause(); }
    @Override protected void onDestroy() { generation++; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void checkSession() {
        final Api api=app.api;
        app.io.execute(()-> {
            try { api.get("/Users/Me");
                final Library db=app.library;
                app.cacheIo.execute(()-> {
                    String last=db.state("lastSync"); long time=0; try { time=Long.parseLong(last); } catch(Exception ignored) {}
                    final boolean due=System.currentTimeMillis()-time>app.settings.getInt("cacheHours",24)*60*60*1000L;
                    runOnUiThread(()-> { if(app.api==api&&!isFinishing()&&!app.syncing&&due&&app.settings.getBoolean("autoPrecache",true)) startCache(); });
                });
            } catch(Api.Failure e) { if(e.status==401||e.status==403) runOnUiThread(()-> { if(app.api==api) { app.logout(); history.clear(); login(I18n.text("Bitte erneut anmelden")); } }); }
            catch(Exception e) { runOnUiThread(()-> { if(app.api==api) toast(I18n.text("Server nicht erreichbar. Gespeicherte Bibliothek bleibt verfügbar.")); }); }
        });
    }
    private void login(String error) {
        generation++; screen=null; grid=null;
        LinearLayout outer=Ui.column(this); outer.setGravity(Gravity.CENTER); outer.setBackgroundColor(Ui.BG);
        LinearLayout form=Ui.column(this); outer.addView(form,new LinearLayout.LayoutParams(Ui.dp(this,470),-2));
        form.addView(Ui.title(this,"LOWFIN",36)); Ui.space(form,8);
        form.addView(Ui.button(this,"Language / Sprache",this::languageChoice)); Ui.space(form,8);
        form.addView(Ui.text(this,I18n.text("Deine Medien. Direkt auf deinem TV."),17,Ui.MUTED)); Ui.space(form,24);
        EditText server=Ui.input(this,I18n.text("Server · https://jellyfin.example"),false);
        server.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(app.settings.getString("lastServer","")); form.addView(server); Ui.space(form,10);
        EditText user=Ui.input(this,I18n.text("Benutzername"),false); form.addView(user); Ui.space(form,10);
        EditText password=Ui.input(this,I18n.text("Passwort"),true); form.addView(password); Ui.space(form,16);
        TextView hint=Ui.text(this,error,14,Ui.MUTED);
        Button signIn=Ui.button(this,I18n.text("Anmelden"),()-> {
            String address=server.getText().toString().trim().replaceAll("/+$","");
            okhttp3.HttpUrl u=okhttp3.HttpUrl.parse(address);
            if(u==null||u.host().isEmpty()||!u.username().isEmpty()||!u.password().isEmpty()||u.query()!=null||u.fragment()!=null) { hint.setText(I18n.text("Bitte eine vollständige Serveradresse ohne Zugangsdaten oder URL-Parameter angeben.")); return; }
            String username=user.getText().toString().trim(),secret=password.getText().toString();
            if(username.isEmpty()) { hint.setText(I18n.text("Benutzername fehlt")); return; }
            hint.setText(I18n.text("Verbindung wird hergestellt …"));
            final int g=generation;
            app.io.execute(()-> {
                try {
                    Api api=new Api(new Account(address,"","","",Account.device(this)));
                    JSONObject response=api.post("/Users/AuthenticateByName",Json.obj("Username",username,"Pw",secret));
                    JSONObject account=Json.object(response,"User");
                    Account a=new Account(address,account.getString("Id"),response.getString("AccessToken"),account.optString("Name",username),Account.device(this));
                    runOnUiThread(()-> { if(g!=generation||isFinishing()) return;
                        try { a.save(this); app.setAccount(a); app.settings.edit().putString("lastServer",address).apply(); password.setText("");
                            views.clear(); viewsLoading=false; show(new Screen("home",I18n.text("Deine Bibliothek"),null,null,null),false); loadViews(); if(app.settings.getBoolean("autoPrecache",true)) startCache(); if(hasHomeLink(getIntent())) handleHomeLink(getIntent());
                        } catch(Exception e) { hint.setText(I18n.text("Sitzung konnte nicht sicher gespeichert werden.")); }
                    });
                } catch(Exception e) { runOnUiThread(()-> { if(g==generation) hint.setText(e instanceof Api.Failure?e.getMessage():I18n.text("Verbindung fehlgeschlagen. Adresse und Netzwerk prüfen.")); }); }
            });
        });
        Ui.editorAction(password,signIn::performClick); form.addView(signIn); Ui.space(form,12); form.addView(hint);
        status=null; setContentView(outer); server.requestFocus();
    }
    private BackdropView detailBackdrop;
    private LinearLayout homeDescription;
    private TextView homeFilmTitle,homeFilmMeta,homeFilmOverview;
    private RatingsRow homeRatings;
    private Runnable ratingFocusUpdate;
    private int focusedHomeRow=-1;
    private String focusedHomeId="";
    private Runnable homeBackdropUpdate;
    private void shell(String title) {
        root=new FrameLayout(this); root.setBackgroundColor(Ui.BG); boolean artworkScreen=screen!=null&&(screen.kind.equals("detail")||screen.kind.equals("home"));
        if(!artworkScreen) root.setPadding(Ui.dp(this,20),Ui.dp(this,18),Ui.dp(this,20),Ui.dp(this,18));
        drawerOpen=false; drawerReturnFocus=null; lastContentFocus=null;
        FrameLayout stage=new FrameLayout(this); contentStage=stage; root.addView(stage,new FrameLayout.LayoutParams(-1,-1)); detailBackdrop=null;
        if(screen!=null&&(screen.kind.equals("detail")||screen.kind.equals("home"))) {
            detailBackdrop=new BackdropView(this); stage.addView(detailBackdrop,new FrameLayout.LayoutParams(-1,-1));
            View shade=new View(this); shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xbd0c0c0d,0x660c0c0d})); stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            View fade=new View(this); fade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xef0c0c0d,0x200c0c0d})); stage.addView(fade,new FrameLayout.LayoutParams(-1,-1));
        }
        LinearLayout content=Ui.column(this); content.setPadding(Ui.dp(this,artworkScreen?30:10),Ui.dp(this,artworkScreen?22:4),Ui.dp(this,artworkScreen?30:10),Ui.dp(this,artworkScreen?18:0)); stage.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heading=Ui.row(this); menuButton=null; heading.addView(Ui.title(this,title,22)); content.addView(heading); Ui.space(content,8);
        body=Ui.column(this); content.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        drawerShade=new View(this); drawerShade.setBackgroundColor(0x99000000); drawerShade.setVisibility(View.GONE); drawerShade.setOnClickListener(v->setDrawer(false)); root.addView(drawerShade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout left=Ui.column(this); drawer=left; left.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,12)); left.setBackground(Ui.background(0xff111113,0));
        TextView brand=Ui.title(this,"LOWFIN",20); brand.setLetterSpacing(.12f); left.addView(brand); Ui.space(left,16);
        ScrollView menu=new ScrollView(this); menu.setVerticalScrollBarEnabled(false); sidebar=Ui.column(this); menu.addView(sidebar); left.addView(menu,new LinearLayout.LayoutParams(-1,0,1));
        Ui.space(left,12); status=Ui.text(this,"",12,Ui.MUTED); status.setMaxLines(3); left.addView(status);
        root.addView(left,new FrameLayout.LayoutParams(Ui.dp(this,214),-1,Gravity.START)); left.setVisibility(View.GONE);
        buildSidebar(); grid=null; setContentView(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus,newFocus)-> {
            if(newFocus==null||newFocus==menuButton) return;
            for(android.view.ViewParent parent=newFocus.getParent();parent!=null;parent=parent.getParent()) if(parent==contentStage) { lastContentFocus=newFocus; break; }
        });
    }
    private void setDrawer(boolean open) {
        if(drawer==null||drawerOpen==open) return; drawerOpen=open; boolean animate=app.settings.getBoolean("animations",true);
        drawer.animate().withEndAction(null).cancel(); drawerShade.animate().withEndAction(null).cancel();
        if(open) {
            drawerReturnFocus=root.findFocus()==menuButton?lastContentFocus:root.findFocus(); contentStage.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            drawer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            drawerShade.setVisibility(View.VISIBLE); drawer.setVisibility(View.VISIBLE);
            drawer.setTranslationX(animate?-Ui.dp(this,214):0); drawerShade.setAlpha(animate?0:1);
            if(animate) { drawer.animate().translationX(0).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(app.settings.getBoolean("smoothNavigation",true)?230:140).start(); drawerShade.animate().alpha(1).setDuration(140).start(); }
            Button target=null;
            for(int i=0;i<sidebar.getChildCount();i++) if(sidebar.getChildAt(i) instanceof Button) { Button button=(Button)sidebar.getChildAt(i); if(target==null) target=button; if(Boolean.TRUE.equals(button.getTag())) { target=button; break; } }
            if(target!=null) {
                final Button selected=target; selected.requestFocus();
                drawer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override public void onLayoutChange(View view,int left,int top,int right,int bottom,int oldLeft,int oldTop,int oldRight,int oldBottom) {
                        view.removeOnLayoutChangeListener(this); view.post(()-> { if(drawerOpen&&selected.isShown()&&root.findFocus()==null) { selected.clearFocus(); selected.requestFocus(); } });
                    }
                });
            }
        } else {
            contentStage.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            drawer.clearFocus(); drawer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            drawerShade.setVisibility(View.GONE); drawerShade.setAlpha(0);
            if(animate) {
                drawer.animate().translationX(-Ui.dp(this,214)).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(app.settings.getBoolean("smoothNavigation",true)?200:120).withEndAction(()-> { if(!drawerOpen) { drawer.setVisibility(View.GONE); drawer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS); } }).start();
                drawerShade.animate().alpha(0).setDuration(120).withEndAction(()-> { if(!drawerOpen) drawerShade.setVisibility(View.GONE); }).start();
            } else { drawer.setVisibility(View.GONE); drawerShade.setVisibility(View.GONE); drawer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS); }
            if(drawerReturnFocus!=null&&drawerReturnFocus!=menuButton&&drawerReturnFocus.isAttachedToWindow()&&drawerReturnFocus.isShown()) drawerReturnFocus.requestFocus();
            else if(grid!=null&&grid.getAdapter() instanceof HomeAdapter) ((HomeAdapter)grid.getAdapter()).requestRow(Math.max(0,focusedHomeRow));
            else if(grid!=null&&grid.getChildCount()>0) grid.getChildAt(0).requestFocus(); else if(body!=null) { body.setFocusable(true); body.requestFocus(); }
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(!drawerOpen&&screen!=null&&screen.kind.equals("detail")&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                View focused=root.findFocus();
                if(focused instanceof Button&&focused.getParent() instanceof LinearLayout) {
                    LinearLayout actions=(LinearLayout)focused.getParent(); int index=actions.indexOfChild(focused);
                    if(index>0) actions.getChildAt(index-1).requestFocus(); else setDrawer(true);
                } else setDrawer(true);
            }
            return true;
        }
        if(app.account!=null&&!drawerOpen&&grid==null&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT) { if(event.getAction()==KeyEvent.ACTION_DOWN) setDrawer(true); return true; }
        if(!drawerOpen&&grid!=null&&grid.getLayoutManager() instanceof GridLayoutManager&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT) {
            View focused=root.findFocus(); View card=focused==null?null:grid.findContainingItemView(focused); int position=card==null?RecyclerView.NO_POSITION:grid.getChildAdapterPosition(card);
            GridLayoutManager layout=(GridLayoutManager)grid.getLayoutManager();
            if(position!=RecyclerView.NO_POSITION&&layout.getSpanSizeLookup().getSpanIndex(position,layout.getSpanCount())==0) { if(event.getAction()==KeyEvent.ACTION_DOWN) setDrawer(true); return true; }
        }
        if(screen!=null&&event.getKeyCode()==KeyEvent.KEYCODE_MENU) { if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) setDrawer(!drawerOpen); return true; }
        if(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT&&(drawerOpen||drawerRightKey)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) { drawerRightKey=true; setDrawer(false); } else drawerRightKey=false;
            return true;
        }
        if(drawerOpen&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()!=KeyEvent.KEYCODE_DPAD_LEFT) {
                List<Button> buttons=new ArrayList<>(); for(int i=0;i<sidebar.getChildCount();i++) if(sidebar.getChildAt(i) instanceof Button) buttons.add((Button)sidebar.getChildAt(i));
                int index=buttons.indexOf(root.findFocus()); int target=Math.max(0,Math.min(buttons.size()-1,index+(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP?-1:1)));
                if(!buttons.isEmpty()) buttons.get(target).requestFocus();
            } return true;
        }
        if(!drawerOpen&&screen!=null&&screen.kind.equals("home")&&grid!=null&&grid.getAdapter() instanceof HomeAdapter&&focusedHomeRow>=0&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) ((HomeAdapter)grid.getAdapter()).moveRow(focusedHomeRow,event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP?-1:1);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void sideButton(String label,Runnable action) {
        Button button=Ui.button(this,label,action); button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); button.setSingleLine(true); button.setStateListAnimator(null); button.setElevation(0); button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        boolean selected=screen!=null&&(label.equals(screen.title)||label.equals(I18n.text("Start"))&&screen.kind.equals("home")||label.equals(I18n.text("Suche"))&&screen.kind.equals("search")&&screen.type.equals("term"));
        selected=selected||label.equals("Genres")&&screen!=null&&screen.kind.startsWith("genre");
        button.setTag(selected); Ui.navigation(button,selected); sidebar.addView(button,new LinearLayout.LayoutParams(-1,Ui.dp(this,44))); Ui.space(sidebar,3);
    }
    private void buildSidebar() {
        String focusedLabel=sidebar.findFocus() instanceof Button?((Button)sidebar.findFocus()).getText().toString():null;
        sidebar.removeAllViews(); sideButton(I18n.text("Start"),()->show(new Screen("home",I18n.text("Deine Bibliothek"),null,null,null),false)); sideButton(I18n.text("Suche"),this::search); sideButton("Genres",()->show(new Screen("genres","Genres",null,null,null),false));
        Ui.space(sidebar,12); sideButton("Live TV",()->show(new Screen("live","Live TV",null,null,null),false));
        sideButton(I18n.text("Zufälliger Film"),()-> { String parent=screen.kind.equals("browse")&&screen.type.contains("Movie")?screen.parent:null; setDrawer(false); randomMovie(parent); });
        if(screen.kind.equals("genre")) sideButton(HomeGenres.read(app.settings,app.account).contains(screen.parent)?I18n.text("Von Startseite lösen"):I18n.text("Auf Startseite anheften"),()-> { HomeGenres.toggle(app.settings,app.account,screen.parent); show(screen,false); });
        Ui.space(sidebar,12); sidebar.addView(Ui.text(this,I18n.text("BIBLIOTHEKEN"),11,Ui.MUTED)); Ui.space(sidebar,10);
        if(views.isEmpty()) {
            sideButton(I18n.text("Filme"),()->show(new Screen("browse",I18n.text("Filme"),"Movie",null,null),false)); sideButton(I18n.text("Serien"),()->show(new Screen("browse",I18n.text("Serien"),"Series",null,null),false));
        } else for(JSONObject library:views) {
            if("livetv".equals(library.optString("CollectionType"))) continue;
            sideButton(library.optString("Name"),()->show(new Screen("browse",library.optString("Name"),viewTypes(library),library.optString("Id"),library),false));
        }
        sideButton(I18n.text("Später ansehen"),()->special(I18n.text("Später ansehen"),"watchLater"));
        sideButton(I18n.text("Favoriten"),()->special(I18n.text("Favoriten"),"favorites")); sideButton(I18n.text("Einstellungen"),()->show(new Screen("settings",I18n.text("Einstellungen"),null,null,null),false));
        if(drawerOpen&&focusedLabel!=null) for(int i=0;i<sidebar.getChildCount();i++) { View button=sidebar.getChildAt(i); if(button instanceof Button&&focusedLabel.equals(((Button)button).getText().toString())) { button.requestFocus(); break; } }
    }
    static String viewTypes(JSONObject library) { switch(library.optString("CollectionType")) { case "movies":return "Movie"; case "tvshows":return "Series"; case "homevideos":return "Video"; case "boxsets":return "BoxSet"; default:return "Movie,Series,Video,BoxSet"; } }
    private void loadViews() {
        if(viewsLoading||app.api==null) return; viewsLoading=true; final Api api=app.api; final Library db=app.library;
        final boolean[] fresh={false};
        app.cacheIo.execute(()-> {
            try { String saved=db.state("views"); if(!saved.isEmpty()) { List<JSONObject> cached=list(new JSONArray(saved));
                runOnUiThread(()-> { if(app.api==api&&sidebar!=null&&!fresh[0]) { views.clear(); views.addAll(cached); buildSidebar(); } });
            } } catch(Exception ignored) {}
        });
        app.navigationIo.execute(()-> {
            try { JSONArray a=Json.array(api.get("/Users/"+api.account.user+"/Views"),"Items"); List<JSONObject> result=list(a);
                runOnUiThread(()-> { if(app.api==api&&sidebar!=null) { fresh[0]=true; views.clear(); views.addAll(result); buildSidebar(); } });
                app.cacheIo.execute(()-> { db.state("views",a.toString()); if(app.api==api) app.channels.refresh(null); });
            } catch(Exception e) { runOnUiThread(()-> { if(app.api==api) toast(I18n.text("Bibliotheken konnten nicht aktualisiert werden: ")+e.getMessage()); }); }
            finally { runOnUiThread(()-> { if(app.api==api) viewsLoading=false; }); }
        });
    }
    private void show(Screen next,boolean push) {
        if(push&&screen!=null) { if(grid!=null) screen.scroll=grid.getLayoutManager().onSaveInstanceState(); history.push(screen); }
        else if(!push) history.clear();
        if(homeBackdropUpdate!=null) handler.removeCallbacks(homeBackdropUpdate);
        focusedHomeRow=-1; focusedHomeId=""; homeDescription=null;
        screen=next; homeReload=null; generation++; shell(next.kind.equals("detail")?"Details":next.title);
        switch(next.kind) {
            case "home": home(); break;
            case "recommendations": recommendationItems(); break;
            case "browse": browse(); break;
            case "seriesResume": seriesResume(); break;
            case "genres": genres(); break;
            case "genre": genreItems(); break;
            case "detail": detail(next.item); break;
            case "seasons": seasons(next.item); break;
            case "episodes": episodes(); break;
            case "live": live(); break;
            case "recordings": recordings(); break;
            case "settings": settings(); break;
            case "genreSettings": new Options(this,app,body,this::refreshOptions).genres(); break;
            case "appearanceSettings": new Options(this,app,body,this::refreshOptions).appearance(); break;
            case "playbackSettings": new Options(this,app,body,this::refreshOptions).playback(); break;
            case "ratings": ratingSettings(); break;
            case "search": results(); break;
        }
        Motion.enter(contentStage,true);
    }
    private interface Work { List<JSONObject> run() throws Exception; }
    private void async(MediaAdapter adapter,Work work,TextView note) {
        int g=generation;
        (note==null?app.cacheIo:app.io).execute(()-> { try { List<JSONObject> data=work.run(); runOnUiThread(()-> { if(g!=generation||isFinishing()||(note==null&&adapter.fresh)) return;
            boolean first=adapter.items.isEmpty(); adapter.set(data); if(note!=null) { adapter.fresh=true; note.setText(data.isEmpty()?I18n.text("Keine Einträge"):""); } if(first) restoreFocus();
        }); } catch(Exception e) { runOnUiThread(()-> { if(g==generation&&note!=null) note.setText(adapter.items.isEmpty()?I18n.text("Laden fehlgeschlagen: ")+e.getMessage():I18n.text("Gespeicherte Daten · Server aktuell nicht erreichbar")); }); } });
    }
    private static List<JSONObject> list(JSONArray a) { List<JSONObject> out=new ArrayList<>(); for(int i=0;i<a.length();i++) { JSONObject j=a.optJSONObject(i); if(j!=null) out.add(j); } return out; }
    private TextView note(String s) { TextView t=Ui.text(this,s,13,Ui.MUTED); body.addView(t); Ui.space(body,5); return t; }
    private MediaAdapter cards(boolean compact) {
        grid=new RecyclerView(this); int columns=app.settings.getInt("gridColumns",8);
        GridLayoutManager layout=new GridLayoutManager(this,columns); grid.setLayoutManager(layout); grid.setItemAnimator(null); grid.setHasFixedSize(true); grid.setItemViewCacheSize(4);
        MediaAdapter adapter=new MediaAdapter(app.images,compact,this::open); adapter.wide=screen!=null&&"resume".equals(screen.type); adapter.onActions(this::itemActions); adapter.onOpenMenu(()->setDrawer(true)); grid.setAdapter(adapter);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() { @Override public int getSpanSize(int position) { return adapter.span(position); } });
        body.addView(grid,new LinearLayout.LayoutParams(-1,0,1)); return adapter;
    }
    private void restoreFocus() {
        if(grid==null) return;
        if(screen.scroll!=null) grid.getLayoutManager().onRestoreInstanceState(screen.scroll);
        grid.post(()-> { if(grid!=null&&grid.getChildCount()>0) grid.getChildAt(0).requestFocus(); });
    }
    private void home() {
        final List<String> pinned=app.settings.getBoolean("genreRows",true)?HomeGenres.read(app.settings,app.account):new ArrayList<>();
        grid=new RecyclerView(this) {
            @Override public boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rect,boolean immediate) { return false; }
        }; grid.setLayoutManager(new LinearLayoutManager(this) {
            @Override public boolean onRequestChildFocus(RecyclerView parent,RecyclerView.State state,View child,View focused) { return true; }
            @Override public boolean requestChildRectangleOnScreen(RecyclerView parent,View child,android.graphics.Rect rect,boolean immediate,boolean keepFocusedChildVisible) { return false; }
        }); grid.setItemAnimator(null); grid.setItemViewCacheSize(1);
        final HomeAdapter[] holder=new HomeAdapter[1];
        HomeAdapter adapter=new HomeAdapter(app.images,pinned,(j,row)-> { if(row<2) play(j,row==0?Json.position(j):0); else open(j); },row->{
            if(row==0) special(I18n.text("Weiterschauen"),"resume"); else if(row==1) special(I18n.text("Als Nächstes"),"next");
            else if(HomeAdapter.recommendation(row)) show(new Screen("recommendations",holder[0].labels.get(row),null,null,Json.obj("Items",new JSONArray(holder[0].rows.get(row)))),true);
            else if(row>=HomeAdapter.GENRE_START) show(new Screen("genre",pinned.get(row-HomeAdapter.GENRE_START),"Movie,Series",pinned.get(row-HomeAdapter.GENRE_START),null),true);
            else show(new Screen("browse",I18n.text("Filme"),"Movie",null,null),true);
        },this::homeFocus,row->{ setHomeFocusMode(row); holder[0].requestRow(row); }); holder[0]=adapter; adapter.onActions(this::itemActions); adapter.onOpenMenu(()->setDrawer(true));
        grid.setAdapter(adapter); body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        homeDescription=Ui.column(this); homeDescription.setPadding(Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,14),0); homeDescription.setVisibility(View.GONE);
        homeFilmTitle=Ui.title(this,"",21); homeFilmTitle.setSingleLine(true); homeFilmTitle.setEllipsize(android.text.TextUtils.TruncateAt.END); homeDescription.addView(homeFilmTitle);
        homeFilmMeta=Ui.text(this,"",11,Ui.MUTED); homeFilmMeta.setSingleLine(true); homeFilmMeta.setEllipsize(android.text.TextUtils.TruncateAt.END); homeDescription.addView(homeFilmMeta); Ui.space(homeDescription,6);
        homeRatings=new RatingsRow(this); homeDescription.addView(homeRatings); Ui.space(homeDescription,6);
        homeFilmOverview=Ui.text(this,"",14,0xffdddddf); homeFilmOverview.setMaxLines(3); homeFilmOverview.setEllipsize(android.text.TextUtils.TruncateAt.END); homeDescription.addView(homeFilmOverview,new LinearLayout.LayoutParams(-1,0,1));
        homeFilmOverview.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)-> {
            int lines=Math.max(1,Math.min(5,(bottom-top-homeFilmOverview.getPaddingTop()-homeFilmOverview.getPaddingBottom())/Math.max(1,homeFilmOverview.getLineHeight())));
            if(homeFilmOverview.getMaxLines()!=lines) homeFilmOverview.setMaxLines(lines);
        });
        body.addView(homeDescription,new LinearLayout.LayoutParams(-1,0,1));
        final Api api=app.api; final Library db=app.library; final int g=generation; final boolean[] fresh=new boolean[HomeAdapter.GENRE_START+pinned.size()];
        app.cacheIo.execute(()-> {
            try { String stored=db.state("homeRecommendations"); if(!stored.isEmpty()) { List<JSONObject> cachedRecommendations=HomeRecommendations.select(new JSONArray(stored)); runOnUiThread(()-> { if(g==generation&&!fresh[2]) adapter.recommendations(cachedRecommendations); }); } } catch(Exception ignored) { }
            for(int i=0;i<fresh.length;i++) {
                if(HomeAdapter.recommendation(i)) continue;
                int row=i; List<JSONObject> cached=new ArrayList<>(); String stored=db.state(row<2?"home-"+row:"homeGenre-"+pinned.get(row-HomeAdapter.GENRE_START));
                try { if(!stored.isEmpty()) cached=list(new JSONArray(stored)); } catch(JSONException ignored) {}
                if(cached.isEmpty()&&row==0) cached=db.query("position>0 AND played=0",null,"stamp DESC",18,0);
                if(cached.isEmpty()&&row>=HomeAdapter.GENRE_START) cached=db.randomGenreMovies(pinned.get(row-HomeAdapter.GENRE_START),18);
                List<JSONObject> data=cached; runOnUiThread(()-> { if(g==generation&&!fresh[row]) adapter.update(row,data); });
            }
        });
        final long[] lastRefresh={0},lastGenreRefresh={0},lastRecommendationRefresh={0};
        homeReload=()-> {
            if(SystemClock.elapsedRealtime()-lastRefresh[0]<1500&&lastRefresh[0]!=0) return; lastRefresh[0]=SystemClock.elapsedRealtime();
            app.io.execute(()-> {
                for(int i=0;i<2;i++) { int row=i; try {
                    JSONObject response;
                    if(row==0) response=api.get("/Users/"+api.account.user+"/Items/Resume","MediaTypes","Video","Limit",18,"Fields","Overview,ParentId,PrimaryImageAspectRatio");
                    else response=api.get("/Shows/NextUp","UserId",api.account.user,"Limit",18,"Fields","Overview,ParentId,PrimaryImageAspectRatio","EnableResumable",false);
                    JSONArray a=Json.array(response,"Items"); db.put(a,System.currentTimeMillis()); db.state("home-"+row,a.toString()); List<JSONObject> data=list(a);
                    runOnUiThread(()-> { if(g==generation&&!isFinishing()) { fresh[row]=true; adapter.update(row,data); } });
                } catch(Exception ignored) { /* Cached shelves remain usable offline. */ } }
                app.channels.publish();
            });
            if(lastRecommendationRefresh[0]==0||SystemClock.elapsedRealtime()-lastRecommendationRefresh[0]>120000) {
                lastRecommendationRefresh[0]=SystemClock.elapsedRealtime();
                app.io.execute(()-> { if(g!=generation) return; try {
                    List<JSONObject> recommendations=HomeRecommendations.load(api);
                    for(JSONObject bucket:recommendations) db.put(Json.array(bucket,"Items"),System.currentTimeMillis());
                    db.state("homeRecommendations",new JSONArray(recommendations).toString());
                    runOnUiThread(()-> { if(g==generation&&!isFinishing()) { fresh[2]=true; adapter.recommendations(recommendations); app.channels.publish(); } });
                } catch(Exception ignored) { /* Account-scoped cached recommendations remain visible offline. */ } });
            }
            if(lastGenreRefresh[0]==0||SystemClock.elapsedRealtime()-lastGenreRefresh[0]>120000) {
                lastGenreRefresh[0]=SystemClock.elapsedRealtime();
                for(int i=0;i<pinned.size();i++) { final int row=HomeAdapter.GENRE_START+i; final String genre=pinned.get(i);
                    app.io.execute(()-> { if(g!=generation) return; try {
                        JSONArray items=Json.array(api.get("/Users/"+api.account.user+"/Items","Recursive",true,"IncludeItemTypes","Movie","Genres",genre,"SortBy","Random","Limit",18,"Fields","Overview,Genres,ProviderIds,ParentId,PrimaryImageAspectRatio"),"Items");
                        db.put(items,System.currentTimeMillis()); db.state("homeGenre-"+genre,items.toString()); List<JSONObject> data=list(items);
                        runOnUiThread(()-> { if(g==generation) { fresh[row]=true; adapter.update(row,data); } });
                    } catch(Exception ignored) { /* Preserve the cached random selection. */ } });
                }
            }
        }; adapter.requestRow(0); homeReload.run();
    }
    private void setHomeFocusMode(int row) {
        if(grid==null||homeDescription==null||screen==null||!screen.kind.equals("home")) return;
        if(focusedHomeRow==row) return;
        boolean genre=row>=2&&app.settings.getBoolean("singleShelf",true); boolean changed=(focusedHomeRow>=2)!=genre; focusedHomeRow=row;
        ((HomeAdapter)grid.getAdapter()).setFocusedShelf(row);
        LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)grid.getLayoutParams();
        size.height=genre?Ui.dp(this,244):0; size.weight=genre?0:1; grid.setLayoutParams(size);
        homeDescription.setVisibility(genre?View.VISIBLE:View.GONE);
        grid.setClipToPadding(false);
        if(genre) { Motion.enter(homeDescription,true); ((LinearLayoutManager)grid.getLayoutManager()).scrollToPositionWithOffset(row,0); }
        else if(changed&&detailBackdrop!=null) { focusedHomeId=""; detailBackdrop.setTag(null); detailBackdrop.setImageDrawable(null); }
    }
    private void homeFocus(JSONObject film,int row) {
        if(row<0||screen==null||!screen.kind.equals("home")) return;
        boolean sameFilm=row==focusedHomeRow&&film!=null&&film.optString("Id").equals(focusedHomeId);
        setHomeFocusMode(row); if(sameFilm) return;
        if(homeBackdropUpdate!=null) handler.removeCallbacks(homeBackdropUpdate);
        if(row<2) return;
        focusedHomeId=film==null?"":film.optString("Id");
        if(film==null) { if(homeRatings!=null) homeRatings.clear(); homeFilmTitle.setText(I18n.text("Keine Filme in dieser Reihe")); homeFilmMeta.setText(""); homeFilmOverview.setText(""); if(detailBackdrop!=null) detailBackdrop.setImageDrawable(null); return; }
        renderHomeFilm(film); if(ratingFocusUpdate!=null) handler.removeCallbacks(ratingFocusUpdate); final RatingsRow badges=homeRatings; final int ratingGeneration=generation;
        badges.clear(); ratingFocusUpdate=()-> { if(ratingGeneration==generation&&film.optString("Id").equals(focusedHomeId)) badges.load(app,film); }; handler.postDelayed(ratingFocusUpdate,250);
        final int g=generation; final String id=focusedHomeId; final Images images=app.images;
        homeBackdropUpdate=()-> { if(g==generation&&id.equals(focusedHomeId)&&focusedHomeRow>=2&&detailBackdrop!=null) images.backdrop(detailBackdrop,film); };
        handler.postDelayed(homeBackdropUpdate,180);
    }
    private void renderHomeFilm(JSONObject film) {
        homeFilmTitle.setText(film.optString("Name"));
        List<String> facts=new ArrayList<>(); if(film.optInt("ProductionYear")>0) facts.add(""+film.optInt("ProductionYear"));
        if(film.optLong("RunTimeTicks")>0) facts.add(film.optLong("RunTimeTicks")/600000000+" min");
        if(!film.optString("OfficialRating").isEmpty()) facts.add(film.optString("OfficialRating")); String genreText=Json.genres(film); if(!genreText.isEmpty()) facts.add(genreText); homeFilmMeta.setText(String.join("  ·  ",facts));
        homeFilmOverview.setText(android.text.Html.fromHtml(film.optString("Overview",I18n.text("Keine Beschreibung vorhanden")),android.text.Html.FROM_HTML_MODE_LEGACY));
    }
    private void recommendationItems() {
        MediaAdapter adapter=cards(false); adapter.set(list(Json.array(screen.item,"Items"))); restoreFocus();
    }
    private void special(String title,String kind) {
        Screen s=new Screen("search",title,kind,null,null); show(s,true);
    }
    private void browse() {
        final Screen s=screen;
        if("Series".equals(s.type)) { LinearLayout tabs=Ui.row(this); Ui.addButton(tabs,Ui.button(this,I18n.text("Alle Serien"),()->{})); Ui.addButton(tabs,Ui.button(this,I18n.text("Begonnene Folgen"),()->show(new Screen("seriesResume",I18n.text("Begonnene Folgen"),null,s.parent,null),true))); body.addView(tabs); }
        TextView note=note(I18n.text("Wird geladen …")); MediaAdapter adapter=cards(false); pager();
        final Api api=app.api; final Library db=app.library;
        async(adapter,()->db.browse(s.type,s.parent,null,s.page*PAGE,PAGE),null);
        async(adapter,()-> { JSONArray a=Json.array(api.items(s.page*PAGE,PAGE,s.type,s.parent,null),"Items"); long stamp=System.currentTimeMillis(); db.put(a,stamp); if(s.parent!=null) db.link(s.parent,a,stamp); return list(a); },note);
    }
    private void seriesResume() {
        final Screen s=screen; final Api api=app.api; final Library db=app.library;
        TextView status=note(I18n.text("Wird geladen …")); MediaAdapter adapter=cards(false); pager();
        String key="seriesResume-"+(s.parent==null?"all":s.parent)+"-"+s.page;
        async(adapter,()-> { String cached=db.state(key); return cached.isEmpty()?new ArrayList<>():list(new JSONArray(cached)); },null);
        async(adapter,()-> { JSONArray items=Json.array(api.get("/Users/"+api.account.user+"/Items","Recursive",true,"ParentId",s.parent,"IncludeItemTypes","Episode","Filters","IsResumable","SortBy","DatePlayed","SortOrder","Descending","StartIndex",s.page*PAGE,"Limit",PAGE,"Fields","Overview,MediaSources,MediaStreams,ParentId,PrimaryImageAspectRatio"),"Items"); db.put(items,System.currentTimeMillis()); db.state(key,items.toString()); return list(items); },status);
    }
    private void genres() {
        TextView note=note(I18n.text("Genres werden geladen …")); grid=new RecyclerView(this); grid.setLayoutManager(new GridLayoutManager(this,3)); grid.setItemAnimator(null);
        GenreAdapter adapter=new GenreAdapter(genre->show(new Screen("genre",genre.optString("Name"),"Movie,Series",genre.optString("Name"),genre),true)); grid.setAdapter(adapter); body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        final Api api=app.api; final Library db=app.library; final int g=generation; final boolean[] fresh={false};
        app.io.execute(()-> {
            List<JSONObject> saved=db.genres(); try { String state=db.state("genres"); if(!state.isEmpty()) saved=list(new JSONArray(state)); } catch(JSONException ignored) {}
            List<JSONObject> cached=saved; runOnUiThread(()-> { if(g==generation&&!fresh[0]) { adapter.set(cached); if(!cached.isEmpty()) note.setText(""); restoreFocus(); } });
            try { List<JSONObject> all=new ArrayList<>(); int offset=0;
                while(true) { JSONObject response=api.get("/Genres","UserId",api.account.user,"IncludeItemTypes","Movie,Series","StartIndex",offset,"Limit",200,"SortBy","SortName","SortOrder","Ascending","EnableImages",false); JSONArray page=Json.array(response,"Items"); all.addAll(list(page)); offset+=page.length(); if(page.length()==0||offset>=response.optInt("TotalRecordCount",offset)) break; }
                db.state("genres",new JSONArray(all).toString()); runOnUiThread(()-> { if(g==generation) { fresh[0]=true; adapter.set(all); note.setText(all.isEmpty()?I18n.text("Keine Genres gefunden"):""); restoreFocus(); } });
            } catch(Exception e) { runOnUiThread(()-> { if(g==generation) note.setText(adapter.items.isEmpty()?I18n.text("Keine gespeicherten Genres · Server nicht erreichbar"):I18n.text("Gespeicherte Genres")); }); }
        });
    }
    private void genreItems() {
        final Screen s=screen;
        TextView note=note(I18n.text("Titel werden geladen …")); MediaAdapter adapter=cards(false); pager(); final Api api=app.api; final Library db=app.library;
        async(adapter,()->db.genreItems(s.parent,s.page*PAGE,PAGE),null);
        async(adapter,()-> { JSONArray items=Json.array(api.get("/Users/"+api.account.user+"/Items","Recursive",true,"IncludeItemTypes","Movie,Series","Genres",s.parent,"StartIndex",s.page*PAGE,"Limit",PAGE,"SortBy","SortName","SortOrder","Ascending","Fields","Overview,Genres,PrimaryImageAspectRatio"),"Items"); db.put(items,System.currentTimeMillis()); return list(items); },note);
    }
    private void chooseChannelLibraries(String type) {
        if(views.isEmpty()) { toast(I18n.text("Bibliotheken werden geladen. Bitte gleich erneut öffnen.")); loadViews(); return; }
        List<JSONObject> available=new ArrayList<>(); for(JSONObject view:views) if(!view.optString("CollectionType").equals("livetv")) available.add(view);
        List<String> saved=ChannelLibraries.selection(app.settings,app.account,type),chosen=new ArrayList<>();
        String[] labels=new String[available.size()]; boolean[] checked=new boolean[labels.length];
        for(int i=0;i<labels.length;i++) { JSONObject view=available.get(i); labels[i]=view.optString("Name"); checked[i]=saved==null||saved.contains(view.optString("Id")); if(checked[i]) chosen.add(view.optString("Id")); }
        Account account=app.account;
        new AlertDialog.Builder(this).setTitle((type.equals("Movie")?I18n.text("Film"):I18n.text("Serien"))+I18n.text("-Zeile · Bibliotheken"))
            .setMultiChoiceItems(labels,checked,(dialog,index,on)-> { String id=available.get(index).optString("Id"); if(on&&!chosen.contains(id)) chosen.add(id); else if(!on) chosen.remove(id); })
            .setPositiveButton(I18n.text("Speichern"),(dialog,which)-> { app.settings.edit().putString(ChannelLibraries.key(account,type),new JSONArray(chosen).toString()).apply(); app.channels.publish(); app.channels.refresh(null); })
            .setNeutralButton(I18n.text("Alle Bibliotheken"),(dialog,which)-> { app.settings.edit().remove(ChannelLibraries.key(account,type)).apply(); app.channels.publish(); app.channels.refresh(null); })
            .setNegativeButton(I18n.text("Abbrechen"),null).show();
    }
    private void randomMovie(String parent) {
        final Api api=app.api; final Library db=app.library; final int g=generation;
        List<String> names=new ArrayList<>(); if(grid!=null&&grid.getAdapter() instanceof MediaAdapter) for(JSONObject film:((MediaAdapter)grid.getAdapter()).items) if("Movie".equals(film.optString("Type"))) names.add(film.optString("Name"));
        final boolean[] freshNames={false}; MovieSlot slot=new MovieSlot(this,names); AlertDialog roulette=new AlertDialog.Builder(this).setView(slot).setNegativeButton(I18n.text("Abbrechen"),null).create();
        roulette.setOnDismissListener(dialog->slot.stop()); roulette.show(); if(roulette.getWindow()!=null) roulette.getWindow().setLayout(Ui.dp(this,500),-2);
        app.cacheIo.execute(()-> { List<JSONObject> cached=parent==null?db.query("type='Movie'",null,"RANDOM()",80,0):db.browse("Movie",parent,null,0,80); List<String> titles=new ArrayList<>(); for(JSONObject film:cached) titles.add(film.optString("Name")); runOnUiThread(()-> { if(g==generation&&roulette.isShowing()&&!freshNames[0]&&!titles.isEmpty()) slot.setNames(titles); }); });
        app.io.execute(()-> {
            JSONObject chosen=null; List<String> titles=new ArrayList<>();
            try { JSONArray candidates=Json.array(api.get("/Users/"+api.account.user+"/Items","Recursive",true,"IncludeItemTypes","Movie","ParentId",parent,"SortBy","Random","Limit",80,"Fields","Overview,Genres,PrimaryImageAspectRatio"),"Items"); List<JSONObject> films=new ArrayList<>(); for(JSONObject candidate:list(candidates)) if("Movie".equals(candidate.optString("Type"))) { films.add(candidate); titles.add(candidate.optString("Name")); } if(!films.isEmpty()) { chosen=films.get(new Random().nextInt(films.size())); db.put(chosen); } }
            catch(Exception e) { chosen=db.randomMovie(parent); }
            JSONObject film=chosen; List<String> slotTitles=titles; runOnUiThread(()-> {
                if(g!=generation||isFinishing()||!roulette.isShowing()) { roulette.dismiss(); return; }
                if(film==null) { roulette.dismiss(); toast(I18n.text("Keine Filme in dieser Bibliothek gefunden")); }
                else { freshNames[0]=true; if(!slotTitles.isEmpty()) slot.setNames(slotTitles); else slot.setNames(Collections.singletonList(film.optString("Name"))); slot.finish(film.optString("Name"),()-> { if(g==generation&&!isFinishing()&&roulette.isShowing()) { roulette.dismiss(); open(film); } }); }
            });
        });
    }
    private void pager() {
        LinearLayout row=Ui.row(this); Button prev=Ui.button(this,I18n.text("← Vorherige Seite"),()-> { if(screen.page>0) { screen.page--; screen.scroll=null; generation++; shell(screen.title); renderCurrent(); } }); prev.setEnabled(screen.page>0);
        Ui.addButton(row,prev); row.addView(Ui.text(this,I18n.text("Seite ")+(screen.page+1)+I18n.text("  ·  60 Einträge  "),13,Ui.MUTED));
        Ui.addButton(row,Ui.button(this,I18n.text("Nächste Seite →"),()-> { screen.page++; screen.scroll=null; generation++; shell(screen.title); renderCurrent(); })); body.addView(row);
    }
    private void renderCurrent() { if(screen.kind.equals("seriesResume")) seriesResume(); else if(screen.kind.equals("genre")) genreItems(); else if(screen.kind.equals("genres")) genres(); else if(screen.kind.equals("episodes")) episodes(); else if(screen.kind.equals("search")) results(); else browse(); }
    private void open(JSONObject item) {
        String type=item.optString("Type");
        if(screen!=null&&screen.kind.equals("seriesResume")&&"Episode".equals(type)) { play(item,Json.position(item)); return; }
        if(type.equals("Series")) show(new Screen("seasons",item.optString("Name"),null,item.optString("Id"),item),true);
        else if(type.equals("BoxSet")) show(new Screen("browse",item.optString("Name"),"Movie,Series",item.optString("Id"),item),true);
        else if(type.equals("Season")) show(new Screen("episodes",item.optString("Name"),"Episode",item.optString("Id"),item),true);
        else if(type.equals("TvChannel")) play(item,0);
        else show(new Screen("detail",Json.label(item),null,null,item),true);
    }
    private void seasons(JSONObject series) {
        LinearLayout controls=Ui.row(this);
        Ui.addButton(controls,Ui.button(this,I18n.text("Seriendetails"),()->show(new Screen("detail",series.optString("Name"),null,null,series),true)));
        TextView count=Ui.text(this,I18n.text("Staffeln"),12,Ui.MUTED); controls.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        EditText number=Ui.input(this,"Staffel-Nr.",false); number.setTextSize(13); number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        controls.addView(number,new LinearLayout.LayoutParams(Ui.dp(this,100),Ui.dp(this,36)));
        body.addView(controls); Ui.space(body,8);
        MediaAdapter adapter=cards(false); List<JSONObject> data=adapter.items;
        GridLayoutManager layout=(GridLayoutManager)grid.getLayoutManager(); RecyclerView seasons=grid;
        Ui.addButton(controls,Ui.button(this,I18n.text("Zur Staffel"),()-> { int target; try { target=Integer.parseInt(number.getText().toString()); } catch(Exception e) { return; }
            for(int i=0;i<data.size();i++) if(data.get(i).optInt("IndexNumber")==target) { int pos=i; layout.scrollToPositionWithOffset(pos,0); seasons.post(()-> { View v=layout.findViewByPosition(pos); if(v!=null) v.requestFocus(); }); return; }
            toast(I18n.text("Staffel nicht gefunden"));
        }));
        Ui.editorAction(number,()->controls.getChildAt(controls.getChildCount()-1).performClick());
        final int g=generation; final Api api=app.api; final Library db=app.library; final String id=series.optString("Id"); final boolean[] fresh={false};
        app.io.execute(()-> {
            List<JSONObject> cached=db.query("type='Season' AND (parent=? OR series=?)",new String[]{id,id},"episode",500,0);
            runOnUiThread(()-> { if(g==generation&&!fresh[0]) { adapter.set(cached); count.setText(cached.size()+I18n.text(" Staffeln")); restoreFocus(); } });
            try { JSONArray a=Json.array(api.get("/Shows/"+id+"/Seasons","UserId",api.account.user,"Fields","Overview,ParentId,RecursiveItemCount,PrimaryImageAspectRatio"),"Items");
                List<JSONObject> all=list(a); all.sort(Comparator.comparingInt(j->j.optInt("IndexNumber"))); db.put(a,System.currentTimeMillis());
                runOnUiThread(()-> { if(g==generation) { fresh[0]=true; adapter.set(all); count.setText(all.size()+I18n.text(" Staffeln")); restoreFocus(); } });
            } catch(Exception e) { runOnUiThread(()-> { if(g==generation&&data.isEmpty()) toast(I18n.text("Staffeln konnten nicht geladen werden")); }); }
        });
    }
    private void episodes() {
        final Screen s=screen; TextView n=note(I18n.text("Folgen werden geladen …")); MediaAdapter adapter=cards(true); pager();
        final Api api=app.api; final Library db=app.library;
        async(adapter,()->db.browse("Episode",s.parent,null,s.page*PAGE,PAGE),null);
        async(adapter,()-> { String series=s.item.optString("SeriesId",s.item.optString("ParentId"));
            JSONArray a=Json.array(api.get("/Shows/"+series+"/Episodes","UserId",api.account.user,"SeasonId",s.parent,"StartIndex",s.page*PAGE,"Limit",PAGE,"Fields","Overview,MediaSources,MediaStreams,ParentId,PrimaryImageAspectRatio"),"Items");
            db.put(a,System.currentTimeMillis()); return list(a); },n);
    }
    private void detail(JSONObject original) {
        ScrollView page=new ScrollView(this); page.setFillViewport(true); page.setVerticalScrollBarEnabled(false);
        LinearLayout detail=Ui.column(this); detail.setPadding(0,Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,16)); page.addView(detail); body.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout hero=Ui.row(this); hero.setGravity(Gravity.TOP); detail.addView(hero);
        ArtworkView image=new ArtworkView(this); image.ratio(Artwork.ratio(original,false)); image.crop(Artwork.crop(original,false));
        hero.addView(image,new LinearLayout.LayoutParams(Ui.dp(this,Artwork.ratio(original,false)>1?224:126),-2));
        LinearLayout content=Ui.column(this); content.setPadding(Ui.dp(this,22),0,0,0); hero.addView(content,new LinearLayout.LayoutParams(0,-2,1));
        TextView eyebrow=Ui.text(this,"",11,Ui.MUTED); eyebrow.setLetterSpacing(.12f); content.addView(eyebrow); Ui.space(content,5);
        TextView title=Ui.title(this,"",27); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); content.addView(title); Ui.space(content,8);
        TextView meta=Ui.text(this,"",13,Ui.MUTED); content.addView(meta); Ui.space(content,8);
        TextView genres=Ui.text(this,"",12,Ui.MUTED); genres.setSingleLine(true); genres.setEllipsize(android.text.TextUtils.TruncateAt.END); content.addView(genres); Ui.space(content,8);
        RatingsRow ratings=new RatingsRow(this); content.addView(ratings); Ui.space(content,12);
        Ui.space(detail,16);
        HorizontalScrollView actionScroll=new HorizontalScrollView(this); actionScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout playActions=Ui.row(this); actionScroll.addView(playActions); detail.addView(actionScroll,new LinearLayout.LayoutParams(-1,-2));
        final AudioTracks.Choice[] selectedAudio={null};
        Ui.space(detail,24); View divider=new View(this); divider.setBackgroundColor(0xff28282c); detail.addView(divider,new LinearLayout.LayoutParams(-1,Ui.dp(this,1))); Ui.space(detail,18);
        detail.addView(Ui.text(this,I18n.text("ÜBER DIESEN TITEL"),11,Ui.MUTED)); Ui.space(detail,8);
        TextView overview=Ui.text(this,"",14,0xffccccd1); overview.setLineSpacing(Ui.dp(this,3),1); detail.addView(overview);
        final JSONObject[] item={original}; final Runnable[] redraw=new Runnable[1];
        redraw[0]=()-> {
            JSONObject j=item[0]; ratings.load(app,j); boolean series="Series".equals(j.optString("Type")),episode="Episode".equals(j.optString("Type"));
            eyebrow.setText(series?I18n.text("SERIE"):episode?j.optString("SeriesName",I18n.text("FOLGE")).toUpperCase(java.util.Locale.GERMAN):I18n.text("FILM")); title.setText(j.optString("Name"));
            java.util.List<String> facts=new ArrayList<>(); if(episode) facts.add(I18n.text("Staffel ")+j.optInt("ParentIndexNumber")+I18n.text(" · Folge ")+j.optInt("IndexNumber"));
            if(j.optInt("ProductionYear")>0) facts.add(""+j.optInt("ProductionYear")); if(j.optLong("RunTimeTicks")>0) facts.add(j.optLong("RunTimeTicks")/600000000+" min"); if(!Json.str(j,"OfficialRating").isEmpty()) facts.add(Json.str(j,"OfficialRating"));
            meta.setText(String.join("  ·  ",facts)); String genreText=Json.genres(j); genres.setText(genreText); genres.setVisibility(genreText.isEmpty()?View.GONE:View.VISIBLE);
            if(detailBackdrop!=null) app.images.backdrop(detailBackdrop,j);
            overview.setText(j.optString("Overview",I18n.text("Keine Beschreibung vorhanden"))); image.ratio(Artwork.ratio(j,false)); image.crop(Artwork.crop(j,false)); image.getLayoutParams().width=Ui.dp(this,Artwork.ratio(j,false)>1?224:126); app.images.show(image,j);
            int focusPlay=-1; for(int i=0;i<playActions.getChildCount();i++) if(playActions.getChildAt(i).hasFocus()) focusPlay=i;
            playActions.removeAllViews();
            if(series) Ui.addButton(playActions,Ui.primary(this,I18n.text("Staffeln ansehen"),()->show(new Screen("seasons",j.optString("Name"),null,j.optString("Id"),j),true)));
            else if(Json.position(j)>0) { Ui.addButton(playActions,Ui.primary(this,I18n.text("▶ Fortsetzen"),()->play(j,Json.position(j),selectedAudio[0]))); Ui.addButton(playActions,Ui.button(this,I18n.text("Von vorn"),()->play(j,0,selectedAudio[0]))); }
            else Ui.addButton(playActions,Ui.primary(this,I18n.text("▶ Abspielen"),()->play(j,0,selectedAudio[0])));
            List<JSONObject> trailers=Trailers.youtube(j);
            if(!trailers.isEmpty()) Ui.addButton(playActions,Ui.button(this,"▶ Trailer",()->showTrailers(j,trailers)));
            if(!series&&!AudioTracks.embedded(j).isEmpty()) {
                Ui.addButton(playActions,Ui.button(this,I18n.text("Tonspur")+(selectedAudio[0]==null?"":" · "+selectedAudio[0].label),()-> {
                    List<AudioTracks.Choice> tracks=AudioTracks.embedded(item[0]);
                    String[] labels=new String[tracks.size()+1]; labels[0]=I18n.text("Automatisch");
                    for(int n=0;n<tracks.size();n++) labels[n+1]=tracks.get(n).label;
                    int checked=0; for(int n=0;n<tracks.size();n++) if(selectedAudio[0]!=null&&tracks.get(n).index==selectedAudio[0].index&&tracks.get(n).sourceId.equals(selectedAudio[0].sourceId)) checked=n+1;
                    new AlertDialog.Builder(this).setTitle(I18n.text("Tonspur")).setSingleChoiceItems(labels,checked,(dialog,which)-> { selectedAudio[0]=which==0?null:tracks.get(which-1); dialog.dismiss(); redraw[0].run(); }).setNegativeButton(I18n.text("Abbrechen"),null).show();
                }));
            }
            boolean favorite=Json.object(j,"UserData").optBoolean("IsFavorite");
            Ui.addButton(playActions,Ui.button(this,favorite?I18n.text("★ Gespeichert"):I18n.text("☆ Merken"),()->mutate(j,"/Users/"+app.account.user+"/FavoriteItems/"+j.optString("Id"),favorite?"DELETE":"POST",()->refreshItem(item,redraw[0]))));
            boolean played=Json.object(j,"UserData").optBoolean("Played");
            Ui.addButton(playActions,Ui.button(this,played?I18n.text("✓ Gesehen · ändern"):I18n.text("Als gesehen"),()->mutate(j,"/Users/"+app.account.user+"/PlayedItems/"+j.optString("Id"),played?"DELETE":"POST",()->refreshItem(item,redraw[0]))));
            for(int n=0;n<playActions.getChildCount();n++) { Button button=(Button)playActions.getChildAt(n); button.setSingleLine(true); button.setMaxWidth(Ui.dp(this,210)); button.setEllipsize(android.text.TextUtils.TruncateAt.END); button.getLayoutParams().height=Ui.dp(this,40); }
            if(focusPlay>=0) playActions.getChildAt(Math.min(focusPlay,playActions.getChildCount()-1)).requestFocus();
        };
        redraw[0].run(); refreshItem(item,redraw[0]); playActions.getChildAt(0).requestFocus();
    }
    private void showTrailers(JSONObject film,List<JSONObject> trailers) {
        java.util.function.Consumer<JSONObject> launch=trailer->startActivity(new Intent(this,TrailerActivity.class).putExtra("videoId",trailer.optString("Id")).putExtra("title",film.optString("Name")+" · Trailer"));
        if(trailers.size()==1) launch.accept(trailers.get(0));
        else { String[] labels=new String[trailers.size()]; for(int i=0;i<labels.length;i++) labels[i]=trailers.get(i).optString("Name"); choice(I18n.text("Trailer wählen"),labels,index->launch.accept(trailers.get(index))); }
    }
    private void refreshItem(JSONObject[] holder,Runnable render) {
        final int g=generation; final Api api=app.api; final Library db=app.library; String id=holder[0].optString("Id");
        app.io.execute(()-> { try { JSONObject j=api.item(id); db.put(j); runOnUiThread(()-> { if(g==generation) { holder[0]=j; render.run(); } }); } catch(Exception ignored) {} });
    }
    private void mutate(JSONObject item,String path,String method,Runnable after) {
        final Api api=app.api; int g=generation;
        app.io.execute(()-> { try { api.call(method,path,null); if(app.api==api&&path.contains("/PlayedItems/")) app.channels.refresh(null); runOnUiThread(()-> { if(g==generation) after.run(); }); }
            catch(Exception e) { runOnUiThread(()-> { if(g==generation) toast(e.getMessage()); }); } });
    }
    private List<JSONObject> watchLaterItems(int page,Api api,Library db) {
        List<JSONObject> saved=new WatchLater(this,api.account).items(),result=new ArrayList<>();
        for(int i=page*PAGE;i<Math.min(saved.size(),(page+1)*PAGE);i++) { JSONObject entry=saved.get(i),cached=db.item(entry.optString("Id")); result.add(cached==null?entry:cached); }
        return result;
    }
    private void itemActions(JSONObject item) {
        if(app.account==null||!Arrays.asList("Movie","Series","Season","Episode","Video").contains(item.optString("Type"))) return;
        final boolean season="Season".equals(item.optString("Type"));
        final Api api=app.api; final Library db=app.library; final int g=generation;
        final WatchLater later=new WatchLater(this,api.account); final View origin=root.findFocus();
        boolean played=Json.object(item,"UserData").optBoolean("Played"),favorite=Json.object(item,"UserData").optBoolean("IsFavorite"),saved=later.contains(item.optString("Id"));
        List<String> labels=new ArrayList<>(Arrays.asList(I18n.text("Fortsetzen"),I18n.text("Von vorn"),I18n.text(played?"Als ungesehen markieren":"Als gesehen markieren"),I18n.text(saved?"Aus Später ansehen entfernen":"Zu Später ansehen hinzufügen"),I18n.text(favorite?"Aus Favoriten entfernen":"Zu Favoriten hinzufügen")));
        String seriesId="Series".equals(item.optString("Type"))?item.optString("Id"):item.optString("SeriesId");
        if(!seriesId.isEmpty()) labels.add(I18n.text("Zur Serienseite"));
        if(season) labels=new ArrayList<>(Collections.singletonList(I18n.text(played?"Als ungesehen markieren":"Als gesehen markieren")));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(Json.label(item)).setItems(labels.toArray(new String[0]),(d,which)-> {
            if(app.api!=api||g!=generation) return;
            if(season) {
                app.io.execute(()-> { try {
                    api.call(played?"DELETE":"POST","/Users/"+api.account.user+"/PlayedItems/"+item.optString("Id"),null);
                    JSONObject updated=new JSONObject(item.toString()),user=Json.object(updated,"UserData"); user.put("Played",!played); user.put("PlaybackPositionTicks",0); updated.put("UserData",user); db.put(updated);
                    int offset=0; while(true) {
                        List<JSONObject> episodes=db.query("type='Episode' AND parent=?",new String[]{item.optString("Id")},"id",100,offset); if(episodes.isEmpty()) break;
                        for(JSONObject episode:episodes) { JSONObject data=Json.object(episode,"UserData"); data.put("Played",!played); data.put("PlaybackPositionTicks",0); episode.put("UserData",data); db.put(episode); } offset+=episodes.size();
                    }
                    if(app.api==api) app.channels.refresh(null);
                    runOnUiThread(()-> { if(app.api!=api||g!=generation) return; try { item.put("UserData",user); } catch(JSONException ignored) { } rebindActionCard(origin); Motion.feedback(origin); toast(I18n.text("Gespeichert")); });
                } catch(Exception e) { runOnUiThread(()-> { if(app.api==api&&g==generation) toast(I18n.text("Aktion fehlgeschlagen")); }); } });
                return;
            }
            if(which==0||which==1) { actionPlay(item,which==1,api,g); return; }
            if(which==3) { later.set(item,!saved); toast(I18n.text(saved?"Aus Später ansehen entfernt":"Zu Später ansehen hinzugefügt")); if(screen!=null&&"watchLater".equals(screen.type)) refreshActionCards(); else Motion.feedback(origin); return; }
            if(which==5) { actionSeries(seriesId,api,g); return; }
            String flag=which==2?"Played":"IsFavorite",category=which==2?"PlayedItems":"FavoriteItems"; boolean value=which==2?!played:!favorite;
            app.io.execute(()-> { try {
                api.call(value?"POST":"DELETE","/Users/"+api.account.user+"/"+category+"/"+item.optString("Id"),null);
                JSONObject updated=new JSONObject(item.toString()),user=Json.object(updated,"UserData"); user.put(flag,value); updated.put("UserData",user); db.put(updated);
                if(app.api==api&&which==2) app.channels.refresh(null);
                runOnUiThread(()-> { if(app.api!=api||g!=generation) return; try { item.put("UserData",user); } catch(JSONException ignored) { } toast(I18n.text("Gespeichert")); rebindActionCard(origin); Motion.feedback(origin); app.channels.publish(); if(screen!=null&&"favorites".equals(screen.type)) refreshActionCards(); });
            } catch(Exception e) { runOnUiThread(()-> { if(app.api==api&&g==generation) toast(I18n.text("Aktion fehlgeschlagen")); }); } });
        }).setNegativeButton(I18n.text("Abbrechen"),null).create();
        final boolean[] openingGesture={true};
        dialog.setOnKeyListener((d,key,event)-> {
            if(key!=KeyEvent.KEYCODE_DPAD_CENTER&&key!=KeyEvent.KEYCODE_ENTER&&key!=KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if(!openingGesture[0]) return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) { openingGesture[0]=false; return false; }
            if(event.getAction()==KeyEvent.ACTION_UP) openingGesture[0]=false;
            return true;
        });
        dialog.setOnDismissListener(d->{ if(origin!=null&&origin.isAttachedToWindow()&&app.api==api&&g==generation) origin.requestFocus(); });
        dialog.show(); dialog.getListView().requestFocus(); dialog.getListView().setSelection(0); Motion.enter(dialog.getListView(),true);
    }
    private void rebindActionCard(View origin) {
        if(origin==null||!(origin.getParent() instanceof RecyclerView)) return;
        RecyclerView list=(RecyclerView)origin.getParent(); int position=list.getChildAdapterPosition(origin);
        if(position!=RecyclerView.NO_POSITION&&list.getAdapter()!=null) list.getAdapter().notifyItemChanged(position);
    }
    private void refreshActionCards() {
        if(grid==null||!(grid.getAdapter() instanceof MediaAdapter)) return;
        final Screen s=screen; final MediaAdapter adapter=(MediaAdapter)grid.getAdapter(); final Api api=app.api; final Library db=app.library;
        adapter.fresh=false;
        async(adapter,()-> "watchLater".equals(s.type)?watchLaterItems(s.page,api,db):db.query("favorite=1",null,"name",PAGE,s.page*PAGE),null);
    }
    private void actionPlay(JSONObject item,boolean restart,Api api,int g) {
        if(!"Series".equals(item.optString("Type"))) { play(item,restart?0:Json.position(item)); return; }
        app.io.execute(()-> { try {
            JSONObject response=restart?api.get("/Shows/"+item.optString("Id")+"/Episodes","UserId",api.account.user,"Limit",1,"IsMissing",false,"Fields","Overview,MediaSources,MediaStreams,ParentId"):
                api.get("/Shows/NextUp","UserId",api.account.user,"SeriesId",item.optString("Id"),"EnableResumable",true,"Limit",1,"Fields","Overview,MediaSources,MediaStreams,ParentId");
            JSONObject episode=Json.array(response,"Items").optJSONObject(0);
            runOnUiThread(()-> { if(app.api!=api||g!=generation) return; if(episode==null) toast(I18n.text("Keine abspielbare Folge gefunden")); else play(episode,restart?0:Json.position(episode)); });
        } catch(Exception e) { runOnUiThread(()-> { if(app.api==api&&g==generation) toast(I18n.text("Aktion fehlgeschlagen")); }); } });
    }
    private void actionSeries(String id,Api api,int g) {
        final Library db=app.library; app.io.execute(()-> { try { JSONObject series=api.item(id); db.put(series); runOnUiThread(()-> { if(app.api==api&&g==generation) show(new Screen("detail",series.optString("Name"),null,id,series),true); }); }
            catch(Exception e) { runOnUiThread(()-> { if(app.api==api&&g==generation) toast(I18n.text("Aktion fehlgeschlagen")); }); } });
    }
    private void play(JSONObject item,long position) { play(item,position,null); }
    private void play(JSONObject item,long position,AudioTracks.Choice audio) {
        if(app.account==null) return;
        Intent intent=new Intent(this,PlayerActivity.class).putExtra("item",item.toString()).putExtra("position",position);
        if(audio!=null) intent.putExtra("audioStreamIndex",audio.index).putExtra("mediaSourceId",audio.sourceId);
        startActivity(intent);
    }
    private void live() {
        TextView n=note(""); MediaAdapter adapter=cards(true);
        final Api api=app.api; final Library db=app.library;
        async(adapter,()->db.browse("TvChannel",null,null,0,500),null);
        async(adapter,()-> { JSONArray a=Json.array(api.get("/LiveTv/Channels","UserId",api.account.user,"SortBy","SortName","Fields","ChannelInfo"),"Items"); db.put(a,System.currentTimeMillis()); return list(a); },n);
    }
    private void recordings() {
        TextView n=note(I18n.text("Aufnahmen werden geladen …")); MediaAdapter adapter=cards(true); final Api api=app.api;
        async(adapter,()->list(Json.array(api.get("/LiveTv/Recordings","UserId",api.account.user,"Fields","Overview,MediaSources,MediaStreams"),"Items")),n);
    }
    private void search() {
        EditText input=Ui.input(this,I18n.text("Titel suchen"),false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(I18n.text("Bibliothek durchsuchen")).setView(input).setPositiveButton(I18n.text("Suchen"),(d,w)->show(new Screen("search",input.getText().toString(),"term",null,null),true)).setNegativeButton(I18n.text("Abbrechen"),null).create(); Ui.editorAction(input,()->dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()); dialog.show(); input.requestFocus();
    }
    private void results() {
        final Screen s=screen; final Api api=app.api; final Library db=app.library;
        TextView n=note(I18n.text("Wird geladen …")); MediaAdapter adapter=cards(false); pager();
        async(adapter,()-> {
            if("resume".equals(s.type)) return db.query("position>0 AND played=0",null,"stamp DESC",PAGE,s.page*PAGE);
            if("watchLater".equals(s.type)) return watchLaterItems(s.page,api,db);
            if("favorites".equals(s.type)) return db.query("favorite=1",null,"name",PAGE,s.page*PAGE);
            return db.query("type IN ('Movie','Series','Episode') AND name LIKE ?",new String[]{"%"+s.title+"%"},"name",PAGE,s.page*PAGE);
        },null);
        async(adapter,()-> {
            JSONObject response;
            if(s.type.equals("watchLater")) return watchLaterItems(s.page,api,db);
            if(s.type.equals("resume")) response=api.get("/Users/"+api.account.user+"/Items/Resume","MediaTypes","Video","StartIndex",s.page*PAGE,"Limit",PAGE,"Fields","Overview,ParentId,PrimaryImageAspectRatio");
            else if(s.type.equals("next")) response=api.get("/Shows/NextUp","UserId",api.account.user,"StartIndex",s.page*PAGE,"Limit",PAGE,"Fields","Overview,ParentId,PrimaryImageAspectRatio");
            else if(s.type.equals("favorites")) response=api.get("/Users/"+api.account.user+"/Items","Recursive",true,"Filters","IsFavorite","IncludeItemTypes","Movie,Series,Episode","StartIndex",s.page*PAGE,"Limit",PAGE);
            else response=api.items(s.page*PAGE,PAGE,"Movie,Series,Episode",null,s.title);
            JSONArray a=Json.array(response,"Items"); db.put(a,System.currentTimeMillis()); return list(a);
        },n);
    }
    private void refreshOptions() { Deque<Screen> saved=new ArrayDeque<>(history); show(screen,false); history.addAll(saved); }
    private void settings() {
        ScrollView scroll=new ScrollView(this); LinearLayout content=Ui.column(this); scroll.addView(content); body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(Ui.button(this,I18n.text("Sprache")+": "+(I18n.language().equals("de")?"Deutsch":"English"),this::languageChoice)); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("Genre-Reihen"),()->show(new Screen("genreSettings",I18n.text("Genre-Reihen"),null,null,null),true))); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("Startseite und Darstellung"),()->show(new Screen("appearanceSettings",I18n.text("Startseite und Darstellung"),null,null,null),true))); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("Erweiterte Wiedergabe"),()->show(new Screen("playbackSettings",I18n.text("Erweiterte Wiedergabe"),null,null,null),true))); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("Bewertungen · Anbieter und API-Keys"),()->show(new Screen("ratings",I18n.text("Bewertungen"),null,null,null),true))); Ui.space(content,18);
        content.addView(Ui.text(this,I18n.text("Wiedergabe"),16,Ui.MUTED)); Ui.space(content,10);
        content.addView(Ui.button(this,I18n.text("Auflösung: ")+app.settings.getInt("height",720)+"p",()->choice(I18n.text("Maximale Auflösung"),new String[]{I18n.text("720p · HD, empfohlen"),"1080p · Full HD","2160p · 4K UHD"},i->{ app.settings.edit().putInt("height",new int[]{720,1080,2160}[i]).apply(); show(screen,false); })));
        Ui.space(content,8); content.addView(Ui.button(this,"Bitrate: "+app.settings.getInt("bitrate",4000000)/1000000+" Mbit/s",()->choice(I18n.text("Maximale Bitrate"),new String[]{"2 Mbit/s","4 Mbit/s","6 Mbit/s","8 Mbit/s","16 Mbit/s","25 Mbit/s","40 Mbit/s","60 Mbit/s"},i->{ app.settings.edit().putInt("bitrate",new int[]{2000000,4000000,6000000,8000000,16000000,25000000,40000000,60000000}[i]).apply(); show(screen,false); })));
        Ui.space(content,8); toggle(content,I18n.text("Audio auf Stereo (2.0) heruntermischen"),"stereo",true);
        Ui.space(content,8); toggle(content,I18n.text("Server-Transcoding bei Bedarf zulassen"),"serverFallback",false);
        Ui.space(content,8); int videoBudget=app.settings.getInt("videoCacheMb",500);
        content.addView(Ui.button(this,I18n.text("Videocache: ")+(videoBudget==0?I18n.text("Aus"):videoBudget+" MB"),this::videoCacheChoice));
        Ui.space(content,6); content.addView(Ui.text(this,I18n.text("RAM-Puffer · siehe erweiterte Wiedergabeeinstellungen"),12,Ui.MUTED));
        Ui.space(content,8); toggle(content,I18n.text("Nächste Folge automatisch"),"nextEpisode",true);
        Ui.space(content,8); toggle(content,I18n.text("Intros und Outros automatisch überspringen"),"autoSkip",true);
        Ui.space(content,8); toggle(content,I18n.text("Live TV beim Öffnen des Clients starten"),"liveOnLaunch",false);
        Ui.space(content,8); content.addView(Ui.button(this,"Live-TV-Aufnahmen",()->show(new Screen("recordings",I18n.text("Aufnahmen"),null,null,null),true)));
        Ui.space(content,8); toggle(content,I18n.text("UI-Animationen"),"animations",true);
        Ui.space(content,20); content.addView(Ui.text(this,I18n.text("Android-TV-Hauptmenü"),16,Ui.MUTED)); Ui.space(content,10);
        content.addView(Ui.button(this,I18n.text("Filme und Serien im TV-Hauptmenü: ")+(app.settings.getBoolean("homeChannels",true)?I18n.text("Ein"):I18n.text("Aus")),()-> {
            boolean enabled=!app.settings.getBoolean("homeChannels",true); app.settings.edit().putBoolean("homeChannels",enabled).apply();
            if(enabled) { app.channels.schedule(); app.channels.refresh(null); } else app.channels.cancelAndClear(); show(screen,false);
        })); Ui.space(content,8); content.addView(Ui.text(this,app.channels.status(),13,Ui.MUTED));
        Ui.space(content,8); content.addView(Ui.button(this,I18n.text("TV-Filmzeile · Bibliotheken wählen"),()->chooseChannelLibraries("Movie")));
        Ui.space(content,8); content.addView(Ui.button(this,I18n.text("TV-Serienzeile · Bibliotheken wählen"),()->chooseChannelLibraries("Series")));
        for(String[] channel:new String[][]{{"resume",I18n.text("Fortsetzen")},{"next",I18n.text("Als Nächstes")},{"movies",I18n.text("Filme")},{"series",I18n.text("Serien")}}) {
            Ui.space(content,8); content.addView(Ui.button(this,I18n.text("TV-Kanal aktivieren · ")+channel[1],()->app.channels.requestVisible(this,channel[0])));
        }
        for(int i=0;i<HomeRecommendations.ROWS;i++) {
            final String key="recommendation-"+i;
            Ui.space(content,8); content.addView(Ui.button(this,I18n.text("TV-Kanal aktivieren · Film-Empfehlungen ")+(i+1),()->app.channels.requestVisible(this,key)));
        }
        Ui.space(content,8); content.addView(Ui.text(this,I18n.text("Der erste Kanal kann automatisch erscheinen. Weitere Reihen aktivierst du hier oder unter ‚Kanäle anpassen‘ im TV-Hauptmenü.\nDie Anzeige hängt vom Launcher ab; Google TV kann eigene Regeln verwenden."),13,Ui.MUTED));
        Ui.space(content,20); content.addView(Ui.text(this,I18n.text("Bibliothekscache · Konto und Server getrennt"),16,Ui.MUTED)); Ui.space(content,10);
        TextView cacheStatus=Ui.text(this,app.syncStatus,14,Ui.MUTED); content.addView(cacheStatus); Ui.space(content,10);
        final Library db=app.library; final int g=generation;
        app.cacheIo.execute(()-> { try { String saved=db.state("cacheStatus"); runOnUiThread(()-> { if(g==generation&&!saved.isEmpty()) cacheStatus.setText(saved); }); } catch(Exception ignored) {} });
        content.addView(Ui.button(this,I18n.text("Bibliothek und Cover vollständig vorladen"),this::startCache)); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("Vorladen stoppen"),()-> { stopService(new Intent(this,SyncService.class)); toast(I18n.text("Cache wird gestoppt")); })); Ui.space(content,8);
        int budget=app.settings.getInt("cacheMb",1024);
        content.addView(Ui.button(this,I18n.text("Cover-Speicher: ")+(budget==0?I18n.text("gesamter freier Speicher"):budget+" MB"),()->choice(I18n.text("Speicherbudget · 64 MB bleiben frei"),new String[]{"256 MB","512 MB","1024 MB","2048 MB","4096 MB",I18n.text("Ohne feste Grenze")},i->{ app.settings.edit().putInt("cacheMb",new int[]{256,512,1024,2048,4096,0}[i]).apply(); show(screen,false); })));
        Ui.space(content,8); content.addView(Ui.button(this,I18n.text("Covercache löschen"),()-> { final Images images=app.images; app.io.execute(()-> { images.clear(); runOnUiThread(()->toast(I18n.text("Covercache gelöscht"))); }); }));
        Ui.space(content,20); content.addView(Ui.text(this,I18n.text("Angemeldet als ")+app.account.name+" · "+app.account.server,14,Ui.MUTED)); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("Abmelden / Konto wechseln"),()-> { app.logout(); history.clear(); login(""); }));
        Ui.space(content,18); content.addView(Ui.text(this,I18n.text("Lowfin 0.6.0 · Intro Skipper über Jellyfin MediaSegments (10.10+)\nAndere Segmenttypen erscheinen als Skip-Taste. Medien werden vom Server abgespielt.\nDer erste Bibliothekslauf kann länger dauern; vorhandene Daten sind sofort nutzbar."),13,Ui.MUTED));
    }
    private void languageChoice() {
        new AlertDialog.Builder(this).setTitle("Language / Sprache").setSingleChoiceItems(new String[]{"English","Deutsch"},I18n.language().equals("de")?1:0,(dialog,which)-> { I18n.select(which==0?"en":"de"); dialog.dismiss(); if(app.account!=null) restartRatings(); if(app.account==null) login(""); else { app.channels.publish(); show(new Screen("settings",I18n.text("Einstellungen"),null,null,null),false); } }).setNegativeButton(I18n.text("Abbrechen"),null).show();
    }
    private void ratingSettings() {
        ScrollView scroll=new ScrollView(this); LinearLayout content=Ui.column(this); scroll.addView(content); body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(Ui.title(this,I18n.text("API-Keys aus TXT-Datei"),18)); Ui.space(content,8);
        importStatus=Ui.text(this,app.keyImport.status(),13,Ui.MUTED); content.addView(importStatus); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("Download-Datei jetzt prüfen"),()->checkKeyFile(true,null))); Ui.space(content,8);
        content.addView(Ui.button(this,I18n.text("TXT-Datei auswählen"),this::selectKeyFile)); Ui.space(content,18);
        content.addView(Ui.title(this,I18n.text("Bewertungsanbieter"),18)); Ui.space(content,8);
        for(String provider:new String[]{"omdb","mdblist","tmdb"}) {
            String name=provider.equals("omdb")?"OMDb":provider.equals("mdblist")?"MDBList":"TMDB";
            Button button=Ui.button(this,name+I18n.text(" · API-Key bearbeiten"),()->ratingKeyDialog(provider,name));
            android.graphics.drawable.Drawable mark=getDrawable(provider.equals("tmdb")?R.drawable.rating_tmdb:provider.equals("omdb")?R.drawable.provider_omdb:R.drawable.provider_mdblist); RatingsRow.fitIcon(mark,this,26,18); button.setCompoundDrawables(mark,null,null,null); button.setCompoundDrawablePadding(Ui.dp(this,8));
            Ui.space(content,8); content.addView(button);
        }
        Ui.space(content,12); content.addView(Ui.text(this,I18n.text("OMDb liefert IMDb, Rotten Tomatoes und Metacritic. MDBList ergänzt weitere Quellen. TMDB liefert die eigene Nutzerbewertung. Fehlende Werte werden ausgeblendet."),13,Ui.MUTED));
        Ui.space(content,20); content.addView(Ui.title(this,I18n.text("Sichtbare Bewertungen"),18));
        for(int i=0;i<Ratings.IDS.length;i++) { String id=Ratings.IDS[i],name=I18n.text(Ratings.NAMES[i]); Ui.space(content,6);
            Button button=Ui.button(this,name+": "+(Ratings.enabled(app.settings,id)?I18n.text("Ein"):I18n.text("Aus")),()-> { app.settings.edit().putBoolean("rating-show-"+id,!Ratings.enabled(app.settings,id)).apply(); restartRatings(); show(screen,false); });
            int icon=RatingsRow.icon(id); if(icon!=0) { android.graphics.drawable.Drawable drawable=getDrawable(icon); RatingsRow.fitIcon(drawable,this,26,18); button.setCompoundDrawables(drawable,null,null,null); button.setCompoundDrawablePadding(Ui.dp(this,8)); } content.addView(button);
        }
        Ui.space(content,16); content.addView(Ui.text(this,app.ratings.status,12,Ui.MUTED));
        Ui.space(content,14); content.addView(Ui.text(this,I18n.text("This product uses the TMDB API but is not endorsed or certified by TMDB.\nOMDb · www.omdbapi.com · Daten: CC BY-NC 4.0\nMDBList · mdblist.com"),11,Ui.MUTED));
    }
    private void checkKeyFile(boolean explicit,String document) {
        if(app==null) return;
        if(importBusy) { if(document!=null) pendingKeyDocument=document; return; }
        if(document==null&&Build.VERSION.SDK_INT<29&&!app.keyImport.configured()&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if(explicit||!app.settings.getBoolean("keyStorageAsked",false)) { app.settings.edit().putBoolean("keyStorageAsked",true).apply(); requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE},KEY_STORAGE_REQUEST); }
            return;
        }
        importBusy=true;
        app.io.execute(()-> {
            RatingKeyImport.Result result=document==null?app.keyImport.check():app.keyImport.selected(document);
            runOnUiThread(()-> {
                importBusy=false; if(result.changed&&app.account!=null) restartRatings();
                if(pendingKeyDocument!=null&&!isFinishing()) { String pending=pendingKeyDocument; pendingKeyDocument=null; checkKeyFile(true,pending); }
                if(isFinishing()||!importActive) return;
                if(result.changed&&app.account!=null) {
                    if(screen!=null&&screen.kind.equals("ratings")) refreshOptions();
                    else if(screen!=null&&screen.kind.equals("detail")) reloadRatingRows(body,screen.item);
                    else if(ratingFocusUpdate!=null) ratingFocusUpdate.run();
                }
                String notice=app.keyImport.status(); if(importStatus!=null&&importStatus.isAttachedToWindow()) importStatus.setText(notice);
                boolean important=result.state==RatingKeyImport.State.CREATED||result.state==RatingKeyImport.State.IMPORTED||result.state==RatingKeyImport.State.INVALID||result.state==RatingKeyImport.State.DELETE_PENDING;
                if(explicit||important&&!notice.equals(lastImportNotice)) { toast(notice); lastImportNotice=notice; }
            });
        });
    }
    private void reloadRatingRows(View view,JSONObject item) {
        if(view instanceof RatingsRow) ((RatingsRow)view).load(app,item);
        else if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) reloadRatingRows(group.getChildAt(i),item); }
    }
    private void selectKeyFile() {
        if(app.keyImport.configured()) { toast(I18n.text("API-Keys sind bereits gespeichert. Automatischer TXT-Import ist deaktiviert.")); return; }
        try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),KEY_FILE_REQUEST); }
        catch(ActivityNotFoundException e) { toast(I18n.text("Kein Dateiauswahldialog verfügbar. Die Download-Datei verwenden.")); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=KEY_FILE_REQUEST||result!=RESULT_OK||data==null||data.getData()==null) return;
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { if(flags==(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else if((flags&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0) getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
            else if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0) getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch(SecurityException ignored) { }
        checkKeyFile(true,data.getData().toString());
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==KEY_STORAGE_REQUEST&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED) checkKeyFile(true,null);
    }
    private void restartRatings() { if(app.ratings!=null) app.ratings.close(); app.ratings=new Ratings(this,app.library,app.api); }
    private void ratingKeyDialog(String provider,String name) {
        LinearLayout form=Ui.column(this); form.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),0);
        EditText input=Ui.input(this,name.equals("TMDB")?I18n.text("API-Key oder Read Access Token"):"API-Key",true); input.setSingleLine(true); form.addView(input);
        TextView hint=Ui.text(this,I18n.text("API-Key wird verschlüsselt auf diesem Gerät gespeichert. Leeres Feld entfernt den Key."),12,Ui.MUTED); form.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(name+I18n.text(" verbinden")).setView(form).setPositiveButton(I18n.text("Speichern"),null).setNegativeButton(I18n.text("Abbrechen"),null).create();
        final RatingKeys keys=new RatingKeys(this); app.io.execute(()-> { String saved=keys.get(provider); runOnUiThread(()-> { if(dialog.isShowing()&&input.getText().length()==0) input.setText(saved); }); });
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{ String value=input.getText().toString(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            app.io.execute(()-> { try { keys.put(provider,value); runOnUiThread(()-> { dialog.dismiss(); restartRatings(); if(screen.kind.equals("ratings")) show(screen,false); toast(I18n.text("API-Key gespeichert")); }); }
                catch(Exception e) { runOnUiThread(()-> { hint.setText(I18n.text("API-Key konnte nicht sicher gespeichert werden.")); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); }); } });
        })); Ui.editorAction(input,()-> { Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE); if(save!=null&&save.isEnabled()) save.performClick(); }); dialog.show();
    }
    private void videoCacheChoice() {
        int[] values={0,128,256,500,1024,2048};
        choice(I18n.text("Videocache auf dem Gerätespeicher"),new String[]{I18n.text("Aus"),"128 MB","256 MB","500 MB","1024 MB","2048 MB",I18n.text("Eigener Wert …")},index->{
            if(index<values.length) { app.settings.edit().putInt("videoCacheMb",values[index]).apply(); show(screen,false); return; }
            EditText input=Ui.input(this,I18n.text("Größe in MB"),false); input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); input.setText(""+app.settings.getInt("videoCacheMb",500));
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(I18n.text("Videocache in MB")).setView(input).setPositiveButton(I18n.text("Speichern"),null).setNegativeButton(I18n.text("Abbrechen"),null).create();
            dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                try { int size=Integer.parseInt(input.getText().toString()); if(size<16||size>16384) { input.setError(I18n.text("16 bis 16384 MB eingeben")); return; }
                    app.settings.edit().putInt("videoCacheMb",size).apply(); dialog.dismiss(); show(screen,false);
                } catch(NumberFormatException e) { input.setError(I18n.text("Eine Größe in MB eingeben")); }
            })); Ui.editorAction(input,()-> { Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE); if(save!=null&&save.isEnabled()) save.performClick(); }); dialog.show();
        });
    }
    private void toggle(LinearLayout parent,String label,String key,boolean defaultValue) {
        boolean enabled=app.settings.getBoolean(key,defaultValue);
        parent.addView(Ui.button(this,label+": "+(enabled?I18n.text("Ein"):I18n.text("Aus")),()-> { app.settings.edit().putBoolean(key,!enabled).apply(); show(screen,false); }));
    }
    private void choice(String title,String[] labels,java.util.function.IntConsumer action) { new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,w)->action.accept(w)).setNegativeButton(I18n.text("Abbrechen"),null).show(); }
    private void startCache() { if(app.account!=null) { startForegroundService(new Intent(this,SyncService.class)); toast(I18n.text("Bibliothek wird im Hintergrund vorgeladen")); } }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if(app.account!=null&&hasHomeLink(intent)) handleHomeLink(intent); }
    private boolean hasHomeLink(Intent intent) { return intent!=null&&(intent.hasExtra(HomeChannels.EXTRA_ITEM)||(intent.getData()!=null&&"lowfin".equals(intent.getData().getScheme()))); }
    private void handleHomeLink(Intent intent) {
        String id=intent.getStringExtra(HomeChannels.EXTRA_ITEM),scope=intent.getStringExtra(HomeChannels.EXTRA_SCOPE),mode=intent.getStringExtra(HomeChannels.EXTRA_MODE);
        android.net.Uri data=intent.getData();
        if(data!=null&&"lowfin".equals(data.getScheme())&&"item".equals(data.getHost())) { id=data.getLastPathSegment(); scope=data.getQueryParameter("scope"); mode=data.getQueryParameter("mode"); }
        if(id==null||!id.matches("[a-zA-Z0-9_-]{1,80}")||scope==null||!scope.equals(app.account.scope())) { toast(I18n.text("Diese TV-Karte gehört zu einem anderen oder abgemeldeten Konto.")); return; }
        final String itemId=id,action=mode==null?"open":mode; final Api api=app.api; final Library db=app.library; final int g=generation;
        // Consume once: a normal Activity recreation must not start it again.
        setIntent(new Intent(this,MainActivity.class));
        app.io.execute(()-> { try { JSONObject j=api.item(itemId); db.put(j);
            runOnUiThread(()-> { if(g!=generation||app.api!=api||isFinishing()) return; if(action.equals("play")) play(j,0); else if(action.equals("resume")) play(j,Json.position(j)); else open(j); });
        } catch(Exception e) { runOnUiThread(()-> { if(g==generation) toast(I18n.text("TV-Karte konnte nicht geöffnet werden: ")+e.getMessage()); }); } });
    }
    private AlertDialog exitDialog;
    private void confirmExit() {
        if(exitDialog!=null&&exitDialog.isShowing()) return;
        exitDialog=new AlertDialog.Builder(this).setTitle(I18n.text("Lowfin beenden?")).setMessage(I18n.text("Möchtest du die App wirklich beenden?"))
            .setPositiveButton(I18n.text("Beenden"),(dialog,which)->finish()).setNegativeButton(I18n.text("Weiter ansehen"),null).create();
        exitDialog.setOnShowListener(dialog->{ Button stay=exitDialog.getButton(AlertDialog.BUTTON_NEGATIVE); stay.setFocusableInTouchMode(true); stay.requestFocus(); stay.post(stay::requestFocus); }); exitDialog.show();
    }
    @Override public void onBackPressed() {
        if(drawerOpen) { setDrawer(false); return; }
        if(!history.isEmpty()) { Screen prev=history.pop(); screen=prev; generation++; shell(prev.title); switch(prev.kind) {
            case "seriesResume": seriesResume(); break; case "seasons": seasons(prev.item); break; case "detail": detail(prev.item); break; case "live": live(); break; case "home": home(); break;
            case "recommendations": recommendationItems(); break; case "settings":settings();break; case "ratings":ratingSettings();break; case "recordings":recordings();break; default: renderCurrent();
        } Motion.enter(contentStage,false); } else if(app.account!=null&&screen!=null&&!screen.kind.equals("home")) {
            show(new Screen("home",I18n.text("Deine Bibliothek"),null,null,null),false);
        } else if(screen!=null&&screen.kind.equals("home")&&focusedHomeRow>=2) {
            setHomeFocusMode(0); ((HomeAdapter)grid.getAdapter()).requestRow(0);
        } else confirmExit();
    }
}
