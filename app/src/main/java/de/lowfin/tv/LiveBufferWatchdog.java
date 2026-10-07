package de.lowfin.tv;

/** Bounded transport retry; keeps the negotiated server session and never enables transcoding. */
final class LiveBufferWatchdog {
    static final int NONE=0,RECONNECT=1,FAIL=2;
    private long since=-1;
    private boolean retried,failed;
    void begin(long now) { if(since<0&&!failed) since=now; }
    void reset() { since=-1; retried=false; failed=false; }
    int poll(long now) {
        if(failed||since<0||now-since<20000) return NONE;
        if(!retried) { retried=true; since=now; return RECONNECT; }
        failed=true; since=-1; return FAIL;
    }
}
