package de.lowfin.tv;

import android.content.Context;
import android.util.Log;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException;

/** Bounded local error history. Never stores source URLs, tokens or exception messages. */
@androidx.media3.common.util.UnstableApi
final class PlaybackDiagnostics {
    static String describe(PlaybackException error,long position,long buffered,boolean live,String method) {
        StringBuilder out=new StringBuilder(error.getErrorCodeName()).append(" positionMs=").append(position)
            .append(" bufferedAheadMs=").append(Math.max(0,buffered-position)).append(" live=").append(live)
            .append(" method=").append(safe(method));
        if(error instanceof ExoPlaybackException) {
            ExoPlaybackException ex=(ExoPlaybackException)error;
            out.append(" renderer=").append(safe(ex.rendererName)).append(" support=").append(ex.rendererFormatSupport);
            Format f=ex.rendererFormat;
            if(f!=null) out.append(" mime=").append(safe(f.sampleMimeType)).append(" codecs=").append(safe(f.codecs))
                .append(" size=").append(f.width).append('x').append(f.height).append(" fps=").append(f.frameRate)
                .append(" channels=").append(f.channelCount);
        }
        Throwable cause=error;
        for(int i=0;i<8&&cause!=null;i++,cause=cause.getCause()) {
            out.append(" cause=").append(cause.getClass().getSimpleName());
            if(cause instanceof MediaCodecDecoderException) {
                MediaCodecDecoderException codec=(MediaCodecDecoderException)cause;
                out.append(" decoder=").append(safe(codec.codecInfo==null?null:codec.codecInfo.name))
                    .append(" diagnostic=").append(safe(codec.diagnosticInfo)).append(" codecError=").append(codec.errorCode);
            }
        }
        return out.toString();
    }
    static void record(Context context,PlaybackException error,ExoPlayer player,boolean live,String method) {
        String detail=describe(error,player.getCurrentPosition(),player.getBufferedPosition(),live,method);
        Log.e("LowfinPlayer",detail);
        android.content.SharedPreferences prefs=context.getSharedPreferences("playback-diagnostics",Context.MODE_PRIVATE);
        String history=prefs.getString("errors","")+System.currentTimeMillis()+" "+detail+"\n";
        if(history.length()>16384) { int cut=history.indexOf('\n',history.length()-16384);history=cut<0?"":history.substring(cut+1); }
        prefs.edit().putString("errors",history).apply();
    }
    private static String safe(String value) {
        return value==null?"unknown":value.replaceAll("[^A-Za-z0-9_.,/+-]","_").substring(0,Math.min(120,value.length()));
    }
}
