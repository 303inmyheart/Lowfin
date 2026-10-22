package de.lowfin.tv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Coalesce committed plugin batches without queuing a toast for every item. */
final class CacheNotice implements AutoCloseable {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BooleanSupplier allowed;
    private final IntConsumer show;
    private volatile boolean closed;
    private static final long MIN_INTERVAL=60000;
    private long nextNotice;
    private int pending;
    private boolean scheduled;
    private final Runnable flush=()->flush();
    CacheNotice(BooleanSupplier allowed,IntConsumer show) { this.allowed=allowed;this.show=show; }
    void changed(int count) {
        if(count<=0||closed) return;
        main.post(()-> {
            if(closed||!allowed.getAsBoolean()) return;
            pending=(int)Math.min(Integer.MAX_VALUE,(long)pending+count);
            if(!scheduled) { scheduled=true;main.postDelayed(flush,Math.max(1500,nextNotice-SystemClock.elapsedRealtime())); }
        });
    }
    private void flush() {
        scheduled=false;int count=pending;pending=0;
        if(count>0&&!closed&&allowed.getAsBoolean()) { nextNotice=SystemClock.elapsedRealtime()+MIN_INTERVAL;show.accept(count); }
    }
    @Override public void close() { closed=true;main.removeCallbacks(flush); }
}
