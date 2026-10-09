package emu.grasscutter.server.game;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ThreadMXBean;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class RuntimeDiagnosticsTest {
    private static ThreadMXBean bean(boolean ownableSupported, long[] deadlocks) {
        return (ThreadMXBean) Proxy.newProxyInstance(ThreadMXBean.class.getClassLoader(),
                new Class<?>[] {ThreadMXBean.class}, (proxy, method, args) -> switch(method.getName()) {
                    case "isSynchronizerUsageSupported" -> ownableSupported;
                    case "findDeadlockedThreads" -> {
                        assertTrue(ownableSupported); yield deadlocks;
                    }
                    case "findMonitorDeadlockedThreads" -> {
                        assertFalse(ownableSupported); yield deadlocks;
                    }
                    default -> throw new AssertionError(method.getName());
                });
    }
    @Test void bothMonitorAndOwnableSynchronizerDeadlocksAreReported() {
        assertEquals(2, RuntimeDiagnostics.deadlockedThreadCount(bean(true, new long[]{1,2})));
        assertEquals(0, RuntimeDiagnostics.deadlockedThreadCount(bean(true, null)));
    }
    @Test void jvmsWithoutSynchronizerSupportUseTheMonitorFallback() {
        assertEquals(3, RuntimeDiagnostics.deadlockedThreadCount(bean(false, new long[]{1,2,3})));
    }
}
