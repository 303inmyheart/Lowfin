package de.lowfin.tv;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

final class Library extends SQLiteOpenHelper {
    volatile boolean companionAvailable;
    private boolean cacheRetired;
    Library(Context context,String scope) { super(context,"library-"+scope+".db",null,8); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY,type TEXT,parent TEXT,series TEXT,name TEXT,season INTEGER,episode INTEGER,position INTEGER,played INTEGER,favorite INTEGER,json TEXT,stamp INTEGER)");
        db.execSQL("CREATE INDEX library_type ON items(type,name)");
        db.execSQL("CREATE INDEX library_parent ON items(parent,season,episode)");
        db.execSQL("CREATE INDEX library_series ON items(series,season,episode)");
        db.execSQL("CREATE TABLE state (key TEXT PRIMARY KEY,value TEXT)");
        members(db); genreTable(db); companionTable(db); artworkQueue(db); performanceIndexes(db);
    }
    private void companionTable(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS companion (companion_item TEXT PRIMARY KEY,companion_json TEXT,companion_resume INTEGER,companion_has_resume INTEGER,companion_stamp INTEGER,companion_visible INTEGER NOT NULL DEFAULT 1)"); }
    synchronized void companionState(String id,JSONObject state) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            JSONObject previous=companionState(id); if(!CompanionPolicy.newer(state,previous)) return;
            long stamp=System.currentTimeMillis(); try { stamp=java.time.Instant.parse(state.optString("updatedAt")).toEpochMilli(); } catch(Exception ignored) { }
            ContentValues values=new ContentValues(); values.put("companion_item",id); values.put("companion_json",state.toString()); values.put("companion_resume",state.optLong("resumeTicks")); values.put("companion_has_resume",CompanionPolicy.active(state)&&state.optBoolean("hasResume")?1:0); values.put("companion_stamp",stamp); values.put("companion_visible",state.optBoolean("resumeVisible",true)?1:0);
            db.insertWithOnConflict("companion",null,values,SQLiteDatabase.CONFLICT_REPLACE); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void mergeCompanionStates(JSONArray states) throws JSONException {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            for(int index=0;index<states.length();index++) {
                JSONObject row=states.optJSONObject(index); if(row==null||row.optJSONObject("state")==null) continue;
                String id=row.optString("itemId"); JSONObject state=row.optJSONObject("state"); companionState(id,state);
                JSONObject accepted=companionState(id);
                if(!CompanionPolicy.active(state)&&accepted!=null&&accepted.optLong("revision")==state.optLong("revision")&&accepted.optLong("sequence",-1)==state.optLong("sequence",-1)) {
                    JSONObject metadata=item(id); if(metadata!=null) { JSONObject data=Json.object(metadata,"UserData"); data.put("PlaybackPositionTicks",state.optLong("resumeTicks")); data.put("Played",state.optBoolean("played")); metadata.put("UserData",data); put(db,metadata,System.currentTimeMillis()); }
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    JSONObject companionState(String id) {
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT companion_json FROM companion WHERE companion_item=?",new String[]{id})) { return cursor.moveToFirst()?new JSONObject(cursor.getString(0)):null; } catch(JSONException e) { return null; }
    }
    JSONObject withCompanion(JSONObject item) {
        boolean enabled=companionAvailable;
        return decorateCompanion(item,enabled?companionState(item.optString("Id")):null,enabled);
    }
    /** One bounded lookup per batch, preserving input order, duplicates and plugin visibility rules. */
    List<JSONObject> withCompanionItems(List<JSONObject> items) {
        boolean enabled=companionAvailable;Map<String,JSONObject> states=new HashMap<>();
        if(enabled&&!items.isEmpty()) {
            List<String> ids=new ArrayList<>(new LinkedHashSet<>(itemIds(items)));
            for(int start=0;start<ids.size();start+=128) {
                List<String> batch=ids.subList(start,Math.min(start+128,ids.size()));
                try(Cursor cursor=getReadableDatabase().rawQuery("SELECT companion_item,companion_json FROM companion WHERE companion_item IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")",batch.toArray(new String[0]))) {
                    while(cursor.moveToNext()) try { states.put(cursor.getString(0),new JSONObject(cursor.getString(1))); } catch(JSONException ignored) {}
                }
            }
        }
        List<JSONObject> result=new ArrayList<>(items.size());
        for(JSONObject item:items) result.add(decorateCompanion(item,states.get(item.optString("Id")),enabled));
        return result;
    }
    private static List<String> itemIds(List<JSONObject> items) { List<String> ids=new ArrayList<>(items.size());for(JSONObject item:items) ids.add(item.optString("Id"));return ids; }
    private static JSONObject decorateCompanion(JSONObject item,JSONObject state,boolean enabled) {
        if(state!=null) return CompanionPolicy.decorate(item,state);
        if(!enabled&&item.has("LowfinCompanion")) try { JSONObject copy=new JSONObject(item.toString()); copy.remove("LowfinCompanion"); return copy; } catch(JSONException ignored) { }
        return item;
    }
    List<JSONObject> resumable(String extra,String[] args,int limit,int offset) {
        boolean enabled=companionAvailable;
        String condition=enabled?"((position>0 AND played=0) OR (companion_has_resume=1 AND companion_resume>0)) AND COALESCE(companion_visible,1)=1":"position>0 AND played=0";
        List<JSONObject> values=query(condition+(extra==null?"":" AND ("+extra+")"),args,enabled?"CASE WHEN companion_has_resume=1 AND companion_stamp>0 THEN companion_stamp ELSE stamp END DESC":"stamp DESC",limit,offset,enabled);
        values.removeIf(item->!Json.inContinue(item)); return values;
    }
    private void members(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS members (library TEXT,item TEXT,stamp INTEGER,PRIMARY KEY(library,item))"); }
    private void genreTable(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS genres (item TEXT,name TEXT COLLATE NOCASE,PRIMARY KEY(item,name))"); db.execSQL("CREATE INDEX IF NOT EXISTS genre_name ON genres(name,item)"); }
    private void indexGenres(SQLiteDatabase db,JSONObject item) {
        String id=item.optString("Id"); db.delete("genres","item=?",new String[]{id}); JSONArray genres=Json.array(item,"Genres");
        for(int i=0;i<genres.length();i++) { String name=genres.optString(i).trim(); if(name.isEmpty()) continue; ContentValues values=new ContentValues(); values.put("item",id); values.put("name",name); db.insertWithOnConflict("genres",null,values,SQLiteDatabase.CONFLICT_IGNORE); }
    }
    private void artworkQueue(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS cache_artwork (item TEXT PRIMARY KEY,retry INTEGER NOT NULL DEFAULT 0)"); }
    private void performanceIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS library_type_nocase ON items(type,name COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS library_resume ON items(stamp DESC) WHERE position>0 AND played=0");
        db.execSQL("CREATE INDEX IF NOT EXISTS members_item ON members(item,library)");
        db.execSQL("CREATE INDEX IF NOT EXISTS artwork_retry ON cache_artwork(retry,item)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int a,int b) {
        if(a<7) artworkQueue(db);
        if(a<5) companionTable(db);
        if(a==5) db.execSQL("ALTER TABLE companion ADD COLUMN companion_visible INTEGER NOT NULL DEFAULT 1");
        if(a<2) members(db);
        if(a<3) {
            genreTable(db);
            ContentValues marker=new ContentValues(); marker.put("key","genreBackfill"); marker.put("value","");
            db.insertWithOnConflict("state",null,marker,SQLiteDatabase.CONFLICT_REPLACE);
        }
        if(a<8) performanceIndexes(db);
    }
    // Never scan the whole library while SQLiteOpenHelper holds its opening lock.
    synchronized boolean backfillGenres() { if(cacheRetired) return false;
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
    synchronized void put(JSONArray items,long stamp) { if(cacheRetired) return;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { for(int i=0;i<items.length();i++) { JSONObject j=items.optJSONObject(i); if(j!=null) put(db,j,stamp); } db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    synchronized void put(JSONObject item) { if(cacheRetired) return;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { put(db,item,System.currentTimeMillis()); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    private void put(SQLiteDatabase db,JSONObject j,long stamp) {
        if(cacheRetired) return;
        if(j.optString("Id").isEmpty()) return;
        boolean genresChanged=true;
        // Short list DTOs must not discard detail metadata already cached.
        try(Cursor old=db.rawQuery("SELECT json FROM items WHERE id=?",new String[]{j.optString("Id")})) {
            if(old.moveToFirst()) {
                JSONObject merged=new JSONObject(old.getString(0));
                boolean changed=merged.has("LowfinCompanion");
                genresChanged=j.has("Genres")&&!MediaDiff.jsonEquals(Json.array(j,"Genres"),Json.array(merged,"Genres"));
                Iterator<String> keys=j.keys();
                while(keys.hasNext()) { String key=keys.next();if(key.equals("LowfinCompanion")) continue;Object value=j.get(key);if(!merged.has(key)||!MediaDiff.jsonEquals(merged.opt(key),value)) {changed=true;merged.put(key,value);} }
                if(!changed) {
                    // Keep freshness/resume ordering without rewriting the JSON blob and every index.
                    ContentValues freshness=new ContentValues();freshness.put("stamp",stamp);db.update("items",freshness,"id=?",new String[]{j.optString("Id")});return;
                }
                j=merged;
            }
        } catch(JSONException ignored) { }
        j.remove("LowfinCompanion");
        JSONObject u=Json.object(j,"UserData"); ContentValues v=new ContentValues();
        v.put("id",j.optString("Id")); v.put("type",j.optString("Type")); v.put("parent",j.optString("ParentId"));
        v.put("series",j.optString("SeriesId")); v.put("name",j.optString("SortName",j.optString("Name")));
        v.put("season",j.optInt("ParentIndexNumber",j.optInt("IndexNumber"))); v.put("episode",j.optInt("IndexNumber"));
        v.put("position",u.optLong("PlaybackPositionTicks")); v.put("played",u.optBoolean("Played")?1:0);
        v.put("favorite",u.optBoolean("IsFavorite")?1:0); v.put("json",j.toString()); v.put("stamp",stamp);
        db.insertWithOnConflict("items",null,v,SQLiteDatabase.CONFLICT_REPLACE); if(genresChanged) indexGenres(db,j);
    }
    List<JSONObject> query(String where,String[] args,String order,int limit,int offset) {
        return query(where,args,order,limit,offset,companionAvailable);
    }
    private List<JSONObject> query(String where,String[] args,String order,int limit,int offset,boolean join) {
        List<JSONObject> result=new ArrayList<>();
        String table=join?"items LEFT JOIN companion ON items.id=companion.companion_item":"items";
        String[] columns=join?new String[]{"items.json","companion_json"}:new String[]{"items.json"};
        try(Cursor c=getReadableDatabase().query(table,columns,where,args,null,null,order,offset+","+limit)) {
            while(c.moveToNext()) { try { JSONObject item=new JSONObject(c.getString(0)); if(join&&!c.isNull(1)) item=CompanionPolicy.decorate(item,new JSONObject(c.getString(1))); result.add(item); } catch(JSONException ignored) {} }
        }
        return result;
    }
    JSONObject item(String id) { List<JSONObject> r=query("id=?",new String[]{id},"name",1,0); return r.isEmpty()?null:r.get(0); }
    /** Refresh a bounded snapshot in SQL batches, preserving order, duplicates and missing entries. */
    List<JSONObject> latestItems(List<JSONObject> items) {
        Map<String,JSONObject> latest=itemsById(itemIds(items));
        List<JSONObject> result=new ArrayList<>(items.size());
        for(JSONObject item:items) { JSONObject current=latest.get(item.optString("Id"));result.add(current==null?item:current); }
        return result;
    }
    /** Only rows actually present in this account database; absence must not revive stale cards. */
    Map<String,JSONObject> itemsById(Collection<String> requested) {
        List<String> ids=new ArrayList<>(new LinkedHashSet<>(requested));
        Map<String,JSONObject> latest=new HashMap<>();
        for(int start=0;start<ids.size();start+=128) {
            List<String> batch=ids.subList(start,Math.min(start+128,ids.size()));
            for(JSONObject item:query("id IN ("+String.join(",",Collections.nCopies(batch.size(),"?"))+")",batch.toArray(new String[0]),"id",128,0)) latest.put(item.optString("Id"),item);
        }
        return latest;
    }
    /** One batched lookup across shelves; retain their boundaries and missing-item fallbacks. */
    List<List<JSONObject>> latestRows(List<List<JSONObject>> rows) {
        List<JSONObject> all=new ArrayList<>();for(List<JSONObject> row:rows) all.addAll(row);
        List<JSONObject> latest=latestItems(all);List<List<JSONObject>> result=new ArrayList<>(rows.size());int offset=0;
        for(List<JSONObject> row:rows) {result.add(new ArrayList<>(latest.subList(offset,offset+row.size())));offset+=row.size();}
        return result;
    }
    List<JSONObject> browse(String type,String parent,String search,int offset,int limit) {
        String[] types=type.split(","); String where="type IN ("+String.join(",",Collections.nCopies(types.length,"?"))+")"; List<String> args=new ArrayList<>(Arrays.asList(types));
        if(parent!=null) {
            if(type.equals("Episode")) { where+=" AND parent=?"; args.add(parent); }
            else { where+=" AND (parent=? OR id IN (SELECT item FROM members WHERE library=?))"; args.add(parent); args.add(parent); }
        }
        if(search!=null&&!search.isEmpty()) { where+=" AND name LIKE ? ESCAPE '\\'"; args.add("%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"); }
        return query(where,args.toArray(new String[0]),type.equals("Episode")?"season,episode":"name COLLATE NOCASE",limit,offset);
    }
    int letterOffset(String type,String parent,String letter) {
        if("#".equals(letter)) return 0;
        if(!letter.matches("[A-Z]")) throw new IllegalArgumentException("Invalid letter");
        String[] types=type.split(",");String where="type IN ("+String.join(",",Collections.nCopies(types.length,"?"))+")";List<String> args=new ArrayList<>(Arrays.asList(types));
        if(parent!=null) { where+=" AND (parent=? OR id IN (SELECT item FROM members WHERE library=?))";args.add(parent);args.add(parent); }
        List<String> values=new ArrayList<>();values.add(letter);values.add(letter);values.addAll(args);
        try(Cursor rows=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(name COLLATE NOCASE < ?),0),COALESCE(SUM(name COLLATE NOCASE >= ?),0) FROM items WHERE "+where,values.toArray(new String[0]))) { return rows.moveToFirst()&&rows.getInt(1)>0?rows.getInt(0):-1; }
    }
    synchronized void link(String library,JSONArray items,long stamp) { if(cacheRetired) return;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction(); try {
            for(int i=0;i<items.length();i++) { JSONObject j=items.optJSONObject(i); if(j==null||j.optString("Id").isEmpty()) continue; ContentValues v=new ContentValues(); v.put("library",library); v.put("item",j.optString("Id")); v.put("stamp",stamp); db.insertWithOnConflict("members",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void completeLinks(String library,long stamp) { if(cacheRetired) return; getWritableDatabase().delete("members","library=? AND stamp<?",new String[]{library,""+stamp}); }
    List<JSONObject> genres() {
        List<JSONObject> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT genres.name,SUM(items.type='Movie'),SUM(items.type='Series') FROM genres JOIN items ON items.id=genres.item WHERE items.type IN ('Movie','Series') GROUP BY genres.name ORDER BY genres.name COLLATE NOCASE",null)) {
            while(cursor.moveToNext()) result.add(Json.obj("Id",cursor.getString(0),"Name",cursor.getString(0),"Type","Genre","MovieCount",cursor.getInt(1),"SeriesCount",cursor.getInt(2)));
        } return result;
    }
    List<JSONObject> genreItems(String genre,int offset,int limit) { return query("type IN ('Movie','Series') AND id IN (SELECT item FROM genres WHERE name=?)",new String[]{genre},"name COLLATE NOCASE",limit,offset); }
    List<JSONObject> randomGenreMovies(String genre,int limit) { return query("type='Movie' AND id IN (SELECT item FROM genres WHERE name=?)",new String[]{genre},"RANDOM()",limit,0); }
    List<JSONObject> randomMovies(String parent,String genre,int limit) {
        String where="type='Movie'"; List<String> args=new ArrayList<>();
        if(parent!=null) { where+=" AND (parent=? OR id IN (SELECT item FROM members WHERE library=?))"; args.add(parent); args.add(parent); }
        if(genre!=null) { where+=" AND id IN (SELECT item FROM genres WHERE name=?)"; args.add(genre); }
        return query(where,args.toArray(new String[0]),"RANDOM()",limit,0);
    }
    List<String> movieGenres(String parent) {
        List<String> values=new ArrayList<>(); String where="items.type='Movie'"; String[] args=null;
        if(parent!=null) { where+=" AND (items.parent=? OR items.id IN (SELECT item FROM members WHERE library=?))"; args=new String[]{parent,parent}; }
        try(Cursor rows=getReadableDatabase().rawQuery("SELECT DISTINCT genres.name FROM genres JOIN items ON items.id=genres.item WHERE "+where+" ORDER BY genres.name COLLATE NOCASE",args)) { while(rows.moveToNext()) values.add(rows.getString(0)); }
        return values;
    }
    JSONObject randomMovie(String parent) { List<JSONObject> items=randomMovies(parent,null,1); return items.isEmpty()?null:items.get(0); }
    JSONObject next(JSONObject j) {
        List<JSONObject> r=query("type='Episode' AND series=? AND (season>? OR (season=? AND episode>?))",
            new String[]{j.optString("SeriesId"),""+j.optInt("ParentIndexNumber"),""+j.optInt("ParentIndexNumber"),""+j.optInt("IndexNumber")},"season,episode",1,0);
        return r.isEmpty()?null:r.get(0);
    }
    /** Persist a small group of state values atomically, without one commit per cover tag. */
    synchronized void stateBatch(Map<String,String> values) {
        if(values.isEmpty()) return;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {for(Map.Entry<String,String> value:values.entrySet()) state(value.getKey(),value.getValue());db.setTransactionSuccessful();}
        finally {db.endTransaction();}
    }
    synchronized void state(String key,String value) { if(cacheRetired&&!persistentState(key)) return; ContentValues v=new ContentValues(); v.put("key",key); v.put("value",value); getWritableDatabase().insertWithOnConflict("state",null,v,SQLiteDatabase.CONFLICT_REPLACE); }
    private static boolean persistentState(String key) { return key.startsWith("ratings-")||key.startsWith("companion-ratings-"); }
    /** Retire this writer generation so late requests cannot repopulate a cleared precache. */
    synchronized void clearAndRetirePrecache() {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            db.delete("items",null,null);db.delete("genres",null,null);db.delete("members",null,null);db.delete("cache_artwork",null,null);
            db.execSQL("DELETE FROM state WHERE key NOT LIKE 'ratings-%' AND key NOT LIKE 'companion-ratings-%'");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        cacheRetired=true;
        try { db.execSQL("VACUUM");try(Cursor checkpoint=db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)) { checkpoint.moveToFirst(); } }
        catch(SQLiteException error) { android.util.Log.w("LowfinCache","Cache deleted; storage compaction deferred"); }
    }
    synchronized boolean precacheComplete() {
        String saved=state("precacheComplete");
        // Upgrade an existing Companion baseline only when its durable cover queue is exhausted.
        if(saved.isEmpty()&&state("precacheBaselinePending").isEmpty()&&!state("lastSync").isEmpty()&&!state("cacheCursor").isEmpty()&&!"true".equals(state("cacheNeedsReview"))&&count()>0&&artworkCount()==0) {
            state("precacheComplete","true");return true;
        }
        return "true".equals(saved);
    }
    synchronized void resetPrecacheCompletion() { state("precacheComplete","false");state("precacheBaselinePending",""); }
    synchronized void finishPrecacheArtwork() {
        if("true".equals(state("precacheBaselinePending"))&&artworkCount()==0) {
            state("precacheComplete","true");state("precacheBaselinePending","");
        }
    }
    String state(String key) { try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM state WHERE key=?",new String[]{key})) { return c.moveToFirst()?c.getString(0):""; } }
    synchronized void completeSync(long stamp) { if(cacheRetired) return;
        getWritableDatabase().delete("items","type IN ('Movie','Series','Season','Episode','BoxSet','Video') AND stamp<?",new String[]{""+stamp});
        getWritableDatabase().execSQL("DELETE FROM genres WHERE item NOT IN (SELECT id FROM items)");
        state("lastSync",""+stamp);
    }
    /** Commit a complete metadata baseline and durable artwork work before the slow cover pass. */
    synchronized void checkpointSync(long stamp,String cursor,boolean channels) { if(cacheRetired) return;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            completeSync(stamp);
            if(!cursor.isEmpty()) {
                db.execSQL("DELETE FROM cache_artwork WHERE item NOT IN (SELECT id FROM items)");
                db.execSQL("INSERT OR IGNORE INTO cache_artwork(item,retry) SELECT id,0 FROM items WHERE type!='TvChannel'");
                db.execSQL("DELETE FROM cache_artwork WHERE item IN (SELECT id FROM items WHERE type='TvChannel')");
                state("cacheCursor",cursor);
                state("cacheNeedsReview","");
                state("cacheStatus",I18n.resource(R.string.cache_baseline_ready));
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void replaceChannels(JSONArray channels) { if(cacheRetired) return;
        for(int i=0;i<channels.length();i++) { JSONObject item=channels.optJSONObject(i); if(item==null||!"TvChannel".equals(item.optString("Type"))||item.optString("Id").isEmpty()) throw new IllegalArgumentException("Invalid channel list"); }
        Set<String> current=new HashSet<>(); for(int i=0;i<channels.length();i++) current.add(channels.optJSONObject(i).optString("Id"));
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            int obsolete=0; try(Cursor old=db.rawQuery("SELECT id FROM items WHERE type='TvChannel'",null)) { while(old.moveToNext()) if(!current.contains(old.getString(0))) obsolete++; }
            // Reconcile only a complete authoritative list, never a single page or an unsuccessful request.
            db.delete("items","type='TvChannel'",null);
            for(int i=0;i<channels.length();i++) { JSONObject item=channels.optJSONObject(i); if(item!=null) put(db,item,System.currentTimeMillis()); }
            if(obsolete>0&&!state("cacheStatus").isEmpty()) state("cacheStatus",I18n.text("Senderliste aktualisiert · bisheriger Cover-Prüfstand veraltet"));
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    /** Reattach to an opaque server cursor without rewriting an existing library. */
    synchronized void subscribeCacheChanges(JSONObject response) throws JSONException { if(cacheRetired) return;
        String cursor=response.getString("cursor");
        if(cursor.isEmpty()||cursor.length()>1024||Json.array(response,"changes").length()!=0) throw new JSONException("Invalid cache subscription");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            state("cacheCursor",cursor);
            state("cacheNeedsReview","true");
            state("cacheStatus",I18n.resource(R.string.cache_history_gap));
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    synchronized void applyCacheChanges(JSONObject response) throws JSONException { if(cacheRetired) return;
        String cursor=response.getString("cursor"); if(cursor.isEmpty()||cursor.length()>1024||response.optBoolean("reset")) throw new JSONException("Invalid cache cursor");
        JSONArray changes=response.getJSONArray("changes"); if(changes.length()>20) throw new JSONException("Oversized cache batch");
        for(int i=0;i<changes.length();i++) { JSONObject change=changes.getJSONObject(i),item=change.getJSONObject("item"); if(item.optString("Id").isEmpty()||item.optString("Type").isEmpty()) throw new JSONException("Missing cache item"); change.getJSONArray("libraries"); }
        if(changes.length()==0&&cursor.equals(state("cacheCursor"))) return;
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Map<String,JSONObject> updated=new HashMap<>();
            for(int i=0;i<changes.length();i++) {
                JSONObject change=changes.getJSONObject(i),item=change.getJSONObject("item"); String id=item.getString("Id"); JSONObject previous=item(id);
                if(!item.has("ImageTags")) item.put("ImageTags",new JSONObject()); if(!item.has("BackdropImageTags")) item.put("BackdropImageTags",new JSONArray());
                for(String badgeId:Json.coverTagIds(item)) {db.delete("state","key=?",new String[]{"cover-tags-v2-"+badgeId});db.delete("state","key=?",new String[]{"cover-tag-labels-v1-"+badgeId});}
                put(db,item,System.currentTimeMillis()); updated.put(id,item); db.delete("members","item=?",new String[]{id});
                JSONArray roots=change.getJSONArray("libraries"); for(int k=0;k<roots.length();k++) { ContentValues member=new ContentValues(); member.put("library",roots.getString(k)); member.put("item",id); member.put("stamp",System.currentTimeMillis()); db.insertWithOnConflict("members",null,member,SQLiteDatabase.CONFLICT_REPLACE); }
                if(!"TvChannel".equals(item.optString("Type"))&&(previous==null||!artworkSignature(previous).equals(artworkSignature(item)))) { ContentValues work=new ContentValues(); work.put("item",id); work.put("retry",0); db.insertWithOnConflict("cache_artwork",null,work,SQLiteDatabase.CONFLICT_REPLACE); }
            }
            if(!updated.isEmpty()) {
                List<String[]> snapshots=new ArrayList<>();
                try(Cursor rows=db.rawQuery("SELECT key,value FROM state WHERE key LIKE 'home-%' OR key LIKE 'homeGenre-%' OR key='homeRecommendations'",null)) { while(rows.moveToNext()) snapshots.add(new String[]{rows.getString(0),rows.getString(1)}); }
                for(String[] snapshot:snapshots) { try { JSONArray values=new JSONArray(snapshot[1]); if(updateSnapshot(values,updated)) state(snapshot[0],values.toString()); } catch(JSONException ignored) { } }
            }
            if(changes.length()>0) {
                JSONArray audit=new JSONArray();
                long received=System.currentTimeMillis();
                for(int i=changes.length()-1;i>=0;i--) {
                    JSONObject item=changes.getJSONObject(i).getJSONObject("item");
                    audit.put(Json.obj("receivedAt",received,"source","companion","id",item.optString("Id"),"type",item.optString("Type"),"name",item.optString("Name"),"series",item.optString("SeriesName"),"season",item.optInt("ParentIndexNumber",-1),"episode",item.optInt("IndexNumber",-1)));
                }
                try { JSONArray previous=new JSONArray(state("cacheChangeAudit")); for(int i=0;i<previous.length()&&audit.length()<50;i++) audit.put(previous.getJSONObject(i)); } catch(JSONException ignored) { }
                state("cacheChangeAudit",audit.toString());
            }
            state("cacheCursor",cursor); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static boolean updateSnapshot(JSONArray items,Map<String,JSONObject> updated) throws JSONException {
        boolean changed=false;
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i); if(item==null) continue;
            JSONObject replacement=updated.get(item.optString("Id"));
            if(replacement!=null) { JSONObject merged=new JSONObject(item.toString()); Iterator<String> keys=replacement.keys(); while(keys.hasNext()) { String key=keys.next(); merged.put(key,replacement.get(key)); } merged.remove("LowfinCompanion"); items.put(i,merged); changed=true; }
            else if(item.has("Items")) changed|=updateSnapshot(item.getJSONArray("Items"),updated);
        }
        return changed;
    }
    private static String artworkSignature(JSONObject item) { return Json.object(item,"ImageTags").toString()+Json.array(item,"BackdropImageTags")+item.optString("SeriesPrimaryImageTag")+item.optString("SeriesId"); }
    int artworkCount() { try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cache_artwork",null)) { c.moveToFirst(); return c.getInt(0); } }
    List<String> artworkPending(int limit) {
        List<String> ids=new ArrayList<>(); try(Cursor c=getReadableDatabase().rawQuery("SELECT item FROM cache_artwork WHERE retry<=? LIMIT ?",new String[]{""+System.currentTimeMillis(),""+limit})) { while(c.moveToNext()) ids.add(c.getString(0)); } return ids;
    }
    synchronized void artworkFinished(String id,boolean success) { if(cacheRetired) return; if(success) getWritableDatabase().delete("cache_artwork","item=?",new String[]{id}); else { ContentValues values=new ContentValues(); values.put("retry",System.currentTimeMillis()+6*60*60*1000L); getWritableDatabase().update("cache_artwork",values,"item=?",new String[]{id}); } }
    int count() { try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items",null)) { c.moveToFirst(); return c.getInt(0); } }
    boolean hasItems() { try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM items LIMIT 1",null)) {return c.moveToFirst();} }
}
