package de.lowfin.tv;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Temporary diagnostic runner only: sample delayed main-loop heartbeats, no media/settings edits. */
final class StartupDiagnostics {
    static String inspect(Instrumentation test) throws Exception {
        Handler main=new Handler(Looper.getMainLooper());
        AtomicLong heartbeat=new AtomicLong(SystemClock.uptimeMillis()),maxDelay=new AtomicLong();
        Map<String,Integer> stacks=new HashMap<>();
        Runnable beat=new Runnable() { public void run() { heartbeat.set(SystemClock.uptimeMillis());main.postDelayed(this,16); } };
        main.post(beat);
        ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(()-> {
            long delay=SystemClock.uptimeMillis()-heartbeat.get();maxDelay.accumulateAndGet(delay,Math::max);
            if(delay<100) return;
            StringBuilder trace=new StringBuilder();
            for(StackTraceElement frame:Looper.getMainLooper().getThread().getStackTrace()) trace.append(frame).append('\n');
            String key=trace.toString();stacks.merge(key,1,Integer::sum);
        },0,20,TimeUnit.MILLISECONDS);
        try {
            Intent launch=new Intent().setClassName(test.getTargetContext(),"de.lowfin.tv.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            test.startActivitySync(launch);
            Thread.sleep(8000);
        } finally { sampler.shutdown();sampler.awaitTermination(3,TimeUnit.SECONDS);main.removeCallbacks(beat); }
        List<Map.Entry<String,Integer>> ordered=new ArrayList<>(stacks.entrySet());ordered.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));
        StringBuilder result=new StringBuilder("Maximum sampled main-loop heartbeat delay ms: ").append(maxDelay.get()).append('\n');
        for(int i=0;i<Math.min(8,ordered.size());i++) result.append("Samples: ").append(ordered.get(i).getValue()).append('\n').append(ordered.get(i).getKey()).append('\n');
        return result.toString();
    }
}
