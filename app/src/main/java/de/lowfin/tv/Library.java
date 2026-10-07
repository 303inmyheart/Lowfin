package de.lowfin.tv;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

final class Library extends SQLiteOpenHelper {
    Library(Context context,String scope) { super(context,"library-"+scope+".db",null,4); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY,type TEXT,parent TEXT,series TEXT,name TEXT,season INTEGER,episode INTEGER,position INTEGER,played INTEGER,favorite INTEGER,json TEXT,stamp INTEGER)");
        db.execSQL("CREATE INDEX library_type ON items(type,name)");
        db.execSQL("CREATE INDEX library_parent ON items(parent,season,episode)");
        db.execSQL("CREATE INDEX library_series ON items(series,season,episode)");
        db.execSQL("CREATE TABLE state (key TEXT PRIMARY KEY,value TEXT)");
        members(db); genreTable(db);
    }
    private void members(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS members (library TEXT,item TEXT,stamp INTEGER,PRIMARY KEY(library,item))"); }
    private void genreTable(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS genres (item TEXT,name TEXT COLLATE NOCASE,PRIMARY KEY(item,name))"); db.execSQL("CREATE INDEX IF NOT EXISTS genre_name ON genres(name,item)"); }
    private void indexGenres(SQLiteDatabase db,JSONObject item) {
        String id=item.optString("Id"); db.delete("genres","item=?",new String[]{id}); JSONArray genres=Json.array(item,"Genres");
        for(int i=0;i<genres.length();i++) { String name=genres.optString(i).trim(); if(name.isEmpty()) continue; ContentValues values=new ContentValues(); values.put("item",id); values.put("name",name); db.insertWithOnConflict("genres",null,values,SQLiteDatabase.CONFLICT_IGNORE); }
    }
    @Override public void onUpgrade(SQLiteDatabase db,int a,int b) {
        if(a<2) members(db);
        if(a<3) {
            genreTable(db);
            ContentValues marker=new ContentValues(); marker.put("key","genreBackfill"); marker.put("value","");
            db.insertWithOnConflict("state",null,marker,SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    // Never scan the whole library while SQLiteOpenHelper holds its opening lock.
    boolean backfillGenres() {
        SQLiteDatabase db=getWritableDatabase(); String after;
        try(Cursor c=db.rawQuery("SELECT value FROM state WHERE key='genreBackfill'",null)) { if(!c.moveToFirst()) return false; after=c.getString(0); }
        List<JSONObject> batch=query("type IN ('Movie','Series') AND id>?",new String[]{after},"id",50,0);
        db.beginTransaction();
        try {
            for(JSONObject item:batch) indexGenres(db,item);
            if(batch.isEmpty()) db.delete("state","key='genreBackfill'",null);
            else { ContentValues marker=new ContentValues(); marker.put("value",batch.get(batch.size()-1).optString("Id")); db.update("state",marker,"key='genreBackfill'",null); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return !batch.isEmpty();
    }
    void put(JSONArray items,long stamp) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { for(int i=0;i<items.length();i++) { JSONObject j=items.optJSONObject(i); if(j!=null) put(db,j,stamp); } db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    void put(JSONObject item) { put(getWritableDatabase(),item,System.currentTimeMillis()); }
    private void put(SQLiteDatabase db,JSONObject j,long stamp) {
        if(j.optString("Id").isEmpty()) return;
        // Short list DTOs must not discard detail metadata already cached.
        try(Cursor old=db.rawQuery("SELECT json FROM items WHERE id=?",new String[]{j.optString("Id")})) {
            if(old.moveToFirst()) {
                JSONObject merged=new JSONObject(old.getString(0)); Iterator<String> keys=j.keys();
                while(keys.hasNext()) { String key=keys.next(); merged.put(key,j.get(key)); }
                j=merged;
            }
        } catch(JSONException ignored) { }
        JSONObject u=Json.object(j,"UserData"); ContentValues v=new ContentValues();
        v.put("id",j.optString("Id")); v.put("type",j.optString("Type")); v.put("parent",j.optString("ParentId"));
        v.put("series",j.optString("SeriesId")); v.put("name",j.optString("SortName",j.optString("Name")));
        v.put("season",j.optInt("ParentIndexNumber",j.optInt("IndexNumber"))); v.put("episode",j.optInt("IndexNumber"));
        v.put("position",u.optLong("PlaybackPositionTicks")); v.put("played",u.optBoolean("Played")?1:0);
        v.put("favorite",u.optBoolean("IsFavorite")?1:0); v.put("json",j.toString()); v.put("stamp",stamp);
        db.insertWithOnConflict("items",null,v,SQLiteDatabase.CONFLICT_REPLACE); indexGenres(db,j);
    }
    List<JSONObject> query(String where,String[] args,String order,int limit,int offset) {
        List<JSONObject> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("items",new String[]{"json"},where,args,null,null,order,offset+","+limit)) {
            while(c.moveToNext()) { try { result.add(new JSONObject(c.getString(0))); } catch(JSONException ignored) {} }
        }
        return result;
    }
    JSONObject item(String id) { List<JSONObject> r=query("id=?",new String[]{id},"name",1,0); return r.isEmpty()?null:r.get(0); }
    List<JSONObject> browse(String type,String parent,String search,int offset,int limit) {
        String[] types=type.split(","); String where="type IN ("+String.join(",",Collections.nCopies(types.length,"?"))+")"; List<String> args=new ArrayList<>(Arrays.asList(types));
        if(parent!=null) {
            if(type.equals("Episode")) { where+=" AND parent=?"; args.add(parent); }
            else { where+=" AND (parent=? OR id IN (SELECT item FROM members WHERE library=?))"; args.add(parent); args.add(parent); }
        }
        if(search!=null&&!search.isEmpty()) { where+=" AND name LIKE ? ESCAPE '\\'"; args.add("%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"); }
        return query(where,args.toArray(new String[0]),type.equals("Episode")?"season,episode":"name COLLATE NOCASE",limit,offset);
    }
    void link(String library,JSONArray items,long stamp) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction(); try {
            for(int i=0;i<items.length();i++) { JSONObject j=items.optJSONObject(i); if(j==null||j.optString("Id").isEmpty()) continue; ContentValues v=new ContentValues(); v.put("library",library); v.put("item",j.optString("Id")); v.put("stamp",stamp); db.insertWithOnConflict("members",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    void completeLinks(String library,long stamp) { getWritableDatabase().delete("members","library=? AND stamp<?",new String[]{library,""+stamp}); }
    List<JSONObject> genres() {
        List<JSONObject> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT genres.name,SUM(items.type='Movie'),SUM(items.type='Series') FROM genres JOIN items ON items.id=genres.item WHERE items.type IN ('Movie','Series') GROUP BY genres.name ORDER BY genres.name COLLATE NOCASE",null)) {
            while(cursor.moveToNext()) result.add(Json.obj("Id",cursor.getString(0),"Name",cursor.getString(0),"Type","Genre","MovieCount",cursor.getInt(1),"SeriesCount",cursor.getInt(2)));
        } return result;
    }
    List<JSONObject> genreItems(String genre,int offset,int limit) { return query("type IN ('Movie','Series') AND id IN (SELECT item FROM genres WHERE name=?)",new String[]{genre},"name COLLATE NOCASE",limit,offset); }
    List<JSONObject> randomGenreMovies(String genre,int limit) { return query("type='Movie' AND id IN (SELECT item FROM genres WHERE name=?)",new String[]{genre},"RANDOM()",limit,0); }
    JSONObject randomMovie(String parent) {
        String where="type='Movie'"; String[] args=null;
        if(parent!=null) { where+=" AND (parent=? OR id IN (SELECT item FROM members WHERE library=?))"; args=new String[]{parent,parent}; }
        List<JSONObject> items=query(where,args,"RANDOM()",1,0); return items.isEmpty()?null:items.get(0);
    }
    JSONObject next(JSONObject j) {
        List<JSONObject> r=query("type='Episode' AND series=? AND (season>? OR (season=? AND episode>?))",
            new String[]{j.optString("SeriesId"),""+j.optInt("ParentIndexNumber"),""+j.optInt("ParentIndexNumber"),""+j.optInt("IndexNumber")},"season,episode",1,0);
        return r.isEmpty()?null:r.get(0);
    }
    void state(String key,String value) { ContentValues v=new ContentValues(); v.put("key",key); v.put("value",value); getWritableDatabase().insertWithOnConflict("state",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    String state(String key) { try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM state WHERE key=?",new String[]{key})) { return c.moveToFirst()?c.getString(0):""; } }
    void completeSync(long stamp) {
        getWritableDatabase().delete("items","type IN ('Movie','Series','Season','Episode','BoxSet','Video') AND stamp<?",new String[]{""+stamp});
        getWritableDatabase().execSQL("DELETE FROM genres WHERE item NOT IN (SELECT id FROM items)");
        state("lastSync",""+stamp);
    }
    int count() { try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items",null)) { c.moveToFirst(); return c.getInt(0); } }
}
