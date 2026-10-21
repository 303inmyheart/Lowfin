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
    private Library db;private String scope;
    @Before public void setup() { scope=UUID.randomUUID().toString();db=new Library(RuntimeEnvironment.getApplication(),scope); }
    @After public void close() { db.close(); }
    @Test public void precacheCompletionRequiresArtworkSuccessPersistsAndOnlyClearingResetsIt() {
        assertFalse(db.precacheComplete());db.state("lastSync","123");db.finishPrecacheArtwork();assertFalse(db.precacheComplete());
        db.put(Json.obj("Id","cover","Type","Movie"));db.checkpointSync(0,"cursor",false);db.state("precacheBaselinePending","true");
        db.finishPrecacheArtwork();assertFalse(db.precacheComplete());db.artworkFinished("cover",false);db.finishPrecacheArtwork();assertFalse(db.precacheComplete());
        db.artworkFinished("cover",true);db.finishPrecacheArtwork();assertTrue(db.precacheComplete());
        db.close();db=new Library(RuntimeEnvironment.getApplication(),scope);assertTrue(db.precacheComplete());
        db.state("cacheNeedsReview","true");db.state("cacheStatus","An error or new updates");assertTrue(db.precacheComplete());
        db.resetPrecacheCompletion();assertFalse(db.precacheComplete());db.state("precacheBaselinePending","true");db.finishPrecacheArtwork();assertTrue(db.precacheComplete());
        db.clearAndRetirePrecache();assertFalse(db.precacheComplete());
    }
    @Test public void clearingPrecacheKeepsRatingsAndCompanionButFencesLateWritersAndAllowsFreshGeneration() throws Exception {
        JSONObject film=Json.obj("Id","cached","Type","Movie","Name","Cached","Genres",Json.arr("Horror"));db.put(film);db.link("library",Json.arr(film),1);
        db.state("lastSync","123");db.state("cacheCursor","cursor");db.state("home-0","snapshot");db.state("person-actor","person");db.state("ratings-values-cached","ratings");db.state("companion-ratings-cached-provider","provider-ratings");db.companionState("cached",Json.obj("revision",1,"active",true,"resumeTicks",12340000));
        db.clearAndRetirePrecache();assertEquals(0,db.count());assertEquals(0,db.artworkCount());assertTrue(db.genres().isEmpty());assertEquals("",db.state("lastSync"));assertEquals("",db.state("cacheCursor"));assertEquals("",db.state("home-0"));assertEquals("",db.state("person-actor"));assertEquals("ratings",db.state("ratings-values-cached"));assertEquals(12340000,db.companionState("cached").optLong("resumeTicks"));
        db.put(film);db.state("lastSync","late");db.link("library",Json.arr(film),2);db.replaceChannels(Json.arr(Json.obj("Id","channel","Type","TvChannel")));db.checkpointSync(3,"late-cursor",false);assertEquals(0,db.count());assertEquals("",db.state("lastSync"));assertEquals("",db.state("cacheCursor"));assertFalse(db.backfillGenres());
        Library fresh=new Library(RuntimeEnvironment.getApplication(),scope);try { fresh.put(film);fresh.state("cacheCursor","fresh");db.completeSync(Long.MAX_VALUE);db.state("cacheCursor","old");assertNotNull(fresh.item("cached"));assertEquals("fresh",fresh.state("cacheCursor"));assertEquals("ratings",fresh.state("ratings-values-cached"));db.state("ratings-values-cached","late-rating");assertEquals("late-rating",fresh.state("ratings-values-cached")); } finally {fresh.close();}
    }
    @Test public void companionBatchesPreserveOrderDuplicatesVisibilityAndOriginalMetadataAcrossChunks() {
        db.companionAvailable=true;List<JSONObject> source=new ArrayList<>();
        for(int i=0;i<260;i++) { JSONObject item=Json.obj("Id","batch-"+i,"Type","Episode","Name","Episode "+i,"UserData",Json.obj("Played",false));source.add(item);if(i%2==0) db.companionState(item.optString("Id"),Json.obj("revision",1,"sequence",0,"active",true,"hasResume",true,"resumeVisible",i%4!=0,"resumeTicks",600000000L+i)); }
        source.add(source.get(2));List<JSONObject> batch=db.withCompanionItems(source);assertEquals(261,batch.size());
        for(int i=0;i<source.size();i++) { JSONObject original=source.get(i),value=batch.get(i);assertEquals(original.optString("Id"),value.optString("Id"));assertEquals(db.withCompanion(original).toString(),value.toString());assertFalse(original.has("LowfinCompanion")); }
        assertEquals(batch.get(2).toString(),batch.get(260).toString());assertTrue(db.withCompanionItems(Collections.emptyList()).isEmpty());
        Library other=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString());try { other.companionAvailable=true;assertFalse(other.withCompanionItems(source).get(2).has("LowfinCompanion")); } finally { other.close(); }
    }
    @Test public void companionBatchStripsOldDecorationsWhenPluginIsUnavailableWithoutChangingSources() {
        JSONObject original=Json.obj("Id","film","Type","Movie","Name","Film","LowfinCompanion",Json.obj("active",true,"resumeTicks",600000000L));db.companionAvailable=false;List<JSONObject> result=db.withCompanionItems(Arrays.asList(original,original));assertFalse(result.get(0).has("LowfinCompanion"));assertEquals("film",result.get(1).optString("Id"));assertTrue(original.has("LowfinCompanion"));
    }
    @Test public void RandomSelectionKeepsGenreAndLibraryScopeAndNeverReturnsSeries() {
        db.put(Json.obj("Id","a","Type","Movie","Name","A","ParentId","lib-a","Genres",Json.arr("Horror")));
        db.put(Json.obj("Id","b","Type","Movie","Name","B","ParentId","lib-b","Genres",Json.arr("Horror")));
        db.put(Json.obj("Id","c","Type","Movie","Name","C","ParentId","lib-a","Genres",Json.arr("Comedy")));
        db.put(Json.obj("Id","s","Type","Series","Name","Series","ParentId","lib-a","Genres",Json.arr("Drama")));
        assertEquals(3,db.randomMovies(null,null,80).size()); assertEquals(2,db.randomMovies("lib-a",null,80).size());
        assertEquals("a",db.randomMovies("lib-a","Horror",80).get(0).optString("Id")); assertEquals(1,db.randomMovies("lib-a","Horror",80).size());
        assertTrue(db.randomMovies("lib-a","Missing",80).isEmpty());assertEquals(Arrays.asList("Comedy","Horror"),db.movieGenres("lib-a"));
    }
    @Test public void LetterJumpCountsWholeScopedLibraryIncludingLinkedItems() {
        JSONArray items=new JSONArray();for(int i=0;i<73;i++) items.put(Json.obj("Id","a"+i,"Type","Movie","Name",String.format("Alpha %03d",i),"ParentId","films"));
        items.put(Json.obj("Id","m","Type","Movie","Name","Mango","ParentId","folder"));items.put(Json.obj("Id","other","Type","Movie","Name","Beta","ParentId","other-library"));items.put(Json.obj("Id","series","Type","Series","Name","Before","ParentId","films"));db.put(items,100);db.link("films",Json.arr(Json.obj("Id","m")),100);
        assertEquals(73,db.letterOffset("Movie","films","M"));assertEquals(74,db.letterOffset("Movie",null,"M"));assertEquals(-1,db.letterOffset("Movie","films","Z"));assertEquals(0,db.letterOffset("Movie","films","#"));assertThrows(IllegalArgumentException.class,()->db.letterOffset("Movie","films","M'"));
    }
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

    @Test public void UnchangedAndChangedGenreUpdatesKeepCorrectMembership() {
        db.put(Json.obj("Id","film","Type","Movie","Name","Film","Genres",Json.arr("Horror")));
        db.put(Json.obj("Id","film","Type","Movie","Name","Renamed","Genres",Json.arr("Horror")));
        assertEquals(1,db.genreItems("Horror",0,20).size());
        db.put(Json.obj("Id","film","Type","Movie","Genres",Json.arr("Thriller")));
        assertTrue(db.genreItems("Horror",0,20).isEmpty()); assertEquals(1,db.genreItems("Thriller",0,20).size());
        db.put(Json.obj("Id","film","Type","Movie","UserData",Json.obj("Played",true)));
        assertEquals(1,db.genreItems("Thriller",0,20).size());
    }
    @Test public void BrowseUsesCaseInsensitiveIndexWithoutTemporarySort() {
        db.put(Json.obj("Id","film","Type","Movie","Name","Film"));
        boolean indexed=false;
        try(android.database.Cursor cursor=db.getReadableDatabase().rawQuery("EXPLAIN QUERY PLAN SELECT json FROM items WHERE type='Movie' ORDER BY name COLLATE NOCASE LIMIT 60",null)) {
            while(cursor.moveToNext()) { String plan=cursor.getString(3); assertFalse(plan.contains("TEMP B-TREE")); indexed|=plan.contains("library_type_nocase"); }
        }
        assertTrue(indexed);
    }
    @Test public void preCompanionSchemaFourUpgradesWithoutLosingLibraryRatingsOrProgress() {
        String selected="upgrade-schema-four-"+UUID.randomUUID();Library old=new Library(RuntimeEnvironment.getApplication(),selected);
        old.put(Json.obj("Id","film","Type","Movie","Name","Film","Genres",Json.arr("Horror"),"UserData",Json.obj("PlaybackPositionTicks",1230000000L)));
        old.link("library",Json.arr(old.item("film")),100);old.state("ratings-values-film","keep-ratings");old.state("lastSync","12345");
        android.database.sqlite.SQLiteDatabase sqlite=old.getWritableDatabase();sqlite.execSQL("DROP TABLE companion");sqlite.execSQL("DROP TABLE cache_artwork");
        for(String index:new String[]{"library_type_nocase","library_resume","members_item"}) sqlite.execSQL("DROP INDEX "+index);
        sqlite.setVersion(4);old.close();Library upgraded=new Library(RuntimeEnvironment.getApplication(),selected);
        try {
            assertEquals(8,upgraded.getReadableDatabase().getVersion());assertEquals(1,upgraded.browse("Movie","library",null,0,20).size());assertEquals(123000,Json.position(upgraded.item("film")));
            assertEquals("keep-ratings",upgraded.state("ratings-values-film"));assertEquals("12345",upgraded.state("lastSync"));assertEquals(1,upgraded.genreItems("Horror",0,20).size());assertEquals(0,upgraded.artworkCount());
            upgraded.companionState("film",Json.obj("revision",1,"sequence",1,"resumeVisible",false));assertFalse(upgraded.companionState("film").optBoolean("resumeVisible",true));
        } finally { upgraded.close(); }
    }
    @Test public void SchemaSevenUpgradePreservesItemsMembershipAndPluginCursor() {
        String scope="upgrade-performance-"+UUID.randomUUID();
        Library old=new Library(RuntimeEnvironment.getApplication(),scope);
        old.put(Json.obj("Id","film","Type","Movie","Name","Film","Genres",Json.arr("Horror")));
        old.link("library",Json.arr(old.item("film")),100); old.state("cacheCursor","keep-cursor");
        android.database.sqlite.SQLiteDatabase sqlite=old.getWritableDatabase();
        for(String index:new String[]{"library_type_nocase","library_resume","members_item","artwork_retry"}) sqlite.execSQL("DROP INDEX "+index);
        sqlite.setVersion(7); old.close();
        Library upgraded=new Library(RuntimeEnvironment.getApplication(),scope);
        try { assertEquals(8,upgraded.getReadableDatabase().getVersion()); assertEquals("keep-cursor",upgraded.state("cacheCursor")); assertEquals(1,upgraded.browse("Movie","library",null,0,20).size()); assertEquals(1,upgraded.genreItems("Horror",0,20).size()); }
        finally { upgraded.close(); }
    }
}
