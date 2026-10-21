package de.lowfin.tv;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RatingsTest {
    @Test public void omdbPreservesEachRealRatingScaleAndIgnoresUnavailableValues() {
        JSONObject scores=Ratings.parseOmdb(Json.obj("imdbRating","7.8","Metascore","N/A","Ratings",Json.arr(Json.obj("Source","Rotten Tomatoes","Value","91%"))));
        assertEquals(7.8,scores.optDouble("imdb"),.001); assertEquals(91,scores.optDouble("tomatoes"),.001); assertFalse(scores.has("metacritic"));
    }
    @Test public void mdblistParsesCriticAudienceAndOtherSourcesWithoutInventingZeroScores() {
        JSONObject scores=Ratings.parseMdb(Json.obj("score",83,"ratings",Json.arr(Json.obj("source","imdb","value",8.1),Json.obj("source","tomatoesaudience","value",94),Json.obj("source","letterboxd","value",4.2),Json.obj("source","tmdb","value",JSONObject.NULL))));
        assertEquals(83,scores.optDouble("mdblist"),.001); assertEquals(94,scores.optDouble("tomatoesaudience"),.001); assertEquals(4.2,scores.optDouble("letterboxd"),.001); assertFalse(scores.has("tmdb"));
    }
    @Test public void providerIdsMatchCaseInsensitively() { assertEquals("tt1234",Ratings.providerId(Json.obj("IMDB","tt1234"),"Imdb")); }
    @Test public void explicitScalesPreventPercentAndTenPointConfusion() { assertEquals("7,8/10",Ratings.format("imdb",7.8)); assertEquals("91%",Ratings.format("tomatoes",91)); assertEquals("83/100",Ratings.format("metacritic",83)); assertEquals("4,2/5",Ratings.format("letterboxd",4.2)); }
    @Test public void invalidNumbersAreNotShownAsRealRatings() { assertEquals(0,Ratings.parseOmdb(Json.obj("imdbRating",25,"Metascore",-1)).length()); }
    @Test public void lighterFormattingMatchesPreviousRoundingAcrossScoresAndBoundaries() {
        for(String source:Ratings.IDS) {
            boolean decimal=source.equals("imdb")||source.equals("tmdb")||source.equals("letterboxd");
            String suffix=decimal?(source.equals("letterboxd")?"/5":"/10"):source.equals("metacritic")||source.equals("mdblist")?"/100":"%";
            for(int i=0;i<=2000;i++) {
                double value=i/20.0;
                for(double score:new double[]{Math.nextDown(value),value,Math.nextUp(value)})
                    assertEquals(source+" "+score,String.format(java.util.Locale.GERMAN,decimal?"%.1f%s":"%.0f%s",score,suffix),Ratings.format(source,score));
            }
            for(double score:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-0.0,-1.25,101,1e20})
                assertEquals(String.format(java.util.Locale.GERMAN,decimal?"%.1f%s":"%.0f%s",score,suffix),Ratings.format(source,score));
        }
    }
}
