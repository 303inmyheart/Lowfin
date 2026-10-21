package de.lowfin.tv;

import org.json.JSONObject;
import java.util.*;

/** Small account/screen-owned history; snapshots contain data only. */
final class DiscoveryHistory {
    final List<JSONObject> items=new ArrayList<>();
    String libraryScope="";
    int index=-1;
    boolean loading,empty;
    Runnable observer;
    DiscoveryHistory() {}
    DiscoveryHistory(DiscoveryHistory source) {libraryScope=source.libraryScope;items.addAll(source.items);index=source.index;empty=source.empty;}
    JSONObject current() {return index>=0&&index<items.size()?items.get(index):null;}
    JSONObject back() {if(index>0) index--;return current();}
    boolean canBack() {return index>0;}
    void add(JSONObject movie) {
        if(movie==null||!"Movie".equals(movie.optString("Type"))||movie.optString("Id").isEmpty()) return;
        while(items.size()>index+1) items.remove(items.size()-1);
        if(current()!=null&&current().optString("Id").equals(movie.optString("Id"))) {items.set(index,movie);return;}
        items.add(movie);if(items.size()>20) items.remove(0);index=items.size()-1;empty=false;
    }
}
