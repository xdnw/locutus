package link.locutus.discord.db.bank;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionEndpointKey;
import link.locutus.discord.db.entities.TransactionNote;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public final class DuplicateAllianceBankTransferReport {
    public static final long MATCH_WINDOW_MS = TimeUnit.MINUTES.toMillis(5);
    private static final TransactionNote ROUTE_NOTE = TransactionNote.of(DepositType.IGNORE);

    private final String primaryMessage;
    private final String detailFileName;
    private final String detailFileContent;

    private DuplicateAllianceBankTransferReport(String primaryMessage, String detailFileName, String detailFileContent) {
        this.primaryMessage = primaryMessage;
        this.detailFileName = detailFileName;
        this.detailFileContent = detailFileContent;
    }

    public static DuplicateAllianceBankTransferReport analyze(BankDB bankDb, int allianceId, long start, long end) {
        if (end < start) {
            throw new IllegalArgumentException("End timestamp must be after start timestamp");
        }

        String allianceLabel = formatAllianceLabel(allianceId);
        List<Transaction2> candidateTransactions = bankDb.getPotentialDuplicateAllianceBankTransfers(
                allianceId,
                start,
                end,
                MATCH_WINDOW_MS
        );
        if (candidateTransactions.isEmpty()) {
            return withoutDetail(noRouteMessage(allianceLabel, start, end));
        }

        CollectionResult result = collectMatches(candidateTransactions, allianceId, start, end);
        if (result.routeCandidateCount() == 0) {
            return withoutDetail(noRouteMessage(allianceLabel, start, end));
        }
        if (result.matches().isEmpty()) {
            return withoutDetail(noMatchesMessage(allianceLabel, start, end, result.routeCandidateCount()));
        }

        String detail = buildDetail(allianceLabel, start, end, result.routeCandidateCount(), result.matches());
        String summary = "Found `" + result.matches().size() + "` likely duplicate routed transfers for `"
                + allianceLabel + "` between `" + TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, start)
                + "` and `" + TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, end)
                + "` after checking `" + result.routeCandidateCount() + "` routed alliance-bank transfers.";
        return withDetail(summary, "duplicate_route_transfers_aa_" + allianceId + ".txt", detail);
    }

    public String primaryMessage() {
        return primaryMessage;
    }

    public boolean hasDetailFile() {
        return detailFileContent != null;
    }

    public String detailFileName() {
        return detailFileName;
    }

    public String detailFileContent() {
        return detailFileContent;
    }

    private static DuplicateAllianceBankTransferReport withoutDetail(String message) {
        return new DuplicateAllianceBankTransferReport(message, null, null);
    }

    private static DuplicateAllianceBankTransferReport withDetail(String message, String fileName, String fileContent) {
        return new DuplicateAllianceBankTransferReport(message, fileName, fileContent);
    }

    private static String noRouteMessage(String allianceLabel, long start, long end) {
        return "No routed alliance-bank transfers found for `" + allianceLabel + "` between `"
                + TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, start) + "` and `"
                + TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, end) + "`.";
    }

    private static String noMatchesMessage(String allianceLabel, long start, long end, int routeCandidateCount) {
        return "Checked `" + routeCandidateCount + "` routed alliance-bank transfers for `" + allianceLabel
                + "` between `" + TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, start) + "` and `"
                + TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, end)
                + "`, but found no likely duplicate direct sends using the 5 minute match window.";
    }

    private static String buildDetail(
            String allianceLabel,
            long start,
            long end,
            int routeCandidateCount,
            List<DuplicateAllianceBankTransferMatch> matches
    ) {
        StringBuilder detail = new StringBuilder();
        detail.append("Alliance bank: ").append(allianceLabel).append('\n');
        detail.append("Start: ").append(TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, start)).append('\n');
        detail.append("End: ").append(TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, end)).append('\n');
        detail.append("Match window: ").append(TimeUtil.secToTime(TimeUnit.MILLISECONDS, MATCH_WINDOW_MS)).append('\n');
        detail.append("Route candidates checked: ").append(routeCandidateCount).append('\n');
        detail.append("Likely duplicates found: ").append(matches.size()).append("\n\n");

        for (DuplicateAllianceBankTransferMatch match : matches) {
            appendMatch(detail, match);
        }
        return detail.toString();
    }

    static CollectionResult collectMatches(
            List<Transaction2> candidateTransactions,
            int allianceId,
            long start,
            long end
    ) {
        Map<DuplicateTransferGroupKey, List<Transaction2>> routeByKey = new HashMap<>();
        Map<DuplicateTransferGroupKey, List<Transaction2>> directByKey = new HashMap<>();
        int routeCandidateCount = 0;

        for (Transaction2 tx : candidateTransactions) {
            if (tx.tx_id <= 0 || !tx.isSenderAA()) {
                continue;
            }

            DuplicateTransferGroupKey key = DuplicateTransferGroupKey.of(tx);
            if (isRouteTransfer(tx, allianceId, start, end)) {
                routeByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tx);
                routeCandidateCount++;
            } else if (isDirectTransferCandidate(tx, allianceId)) {
                directByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tx);
            }
        }

        List<DuplicateAllianceBankTransferMatch> matches = new ArrayList<>();
        for (Map.Entry<DuplicateTransferGroupKey, List<Transaction2>> entry : routeByKey.entrySet()) {
            List<Transaction2> directs = directByKey.get(entry.getKey());
            if (directs == null || directs.isEmpty()) {
                continue;
            }
            collectMatches(entry.getValue(), directs, matches);
        }

        matches.sort(Comparator.comparingLong((DuplicateAllianceBankTransferMatch match) -> match.routeTx().tx_datetime)
                .thenComparingInt(match -> match.routeTx().tx_id));
        return new CollectionResult(routeCandidateCount, matches);
    }

    private static boolean isRouteTransfer(Transaction2 tx, int allianceId, long start, long end) {
        return tx.tx_datetime >= start
                && tx.tx_datetime <= end
                && tx.isReceiverAA()
                && tx.receiver_id == allianceId
                && ROUTE_NOTE.equals(tx.getStructuredNote());
    }

    private static boolean isDirectTransferCandidate(Transaction2 tx, int allianceId) {
        if (tx.matchesReceiver(allianceId, TransactionEndpointKey.ALLIANCE_TYPE)) {
            return false;
        }

        // The direct duplicate can itself carry #ignore=<account> while still targeting a nation.
        // Only exclude plain routed ignore transfers into alliance-bank receivers.
        return !(tx.isReceiverAA() && ROUTE_NOTE.equals(tx.getStructuredNote()));
    }

    private static void collectMatches(
            List<Transaction2> routeTransactions,
            List<Transaction2> directTransactions,
            List<DuplicateAllianceBankTransferMatch> matches
    ) {
        NavigableSet<DuplicateTransferCandidate> activeDirects = new TreeSet<>(
                Comparator.comparingLong(DuplicateTransferCandidate::txDatetime)
                        .thenComparingInt(DuplicateTransferCandidate::txId)
        );
        int nextDirectIndex = 0;

        for (Transaction2 routeTx : routeTransactions) {
            long minTime = routeTx.tx_datetime - MATCH_WINDOW_MS;
            long maxTime = routeTx.tx_datetime + MATCH_WINDOW_MS;

            while (nextDirectIndex < directTransactions.size()
                    && directTransactions.get(nextDirectIndex).tx_datetime <= maxTime) {
                activeDirects.add(DuplicateTransferCandidate.of(directTransactions.get(nextDirectIndex++)));
            }

            while (!activeDirects.isEmpty() && activeDirects.first().txDatetime() < minTime) {
                activeDirects.pollFirst();
            }

            DuplicateTransferCandidate best = findClosestDuplicateCandidate(activeDirects, routeTx.tx_datetime);
            if (best == null) {
                continue;
            }

            matches.add(new DuplicateAllianceBankTransferMatch(routeTx, best.tx()));
            activeDirects.remove(best);
        }
    }

    private static DuplicateTransferCandidate findClosestDuplicateCandidate(
            NavigableSet<DuplicateTransferCandidate> activeDirects,
            long targetTime
    ) {
        if (activeDirects.isEmpty()) {
            return null;
        }

        DuplicateTransferCandidate floor = activeDirects.floor(
                new DuplicateTransferCandidate(targetTime, Integer.MAX_VALUE, null));
        DuplicateTransferCandidate ceil = activeDirects.ceiling(
                new DuplicateTransferCandidate(targetTime, Integer.MIN_VALUE, null));

        if (floor == null) {
            return ceil;
        }
        if (ceil == null) {
            return floor;
        }

        long floorDiff = Math.abs(floor.txDatetime() - targetTime);
        long ceilDiff = Math.abs(ceil.txDatetime() - targetTime);
        return ceilDiff < floorDiff ? ceil : floor;
    }

    private static void appendMatch(StringBuilder detail, DuplicateAllianceBankTransferMatch match) {
        Transaction2 routeTx = match.routeTx();
        Transaction2 duplicateTx = match.duplicateTx();
        detail.append("Route tx ").append(routeTx.tx_id)
                .append(" + direct tx ").append(duplicateTx.tx_id)
                .append(" | delta=")
                .append(TimeUtil.secToTime(TimeUnit.MILLISECONDS,
                        Math.abs(duplicateTx.tx_datetime - routeTx.tx_datetime)))
                .append('\n');
        detail.append("  sender: ").append(formatEndpoint(routeTx.getSenderObj()))
                .append(" | banker: nation:").append(routeTx.banker_nation).append('\n');
        detail.append("  route: ").append(formatTransfer(routeTx)).append('\n');
        detail.append("  direct: ").append(formatTransfer(duplicateTx)).append("\n\n");
    }

    private static String formatTransfer(Transaction2 tx) {
        return "tx_id=" + tx.tx_id
                + " | time=" + TimeUtil.format(TimeUtil.YYYY_MM_DD_HH_MM_SS, tx.tx_datetime)
                + " | sender=" + formatEndpoint(tx.getSenderObj())
                + " | receiver=" + formatEndpoint(tx.getReceiverObj())
                + " | note=" + tx.getNoteSummary()
                + " | amount=" + ResourceType.toString(tx.resources);
    }

    private static String formatEndpoint(NationOrAllianceOrGuild endpoint) {
        String name = endpoint.getName();
        if (name == null || name.isEmpty()) {
            return endpoint.getQualifiedId();
        }
        return endpoint.getQualifiedId() + "(" + name + ")";
    }

    private static String formatAllianceLabel(int allianceId) {
        DBAlliance alliance = DBAlliance.get(allianceId);
        if (alliance == null) {
            return "aa:" + allianceId;
        }
        return formatEndpoint(alliance);
    }

        static record CollectionResult(
            int routeCandidateCount,
            List<DuplicateAllianceBankTransferMatch> matches
    ) {
    }

    private record DuplicateTransferCandidate(
            long txDatetime,
            int txId,
            Transaction2 tx
    ) {
        private static DuplicateTransferCandidate of(Transaction2 tx) {
            return new DuplicateTransferCandidate(tx.tx_datetime, tx.tx_id, tx);
        }
    }

    private static final class DuplicateTransferGroupKey {
        private final long senderAllianceId;
        private final int bankerNationId;
        private final long[] resourceAmounts;
        private final int hashCode;

        private DuplicateTransferGroupKey(long senderAllianceId, int bankerNationId, long[] resourceAmounts) {
            this.senderAllianceId = senderAllianceId;
            this.bankerNationId = bankerNationId;
            this.resourceAmounts = resourceAmounts;

            int result = Long.hashCode(senderAllianceId);
            result = 31 * result + Integer.hashCode(bankerNationId);
            result = 31 * result + Arrays.hashCode(resourceAmounts);
            this.hashCode = result;
        }

        private static DuplicateTransferGroupKey of(Transaction2 tx) {
            long[] resourceAmounts = new long[tx.resources.length];
            for (int i = 0; i < tx.resources.length; i++) {
                resourceAmounts[i] = ArrayUtil.toCents(tx.resources[i]);
            }
            return new DuplicateTransferGroupKey(tx.sender_id, tx.banker_nation, resourceAmounts);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DuplicateTransferGroupKey other)) {
                return false;
            }
            return senderAllianceId == other.senderAllianceId
                    && bankerNationId == other.bankerNationId
                    && Arrays.equals(resourceAmounts, other.resourceAmounts);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    static record DuplicateAllianceBankTransferMatch(Transaction2 routeTx, Transaction2 duplicateTx) {
    }
}
