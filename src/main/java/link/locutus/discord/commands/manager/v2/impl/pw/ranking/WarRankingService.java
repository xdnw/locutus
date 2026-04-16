package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.handlers.AttackQuery;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class WarRankingService {
    private WarRankingService() {
    }

    public record WarCostRequest(
            long timeStartMs,
            long timeEndMs,
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
            Set<Integer> warAllianceIds,
            boolean onlyOffensiveWars,
            boolean onlyDefensiveWars,
            Set<Integer> highlightedAllianceIds
    ) {
        public static WarCostRequest normalize(
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
            if (onlyOffensiveWars && onlyDefensiveWars) {
                throw new IllegalArgumentException("Cannot combine `onlyOffensiveWars` and `onlyDefensiveWars`");
            }
            if (type == null) {
                type = WarCostMode.DEALT;
            }
            if (stat == null) {
                stat = WarCostStat.WAR_VALUE;
            }
            if (type == WarCostMode.PROFIT && stat.unit() != null) {
                throw new IllegalArgumentException("Cannot rank by `type: profit` with a unit stat");
            }
            if (type == WarCostMode.PROFIT && stat.isAttack()) {
                throw new IllegalArgumentException("Cannot rank by `type: profit` with an attack type stat");
            }
            long resolvedEndMs = timeEndMs == null ? Long.MAX_VALUE : timeEndMs;
            if (resolvedEndMs < timeStartMs) {
                throw new IllegalArgumentException("timeEndMs must be >= timeStartMs");
            }
            return new WarCostRequest(
                    timeStartMs,
                    resolvedEndMs,
                    normalizeCoalition(coalition1),
                    normalizeCoalition(coalition2),
                    onlyRankCoalition1,
                    type,
                    stat,
                    excludeInfra,
                    excludeConsumption,
                    excludeLoot,
                    excludeBuildings,
                    excludeUnits,
                    groupByAlliance,
                    scalePerWar,
                    scalePerCity,
                    normalizeOptionalSet(allowedWarTypes),
                    normalizeOptionalSet(allowedWarStatuses),
                    normalizeOptionalSet(allowedAttackTypes),
                    normalizeAllianceIds(warAlliances),
                    onlyOffensiveWars,
                    onlyDefensiveWars,
                    normalizeAllianceIds(highlight)
            );
        }
    }

    public record WarCountRequest(
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
        public static WarCountRequest normalize(
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
            if (onlyOffensives && onlyDefensives) {
                throw new IllegalArgumentException("Cannot combine `onlyOffensives` and `onlyDefensives`");
            }
            return new WarCountRequest(
                    timeStartMs,
                    normalizeCoalition(attackers),
                    normalizeCoalition(defenders),
                    onlyOffensives,
                    onlyDefensives,
                    onlyRankAttackers,
                    !rankByNation && normalizePerMember,
                    ignoreInactiveNations,
                    rankByNation,
                    warType,
                    normalizeOptionalSet(statuses)
            );
        }
    }

    public record AttackTypeRequest(
            long timeMs,
            AttackType type,
            Set<DBAlliance> alliances,
            Integer onlyTopX,
            boolean percent,
            boolean onlyOffWars,
            boolean onlyDefWars
    ) {
        public static AttackTypeRequest normalize(
                long timeMs,
                AttackType type,
                Set<DBAlliance> alliances,
                Integer onlyTopX,
                boolean percent,
                boolean onlyOffWars,
                boolean onlyDefWars
        ) {
            if (onlyOffWars && onlyDefWars) {
                throw new IllegalArgumentException("Cannot combine `only_off_wars` and `only_def_wars`");
            }

            Set<DBAlliance> source = (alliances == null || alliances.isEmpty())
                    ? Locutus.imp().getNationDB().getAlliances()
                    : alliances;

            Set<Integer> topAllianceIds = null;
            if (onlyTopX != null) {
                topAllianceIds = Locutus.imp().getNationDB().getAlliances(true, true, true, onlyTopX).stream()
                        .map(DBAlliance::getAlliance_id)
                        .collect(Collectors.toSet());
            }

            Set<DBAlliance> selected = new LinkedHashSet<>();
            for (DBAlliance alliance : source) {
                if (alliance == null) {
                    continue;
                }
                if (topAllianceIds != null && !topAllianceIds.contains(alliance.getAlliance_id())) {
                    continue;
                }
                selected.add(alliance);
            }

            return new AttackTypeRequest(timeMs, type, Set.copyOf(selected), onlyTopX, percent, onlyOffWars, onlyDefWars);
        }
    }

    public static RankingResult warCostRanking(WarCostRequest request) {
        WarParser parser = WarParser.of(request.coalition1(), request.coalition2(), request.timeStartMs(), request.timeEndMs())
                .allowWarStatuses(request.allowedWarStatuses())
                .allowedWarTypes(request.allowedWarTypes())
                .allowedAttackTypes(request.allowedAttackTypes());
        if (request.onlyOffensiveWars()) {
            parser.addFilter((war, attack) -> parser.getIsPrimary().apply(war));
        }
        if (request.onlyDefensiveWars()) {
            parser.addFilter((war, attack) -> !parser.getIsPrimary().apply(war));
        }

        IntSet warAllianceIds = request.warAllianceIds().isEmpty() ? null : new IntOpenHashSet(request.warAllianceIds());

        Map<Integer, DBWar> wars = parser.getWars();
        Map<Integer, Integer> warsByGroup = request.scalePerWar()
                ? countWarsByGroup(wars.values(), request.groupByAlliance(), warAllianceIds)
                : Map.of();
        Map<Integer, Integer> allianceCityCountCache = request.scalePerCity() && request.groupByAlliance()
                ? new HashMap<>()
                : Map.of();
        Map<Integer, Integer> nationCityCountCache = request.scalePerCity() && !request.groupByAlliance()
                ? new HashMap<>()
                : Map.of();

        TriFunction<Boolean, DBWar, AbstractCursor, Double> valueFunc = request.stat().getFunction(
                request.excludeUnits(),
                request.excludeInfra(),
                request.excludeConsumption(),
                request.excludeLoot(),
                request.excludeBuildings(),
                request.type()
        );
        TriFunction<Boolean, DBWar, AbstractCursor, Double> attackValueFunc = request.type().getAttackFunc(valueFunc);

        Map<Integer, Double> values = new LinkedHashMap<>();
        parser.getAttacks().accept((war, attack) -> {
            if (request.onlyRankCoalition1()) {
                boolean isPrimary = parser.getIsPrimary().apply(war);
                addWarCostValue(values, warsByGroup, allianceCityCountCache, nationCityCountCache, request, war, attack, isPrimary, attackValueFunc, warAllianceIds);
            } else {
                addWarCostValue(values, warsByGroup, allianceCityCountCache, nationCityCountCache, request, war, attack, true, attackValueFunc, warAllianceIds);
                addWarCostValue(values, warsByGroup, allianceCityCountCache, nationCityCountCache, request, war, attack, false, attackValueFunc, warAllianceIds);
            }
        });

        boolean scaled = request.scalePerWar() || request.scalePerCity();
        RankingValueFormat valueFormat = warCostValueFormat(request.stat(), scaled);
        RankingNumericType numericType = valueFormat == RankingValueFormat.COUNT ? RankingNumericType.INTEGER : RankingNumericType.DECIMAL;
        RankingMetricDescriptor metric = RankingSupport.metricDescriptor(
                RankingSupport.machineKey(request.stat().name()),
                request.stat().name(),
                valueFormat,
                numericType
        );
        RankingEntityType entityType = request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION;
        RankingSection section = RankingBuilders.singleMetricSection(
                entityType == RankingEntityType.ALLIANCE ? "alliances" : "nations",
                entityType == RankingEntityType.ALLIANCE ? "Alliances" : "Nations",
                entityType,
                metric,
                RankingSortDirection.DESC,
                values,
                entityType == RankingEntityType.ALLIANCE ? request.highlightedAllianceIds() : Set.of(),
                entityType == RankingEntityType.ALLIANCE ? WarRankingService::allianceName : WarRankingService::nationName,
                warCostSectionMetadata(request),
                List.of()
        );

        List<RankingQueryField> querySummary = new ArrayList<>();
        querySummary.add(RankingSupport.field("start_ms", "Start", request.timeStartMs()));
        querySummary.add(RankingSupport.field("end_ms", "End", request.timeEndMs() == Long.MAX_VALUE ? System.currentTimeMillis() : request.timeEndMs()));
        querySummary.add(RankingSupport.field("group_by", "Group By", request.groupByAlliance() ? "alliance" : "nation"));
        querySummary.add(RankingSupport.field("mode", "Mode", request.type().name()));
        querySummary.add(RankingSupport.field("stat", "Stat", request.stat().name()));
        querySummary.add(RankingSupport.field("scale_per_war", "Scale Per War", request.scalePerWar()));
        querySummary.add(RankingSupport.field("scale_per_city", "Scale Per City", request.scalePerCity()));
        querySummary.add(RankingSupport.field("only_rank_coalition1", "Only Rank Coalition 1", request.onlyRankCoalition1()));
        querySummary.add(RankingSupport.field("only_offensive_wars", "Only Offensive Wars", request.onlyOffensiveWars()));
        querySummary.add(RankingSupport.field("only_defensive_wars", "Only Defensive Wars", request.onlyDefensiveWars()));
        if (!request.warAllianceIds().isEmpty()) {
            querySummary.add(RankingSupport.field("war_alliance_filter", "War Alliance Filter", request.warAllianceIds().size() + " alliances"));
        }

        return new RankingResult(
                "war_cost_ranking",
                buildWarCostTitle(request, request.timeEndMs()),
                List.copyOf(querySummary),
                RankingBuilders.totalRowCount(List.of(section)),
                Math.min(System.currentTimeMillis(), request.timeEndMs()),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
        );
    }

    public static RankingResult warRanking(WarCountRequest request) {
        WarParser parser = WarParser.of(request.attackers(), request.defenders(), request.timeStartMs(), Long.MAX_VALUE);
        Map<Integer, DBWar> wars = parser.getWars();

        Map<Integer, Double> values = new LinkedHashMap<>();
        for (DBWar war : wars.values()) {
            if (request.warType() != null && war.getWarType() != request.warType()) {
                continue;
            }
            if (request.statuses() != null && !request.statuses().contains(war.getStatus())) {
                continue;
            }

            boolean includeAttacker;
            boolean includeDefender;
            if (request.onlyRankAttackers()) {
                if (parser.getIsPrimary().apply(war)) {
                    includeAttacker = true;
                    includeDefender = false;
                } else {
                    includeAttacker = false;
                    includeDefender = true;
                }
            } else {
                includeAttacker = true;
                includeDefender = true;
            }

            if (!request.rankByNation()) {
                if (includeAttacker && !request.onlyDefensives() && war.getAttacker_aa() != 0) {
                    values.merge(war.getAttacker_aa(), 1d, Double::sum);
                }
                if (includeDefender && !request.onlyOffensives() && war.getDefender_aa() != 0) {
                    values.merge(war.getDefender_aa(), 1d, Double::sum);
                }
            } else {
                if (includeAttacker && !request.onlyDefensives()) {
                    values.merge(war.getAttacker_id(), 1d, Double::sum);
                }
                if (includeDefender && !request.onlyOffensives()) {
                    values.merge(war.getDefender_id(), 1d, Double::sum);
                }
            }
        }

        if (request.normalizePerMember() && !request.rankByNation()) {
            Map<Integer, Double> normalized = new LinkedHashMap<>();
            for (Map.Entry<Integer, Double> entry : values.entrySet()) {
                int memberCount = allianceMemberCount(entry.getKey(), request.ignoreInactiveNations());
                normalized.put(entry.getKey(), memberCount <= 0 ? 0d : entry.getValue() / memberCount);
            }
            values = normalized;
        }

        RankingMetricDescriptor metric = RankingSupport.metricDescriptor(
                "count",
                "Count",
                request.normalizePerMember() ? RankingValueFormat.NUMBER : RankingValueFormat.COUNT,
                request.normalizePerMember() ? RankingNumericType.DECIMAL : RankingNumericType.INTEGER
        );
        RankingEntityType entityType = request.rankByNation() ? RankingEntityType.NATION : RankingEntityType.ALLIANCE;
        RankingSection section = RankingBuilders.singleMetricSection(
                entityType == RankingEntityType.ALLIANCE ? "alliances" : "nations",
                entityType == RankingEntityType.ALLIANCE ? "Alliances" : "Nations",
                entityType,
                metric,
                RankingSortDirection.DESC,
                values,
                Set.of(),
                entityType == RankingEntityType.ALLIANCE ? WarRankingService::allianceName : WarRankingService::nationName,
                warCountSectionMetadata(request),
                List.of()
        );

        List<RankingQueryField> querySummary = new ArrayList<>();
        querySummary.add(RankingSupport.field("start_ms", "Start", request.timeStartMs()));
        querySummary.add(RankingSupport.field("result_entity_type", "Result Entity Type", entityType.name()));
        querySummary.add(RankingSupport.field("only_offensives", "Only Offensives", request.onlyOffensives()));
        querySummary.add(RankingSupport.field("only_defensives", "Only Defensives", request.onlyDefensives()));
        querySummary.add(RankingSupport.field("only_rank_attackers", "Only Rank Attackers", request.onlyRankAttackers()));
        querySummary.add(RankingSupport.field("normalize_per_member", "Normalize Per Member", request.normalizePerMember()));
        querySummary.add(RankingSupport.field("ignore_inactive_nations", "Ignore Inactive Nations", request.ignoreInactiveNations()));
        if (request.warType() != null) {
            querySummary.add(RankingSupport.field("war_type", "War Type", request.warType().name()));
        }
        if (request.statuses() != null) {
            querySummary.add(RankingSupport.field("statuses", "Statuses", request.statuses().stream().map(Enum::name).collect(Collectors.joining(","))));
        }

        return new RankingResult(
                "war_ranking",
                buildWarTitle(request),
                List.copyOf(querySummary),
                RankingBuilders.totalRowCount(List.of(section)),
                System.currentTimeMillis(),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
        );
    }

    public static RankingResult attackTypeRanking(AttackTypeRequest request) {
        Set<DBAlliance> selectedAlliances = request.alliances();
        Set<Integer> allianceIds = selectedAlliances.stream()
                .map(DBAlliance::getAlliance_id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        AttackQuery query = Locutus.imp().getWarDb().queryAttacks().withActiveWars(Predicates.alwaysTrue(), war -> {
            if (!request.onlyDefWars() && allianceIds.contains(war.getAttacker_aa())) {
                return true;
            }
            return !request.onlyOffWars() && allianceIds.contains(war.getDefender_aa());
        }).afterDate(request.timeMs());

        Map<Integer, Integer> totalAttacks = new HashMap<>();
        Map<Integer, Integer> attackOfType = new HashMap<>();
        Map<Integer, Integer> attackerAllianceCache = new HashMap<>();

        query.iterateAttacks((war, attack) -> {
            int attackerId = attack.getAttacker_id();
            int allianceId = attackerAllianceCache.computeIfAbsent(attackerId, id -> {
                DBNation nation = Locutus.imp().getNationDB().getNationById(id);
                if (nation == null || nation.getAlliance_id() == 0 || nation.getPosition() <= 1) {
                    return 0;
                }
                return nation.getAlliance_id();
            });

            if (allianceId == 0 || !allianceIds.contains(allianceId)) {
                return;
            }

            totalAttacks.merge(allianceId, 1, Integer::sum);
            if (attack.getAttack_type() == request.type()) {
                attackOfType.merge(allianceId, 1, Integer::sum);
            }
        });

        Map<Integer, Double> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : totalAttacks.entrySet()) {
            int allianceId = entry.getKey();
            int total = entry.getValue();
            if (total <= 0) {
                continue;
            }

            int matching = attackOfType.getOrDefault(allianceId, 0);
            double value = request.percent() ? matching / (double) total : matching;
            values.put(allianceId, value);
        }

        RankingMetricDescriptor metric = RankingSupport.metricDescriptor(
                request.percent() ? "percent" : "count",
                request.percent() ? "Percent" : "Count",
                request.percent() ? RankingValueFormat.PERCENT : RankingValueFormat.COUNT,
                request.percent() ? RankingNumericType.DECIMAL : RankingNumericType.INTEGER
        );

        RankingSection section = RankingBuilders.singleMetricSection(
                "alliances",
                "Alliances",
                RankingEntityType.ALLIANCE,
                metric,
                RankingSortDirection.DESC,
                values,
                Set.of(),
                WarRankingService::allianceName,
                attackTypeSectionMetadata(request),
                List.of()
        );

        List<RankingQueryField> querySummary = new ArrayList<>();
        querySummary.add(RankingSupport.field("time_ms", "Time", request.timeMs()));
        querySummary.add(RankingSupport.field("attack_type", "Attack Type", request.type().name()));
        querySummary.add(RankingSupport.field("mode", "Mode", request.percent() ? "percent" : "total"));
        querySummary.add(RankingSupport.field("only_top_x", "Only Top X", request.onlyTopX() == null ? "none" : request.onlyTopX()));
        querySummary.add(RankingSupport.field("selected_alliance_count", "Selected Alliance Count", allianceIds.size()));
        querySummary.add(RankingSupport.field("only_off_wars", "Only Offensive Wars", request.onlyOffWars()));
        querySummary.add(RankingSupport.field("only_def_wars", "Only Defensive Wars", request.onlyDefWars()));

        return new RankingResult(
                "attack_type_ranking",
                buildAttackTypeTitle(request),
                List.copyOf(querySummary),
                RankingBuilders.totalRowCount(List.of(section)),
                System.currentTimeMillis(),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
        );
    }

    private static void addWarCostValue(
            Map<Integer, Double> values,
            Map<Integer, Integer> warsByGroup,
            Map<Integer, Integer> allianceCityCountCache,
            Map<Integer, Integer> nationCityCountCache,
            WarCostRequest request,
            DBWar war,
            AbstractCursor attack,
            boolean attackerSide,
            TriFunction<Boolean, DBWar, AbstractCursor, Double> attackValueFunc,
            IntSet warAllianceIds
    ) {
        if (!matchesWarAllianceFilter(war, warAllianceIds)) {
            return;
        }

        int entityId = request.groupByAlliance()
                ? attackerSide ? war.getAttacker_aa() : war.getDefender_aa()
                : attackerSide ? war.getAttacker_id() : war.getDefender_id();

        if (entityId == 0) {
            return;
        }

        double value = attackValueFunc.apply(attackerSide, war, attack);
        if (!Double.isFinite(value)) {
            return;
        }

        double scaled = scaleWarCostValue(
                entityId,
                war,
                attackerSide,
                value,
                request.groupByAlliance(),
                request.scalePerWar(),
                request.scalePerCity(),
                warsByGroup,
                allianceCityCountCache,
                nationCityCountCache
        );
        if (!Double.isFinite(scaled)) {
            return;
        }

        values.merge(entityId, scaled, Double::sum);
    }

    private static double scaleWarCostValue(
            int entityId,
            DBWar war,
            boolean attackerSide,
            double value,
            boolean groupByAlliance,
            boolean scalePerWar,
            boolean scalePerCity,
            Map<Integer, Integer> warsByGroup,
            Map<Integer, Integer> allianceCityCountCache,
            Map<Integer, Integer> nationCityCountCache
    ) {
        int warCount = scalePerWar ? warsByGroup.getOrDefault(entityId, 0) : 1;

        int cityCount = 1;
        if (scalePerCity) {
            cityCount = groupByAlliance
                    ? allianceCityCountCache.computeIfAbsent(entityId, WarRankingService::allianceCityCount)
                    : nationCityCount(entityId, war, attackerSide, nationCityCountCache);
        }

        return applyNormalization(value, warCount, cityCount, scalePerWar, scalePerCity);
    }

    private static RankingValueFormat warCostValueFormat(WarCostStat stat, boolean scaled) {
        if (stat == WarCostStat.WAR_VALUE || stat.resource() == ResourceType.MONEY) {
            return RankingValueFormat.MONEY;
        }
        if (stat.resource() != null) {
            return RankingValueFormat.NUMBER;
        }
        if (stat.unit() != null || stat.isAttack()) {
            return scaled ? RankingValueFormat.NUMBER : RankingValueFormat.COUNT;
        }
        return RankingValueFormat.MONEY;
    }

    private static Map<Integer, Integer> countWarsByGroup(Iterable<DBWar> wars, boolean groupByAlliance, IntSet warAllianceIds) {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        for (DBWar war : wars) {
            if (!matchesWarAllianceFilter(war, warAllianceIds)) {
                continue;
            }
            if (groupByAlliance) {
                counts.merge(war.getAttacker_aa(), 1, Integer::sum);
                counts.merge(war.getDefender_aa(), 1, Integer::sum);
            } else {
                counts.merge(war.getAttacker_id(), 1, Integer::sum);
                counts.merge(war.getDefender_id(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static boolean matchesWarAllianceFilter(DBWar war, IntSet warAllianceIds) {
        if (warAllianceIds == null || warAllianceIds.isEmpty()) {
            return true;
        }
        int attackerAllianceId = war.getAttacker_aa();
        int defenderAllianceId = war.getDefender_aa();
        return (attackerAllianceId != 0 && warAllianceIds.contains(attackerAllianceId))
                || (defenderAllianceId != 0 && warAllianceIds.contains(defenderAllianceId));
    }

    private static int allianceMemberCount(int allianceId, boolean ignoreInactiveNations) {
        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
        if (alliance == null) {
            return 0;
        }
        return alliance.getNations(true, ignoreInactiveNations ? 2440 : Integer.MAX_VALUE, true).size();
    }

    private static int allianceCityCount(int allianceId) {
        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
        if (alliance == null) {
            return 0;
        }
        int total = 0;
        for (DBNation nation : alliance.getNations(true, 0, true)) {
            total += nation.getCities();
        }
        return total;
    }

    private static int nationCityCount(int nationId, DBWar war, boolean attackerSide, Map<Integer, Integer> nationCityCountCache) {
        int cities = war.getCities(attackerSide);
        if (cities != 0) {
            return cities;
        }

        return nationCityCountCache.computeIfAbsent(nationId, id -> {
            DBNation nation = DBNation.getById(id);
            return nation == null ? 1 : Math.max(1, nation.getCities());
        });
    }

    private static String buildWarCostTitle(WarCostRequest request, long timeEndMs) {
        String diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, Math.min(System.currentTimeMillis(), timeEndMs) - request.timeStartMs());
        String title = (request.groupByAlliance() ? "Alliance" : "Nation") + " " + request.stat().name() + " " + request.type().name();
        if (request.scalePerWar() && request.scalePerCity()) {
            title += "/war*city";
        } else if (request.scalePerWar()) {
            title += "/war";
        } else if (request.scalePerCity()) {
            title += "/city";
        }
        return title + " (" + diffStr + ")";
    }

    private static String buildWarTitle(WarCountRequest request) {
        String offOrDef = "";
        if (request.onlyOffensives() != request.onlyDefensives()) {
            offOrDef = request.onlyDefensives() ? "defensive " : "offensive ";
        }
        String title = "Most " + offOrDef + "wars (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - request.timeStartMs()) + ")";
        if (request.normalizePerMember()) {
            title += " (per " + (request.ignoreInactiveNations() ? "active " : "") + "nation)";
        }
        return title;
    }

    private static String buildAttackTypeTitle(AttackTypeRequest request) {
        String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - request.timeMs());
        return (request.percent() ? "Percent" : "Total") + " attacks of type: " + request.type().getName() + " (" + timeStr + ")";
    }

    static double applyNormalization(double value, int warCount, int cityCount, boolean scalePerWar, boolean scalePerCity) {
        double factor = 1d;
        if (scalePerWar) {
            factor *= Math.max(1, warCount);
        }
        if (scalePerCity) {
            factor *= Math.max(1, cityCount);
        }
        return value / factor;
    }

    static List<RankingQueryField> warCostSectionMetadata(WarCostRequest request) {
        List<RankingQueryField> metadata = new ArrayList<>(RankingSupport.sectionMetadata("WAR_ATTACK", RankingAggregationMode.SUM));
        metadata.add(RankingSupport.field("result_entity_type", "Result Entity Type",
                request.groupByAlliance() ? RankingEntityType.ALLIANCE.name() : RankingEntityType.NATION.name()));
        metadata.add(RankingSupport.field("stat", "Stat", request.stat().name()));
        metadata.add(RankingSupport.field("value_mode", "Value Mode", request.type().name()));
        metadata.add(RankingSupport.field("normalization", "Normalization", normalizationMode(request.scalePerWar(), request.scalePerCity())));
        metadata.add(RankingSupport.field("coalition_scope", "Coalition Scope", request.onlyRankCoalition1() ? "coalition_1_only" : "both"));
        metadata.add(RankingSupport.field("war_side_scope", "War Side Scope",
                warSideScope(request.onlyOffensiveWars(), request.onlyDefensiveWars())));
        if (!request.warAllianceIds().isEmpty()) {
            metadata.add(RankingSupport.field("war_alliance_filter", "War Alliance Filter", request.warAllianceIds().size() + " alliances"));
        }
        return List.copyOf(metadata);
    }

    static List<RankingQueryField> warCountSectionMetadata(WarCountRequest request) {
        List<RankingQueryField> metadata = new ArrayList<>(RankingSupport.sectionMetadata("WAR", RankingAggregationMode.COUNT));
        metadata.add(RankingSupport.field("result_entity_type", "Result Entity Type",
                request.rankByNation() ? RankingEntityType.NATION.name() : RankingEntityType.ALLIANCE.name()));
        metadata.add(RankingSupport.field("normalization", "Normalization", request.normalizePerMember() ? "per_member" : "none"));
        metadata.add(RankingSupport.field("war_side_scope", "War Side Scope",
                warSideScope(request.onlyOffensives(), request.onlyDefensives())));
        metadata.add(RankingSupport.field("coalition_scope", "Coalition Scope", request.onlyRankAttackers() ? "attackers_only" : "both"));
        if (request.normalizePerMember()) {
            metadata.add(RankingSupport.field("member_scope", "Member Scope",
                    request.ignoreInactiveNations() ? "active_only" : "all_members"));
        }
        return List.copyOf(metadata);
    }

    static List<RankingQueryField> attackTypeSectionMetadata(AttackTypeRequest request) {
        List<RankingQueryField> metadata = new ArrayList<>(RankingSupport.sectionMetadata("WAR_ATTACK", RankingAggregationMode.COUNT));
        metadata.add(RankingSupport.field("attack_type", "Attack Type", request.type().name()));
        metadata.add(RankingSupport.field("value_mode", "Value Mode", request.percent() ? "percent" : "count"));
        metadata.add(RankingSupport.field("war_side_scope", "War Side Scope",
                warSideScope(request.onlyOffWars(), request.onlyDefWars())));
        return List.copyOf(metadata);
    }

    static String normalizationMode(boolean scalePerWar, boolean scalePerCity) {
        if (scalePerWar && scalePerCity) {
            return "per_war_per_city";
        }
        if (scalePerWar) {
            return "per_war";
        }
        if (scalePerCity) {
            return "per_city";
        }
        return "none";
    }

    static String warSideScope(boolean onlyOffensiveWars, boolean onlyDefensiveWars) {
        if (onlyOffensiveWars) {
            return "offensive";
        }
        if (onlyDefensiveWars) {
            return "defensive";
        }
        return "all";
    }

    private static Set<NationOrAlliance> normalizeCoalition(Set<NationOrAlliance> coalition) {
        if (coalition == null || coalition.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(coalition);
    }

    private static <T> Set<T> normalizeOptionalSet(Set<T> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return Set.copyOf(values);
    }

    private static Set<Integer> normalizeAllianceIds(Set<DBAlliance> alliances) {
        if (alliances == null || alliances.isEmpty()) {
            return Set.of();
        }
        Set<Integer> result = new LinkedHashSet<>();
        for (DBAlliance alliance : alliances) {
            if (alliance != null) {
                result.add(alliance.getAlliance_id());
            }
        }
        return Set.copyOf(result);
    }

    private static String allianceName(int allianceId) {
        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
        return alliance == null ? null : alliance.getName();
    }

    private static String nationName(int nationId) {
        DBNation nation = DBNation.getById(nationId);
        return nation == null ? null : nation.getName();
    }
}
