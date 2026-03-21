package link.locutus.discord.apiv1.enums;

import link.locutus.discord.db.entities.TransactionNote;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositTypeInfoTest {
    @Test
    void toTransactionNoteAddsParentAccountForChildType() {
        DepositTypeInfo info = new DepositTypeInfo(DepositType.CITY, 3, 0, false);

        TransactionNote note = info.toTransactionNote(42);

        assertEquals(42L, ((Number) note.get(DepositType.GRANT)).longValue());
        assertEquals(3L, ((Number) note.get(DepositType.CITY)).longValue());
        assertFalse(note.hasTag(DepositType.IGNORE));
    }

    @Test
    void toTransactionNoteKeepsIgnoreAccountOnClassifierWhenIgnored() {
        DepositTypeInfo info = new DepositTypeInfo(DepositType.CITY, 3, 0, true);

        TransactionNote note = info.toTransactionNote(42);

        assertEquals(3L, ((Number) note.get(DepositType.CITY)).longValue());
        assertEquals(42L, ((Number) note.get(DepositType.IGNORE)).longValue());
        assertFalse(note.hasTag(DepositType.GRANT));
    }

    @Test
    void toTransactionNoteUsesRootAccountValueAndIgnoreFlag() {
        DepositTypeInfo info = new DepositTypeInfo(DepositType.DEPOSIT, 0, 0, true);

        TransactionNote note = info.toTransactionNote(42);

        assertEquals(42L, ((Number) note.get(DepositType.DEPOSIT)).longValue());
        assertTrue(note.hasTag(DepositType.IGNORE));
        assertNull(note.get(DepositType.IGNORE));
    }

    @Test
    void typedRenderingMatchesLegacyStringForChildAccountNote() {
        DepositTypeInfo info = new DepositTypeInfo(DepositType.CITY, 3, 0, false);

        assertEquals(info.toString(42), info.toTransactionNote(42).toLegacyString());
    }

    @Test
    void typedRenderingMatchesLegacyStringForIgnoredChildAccountNote() {
        DepositTypeInfo info = new DepositTypeInfo(DepositType.CITY, 3, 0, true);

        assertEquals(info.toString(42), info.toTransactionNote(42).toLegacyString());
    }

    @Test
    void legacyRoundTripPreservesParentAccountSemantics() {
        DepositTypeInfo info = new DepositTypeInfo(DepositType.CITY, 3, 0, false);

        TransactionNote reparsed = TransactionNote.parseLegacy(info.toString(42), 0L);

        assertEquals(info.toTransactionNote(42), reparsed);
        assertEquals(42L, ((Number) reparsed.get(DepositType.GRANT)).longValue());
        assertEquals(3L, ((Number) reparsed.get(DepositType.CITY)).longValue());
        assertFalse(reparsed.hasTag(DepositType.IGNORE));
    }

    @Test
    void legacyRoundTripPreservesIgnoredChildAccountSemantics() {
        DepositTypeInfo info = new DepositTypeInfo(DepositType.CITY, 3, 0, true);

        TransactionNote reparsed = TransactionNote.parseLegacy(info.toString(42), 0L);

        assertEquals(info.toTransactionNote(42), reparsed);
        assertEquals(42L, ((Number) reparsed.get(DepositType.IGNORE)).longValue());
        assertEquals(3L, ((Number) reparsed.get(DepositType.CITY)).longValue());
        assertFalse(reparsed.hasTag(DepositType.GRANT));
    }
}

