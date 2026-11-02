package de.lowfin.tv;

import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONArray;

final class SidebarLayout {
    static final List<String> ALL=Collections.unmodifiableList(Arrays.asList("home","search","genres","live","random","favorites","watchLater"));
    private static String key(Account account) {return "sidebarOrder-"+account.scope();}
    static List<String> read(SharedPreferences prefs,Account account,List<String> available) {
        List<String> result=new ArrayList<>();try {JSONArray saved=new JSONArray(prefs.getString(key(account),"[]"));for(int i=0;i<saved.length();i++) {String id=saved.optString(i);if(available.contains(id)&&!result.contains(id))result.add(id);}}catch(Exception ignored){}
        for(String id:available)if(ALL.contains(id)&&!result.contains(id))result.add(id);return result;
    }
    static void save(SharedPreferences prefs,Account account,List<String> visible,List<String> desired) {
        prefs.edit().putString(key(account),new JSONArray(OrderSequence.merge(read(prefs,account,ALL),visible,desired)).toString()).apply();
    }
    static String label(String id) {
        switch(id) {case "home":return I18n.text("Start");case "search":return I18n.text("Suche");case "genres":return "Genres";case "live":return "Live TV";case "random":return I18n.text("Zufälliger Film");case "favorites":return I18n.text("Favoriten");default:return I18n.text("Später ansehen");}
    }
}
