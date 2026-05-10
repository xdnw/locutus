package link.locutus.discord.treatyvis.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TreatyHistoryServiceTest {
    @Test
    void normalizesSparseFlagIconIndexesToDenseSequence() {
        byte[] alpha = new byte[] {1};
        byte[] beta = new byte[] {2};
        byte[] gamma = new byte[] {3};

        Map<Integer, byte[]> normalized = TreatyHistoryService.normalizeDenseFlagIconIndexMap(Map.of(
                8093, gamma,
                1, alpha,
                57, beta,
                0, new byte[] {9}
        ));

        assertEquals(3, normalized.size());
        assertArrayEquals(alpha, normalized.get(1));
        assertArrayEquals(beta, normalized.get(2));
        assertArrayEquals(gamma, normalized.get(3));
    }
}