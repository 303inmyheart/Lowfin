package de.lowfin.tv;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.util.Log;

/** Short local cue, mixed with media without requesting audio focus or changing any volume. */
final class NoticeSound implements AutoCloseable {
    private final Context context;
    private final Handler main;
    private final Runnable started;
    private SoundPool pool;
    private boolean closed;
    private final Runnable release=this::close;
    NoticeSound(Context context,Handler main) {this(context,main,()->{});}
    NoticeSound(Context context,Handler main,Runnable started) {this.context=context;this.main=main;this.started=started;}
    static boolean audible(AudioManager audio) {
        return audio!=null&&audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL&&!audio.isStreamMute(AudioManager.STREAM_MUSIC)&&audio.getStreamVolume(AudioManager.STREAM_MUSIC)>0;
    }
    void play() {
        if(closed||pool!=null)return;
        if(!audible((AudioManager)context.getSystemService(Context.AUDIO_SERVICE))) {Log.i("LowfinNotice","Sound suppressed by media volume or silent mode");return;}
        try {
            pool=new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener((loaded,sample,status)->main.post(()-> {
                if(closed||loaded!=pool)return;
                if(status!=0) {Log.w("LowfinNotice","Sound load failed, status="+status);close();return;}
                if(!audible((AudioManager)context.getSystemService(Context.AUDIO_SERVICE))) {close();return;}
                int stream=pool.play(sample,.28f,.28f,1,0,1);
                if(stream==0) {Log.w("LowfinNotice","Sound playback could not start");close();return;}
                started.run();
                Log.i("LowfinNotice","Notification cue started on media output");
                main.removeCallbacks(release);main.postDelayed(release,1200);
            }));
            if(pool.load(context,R.raw.dashboard_notice,1)==0) {Log.w("LowfinNotice","Sound resource unavailable");close();return;}
            main.postDelayed(release,5000);
        } catch(RuntimeException unavailable) {Log.w("LowfinNotice","Notification audio unavailable: "+unavailable.getClass().getSimpleName());close();}
    }
    @Override public void close() {closed=true;main.removeCallbacks(release);if(pool!=null) {pool.release();pool=null;}}
}
