package de.lowfin.tv;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class AudioTracksTest {
    @Test public void onlyEmbeddedAudioKeepsActualIndexAndSource() {
        JSONObject item=Json.obj("MediaSources",Json.arr(Json.obj("Id","file1","MediaStreams",Json.arr(
            Json.obj("Type","Video","Index",0), Json.obj("Type","Audio","Index",3,"DisplayTitle","Deutsch 5.1"),
            Json.obj("Type","Subtitle","Index",4), Json.obj("Type","Audio","Index",5,"IsExternal",true)))));
        assertEquals(1,AudioTracks.embedded(item).size());
        AudioTracks.Choice choice=AudioTracks.embedded(item).get(0);
        assertEquals(3,choice.index); assertEquals("file1",choice.sourceId); assertEquals("Deutsch 5.1",choice.label);
    }
    @Test public void multipleFilesRemainDistinguishableAndMissingStreamsAreSafe() {
        assertTrue(AudioTracks.embedded(new JSONObject()).isEmpty());
        JSONObject item=Json.obj("MediaSources",Json.arr(
            Json.obj("Id","a","Name","1080p","MediaStreams",Json.arr(Json.obj("Type","Audio","Index",1,"Language","deu","Codec","aac","Channels",2))),
            Json.obj("Id","b","Name","720p","MediaStreams",Json.arr(Json.obj("Type","Audio","Index",2,"DisplayTitle","English")))));
        assertEquals(2,AudioTracks.embedded(item).size());
        assertTrue(AudioTracks.embedded(item).get(0).label.startsWith("1080p")); assertEquals("b",AudioTracks.embedded(item).get(1).sourceId);
    }
}
