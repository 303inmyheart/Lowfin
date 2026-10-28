package de.lowfin.tv;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class LibraryResumeTest {
    @Test public void candidateLookupMatchesOriginalJoinAcrossScopesPagesAndManagedStates() throws Exception {
        try(Library db=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString())) {
            for(int i=0;i<120;i++) {
                db.put(Json.arr(Json.obj("Id","item-"+i,"Type",i%2==0?"Movie":"Episode","Name","Item "+i,"ParentId",i%3==0?"a":"b","RunTimeTicks",100000000000L,"UserData",Json.obj("Played",i%4==0,"PlaybackPositionTicks",i%5==0?10000000000L:0))),1000+i);
                if(i%7==0) db.companionState("item-"+i,Json.obj("active",i%2==0,"revision",1,"sequence",0,"hasResume",true,"resumeTicks",15000000000L,"played",i%4==0,"resumeVisible",i%3!=0,"updatedAt",java.time.Instant.ofEpochMilli(2000+i).toString()));
            }
            // A state without a cached item must not create a metadata card.
            db.companionState("missing",Json.obj("active",true,"revision",1,"hasResume",true,"resumeTicks",100));
            for(boolean enabled:new boolean[]{false,true}) {
                db.companionAvailable=enabled;
                for(String scope:new String[]{null,"a","b"}) for(int offset:new int[]{0,1,7}) {
                    String original=enabled?"((position>0 AND played=0) OR (companion_has_resume=1 AND companion_resume>0)) AND COALESCE(companion_visible,1)=1":"position>0 AND played=0";
                    String order=enabled?"CASE WHEN companion_has_resume=1 AND companion_stamp>0 THEN companion_stamp ELSE stamp END DESC":"stamp DESC";
                    String extra=scope==null?null:"parent=?";String[] args=scope==null?null:new String[]{scope};
                    List<JSONObject> expected=db.query(original+(extra==null?"":" AND ("+extra+")"),args,order,9,offset);expected.removeIf(item->!Json.inContinue(item));
                    List<JSONObject> actual=db.resumable(extra,args,9,offset);
                    assertEquals("scope="+scope+", offset="+offset+", companion="+enabled,new JSONArray(expected).toString(),new JSONArray(actual).toString());
                }
            }
        }
    }
    @Test public void openedReadsDoNotWaitForWriterHelperMonitorAndClosedHandleCanReopen() throws Exception {
        try(Library db=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString())) {
            db.put(Json.obj("Id","resume","Type","Movie","Name","Resume","UserData",Json.obj("Played",false,"PlaybackPositionTicks",50000000L)));
            java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.CountDownLatch locked=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Future<?> writer=workers.submit(()-> { synchronized(db) {locked.countDown();try {release.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}} });
            try {
                assertTrue(locked.await(2,java.util.concurrent.TimeUnit.SECONDS));
                java.util.concurrent.Future<List<JSONObject>> reader=workers.submit(()->db.resumable(null,null,18,0));
                assertEquals("resume",reader.get(2,java.util.concurrent.TimeUnit.SECONDS).get(0).optString("Id"));
            } finally {release.countDown();writer.get(2,java.util.concurrent.TimeUnit.SECONDS);workers.shutdownNow();}
            android.database.sqlite.SQLiteDatabase old=db.getReadableDatabase();db.close();assertFalse(old.isOpen());
            assertEquals("Resume",db.item("resume").optString("Name"));assertNotSame(old,db.getReadableDatabase());
        }
    }
    @Test public void candidatePlanUsesNativeResumeIndexAndPrimaryKeyMetadataLookup() {
        try(Library db=new Library(RuntimeEnvironment.getApplication(),UUID.randomUUID().toString())) {
            StringBuilder plan=new StringBuilder();
            try(android.database.Cursor c=db.getReadableDatabase().rawQuery("EXPLAIN QUERY PLAN SELECT items.json FROM items LEFT JOIN companion ON items.id=companion.companion_item WHERE id IN (SELECT id FROM items WHERE position>0 AND played=0 UNION SELECT companion_item FROM companion WHERE companion_has_resume=1 AND companion_resume>0) AND COALESCE(companion_visible,1)=1",null)) {
                while(c.moveToNext()) plan.append(c.getString(3)).append('\n');
            }
            assertTrue(plan.toString(),plan.toString().contains("library_resume"));
            assertTrue(plan.toString(),plan.toString().contains("sqlite_autoindex_items_1"));
        }
    }
}
