package de.lowfin.tv;

import androidx.media3.common.C;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class AdaptiveTsExtractorTest {
    static final class Sink implements ExtractorOutput {
        SeekMap map;int tracks,ends,samples;
        @Override public TrackOutput track(int id,int type) {
            tracks++;return new ForwardingTrackOutput(new DummyTrackOutput()) {
                @Override public void sampleData(ParsableByteArray data,int size,int part) {samples++;super.sampleData(data,size,part);}
            };
        }
        @Override public void endTracks() {ends++;}
        @Override public void seekMap(SeekMap map) {this.map=map;}
    }
    static class Probe implements Extractor {
        ExtractorOutput output;boolean emitted;final SeekMap map;final boolean earlySample;
        Probe(SeekMap map,boolean earlySample) {this.map=map;this.earlySample=earlySample;}
        @Override public boolean sniff(ExtractorInput input) {return true;}
        @Override public void init(ExtractorOutput output) {this.output=output;}
        @Override public int read(ExtractorInput input,PositionHolder seek) throws IOException {
            if(emitted) return RESULT_END_OF_INPUT;
            emitted=true;TrackOutput track=output.track(256,C.TRACK_TYPE_VIDEO);output.endTracks();
            if(earlySample) track.sampleData(new ParsableByteArray(new byte[]{1}),1);
            output.seekMap(map);return RESULT_CONTINUE;
        }
        @Override public void seek(long position,long time) {}
        @Override public void release() {}
    }
    private static Sink run(AdaptiveTsExtractor extractor,long length) throws Exception {
        Sink sink=new Sink();extractor.init(sink);PositionHolder position=new PositionHolder();
        ExtractorInput input=new DefaultExtractorInput((data,offset,size)->-1,0,length);
        try {for(int i=0;i<20&&sink.map==null;i++) {int result=extractor.read(input,position);if(result==Extractor.RESULT_SEEK) assertEquals(0,position.position);}return sink;}
        finally {extractor.release();}
    }
    @Test public void NormalSeekableStreamDoesNotRetry() throws Exception {
        SeekMap seekable=new SeekMap() {
            public boolean isSeekable() {return true;}public long getDurationUs() {return 1000000;}
            public SeekPoints getSeekPoints(long time) {return new SeekPoints(SeekPoint.START);}
        };
        Sink sink=run(new AdaptiveTsExtractor(new Probe(seekable,false),budget->{fail("No retry for valid map");return null;}),8*1024*1024);
        assertSame(seekable,sink.map);
    }
    @Test public void MissingTimestampsStopAtFiniteBudgetAndDoNotDuplicateTracks() throws Exception {
        List<Integer> windows=new ArrayList<>();
        List<AdaptiveTsExtractor.Issue> failures=new ArrayList<>();
        Sink sink=run(new AdaptiveTsExtractor(new Probe(new SeekMap.Unseekable(C.TIME_UNSET),false),budget->{windows.add(budget);return new Probe(new SeekMap.Unseekable(C.TIME_UNSET),false);},failures::add),8*1024*1024);
        assertEquals(java.util.Arrays.asList(512*1024,2*1024*1024,4*1024*1024),windows);
        assertFalse(sink.map.isSeekable());assertEquals(C.TIME_UNSET,sink.map.getDurationUs());assertEquals(1,sink.tracks);assertEquals(1,sink.ends);
        assertEquals(1,failures.size());assertEquals(AdaptiveTsExtractor.Failure.SEARCH_LIMIT,failures.get(0).reason);assertEquals(4*1024*1024,failures.get(0).window);
    }
    @Test public void UnknownLengthAndAlreadyFullySearchedSmallFilesDoNotRetry() throws Exception {
        for(long length:new long[]{C.LENGTH_UNSET,100000,112800}) {
            List<AdaptiveTsExtractor.Issue> failures=new ArrayList<>();
            Sink sink=run(new AdaptiveTsExtractor(new Probe(new SeekMap.Unseekable(C.TIME_UNSET),false),budget->{fail("No futile or non-seekable-input retry");return null;},failures::add),length);
            assertFalse(sink.map.isSeekable());
            assertEquals(1,failures.size());assertEquals(length==C.LENGTH_UNSET?AdaptiveTsExtractor.Failure.UNKNOWN_LENGTH:AdaptiveTsExtractor.Failure.NO_TIMING,failures.get(0).reason);
        }
    }
    @Test public void FailureExplanationIsAvailableInBothLanguages() {
        I18n.init(org.robolectric.RuntimeEnvironment.getApplication());
        try {
            for(String language:new String[]{"en","de"}) {
                I18n.select(language);
                for(AdaptiveTsExtractor.Failure reason:AdaptiveTsExtractor.Failure.values()) {
                    String text=PlayerActivity.timingIssueText(new AdaptiveTsExtractor.Issue(reason,4*1024*1024));
                    assertTrue(text.contains(language.equals("de")?"Über Server abspielen":"Play via server"));assertTrue(text.contains("OK"));
                    if(reason==AdaptiveTsExtractor.Failure.SEARCH_LIMIT) assertTrue(text.contains("4096"));
                    if(reason==AdaptiveTsExtractor.Failure.UNKNOWN_LENGTH) assertTrue(text.contains(language.equals("de")?"Gesamtlänge":"total length"));
                    assertFalse(text.contains("%1$"));
                }
            }
        } finally {I18n.select("en");}
    }
    @Test public void SamplesAlreadyPassedThroughNeverRestart() throws Exception {
        Sink sink=run(new AdaptiveTsExtractor(new Probe(new SeekMap.Unseekable(C.TIME_UNSET),true),budget->{fail("Cannot replay emitted samples");return null;}),8*1024*1024);
        assertEquals(1,sink.samples);assertFalse(sink.map.isSeekable());
    }
    @Test public void NetworkErrorsArePropagatedWithoutRestart() throws Exception {
        Probe probe=new Probe(new SeekMap.Unseekable(C.TIME_UNSET),false) {
            @Override public int read(ExtractorInput input,PositionHolder seek) throws IOException {throw new IOException("network fixture");}
        };
        AdaptiveTsExtractor extractor=new AdaptiveTsExtractor(probe,budget->{fail("Not a network retry mechanism");return null;});extractor.init(new Sink());
        ExtractorInput input=new DefaultExtractorInput((data,offset,size)->-1,0,8*1024*1024);
        assertThrows(IOException.class,()->extractor.read(input,new PositionHolder()));extractor.release();
    }
}
