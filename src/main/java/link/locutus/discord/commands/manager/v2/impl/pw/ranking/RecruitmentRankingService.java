package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.db.entities.AllianceChange;
import link.locutus.discord.db.entities.DBAlliance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecruitmentRankingService {
    private RecruitmentRankingService() {
    }

    public record Request(long cutoffMs, int topX) {
    }

    public static RankingResult ranking(Request request) {
        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, request.topX());
        Set<Integer> allianceIds = alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());
        Map<Integer, List<AllianceChange>> removesByAlliance = Locutus.imp().getNationDB().getRemovesByAlliances(allianceIds, request.cutoffMs());

        Map<Integer, Integer> rankings = new LinkedHashMap<>();
        for (DBAlliance alliance : alliances) {
            int allianceId = alliance.getAlliance_id();

            Map<Integer, Long> noneToApplicant = new Int2LongOpenHashMap();
            Map<Integer, Long> applicantToMember = new Int2LongOpenHashMap();
            Map<Integer, Long> removed = new Int2LongOpenHashMap();

            List<AllianceChange> rankChanges = removesByAlliance.getOrDefault(allianceId, List.of());
            for (AllianceChange change : rankChanges) {
                int nationId = change.getNationId();

                if (change.getFromId() != allianceId) {
                    if (change.getToId() == allianceId) {
                        noneToApplicant.put(nationId, Math.max(noneToApplicant.getOrDefault(nationId, 0L), change.getDate()));
                        if (change.getToRank() != Rank.APPLICANT) {
                            applicantToMember.put(nationId, Math.max(applicantToMember.getOrDefault(nationId, 0L), change.getDate()));
                        }
                    }
                } else if (change.getToId() != allianceId) {
                    removed.put(nationId, Math.max(removed.getOrDefault(nationId, 0L), change.getDate()));
                } else if (change.getToRank() != Rank.APPLICANT) {
                    applicantToMember.put(nationId, Math.max(applicantToMember.getOrDefault(nationId, 0L), change.getDate()));
                }
            }

            noneToApplicant.entrySet().removeIf(entry -> removed.getOrDefault(entry.getKey(), 0L) > entry.getValue());
            applicantToMember.entrySet().removeIf(entry -> removed.getOrDefault(entry.getKey(), 0L) > entry.getValue());

            long total = noneToApplicant.entrySet().stream()
                    .filter(entry -> applicantToMember.getOrDefault(entry.getKey(), 0L) >= entry.getValue())
                    .count();

            if (total > 0) {
                rankings.put(allianceId, (int) total);
            }
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.RECRUITMENT,
                RankingEntityType.ALLIANCE,
                RankingValueDescriptor.recruitment(),
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        rankings
                )),
                Set.of(),
                System.currentTimeMillis()
        );
    }
}
