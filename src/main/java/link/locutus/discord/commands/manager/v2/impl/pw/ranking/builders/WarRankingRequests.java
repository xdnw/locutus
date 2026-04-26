package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.WarRankingService;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class WarRankingRequests {
    private WarRankingRequests() {
    }

    public static WarRankingService.WarCostRequest cost(
            long timeStartMs,
            Long timeEndMs,
            Set<NationOrAlliance> coalition1,
            Set<NationOrAlliance> coalition2,
            boolean onlyRankCoalition1,
            WarCostMode type,
            WarCostStat stat,
            boolean excludeInfra,
            boolean excludeConsumption,
            boolean excludeLoot,
            boolean excludeBuildings,
            boolean excludeUnits,
            boolean groupByAlliance,
            boolean scalePerWar,
            boolean scalePerCity,
            Set<WarType> allowedWarTypes,
            Set<WarStatus> allowedWarStatuses,
            Set<AttackType> allowedAttackTypes,
            Set<DBAlliance> warAlliances,
            boolean onlyOffensiveWars,
            boolean onlyDefensiveWars,
            Set<DBAlliance> highlight
    ) {
        long resolvedEndMs = timeEndMs == null ? System.currentTimeMillis() : timeEndMs;
        WarCostMode resolvedType = type == null ? WarCostMode.DEALT : type;
        WarCostStat resolvedStat = stat == null ? WarCostStat.WAR_VALUE : stat;

        return new WarRankingService.WarCostRequest(
                timeStartMs,
                resolvedEndMs,
                RankingRequestSupport.coalition(coalition1),
                RankingRequestSupport.coalition(coalition2),
                onlyRankCoalition1,
                resolvedType,
                resolvedStat,
                excludeInfra,
                excludeConsumption,
                excludeLoot,
                excludeBuildings,
                excludeUnits,
                groupByAlliance,
                scalePerWar,
                scalePerCity,
                RankingRequestSupport.optionalSet(allowedWarTypes),
                RankingRequestSupport.optionalSet(allowedWarStatuses),
                RankingRequestSupport.optionalSet(allowedAttackTypes),
                RankingRequestSupport.allianceIds(warAlliances),
                onlyOffensiveWars,
                onlyDefensiveWars,
                RankingRequestSupport.allianceIds(highlight)
        );
    }

    public static WarRankingService.WarCountRequest count(
            long timeStartMs,
            Set<NationOrAlliance> attackers,
            Set<NationOrAlliance> defenders,
            boolean onlyOffensives,
            boolean onlyDefensives,
            boolean onlyRankAttackers,
            boolean normalizePerMember,
            boolean ignoreInactiveNations,
            boolean rankByNation,
            WarType warType,
            Set<WarStatus> statuses
    ) {
        return new WarRankingService.WarCountRequest(
                timeStartMs,
                RankingRequestSupport.coalition(attackers),
                RankingRequestSupport.coalition(defenders),
                onlyOffensives,
                onlyDefensives,
                onlyRankAttackers,
                !rankByNation && normalizePerMember,
                ignoreInactiveNations,
                rankByNation,
                warType,
                RankingRequestSupport.optionalSet(statuses)
        );
    }

    public static WarRankingService.AttackTypeRequest attackType(
            long timeMs,
            AttackType type,
            Set<DBAlliance> alliances,
            Integer onlyTopX,
            boolean percent,
            boolean onlyOffWars,
            boolean onlyDefWars
    ) {
        Objects.requireNonNull(type, "type");
        if (onlyOffWars && onlyDefWars) {
            throw new IllegalArgumentException("Cannot combine `only_off_wars` and `only_def_wars`");
        }
        if (onlyTopX != null && onlyTopX < 1) {
            throw new IllegalArgumentException("onlyTopX must be >= 1");
        }

        Set<DBAlliance> source = alliances == null || alliances.isEmpty()
                ? Locutus.imp().getNationDB().getAlliances()
                : alliances;

        Set<Integer> topAllianceIds = null;
        if (onlyTopX != null) {
            topAllianceIds = new LinkedHashSet<>();
            for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances(true, true, true, onlyTopX)) {
                topAllianceIds.add(alliance.getAlliance_id());
            }
        }

        LinkedHashSet<DBAlliance> selected = new LinkedHashSet<>();
        for (DBAlliance alliance : source) {
            if (alliance == null) {
                continue;
            }
            if (topAllianceIds != null && !topAllianceIds.contains(alliance.getAlliance_id())) {
                continue;
            }
            selected.add(alliance);
        }

        return new WarRankingService.AttackTypeRequest(
                timeMs,
                type,
                Set.copyOf(selected),
                onlyTopX,
                percent,
                onlyOffWars,
                onlyDefWars
        );
    }
}
