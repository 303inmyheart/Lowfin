package de.lowfin.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class SeekGestureTest {
    @Test public void holdAcceleratesAndCommitsOnlyPreview() {
        SeekGesture seek=new SeekGesture(); assertEquals(15000,seek.begin(22,1,10000,0,120000));
        assertEquals(15000,seek.repeat(100,120000)); assertEquals(20000,seek.repeat(250,120000));
        assertEquals(30000,seek.repeat(1250,120000)); assertEquals(50000,seek.repeat(3000,120000));
        assertEquals(80000,seek.repeat(5000,120000)); assertEquals(120000,seek.repeat(8000,120000));
        seek.reset(); assertFalse(seek.active());
    }
    @Test public void newGestureResetsSpeedAndClampsBothEnds() {
        SeekGesture seek=new SeekGesture(); assertEquals(0,seek.begin(21,-1,3000,0,10000));
        seek.repeat(8000,10000); seek.reset(); assertEquals(5000,seek.begin(22,1,0,9000,10000)); assertEquals(10000,seek.repeat(9250,10000));
        assertEquals(5000,SeekGesture.step(0));
    }
}
