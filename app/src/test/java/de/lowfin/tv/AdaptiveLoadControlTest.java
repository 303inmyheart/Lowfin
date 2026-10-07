package de.lowfin.tv;

import org.junit.Test;
import static org.junit.Assert.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class AdaptiveLoadControlTest {
    private static final int MB=1024*1024;
    @Test public void healthyMemoryAllowsBiggerBufferWithoutTakingAllRam() {
        assertEquals(48*MB,AdaptiveLoadControl.budget(180L*MB,256L*MB,500L*MB,64L*MB,8*MB,48*MB,false));
    }
    @Test public void heapAndSystemReserveBothStopBufferGrowth() {
        assertEquals(8*MB,AdaptiveLoadControl.budget(20L*MB,128L*MB,500L*MB,64L*MB,8*MB,48*MB,false));
        assertEquals(8*MB,AdaptiveLoadControl.budget(180L*MB,256L*MB,100L*MB,64L*MB,8*MB,48*MB,false));
    }
    @Test public void androidPressureReducesTargetAndRecoversWhenPressureEnds() {
        assertEquals(8*MB,AdaptiveLoadControl.budget(180L*MB,256L*MB,500L*MB,64L*MB,32*MB,48*MB,true));
        assertEquals(48*MB,AdaptiveLoadControl.budget(180L*MB,256L*MB,500L*MB,64L*MB,8*MB,48*MB,false));
    }
    @Test public void lowEndHeapCanBootstrapWithoutSacrificingEmergencyReserve() {
        assertEquals(MB,AdaptiveLoadControl.budget(20L*MB,128L*MB,100L*MB,64L*MB,0,32*MB,false));
        assertEquals(0,AdaptiveLoadControl.budget(8L*MB,128L*MB,100L*MB,64L*MB,0,32*MB,true));
        assertEquals(0,AdaptiveLoadControl.budget(20L*MB,128L*MB,70L*MB,64L*MB,0,32*MB,true));
    }
    @Test public void cannotReserveABufferWhenNoHeapSpaceRemains() {
        assertEquals(0,AdaptiveLoadControl.budget(0,128L*MB,500L*MB,64L*MB,0,48*MB,false));
    }    @Test public void consumedLiveSamplesDoNotRatchetTheRefillBudgetDownToZero() {
        int budget=8*MB;
        for(int allocated:new int[]{8*MB,6*MB,4*MB,2*MB,0}) budget=AdaptiveLoadControl.budget(20L*MB,128L*MB,100L*MB,64L*MB,allocated,budget,32*MB,false);
        assertEquals(8*MB,budget);
    }
    @Test public void retainingRefillTargetStillPreservesEmergencyHeadroomUnderPressure() {
        assertEquals(2*MB,AdaptiveLoadControl.budget(10L*MB,128L*MB,100L*MB,64L*MB,0,8*MB,32*MB,true));
        assertEquals(0,AdaptiveLoadControl.budget(8L*MB,128L*MB,70L*MB,64L*MB,0,8*MB,32*MB,true));
    }

}
