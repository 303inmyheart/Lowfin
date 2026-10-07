package de.lowfin.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class LiveBufferWatchdogTest {
    @Test public void streamGetsOneRetryThenStopsRatherThanBufferingForever() {
        LiveBufferWatchdog w=new LiveBufferWatchdog(); w.begin(0);
        assertEquals(0,w.poll(19999)); assertEquals(1,w.poll(20000));
        w.begin(20001); assertEquals(0,w.poll(39999)); assertEquals(2,w.poll(40000));
        w.begin(50000); assertEquals(0,w.poll(90000));
    }
    @Test public void readyOrManualRetryResetsTheDeadline() {
        LiveBufferWatchdog w=new LiveBufferWatchdog(); w.begin(100); w.poll(20100); w.reset();
        assertEquals(0,w.poll(99999)); w.begin(100000); assertEquals(1,w.poll(120000));
    }
    @Test public void repeatedBufferingCallbacksDoNotPostponeTimeout() {
        LiveBufferWatchdog w=new LiveBufferWatchdog(); w.begin(10); w.begin(15000); assertEquals(1,w.poll(20010));
    }
}
