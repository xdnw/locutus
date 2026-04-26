package link.locutus.discord.sim.planners.providers;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.sim.GlobalPriorActivityProvider;
import link.locutus.discord.sim.combat.RandomSource;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.SnapshotActivityProvider;
import link.locutus.discord.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Planner-entry composite activity model for blitz declare weighting.
 *
 * <p>The planner still consumes only {@link SnapshotActivityProvider}. This model exists to
 * assemble the stronger blitz/wartime signals once at request entry instead of repeatedly
 * approximating wartime behavior from peacetime login buckets.</p>
 */
public final class CompositeBlitzActivityModel {
    static final int LOOKBACK_TURNS = 14 * 12;
    static final int WEEK_TURNS = 7 * 12;
    static final int MIN_WARTIME_TURNS = 48;
    static final int MIN_OFFENSIVE_ACTION_SAMPLES = 2;
    static final int RESPONSE_LOOKAHEAD_TURNS = 24;
    static final int COORDINATED_DECLARANT_THRESHOLD = 3;
    static final double ACTIVITY_TIE_EPSILON = 1e-4;

    private static final double WARTIME_LOGIN_WEIGHT = 0.55;
    private static final double PEACETIME_STABILIZER_WEIGHT = 0.15;
    private static final double PEACETIME_FALLBACK_WEIGHT = 0.70;
    private static final double OFFENSIVE_ACTION_WEIGHT = 0.20;
    private static final double DEFENSIVE_RESPONSE_WEIGHT = 0.25;
    private static final double ALLIANCE_COORDINATION_UPLIFT_CAP = 0.15;
    private static final double TIE_BREAK_SCALE = ACTIVITY_TIE_EPSILON * 0.1;

    private final Int2DoubleOpenHashMap resolvedActivityByNationId;
    private final Int2IntOpenHashMap activityBasisPointsByNationId;
    private final Map<Integer, NationSignals> signalsByNationId;

    public record Options(boolean includeAllianceCoordination) {
        public static final Options DEFAULT = new Options(true);

        public Options withoutAllianceCoordination() {
            return includeAllianceCoordination ? new Options(false) : this;
        }
    }

    private CompositeBlitzActivityModel(
            Int2DoubleOpenHashMap resolvedActivityByNationId,
            Int2IntOpenHashMap activityBasisPointsByNationId,
            Map<Integer, NationSignals> signalsByNationId
    ) {
        this.resolvedActivityByNationId = resolvedActivityByNationId;
        this.activityBasisPointsByNationId = activityBasisPointsByNationId;
        this.signalsByNationId = signalsByNationId;
    }

    public static CompositeBlitzActivityModel build(
            Collection<DBNation> nations,
            WarDB warDb,
            int currentWeekTurn,
            long tieBreakSeed
    ) {
        return build(nations, warDb, currentWeekTurn, tieBreakSeed, Options.DEFAULT);
    }

    public static CompositeBlitzActivityModel build(
            Collection<DBNation> nations,
            WarDB warDb,
            int currentWeekTurn,
            long tieBreakSeed,
            Options options
    ) {
        Objects.requireNonNull(nations, "nations");
        Objects.requireNonNull(warDb, "warDb");
        Objects.requireNonNull(options, "options");
        if (nations.isEmpty()) {
            return empty();
        }

        NationDB nationDb = Locutus.imp().getNationDB();
        Set<Integer> nationIds = new LinkedHashSet<>(nations.size());
        Int2IntOpenHashMap allianceByNationId = new Int2IntOpenHashMap(nations.size());
        allianceByNationId.defaultReturnValue(0);
        for (DBNation nation : nations) {
            nationIds.add(nation.getNation_id());
            allianceByNationId.put(nation.getNation_id(), nation.getAlliance_id());
        }

        long nowTurn = TimeUtil.getTurn();
        long lookbackStartTurn = Math.max(0L, nowTurn - LOOKBACK_TURNS + 1L);
        long lookbackStartMs = TimeUtil.getTimeFromTurn(lookbackStartTurn);
        long lookbackEndMs = TimeUtil.getTimeFromTurn(nowTurn);

        Map<Integer, Set<Long>> loginTurnsByNation = nationDb.getActivityByTurn(lookbackStartTurn, nowTurn, nationIds);
        Set<DBWar> relevantWars = warDb.getWarsForNationOrAlliance(nationIds, Set.of());
        relevantWars.removeIf(war -> war.getDate() > lookbackEndMs || war.possibleEndDate() < lookbackStartMs);

        return fromHistory(
                nationIds,
                allianceByNationId,
                loginTurnsByNation,
                relevantWars,
                warDb,
                lookbackStartTurn,
                nowTurn,
                currentWeekTurn,
                tieBreakSeed,
                options
        );
    }

    static CompositeBlitzActivityModel fromHistory(
            Set<Integer> nationIds,
            Int2IntOpenHashMap allianceByNationId,
            Map<Integer, Set<Long>> loginTurnsByNation,
            Collection<DBWar> wars,
            WarDB warDb,
            long lookbackStartTurn,
            long lookbackEndTurn,
            int currentWeekTurn,
            long tieBreakSeed
    ) {
        return fromHistory(
                nationIds,
                allianceByNationId,
                loginTurnsByNation,
                wars,
                warDb,
                lookbackStartTurn,
                lookbackEndTurn,
                currentWeekTurn,
                tieBreakSeed,
                Options.DEFAULT
        );
    }

    static CompositeBlitzActivityModel fromHistoryWithAttacks(
            Set<Integer> nationIds,
            Int2IntOpenHashMap allianceByNationId,
            Map<Integer, Set<Long>> loginTurnsByNation,
            Collection<DBWar> wars,
            Collection<? extends AbstractCursor> attacks,
            long lookbackStartTurn,
            long lookbackEndTurn,
            int currentWeekTurn,
            long tieBreakSeed,
            Options options
    ) {
        Objects.requireNonNull(nationIds, "nationIds");
        Objects.requireNonNull(allianceByNationId, "allianceByNationId");
        Objects.requireNonNull(loginTurnsByNation, "loginTurnsByNation");
        Objects.requireNonNull(wars, "wars");
        Objects.requireNonNull(options, "options");

        if (nationIds.isEmpty()) {
            return empty();
        }

        int windowLength = (int) Math.max(0L, lookbackEndTurn - lookbackStartTurn + 1L);
        Int2ObjectOpenHashMap<NationWindow> windowsByNationId = new Int2ObjectOpenHashMap<>(nationIds.size());
        for (int nationId : nationIds) {
            NationWindow window = new NationWindow(windowLength);
            windowsByNationId.put(nationId, window);
            for (long loginTurn : loginTurnsByNation.getOrDefault(nationId, Set.of())) {
                int offset = offset(loginTurn, lookbackStartTurn, windowLength);
                if (offset >= 0) {
                    window.loginByOffset[offset] = true;
                }
            }
        }

        Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<IntOpenHashSet>> declarantsByAllianceTurn = new Int2ObjectOpenHashMap<>();
        Int2IntOpenHashMap observedWarEndTurnByWarId = new Int2IntOpenHashMap();
        observedWarEndTurnByWarId.defaultReturnValue(Integer.MIN_VALUE);

        for (DBWar war : wars) {
            int attackerId = war.getAttacker_id();
            int defenderId = war.getDefender_id();
            long startTurn = TimeUtil.getTurn(war.getDate());
            int startOffset = offset(startTurn, lookbackStartTurn, windowLength);

            if (nationIds.contains(attackerId) && startOffset >= 0) {
                windowsByNationId.get(attackerId).offensiveActionByOffset[startOffset] = true;
                int attackerAllianceId = allianceByNationId.get(attackerId);
                if (attackerAllianceId > 0) {
                    declarantsByAllianceTurn
                            .computeIfAbsent(attackerAllianceId, ignored -> new Int2ObjectOpenHashMap<>())
                            .computeIfAbsent((int) startTurn, ignored -> new IntOpenHashSet())
                            .add(attackerId);
                }
            }
            if (nationIds.contains(defenderId) && startOffset >= 0) {
                windowsByNationId.get(defenderId).defensiveDeclareOffsets.add(startOffset);
            }
        }

        if (attacks != null && !attacks.isEmpty()) {
            Map<Integer, DBWar> warsById = new java.util.HashMap<>(Math.max(16, wars.size() * 2));
            for (DBWar war : wars) {
                warsById.put(war.warId, war);
            }
            for (AbstractCursor attack : attacks) {
                DBWar war = warsById.get(attack.getWar_id());
                if (war != null) {
                    accumulateAttack(nationIds, lookbackStartTurn, windowLength, windowsByNationId, observedWarEndTurnByWarId, war, attack);
                }
            }
        }

        for (DBWar war : wars) {
            applyWartimeWindow(nationIds, windowsByNationId, observedWarEndTurnByWarId, war, lookbackStartTurn, lookbackEndTurn, windowLength);
        }

        Int2DoubleOpenHashMap coordinationByNationId = options.includeAllianceCoordination()
            ? compileAllianceCoordination(nationIds, allianceByNationId, declarantsByAllianceTurn)
            : emptyCoordination();
        Int2DoubleOpenHashMap resolvedActivityByNationId = new Int2DoubleOpenHashMap(nationIds.size());
        resolvedActivityByNationId.defaultReturnValue(GlobalPriorActivityProvider.DEFAULT_PRIOR);
        Int2IntOpenHashMap activityBasisPointsByNationId = new Int2IntOpenHashMap(nationIds.size());
        activityBasisPointsByNationId.defaultReturnValue((int) Math.round(GlobalPriorActivityProvider.DEFAULT_PRIOR * 10_000d));
        Int2ObjectOpenHashMap<NationSignals> signalsByNationId = new Int2ObjectOpenHashMap<>(nationIds.size());
        RandomSource tieBreakRandom = RandomSource.splittable(tieBreakSeed);
        int normalizedWeekTurn = Math.floorMod(currentWeekTurn, WEEK_TURNS);

        for (int nationId : nationIds) {
            NationWindow window = windowsByNationId.get(nationId);
            WeekTurnProfile peacetimeProfile = WeekTurnProfile.from(window.loginByOffset, null, lookbackStartTurn);
            WeekTurnProfile wartimeProfile = WeekTurnProfile.from(window.loginByOffset, window.wartimeByOffset, lookbackStartTurn);
            WeekTurnProfile offensiveProfile = WeekTurnProfile.from(window.offensiveActionByOffset, null, lookbackStartTurn);
            ResponseSummary responseSummary = ResponseSummary.from(window.loginByOffset, window.defensiveDeclareOffsets, windowLength);

            double coordination = coordinationByNationId.get(nationId);
            double composite = compositeScore(peacetimeProfile, wartimeProfile, offensiveProfile, responseSummary, coordination, normalizedWeekTurn);
            double quantized = quantize(composite);
            double resolved = clamp01(quantized + tieBreakJitter(tieBreakRandom, nationId, normalizedWeekTurn));

            NationSignals signals = new NationSignals(
                    peacetimeProfile.valueAt(normalizedWeekTurn),
                    wartimeProfile.eligibleTurns >= MIN_WARTIME_TURNS ? wartimeProfile.valueAt(normalizedWeekTurn) : null,
                    offensiveProfile.eventTurns >= MIN_OFFENSIVE_ACTION_SAMPLES ? offensiveProfile.valueAt(normalizedWeekTurn) : null,
                    responseSummary.samples > 0 ? responseSummary.average : null,
                    coordination,
                    quantized,
                    resolved,
                    wartimeProfile.eligibleTurns,
                    offensiveProfile.eventTurns,
                    responseSummary.samples
            );
            signalsByNationId.put(nationId, signals);
            resolvedActivityByNationId.put(nationId, resolved);
            activityBasisPointsByNationId.put(nationId, (int) Math.round(quantized * 10_000d));
        }

        return new CompositeBlitzActivityModel(resolvedActivityByNationId, activityBasisPointsByNationId, Collections.unmodifiableMap(signalsByNationId));
    }

    static CompositeBlitzActivityModel fromHistory(
            Set<Integer> nationIds,
            Int2IntOpenHashMap allianceByNationId,
            Map<Integer, Set<Long>> loginTurnsByNation,
            Collection<DBWar> wars,
            WarDB warDb,
            long lookbackStartTurn,
            long lookbackEndTurn,
            int currentWeekTurn,
            long tieBreakSeed,
            Options options
    ) {
        List<AbstractCursor> attacks = new ArrayList<>();
        if (warDb != null && !wars.isEmpty()) {
            warDb.iterateAttacksByWars(
                wars,
                TimeUtil.getTimeFromTurn(lookbackStartTurn),
                TimeUtil.getTimeFromTurn(lookbackEndTurn),
                (war, attack) -> attacks.add(attack)
            );
        }
        return fromHistoryWithAttacks(
                nationIds,
                allianceByNationId,
                loginTurnsByNation,
                wars,
                attacks,
                lookbackStartTurn,
                lookbackEndTurn,
                currentWeekTurn,
                tieBreakSeed,
                options
        );
    }

    public SnapshotActivityProvider snapshotProvider() {
        return (snapshot, turn) -> resolvedActivityByNationId.get(snapshot.nationId());
    }

    public int activityBasisPoints(int nationId) {
        return activityBasisPointsByNationId.get(nationId);
    }

    public NationSignals signalsFor(int nationId) {
        return signalsByNationId.get(nationId);
    }

    private static CompositeBlitzActivityModel empty() {
        Int2DoubleOpenHashMap resolved = new Int2DoubleOpenHashMap();
        resolved.defaultReturnValue(GlobalPriorActivityProvider.DEFAULT_PRIOR);
        Int2IntOpenHashMap basisPoints = new Int2IntOpenHashMap();
        basisPoints.defaultReturnValue((int) Math.round(GlobalPriorActivityProvider.DEFAULT_PRIOR * 10_000d));
        return new CompositeBlitzActivityModel(resolved, basisPoints, Map.of());
    }

    private static void accumulateAttack(
            Set<Integer> nationIds,
            long lookbackStartTurn,
            int windowLength,
            Int2ObjectOpenHashMap<NationWindow> windowsByNationId,
            Int2IntOpenHashMap observedWarEndTurnByWarId,
            DBWar war,
            AbstractCursor attack
    ) {
        int attackerId = attack.getAttacker_id();
        if (nationIds.contains(attackerId)) {
            int attackOffset = offset(TimeUtil.getTurn(attack.getDate()), lookbackStartTurn, windowLength);
            if (attackOffset >= 0) {
                windowsByNationId.get(attackerId).offensiveActionByOffset[attackOffset] = true;
            }
        }
        AttackType attackType = attack.getAttack_type();
        if (attackType == AttackType.PEACE || attackType == AttackType.VICTORY) {
            int attackTurn = (int) TimeUtil.getTurn(attack.getDate());
            if (attackTurn > observedWarEndTurnByWarId.get(war.warId)) {
                observedWarEndTurnByWarId.put(war.warId, attackTurn);
            }
        }
    }

    private static void applyWartimeWindow(
            Set<Integer> nationIds,
            Int2ObjectOpenHashMap<NationWindow> windowsByNationId,
            Int2IntOpenHashMap observedWarEndTurnByWarId,
            DBWar war,
            long lookbackStartTurn,
            long lookbackEndTurn,
            int windowLength
    ) {
        int startTurn = (int) Math.max(lookbackStartTurn, TimeUtil.getTurn(war.getDate()));
        int fallbackEndTurn = (int) Math.min(lookbackEndTurn, TimeUtil.getTurn(war.possibleEndDate()));
        int observedEndTurn = observedWarEndTurnByWarId.get(war.warId);
        int endTurn = observedEndTurn == Integer.MIN_VALUE ? fallbackEndTurn : Math.min(fallbackEndTurn, observedEndTurn);
        if (endTurn < startTurn) {
            return;
        }
        if (nationIds.contains(war.getAttacker_id())) {
            markWindow(windowsByNationId.get(war.getAttacker_id()).wartimeByOffset, startTurn, endTurn, lookbackStartTurn, windowLength);
        }
        if (nationIds.contains(war.getDefender_id())) {
            markWindow(windowsByNationId.get(war.getDefender_id()).wartimeByOffset, startTurn, endTurn, lookbackStartTurn, windowLength);
        }
    }

    private static void markWindow(boolean[] buffer, int startTurn, int endTurn, long lookbackStartTurn, int windowLength) {
        int startOffset = offset(startTurn, lookbackStartTurn, windowLength);
        int endOffset = offset(endTurn, lookbackStartTurn, windowLength);
        if (startOffset < 0 || endOffset < 0) {
            return;
        }
        for (int offset = startOffset; offset <= endOffset; offset++) {
            buffer[offset] = true;
        }
    }

    private static Int2DoubleOpenHashMap compileAllianceCoordination(
            Set<Integer> nationIds,
            Int2IntOpenHashMap allianceByNationId,
            Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<IntOpenHashSet>> declarantsByAllianceTurn
    ) {
        Int2DoubleOpenHashMap coefficientByAllianceId = new Int2DoubleOpenHashMap();
        coefficientByAllianceId.defaultReturnValue(0.0d);
        for (Int2ObjectOpenHashMap.Entry<Int2ObjectOpenHashMap<IntOpenHashSet>> entry : declarantsByAllianceTurn.int2ObjectEntrySet()) {
            Int2ObjectOpenHashMap<IntOpenHashSet> turnToDeclarants = entry.getValue();
            if (turnToDeclarants.isEmpty()) {
                continue;
            }
            int coordinatedTurns = 0;
            for (Int2ObjectOpenHashMap.Entry<IntOpenHashSet> turnEntry : turnToDeclarants.int2ObjectEntrySet()) {
                if (turnEntry.getValue().size() >= COORDINATED_DECLARANT_THRESHOLD) {
                    coordinatedTurns++;
                }
            }
            coefficientByAllianceId.put(entry.getIntKey(), coordinatedTurns / (double) turnToDeclarants.size());
        }

        Int2DoubleOpenHashMap result = new Int2DoubleOpenHashMap(nationIds.size());
        result.defaultReturnValue(0.0d);
        for (int nationId : nationIds) {
            result.put(nationId, coefficientByAllianceId.get(allianceByNationId.get(nationId)));
        }
        return result;
    }

    private static Int2DoubleOpenHashMap emptyCoordination() {
        Int2DoubleOpenHashMap result = new Int2DoubleOpenHashMap();
        result.defaultReturnValue(0.0d);
        return result;
    }

    private static double compositeScore(
            WeekTurnProfile peacetimeProfile,
            WeekTurnProfile wartimeProfile,
            WeekTurnProfile offensiveProfile,
            ResponseSummary responseSummary,
            double coordination,
            int currentWeekTurn
    ) {
        double weightedSum = 0.0d;
        double weightTotal = 0.0d;

        if (wartimeProfile.eligibleTurns >= MIN_WARTIME_TURNS) {
            weightedSum += wartimeProfile.valueAt(currentWeekTurn) * WARTIME_LOGIN_WEIGHT;
            weightTotal += WARTIME_LOGIN_WEIGHT;
            weightedSum += peacetimeProfile.valueAt(currentWeekTurn) * PEACETIME_STABILIZER_WEIGHT;
            weightTotal += PEACETIME_STABILIZER_WEIGHT;
        } else {
            weightedSum += peacetimeProfile.valueAt(currentWeekTurn) * PEACETIME_FALLBACK_WEIGHT;
            weightTotal += PEACETIME_FALLBACK_WEIGHT;
        }

        if (offensiveProfile.eventTurns >= MIN_OFFENSIVE_ACTION_SAMPLES) {
            weightedSum += offensiveProfile.valueAt(currentWeekTurn) * OFFENSIVE_ACTION_WEIGHT;
            weightTotal += OFFENSIVE_ACTION_WEIGHT;
        }
        if (responseSummary.samples > 0) {
            weightedSum += responseSummary.average * DEFENSIVE_RESPONSE_WEIGHT;
            weightTotal += DEFENSIVE_RESPONSE_WEIGHT;
        }

        double base = weightTotal == 0.0d
                ? GlobalPriorActivityProvider.DEFAULT_PRIOR
                : weightedSum / weightTotal;
        return clamp01(base + coordination * ALLIANCE_COORDINATION_UPLIFT_CAP);
    }

    private static double quantize(double value) {
        return clamp01(Math.round(value / ACTIVITY_TIE_EPSILON) * ACTIVITY_TIE_EPSILON);
    }

    private static double tieBreakJitter(RandomSource tieBreakRandom, int nationId, int currentWeekTurn) {
        long streamKey = (((long) nationId) << 32) ^ (currentWeekTurn & 0xffffffffL);
        return (tieBreakRandom.nextDouble(streamKey) - 0.5d) * TIE_BREAK_SCALE;
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static int offset(long absoluteTurn, long lookbackStartTurn, int windowLength) {
        long offset = absoluteTurn - lookbackStartTurn;
        if (offset < 0 || offset >= windowLength) {
            return -1;
        }
        return (int) offset;
    }

    public record NationSignals(
            double peacetimeWeekTurn,
            Double wartimeWeekTurn,
            Double offensiveActionWeekTurn,
            Double defensiveResponse,
            double allianceCoordination,
            double compositeScore,
            double resolvedScore,
            int wartimeEligibleTurns,
            int offensiveActionSamples,
            int defensiveResponseSamples
    ) {
    }

    private static final class NationWindow {
        private final boolean[] loginByOffset;
        private final boolean[] wartimeByOffset;
        private final boolean[] offensiveActionByOffset;
        private final List<Integer> defensiveDeclareOffsets;

        private NationWindow(int windowLength) {
            this.loginByOffset = new boolean[windowLength];
            this.wartimeByOffset = new boolean[windowLength];
            this.offensiveActionByOffset = new boolean[windowLength];
            this.defensiveDeclareOffsets = new ArrayList<>();
        }
    }

    private static final class WeekTurnProfile {
        private final double[] values;
        private final int eligibleTurns;
        private final int eventTurns;

        private WeekTurnProfile(double[] values, int eligibleTurns, int eventTurns) {
            this.values = values;
            this.eligibleTurns = eligibleTurns;
            this.eventTurns = eventTurns;
        }

        static WeekTurnProfile from(boolean[] activeByOffset, boolean[] eligibleByOffset, long lookbackStartTurn) {
            double[] numerators = new double[WEEK_TURNS];
            int[] denominators = new int[WEEK_TURNS];
            int eligibleTurns = 0;
            int eventTurns = 0;
            for (int offset = 0; offset < activeByOffset.length; offset++) {
                if (eligibleByOffset != null && !eligibleByOffset[offset]) {
                    continue;
                }
                int weekTurn = Math.floorMod((int) (lookbackStartTurn + offset), WEEK_TURNS);
                denominators[weekTurn]++;
                eligibleTurns++;
                if (activeByOffset[offset]) {
                    numerators[weekTurn]++;
                    eventTurns++;
                }
            }
            double[] values = new double[WEEK_TURNS];
            for (int weekTurn = 0; weekTurn < WEEK_TURNS; weekTurn++) {
                values[weekTurn] = denominators[weekTurn] == 0 ? 0.0d : numerators[weekTurn] / denominators[weekTurn];
            }
            return new WeekTurnProfile(values, eligibleTurns, eventTurns);
        }

        double valueAt(int currentWeekTurn) {
            return values[Math.floorMod(currentWeekTurn, WEEK_TURNS)];
        }
    }

    private static final class ResponseSummary {
        private final double average;
        private final int samples;

        private ResponseSummary(double average, int samples) {
            this.average = average;
            this.samples = samples;
        }

        static ResponseSummary from(boolean[] loginByOffset, List<Integer> defensiveDeclareOffsets, int windowLength) {
            if (defensiveDeclareOffsets.isEmpty()) {
                return new ResponseSummary(0.0d, 0);
            }
            double total = 0.0d;
            for (int declareOffset : defensiveDeclareOffsets) {
                int horizon = Math.min(windowLength - 1, declareOffset + RESPONSE_LOOKAHEAD_TURNS);
                int responseOffset = -1;
                for (int offset = Math.max(0, declareOffset); offset <= horizon; offset++) {
                    if (loginByOffset[offset]) {
                        responseOffset = offset;
                        break;
                    }
                }
                if (responseOffset >= 0) {
                    total += 1.0d - ((responseOffset - declareOffset) / (double) RESPONSE_LOOKAHEAD_TURNS);
                }
            }
            return new ResponseSummary(total / defensiveDeclareOffsets.size(), defensiveDeclareOffsets.size());
        }
    }
}