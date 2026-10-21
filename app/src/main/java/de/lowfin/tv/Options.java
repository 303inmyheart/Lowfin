package de.lowfin.tv;
import android.app.*;
import android.widget.*;
import android.content.*;
import org.json.*;
import java.util.*;
/** Small, separate settings pages; all selections persist on this device. */
final class Options {
    private final Activity activity; private final LowfinApp app; private final LinearLayout content; private final Runnable reload;
    Options(Activity activity,LowfinApp app,LinearLayout body,Runnable reload) { this.activity=activity; this.app=app; this.reload=reload; ScrollView scroll=new ScrollView(activity); content=new SettingsColumn(activity); scroll.addView(content); body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); }
    Options(Activity activity,LowfinApp app,LinearLayout content,Runnable reload,boolean embedded) { this.activity=activity; this.app=app; this.content=content; this.reload=reload; }
    private void button(String label,Runnable action) { Ui.space(content,8); content.addView(Ui.button(activity,label,action)); }
    private void toggle(String label,String key,boolean fallback) { Ui.space(content,8); content.addView(Ui.toggle(activity,I18n.text(label),app.settings.getBoolean(key,fallback),on-> { app.settings.edit().putBoolean(key,on).apply(); if(key.equals("randomAccent")||key.equals("rotateAccent")) app.accent.refresh(); })); }
    private void number(String label,String key,int fallback,int[] values,String[] names) { button(I18n.text(label)+": "+app.settings.getInt(key,fallback),()-> LowfinDialog.builder(activity).setTitle(I18n.text(label)).setItems(names,(d,w)-> { app.settings.edit().putInt(key,values[w]).apply(); if(key.equals("accentMinutes")) app.accent.refresh(); reload.run(); }).setNegativeButton(I18n.text("Abbrechen"),null).show()); }
    private String[] numbers(int[] values) { String[] names=new String[values.length]; for(int i=0;i<values.length;i++) names[i]=""+values[i]; return names; }
    void genres() {
        toggle("Genre-Reihen anzeigen","genreRows",true); toggle("Nur die fokussierte Reihe anzeigen","singleShelf",true);
        content.addView(Ui.text(activity,I18n.text("Genres einzeln an- und ausschalten. Die Auswahl gilt für dieses Konto."),13,Ui.MUTED));
        Account account=app.account; TreeSet<String> names=new TreeSet<>(HomeGenres.read(app.settings,account)); LinearLayout choices=Ui.column(activity); content.addView(choices);
        Runnable render=()-> { if(activity.isFinishing()||app.account!=account) return; choices.removeAllViews(); List<String> pinned=HomeGenres.read(app.settings,account); for(String genre:names) { SettingSwitch check=Ui.toggle(activity,genre,pinned.contains(genre),on-> { if(on!=HomeGenres.read(app.settings,account).contains(genre)) HomeGenres.toggle(app.settings,account,genre); }); Ui.space(choices,6); choices.addView(check); } };
        render.run(); app.cacheIo.execute(()-> { List<JSONObject> cached=app.library.genres(); activity.runOnUiThread(()-> { if(!choices.isAttachedToWindow()) return; for(JSONObject genre:cached) names.add(genre.optString("Name")); render.run(); }); });
        Api api=app.api; app.navigationIo.execute(()-> { try { JSONArray items=Json.array(api.get("/Genres","UserId",account.user,"Recursive",true,"IncludeItemTypes","Movie"),"Items"); activity.runOnUiThread(()-> { if(!choices.isAttachedToWindow()) return; for(int i=0;i<items.length();i++) { JSONObject genre=items.optJSONObject(i); if(genre!=null&&!genre.optString("Name").isEmpty()) names.add(genre.optString("Name")); } render.run(); }); } catch(Exception ignored) {} });
    }
    void screensaver() {
        toggle(I18n.resource(R.string.screensaver_enable),"screensaver",false);
        content.addView(Ui.text(activity,I18n.resource(R.string.screensaver_hint),12,Ui.MUTED));
        button(I18n.resource(R.string.screensaver_libraries),()->AmbientLibraries.choose(activity,app,"screensaver"));
        int[] minutes=new int[20];for(int i=0;i<minutes.length;i++) minutes[i]=i+1;
        number(I18n.resource(R.string.screensaver_delay),"screensaverMinutes",3,minutes,numbers(minutes));
        number(I18n.resource(R.string.screensaver_interval),"screensaverSeconds",20,new int[]{10,20,30,60},numbers(new int[]{10,20,30,60}));
    }
    void appearance() {
        if(app.companion!=null&&app.companion.coverTagsSupported()) { Ui.space(content,8);content.addView(Ui.toggle(activity,I18n.resource(R.string.show_cover_tags),app.settings.getBoolean("showCoverTags",true),on->app.settings.edit().putBoolean("showCoverTags",on).apply())); }
        toggle("UI-Animationen","animations",true);
        Ui.space(content,8);content.addView(Ui.toggle(activity,I18n.resource(R.string.startup_logo),app.settings.getBoolean("startupLogo",true),on->app.settings.edit().putBoolean("startupLogo",on).apply()));
        toggle("Weiche Scrollanimationen","scrollAnimations",false);
        content.addView(Ui.text(activity,I18n.resource(R.string.scroll_animation_hint),12,Ui.MUTED));
        toggle("Flüssige Navigation und Aktionsanimationen","smoothNavigation",true);
        button(I18n.resource(R.string.dialog_transparency)+": "+app.settings.getInt("dialogTransparency",0)+" %",this::dialogTransparency);
        number("Posterspalten","gridColumns",8,new int[]{4,6,8,10},numbers(new int[]{4,6,8,10}));
        number("Zufallsanimation · Sekunden","slotSeconds",4,new int[]{2,4,6,8},numbers(new int[]{2,4,6,8}));
        toggle("Zufällige Akzentfarbe beim Start","randomAccent",true); toggle("Akzentfarbe regelmäßig wechseln","rotateAccent",true);
        number("Akzentwechsel · aktive Minuten","accentMinutes",60,new int[]{15,30,60,120},numbers(new int[]{15,30,60,120}));
        button(I18n.text("Feste Akzentfarbe"),()-> { String[] names={I18n.text("Flieder"),I18n.text("Blaugrau"),I18n.text("Salbei"),I18n.text("Moos"),I18n.text("Sand"),I18n.text("Champagner"),I18n.text("Ton"),I18n.text("Rosé"),I18n.text("Lavendel"),I18n.text("Schiefer"),I18n.text("Eukalyptus"),I18n.text("Taupe")}; LowfinDialog.builder(activity).setTitle(I18n.text("Feste Akzentfarbe")).setItems(names,(d,w)-> { app.settings.edit().putInt("accentIndex",w).putBoolean("randomAccent",false).putBoolean("rotateAccent",false).apply(); app.accent.refresh(); reload.run(); }).show(); });

    }
    private void dialogTransparency() {
        int saved=Math.max(0,Math.min(100,app.settings.getInt("dialogTransparency",0)));
        LinearLayout box=Ui.column(activity);box.setPadding(Ui.dp(activity,12),0,Ui.dp(activity,12),Ui.dp(activity,4));TextView value=Ui.text(activity,saved+" %",16,android.graphics.Color.WHITE);box.addView(value);
        SeekBar slider=new SeekBar(activity);slider.setFocusable(true);slider.setFocusableInTouchMode(true);slider.setMax(100);slider.setProgress(saved);slider.setKeyProgressIncrement(1);slider.setContentDescription(I18n.resource(R.string.dialog_transparency));box.addView(slider,new LinearLayout.LayoutParams(-1,Ui.dp(activity,40)));
        box.addView(Ui.text(activity,I18n.resource(R.string.dialog_transparency_hint),12,Ui.MUTED));
        AlertDialog dialog=LowfinDialog.builder(activity).setTitle(I18n.resource(R.string.dialog_transparency)).setView(box)
            .setNegativeButton(I18n.text("Abbrechen"),null).setPositiveButton(I18n.text("Speichern"),(d,w)-> { app.settings.edit().putInt("dialogTransparency",slider.getProgress()).apply();reload.run(); }).create();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int progress,boolean user) { value.setText(progress+" %");LowfinDialog.applyTransparency(dialog,progress); }
            public void onStartTrackingTouch(SeekBar bar) {}public void onStopTrackingTouch(SeekBar bar) {}
        });
        dialog.show();slider.requestFocus();dialog.getWindow().getDecorView().post(slider::requestFocus);
    }
    private void homeOrder() {
        Account account=app.account;List<String> keys=HomeLayout.keys(HomeGenres.read(app.settings,account),true,app.settings.getBoolean("discoveryBanner",false));
        LinearLayout rows=Ui.column(activity);ScrollView scroll=new ScrollView(activity);scroll.addView(rows);
        final Runnable[] render={null};final String[] selected={null};
        render[0]=()-> {
            rows.removeAllViews();List<String> order=HomeLayout.read(app.settings,account,keys);
            for(int i=0;i<order.size();i++) {
                String key=order.get(i);final int index=i;
                Button row=Ui.button(activity,(i+1)+" · "+HomeLayout.label(key),()-> {
                    List<String> names=new ArrayList<>();List<Integer> directions=new ArrayList<>();
                    if(index>0) { names.add(I18n.resource(R.string.home_move_up));directions.add(-1); }
                    if(index<order.size()-1) { names.add(I18n.resource(R.string.home_move_down));directions.add(1); }
                    LowfinDialog.builder(activity).setTitle(HomeLayout.label(key)).setItems(names.toArray(new String[0]),(d,w)-> {
                        if(app.account!=account) return;HomeLayout.move(app.settings,account,keys,key,directions.get(w));selected[0]=key;render[0].run();
                    }).setNegativeButton(I18n.text("Abbrechen"),null).show();
                });rows.addView(row);Ui.space(rows,4);if(key.equals(selected[0])) row.post(row::requestFocus);
            }
        };
        render[0].run();LowfinDialog.builder(activity).setTitle(I18n.resource(R.string.home_row_order)).setView(scroll).setNegativeButton(I18n.text("Schließen"),null).show();
    }
    void home() {
        Ui.space(content,8);content.addView(Ui.toggle(activity,I18n.resource(R.string.discovery_banner),app.settings.getBoolean("discoveryBanner",false),on->app.settings.edit().putBoolean("discoveryBanner",on).apply()));
        button(I18n.resource(R.string.banner_libraries),()->AmbientLibraries.choose(activity,app,"banner"));
        toggle(I18n.resource(R.string.home_show_libraries),"homeLibraries",false);
        button(I18n.resource(R.string.home_row_order),this::homeOrder);
        toggle("Fortsetzen-Reihe anzeigen","homeResume",true); toggle("Next-Up-Reihe anzeigen","homeNext",true);
        number("Empfehlungsreihen","recommendationRows",5,new int[]{0,1,2,3,4,5},numbers(new int[]{0,1,2,3,4,5}));
    }
    void cache() {
        Ui.space(content,8);content.addView(Ui.toggle(activity,I18n.resource(R.string.precache_keep_awake),app.settings.getBoolean("keepAwakeDuringPrecache",false),on->app.settings.edit().putBoolean("keepAwakeDuringPrecache",on).apply()));
        content.addView(Ui.text(activity,I18n.resource(R.string.precache_keep_awake_hint),12,Ui.MUTED));
        if(app.companion!=null&&app.companion.incrementalCache()) {
            Ui.space(content,8);content.addView(Ui.toggle(activity,I18n.resource(R.string.companion_update_toast),app.settings.getBoolean("companionUpdateToast",true),on->app.settings.edit().putBoolean("companionUpdateToast",on).apply()));
        }
        toggle("Bibliothek automatisch vorladen","autoPrecache",true); if(app.companion==null||!app.companion.incrementalCache()) number("Cache-Aktualisierung · Stunden","cacheHours",24,new int[]{6,12,24,48,168},numbers(new int[]{6,12,24,48,168}));
    }
    void live() {
        number("Live-TV-Puffer · Maximum MB","liveBufferMb",32,new int[]{16,24,32,48},numbers(new int[]{16,24,32,48}));
    }
    void playback() {
        content.addView(Ui.toggle(activity,I18n.resource(R.string.denoise_title),app.settings.getBoolean("videoDenoise",false),on->app.settings.edit().putBoolean("videoDenoise",on).apply()));
        content.addView(Ui.text(activity,I18n.resource(R.string.denoise_hint),12,Ui.MUTED));
        toggle("HDR-Wiedergabe","hdrPlayback",false);
        content.addView(Ui.text(activity,I18n.resource(R.string.hdr_hint),12,Ui.MUTED));
        toggle("Untertitel standardmäßig aktivieren","defaultSubtitles",false);
        toggle("Intros automatisch überspringen","skipIntro",true); toggle("Outros automatisch überspringen","skipOutro",true);
        content.addView(Ui.text(activity,I18n.text("Seeken startet bei 5 Sekunden und beschleunigt beim Halten."),12,Ui.MUTED));
        number("Player-Menü ausblenden · Sekunden","controlsSeconds",5,new int[]{3,5,8,12,30},numbers(new int[]{3,5,8,12,30}));
        number("RAM-Puffer · Maximum MB","bufferMb",48,new int[]{24,32,48,64},numbers(new int[]{24,32,48,64}));
        content.addView(Ui.text(activity,I18n.text("Der tatsächliche Puffer bleibt automatisch an freien RAM und Speicherreserve angepasst."),12,Ui.MUTED));
        button(I18n.text("Bevorzugte Audiosprache")+": "+app.settings.getString("audioLanguage",I18n.language()),()-> LowfinDialog.builder(activity).setTitle(I18n.text("Bevorzugte Audiosprache")).setItems(new String[]{"English","Deutsch",I18n.text("Original / automatisch")},(d,w)-> { app.settings.edit().putString("audioLanguage",new String[]{"en","de",""}[w]).apply(); reload.run(); }).show());
    }
}
