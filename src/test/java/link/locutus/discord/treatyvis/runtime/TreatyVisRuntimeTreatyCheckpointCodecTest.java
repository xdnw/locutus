package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreatyVisRuntimeTreatyCheckpointCodecTest {
    @Test
    void encodesAndDecodesCheckpointEntriesInStableSortedOrder() {
        byte[] payload = TreatyVisRuntimeTreatyCheckpointCodec.encode(List.of(
                new TreatyVisRuntimeRepository.TreatyCheckpointEntry(9123, 4729, TreatyType.MDOAP, 6),
                new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 881, TreatyType.MDP, -1),
                new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 881, TreatyType.NAP, 4)
        ));
        List<TreatyVisRuntimeRepository.TreatyCheckpointEntry> decoded = TreatyVisRuntimeTreatyCheckpointCodec.decode(payload);

        assertEquals(List.of(
                new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 881, TreatyType.MDP, -1),
                new TreatyVisRuntimeRepository.TreatyCheckpointEntry(4729, 881, TreatyType.NAP, 4),
                new TreatyVisRuntimeRepository.TreatyCheckpointEntry(9123, 4729, TreatyType.MDOAP, 6)
        ), decoded);
    }

    @Test
    void rejectsCheckpointPayloadWithInvalidRowLength() {
        assertThrows(IllegalArgumentException.class, () -> TreatyVisRuntimeTreatyCheckpointCodec.decode(new byte[] {1, 2, 3}));
    }

    @Test
    void rejectsCheckpointPayloadWithInvalidTreatyTypeOrdinal() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Integer.BYTES + Byte.BYTES + Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(4729);
        buffer.putInt(881);
        buffer.put((byte) 127);
        buffer.putInt(-1);

        assertThrows(IllegalArgumentException.class, () -> TreatyVisRuntimeTreatyCheckpointCodec.decode(buffer.array()));
    }
}