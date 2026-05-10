package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
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
            if (type.isProfitLike() && stat.unit() != null) {
                throw new IllegalArgumentException("Cannot rank by `type: profit` with a unit stat");
            }
            if (type.isProfitLike() && stat.isAttack()) {
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

        IntSet allowedAllianceIds = request.warAllianceIds().isEmpty() ? null : new IntOpenHashSet(request.warAllianceIds());

        Map<Integer, DBWar> wars = parser.getWars();
    Int2IntOpenHashMap warsByGroup = request.scalePerWar()
                ? countWarsByGroup(wars.values(), request.groupByAlliance(), allowedAllianceIds)
        : null;
    Int2IntOpenHashMap allianceCityCountCache = request.scalePerCity() && request.groupByAlliance()
        ? new Int2IntOpenHashMap()
        : null;
    Int2IntOpenHashMap nationCityCountCache = request.scalePerCity() && !request.groupByAlliance()
        ? new Int2IntOpenHashMap()
        : null;

        TriFunction<Boolean, DBWar, AbstractCursor, Double> valueFunc = request.stat().getFunction(
                request.excludeUnits(),
                request.excludeInfra(),
                request.excludeConsumption(),
                request.excludeLoot(),
                request.excludeBuildings(),
                request.type()
        );
        TriFunction<Boolean, DBWar, AbstractCursor, Double> attackValueFunc = request.type().getUncheckedAttackFunc(valueFunc);
        boolean filterAttackOrigins = !request.type().includesAllAttackOrigins();

        Int2DoubleOpenHashMap values = new Int2DoubleOpenHashMap();
        parser.getAttacks().accept((war, attack) -> {
            if (filterAttackOrigins && !request.type().includesAttack(war, attack)) {
                return;
            }
            if (request.onlyRankCoalition1()) {
                boolean isPrimary = parser.getIsPrimary().apply(war);
                addWarCostValue(values, warsByGroup, allianceCityCountCache, nationCityCountCache, request, war, attack, isPrimary, attackValueFunc, allowedAllianceIds);
            } else {
                addWarCostValue(values, warsByGroup, allianceCityCountCache, nationCityCountCache, request, war, attack, true, attackValueFunc, allowedAllianceIds);
                addWarCostValue(values, warsByGroup, allianceCityCountCache, nationCityCountCache, request, war, attack, false, attackValueFunc, allowedAllianceIds);
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

        Int2DoubleOpenHashMap values = new Int2DoubleOpenHashMap();
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
                    values.addTo(war.getAttacker_aa(), 1d);
                }
                if (includeDefender && !request.onlyOffensives() && war.getDefender_aa() != 0) {
                    values.addTo(war.getDefender_aa(), 1d);
                }
            } else {
                if (includeAttacker && !request.onlyDefensives()) {
                    values.addTo(war.getAttacker_id(), 1d);
                }
                if (includeDefender && !request.onlyOffensives()) {
                    values.addTo(war.getDefender_id(), 1d);
                }
            }
        }

        if (request.normalizePerMember() && !request.rankByNation()) {
            Int2DoubleOpenHashMap normalized = new Int2DoubleOpenHashMap(values.size());
            for (Int2DoubleMap.Entry entry : values.int2DoubleEntrySet()) {
                int memberCount = allianceMemberCount(entry.getKey(), request.ignoreInactiveNations());
                normalized.put(entry.getIntKey(), memberCount <= 0 ? 0d : entry.getDoubleValue() / memberCount);
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
        IntOpenHashSet allianceIdSet = new IntOpenHashSet();
        for (DBAlliance alliance : selectedAlliances) {
            allianceIdSet.add(alliance.getAlliance_id());
        }

        AttackQuery query = Locutus.imp().getWarDb().queryAttacks().withActiveWars(Predicates.alwaysTrue(), war -> {
            if (!request.onlyDefWars() && allianceIdSet.contains(war.getAttacker_aa())) {
                return true;
            }
            return !request.onlyOffWars() && allianceIdSet.contains(war.getDefender_aa());
        }).afterDate(request.timeMs());

        Int2IntOpenHashMap totalAttacks = new Int2IntOpenHashMap();
        Int2IntOpenHashMap attackOfType = new Int2IntOpenHashMap();
        Int2IntOpenHashMap attackerAllianceCache = new Int2IntOpenHashMap();
        attackerAllianceCache.defaultReturnValue(Integer.MIN_VALUE);

        query.iterateAttacks((war, attack) -> {
            int attackerId = attack.getAttacker_id();
            int allianceId = attackerAllianceCache.get(attackerId);
            if (allianceId == Integer.MIN_VALUE) {
                DBNation nation = Locutus.imp().getNationDB().getNationById(attackerId);
                if (nation == null || nation.getAlliance_id() == 0 || nation.getPosition() <= 1) {
                    allianceId = 0;
                } else {
                    allianceId = nation.getAlliance_id();
                }
                attackerAllianceCache.put(attackerId, allianceId);
            }

            if (allianceId == 0 || !allianceIdSet.contains(allianceId)) {
                return;
            }

            totalAttacks.addTo(allianceId, 1);
            if (attack.getAttack_type() == request.type()) {
                attackOfType.addTo(allianceId, 1);
            }
        });

        Int2DoubleOpenHashMap values = new Int2DoubleOpenHashMap(totalAttacks.size());
        for (Int2IntMap.Entry entry : totalAttacks.int2IntEntrySet()) {
            int allianceId = entry.getIntKey();
            int total = entry.getIntValue();
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
            Int2DoubleOpenHashMap values,
            Int2IntOpenHashMap warsByGroup,
            Int2IntOpenHashMap allianceCityCountCache,
            Int2IntOpenHashMap nationCityCountCache,
            WarCostRequest request,
            DBWar war,
            AbstractCursor attack,
            boolean attackerSide,
            TriFunction<Boolean, DBWar, AbstractCursor, Double> attackValueFunc,
            IntSet allowedAllianceIds
    ) {
        int entityId = rankedEntityId(war, request.groupByAlliance(), attackerSide, allowedAllianceIds);
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

        values.addTo(entityId, scaled);
    }

    private static double scaleWarCostValue(
            int entityId,
            DBWar war,
            boolean attackerSide,
            double value,
            boolean groupByAlliance,
            boolean scalePerWar,
            boolean scalePerCity,
                Int2IntOpenHashMap warsByGroup,
                Int2IntOpenHashMap allianceCityCountCache,
                Int2IntOpenHashMap nationCityCountCache
    ) {
        int warCount = scalePerWar ? warsByGroup.getOrDefault(entityId, 0) : 1;

        int cityCount = 1;
        if (scalePerCity) {
            cityCount = groupByAlliance
                    ? cachedAllianceCityCount(entityId, allianceCityCountCache)
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

    static Int2IntOpenHashMap countWarsByGroup(Iterable<DBWar> wars, boolean groupByAlliance, IntSet allowedAllianceIds) {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        for (DBWar war : wars) {
            int attackerEntityId = rankedEntityId(war, groupByAlliance, true, allowedAllianceIds);
            if (attackerEntityId != 0) {
                counts.addTo(attackerEntityId, 1);
            }
            int defenderEntityId = rankedEntityId(war, groupByAlliance, false, allowedAllianceIds);
            if (defenderEntityId != 0) {
                counts.addTo(defenderEntityId, 1);
            }
        }
        return counts;
    }

    static int rankedEntityId(DBWar war, boolean groupByAlliance, boolean attackerSide, IntSet allowedAllianceIds) {
        if (!matchesAllowedAllianceSide(war, attackerSide, allowedAllianceIds)) {
            return 0;
        }
        int allianceId = attackerSide ? war.getAttacker_aa() : war.getDefender_aa();
        return groupByAlliance
                ? allianceId
                : attackerSide ? war.getAttacker_id() : war.getDefender_id();
    }

    static boolean matchesAllowedAllianceSide(DBWar war, boolean attackerSide, IntSet allowedAllianceIds) {
        if (allowedAllianceIds == null || allowedAllianceIds.isEmpty()) {
            return true;
        }
        int allianceId = attackerSide ? war.getAttacker_aa() : war.getDefender_aa();
        return allianceId != 0 && allowedAllianceIds.contains(allianceId);
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

    private static int cachedAllianceCityCount(int allianceId, Int2IntOpenHashMap allianceCityCountCache) {
        if (allianceCityCountCache.containsKey(allianceId)) {
            return allianceCityCountCache.get(allianceId);
        }
        int cityCount = allianceCityCount(allianceId);
        allianceCityCountCache.put(allianceId, cityCount);
        return cityCount;
    }

    private static int nationCityCount(int nationId, DBWar war, boolean attackerSide, Int2IntOpenHashMap nationCityCountCache) {
        int cities = war.getCities(attackerSide);
        if (cities != 0) {
            return cities;
        }

        if (nationCityCountCache.containsKey(nationId)) {
            return nationCityCountCache.get(nationId);
        }

        DBNation nation = DBNation.getById(nationId);
        int cityCount = nation == null ? 1 : Math.max(1, nation.getCities());
        nationCityCountCache.put(nationId, cityCount);
        return cityCount;
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
