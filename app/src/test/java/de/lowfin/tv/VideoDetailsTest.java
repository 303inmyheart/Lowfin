package de.lowfin.tv;
import org.json.*;
import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;
public class VideoDetailsTest {
    private JSONObject source(String name,int width,int height,long size) { return Json.obj("Name",name,"Size",size,"MediaStreams",Json.arr(Json.obj("Type","Audio","Width",999,"Height",999),Json.obj("Type","Video","Width",width,"Height",height))); }
    @Test public void originalDimensionsAndLargeFileSizesUseSelectedLocale() {
        JSONObject item=Json.obj("Type","Movie","MediaSources",Json.arr(source("Film",3840,1600,12500000000L)));
        assertEquals("3840 × 1600  ·  12,5 GB",VideoDetails.describe(item,Locale.GERMAN));
        assertEquals("3840 × 1600  ·  12.5 GB",VideoDetails.describe(item,Locale.ENGLISH));
    }
    @Test public void multipleVersionsKeepSizeAndResolutionPaired() {
        JSONObject item=Json.obj("Type","Episode","MediaSources",Json.arr(source("HD",1920,1080,1000000000),source("SD",640,480,250000000)));
        assertEquals("HD  ·  1920 × 1080  ·  1 GB\nSD  ·  640 × 480  ·  250 MB",VideoDetails.describe(item,Locale.ENGLISH));
    }
    @Test public void unknownMetadataIsHiddenAndSeriesAreNotAssignedAnEpisodeSize() {
        assertEquals("",VideoDetails.describe(Json.obj("Type","Movie"),Locale.ENGLISH));
        assertEquals("",VideoDetails.describe(Json.obj("Type","Series","MediaSources",Json.arr(source("",1920,1080,1000))),Locale.ENGLISH));
        assertEquals("",VideoDetails.fileSize(-1,Locale.ENGLISH));
    }
}
