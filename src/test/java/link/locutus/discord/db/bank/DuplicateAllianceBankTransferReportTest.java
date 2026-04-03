package link.locutus.discord.db.bank;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionEndpointKey;
import link.locutus.discord.db.entities.TransactionNote;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuplicateAllianceBankTransferReportTest {
    private static final int ALLIANCE_ID = 11009;
    private static final int SENDER_ALLIANCE_ID = 13809;
    private static final int BANKER_NATION_ID = 189573;
    private static final long ROUTE_TIME = Instant.parse("2026-03-20T00:12:16Z").toEpochMilli();

    @Test
    void collectMatchesAllowsIgnoreTaggedDirectTransferToNation() {
        double[] amount = new double[ResourceType.values.length];
        amount[ResourceType.GASOLINE.ordinal()] = 7_000d;
        amount[ResourceType.MUNITIONS.ordinal()] = 13_000d;
        amount[ResourceType.STEEL.ordinal()] = 13_000d;
        amount[ResourceType.ALUMINUM.ordinal()] = 4_000d;

        Transaction2 routeTx = transaction(
                1,
                ROUTE_TIME,
                SENDER_ALLIANCE_ID,
                TransactionEndpointKey.ALLIANCE_TYPE,
                ALLIANCE_ID,
                TransactionEndpointKey.ALLIANCE_TYPE,
                TransactionNote.of(DepositType.IGNORE),
                amount.clone()
        );
        Transaction2 directTx = transaction(
                2,
                ROUTE_TIME + 4_000L,
                SENDER_ALLIANCE_ID,
                TransactionEndpointKey.ALLIANCE_TYPE,
                575_967,
                TransactionEndpointKey.NATION_TYPE,
                TransactionNote.of(Map.of(
                        DepositType.IGNORE, (long) ALLIANCE_ID,
                        DepositType.BANKER, 547_627L
                )),
                amount.clone()
        );

        DuplicateAllianceBankTransferReport.CollectionResult result = DuplicateAllianceBankTransferReport.collectMatches(
                List.of(routeTx, directTx),
                ALLIANCE_ID,
                ROUTE_TIME - 60_000L,
                ROUTE_TIME + 60_000L
        );

        assertEquals(1, result.routeCandidateCount());
        assertEquals(1, result.matches().size());
        assertEquals(routeTx.tx_id, result.matches().get(0).routeTx().tx_id);
        assertEquals(directTx.tx_id, result.matches().get(0).duplicateTx().tx_id);
    }

    @Test
    void collectMatchesStillExcludesPlainIgnoreAllianceTransferAsDirectCounterpart() {
        double[] amount = new double[ResourceType.values.length];
        amount[ResourceType.MONEY.ordinal()] = 300_000_000d;

        Transaction2 routeTx = transaction(
                10,
                ROUTE_TIME,
                SENDER_ALLIANCE_ID,
                TransactionEndpointKey.ALLIANCE_TYPE,
                ALLIANCE_ID,
                TransactionEndpointKey.ALLIANCE_TYPE,
                TransactionNote.of(DepositType.IGNORE),
                amount.clone()
        );
        Transaction2 otherAllianceRouteTx = transaction(
                11,
                ROUTE_TIME + 4_000L,
                SENDER_ALLIANCE_ID,
                TransactionEndpointKey.ALLIANCE_TYPE,
                14_790,
                TransactionEndpointKey.ALLIANCE_TYPE,
                TransactionNote.of(DepositType.IGNORE),
                amount.clone()
        );

        DuplicateAllianceBankTransferReport.CollectionResult result = DuplicateAllianceBankTransferReport.collectMatches(
                List.of(routeTx, otherAllianceRouteTx),
                ALLIANCE_ID,
                ROUTE_TIME - 60_000L,
                ROUTE_TIME + 60_000L
        );

        assertEquals(1, result.routeCandidateCount());
        assertEquals(0, result.matches().size());
    }

    private static Transaction2 transaction(
            int txId,
            long txTime,
            long senderId,
            int senderType,
            long receiverId,
            int receiverType,
            TransactionNote note,
            double[] resources
    ) {
        return Transaction2.construct(
                txId,
                txTime,
                senderId,
                senderType,
                receiverId,
                receiverType,
                BANKER_NATION_ID,
                note,
                true,
                false,
                resources
        );
    }
}