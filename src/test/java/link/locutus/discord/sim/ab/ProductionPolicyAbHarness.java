package link.locutus.discord.sim.ab;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.SideOpeningSettings;
import link.locutus.discord.sim.planners.SidePlannerSettings;
import link.locutus.discord.sim.planners.SidePolicy;
import link.locutus.discord.sim.planners.SideProjectionPolicies;
import link.locutus.discord.sim.planners.TreatyProvider;
import link.locutus.discord.sim.planners.compile.CompiledActiveWar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Symmetric A/B scorecard over synthetic scenario families using only public production planner APIs.
 *
 * <pre>
 * .\gradlew.bat runTestMain -PmainClass=link.locutus.discord.sim.ab.ProductionPolicyAbHarness --no-daemon --console=plain "-PappArgs=--policyA=A:projection:CONTROL --policyB=B:projection:NET_DAMAGE --scenarios=parity,partialRangeControl"
 * </pre>
 */
public final class ProductionPolicyAbHarness {
    private static final int DEFAULT_HORIZON_TURNS = 72;
    private static final int DEFAULT_POPULATION = 0;
    private static final String CSV_HEADER = String.join(",",
            "family",
            "horizon",
            "pass",
            "attackerCount",
            "defenderCount",
            "attackerPolicyName",
            "defenderPolicyName",
            "attackerMode",
            "defenderMode",
            "attackerObjective",
            "defenderObjective",
            "assignmentPairs",
            "idleAttackersFreeSlot",
            "idleAttackersFreeSlotPct",
            "objectiveMean",
            "objectiveP10",
            "objectiveP50",
            "objectiveP90",
            "diagnosticWarnings",
            "assignedWarTypes",
            "assignedAttackTypes",
            "bestMs",
            "avgMs"
    );

    private ProductionPolicyAbHarness() {
    }

    public static void main(String[] args) {
        int horizonTurns = optionInt(args, "horizon", DEFAULT_HORIZON_TURNS);
        int repetitions = Math.max(1, optionInt(args, "repetitions", 1));
        int requestedPopulation = optionInt(args, "population", optionInt(args, "maxPopulation", DEFAULT_POPULATION));
        PolicySpec policyA = PolicySpec.parse(option(args, "policyA"), "A");
        PolicySpec policyB = PolicySpec.parse(option(args, "policyB"), "B");
        System.out.print(renderCsv(
                horizonTurns,
                repetitions,
                requestedPopulation,
                policyA,
                policyB,
                option(args, "scenarios")
        ));
    }

    static String renderCsv(
            int horizonTurns,
            int repetitions,
            int requestedPopulation,
            PolicySpec policyA,
            PolicySpec policyB,
            String scenarioFilter
    ) {
        StringBuilder out = new StringBuilder();
        out.append(CSV_HEADER).append(System.lineSeparator());
        for (ScenarioFamily family : selectedScenarios(scenarioFilter)) {
            Fixture fixture = family.fixture(requestedPopulation);
            appendPass(out, family.cliName(), fixture, policyA, policyB, "AvsB", horizonTurns, repetitions);
            appendPass(out, family.cliName(), fixture.reversed(), policyB, policyA, "BvsA", horizonTurns, repetitions);
        }
        return out.toString();
    }

    private static void appendPass(
            StringBuilder out,
            String familyName,
            Fixture fixture,
            PolicySpec attackerSpec,
            PolicySpec defenderSpec,
            String pass,
            int horizonTurns,
            int repetitions
    ) {
        PassScorecard best = null;
        long totalNanos = 0L;
        for (int repetition = 0; repetition < repetitions; repetition++) {
            long startNanos = System.nanoTime();
            PassScorecard scorecard = runPass(fixture, attackerSpec, defenderSpec, horizonTurns);
            long elapsedNanos = System.nanoTime() - startNanos;
            totalNanos += elapsedNanos;
            scorecard = scorecard.withElapsedNanos(elapsedNanos);
            if (best == null || elapsedNanos < best.elapsedNanos()) {
                best = scorecard;
            }
        }
        double bestMs = best.elapsedNanos() / 1_000_000.0d;
        double avgMs = (totalNanos / (double) repetitions) / 1_000_000.0d;
        out.append(String.join(",",
                familyName,
                Integer.toString(horizonTurns),
                pass,
                Integer.toString(fixture.attackers().size()),
                Integer.toString(fixture.defenders().size()),
                attackerSpec.name(),
                defenderSpec.name(),
                attackerSpec.mode().cliName,
                defenderSpec.mode().cliName,
                attackerSpec.objective().name(),
                defenderSpec.objective().name(),
                Integer.toString(best.assignmentPairs()),
                Integer.toString(best.idleAttackersFreeSlot()),
                formatDouble(best.idleAttackersFreeSlotPct(), 2),
                formatDouble(best.objectiveMean(), 3),
                formatDouble(best.objectiveP10(), 3),
                formatDouble(best.objectiveP50(), 3),
                formatDouble(best.objectiveP90(), 3),
                Integer.toString(best.diagnosticWarnings()),
                enumCountSummary(WarType.values, best.assignedWarTypeCounts()),
                enumCountSummary(AttackType.values, best.assignedAttackTypeCounts()),
                formatDouble(bestMs, 3),
                formatDouble(avgMs, 3)
        )).append(System.lineSeparator());
    }

    static PassScorecard runPass(
            Fixture fixture,
            PolicySpec attackerSpec,
            PolicySpec defenderSpec,
            int horizonTurns
    ) {
        BlitzPlanner planner = new BlitzPlanner(
                SimTuning.defaults(),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                attackerSpec.objective().objective()
        );
        BlitzAssignment assignment = planner.assign(
                fixture.attackers(),
                fixture.defenders(),
                attackerSpec.actingPolicy(),
                defenderSpec.passivePolicy(),
                0,
                List.of(),
                fixture.activeWars(),
                horizonTurns
        );

        int[] warTypeCounts = new int[WarType.values.length];
        int[] attackTypeCounts = new int[AttackType.values.length];
        for (Map.Entry<Integer, List<Integer>> entry : assignment.assignment().entrySet()) {
            int attackerNationId = entry.getKey();
            for (Integer defenderNationId : entry.getValue()) {
                int warTypeOrdinal = assignment.initialWarTypeOrdinal(attackerNationId, defenderNationId);
                if (warTypeOrdinal >= 0 && warTypeOrdinal < warTypeCounts.length) {
                    warTypeCounts[warTypeOrdinal]++;
                }
                int attackTypeOrdinal = assignment.initialAttackTypeOrdinal(attackerNationId, defenderNationId);
                if (attackTypeOrdinal >= 0 && attackTypeOrdinal < attackTypeCounts.length) {
                    attackTypeCounts[attackTypeOrdinal]++;
                }
            }
        }

        int idleAttackersFreeSlot = 0;
        for (DBNationSnapshot attacker : fixture.attackers()) {
            if (attacker.rawFreeOff() > 0 && assignment.targetsFor(attacker.nationId()).isEmpty()) {
                idleAttackersFreeSlot++;
            }
        }

        return new PassScorecard(
                assignment.pairCount(),
                idleAttackersFreeSlot,
                fixture.attackers().isEmpty() ? 0.0d : (100.0d * idleAttackersFreeSlot / fixture.attackers().size()),
                assignment.objectiveSummary().mean(),
                assignment.objectiveSummary().p10(),
                assignment.objectiveSummary().p50(),
                assignment.objectiveSummary().p90(),
                assignment.diagnostics().size(),
                warTypeCounts,
                attackTypeCounts,
                0L
        );
    }

    enum PolicyMode {
        PROJECTION("projection");

        private final String cliName;

        PolicyMode(String cliName) {
            this.cliName = cliName;
        }

        static PolicyMode parse(String value) {
            for (PolicyMode mode : values()) {
                if (mode.cliName.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown policy mode: " + value);
        }
    }

    static final class PolicySpec {
        private final String name;
        private final PolicyMode mode;
        private final BlitzObjective objective;
        private final int projectedAuditLimit;
        private final int maxLaterDeclarationsPerTurn;
        private final double[] warTypeWeights;
        private final double[] attackTypeWeights;
        private final Double minimumViabilityProbe;
        private final Boolean allowLegalSpecialistFallback;
        private final Boolean admitPositiveOpeningBaseline;

        private PolicySpec(
                String name,
                PolicyMode mode,
                BlitzObjective objective,
                int projectedAuditLimit,
                int maxLaterDeclarationsPerTurn,
                double[] warTypeWeights,
                double[] attackTypeWeights,
                Double minimumViabilityProbe,
                Boolean allowLegalSpecialistFallback,
                Boolean admitPositiveOpeningBaseline
        ) {
            this.name = name;
            this.mode = mode;
            this.objective = objective;
            this.projectedAuditLimit = projectedAuditLimit;
            this.maxLaterDeclarationsPerTurn = maxLaterDeclarationsPerTurn;
            this.warTypeWeights = warTypeWeights;
            this.attackTypeWeights = attackTypeWeights;
            this.minimumViabilityProbe = minimumViabilityProbe;
            this.allowLegalSpecialistFallback = allowLegalSpecialistFallback;
            this.admitPositiveOpeningBaseline = admitPositiveOpeningBaseline;
        }

        static PolicySpec parse(String value, String fallbackName) {
            String effective = value == null || value.isBlank()
                    ? fallbackName + ":projection:NET_DAMAGE"
                    : value;
            String[] rawParts = effective.split(":", 4);

            String name;
            String modeValue;
            String objectiveValue;
            String flags = null;
            if (rawParts.length == 1) {
                name = fallbackName;
                modeValue = PolicyMode.PROJECTION.cliName;
                objectiveValue = rawParts[0];
            } else if (rawParts.length == 2) {
                name = fallbackName;
                modeValue = rawParts[0];
                objectiveValue = rawParts[1];
            } else {
                name = rawParts[0];
                modeValue = rawParts[1];
                objectiveValue = rawParts[2];
                if (rawParts.length == 4) {
                    flags = rawParts[3];
                }
            }

            int projectedAuditLimit = SidePlannerSettings.DEFAULT_PROJECTED_AUDIT_LIMIT;
            int maxLaterDeclarationsPerTurn = SidePlannerSettings.DEFAULT_MAX_LATER_DECLARATIONS_PER_TURN;
            double[] warTypeWeights = neutralWarTypeWeights();
            double[] attackTypeWeights = neutralAttackTypeWeights();
            Double minimumViabilityProbe = null;
            Boolean allowLegalSpecialistFallback = null;
            Boolean admitPositiveOpeningBaseline = null;
            if (flags != null && !flags.isBlank()) {
                for (String flag : flags.split(";")) {
                    String[] kv = flag.split("=", 2);
                    if (kv.length != 2) {
                        throw new IllegalArgumentException("Unknown policy flag: " + flag);
                    }
                    if (kv[0].equalsIgnoreCase("audit")) {
                        projectedAuditLimit = Integer.parseInt(kv[1]);
                    } else if (kv[0].equalsIgnoreCase("laterCap")) {
                        maxLaterDeclarationsPerTurn = Integer.parseInt(kv[1]);
                    } else if (kv[0].equalsIgnoreCase("war")) {
                        parseWeights(kv[1], WarType.values, warTypeWeights, "war");
                    } else if (kv[0].equalsIgnoreCase("attack")) {
                        parseWeights(kv[1], AttackType.values, attackTypeWeights, "attack");
                    } else if (kv[0].equalsIgnoreCase("minProbe")) {
                        minimumViabilityProbe = Double.parseDouble(kv[1]);
                    } else if (kv[0].equalsIgnoreCase("specialists")) {
                        allowLegalSpecialistFallback = Boolean.parseBoolean(kv[1]);
                    } else if (kv[0].equalsIgnoreCase("positiveBaseline")) {
                        admitPositiveOpeningBaseline = Boolean.parseBoolean(kv[1]);
                    } else {
                        throw new IllegalArgumentException("Unknown policy flag: " + flag);
                    }
                }
            }

            return new PolicySpec(
                    name,
                    PolicyMode.parse(modeValue),
                    BlitzObjective.valueOf(objectiveValue.toUpperCase(Locale.ROOT)),
                    projectedAuditLimit,
                    maxLaterDeclarationsPerTurn,
                    warTypeWeights,
                    attackTypeWeights,
                    minimumViabilityProbe,
                    allowLegalSpecialistFallback,
                    admitPositiveOpeningBaseline
            );
        }

        String name() {
            return name;
        }

        PolicyMode mode() {
            return mode;
        }

        BlitzObjective objective() {
            return objective;
        }

        SidePolicy actingPolicy() {
            return buildPolicy(true);
        }

        SidePolicy passivePolicy() {
            return buildPolicy(false);
        }

        private SidePolicy buildPolicy(boolean allowInitialDeclarations) {
            StrategicObjective strategicObjective = objective.objective();
            SideOpeningSettings opening = new SideOpeningSettings(
                    Arrays.copyOf(warTypeWeights, warTypeWeights.length),
                    Arrays.copyOf(attackTypeWeights, attackTypeWeights.length),
                    admissionPolicy(strategicObjective)
            );
            SidePlannerSettings basePlanner = SidePlannerSettings.fromTuning(SimTuning.defaults());
            SidePlannerSettings planner = (allowInitialDeclarations
                    ? basePlanner.withIdlePressureWeight(SidePlannerSettings.DEFAULT_ACTING_IDLE_PRESSURE_WEIGHT)
                    : basePlanner)
                    .withProjectedAuditLimit(projectedAuditLimit)
                    .withMaxLaterDeclarationsPerTurn(maxLaterDeclarationsPerTurn);
            SideProjectionPolicies projection = switch (mode) {
                case PROJECTION -> SideProjectionPolicies.objectiveDriven(strategicObjective, opening);
            };
            return new SidePolicy(
                    name,
                    strategicObjective,
                    planner,
                    opening,
                    projection,
                    SidePolicy.NO_OP_ACTOR,
                    allowInitialDeclarations
            );
        }

        private CandidateEdgeAdmissionPolicy admissionPolicy(StrategicObjective strategicObjective) {
            CandidateEdgeAdmissionPolicy base = strategicObjective.candidateEdgeAdmissionPolicy();
            if (base == null) {
                base = CandidateEdgeAdmissionPolicy.defaultPolicy();
            }
            return new CandidateEdgeAdmissionPolicy(
                    minimumViabilityProbe == null ? base.minimumViabilityProbe() : minimumViabilityProbe,
                    allowLegalSpecialistFallback == null ? base.allowLegalSpecialistFallback() : allowLegalSpecialistFallback,
                    admitPositiveOpeningBaseline == null ? base.admitPositiveOpeningBaseline() : admitPositiveOpeningBaseline
            );
        }

        private static double[] neutralWarTypeWeights() {
            double[] weights = new double[WarType.values.length];
            Arrays.fill(weights, 1d);
            return weights;
        }

        private static double[] neutralAttackTypeWeights() {
            double[] weights = new double[AttackType.values.length];
            Arrays.fill(weights, 1d);
            return weights;
        }

        private static <E extends Enum<E>> void parseWeights(
                String value,
                E[] values,
                double[] weights,
                String flagName
        ) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(flagName + " weights must not be blank");
            }
            for (String entry : value.split(",")) {
                String[] kv = entry.split(":", 2);
                if (kv.length != 2) {
                    throw new IllegalArgumentException("Expected " + flagName + " weight NAME:VALUE, got: " + entry);
                }
                E enumValue = enumValue(values, kv[0].trim(), flagName);
                weights[enumValue.ordinal()] = Double.parseDouble(kv[1].trim());
            }
        }

        private static <E extends Enum<E>> E enumValue(E[] values, String name, String flagName) {
            for (E value : values) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown " + flagName + " enum value: " + name);
        }
    }

    enum ScenarioFamily {
        PARITY("parity", 8),
        ATTACKER_FAVORED("attackerFavored", 8),
        DEFENDER_FAVORED("defenderFavored", 8),
        UNMILITARIZED_VS_FULL("unmilitarizedVsFull", 6),
        MIXED_STRONG_DEFENDERS("mixedStrongDefenders", 10),
        EXHAUSTED_VS_FULL_REBUY("exhaustedVsFullRebuy", 8),
        RESET_TIMING("resetTiming", 8),
        ACTIVE_WAR_SLOT_PRESSURE("activeWarSlotPressure", 8),
        ACTIVE_WAR_BARELY_WINNING("activeWarBarelyWinning", 8),
        ACTIVE_WAR_DECISIVE("activeWarDecisive", 8),
        SLOT_SATURATED_FRONT("slotSaturatedFront", 8),
        PARTIAL_RANGE_CONTROL("partialRangeControl", 10),
        DEMILITARIZED_BREAKOUT("demilitarizedBreakout", 8),
        BEIGE_REBUILD_BREAKOUT("beigeRebuildBreakout", 8),
        CONTROL_UNATTAINABLE("controlUnattainable", 6),
        LOST_CONTROL_SPECIALISTS("lostControlSpecialists", 8),
        CONVENTIONAL_THEN_SPECIALISTS("conventionalThenSpecialists", 8),
        ALLY_SLOT_CONFLICT("allySlotConflict", 8);

        private final String cliName;
        private final int tinyPopulation;

        ScenarioFamily(String cliName, int tinyPopulation) {
            this.cliName = cliName;
            this.tinyPopulation = tinyPopulation;
        }

        String cliName() {
            return cliName;
        }

        Fixture fixture(int requestedPopulation) {
            int population = requestedPopulation > 0 ? requestedPopulation : tinyPopulation;
            return switch (this) {
                case PARITY -> Fixture.create(population, this::parityAttacker, this::parityDefender);
                case ATTACKER_FAVORED -> Fixture.create(population, this::favoredAttacker, this::weakDefender);
                case DEFENDER_FAVORED -> Fixture.create(population, this::weakAttacker, this::favoredDefender);
                case UNMILITARIZED_VS_FULL -> Fixture.create(population, this::unmilitarizedAttacker, this::favoredDefender);
                case MIXED_STRONG_DEFENDERS -> Fixture.create(population, this::parityAttacker, this::mixedDefender);
                case EXHAUSTED_VS_FULL_REBUY -> Fixture.create(population, this::exhaustedRebuyAttacker, this::fullRebuyDefender);
                case RESET_TIMING -> Fixture.create(population, this::imminentResetAttacker, this::delayedResetDefender);
                case ACTIVE_WAR_SLOT_PRESSURE -> Fixture.create(population, this::activeWarPressedAttacker, this::activeWarPressedDefender);
                case ACTIVE_WAR_BARELY_WINNING -> Fixture.createSeededActiveWars(population, this::barelyWinningActiveWarAttacker, this::barelyWinningActiveWarDefender, false);
                case ACTIVE_WAR_DECISIVE -> Fixture.createSeededActiveWars(population, this::decisiveActiveWarAttacker, this::decisiveActiveWarDefender, true);
                case SLOT_SATURATED_FRONT -> Fixture.create(population, this::slotSaturatedAttacker, this::slotSaturatedDefender);
                case PARTIAL_RANGE_CONTROL -> Fixture.create(population, this::partialRangeAttacker, this::partialRangeDefender);
                case DEMILITARIZED_BREAKOUT -> Fixture.create(population, this::demilitarizedBreakoutAttacker, this::parityDefender);
                case BEIGE_REBUILD_BREAKOUT -> Fixture.create(population, this::beigeRebuildBreakoutAttacker, this::beigeRebuildBreakoutDefender);
                case CONTROL_UNATTAINABLE -> Fixture.create(population, this::controlUnattainableAttacker, this::controlUnattainableDefender);
                case LOST_CONTROL_SPECIALISTS -> Fixture.create(population, this::lostControlSpecialistAttacker, this::lostControlSpecialistDefender);
                case CONVENTIONAL_THEN_SPECIALISTS -> Fixture.create(population, this::conventionalThenSpecialistAttacker, this::conventionalThenSpecialistDefender);
                case ALLY_SLOT_CONFLICT -> Fixture.create(population, this::allySlotConflictAttacker, this::allySlotConflictDefender);
            };
        }

        static ScenarioFamily parse(String value) {
            for (ScenarioFamily family : values()) {
                if (family.cliName.equalsIgnoreCase(value) || family.name().equalsIgnoreCase(value)) {
                    return family;
                }
            }
            throw new IllegalArgumentException("Unknown scenario family: " + value);
        }

        private DBNationSnapshot parityAttacker(int index) {
            return nation(10_000 + index, 1, index, 20 + index % 4, 1.0d, 3);
        }

        private DBNationSnapshot parityDefender(int index) {
            return nation(20_000 + index, 2, index, 20 + index % 4, 1.0d, 1);
        }

        private DBNationSnapshot favoredAttacker(int index) {
            return nation(10_000 + index, 1, index, 23 + index % 5, 1.35d, 3);
        }

        private DBNationSnapshot weakAttacker(int index) {
            return nation(10_000 + index, 1, index, 18 + index % 3, 0.55d, 3);
        }

        private DBNationSnapshot favoredDefender(int index) {
            return nation(20_000 + index, 2, index, 25 + index % 5, 1.55d, 1);
        }

        private DBNationSnapshot weakDefender(int index) {
            return nation(20_000 + index, 2, index, 18 + index % 3, 0.60d, 1);
        }

        private DBNationSnapshot unmilitarizedAttacker(int index) {
            return nation(10_000 + index, 1, index, 18 + index % 3, 0.15d, 3);
        }

        private DBNationSnapshot mixedDefender(int index) {
            double multiplier = index < 3 ? 1.75d : 0.50d;
            int cities = index < 3 ? 28 + index : 16 + index % 4;
            return nation(20_000 + index, 2, index, cities, multiplier, 1);
        }

        private DBNationSnapshot exhaustedRebuyAttacker(int index) {
            return exhaustDailyBuys(nation(10_000 + index, 1, index, 22 + index % 4, 0.95d, 3));
        }

        private DBNationSnapshot fullRebuyDefender(int index) {
            return nation(20_000 + index, 2, index, 22 + index % 4, 0.95d, 1);
        }

        private DBNationSnapshot imminentResetAttacker(int index) {
            DBNationSnapshot snapshot = nation(10_000 + index, 1, index, 21 + index % 4, 0.75d, 3)
                    .toBuilder()
                    .resetHourUtc((byte) 0)
                    .build();
            DBNationSnapshot.Builder builder = snapshot.toBuilder();
            builder.pendingBuyNextTurn(MilitaryUnit.SOLDIER, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.SOLDIER) / 2))
                    .pendingBuyNextTurn(MilitaryUnit.TANK, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.TANK) / 2))
                    .pendingBuyNextTurn(MilitaryUnit.AIRCRAFT, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.AIRCRAFT) / 2))
                    .pendingBuyNextTurn(MilitaryUnit.SHIP, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.SHIP) / 2));
            return builder.build();
        }

        private DBNationSnapshot delayedResetDefender(int index) {
            DBNationSnapshot.Builder builder = nation(20_000 + index, 2, index, 21 + index % 4, 1.20d, 1)
                    .toBuilder()
                    .resetHourUtc((byte) 23);
            return exhaustDailyBuys(builder.build());
        }

        private DBNationSnapshot activeWarPressedAttacker(int index) {
            DBNationSnapshot.Builder builder = nation(10_000 + index, 1, index, 23 + index % 4, 1.05d, 3)
                    .toBuilder()
                    .currentOffensiveWars(index % 3)
                    .currentDefensiveWars(index % 2);
            builder.activeOpponentNationId(30_000 + index);
            if ((index & 1) == 0) {
                builder.activeOpponentNationId(31_000 + index);
            }
            return builder.build();
        }

        private DBNationSnapshot activeWarPressedDefender(int index) {
            DBNationSnapshot.Builder builder = nation(20_000 + index, 2, index, 23 + index % 4, 1.05d, 1)
                    .toBuilder()
                    .currentDefensiveWars(index % 3);
            builder.activeOpponentNationId(40_000 + index);
            return builder.build();
        }

        private DBNationSnapshot barelyWinningActiveWarAttacker(int index) {
            double multiplier = (index & 1) == 0 ? 0.78d : 1.28d;
            return activeWarParticipant(nation(10_000 + index, 1, index, 22 + index % 4, multiplier, 3), 20_000 + index, true);
        }

        private DBNationSnapshot barelyWinningActiveWarDefender(int index) {
            double multiplier = (index & 1) == 0 ? 1.28d : 0.78d;
            return activeWarParticipant(nation(20_000 + index, 2, index, 22 + index % 4, multiplier, 1), 10_000 + index, false);
        }

        private DBNationSnapshot decisiveActiveWarAttacker(int index) {
            double multiplier = (index & 1) == 0 ? 1.45d : 0.65d;
            return activeWarParticipant(nation(10_000 + index, 1, index, 23 + index % 4, multiplier, 3), 20_000 + index, true);
        }

        private DBNationSnapshot decisiveActiveWarDefender(int index) {
            double multiplier = (index & 1) == 0 ? 0.70d : 1.55d;
            return activeWarParticipant(nation(20_000 + index, 2, index, 23 + index % 4, multiplier, 1), 10_000 + index, false);
        }

        private DBNationSnapshot slotSaturatedAttacker(int index) {
            return nation(10_000 + index, 1, index, 24 + index % 4, 1.25d, index % 4 == 0 ? 1 : 3)
                    .toBuilder()
                    .currentOffensiveWars(index % 4 == 0 ? 2 : 0)
                    .build();
        }

        private DBNationSnapshot slotSaturatedDefender(int index) {
            int defensiveWars = index < 3 ? 2 : 0;
            return nation(20_000 + index, 2, index, index < 3 ? 29 + index : 16 + index % 3, index < 3 ? 1.85d : 0.45d, 1)
                    .toBuilder()
                    .currentDefensiveWars(defensiveWars)
                    .build();
        }

        private DBNationSnapshot partialRangeAttacker(int index) {
            int cities = index < 4 ? 31 + index : 16 + index % 4;
            double multiplier = index < 4 ? 1.45d : 0.85d;
            return nation(10_000 + index, 1, index, cities, multiplier, 3);
        }

        private DBNationSnapshot partialRangeDefender(int index) {
            int cities = index < 4 ? 34 + index : 13 + index % 4;
            double multiplier = index < 4 ? 1.50d : 0.70d;
            return nation(20_000 + index, 2, index, cities, multiplier, 1);
        }

        private DBNationSnapshot demilitarizedBreakoutAttacker(int index) {
            if (index < 2) {
                return nation(10_000 + index, 1, index, 22 + index, 1.05d, 3);
            }
            return nation(10_000 + index, 1, index, 20 + index % 3, 0.08d, 3);
        }

        private DBNationSnapshot beigeRebuildBreakoutAttacker(int index) {
            DBNationSnapshot snapshot = nation(10_000 + index, 1, index, 21 + index % 4, 0.10d, 3)
                    .toBuilder()
                    .beigeTurns(6)
                    .resetHourUtc((byte) 0)
                    .build();
            DBNationSnapshot.Builder builder = snapshot.toBuilder();
            builder.pendingBuyNextTurn(MilitaryUnit.SOLDIER, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.SOLDIER)))
                    .pendingBuyNextTurn(MilitaryUnit.TANK, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.TANK)))
                    .pendingBuyNextTurn(MilitaryUnit.AIRCRAFT, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.AIRCRAFT)))
                    .pendingBuyNextTurn(MilitaryUnit.SHIP, Math.max(1, snapshot.dailyBuyCap(MilitaryUnit.SHIP)));
            return builder.build();
        }

        private DBNationSnapshot beigeRebuildBreakoutDefender(int index) {
            return nation(20_000 + index, 2, index, 20 + index % 4, 0.85d, 1);
        }

        private DBNationSnapshot controlUnattainableAttacker(int index) {
            return nation(10_000 + index, 1, index, 17 + index % 3, 0.45d, 3);
        }

        private DBNationSnapshot controlUnattainableDefender(int index) {
            return nation(20_000 + index, 2, index, 28 + index % 4, 1.60d, 1);
        }

        private DBNationSnapshot lostControlSpecialistAttacker(int index) {
            long specialistProjects = projectMask(Projects.MISSILE_LAUNCH_PAD, Projects.NUCLEAR_RESEARCH_FACILITY);
            return nation(10_000 + index, 1, index, 21 + index % 4, 0.30d, 3)
                    .toBuilder()
                    .projectBits(specialistProjects)
                    .unit(MilitaryUnit.MISSILE, 4 + index % 3)
                    .unit(MilitaryUnit.NUKE, index % 2 == 0 ? 2 : 1)
                    .build();
        }

        private DBNationSnapshot lostControlSpecialistDefender(int index) {
            return nation(20_000 + index, 2, index, 29 + index % 5, 1.55d, 1);
        }

        private DBNationSnapshot conventionalThenSpecialistAttacker(int index) {
            long specialistProjects = projectMask(Projects.MISSILE_LAUNCH_PAD, Projects.NUCLEAR_RESEARCH_FACILITY);
            return nation(10_000 + index, 1, index, 23 + index % 4, 0.70d, 3)
                    .toBuilder()
                    .projectBits(specialistProjects)
                    .unit(MilitaryUnit.MISSILE, 5 + index % 2)
                    .unit(MilitaryUnit.NUKE, 1)
                    .build();
        }

        private DBNationSnapshot conventionalThenSpecialistDefender(int index) {
            return nation(20_000 + index, 2, index, 27 + index % 5, 1.20d, 1);
        }

        private DBNationSnapshot allySlotConflictAttacker(int index) {
            if (index < 4) {
                DBNationSnapshot.Builder builder = nation(10_000 + index, 1, index, 23 + index % 3, 1.10d, 3)
                        .toBuilder()
                        .currentOffensiveWars(2);
                builder.activeOpponentNationId(50_000 + index);
                builder.activeOpponentNationId(51_000 + index);
                return builder.build();
            }
            return nation(10_000 + index, 1, index, 23 + index % 3, 1.10d, 3);
        }

        private DBNationSnapshot allySlotConflictDefender(int index) {
            if (index < 3) {
                return nation(20_000 + index, 2, index, 28 + index, 1.80d, 1);
            }
            return nation(20_000 + index, 2, index, 14 + index % 4, 0.40d, 1);
        }

        private static DBNationSnapshot activeWarParticipant(DBNationSnapshot source, int opponentNationId, boolean offensive) {
            DBNationSnapshot.Builder builder = source.toBuilder().activeOpponentNationId(opponentNationId);
            if (offensive) {
                builder.currentOffensiveWars(1);
            } else {
                builder.currentDefensiveWars(1);
            }
            return builder.build();
        }
    }

    private static List<ScenarioFamily> selectedScenarios(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of(ScenarioFamily.values());
        }
        String[] names = filter.split(",");
        List<ScenarioFamily> families = new ArrayList<>(names.length);
        for (String name : names) {
            families.add(ScenarioFamily.parse(name.trim()));
        }
        return List.copyOf(families);
    }

    interface NationFactory {
        DBNationSnapshot create(int index);
    }

    record Fixture(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders, List<CompiledActiveWar> activeWars) {
        static Fixture create(int population, NationFactory attackerFactory, NationFactory defenderFactory) {
            return create(population, attackerFactory, defenderFactory, List.of());
        }

        static Fixture createSeededActiveWars(
                int population,
                NationFactory attackerFactory,
                NationFactory defenderFactory,
                boolean decisive
        ) {
            List<CompiledActiveWar> activeWars = new ArrayList<>(population);
            for (int index = 0; index < population; index++) {
                boolean attackerInitiallyWinning = (index & 1) == 0;
                int attackerResistance = decisive
                        ? (attackerInitiallyWinning ? 92 : 34)
                        : (attackerInitiallyWinning ? 61 : 56);
                int defenderResistance = decisive
                        ? (attackerInitiallyWinning ? 31 : 94)
                        : (attackerInitiallyWinning ? 55 : 62);
                CompiledActiveWar.FlagOwner favoredOwner = attackerInitiallyWinning
                        ? CompiledActiveWar.FlagOwner.ATTACKER
                        : CompiledActiveWar.FlagOwner.DEFENDER;
                CompiledActiveWar.FlagOwner otherOwner = attackerInitiallyWinning
                        ? CompiledActiveWar.FlagOwner.DEFENDER
                        : CompiledActiveWar.FlagOwner.ATTACKER;
                activeWars.add(new CompiledActiveWar(
                        10_000 + index,
                        20_000 + index,
                        WarType.ORD,
                        decisive ? 8 : 18,
                        decisive ? (attackerInitiallyWinning ? 11 : 4) : 8,
                        decisive ? (attackerInitiallyWinning ? 3 : 11) : 8,
                        attackerResistance,
                        defenderResistance,
                        favoredOwner,
                        decisive ? favoredOwner : CompiledActiveWar.FlagOwner.NONE,
                        decisive ? favoredOwner : otherOwner,
                        false,
                        false
                ));
            }
            return create(population, attackerFactory, defenderFactory, activeWars);
        }

        private static Fixture create(
                int population,
                NationFactory attackerFactory,
                NationFactory defenderFactory,
                List<CompiledActiveWar> activeWars
        ) {
            List<DBNationSnapshot> attackers = new ArrayList<>(population);
            List<DBNationSnapshot> defenders = new ArrayList<>(population);
            for (int index = 0; index < population; index++) {
                attackers.add(attackerFactory.create(index));
                defenders.add(defenderFactory.create(index));
            }
            return new Fixture(List.copyOf(attackers), List.copyOf(defenders), List.copyOf(activeWars));
        }

        Fixture reversed() {
            return new Fixture(defenders, attackers, reverseActiveWars(activeWars));
        }

        private static List<CompiledActiveWar> reverseActiveWars(List<CompiledActiveWar> activeWars) {
            if (activeWars.isEmpty()) {
                return List.of();
            }
            List<CompiledActiveWar> reversed = new ArrayList<>(activeWars.size());
            for (CompiledActiveWar war : activeWars) {
                reversed.add(new CompiledActiveWar(
                        war.defenderNationId(),
                        war.attackerNationId(),
                        war.warType(),
                        war.startTurn(),
                        war.defenderMaps(),
                        war.attackerMaps(),
                        war.defenderResistance(),
                        war.attackerResistance(),
                        reverseOwner(war.groundSuperiorityOwner()),
                        reverseOwner(war.airSuperiorityOwner()),
                        reverseOwner(war.blockadeOwner()),
                        war.defenderFortified(),
                        war.attackerFortified()
                ));
            }
            return List.copyOf(reversed);
        }

        private static CompiledActiveWar.FlagOwner reverseOwner(CompiledActiveWar.FlagOwner owner) {
            return switch (owner) {
                case ATTACKER -> CompiledActiveWar.FlagOwner.DEFENDER;
                case DEFENDER -> CompiledActiveWar.FlagOwner.ATTACKER;
                case NONE -> CompiledActiveWar.FlagOwner.NONE;
            };
        }
    }

    record PassScorecard(
            int assignmentPairs,
            int idleAttackersFreeSlot,
            double idleAttackersFreeSlotPct,
            double objectiveMean,
            double objectiveP10,
            double objectiveP50,
            double objectiveP90,
            int diagnosticWarnings,
            int[] assignedWarTypeCounts,
            int[] assignedAttackTypeCounts,
            long elapsedNanos
    ) {
        PassScorecard {
            assignedWarTypeCounts = Arrays.copyOf(assignedWarTypeCounts, assignedWarTypeCounts.length);
            assignedAttackTypeCounts = Arrays.copyOf(assignedAttackTypeCounts, assignedAttackTypeCounts.length);
        }

        PassScorecard withElapsedNanos(long value) {
            return new PassScorecard(
                    assignmentPairs,
                    idleAttackersFreeSlot,
                    idleAttackersFreeSlotPct,
                    objectiveMean,
                    objectiveP10,
                    objectiveP50,
                    objectiveP90,
                    diagnosticWarnings,
                    assignedWarTypeCounts,
                    assignedAttackTypeCounts,
                    value
            );
        }
    }

    private static DBNationSnapshot exhaustDailyBuys(DBNationSnapshot snapshot) {
        DBNationSnapshot.Builder builder = snapshot.toBuilder();
        for (MilitaryUnit unit : List.of(MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP)) {
            builder.unitBoughtToday(unit, snapshot.dailyBuyCap(unit));
        }
        return builder.build();
    }

    private static DBNationSnapshot nation(
            int nationId,
            int teamId,
            int offset,
            int cities,
            double militaryMultiplier,
            int maxOff
    ) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .cities(cities)
                .cityInfra(uniformInfra(cities, 1_800.0d + (offset % 4) * 150.0d))
                .maxOff(maxOff)
                .unit(MilitaryUnit.SOLDIER, scaled(250_000 + offset * 2_000, militaryMultiplier))
                .unit(MilitaryUnit.TANK, scaled(20_000 + offset * 150, militaryMultiplier))
                .unit(MilitaryUnit.AIRCRAFT, scaled(1_600 + offset * 20, militaryMultiplier))
                .unit(MilitaryUnit.SHIP, scaled(250 + offset * 4, militaryMultiplier))
                .unit(MilitaryUnit.MISSILE, militaryMultiplier < 0.30d ? 3 : 0)
                .unit(MilitaryUnit.NUKE, militaryMultiplier < 0.30d ? 1 : 0)
                .resource(ResourceType.MONEY, 1_000_000d + cities * 125_000d)
                .resource(ResourceType.FOOD, 2_000d + cities * 900d)
                .resource(ResourceType.GASOLINE, 1_000d + cities * 450d)
                .resource(ResourceType.MUNITIONS, 1_000d + cities * 450d)
                .resource(ResourceType.STEEL, 900d + cities * 325d)
                .resource(ResourceType.ALUMINUM, 900d + cities * 325d)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private static int scaled(int value, double multiplier) {
        return Math.max(0, (int) Math.round(value * multiplier));
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] values = new double[cities];
        Arrays.fill(values, infra);
        return values;
    }

    private static long projectMask(Project... projects) {
        long bits = 0L;
        for (Project project : projects) {
            bits |= 1L << project.ordinal();
        }
        return bits;
    }

    private static String enumCountSummary(Enum<?>[] values, int[] counts) {
        List<String> populated = new ArrayList<>();
        for (int index = 0; index < Math.min(values.length, counts.length); index++) {
            if (counts[index] > 0) {
                populated.add(values[index].name() + ":" + counts[index]);
            }
        }
        return populated.isEmpty() ? "none" : String.join("|", populated);
    }

    private static String formatDouble(double value, int scale) {
        return String.format(Locale.ROOT, "%1$." + scale + "f", value);
    }

    private static int optionInt(String[] args, String name, int defaultValue) {
        String value = option(args, name);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static String option(String[] args, String name) {
        String prefix = "--" + name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
    }
}
