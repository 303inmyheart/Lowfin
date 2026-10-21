package de.lowfin.tv;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import java.util.*;
import java.util.function.Supplier;

/** Device decoder capabilities are stable for the process; failed reads remain retryable. */
final class DeviceCodecs {
    static final class Memo<T> {
        private final Supplier<T> read;
        private T value;
        Memo(Supplier<T> read) { this.read=read; }
        synchronized T get() { if(value==null) value=Objects.requireNonNull(read.get()); return value; }
    }
    private static final Memo<MediaCodecInfo[]> INFO=new Memo<>(()-> {
        MediaCodecInfo[] values=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
        if(values.length==0) throw new IllegalStateException("Decoder catalog unavailable");
        return values;
    });
    private static final Memo<Set<String>> TYPES=new Memo<>(()-> {
        Set<String> values=new HashSet<>();
        for(MediaCodecInfo info:infos()) if(!info.isEncoder()) for(String type:info.getSupportedTypes()) values.add(type.toLowerCase(Locale.ROOT));
        if(values.isEmpty()) throw new IllegalStateException("Decoder types unavailable");
        return Collections.unmodifiableSet(values);
    });
    static MediaCodecInfo[] infos() { return INFO.get(); }
    static Set<String> types() { return TYPES.get(); }
    static void warmUp() { try { types(); } catch(RuntimeException ignored) { /* Normal playback retains its fallback and retries. */ } }
}
