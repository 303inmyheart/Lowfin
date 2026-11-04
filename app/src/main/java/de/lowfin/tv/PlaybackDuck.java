package de.lowfin.tv;

import android.os.Handler;
import java.util.function.*;

/** Temporarily lower only the current player's volume; never touch device volume or a retired player. */
final class PlaybackDuck<P> implements AutoCloseable {
    private final Handler main;
    private final Supplier<P> current;
    private final ToDoubleFunction<P> volume;
    private final ObjDoubleConsumer<P> change;
    private P lowered;
    private double original,applied;
    private boolean active;
    private final Runnable restore=this::restore;
    PlaybackDuck(Handler main,Supplier<P> current,ToDoubleFunction<P> volume,ObjDoubleConsumer<P> change) {
        this.main=main;this.current=current;this.volume=volume;this.change=change;
    }
    void lower() {
        P player=current.get();if(player==null)return;
        double now=volume.applyAsDouble(player);
        if(!active||lowered!=player||Math.abs(now-applied)>.00001)original=now;
        lowered=player;active=true;applied=original*.25;change.accept(player,applied);
        main.removeCallbacks(restore);main.postDelayed(restore,3000);
    }
    private void restore() {
        if(active&&current.get()==lowered&&Math.abs(volume.applyAsDouble(lowered)-applied)<.00001)change.accept(lowered,original);
        active=false;lowered=null;
    }
    @Override public void close() {main.removeCallbacks(restore);restore();}
}
