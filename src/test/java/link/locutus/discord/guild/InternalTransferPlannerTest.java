package link.locutus.discord.guild;

import link.locutus.discord.db.guild.InternalTransferPlanner;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InternalTransferPlannerTest {

    private static final long G1 = 1001L;
    private static final long G2 = 2002L;

    private static final long A1 = 101L;
    private static final long A2 = 102L;
    private static final long A3 = 201L;

    private static final long O1 = 9001L;
    private static final long O2 = 9002L;

    private static final int N1 = 1;
    private static final int N2 = 2;

    private static InternalTransferPlanner.BackingAccount guild(long id) {
        return InternalTransferPlanner.BackingAccount.guild(id);
    }

    private static InternalTransferPlanner.BackingAccount alliance(long id) {
        return InternalTransferPlanner.BackingAccount.alliance(id);
    }

    private static InternalTransferPlanner.Plan plan(
            long senderGuildId,
            Integer senderNationId,
            InternalTransferPlanner.BackingAccount senderBacking,
            long receiverGuildId,
            Integer receiverNationId,
            InternalTransferPlanner.BackingAccount receiverBacking,
            long senderOffshoreId,
            long receiverOffshoreId
    ) {
        return InternalTransferPlanner.plan(
                new InternalTransferPlanner.Request(
                        senderGuildId,
                        senderNationId,
                        senderBacking,
                        receiverGuildId,
                        receiverNationId,
                        receiverBacking,
                        senderOffshoreId,
                        receiverOffshoreId
                )
        );
    }

    private static InternalTransferPlanner.Plan plan(
            Placement sender,
            Integer senderNationId,
            Placement receiver,
            Integer receiverNationId
    ) {
        return plan(
                sender.guildId,
                senderNationId,
                sender.backing,
                receiver.guildId,
                receiverNationId,
                receiver.backing,
                sender.offshoreId,
                receiver.offshoreId
        );
    }

    @Test
    public void sameBacking_nationToSameAllianceAccount_isDonationOnly() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, alliance(A1),
                G1, null, alliance(A1),
                O1, O1
        );

        assertTrue(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertFalse(p.debitSenderBacking);
        assertFalse(p.creditReceiverBacking);
        assertFalse(p.creditReceiverNation);
        assertFalse(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void sameBacking_nationToSameGuildAccount_isDonationOnly() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, guild(G1),
                G1, null, guild(G1),
                O1, O1
        );

        assertTrue(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertFalse(p.debitSenderBacking);
        assertFalse(p.creditReceiverBacking);
        assertFalse(p.creditReceiverNation);
        assertFalse(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void sameBacking_accountToNation_debitsBackingAndCreditsNation() {
        InternalTransferPlanner.Plan p = plan(
                G1, null, alliance(A1),
                G1, N2, alliance(A1),
                O1, O1
        );

        assertTrue(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertFalse(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertFalse(p.creditReceiverBacking);
        assertTrue(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void sameBacking_nationToNation_movesOnlyLocalBalances() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, alliance(A1),
                G1, N2, alliance(A1),
                O1, O1
        );

        assertTrue(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertFalse(p.debitSenderBacking);
        assertFalse(p.creditReceiverBacking);
        assertTrue(p.creditReceiverNation);
        assertFalse(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void differentBacking_sameOffshore_nationToNation_movesBackingAndLocal() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, alliance(A1),
                G1, N2, alliance(A2),
                O1, O1
        );

        assertFalse(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertTrue(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void differentBacking_sameOffshore_nationToAccount_movesBackingWithoutNationCredit() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, alliance(A1),
                G1, null, alliance(A2),
                O1, O1
        );

        assertFalse(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertFalse(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void differentBacking_sameOffshore_accountToNation_movesBackingAndNationCredit() {
        InternalTransferPlanner.Plan p = plan(
                G1, null, alliance(A1),
                G1, N2, alliance(A2),
                O1, O1
        );

        assertFalse(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertFalse(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertTrue(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void guildToAlliance_sameOffshore_movesBackingOnly() {
        InternalTransferPlanner.Plan p = plan(
                G1, null, guild(G1),
                G1, null, alliance(A1),
                O1, O1
        );

        assertFalse(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertFalse(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertFalse(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void allianceToGuild_sameOffshore_movesBackingOnly() {
        InternalTransferPlanner.Plan p = plan(
                G1, null, alliance(A1),
                G1, null, guild(G1),
                O1, O1
        );

        assertFalse(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertFalse(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertFalse(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void allianceToAlliance_crossOffshore_movesBackingAndCrossesOffshore() {
        InternalTransferPlanner.Plan p = plan(
                G1, null, alliance(A1),
                G2, null, alliance(A3),
                O1, O2
        );

        assertFalse(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertFalse(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertFalse(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertTrue(p.crossesOffshore);
    }

    @Test
    public void nationToNation_crossOffshore_movesBackingAndCrossesOffshore() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, alliance(A1),
                G2, N2, alliance(A3),
                O1, O2
        );

        assertFalse(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertTrue(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertTrue(p.crossesOffshore);
    }

    @Test
    public void sameAllianceDifferentGuild_isSameBacking_notSameNationBalance() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, alliance(A1),
                G2, N2, alliance(A1),
                O1, O1
        );

        assertTrue(p.sameBacking);
        assertFalse(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertFalse(p.debitSenderBacking);
        assertFalse(p.creditReceiverBacking);
        assertTrue(p.creditReceiverNation);
        assertFalse(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void sameNationSameGuild_differentBacking_setsSameNationBalance_andSuppressesReceiverNationCredit() {
        InternalTransferPlanner.Plan p = plan(
                G1, N1, guild(G1),
                G1, N1, alliance(A1),
                O1, O1
        );

        assertFalse(p.sameBacking);
        assertTrue(p.sameNationBalance);
        assertTrue(p.debitSenderNation);
        assertTrue(p.debitSenderBacking);
        assertTrue(p.creditReceiverBacking);
        assertFalse(p.creditReceiverNation);
        assertTrue(p.movesBacking);
        assertFalse(p.crossesOffshore);
    }

    @Test
    public void sameBacking_backingToBacking_isRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                plan(
                        G1, null, alliance(A1),
                        G1, null, alliance(A1),
                        O1, O1
                )
        );

        assertNotNull(ex.getMessage());
    }

    @Test
    public void sameGuild_sameNation_sameBacking_isRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                plan(
                        G1, N1, alliance(A1),
                        G1, N1, alliance(A1),
                        O1, O1
                )
        );

        assertNotNull(ex.getMessage());
    }

    @Test
    public void exhaustive_truthTable_matchesPlannerFormulas() {
        List<Placement> placements = List.of(
                new Placement(G1, guild(G1), O1),
                new Placement(G1, alliance(A1), O1),
                new Placement(G1, alliance(A2), O1),

                // same alliance backing, different guild
                new Placement(G2, alliance(A1), O1),

                new Placement(G2, guild(G2), O2),
                new Placement(G2, alliance(A3), O2)
        );

        List<Integer> senderNationOptions = Arrays.asList(null, N1, N2);
        List<Integer> receiverNationOptions = Arrays.asList(null, N1, N2);

        for (Placement sender : placements) {
            for (Placement receiver : placements) {
                for (Integer senderNation : senderNationOptions) {
                    for (Integer receiverNation : receiverNationOptions) {

                        boolean expectedSameBacking =
                                sender.backing.kind == receiver.backing.kind
                                        && sender.backing.id == receiver.backing.id;

                        boolean expectedSameNationBalance =
                                senderNation != null
                                        && receiverNation != null
                                        && sender.guildId == receiver.guildId
                                        && senderNation.equals(receiverNation);

                        boolean shouldThrow =
                                expectedSameBacking && (
                                        (senderNation == null && receiverNation == null)
                                                || expectedSameNationBalance
                                );

                        String desc = describe(sender, senderNation, receiver, receiverNation);

                        if (shouldThrow) {
                            assertThrows(IllegalArgumentException.class, () ->
                                            plan(sender, senderNation, receiver, receiverNation),
                                    desc
                            );
                            continue;
                        }

                        InternalTransferPlanner.Plan p = plan(sender, senderNation, receiver, receiverNation);

                        boolean expectedDebitSenderNation = senderNation != null;
                        boolean expectedDebitSenderBacking = senderNation == null || !expectedSameBacking;
                        boolean expectedCreditReceiverBacking = !expectedSameBacking;
                        boolean expectedCreditReceiverNation = receiverNation != null && !expectedSameNationBalance;
                        boolean expectedMovesBacking = expectedDebitSenderBacking || expectedCreditReceiverBacking;
                        boolean expectedCrossesOffshore = expectedMovesBacking && sender.offshoreId != receiver.offshoreId;

                        assertEquals(expectedSameBacking, p.sameBacking, desc + " sameBacking");
                        assertEquals(expectedSameNationBalance, p.sameNationBalance, desc + " sameNationBalance");
                        assertEquals(expectedDebitSenderNation, p.debitSenderNation, desc + " debitSenderNation");
                        assertEquals(expectedDebitSenderBacking, p.debitSenderBacking, desc + " debitSenderBacking");
                        assertEquals(expectedCreditReceiverBacking, p.creditReceiverBacking, desc + " creditReceiverBacking");
                        assertEquals(expectedCreditReceiverNation, p.creditReceiverNation, desc + " creditReceiverNation");
                        assertEquals(expectedMovesBacking, p.movesBacking, desc + " movesBacking");
                        assertEquals(expectedCrossesOffshore, p.crossesOffshore, desc + " crossesOffshore");

                        // Additional invariant checks
                        assertFalse(
                                p.creditReceiverBacking && !p.debitSenderBacking,
                                desc + " receiver backing credit without sender backing debit"
                        );

                        assertFalse(
                                p.crossesOffshore && !p.movesBacking,
                                desc + " crossesOffshore requires movesBacking"
                        );

                        assertFalse(
                                p.creditReceiverNation && p.sameNationBalance,
                                desc + " creditReceiverNation must be false when sameNationBalance is true"
                        );

                        if (p.sameBacking) {
                            assertFalse(
                                    p.creditReceiverBacking,
                                    desc + " same-backing transfer must not credit receiver backing"
                            );
                        }

                        if (p.sameBacking && senderNation != null && receiverNation == null) {
                            assertFalse(
                                    p.debitSenderBacking,
                                    desc + " same-backing nation->backing must not debit sender backing"
                            );
                            assertFalse(
                                    p.creditReceiverBacking,
                                    desc + " same-backing nation->backing must not credit receiver backing"
                            );
                        }
                    }
                }
            }
        }
    }

    private static String describe(
            Placement sender,
            Integer senderNation,
            Placement receiver,
            Integer receiverNation
    ) {
        return "sender=" + sender
                + ", senderNation=" + senderNation
                + ", receiver=" + receiver
                + ", receiverNation=" + receiverNation;
    }

    private static final class Placement {
        final long guildId;
        final InternalTransferPlanner.BackingAccount backing;
        final long offshoreId;

        Placement(long guildId, InternalTransferPlanner.BackingAccount backing, long offshoreId) {
            this.guildId = guildId;
            this.backing = backing;
            this.offshoreId = offshoreId;
        }

        @Override
        public String toString() {
            return "Placement{guildId=" + guildId
                    + ", backing=" + backing
                    + ", offshoreId=" + offshoreId + "}";
        }
    }
}
