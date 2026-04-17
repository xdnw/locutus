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
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.handlers.AttackQuery;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
        public WarCostRequest {
            coalition1 = Set.copyOf(Objects.requireNonNull(coalition1, "coalition1"));
            coalition2 = Set.copyOf(Objects.requireNonNull(coalition2, "coalition2"));
            type = Objects.requireNonNull(type, "type");
            stat = Objects.requireNonNull(stat, "stat");
            warAllianceIds = Set.copyOf(Objects.requireNonNull(warAllianceIds, "warAllianceIds"));
            highlightedAllianceIds = Set.copyOf(Objects.requireNonNull(highlightedAllianceIds, "highlightedAllianceIds"));
            if (allowedWarTypes != null) {
                allowedWarTypes = Set.copyOf(allowedWarTypes);
            }
            if (allowedWarStatuses != null) {
                allowedWarStatuses = Set.copyOf(allowedWarStatuses);
            }
            if (allowedAttackTypes != null) {
                allowedAttackTypes = Set.copyOf(allowedAttackTypes);
            }
            if (onlyOffensiveWars && onlyDefensiveWars) {
                throw new IllegalArgumentException("Cannot combine `onlyOffensiveWars` and `onlyDefensiveWars`");
            }
            if (type == WarCostMode.PROFIT && stat.unit() != null) {
                throw new IllegalArgumentException("Cannot rank by `type: profit` with a unit stat");
            }
            if (type == WarCostMode.PROFIT && stat.isAttack()) {
                throw new IllegalArgumentException("Cannot rank by `type: profit` with an attack type stat");
            }
            if (timeEndMs < timeStartMs) {
                throw new IllegalArgumentException("timeEndMs must be >= timeStartMs");
            }
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
        public WarCountRequest {
            attackers = Set.copyOf(Objects.requireNonNull(attackers, "attackers"));
            defenders = Set.copyOf(Objects.requireNonNull(defenders, "defenders"));
            if (statuses != null) {
                statuses = Set.copyOf(statuses);
            }
            if (onlyOffensives && onlyDefensives) {
                throw new IllegalArgumentException("Cannot combine `onlyOffensives` and `onlyDefensives`");
            }
            if (rankByNation && normalizePerMember) {
                throw new IllegalArgumentException("normalizePerMember requires alliance rows");
            }
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
        public AttackTypeRequest {
            type = Objects.requireNonNull(type, "type");
            alliances = Set.copyOf(Objects.requireNonNull(alliances, "alliances"));
            if (onlyOffWars && onlyDefWars) {
                throw new IllegalArgumentException("Cannot combine `only_off_wars` and `only_def_wars`");
            }
            if (onlyTopX != null && onlyTopX < 1) {
                throw new IllegalArgumentException("onlyTopX must be >= 1");
            }
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
        RankingEntityType entityType = request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION;

        return RankingBuilders.singleMetricRanking(
                RankingKind.WAR_COST,
                entityType,
                valueFormat,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.forEntityType(entityType),
                        RankingSortDirection.DESC,
                        values
                )),
                entityType == RankingEntityType.ALLIANCE ? request.highlightedAllianceIds() : Set.of(),
                request.timeEndMs()
        );
    }

    public static RankingResult warRanking(WarCountRequest request) {
        long endMs = System.currentTimeMillis();
        WarParser parser = WarParser.of(request.attackers(), request.defenders(), request.timeStartMs(), endMs);
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

        RankingEntityType entityType = request.rankByNation() ? RankingEntityType.NATION : RankingEntityType.ALLIANCE;
        RankingValueFormat valueFormat = request.normalizePerMember() ? RankingValueFormat.NUMBER : RankingValueFormat.COUNT;

        return RankingBuilders.singleMetricRanking(
                RankingKind.WAR_COUNT,
                entityType,
                valueFormat,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.forEntityType(entityType),
                        RankingSortDirection.DESC,
                        values
                )),
                Set.of(),
                endMs
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

        RankingValueFormat valueFormat = request.percent() ? RankingValueFormat.PERCENT : RankingValueFormat.COUNT;

        return RankingBuilders.singleMetricRanking(
                RankingKind.ATTACK_TYPE,
                RankingEntityType.ALLIANCE,
                valueFormat,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        values
                )),
                Set.of(),
                System.currentTimeMillis()
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

    static RankingNormalizationMode normalizationMode(boolean scalePerWar, boolean scalePerCity) {
        if (scalePerWar && scalePerCity) {
            return RankingNormalizationMode.PER_WAR_PER_CITY;
        }
        if (scalePerWar) {
            return RankingNormalizationMode.PER_WAR;
        }
        if (scalePerCity) {
            return RankingNormalizationMode.PER_CITY;
        }
        return RankingNormalizationMode.NONE;
    }

}
