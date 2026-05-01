package link.locutus.discord.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class TimeUtilTest {
    @Test
    void timeToSecAcceptsZeroTimediffPrefix() {
        assertEquals(0L, TimeUtil.timeToSec("timediff:0", 1_700_000_000_000L, false));
    }

    @Test
    void timeToSecPreservesTimestampPrefixSemantics() {
        long now = 1_700_000_000_000L;

        assertEquals(5L, TimeUtil.timeToSec("timestamp:" + (now - 5_000L), now, false));
        assertEquals(5L, TimeUtil.timeToSec("timestamp:" + (now + 5_000L), now, true));
    }

    @Test
    void schedulerRunsOnDaemonThread() throws Exception {
        Field schedulerField = TimeUtil.class.getDeclaredField("SCHEDULER");
        schedulerField.setAccessible(true);
        ScheduledExecutorService scheduler = (ScheduledExecutorService) schedulerField.get(null);

        SchedulerThreadProperties threadProperties = scheduler.schedule(
                () -> new SchedulerThreadProperties(Thread.currentThread().isDaemon(), Thread.currentThread().getName()),
                0,
                TimeUnit.MILLISECONDS
        ).get(5, TimeUnit.SECONDS);

        assertTrue(threadProperties.daemon());
        assertTrue(threadProperties.name().startsWith("TimeUtil-Scheduler"));
    }

    private record SchedulerThreadProperties(boolean daemon, String name) {
    }
}
