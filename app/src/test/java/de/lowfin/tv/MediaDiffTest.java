package de.lowfin.tv;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class MediaDiffTest {
    @Test public void homeComparisonDoesNotSerializeLargePlaybackMetadata() throws Exception {
        JSONObject before=new JSONObject() { @Override public String toString() { throw new AssertionError("Full DTO serialization on UI thread"); } };
        before.put("Name","Film"); before.put("Type","Movie"); before.put("MediaSources",Json.arr(Json.obj("Size",500)));
        JSONObject after=Json.obj("Name","Film","Type","Movie","MediaSources",Json.arr(Json.obj("Size",1000)));
        assertTrue(MediaDiff.presentationEquals(before,after));
        after.put("UserData",Json.obj("PlaybackPositionTicks",500000000L)); assertFalse(MediaDiff.presentationEquals(before,after));
    }
    @Test public void completeSnapshotsCompareWithoutSerializationAndIgnoreObjectKeyOrder() throws Exception {
        JSONObject before=new JSONObject() { @Override public String toString() { throw new AssertionError("Serialized full metadata"); } };
        before.put("Id","film"); before.put("MediaSources",new JSONArray("[{\"Size\":9000000000,\"Streams\":[{\"Codec\":\"hevc\",\"Index\":1}]}]"));
        JSONObject after=new JSONObject("{\"MediaSources\":[{\"Streams\":[{\"Index\":1,\"Codec\":\"hevc\"}],\"Size\":9000000000}],\"Id\":\"film\"}");
        assertTrue(MediaDiff.CALLBACK.areContentsTheSame(before,after));
        after.getJSONArray("MediaSources").getJSONObject(0).getJSONArray("Streams").getJSONObject(0).put("Codec","h264");
        assertFalse(MediaDiff.CALLBACK.areContentsTheSame(before,after));
    }
    @Test public void integralFastPathKeepsPrecisionAndRejectsNonFiniteNumbers() {
        assertTrue(MediaDiff.jsonEquals(Long.valueOf(9007199254740993L),Long.valueOf(9007199254740993L)));
        assertFalse(MediaDiff.jsonEquals(Long.valueOf(Long.MIN_VALUE),Long.valueOf(Long.MAX_VALUE)));
        assertTrue(MediaDiff.jsonEquals(Short.valueOf((short)32000),Short.valueOf((short)32000)));
        assertFalse(MediaDiff.jsonEquals(Byte.valueOf((byte)-1),Byte.valueOf((byte)1)));
        assertTrue(MediaDiff.jsonEquals(32000,32000L));
        assertFalse(MediaDiff.jsonEquals(Double.NaN,Float.NaN));
        assertFalse(MediaDiff.jsonEquals(Double.POSITIVE_INFINITY,Float.POSITIVE_INFINITY));
    }
    @Test public void comparisonPreservesArrayOrderTypesMissingKeysAndNumericPrecision() throws Exception {
        assertFalse(MediaDiff.jsonEquals(new JSONArray("[1,2]"),new JSONArray("[2,1]")));
        assertFalse(MediaDiff.jsonEquals(Json.obj("A",JSONObject.NULL),Json.obj("B",JSONObject.NULL)));
        assertFalse(MediaDiff.jsonEquals(Json.obj("A",1),Json.obj("A","1")));
        assertFalse(MediaDiff.jsonEquals(9007199254740992L,9007199254740993L));
        assertTrue(MediaDiff.jsonEquals(1,1L)); assertTrue(MediaDiff.jsonEquals(1,1.0));
        assertFalse(MediaDiff.jsonEquals(null,JSONObject.NULL));
        assertTrue(MediaDiff.jsonEquals(JSONObject.NULL,JSONObject.NULL));
        assertFalse(MediaDiff.jsonEquals(new JSONArray("[1]"),new JSONArray("[1,2]")));
    }
}
