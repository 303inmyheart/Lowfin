package de.lowfin.tv;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class LibraryTest {
    private Library db;
    @Before public void setup() { db=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString()); }
    @After public void close() { db.close(); }
    @Test public void fortyFiveSeasonsSortNumericallyAndRemainIndividuallyAddressable() {
        JSONArray seasons=new JSONArray(); for(int i=45;i>=1;i--) seasons.put(Json.obj("Id","season"+i,"Type","Season","ParentId","series","SeriesId","series","IndexNumber",i,"Name","Staffel "+i));
        db.put(seasons,100);
        List<JSONObject> all=db.query("type='Season' AND parent=?",new String[]{"series"},"episode",500,0);
        assertEquals(45,all.size()); for(int i=0;i<45;i++) assertEquals(i+1,all.get(i).optInt("IndexNumber"));
        assertEquals("Staffel 40",db.item("season40").optString("Name"));
    }
    @Test public void partialResponsesKeepPreviouslyCachedOverviewParentAndStreams() {
        JSONObject original=Json.obj("Id","ep","Type","Episode","Name","Pilot","ParentId","season","SeriesId","show","Overview","A full description","MediaStreams",Json.arr(Json.obj("Index",2,"Type","Audio")));
        db.put(original); db.put(Json.obj("Id","ep","Type","Episode","Name","Pilot","UserData",Json.obj("PlaybackPositionTicks",600000000L)));
        JSONObject cached=db.item("ep"); assertEquals("A full description",cached.optString("Overview"));
        assertEquals("season",cached.optString("ParentId")); assertEquals(1,cached.optJSONArray("MediaStreams").length()); assertEquals(60000L,Json.position(cached));
    }
    @Test public void onlySuccessfulSyncRemovesDeletedItems() {
        db.put(Json.arr(Json.obj("Id","old","Type","Movie")),100);
        db.put(Json.arr(Json.obj("Id","new","Type","Movie")),200);
        assertEquals(2,db.count()); // A stopped/failed sync never calls completeSync.
        db.completeSync(200); assertNull(db.item("old")); assertNotNull(db.item("new")); assertEquals("200",db.state("lastSync"));
    }
    @Test public void nextEpisodeCrossesSeasonBoundariesInOrder() {
        JSONObject a=episode("a",1,9),b=episode("b",2,1),c=episode("c",2,2);
        db.put(a); db.put(b); db.put(c);
        assertEquals("b",db.next(a).optString("Id")); assertEquals("c",db.next(b).optString("Id")); assertNull(db.next(c));
    }
    private JSONObject episode(String id,int season,int episode) { return Json.obj("Id",id,"Type","Episode","SeriesId","show","ParentIndexNumber",season,"IndexNumber",episode); }
    @Test public void pagedLibraryDoesNotLoadEntireDataset() {
        JSONArray items=new JSONArray(); for(int i=0;i<1500;i++) items.put(Json.obj("Id","m"+i,"Type","Movie","Name",String.format("Film %04d",i)));
        db.put(items,100); assertEquals(1500,db.count()); List<JSONObject> page=db.browse("Movie",null,null,60,60);
        assertEquals(60,page.size()); assertEquals("Film 0060",page.get(0).optString("Name"));
    }
    @Test public void percentAndUnderscoreInSearchAreLiteral() {
        db.put(Json.obj("Id","a","Type","Movie","Name","100%_Film")); db.put(Json.obj("Id","b","Type","Movie","Name","10000Film"));
        assertEquals(1,db.browse("Movie",null,"%_",0,60).size());
    }
    @Test public void usersHaveSeparateDatabases() {
        Library other=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString());
        try { db.put(Json.obj("Id","private","Type","Movie")); assertNull(other.item("private")); } finally { other.close(); }
    }
    @Test public void libraryMembershipIncludesNestedFoldersAndMixedTypes() {
        JSONArray items=Json.arr(Json.obj("Id","nested","Type","Movie","ParentId","folder","Name","Film"),Json.obj("Id","show","Type","Series","ParentId","folder","Name","Serie"));
        db.put(items,100); db.link("my-library",items,100);
        assertEquals(2,db.browse("Movie,Series","my-library",null,0,60).size());
        assertEquals(0,db.browse("Movie,Series","other-library",null,0,60).size());
        db.link("my-library",Json.arr(items.optJSONObject(0)),200); db.completeLinks("my-library",200);
        assertEquals(1,db.browse("Movie,Series","my-library",null,0,60).size());
    }
    @Test public void randomFilmRespectsMovieTypeAndNestedLibraryMembership() {
        db.put(Json.obj("Id","film","Type","Movie","ParentId","folder")); db.put(Json.obj("Id","series","Type","Series","ParentId","folder"));
        db.link("library",Json.arr(db.item("film"),db.item("series")),100);
        assertEquals("film",db.randomMovie("library").optString("Id")); assertNull(db.randomMovie("other"));
    }
    @Test public void genreFiltersMatchExactGenreNamesAndPreserveRichMetadata() {
        db.put(Json.obj("Id","movie","Type","Movie","Name","Film","Genres",Json.arr("Drama","Science-Fiction")));
        db.put(Json.obj("Id","series","Type","Series","Name","Serie","Genres",Json.arr("Drama")));
        db.put(Json.obj("Id","episode","Type","Episode","Genres",Json.arr("Drama")));
        assertEquals(2,db.genres().size()); assertEquals(2,db.genreItems("Drama",0,60).size()); assertEquals(1,db.genreItems("Science-Fiction",0,60).size()); assertEquals(0,db.genreItems("Dram",0,60).size());
        db.put(Json.obj("Id","movie","Name","Updated")); assertEquals(1,db.genreItems("Science-Fiction",0,60).size());
        db.put(Json.obj("Id","movie","Genres",Json.arr("Comedy"))); assertEquals(0,db.genreItems("Science-Fiction",0,60).size());
    }
    @Test public void genreUpgradePreservesAlreadyCachedItems() {
        String name=db.getDatabaseName(); db.put(Json.obj("Id","movie","Type","Movie","Genres",Json.arr("Drama")));
        android.database.sqlite.SQLiteDatabase database=db.getWritableDatabase(); database.execSQL("DROP TABLE genres"); database.setVersion(2); db.close();
        Library upgraded=new Library(RuntimeEnvironment.getApplication(),name.substring("library-".length(),name.length()-3));
        try { assertNotNull(upgraded.item("movie")); while(upgraded.backfillGenres()) {} assertEquals(1,upgraded.genreItems("Drama",0,60).size()); } finally { upgraded.close(); }
    }
    @Test public void upgradeDoesNotScanItemsAndGenreBackfillRunsInBoundedBatches() {
        String name=db.getDatabaseName(); JSONArray items=new JSONArray();
        for(int i=0;i<61;i++) items.put(Json.obj("Id",String.format("m%03d",i),"Type","Movie","Genres",Json.arr("Drama")));
        items.put(Json.obj("Id","ep","Type","Episode","Genres",Json.arr("Drama"))); db.put(items,100);
        db.getWritableDatabase().execSQL("DROP TABLE genres"); db.getWritableDatabase().setVersion(2); db.close();
        Library upgraded=new Library(RuntimeEnvironment.getApplication(),name.substring(8,name.length()-3));
        try {
            assertEquals(62,upgraded.count()); assertEquals(0,upgraded.genreItems("Drama",0,100).size());
            assertTrue(upgraded.backfillGenres()); assertEquals(50,upgraded.genreItems("Drama",0,100).size());
            assertTrue(upgraded.backfillGenres()); assertEquals(61,upgraded.genreItems("Drama",0,100).size());
            assertFalse(upgraded.backfillGenres());
        } finally { upgraded.close(); }
    }

}
