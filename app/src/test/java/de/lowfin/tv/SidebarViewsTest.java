package de.lowfin.tv;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SidebarViewsTest {
    @Test public void artworkRefreshDoesNotRequireNewSidebarButtons() throws Exception {
        JSONObject old=Json.obj("Id","films","Name","Movies","CollectionType","movies","ImageTags",Json.obj("Thumb","old"));JSONObject fresh=new JSONObject(old.toString());fresh.put("ImageTags",Json.obj("Thumb","new"));
        assertTrue(SidebarViews.sameButtons(Arrays.asList(old),Arrays.asList(fresh)));assertFalse(SidebarViews.sameData(Arrays.asList(old),Arrays.asList(fresh)));assertTrue(SidebarViews.sameData(Arrays.asList(old),Arrays.asList(new JSONObject(old.toString()))));
    }
    @Test public void renamedReorderedAddedOrReroutedLibrariesStillRebuild() throws Exception {
        JSONObject a=Json.obj("Id","a","Name","A","CollectionType","movies"),b=Json.obj("Id","b","Name","B","CollectionType","tvshows");assertFalse(SidebarViews.sameButtons(Arrays.asList(a,b),Arrays.asList(b,a)));assertFalse(SidebarViews.sameButtons(Arrays.asList(a),Arrays.asList(a,b)));
        for(String field:new String[]{"Id","Name","CollectionType"}){JSONObject changed=new JSONObject(a.toString());changed.put(field,"changed");assertFalse(SidebarViews.sameButtons(Arrays.asList(a),Arrays.asList(changed)));}
    }
    @Test public void idNormalizationKeepsStrictGuidValidationWithoutRegexCompilation() {
        String lower="0123456789abcdef0123456789abcdef";assertEquals(lower,Json.normalizedId("01234567-89AB-CDEF-0123-456789ABCDEF"));assertSame(lower,Json.normalizedId(lower));for(String invalid:new String[]{"",lower+"0",lower.substring(1),lower.substring(1)+"g",lower.substring(1)+"\n"})assertEquals("",Json.normalizedId(invalid));
    }
}
