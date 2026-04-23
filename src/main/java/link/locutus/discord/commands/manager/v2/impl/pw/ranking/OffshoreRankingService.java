package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

public final class OffshoreRankingService {
    private OffshoreRankingService() {
    }

    public record PotentialRequest(int allianceId, long cutoffMs, boolean transferCount) {
        public PotentialRequest {
            if (allianceId <= 0) {
                throw new IllegalArgumentException("allianceId must be positive");
            }
            if (cutoffMs < 0L) {
                throw new IllegalArgumentException("cutoffMs cannot be negative");
            }
        }
    }

    public record ProlificRequest(long cutoffMs) {
        public ProlificRequest {
            if (cutoffMs < 0L) {
                throw new IllegalArgumentException("cutoffMs cannot be negative");
            }
        }
    }

    public static RankingResult potentialOffshoreRanking(PotentialRequest request) {
        Objects.requireNonNull(request, "request");
        List<Transaction2> transactions = Locutus.imp().getBankDB().getToNationTransactions(request.cutoffMs());
        return potentialOffshoreRanking(request, transactions);
    }

    static RankingResult potentialOffshoreRanking(PotentialRequest request, List<Transaction2> transactions) {
        return potentialOffshoreRanking(request, transactions, OffshoreRankingService::allianceIdForNation);
    }

    static RankingResult potentialOffshoreRanking(
            PotentialRequest request,
            List<Transaction2> transactions,
            IntUnaryOperator receiverAllianceResolver
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(receiverAllianceResolver, "receiverAllianceResolver");
        Int2IntOpenHashMap transactionCounts = new Int2IntOpenHashMap();
        Int2DoubleOpenHashMap transferValues = new Int2DoubleOpenHashMap();
        long asOfMs = 0L;

        long now = System.currentTimeMillis();
        if (transactions != null) {
            for (Transaction2 transaction : transactions) {
                if (transaction == null || !transaction.isReceiverNation()) {
                    continue;
                }
                int receiverAllianceId = receiverAllianceResolver.applyAsInt((int) transaction.getReceiver());
                if (receiverAllianceId == 0
                        || transaction.getDate() > now
                        || transaction.getSender() == request.allianceId()
                        || receiverAllianceId != request.allianceId()
                        || transaction.isLootTransfer()) {
                    continue;
                }
                int senderAllianceId = (int) transaction.getSender();
                transactionCounts.addTo(senderAllianceId, 1);
                transferValues.addTo(senderAllianceId, ResourceType.convertedTotal(transaction.resources));
                asOfMs = Math.max(asOfMs, transaction.getDate());
            }
        }

        if (request.transferCount()) {
            return RankingBuilders.singleMetricRanking(
                    RankingKind.POTENTIAL_OFFSHORES,
                    RankingEntityType.ALLIANCE,
                    RankingValueFormat.COUNT,
                    List.of(RankingBuilders.singleMetricSection(
                            RankingSectionKind.ALLIANCES,
                            RankingSortDirection.DESC,
                            transactionCounts
                    )),
                    null,
                    asOfMs == 0L ? System.currentTimeMillis() : asOfMs
            );
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.POTENTIAL_OFFSHORES,
                RankingEntityType.ALLIANCE,
                RankingValueFormat.MONEY,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        transferValues
                )),
                null,
                asOfMs == 0L ? System.currentTimeMillis() : asOfMs
        );
    }

    private static int allianceIdForNation(int nationId) {
        DBNation nation = DBNation.getById(nationId);
        return nation == null ? 0 : nation.getAlliance_id();
    }

    public static RankingResult prolificOffshoreRanking(ProlificRequest request) {
        Objects.requireNonNull(request, "request");

        Map<Integer, Long> nationCountByAlliance = new HashMap<>();
        Map<Integer, DBNation> nationsById = Locutus.imp().getNationDB().getNationsById();
        for (DBNation nation : nationsById.values()) {
            nationCountByAlliance.merge(nation.getAlliance_id(), 1L, Long::sum);
        }
        nationCountByAlliance.entrySet().removeIf(entry -> entry.getValue() > 2L);

        Int2DoubleOpenHashMap transferValues = new Int2DoubleOpenHashMap();
        long asOfMs = 0L;
        for (Integer allianceId : nationCountByAlliance.keySet()) {
            List<Transaction2> transfers = Locutus.imp().getBankDB().getAllianceTransfers(allianceId, request.cutoffMs());
            double sum = 0d;
            if (transfers != null) {
                for (Transaction2 transfer : transfers) {
                    if (transfer.banker_nation == transfer.getReceiver()) {
                        continue;
                    }
                    DBNation nation = nationsById.get((int) transfer.getReceiver());
                    if (nation == null || nation.getAlliance_id() == transfer.getSender()) {
                        continue;
                    }
                    sum += Math.abs(ResourceType.convertedTotal(transfer.resources));
                    asOfMs = Math.max(asOfMs, transfer.getDate());
                }
            }
            if (sum > 0d) {
                transferValues.put(allianceId.intValue(), sum);
            }
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.PROLIFIC_OFFSHORES,
                RankingEntityType.ALLIANCE,
                RankingValueFormat.MONEY,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        transferValues
                )),
                null,
                asOfMs == 0L ? System.currentTimeMillis() : asOfMs
        );
    }
}
