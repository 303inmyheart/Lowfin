package de.lowfin.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiscoveryHistoryTest {
    @Test public void historyIsMovieOnlyBoundedAndCopiedWithoutPendingRequestsOrViews() {
        DiscoveryHistory history=new DiscoveryHistory();assertNull(history.current());
        history.add(Json.obj("Id","person","Type","Person"));assertNull(history.current());
        for(int i=0;i<25;i++) history.add(Json.obj("Id","movie"+i,"Type","Movie"));
        assertEquals(20,history.items.size());assertEquals("movie5",history.items.get(0).optString("Id"));assertEquals("movie24",history.current().optString("Id"));
        history.back();history.back();assertEquals("movie22",history.current().optString("Id"));
        history.loading=true;history.observer=()->fail("Observer must not be copied");DiscoveryHistory snapshot=new DiscoveryHistory(history);
        assertFalse(snapshot.loading);assertNull(snapshot.observer);assertEquals("movie22",snapshot.current().optString("Id"));
        snapshot.add(Json.obj("Id","fresh","Type","Movie"));assertEquals("fresh",snapshot.current().optString("Id"));assertEquals("movie22",history.current().optString("Id"));
    }
    @Test public void duplicateCurrentFilmDoesNotGrowHistoryAndBackIsBounded() {
        DiscoveryHistory history=new DiscoveryHistory();history.add(Json.obj("Id","one","Type","Movie","Name","Old"));history.add(Json.obj("Id","one","Type","Movie","Name","New"));
        assertEquals(1,history.items.size());assertFalse(history.canBack());assertEquals("New",history.back().optString("Name"));
    }
}
