package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TreatyVisRuntimeTreatyCheckpointCodec {
    private static final int ROW_BYTES = Integer.BYTES + Integer.BYTES + Byte.BYTES + Integer.BYTES;

    private TreatyVisRuntimeTreatyCheckpointCodec() {
    }

    static byte[] encode(List<TreatyVisRuntimeRepository.TreatyCheckpointEntry> entries) {
        List<TreatyVisRuntimeRepository.TreatyCheckpointEntry> ordered = entries.stream()
                .sorted(Comparator
                        .comparingInt(TreatyVisRuntimeRepository.TreatyCheckpointEntry::fromAllianceId)
                        .thenComparingInt(TreatyVisRuntimeRepository.TreatyCheckpointEntry::toAllianceId)
                        .thenComparing(entry -> entry.treatyType().ordinal()))
                .toList();
        ByteBuffer buffer = ByteBuffer.allocate(ordered.size() * ROW_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (TreatyVisRuntimeRepository.TreatyCheckpointEntry entry : ordered) {
            buffer.putInt(entry.fromAllianceId());
            buffer.putInt(entry.toAllianceId());
            buffer.put((byte) entry.treatyType().ordinal());
            buffer.putInt(entry.turnsRemaining());
        }
        return buffer.array();
    }

    static List<TreatyVisRuntimeRepository.TreatyCheckpointEntry> decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return List.of();
        }
        if (payload.length % ROW_BYTES != 0) {
            throw new IllegalArgumentException("Invalid treaty checkpoint payload length: " + payload.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        List<TreatyVisRuntimeRepository.TreatyCheckpointEntry> entries = new ArrayList<>(payload.length / ROW_BYTES);
        while (buffer.remaining() >= ROW_BYTES) {
            int fromAllianceId = buffer.getInt();
            int toAllianceId = buffer.getInt();
            int treatyTypeOrdinal = Byte.toUnsignedInt(buffer.get());
            if (treatyTypeOrdinal >= TreatyType.values.length) {
                throw new IllegalArgumentException("Invalid treaty checkpoint treaty type ordinal: " + treatyTypeOrdinal);
            }
            int turnsRemaining = buffer.getInt();
            entries.add(new TreatyVisRuntimeRepository.TreatyCheckpointEntry(
                    fromAllianceId,
                    toAllianceId,
                    TreatyType.values[treatyTypeOrdinal],
                    turnsRemaining
            ));
        }
        return List.copyOf(entries);
    }
}