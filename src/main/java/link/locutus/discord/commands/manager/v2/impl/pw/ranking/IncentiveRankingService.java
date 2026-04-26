package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Transaction2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IncentiveRankingService {
    private static final List<NationMeta> SECTION_ORDER = List.of(
            NationMeta.INCENTIVE_REFERRER,
            NationMeta.INCENTIVE_INTERVIEWER,
            NationMeta.INCENTIVE_MENTOR
    );

    private IncentiveRankingService() {
    }

    public record Request(long timeStartMs) {
        public Request {
            if (timeStartMs < 0L) {
                throw new IllegalArgumentException("timeStartMs cannot be negative");
            }
        }
    }

    public static RankingResult ranking(GuildDB db, Request request) {
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(request, "request");
        return ranking(request, db.getTransactions(request.timeStartMs(), false));
    }

    static RankingResult ranking(Request request, Iterable<Transaction2> transactions) {
        Objects.requireNonNull(request, "request");

        List<RankingSectionSpec> sections = new ArrayList<>();
        for (NationMeta meta : SECTION_ORDER) {
            Object2IntOpenHashMap<Integer> values = new Object2IntOpenHashMap<>();
            for (Transaction2 transaction : transactions) {
                if (transaction == null || !transaction.isSenderNation() || !transaction.hasNoteTag(DepositType.INCENTIVE)) {
                    continue;
                }

                Object value = transaction.getStructuredNote().get(DepositType.INCENTIVE);
                if (value != meta) {
                    continue;
                }

                if (transaction.sender_id <= 0L || transaction.sender_id > Integer.MAX_VALUE) {
                    continue;
                }
                values.addTo((int) transaction.sender_id, 1);
            }

            if (!values.isEmpty()) {
                sections.add(RankingBuilders.singleMetricSection(
                        sectionKind(meta),
                        RankingSortDirection.DESC,
                        values
                ));
            }
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.INCENTIVE,
                RankingEntityType.NATION,
                RankingValueFormat.COUNT,
                sections,
                null,
                System.currentTimeMillis()
        );
    }

    private static RankingSectionKind sectionKind(NationMeta meta) {
        return switch (meta) {
            case INCENTIVE_REFERRER -> RankingSectionKind.INCENTIVE_REFERRERS;
            case INCENTIVE_INTERVIEWER -> RankingSectionKind.INCENTIVE_INTERVIEWERS;
            case INCENTIVE_MENTOR -> RankingSectionKind.INCENTIVE_MENTORS;
            default -> throw new IllegalArgumentException("Unsupported incentive meta: " + meta);
        };
    }
}
