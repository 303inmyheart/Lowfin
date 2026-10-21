package de.lowfin.tv;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DeviceCodecsTest {
    @Test public void ConcurrentStartsShareOneSuccessfulCapabilityRead() throws Exception {
        AtomicInteger calls=new AtomicInteger();Object capabilities=new Object();DeviceCodecs.Memo<Object> memo=new DeviceCodecs.Memo<>(()-> { calls.incrementAndGet();return capabilities; });
        ExecutorService workers=Executors.newFixedThreadPool(4);
        try { List<Future<Object>> results=new ArrayList<>();for(int i=0;i<12;i++) results.add(workers.submit(memo::get));for(Future<Object> result:results) assertSame(capabilities,result.get(3,TimeUnit.SECONDS));assertEquals(1,calls.get()); } finally { workers.shutdownNow(); }
    }
    @Test public void FailedCapabilityReadDoesNotPoisonLaterStarts() {
        AtomicInteger calls=new AtomicInteger();DeviceCodecs.Memo<String> memo=new DeviceCodecs.Memo<>(()-> { if(calls.incrementAndGet()==1) throw new IllegalStateException("temporary vendor failure");return "supported"; });
        assertThrows(IllegalStateException.class,memo::get);assertEquals("supported",memo.get());assertEquals("supported",memo.get());assertEquals(2,calls.get());
    }
    @Test public void MissingCapabilityResultAlsoRemainsRetryable() {
        AtomicInteger calls=new AtomicInteger();DeviceCodecs.Memo<String> memo=new DeviceCodecs.Memo<>(()->calls.incrementAndGet()==1?null:"recovered");
        assertThrows(NullPointerException.class,memo::get);assertEquals("recovered",memo.get());assertEquals(2,calls.get());
    }
}
