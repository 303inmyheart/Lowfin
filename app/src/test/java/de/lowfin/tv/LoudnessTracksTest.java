package de.lowfin.tv;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class LoudnessTracksTest {
    private JSONObject track(int index,String language,int channels) throws Exception {return Json.obj("Type","Audio","Index",index,"Language",language,"Channels",channels,"Codec","aac","SampleRate",48000);}
    private Format format(String language,int channels) { return new Format.Builder().setLanguage(language).setChannelCount(channels).setSampleRate(48000).setSampleMimeType(MimeTypes.AUDIO_AAC).build(); }
    @Test public void MultipleLanguagesAndChannelLayoutsUseFileIndicesNotOrdinals() throws Exception {
        JSONArray streams=Json.arr(track(1,"eng",6),track(3,"deu",2),track(7,"eng",2));
        assertEquals(3,LoudnessTracks.stream(streams,format("de",2)));assertEquals(7,LoudnessTracks.stream(streams,format("en",2)));assertEquals(1,LoudnessTracks.stream(streams,format("eng",6)));
        assertEquals(-1,LoudnessTracks.stream(streams,format("fra",2)));
    }
    @Test public void AmbiguousOrExternalTracksNeverReceiveAnotherTracksCalibration() throws Exception {
        JSONArray streams=Json.arr(track(1,"eng",2),track(4,"eng",2));assertEquals(-1,LoudnessTracks.stream(streams,format("en",2)));
        streams.getJSONObject(1).put("IsExternal",true);assertEquals(1,LoudnessTracks.stream(streams,format("en",2)));
    }
    @Test public void NativeAndDownmixMeasurementsCannotBeSubstituted() throws Exception {
        JSONArray values=Json.arr(Json.obj("streamIndex",4,"profile",LoudnessTracks.NATIVE,"integratedLufs",-24),Json.obj("streamIndex",8,"profile",LoudnessTracks.STEREO,"integratedLufs",-30));
        assertNull(LoudnessTracks.measurement(values,4,LoudnessTracks.STEREO));assertNull(LoudnessTracks.measurement(values,0,LoudnessTracks.NATIVE));assertEquals(-30,LoudnessTracks.measurement(values,8,LoudnessTracks.STEREO).getDouble("integratedLufs"),0);
    }
}
