package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.SQLUtil;
import link.locutus.discord.util.io.BitBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionPayloadDecodeDebugTest {
    private static final String FAILING_HEX = "0c0818da3b2614301e361464fc1a5e98bc00";

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

    @Test
    void malformedRoundedZeroResourcePayloadStillDecodes() {
        long senderKey = TransactionEndpointKey.encode(13809L, TransactionEndpointKey.ALLIANCE_TYPE);
        long receiverKey = TransactionEndpointKey.encode(189573L, TransactionEndpointKey.NATION_TYPE);

        Transaction2 restored = Transaction2.fromStoredPayload(
                133729137,
                1_774_881_528_000L,
                senderKey,
                receiverKey,
                189573,
                SQLUtil.hexStringToByteArray(FAILING_HEX),
                Transaction2.createNoteBuffer()
        );

        assertEquals("#ignore=13809 #banker=189573", restored.getLegacyNote());
        assertEquals(95_594.27d, restored.resources[ResourceType.FOOD.ordinal()], 0.00001d);
        assertEquals(1_069.5d, restored.resources[ResourceType.URANIUM.ordinal()], 0.00001d);
        assertEquals(0d, restored.resources[ResourceType.MONEY.ordinal()], 0d);

        byte[] canonical = Transaction2.canonicalizeStoredPayload(
                SQLUtil.hexStringToByteArray(FAILING_HEX),
                Transaction2.createNoteBuffer()
        );

        assertTrue(!FAILING_HEX.equals(SQLUtil.byteArrayToHexString(canonical)));

        Transaction2 repaired = Transaction2.fromStoredPayload(
                133729137,
                1_774_881_528_000L,
                senderKey,
                receiverKey,
                189573,
                canonical,
                Transaction2.createNoteBuffer(),
                false
        );

        assertEquals("#ignore=13809 #banker=189573", repaired.getLegacyNote());
        assertEquals(95_594.27d, repaired.resources[ResourceType.FOOD.ordinal()], 0.00001d);
        assertEquals(1_069.5d, repaired.resources[ResourceType.URANIUM.ordinal()], 0.00001d);
        assertEquals(0d, repaired.resources[ResourceType.MONEY.ordinal()], 0d);
    }

    @Test
    void roundedZeroResourcesDoNotProduceMalformedPayloadsOnWrite() {
        TransactionNote note = TransactionNote.parseStructuredQuery("#ignore=13809 #banker=189573");
        double[] resources = new double[ResourceType.values.length];
        resources[ResourceType.MONEY.ordinal()] = 0.004d;
        resources[ResourceType.FOOD.ordinal()] = 95_594.27d;
        resources[ResourceType.URANIUM.ordinal()] = 1_069.5d;

        Transaction2 tx = Transaction2.construct(
                133729137,
                1_774_881_528_000L,
                13809L,
                TransactionEndpointKey.ALLIANCE_TYPE,
                189573L,
                TransactionEndpointKey.NATION_TYPE,
                189573,
                note,
                false,
                false,
                resources
        );

        byte[] payload = tx.getNoteBytes(Transaction2.createNoteBuffer());
        assertTrue(!FAILING_HEX.equals(SQLUtil.byteArrayToHexString(payload)));

        Transaction2 restored = Transaction2.fromStoredPayload(
                tx.tx_id,
                tx.tx_datetime,
                tx.getSenderKey(),
                tx.getReceiverKey(),
                tx.banker_nation,
                payload,
                Transaction2.createNoteBuffer()
        );

        assertEquals("#ignore=13809 #banker=189573", restored.getLegacyNote());
        assertEquals(95_594.27d, restored.resources[ResourceType.FOOD.ordinal()], 0.00001d);
        assertEquals(1_069.5d, restored.resources[ResourceType.URANIUM.ordinal()], 0.00001d);
        assertEquals(0d, restored.resources[ResourceType.MONEY.ordinal()], 0d);
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
