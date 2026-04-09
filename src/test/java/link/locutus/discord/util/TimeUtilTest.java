package link.locutus.discord.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
