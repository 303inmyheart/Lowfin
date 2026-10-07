package de.lowfin.tv;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Ordered pins belong to a server account, including an explicitly empty list. */
final class HomeGenres {
    private static String key(Account account) { return "homeGenres-"+account.scope(); }
    static List<String> read(SharedPreferences prefs,Account account) {
        List<String> result=new ArrayList<>();
        try {
            JSONArray names=new JSONArray(prefs.getString(key(account),"[\"Horror\",\"Thriller\"]"));
            for(int i=0;i<names.length();i++) { String name=names.optString(i).trim(); if(!name.isEmpty()&&!result.contains(name)) result.add(name); }
        } catch(JSONException ignored) { }
        return result;
    }
    static void toggle(SharedPreferences prefs,Account account,String genre) {
        List<String> names=read(prefs,account); if(!names.remove(genre)) names.add(genre);
        prefs.edit().putString(key(account),new JSONArray(names).toString()).apply();
    }
}
