package de.lowfin.tv;

/** Preview a held remote seek, then commit once on release. */
final class SeekGesture {
    static long step(long heldMillis) { return heldMillis<1000?5000:heldMillis<2500?10000:heldMillis<4500?20000:heldMillis<7000?30000:60000; }
    private long started,last,preview; private int key,direction;
    boolean active() { return key!=0; }
    int key() { return key; }
    long target() { return preview; }
    long begin(int key,int direction,long position,long now,long duration) { this.key=key; this.direction=direction; started=now; last=now; preview=clamp(position+direction*5000,duration); return preview; }
    long repeat(long now,long duration) { if(now-last>=250) { preview=clamp(preview+direction*step(Math.max(0,now-started)),duration); last=now; } return preview; }
    void reset() { key=0; }
    private static long clamp(long value,long duration) { return Math.max(0,duration>0?Math.min(value,duration):value); }
}
