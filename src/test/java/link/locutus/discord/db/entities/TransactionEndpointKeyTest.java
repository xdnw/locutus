package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.example.jooq.bank.tables.records.Transactions_2Record;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionEndpointKeyTest {
    @Test
    void endpointKeyRoundTripsAllSupportedEndpointTypes() {
        assertRoundTrip(0L, 0, 0L);
        assertRoundTrip(123L, 1, TransactionEndpointKey.encode(123L, 1));
        assertRoundTrip(456L, 2, TransactionEndpointKey.encode(456L, 2));
        assertRoundTrip(987654321012345678L, 3, TransactionEndpointKey.encode(987654321012345678L, 3));
        assertRoundTrip(789L, 4, TransactionEndpointKey.encode(789L, 4));
    }

    @Test
    void endpointKeyValidationRejectsUnsupportedOrAmbiguousInputs() {
        assertThrows(IllegalArgumentException.class, () -> TransactionEndpointKey.encode(1L, 0));
        assertThrows(IllegalArgumentException.class, () -> TransactionEndpointKey.encode(0L, 1));
        assertThrows(IllegalArgumentException.class, () -> TransactionEndpointKey.encode(-1L, 1));
        assertThrows(IllegalArgumentException.class, () -> TransactionEndpointKey.encode(1L, 5));
    }

    @Test
    void transactionPersistenceRoundTripPreservesTaxAndNoneEndpoints() {
        TransactionNote note = TransactionNote.of(DepositType.TAX);
        Transaction2 tx = Transaction2.construct(7, 9L, 11L, 4, 0L, 0, 17, note, false, false,
                new double[ResourceType.values.length]);
        Transactions_2Record record = new Transactions_2Record();
        tx.set(record);

        assertEquals(TransactionEndpointKey.encode(11L, 4), record.getSenderKey());
        assertEquals(TransactionEndpointKey.NONE, record.getReceiverKey());

        Transaction2 restored = Transaction2.fromTX2Table(record, Transaction2.reusableNoteBuffer());

        assertEquals(11L, restored.sender_id);
        assertEquals(4, restored.sender_type);
        assertEquals(0L, restored.receiver_id);
        assertEquals(0, restored.receiver_type);
        assertEquals("#tax", restored.getLegacyNote());
    }

    private static void assertRoundTrip(long id, int type, long expectedKey) {
        long key = TransactionEndpointKey.encode(id, type);
        assertEquals(expectedKey, key);
        assertEquals(id, TransactionEndpointKey.idFromKey(key));
        assertEquals(type, TransactionEndpointKey.typeFromKey(key));
    }
}

