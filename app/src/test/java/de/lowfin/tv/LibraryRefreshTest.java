package de.lowfin.tv;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class LibraryRefreshTest {
    private long scalar(SQLiteDatabase db,String sql) {try(Cursor c=db.rawQuery(sql,null)){assertTrue(c.moveToFirst());return c.getLong(0);}}
    @Test public void unchangedShortDtosOnlyTouchFreshnessWhileRealChangesRetainAllMetadata() throws Exception {
        try(Library library=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString())) {
            SQLiteDatabase db=library.getWritableDatabase();db.execSQL("CREATE TABLE inserted (id TEXT)");db.execSQL("CREATE TRIGGER item_insert AFTER INSERT ON items BEGIN INSERT INTO inserted VALUES(NEW.id); END");
            JSONObject full=Json.obj("Id","film","Type","Movie","Name","Film","Genres",Json.arr("Horror","Drama"),"MediaSources",Json.arr(Json.obj("Size",9007199254740993L)),"UserData",Json.obj("PlaybackPositionTicks",10L,"Played",false));
            library.put(Json.arr(full),1);long row=scalar(db,"SELECT rowid FROM items WHERE id='film'");String saved;try(Cursor c=db.rawQuery("SELECT json FROM items WHERE id='film'",null)){assertTrue(c.moveToFirst());saved=c.getString(0);}
            JSONObject shortDto=Json.obj("Name","Film","Id","film","Type","Movie","Genres",Json.arr("Horror","Drama"),"UserData",Json.obj("Played",false,"PlaybackPositionTicks",10L),"LowfinCompanion",Json.obj("active",true));
            for(int i=2;i<=101;i++) library.put(Json.arr(shortDto),i);
            assertEquals("No replacement INSERTs for identical refreshes",1,scalar(db,"SELECT COUNT(*) FROM inserted"));assertEquals(row,scalar(db,"SELECT rowid FROM items WHERE id='film'"));assertEquals(101,scalar(db,"SELECT stamp FROM items WHERE id='film'"));
            try(Cursor c=db.rawQuery("SELECT json FROM items WHERE id='film'",null)){assertTrue(c.moveToFirst());assertEquals(saved,c.getString(0));}
            assertEquals(2,scalar(db,"SELECT COUNT(*) FROM genres"));
            shortDto.put("UserData",Json.obj("PlaybackPositionTicks",200L,"Played",true));shortDto.put("Genres",Json.arr("Thriller"));library.put(Json.arr(shortDto),102);
            JSONObject changed=library.item("film");assertEquals(200L,Json.object(changed,"UserData").getLong("PlaybackPositionTicks"));assertTrue(Json.played(changed));assertFalse(changed.has("LowfinCompanion"));assertEquals(9007199254740993L,changed.getJSONArray("MediaSources").getJSONObject(0).getLong("Size"));assertEquals(2,scalar(db,"SELECT COUNT(*) FROM inserted"));assertEquals(1,scalar(db,"SELECT COUNT(*) FROM genres"));assertEquals("Thriller",changed.getJSONArray("Genres").getString(0));
            shortDto.put("Overview",JSONObject.NULL);library.put(shortDto);assertTrue(library.item("film").has("Overview"));assertTrue(library.item("film").isNull("Overview"));
        }
    }
    @Test public void batchRowsPreserveBoundariesDuplicatesFallbacksAndCompanionWithoutMutatingSnapshots() throws Exception {
        try(Library library=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString())) {
            JSONArray stored=new JSONArray();for(int i=0;i<150;i++) stored.put(Json.obj("Id","film"+i,"Type","Movie","Name","Current "+i));library.put(stored,1);
            library.companionState("film0",Json.obj("active",true,"revision",1,"hasResume",true,"resumeTicks",123400000L,"played",false));library.companionAvailable=true;
            List<List<JSONObject>> rows=new ArrayList<>();for(int row=0;row<10;row++){List<JSONObject> values=new ArrayList<>();for(int i=0;i<30;i++)values.add(Json.obj("Id","film"+(row*15+i),"Name","Old"));rows.add(values);}rows.add(Collections.emptyList());JSONObject missing=Json.obj("Id","missing","Name","Fallback");rows.add(Arrays.asList(missing,rows.get(0).get(0)));
            List<List<JSONObject>> fresh=library.latestRows(rows);assertEquals(rows.size(),fresh.size());
            for(int r=0;r<rows.size();r++){assertEquals(rows.get(r).size(),fresh.get(r).size());for(int i=0;i<rows.get(r).size();i++){JSONObject original=rows.get(r).get(i),current=fresh.get(r).get(i);assertEquals(original.optString("Id"),current.optString("Id"));if(current.optString("Id").equals("missing")||Integer.parseInt(current.optString("Id").substring(4))>=150)assertSame(original,current);else assertTrue(current.getString("Name").startsWith("Current "));}}
            assertEquals(12340,Json.position(fresh.get(0).get(0)));assertEquals(12340,Json.position(fresh.get(11).get(1)));assertEquals("Old",rows.get(0).get(0).getString("Name"));assertFalse(rows.get(0).get(0).has("LowfinCompanion"));assertTrue(library.latestRows(Collections.emptyList()).isEmpty());
        }
    }
    @Test public void baselineUsesExistenceAndStillRecognizesEmptyAndUninitializedLibraries() {
        try(Library library=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString())) {
            assertFalse(library.hasItems());assertTrue(CacheChanges.needsBaseline(library));library.state("lastSync","1");assertTrue(CacheChanges.needsBaseline(library));library.put(Json.obj("Id","seed","Type","Movie"));assertTrue(library.hasItems());assertFalse(CacheChanges.needsBaseline(library));library.state("lastSync","");assertTrue(CacheChanges.needsBaseline(library));
        }
    }
}
