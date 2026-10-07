package de.lowfin.tv;

import android.app.ActivityManager;
import android.content.*;
import android.content.res.Configuration;
import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.ExoTrackSelection;
import androidx.media3.exoplayer.upstream.DefaultAllocator;

/** Grow only while both the app heap and Android retain reserve. Check off the UI thread. */
@androidx.media3.common.util.UnstableApi
final class AdaptiveLoadControl extends DefaultLoadControl implements ComponentCallbacks2 {
    static final int MB=1024*1024;
    private final DefaultAllocator allocator;
    private final Context context;
    private final ActivityManager manager;
    private final ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
    private final int ceiling;
    private volatile long pressureUntil;
    private long lastCheck=-1000;
    private int budget;
    private boolean registered=true;
    AdaptiveLoadControl(Context context,boolean live) { this(context,live,new DefaultAllocator(true,C.DEFAULT_BUFFER_SEGMENT_SIZE)); }
    private AdaptiveLoadControl(Context context,boolean live,DefaultAllocator allocator) {
        super(allocator,live?3000:30000,live?15000:90000,live?750:2500,live?1500:6000,cap(context,live)*MB,false,0,false);
        this.allocator=allocator; this.context=context.getApplicationContext(); manager=(ActivityManager)this.context.getSystemService(Context.ACTIVITY_SERVICE);
        ceiling=cap(context,live)*MB; budget=8*MB; this.context.registerComponentCallbacks(this);
    }
    private static int cap(Context context,boolean live) { return Math.max(16,Math.min(64,context.getSharedPreferences("settings",0).getInt(live?"liveBufferMb":"bufferMb",live?32:48))); }
    static int budget(long heapFree,long heapMax,long availableRam,long systemThreshold,int allocated,int ceiling,boolean pressure) {
        return budget(heapFree,heapMax,availableRam,systemThreshold,allocated,0,ceiling,pressure);
    }
    static int budget(long heapFree,long heapMax,long availableRam,long systemThreshold,int allocated,int previousBudget,int ceiling,boolean pressure) {
        long heapReserve=Math.max(24L*MB,heapMax/4),systemReserve=Math.max(64L*MB,systemThreshold/2);
        long headroom=Math.min(Math.max(0,heapFree-heapReserve),Math.max(0,availableRam-systemThreshold-systemReserve));
        long target=Math.min(ceiling,(long)allocated+headroom);
        // Permit a small initial buffer while keeping at least 8 MiB of emergency headroom.
        if(heapFree>9L*MB&&availableRam>systemThreshold+9L*MB) target=Math.max(target,Math.min(ceiling,MB));
        // Do not lower the refill target just because playback consumed queued samples.
        // A retained target may use the existing allocator pool or bounded emergency headroom.
        long refillRoom=Math.min(Math.max(0,heapFree-8L*MB),Math.max(0,availableRam-systemThreshold-8L*MB));
        target=Math.max(target,Math.min(previousBudget,(long)allocated+refillRoom));
        if(pressure) target=Math.min(target,8L*MB);
        return (int)Math.max(0,target);
    }
    private void check() {
        long now=SystemClock.elapsedRealtime(); if(now-lastCheck<1000) return; lastCheck=now;
        try {
            manager.getMemoryInfo(info); Runtime runtime=Runtime.getRuntime(); long used=runtime.totalMemory()-runtime.freeMemory();
            budget=budget(runtime.maxMemory()-used,runtime.maxMemory(),info.availMem,info.threshold,allocator.getTotalBytesAllocated(),budget,ceiling,info.lowMemory||now<pressureUntil);
        } catch(RuntimeException e) { budget=Math.min(ceiling,16*MB); }
        allocator.setTargetBufferSize(budget); allocator.trim();
    }
    @Override public boolean shouldContinueLoading(LoadControl.Parameters parameters) {
        check(); if(allocator.getTotalBytesAllocated()>=budget) return false; return super.shouldContinueLoading(parameters);
    }
    @Override public boolean shouldStartPlayback(LoadControl.Parameters parameters) {
        check(); return super.shouldStartPlayback(parameters)||budget>0&&allocator.getTotalBytesAllocated()>=budget&&parameters.bufferedDurationUs>=500000;
    }
    @Override public void onTracksSelected(LoadControl.Parameters parameters,TrackGroupArray tracks,ExoTrackSelection[] selections) {
        super.onTracksSelected(parameters,tracks,selections); allocator.setTargetBufferSize(budget); allocator.trim();
    }
    @Override public void onReleased(PlayerId id) {
        super.onReleased(id); if(registered) { context.unregisterComponentCallbacks(this); registered=false; }
    }
    @Override public void onTrimMemory(int level) { if(level!=TRIM_MEMORY_UI_HIDDEN&&level>=TRIM_MEMORY_RUNNING_LOW) { pressureUntil=SystemClock.elapsedRealtime()+10000; lastCheck=-1000; } }
    @Override public void onLowMemory() { pressureUntil=SystemClock.elapsedRealtime()+30000; lastCheck=-1000; }
    @Override public void onConfigurationChanged(Configuration configuration) {}
}
