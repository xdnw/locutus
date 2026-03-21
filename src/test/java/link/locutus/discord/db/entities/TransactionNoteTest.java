package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.web.commands.binding.value_types.WebTransaction;
import org.example.jooq.bank.tables.records.Transactions_2Record;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionNoteTest {
        private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;

        @Test
        void builderPreservesMultipleTagsAndCanonicalRendering() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CITY, 3L)
                                .put(DepositType.EXPIRE, 1234L)
                                .build();

                assertEquals("#grant #city=3 #expire=timestamp:1234", note.toLegacyString());
                assertEquals(DepositType.GRANT, note.primaryType());
                assertEquals("#grant #city=3", note.without(DepositType.EXPIRE, DepositType.DECAY).toLegacyString());
        }

        @Test
        void legacyParsingClassifiesStructuredTags() {
                TransactionNote note = TransactionNote.parseLegacy("#grant #city=3 #infra=1500 #land=2000", 100L);

                assertTrue(note.hasTag(DepositType.GRANT));
                assertEquals(3L, ((Number) note.get(DepositType.CITY)).longValue());
                assertEquals(1500d, ((Number) note.get(DepositType.INFRA)).doubleValue());
                assertEquals(2000d, ((Number) note.get(DepositType.LAND)).doubleValue());
        }

        @Test
        void builderMutationAddsCashAndRssWithoutDroppingExistingTags() {
                TransactionNote base = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CITY, 5L)
                                .build();

                TransactionNote mutated = base.toBuilder()
                                .put(DepositType.CASH, 42.5d)
                                .put(DepositType.RSS, 7L)
                                .build();

                assertEquals("#grant #city=5 #cash=42.5 #rss=7", mutated.toLegacyString());
                assertTrue(mutated.hasTag(DepositType.GRANT));
                assertTrue(mutated.hasTag(DepositType.CITY));
                assertEquals(42.5d, ((Number) mutated.get(DepositType.CASH)).doubleValue());
                assertEquals(7L, ((Number) mutated.get(DepositType.RSS)).longValue());
        }

        @Test
        void typedFlowShiftNotesRenderTheSameCanonicalLegacyStrings() {
                TransactionNote fromNote = TransactionNote.of(DepositType.TAX);
                TransactionNote toNote = TransactionNote.of(DepositType.DEPOSIT);

                assertEquals("#tax", fromNote.toLegacyString());
                assertEquals("#deposit", toNote.toLegacyString());
                assertEquals(TransactionNote.parseLegacy("#tax", 0L), fromNote);
                assertEquals(TransactionNote.parseLegacy("#deposit", 0L), toNote);
        }

        @Test
        void typedGrantShiftNotesMatchLegacyParsingWithExpireAndDecay() {
                long now = 1_700_000_000_000L;
                TransactionNote note = TransactionNote.of(DepositType.GRANT)
                                .toBuilder()
                                .put(DepositType.EXPIRE, now + 60_000L)
                                .put(DepositType.DECAY, now + 120_000L)
                                .build();

                assertEquals("#grant #expire=timestamp:1700000060000 #decay=timestamp:1700000120000",
                                note.toLegacyString());
                assertEquals(TransactionNote.parseLegacy(note.toLegacyString(), now), note);
        }

        @Test
        void structuredQueryParsingOnlyAcceptsPureNoteQueries() {
                TransactionNote structured = TransactionNote.parseStructuredQuery("#grant #expire=timestamp:1234");

                assertEquals("#grant #expire=timestamp:1234", structured.toLegacyString());
                assertTrue(TransactionNote.parseStructuredQuery("timestamp:1234").isEmpty());
                assertTrue(TransactionNote.parseStructuredQuery("grant note #expire=timestamp:1234").isEmpty());
        }

        @Test
        void containsAllMatchesStructuredSubsetsUsingCanonicalLegacyValues() {
                TransactionNote stored = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CASH, 42.0d)
                                .put(DepositType.BANKER, 99L)
                                .put(DepositType.EXPIRE, 1234L)
                                .build();

                assertTrue(stored.containsAll(TransactionNote.parseStructuredQuery("#grant #cash=42 #banker=99")));
                assertTrue(stored.containsAll(TransactionNote.parseStructuredQuery("#cash=42")));
                assertTrue(stored.containsAll(TransactionNote.parseStructuredQuery("#expire=timestamp:1234")));
                assertFalse(stored.containsAll(TransactionNote.parseStructuredQuery("#cash=41")));
                assertFalse(stored.containsAll(TransactionNote.parseStructuredQuery("#expire=timestamp:1235")));
                assertFalse(stored.containsAll(TransactionNote.parseStructuredQuery("#ignore")));
        }

        @Test
        void receiverEndpointTagsRoundTripThroughCanonicalLegacyRendering() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.DEPOSIT)
                                .put(DepositType.RECEIVER_ID, 321L)
                                .put(DepositType.RECEIVER_TYPE, 2L)
                                .build();

                assertEquals("#deposit #receiver_id=321 #receiver_type=2", note.toLegacyString());
                assertEquals(note, TransactionNote.parseLegacy(note.toLegacyString(), 0L));
        }

        @Test
        void legacyValueFormatsTypedEnumAndTimestampPayloadsCanonically() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.DEPOSIT)
                                .put(DepositType.INCENTIVE, NationMeta.REFERRER)
                                .put(DepositType.EXPIRE, 1234L)
                                .build();

                assertEquals("REFERRER", note.getLegacyValue(DepositType.INCENTIVE));
                assertEquals("timestamp:1234", note.getLegacyValue(DepositType.EXPIRE));
        }

        @Test
        void selfWithdrawalFallsBackToReceiverIdWhenBankerTagIsAbsent() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.DEPOSIT)
                                .put(DepositType.RECEIVER_ID, 777L)
                                .put(DepositType.RECEIVER_TYPE, 1L)
                                .build();
                Transaction2 tx = Transaction2.construct(1, 2L, 42L, 2, 99L, 1, 5, note, false, false,
                                new double[ResourceType.values.length]);

                assertTrue(tx.isSelfWithdrawal(777));
                assertFalse(tx.isSelfWithdrawal(778));
        }

        @Test
        void taggedAccountIdPrefersStructuredAccountTagsAndRootAccountValues() {
                Transaction2 rootTagged = Transaction2.construct(1, 2L, 10L, 2, 20L, 2, 5,
                                TransactionNote.builder().put(DepositType.DEPOSIT, 321L).build(), false, false,
                                new double[ResourceType.values.length]);
                Transaction2 explicitTagged = Transaction2.construct(1, 2L, 10L, 2, 20L, 2, 5,
                                TransactionNote.builder().put(DepositType.DEPOSIT).put(DepositType.GUILD, 654L).build(),
                                false, false, new double[ResourceType.values.length]);

                assertEquals(321L, rootTagged.getTaggedAccountId());
                assertEquals(321L, rootTagged.getAccountId(java.util.Collections.emptySet(), false));
                assertEquals(654L, explicitTagged.getTaggedAccountId());
        }

        @Test
        void taggedAccountIdIgnoresReservedNumericMetadataValues() {
                Transaction2 tx = Transaction2.construct(1, 2L, 10L, 2, 20L, 2, 5,
                                TransactionNote.builder()
                                                .put(DepositType.DEPOSIT)
                                                .put(DepositType.CASH, 42.5d)
                                                .put(DepositType.RECEIVER_ID, 777L)
                                                .build(),
                                false, false, new double[ResourceType.values.length]);

                assertEquals(0L, tx.getTaggedAccountId());
                assertEquals(0L, tx.getAccountId(java.util.Collections.emptySet(), false));
        }

        @Test
        void taggedAccountIdRejectsConflictingExplicitAccountTags() {
                Transaction2 tx = Transaction2.construct(1, 2L, 10L, 2, 20L, 2, 5,
                                TransactionNote.builder()
                                                .put(DepositType.ALLIANCE, 111L)
                                                .put(DepositType.GUILD, 222L)
                                                .build(),
                                false, false, new double[ResourceType.values.length]);

                assertEquals(0L, tx.getTaggedAccountId());
                assertEquals(0L, tx.getAccountId(java.util.Collections.emptySet(), false));
        }

        @Test
        void ignoreTaggedAccountStillUsesLegacyOwnerValueUntilIgnoreIsRequested() {
                Transaction2 tx = Transaction2.construct(1, 2L, 10L, 2, 20L, 2, 5,
                                TransactionNote.builder()
                                                .put(DepositType.IGNORE, 444L)
                                                .build(),
                                false, false, new double[ResourceType.values.length]);

                assertEquals(444L, tx.getTaggedAccountId());
                assertEquals(444L, tx.getAccountId(java.util.Collections.emptySet(), false));
                assertEquals(0L, tx.getAccountId(java.util.Collections.emptySet(), true));
        }

        @Test
        void stableHashWithoutExpireAndDecayIgnoresTimestampDifferences() {
                TransactionNote first = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CITY, 2L)
                                .put(DepositType.EXPIRE, 1000L)
                                .build();
                TransactionNote second = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CITY, 2L)
                                .put(DepositType.EXPIRE, 2000L)
                                .build();

                assertEquals(first.stableHashWithout(DepositType.EXPIRE, DepositType.DECAY),
                                second.stableHashWithout(DepositType.EXPIRE, DepositType.DECAY));
                assertFalse(first.stableHash() == second.stableHash());
        }

        @Test
        void stableHashMatchesHistoricalLegacyStringHashWithoutAllocatingLegacyStringInternally() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CITY, 3L)
                                .put(DepositType.EXPIRE, 1234L)
                                .put(DepositType.CASH, 42.5d)
                                .build();

                assertEquals(legacyFnvHash(note.toLegacyString()), note.stableHash());
        }

        @Test
        void webTransactionExposesRawSerializedPayload() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CITY, 4L)
                                .build();
                Transaction2 tx = Transaction2.construct(7, 9L, 11L, 1, 13L, 2, 17, note, false, false,
                                new double[ResourceType.values.length]);

                WebTransaction webTransaction = new WebTransaction(tx);

                assertArrayEquals(tx.getNoteBytes(), webTransaction.note);
        }

        @Test
        void legacyConstructionStillDerivesLootTransferWithoutRawCallerChecks() {
                Transaction2 tx = Transaction2.constructLegacy(1, 2L, 3L, 1, 4L, 2, 5,
                                "Alice defeated Bob's nation and captured.", new double[ResourceType.values.length]);

                assertTrue(tx.isLootTransfer());
                assertTrue(tx.note().hasData());
                assertNull(tx.getStructuredNote().toDisplayString());
        }

        @Test
        void lootTransferFlagSurvivesStructuredPersistenceWithoutRawNoteRoundTrip() {
                Transaction2 original = Transaction2.constructLegacy(1, 2L, 3L, 1, 4L, 2, 5,
                                "Alice defeated Bob's nation and captured.", new double[ResourceType.values.length]);
                Transactions_2Record record = new Transactions_2Record();
                original.set(record);

                Transaction2 restored = Transaction2.fromTX2Table(record, Transaction2.reusableNoteBuffer());

                assertNotSame(original, restored);
                assertTrue(restored.isLootTransfer());
                assertNull(restored.getLegacyNote());
        }

        @Test
        void rewritingStructuredNotePreservesLootTransferFlag() {
                Transaction2 original = Transaction2.constructLegacy(1, 2L, 3L, 1, 4L, 2, 5,
                                "Alice defeated Bob's nation and captured.", new double[ResourceType.values.length]);

                Transaction2 rewritten = original.withStructuredNote(
                                original.editNote().put(DepositType.RSS, 7L).build(),
                                original.isValidHash());

                assertTrue(rewritten.isLootTransfer());
                assertEquals(7L, ((Number) rewritten.getStructuredNote().get(DepositType.RSS)).longValue());
        }

        @Test
        void parsedNoteMapIsExposedAsTypedDtoData() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.TAX)
                                .put(DepositType.BANKER, 99L)
                                .build();

                Map<String, Object> data = note.toDataMap();

                assertTrue(data.containsKey("tax"));
                assertNull(data.get("tax"));
                assertEquals(99L, ((Number) data.get("banker")).longValue());
                assertNotNull(data);
        }

        @Test
        void enumValuedStructuredNotesSurvivePersistence() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.DEPOSIT)
                                .put(DepositType.INCENTIVE, NationMeta.REFERRER)
                                .build();
                Transaction2 tx = Transaction2.construct(7, 9L, 11L, 1, 13L, 2, 17, note, false, false,
                                new double[ResourceType.values.length]);
                Transactions_2Record record = new Transactions_2Record();
                tx.set(record);

                Transaction2 restored = Transaction2.fromTX2Table(record, Transaction2.reusableNoteBuffer());

                assertEquals(NationMeta.REFERRER, restored.getStructuredNote().get(DepositType.INCENTIVE));
                assertEquals("#deposit #incentive=REFERRER", restored.getLegacyNote());
        }

        @Test
        void payloadRoundTripPreservesEmbeddedResources() {
                TransactionNote note = TransactionNote.builder()
                                .put(DepositType.GRANT)
                                .put(DepositType.CITY, 2L)
                                .build();
                double[] resources = new double[ResourceType.values.length];
                resources[ResourceType.MONEY.ordinal()] = 12.34d;
                resources[ResourceType.STEEL.ordinal()] = -5.5d;
                Transaction2 tx = Transaction2.construct(7, 9L, 11L, 1, 13L, 2, 17, note, false, false, resources);
                Transactions_2Record record = new Transactions_2Record();
                tx.set(record);

                Transaction2 restored = Transaction2.fromTX2Table(record, Transaction2.reusableNoteBuffer());

                assertEquals(12.34d, restored.resources[ResourceType.MONEY.ordinal()]);
                assertEquals(-5.5d, restored.resources[ResourceType.STEEL.ordinal()]);
                assertEquals("#grant #city=2", restored.getLegacyNote());
        }

        @Test
        void legacyParsingPreservesEnumValuedNotePayloadText() {
                TransactionNote note = TransactionNote.parseLegacy("#deposit #incentive=REFERRER", 0L);

                assertEquals("REFERRER", note.get(DepositType.INCENTIVE));
                assertEquals("#deposit #incentive=REFERRER", note.toLegacyString());
        }

        @Test
        void parseTransferHashNotesCompatibilityHelperPreservesEnumPayloadText() {
                Map<DepositType, Object> parsed = Transaction2.parseTransferHashNotes("#deposit #incentive=REFERRER",
                                0L);

                assertTrue(parsed.containsKey(DepositType.DEPOSIT));
                assertEquals("REFERRER", parsed.get(DepositType.INCENTIVE));
                assertEquals(TransactionNote.parseLegacy("#deposit #incentive=REFERRER", 0L).asMap(), parsed);
        }

        private static long legacyFnvHash(String legacy) {
                long hash = FNV_OFFSET_BASIS;
                for (int i = 0; i < legacy.length(); i++) {
                        hash ^= legacy.charAt(i);
                        hash *= FNV_PRIME;
                }
                return hash;
        }
}
