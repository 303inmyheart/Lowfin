package de.lowfin.tv;

import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class ArtworkTest {
    @Test public void postersUseTwoByThreeAndCartoonsUseSixteenByNine() {
        for(String kind:new String[]{"Movie","Series","Season","BoxSet"}) assertEquals(2.0/3,Artwork.ratio(Json.obj("Type",kind,"PrimaryImageAspectRatio",1.78),false),.0001);
        assertEquals(16.0/9,Artwork.ratio(Json.obj("Type","Episode","PrimaryImageAspectRatio",4.0/3),false),.0001);
    }
    @Test public void episodeKeepsLandscapeFrameWithPosterFallback() {
        JSONObject item=Json.obj("Id","episode","Type","Episode","SeriesId","series","SeriesPrimaryImageTag","poster");
        assertEquals(16.0/9,Artwork.ratio(item,true),.0001);
    }
    @Test public void landscapeArtworkIsChosenOnlyWhenItExists() {
        JSONObject movie=Json.obj("Id","movie","Type","Movie","ImageTags",Json.obj("Primary","poster","Thumb","thumb"));
        assertEquals("Thumb",Artwork.type(movie,true)); assertEquals("Primary",Artwork.type(movie,false));
        assertEquals(2.0/3,Artwork.ratio(movie,true),.0001);
        Api api=new Api(new Account("http://localhost/jellyfin","user","token","name","device"));
        assertTrue(api.image(movie,240,true).contains("/Images/Thumb?")); assertTrue(api.image(movie,240,false).contains("/Images/Primary?"));
    }
    @Test public void backdropsAndNullOrBadRatiosAreHandledSafely() {
        assertEquals("Backdrop",Artwork.type(Json.obj("Type","Movie","BackdropImageTags",Json.arr("backdrop")),true));
        assertEquals(16.0/9,Artwork.ratio(Json.obj("Type","Episode","PrimaryImageAspectRatio",0),false),.0001);
    }
    @Test public void backdropNeverSubstitutesAPosterAndUsesParentWhenNeeded() {
        assertNull(Artwork.backdrop(Json.obj("Id","movie","ImageTags",Json.obj("Primary","poster"))));
        JSONObject parent=Artwork.backdrop(Json.obj("Id","episode","ParentBackdropItemId","series","ParentBackdropImageTags",Json.arr("tag")));
        assertEquals("series",parent.optString("Id")); assertEquals("Backdrop",Artwork.type(parent,true));
        assertTrue(Artwork.crop(Json.obj("Type","Season"),false));
        assertTrue(Artwork.crop(Json.obj("Type","Episode","PrimaryImageAspectRatio",4.0/3),false));
    }
    @Test public void everyCardFrameUsesAPosterOrThumbnailRatioRegardlessOfServerRatios() {
        for(String kind:new String[]{"Movie","Series","Season","BoxSet","Unknown","Episode","Video","TvChannel"}) for(double ratio:new double[]{0,1,4.0/3,2.4}) {
            double expected=kind.equals("Episode")||kind.equals("Video")||kind.equals("TvChannel")?16.0/9:2.0/3;
            assertEquals(expected,Artwork.ratio(Json.obj("Type",kind,"PrimaryImageAspectRatio",ratio),false),.0001);
        }
    }

}
