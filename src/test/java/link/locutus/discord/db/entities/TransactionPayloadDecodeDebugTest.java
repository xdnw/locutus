package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.db.SQLUtil;
import link.locutus.discord.util.io.BitBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionPayloadDecodeDebugTest {
    @Test
    void invalidDepositTypeOrdinalReportsTransactionContextAndRawPayload() {
        byte[] payload = invalidOrdinalPayload(DepositType.values.length);
        long senderKey = TransactionEndpointKey.encode(123L, TransactionEndpointKey.ALLIANCE_TYPE);
        long receiverKey = TransactionEndpointKey.encode(456L, TransactionEndpointKey.NATION_TYPE);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                Transaction2.fromStoredPayload(
                        789,
                        1_234_567_890L,
                        senderKey,
                        receiverKey,
                        999,
                        payload,
                        Transaction2.createNoteBuffer()
                ));

        assertTrue(failure.getMessage().contains("txId=789"));
        assertTrue(failure.getMessage().contains("txDatetime=1234567890"));
        assertTrue(failure.getMessage().contains("senderKey=" + senderKey));
        assertTrue(failure.getMessage().contains("receiverKey=" + receiverKey));
        assertTrue(failure.getMessage().contains("noteLength=" + payload.length));
        assertTrue(failure.getMessage().contains("noteHex=" + SQLUtil.byteArrayToHexString(payload)));
        assertTrue(failure.getMessage().contains("ordinal " + DepositType.values.length));

        assertNotNull(failure.getCause());
        assertTrue(failure.getCause().getMessage().contains(
                "Unsupported transaction note deposit type ordinal " + DepositType.values.length));
    }

    private static byte[] invalidOrdinalPayload(int invalidOrdinal) {
        BitBuffer buffer = Transaction2.createNoteBuffer();
        buffer.reset();
        buffer.writeBit(false);
        buffer.writeBit(false);
        buffer.writeVarInt(0);
        buffer.writeBit(true);
        buffer.writeBits(1, 5);
        buffer.writeBits(invalidOrdinal, 5);
        buffer.writeBit(false);
        return buffer.getWrittenBytes();
    }
}
