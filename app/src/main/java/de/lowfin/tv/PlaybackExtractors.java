package de.lowfin.tv;

import android.net.Uri;
import androidx.media3.extractor.*;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.text.SubtitleParser;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Keep Media3's format sniffing and configuration; adapt only finite progressive TS inputs. */
@androidx.media3.common.util.UnstableApi
final class PlaybackExtractors implements ExtractorsFactory {
    private final DefaultExtractorsFactory base=new DefaultExtractorsFactory();
    private final Consumer<AdaptiveTsExtractor.Issue> failure;
    PlaybackExtractors(Consumer<AdaptiveTsExtractor.Issue> failure) {this.failure=failure;}
    @Override public synchronized Extractor[] createExtractors() {base.setTsExtractorTimestampSearchBytes(AdaptiveTsExtractor.WINDOWS[0]);return wrap(base.createExtractors());}
    @Override public synchronized Extractor[] createExtractors(Uri uri,Map<String,List<String>> headers) {base.setTsExtractorTimestampSearchBytes(AdaptiveTsExtractor.WINDOWS[0]);return wrap(base.createExtractors(uri,headers));}
    private Extractor[] wrap(Extractor[] values) {
        for(int i=0;i<values.length;i++) if(values[i] instanceof TsExtractor) values[i]=new AdaptiveTsExtractor(values[i],this::freshTs,failure);
        return values;
    }
    private synchronized Extractor freshTs(int window) {
        Extractor selected=null;
        for(Extractor value:base.setTsExtractorTimestampSearchBytes(window).createExtractors()) {
            if(value instanceof TsExtractor) selected=value;else value.release();
        }
        if(selected==null) throw new IllegalStateException("TS extractor unavailable");return selected;
    }
    @Override public synchronized ExtractorsFactory setSubtitleParserFactory(SubtitleParser.Factory value) {base.setSubtitleParserFactory(value);return this;}
    @Override public synchronized ExtractorsFactory experimentalSetTextTrackTranscodingEnabled(boolean value) {base.experimentalSetTextTrackTranscodingEnabled(value);return this;}
    @Override public synchronized ExtractorsFactory experimentalSetCodecsToParseWithinGopSampleDependencies(int value) {base.experimentalSetCodecsToParseWithinGopSampleDependencies(value);return this;}
}
