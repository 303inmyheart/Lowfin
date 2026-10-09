package de.lowfin.tv;
import android.app.*;
import android.widget.*;
import android.content.*;
import org.json.*;
import java.util.*;
/** Small, separate settings pages; all selections persist on this device. */
final class Options {
    private final Activity activity; private final LowfinApp app; private final LinearLayout content; private final Runnable reload;
    Options(Activity activity,LowfinApp app,LinearLayout body,Runnable reload) { this.activity=activity; this.app=app; this.reload=reload; ScrollView scroll=new ScrollView(activity); content=Ui.column(activity); scroll.addView(content); body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); }
    private void button(String label,Runnable action) { Ui.space(content,8); content.addView(Ui.button(activity,label,action)); }
    private void toggle(String label,String key,boolean fallback) { button(I18n.text(label)+": "+I18n.text(app.settings.getBoolean(key,fallback)?"Ein":"Aus"),()-> { app.settings.edit().putBoolean(key,!app.settings.getBoolean(key,fallback)).apply(); if(key.equals("randomAccent")||key.equals("rotateAccent")) app.accent.refresh(); reload.run(); }); }
    private void number(String label,String key,int fallback,int[] values,String[] names) { button(I18n.text(label)+": "+app.settings.getInt(key,fallback),()-> new AlertDialog.Builder(activity).setTitle(I18n.text(label)).setItems(names,(d,w)-> { app.settings.edit().putInt(key,values[w]).apply(); if(key.equals("accentMinutes")) app.accent.refresh(); reload.run(); }).setNegativeButton(I18n.text("Abbrechen"),null).show()); }
    private String[] numbers(int[] values) { String[] names=new String[values.length]; for(int i=0;i<values.length;i++) names[i]=""+values[i]; return names; }
    void genres() {
        toggle("Genre-Reihen anzeigen","genreRows",true); toggle("Nur die fokussierte Reihe anzeigen","singleShelf",true);
        content.addView(Ui.text(activity,I18n.text("Genres einzeln an- und ausschalten. Die Auswahl gilt für dieses Konto."),13,Ui.MUTED));
        Account account=app.account; TreeSet<String> names=new TreeSet<>(HomeGenres.read(app.settings,account)); LinearLayout choices=Ui.column(activity); content.addView(choices);
        Runnable render=()-> { if(activity.isFinishing()||app.account!=account) return; choices.removeAllViews(); List<String> pinned=HomeGenres.read(app.settings,account); for(String genre:names) { CheckBox check=new CheckBox(activity); check.setText(genre); check.setTextSize(14); check.setTextColor(android.graphics.Color.WHITE); check.setButtonTintList(android.content.res.ColorStateList.valueOf(Accent.color)); check.setChecked(pinned.contains(genre)); check.setOnCheckedChangeListener((b,on)-> { if(on!=HomeGenres.read(app.settings,account).contains(genre)) HomeGenres.toggle(app.settings,account,genre); }); choices.addView(check); } };
        render.run(); app.cacheIo.execute(()-> { List<JSONObject> cached=app.library.genres(); activity.runOnUiThread(()-> { if(!choices.isAttachedToWindow()) return; for(JSONObject genre:cached) names.add(genre.optString("Name")); render.run(); }); });
        Api api=app.api; app.navigationIo.execute(()-> { try { JSONArray items=Json.array(api.get("/Genres","UserId",account.user,"Recursive",true,"IncludeItemTypes","Movie"),"Items"); activity.runOnUiThread(()-> { if(!choices.isAttachedToWindow()) return; for(int i=0;i<items.length();i++) { JSONObject genre=items.optJSONObject(i); if(genre!=null&&!genre.optString("Name").isEmpty()) names.add(genre.optString("Name")); } render.run(); }); } catch(Exception ignored) {} });
    }
    void appearance() {
        toggle("UI-Animationen","animations",true);
        toggle("Flüssige Navigation und Aktionsanimationen","smoothNavigation",true);
        toggle("Fortsetzen-Reihe anzeigen","homeResume",true); toggle("Next-Up-Reihe anzeigen","homeNext",true);
        number("Empfehlungsreihen","recommendationRows",5,new int[]{0,1,2,3,4,5},numbers(new int[]{0,1,2,3,4,5}));
        number("Posterspalten","gridColumns",8,new int[]{4,6,8,10},numbers(new int[]{4,6,8,10}));
        number("Zufallsanimation · Sekunden","slotSeconds",4,new int[]{2,4,6,8},numbers(new int[]{2,4,6,8}));
        toggle("Zufällige Akzentfarbe beim Start","randomAccent",true); toggle("Akzentfarbe regelmäßig wechseln","rotateAccent",true);
        number("Akzentwechsel · aktive Minuten","accentMinutes",60,new int[]{15,30,60,120},numbers(new int[]{15,30,60,120}));
        button(I18n.text("Feste Akzentfarbe"),()-> { String[] names={I18n.text("Flieder"),I18n.text("Blaugrau"),I18n.text("Salbei"),I18n.text("Moos"),I18n.text("Sand"),I18n.text("Champagner"),I18n.text("Ton"),I18n.text("Rosé"),I18n.text("Lavendel"),I18n.text("Schiefer"),I18n.text("Eukalyptus"),I18n.text("Taupe")}; new AlertDialog.Builder(activity).setTitle(I18n.text("Feste Akzentfarbe")).setItems(names,(d,w)-> { app.settings.edit().putInt("accentIndex",w).putBoolean("randomAccent",false).putBoolean("rotateAccent",false).apply(); app.accent.refresh(); reload.run(); }).show(); });
        toggle("Bibliothek automatisch vorladen","autoPrecache",true); number("Cache-Aktualisierung · Stunden","cacheHours",24,new int[]{6,12,24,48,168},numbers(new int[]{6,12,24,48,168}));
    }
    void playback() {
        toggle("Untertitel standardmäßig aktivieren","defaultSubtitles",false);
        toggle("Intros automatisch überspringen","skipIntro",true); toggle("Outros automatisch überspringen","skipOutro",true);
        content.addView(Ui.text(activity,I18n.text("Seeken startet bei 5 Sekunden und beschleunigt beim Halten."),12,Ui.MUTED));
        number("Player-Menü ausblenden · Sekunden","controlsSeconds",5,new int[]{3,5,8,12,30},numbers(new int[]{3,5,8,12,30}));
        number("RAM-Puffer · Maximum MB","bufferMb",48,new int[]{24,32,48,64},numbers(new int[]{24,32,48,64}));
        number("Live-TV-Puffer · Maximum MB","liveBufferMb",32,new int[]{16,24,32,48},numbers(new int[]{16,24,32,48}));
        content.addView(Ui.text(activity,I18n.text("Der tatsächliche Puffer bleibt automatisch an freien RAM und Speicherreserve angepasst."),12,Ui.MUTED));
        button(I18n.text("Bevorzugte Audiosprache")+": "+app.settings.getString("audioLanguage",I18n.language()),()-> new AlertDialog.Builder(activity).setTitle(I18n.text("Bevorzugte Audiosprache")).setItems(new String[]{"English","Deutsch",I18n.text("Original / automatisch")},(d,w)-> { app.settings.edit().putString("audioLanguage",new String[]{"en","de",""}[w]).apply(); reload.run(); }).show());
    }
}
