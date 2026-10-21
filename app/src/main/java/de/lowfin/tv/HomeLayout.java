package de.lowfin.tv;

import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;

/** Stable semantic shelf keys; titles and asynchronous data never determine ordering. */
final class HomeLayout {
    static final String LIBRARIES="libraries",BANNER="discovery";
    static List<String> keys(List<String> genres,boolean libraries) { return keys(genres,libraries,false); }
    static List<String> keys(List<String> genres,boolean libraries,boolean banner) {
        List<String> keys=new ArrayList<>(Arrays.asList("resume","next"));
        for(int i=0;i<HomeRecommendations.ROWS;i++) keys.add("recommendation:"+i);
        for(String genre:genres) keys.add("genre:"+genre);
        if(libraries) keys.add(LIBRARIES);
        if(banner) keys.add(0,BANNER);
        return keys;
    }
    private static String preference(Account account) { return "homeOrder-"+account.scope(); }
    static List<String> read(SharedPreferences prefs,Account account,List<String> available) {
        List<String> result=new ArrayList<>();
        try { JSONArray saved=new JSONArray(prefs.getString(preference(account),"[]"));
            for(int i=0;i<saved.length();i++) { String key=saved.optString(i);if(available.contains(key)&&!result.contains(key)) result.add(key); }
        } catch(Exception ignored) { }
        for(String key:available) if(!result.contains(key)) result.add(key);
        return result;
    }
    static void move(SharedPreferences prefs,Account account,List<String> available,String key,int direction) {
        // Preserve dormant rows, including genres temporarily disabled in settings.
        List<String> all=new ArrayList<>(available);
        try { JSONArray saved=new JSONArray(prefs.getString(preference(account),"[]"));for(int i=0;i<saved.length();i++) if(!all.contains(saved.optString(i))) all.add(saved.optString(i)); } catch(Exception ignored) { }
        List<String> order=read(prefs,account,all),visible=read(prefs,account,available);int from=visible.indexOf(key),to=from+direction;
        if(from<0||to<0||to>=visible.size()) return;
        Collections.swap(order,order.indexOf(key),order.indexOf(visible.get(to)));
        prefs.edit().putString(preference(account),new JSONArray(order).toString()).apply();
    }
    static String label(String key) {
        if(key.equals(BANNER)) return I18n.resource(R.string.discovery_banner);
        if(key.equals("resume")) return I18n.text("Weiterschauen");
        if(key.equals("next")) return I18n.text("Als Nächstes");
        if(key.equals(LIBRARIES)) return I18n.resource(R.string.home_libraries);
        if(key.startsWith("genre:")) return key.substring(6);
        return I18n.text("Empfehlungen")+" "+(Integer.parseInt(key.substring(15))+1);
    }
}
