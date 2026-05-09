package link.locutus.discord.treatyvis.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

final class TreatyVisRuntimeScoreRowCodec {
    private static final int ROW_BYTES = Integer.BYTES * 2;

    private TreatyVisRuntimeScoreRowCodec() {
    }

    static byte[] encode(List<TreatyVisRuntimeLegacyScoreImportService.ScoreRow> scoreRows) {
        ByteBuffer buffer = ByteBuffer.allocate(scoreRows.size() * ROW_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (TreatyVisRuntimeLegacyScoreImportService.ScoreRow scoreRow : scoreRows) {
            buffer.putInt(scoreRow.allianceId());
            buffer.putInt(scoreRow.quantizedScore());
        }
        return buffer.array();
    }

    static List<TreatyVisRuntimeLegacyScoreImportService.ScoreRow> decode(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return List.of();
        }
        if (payload.length % ROW_BYTES != 0) {
            throw new IllegalArgumentException("Invalid score row payload length: " + payload.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        List<TreatyVisRuntimeLegacyScoreImportService.ScoreRow> rows = new ArrayList<>(payload.length / ROW_BYTES);
        while (buffer.remaining() >= ROW_BYTES) {
            rows.add(new TreatyVisRuntimeLegacyScoreImportService.ScoreRow(buffer.getInt(), buffer.getInt()));
        }
        return List.copyOf(rows);
    }
}