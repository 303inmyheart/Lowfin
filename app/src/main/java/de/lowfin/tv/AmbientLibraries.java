package de.lowfin.tv;

import android.app.Activity;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Independent, account-scoped library choices for the banner and screensaver. */
final class AmbientLibraries {
    static String key(Account account,String feature) { return "ambient-libraries-"+feature+"-"+account.scope(); }
    static List<String> selection(LowfinApp app,String feature) {
        if(!app.settings.contains(key(app.account,feature))) return null;
        List<String> ids=new ArrayList<>();try {JSONArray saved=new JSONArray(app.settings.getString(key(app.account,feature),"[]"));for(int i=0;i<saved.length();i++) if(!saved.optString(i).isEmpty()&&!ids.contains(saved.optString(i))) ids.add(saved.optString(i));}catch(Exception ignored){}return ids;
    }
    static String fingerprint(LowfinApp app,String feature) { return key(app.account,feature)+app.settings.getString(key(app.account,feature),"all"); }
    static String parent(List<String> selection) { return selection==null?null:selection.isEmpty()?"":selection.get(new Random().nextInt(selection.size())); }
    static void choose(Activity activity,LowfinApp app,String feature) {
        Api api=app.api;Account account=app.account;
        android.app.AlertDialog loading=LowfinDialog.builder(activity).setMessage(I18n.resource(R.string.ambient_libraries_loading)).setNegativeButton(I18n.text("Abbrechen"),null).show();
        app.navigationIo.execute(()-> {
            JSONArray result=new JSONArray();
            try {String cached=app.library.state("views");if(!cached.isEmpty()) result=new JSONArray(cached);if(result.length()==0) result=Json.array(api.get("/Users/"+account.user+"/Views"),"Items");}catch(Exception ignored){}
            JSONArray libraries=result;
            activity.runOnUiThread(()-> {
                if(!loading.isShowing()||activity.isFinishing()||app.api!=api) {loading.dismiss();return;}loading.dismiss();
                List<String> saved=selection(app,feature),chosen=new ArrayList<>();ScrollView scroll=new ScrollView(activity);SettingsColumn rows=new SettingsColumn(activity);scroll.addView(rows);
                for(int i=0;i<libraries.length();i++) {
                    JSONObject library=libraries.optJSONObject(i);if(library==null||library.optString("Id").isEmpty()) continue;
                    String type=library.optString("CollectionType");if(feature.equals("banner")&&type.equals("tvshows")) continue;if(Arrays.asList("livetv","music","books","photos","playlists").contains(type)) continue;
                    String id=library.optString("Id");boolean checked=saved==null||saved.contains(id);if(checked) chosen.add(id);
                    rows.addView(Ui.toggle(activity,library.optString("Name"),checked,on->{if(on&&!chosen.contains(id)) chosen.add(id);else if(!on) chosen.remove(id);}));
                }
                if(rows.getChildCount()==0) {LowfinDialog.builder(activity).setMessage(I18n.resource(R.string.ambient_libraries_empty)).setPositiveButton("OK",null).show();return;}
                LowfinDialog.builder(activity).setTitle(I18n.resource(feature.equals("screensaver")?R.string.screensaver_libraries:R.string.banner_libraries)).setView(scroll)
                    .setPositiveButton(I18n.text("Speichern"),(dialog,which)-> {if(app.api==api) app.settings.edit().putString(key(account,feature),new JSONArray(chosen).toString()).apply();})
                    .setNeutralButton(I18n.text("Alle Bibliotheken"),(dialog,which)-> {if(app.api==api) app.settings.edit().remove(key(account,feature)).apply();})
                    .setNegativeButton(I18n.text("Abbrechen"),null).show();
            });
        });
    }
}
