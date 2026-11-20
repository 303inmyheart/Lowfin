package de.lowfin.tv;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Consumer;

/** Retry only TS duration discovery, before any samples escape, with a finite search budget. */
@androidx.media3.common.util.UnstableApi
final class AdaptiveTsExtractor implements Extractor {
    static final int[] WINDOWS={androidx.media3.extractor.ts.TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,512*1024,2*1024*1024,4*1024*1024};
    private static final class Retry extends RuntimeException {
        Retry() {super(null,null,false,false);}
    }
    private final IntFunction<Extractor> create;
    enum Failure { UNKNOWN_LENGTH, SEARCH_LIMIT, NO_TIMING, ALREADY_STARTED }
    static final class Issue {
        final Failure reason;final int window;
        Issue(Failure reason,int window) {this.reason=reason;this.window=window;}
    }
    private final Consumer<Issue> failure;
    private Extractor delegate;
    private ExtractorOutput output;
    private final Map<Integer,TrackOutput> tracks=new HashMap<>();
    private boolean samples,ended,reported;
    private long length=C.LENGTH_UNSET;
    private int stage;
    AdaptiveTsExtractor(Extractor initial,IntFunction<Extractor> create) {this(initial,create,issue->{});}
    AdaptiveTsExtractor(Extractor initial,IntFunction<Extractor> create,Consumer<Issue> failure) {delegate=initial;this.create=create;this.failure=failure;}
    @Override public boolean sniff(ExtractorInput input) throws IOException {return delegate.sniff(input);}
    @Override public void init(ExtractorOutput value) {output=value;delegate.init(forward);}
    private final ExtractorOutput forward=new ExtractorOutput() {
        @Override public TrackOutput track(int id,int type) {
            return tracks.computeIfAbsent(id,key-> {
                TrackOutput target=output.track(id,type);
                return new TrackOutput() {
                    @Override public void format(Format format) {target.format(format);}
                    @Override public void durationUs(long duration) {target.durationUs(duration);}
                    @Override public int sampleData(DataReader reader,int size,boolean allowEnd,int part) throws IOException {samples=true;return target.sampleData(reader,size,allowEnd,part);}
                    @Override public void sampleData(ParsableByteArray data,int size,int part) {samples=true;target.sampleData(data,size,part);}
                    @Override public void sampleMetadata(long time,int flags,int size,int offset,CryptoData crypto) {samples=true;target.sampleMetadata(time,flags,size,offset,crypto);}
                };
            });
        }
        @Override public void endTracks() {if(!ended) {ended=true;output.endTracks();}}
        @Override public void seekMap(SeekMap map) {
            // Do not invent timestamps, repeatedly retry missing PCRs, or restart emitted media.
            if(!map.isSeekable()||map.getDurationUs()<=0) {
                if(!samples&&length>WINDOWS[stage]&&stage+1<WINDOWS.length) throw new Retry();
                if(!reported) {
                    reported=true;
                    Failure reason=length==C.LENGTH_UNSET?Failure.UNKNOWN_LENGTH:samples?Failure.ALREADY_STARTED:length>WINDOWS[stage]?Failure.SEARCH_LIMIT:Failure.NO_TIMING;
                    failure.accept(new Issue(reason,WINDOWS[stage]));
                }
            }
            output.seekMap(map);
        }
    };
    @Override public int read(ExtractorInput input,PositionHolder position) throws IOException {
        length=input.getLength();
        try {return delegate.read(input,position);}
        catch(Retry retry) {
            delegate.release();delegate=create.apply(WINDOWS[++stage]);delegate.init(forward);
            position.position=0;
            android.util.Log.i("LowfinPlayer","Retrying TS timestamp discovery, searchBytes="+WINDOWS[stage]);
            return RESULT_SEEK;
        }
    }
    @Override public void seek(long position,long timeUs) {delegate.seek(position,timeUs);}
    @Override public void release() {delegate.release();}
}
