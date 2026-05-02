package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic strategic lane scorecard for planner behavior review.
 *
 * <p>This is intentionally not a JMH benchmark. It runs tiny synthetic scenario families through
 * comparable assignment lanes and emits compact CSV rows with strategic and throughput signals.
 * Use pipeline/JMH benchmarks for micro-performance once this identifies a hot behavior path.</p>
 *
 * <pre>
 * .\gradlew.bat runTestMain -PmainClass=link.locutus.discord.sim.planners.StrategicLaneComparisonHarness --no-daemon --console=plain
 * </pre>
 */
public final class StrategicLaneComparisonHarness {
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();
    private static final int DEFAULT_HORIZON_TURNS = 72;
    private static final int DEFAULT_POPULATION = 0;

    private StrategicLaneComparisonHarness() {
    }

    public static void main(String[] args) {
        int horizonTurns = optionInt(args, "horizon", DEFAULT_HORIZON_TURNS);
        int repetitions = Math.max(1, optionInt(args, "repetitions", 1));
        int requestedPopulation = optionInt(args, "population", DEFAULT_POPULATION);
        System.out.println("family,lane,objective,horizon,attackers,defenders,edges,assignments,idleViableAttackers,strongDefenderCoveragePct,defenderCoverageByTier,maxWarsPerAttacker,avgAssignedCounterRisk,terminalObjective,attackerTerminalValue,defenderTerminalValue,attackerUnitLosses,defenderUnitLosses,attackerRebuyPreserved,defenderRebuyPreserved,attackerRebuyDestroyed,defenderRebuyDestroyed,attackerInfraDestroyed,defenderInfraDestroyed,attackerWiped,defenderWiped,attackerWipeRisk,defenderWipeRisk,activeWars,attackerControlFlags,defenderControlFlags,attackerWinningWars,defenderWinningWars,concludedWars,concludedWarsByDefenderTier,assignedWarTypes,assignedAttackTypes,payloadBytes,bestMs,avgMs");
        for (ScenarioFamily family : ScenarioFamily.values()) {
            Fixture fixture = family.fixture(requestedPopulation);
            for (Lane lane : Lane.values()) {
                for (BlitzObjective objective : lane.objectives()) {
                    Scorecard best = null;
                    long totalNanos = 0L;
                    for (int repetition = 0; repetition < repetitions; repetition++) {
                        long startNanos = System.nanoTime();
                        Scorecard scorecard = fixture.run(lane, objective, horizonTurns);
                        long elapsedNanos = System.nanoTime() - startNanos;
                        totalNanos += elapsedNanos;
                        scorecard = scorecard.withElapsedNanos(elapsedNanos);
                        if (best == null || elapsedNanos < best.elapsedNanos()) {
                            best = scorecard;
                        }
                    }
                    double bestMs = best.elapsedNanos() / 1_000_000.0d;
                    double avgMs = (totalNanos / (double) repetitions) / 1_000_000.0d;
                    String row = String.format(Locale.ROOT,
                            "%s,%s,%s,%d,%d,%d,%d,%d,%d,%.3f,%s,%d,%.6f,%.3f,%.3f,%.3f,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%s,%d,%.3f,%.3f",
                            family.cliName,
                            lane.cliName,
                            objective.name(),
                            horizonTurns,
                            fixture.scenario.attackerCount(),
                            fixture.scenario.defenderCount(),
                            best.edgeCount(),
                            best.assignmentCount(),
                            best.idleViableAttackers(),
                            best.strongDefenderCoveragePct(),
                            best.defenderCoverageByTier(),
                            best.maxWarsPerAttacker(),
                            best.avgAssignedCounterRisk(),
                            best.terminalObjective(),
                            best.attackerTerminalValue(),
                            best.defenderTerminalValue(),
                            best.attackerUnitLosses(),
                            best.defenderUnitLosses(),
                            best.attackerRebuyPreserved(),
                            best.defenderRebuyPreserved(),
                            best.attackerRebuyDestroyed(),
                            best.defenderRebuyDestroyed(),
                            best.attackerInfraDestroyed(),
                            best.defenderInfraDestroyed(),
                            best.attackerWiped(),
                            best.defenderWiped(),
                            best.attackerWipeRisk(),
                            best.defenderWipeRisk(),
                            best.activeWars(),
                            best.attackerControlFlags(),
                            best.defenderControlFlags(),
                            best.attackerWinningWars(),
                            best.defenderWinningWars(),
                            best.concludedWars(),
                            best.concludedWarsByDefenderTier(),
                            best.assignedWarTypes(),
                            best.assignedAttackTypes(),
                            best.payloadBytes(),
                            bestMs,
                            avgMs);
                    System.out.println(row);
                }
            }
        }
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

    private enum Lane {
        OPENING_PRIMITIVE("openingPrimitive", List.of(BlitzObjective.NET_DAMAGE)),
        LONG_HORIZON_SCALAR("longHorizonScalar", List.of(BlitzObjective.NET_DAMAGE)),
        PROJECTED_OBJECTIVE("projectedObjective", List.of(
                BlitzObjective.NET_DAMAGE,
                BlitzObjective.DAMAGE,
                BlitzObjective.MINIMUM_DAMAGE_RECEIVED,
                BlitzObjective.CONTROL,
                BlitzObjective.BALANCED
        ));

        private final String cliName;
        private final List<BlitzObjective> objectives;

        Lane(String cliName, List<BlitzObjective> objectives) {
            this.cliName = cliName;
            this.objectives = objectives;
        }

        List<BlitzObjective> objectives() {
            return objectives;
        }
    }

    private enum TierSegment {
        LOW("low"),
        MID("mid"),
        HIGH("high");

        private final String label;

        TierSegment(String label) {
            this.label = label;
        }

        static TierSegment fromCities(int cities) {
            if (cities >= 28) {
                return HIGH;
            }
            if (cities >= 20) {
                return MID;
            }
            return LOW;
        }
    }

    private enum ScenarioFamily {
        PARITY("parity", 8),
        ATTACKER_FAVORED("attackerFavored", 8),
        DEFENDER_FAVORED("defenderFavored", 8),
        UNMILITARIZED_VS_FULL("unmilitarizedVsFull", 6),
        MIXED_STRONG_DEFENDERS("mixedStrongDefenders", 10),
        LOW_VALUE_SWARM("lowValueSwarm", 12),
        SEVERE_ATTACKER_ADVANTAGE("severeAttackerAdvantage", 8),
        SEVERE_DEFENDER_ADVANTAGE("severeDefenderAdvantage", 8),
        EXHAUSTED_VS_FULL_REBUY("exhaustedVsFullRebuy", 8),
        RESET_TIMING("resetTiming", 8),
        ACTIVE_WAR_SLOT_PRESSURE("activeWarSlotPressure", 8),
        SLOT_SATURATED_FRONT("slotSaturatedFront", 8),
        PARTIAL_RANGE_CONTROL("partialRangeControl", 10),
        UNWINNABLE_CONVENTIONAL("unwinnableConventional", 8);

        private final String cliName;
        private final int tinyPopulation;

        ScenarioFamily(String cliName, int tinyPopulation) {
            this.cliName = cliName;
            this.tinyPopulation = tinyPopulation;
        }

        Fixture fixture(int requestedPopulation) {
            int population = requestedPopulation > 0 ? requestedPopulation : tinyPopulation;
            return switch (this) {
                case PARITY -> Fixture.create(cliName, population, this::parityAttacker, this::parityDefender);
                case ATTACKER_FAVORED -> Fixture.create(cliName, population, this::favoredAttacker, this::weakDefender);
                case DEFENDER_FAVORED -> Fixture.create(cliName, population, this::weakAttacker, this::favoredDefender);
                case UNMILITARIZED_VS_FULL -> Fixture.create(cliName, population, this::unmilitarizedAttacker, this::favoredDefender);
                case MIXED_STRONG_DEFENDERS -> Fixture.create(cliName, population, this::parityAttacker, this::mixedDefender);
                case LOW_VALUE_SWARM -> Fixture.create(cliName, population, this::favoredAttacker, this::lowValueDefender);
                case SEVERE_ATTACKER_ADVANTAGE -> Fixture.create(cliName, population, this::severeAttacker, this::weakDefender);
                case SEVERE_DEFENDER_ADVANTAGE -> Fixture.create(cliName, population, this::weakAttacker, this::severeDefender);
                case EXHAUSTED_VS_FULL_REBUY -> Fixture.create(cliName, population, this::exhaustedRebuyAttacker, this::fullRebuyDefender);
                case RESET_TIMING -> Fixture.create(cliName, population, this::imminentResetAttacker, this::delayedResetDefender);
                case ACTIVE_WAR_SLOT_PRESSURE -> Fixture.create(cliName, population, this::activeWarPressedAttacker, this::activeWarPressedDefender);
                case SLOT_SATURATED_FRONT -> Fixture.create(cliName, population, this::slotSaturatedAttacker, this::slotSaturatedDefender);
                case PARTIAL_RANGE_CONTROL -> Fixture.create(cliName, population, this::partialRangeAttacker, this::partialRangeDefender);
                case UNWINNABLE_CONVENTIONAL -> Fixture.create(cliName, population, this::unwinnableConventionalAttacker, this::severeDefender);
            };
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

        private DBNationSnapshot lowValueDefender(int index) {
            double multiplier = index < 4 ? 1.1d : 0.20d;
            int cities = index < 4 ? 20 + index % 3 : 12 + index % 2;
            return nation(20_000 + index, 2, index, cities, multiplier, 1);
        }

        private DBNationSnapshot severeAttacker(int index) {
            return nation(10_000 + index, 1, index, 28 + index % 6, 2.15d, 3);
        }

        private DBNationSnapshot severeDefender(int index) {
            return nation(20_000 + index, 2, index, 31 + index % 7, 2.25d, 1);
        }

        private DBNationSnapshot exhaustedRebuyAttacker(int index) {
            DBNationSnapshot.Builder builder = nation(10_000 + index, 1, index, 22 + index % 4, 0.95d, 3)
                    .toBuilder();
            exhaustDailyBuys(builder);
            return builder.build();
        }

        private DBNationSnapshot fullRebuyDefender(int index) {
            return nation(20_000 + index, 2, index, 22 + index % 4, 0.95d, 1);
        }

        private DBNationSnapshot imminentResetAttacker(int index) {
            DBNationSnapshot.Builder builder = nation(10_000 + index, 1, index, 21 + index % 4, 0.75d, 3)
                    .toBuilder()
                    .resetHourUtc((byte) 0);
            builder.pendingBuyNextTurn(MilitaryUnit.SOLDIER, 35_000)
                    .pendingBuyNextTurn(MilitaryUnit.TANK, 2_500)
                    .pendingBuyNextTurn(MilitaryUnit.AIRCRAFT, 180)
                    .pendingBuyNextTurn(MilitaryUnit.SHIP, 28);
            return builder.build();
        }

        private DBNationSnapshot delayedResetDefender(int index) {
            DBNationSnapshot.Builder builder = nation(20_000 + index, 2, index, 21 + index % 4, 1.20d, 1)
                    .toBuilder()
                    .resetHourUtc((byte) 23);
            exhaustDailyBuys(builder);
            return builder.build();
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

        private DBNationSnapshot unwinnableConventionalAttacker(int index) {
            DBNationSnapshot.Builder builder = nation(10_000 + index, 1, index, 18 + index % 3, 0.20d, 3)
                    .toBuilder()
                    .unit(MilitaryUnit.MISSILE, 4)
                    .unit(MilitaryUnit.NUKE, 2);
            exhaustDailyBuys(builder);
            return builder.build();
        }
    }

    private interface NationFactory {
        DBNationSnapshot create(int index);
    }

    private record Fixture(
            String label,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        static Fixture create(String label, int population, NationFactory attackerFactory, NationFactory defenderFactory) {
            List<DBNationSnapshot> attackers = new ArrayList<>(population);
            List<DBNationSnapshot> defenders = new ArrayList<>(population);
            for (int index = 0; index < population; index++) {
                attackers.add(attackerFactory.create(index));
                defenders.add(defenderFactory.create(index));
            }
            CompiledScenario scenario = SCENARIO_COMPILER.compile(
                    attackers,
                    defenders,
                    OverrideSet.EMPTY,
                    TreatyProvider.NONE,
                    Map.of()
            );
            int[] attackerCaps = new int[attackers.size()];
            int[] defenderCaps = new int[defenders.size()];
            int[] attackerStrengthRanks = new int[attackers.size()];
            int[] attackerNationIds = new int[attackers.size()];
            int[] defenderNationIds = new int[defenders.size()];
            Integer[] attackerOrder = new Integer[attackers.size()];
            for (int index = 0; index < attackers.size(); index++) {
                attackerCaps[index] = OverrideSet.EMPTY.effectiveFreeOff(attackers.get(index));
                attackerNationIds[index] = scenario.attackerNationId(index);
                attackerOrder[index] = index;
            }
            Arrays.sort(attackerOrder, (lhs, rhs) -> Double.compare(
                    combatStrength(attackers.get(rhs)),
                    combatStrength(attackers.get(lhs))
            ));
            for (int rank = 0; rank < attackerOrder.length; rank++) {
                attackerStrengthRanks[attackerOrder[rank]] = rank;
            }
            for (int index = 0; index < defenders.size(); index++) {
                defenderCaps[index] = OverrideSet.EMPTY.effectiveFreeDef(defenders.get(index));
                defenderNationIds[index] = scenario.defenderNationId(index);
            }
            return new Fixture(
                    label,
                    List.copyOf(attackers),
                    List.copyOf(defenders),
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds
            );
        }

        Scorecard run(Lane lane, BlitzObjective blitzObjective, int horizonTurns) {
            StrategicObjective objective = blitzObjective.objective();
            CandidateEdgeTable edges = openingEdges(objective);
            LongHorizonAssignmentOptimizer.Result result = switch (lane) {
                case OPENING_PRIMITIVE -> new LongHorizonAssignmentOptimizer.Result(
                        primitiveAssignment(edges),
                        null
                );
                case LONG_HORIZON_SCALAR -> LongHorizonAssignmentOptimizer.solveDetailed(
                        edges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        List.of(),
                        horizonTurns,
                        null
                );
                case PROJECTED_OBJECTIVE -> LongHorizonAssignmentOptimizer.solveDetailed(
                        edges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        List.of(),
                        horizonTurns,
                        new LongHorizonAssignmentOptimizer.ProjectionScoringContext(objective)
                );
            };
            ObjectiveValueSummary summary = result.projectedObjectiveSummary() != null
                    ? result.projectedObjectiveSummary()
                    : LongHorizonAssignmentOptimizer.projectedObjectiveSummary(
                            edges,
                            scenario,
                            attackerCaps,
                            defenderCaps,
                            horizonTurns,
                            result.assignment(),
                            objective,
                            attackerNationIds,
                            defenderNationIds
                    );
            return scorecard(edges, result.assignment(), summary.mean(), horizonTurns);
        }

        private CandidateEdgeTable openingEdges(StrategicObjective objective) {
            CandidateEdgeTable edges = new CandidateEdgeTable();
            OpeningEvaluator.evaluate(
                    scenario,
                    SimTuning.defaults(),
                    OverrideSet.EMPTY,
                    objective,
                    attackerCaps.clone(),
                    defenderCaps.clone(),
                    edges
            );
            return edges;
        }

        private Map<Integer, List<Integer>> primitiveAssignment(CandidateEdgeTable edges) {
            return PrimitiveAssignmentSolver.solveAssignment(
                    edges,
                    scenario.attackerCount(),
                    scenario.defenderCount(),
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    List.of()
            );
        }

        private Scorecard scorecard(
                CandidateEdgeTable edges,
                Map<Integer, List<Integer>> assignment,
                double terminalObjective,
                int horizonTurns
        ) {
            int[] attackerCounts = new int[scenario.attackerCount()];
            int[] defenderCounts = new int[scenario.defenderCount()];
            boolean[] edgeAssigned = new boolean[edges.edgeCount()];
            int[] warTypeCounts = new int[WarType.values.length];
            int[] attackTypeCounts = new int[AttackType.values.length];
            int assignmentCount = 0;
            for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
                int attackerIndex = indexOf(attackerNationIds, entry.getKey());
                if (attackerIndex < 0) {
                    continue;
                }
                for (int defenderNationId : entry.getValue()) {
                    int defenderIndex = indexOf(defenderNationIds, defenderNationId);
                    if (defenderIndex < 0) {
                        continue;
                    }
                    attackerCounts[attackerIndex]++;
                    defenderCounts[defenderIndex]++;
                    assignmentCount++;
                    int edgeIndex = edgeIndex(edges, attackerIndex, defenderIndex);
                    if (edgeIndex >= 0) {
                        edgeAssigned[edgeIndex] = true;
                        int warTypeId = edges.preferredWarTypeId(edgeIndex);
                        if (warTypeId >= 0 && warTypeId < warTypeCounts.length) {
                            warTypeCounts[warTypeId]++;
                        }
                        int attackTypeId = edges.bestAttackTypeId(edgeIndex);
                        if (attackTypeId >= 0 && attackTypeId < attackTypeCounts.length) {
                            attackTypeCounts[attackTypeId]++;
                        }
                    }
                }
            }
            int idleViableAttackers = idleViableAttackers(edges, attackerCounts);
            int maxWarsPerAttacker = 0;
            for (int count : attackerCounts) {
                maxWarsPerAttacker = Math.max(maxWarsPerAttacker, count);
            }
            LongHorizonForwardProjection.ProjectionDiagnostics diagnostics = LongHorizonControlProjection.create(
                    edges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    horizonTurns,
                    LongHorizonAssignmentOptimizer.horizonFactor(horizonTurns)
            ).projectionDiagnostics(edgeAssigned, attackerCounts, defenderCounts);
            return new Scorecard(
                    edges.edgeCount(),
                    assignmentCount,
                    idleViableAttackers,
                    strongDefenderCoveragePct(defenderCounts),
                    defenderCoverageByTier(defenderCounts),
                    maxWarsPerAttacker,
                    avgAssignedCounterRisk(edges, assignment),
                    terminalObjective,
                    diagnostics.attackerStrategicValue(),
                    diagnostics.defenderStrategicValue(),
                    unitLossSummary(diagnostics.attackerUnitLosses()),
                    unitLossSummary(diagnostics.defenderUnitLosses()),
                    diagnostics.attackerRebuyPreservedValue(),
                    diagnostics.defenderRebuyPreservedValue(),
                    diagnostics.attackerRebuyDestroyedValue(),
                    diagnostics.defenderRebuyDestroyedValue(),
                    diagnostics.attackerInfraDestroyed(),
                    diagnostics.defenderInfraDestroyed(),
                    diagnostics.attackerWiped(),
                    diagnostics.defenderWiped(),
                    diagnostics.attackerWipeRisk(),
                    diagnostics.defenderWipeRisk(),
                    diagnostics.activeWars(),
                    diagnostics.attackerControlFlags(),
                    diagnostics.defenderControlFlags(),
                    diagnostics.attackerWinningWars(),
                    diagnostics.defenderWinningWars(),
                    diagnostics.concludedWars(),
                    tierCountSummary(diagnostics.concludedWarsByDefenderTier()),
                    enumCountSummary(WarType.values, warTypeCounts),
                    enumCountSummary(AttackType.values, attackTypeCounts),
                    payloadBytes(assignment),
                    0L
            );
        }

        private int edgeIndex(CandidateEdgeTable edges, int attackerIndex, int defenderIndex) {
            for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
                if (edges.attackerIndex(edgeIndex) == attackerIndex
                        && edges.defenderIndex(edgeIndex) == defenderIndex) {
                    return edgeIndex;
                }
            }
            return -1;
        }

        private int idleViableAttackers(CandidateEdgeTable edges, int[] attackerCounts) {
            boolean[] hasEdge = new boolean[scenario.attackerCount()];
            for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
                hasEdge[edges.attackerIndex(edgeIndex)] = true;
            }
            int idle = 0;
            for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
                if (attackerCaps[attackerIndex] > 0 && hasEdge[attackerIndex] && attackerCounts[attackerIndex] == 0) {
                    idle++;
                }
            }
            return idle;
        }

        private double strongDefenderCoveragePct(int[] defenderCounts) {
            int strongCount = Math.max(1, defenderCounts.length / 3);
            Integer[] defenderOrder = new Integer[defenders.size()];
            for (int index = 0; index < defenderOrder.length; index++) {
                defenderOrder[index] = index;
            }
            Arrays.sort(defenderOrder, (lhs, rhs) -> Double.compare(
                    combatStrength(defenders.get(rhs)),
                    combatStrength(defenders.get(lhs))
            ));
            int covered = 0;
            for (int rank = 0; rank < strongCount; rank++) {
                if (defenderCounts[defenderOrder[rank]] > 0) {
                    covered++;
                }
            }
            return 100.0d * covered / strongCount;
        }

        private String defenderCoverageByTier(int[] defenderCounts) {
            int[] totals = new int[TierSegment.values().length];
            int[] covered = new int[TierSegment.values().length];
            for (int defenderIndex = 0; defenderIndex < defenders.size(); defenderIndex++) {
                int tierIndex = TierSegment.fromCities(defenders.get(defenderIndex).cities()).ordinal();
                totals[tierIndex]++;
                if (defenderCounts[defenderIndex] > 0) {
                    covered[tierIndex]++;
                }
            }
            StringBuilder builder = new StringBuilder();
            for (TierSegment tier : TierSegment.values()) {
                if (builder.length() > 0) {
                    builder.append(';');
                }
                int tierIndex = tier.ordinal();
                builder.append(tier.label)
                        .append(':')
                        .append(covered[tierIndex])
                        .append('/')
                        .append(totals[tierIndex]);
            }
            return builder.toString();
        }

        private double avgAssignedCounterRisk(CandidateEdgeTable edges, Map<Integer, List<Integer>> assignment) {
            int assignedEdges = 0;
            double risk = 0d;
            for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
                int attackerId = attackerNationIds[edges.attackerIndex(edgeIndex)];
                int defenderId = defenderNationIds[edges.defenderIndex(edgeIndex)];
                if (assignment.getOrDefault(attackerId, List.of()).contains(defenderId)) {
                    assignedEdges++;
                    risk += edges.counterRisk(edgeIndex);
                }
            }
            return assignedEdges == 0 ? 0d : risk / assignedEdges;
        }

        private int payloadBytes(Map<Integer, List<Integer>> assignment) {
            int bytes = 2;
            for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
                bytes += Integer.toString(entry.getKey()).length() + 3;
                for (int defenderId : entry.getValue()) {
                    bytes += Integer.toString(defenderId).length() + 1;
                }
            }
            return bytes;
        }
    }

    private record Scorecard(
            int edgeCount,
            int assignmentCount,
            int idleViableAttackers,
            double strongDefenderCoveragePct,
            String defenderCoverageByTier,
            int maxWarsPerAttacker,
            double avgAssignedCounterRisk,
            double terminalObjective,
            double attackerTerminalValue,
            double defenderTerminalValue,
            String attackerUnitLosses,
            String defenderUnitLosses,
            double attackerRebuyPreserved,
            double defenderRebuyPreserved,
            double attackerRebuyDestroyed,
            double defenderRebuyDestroyed,
            double attackerInfraDestroyed,
            double defenderInfraDestroyed,
            int attackerWiped,
            int defenderWiped,
            int attackerWipeRisk,
            int defenderWipeRisk,
            int activeWars,
            int attackerControlFlags,
            int defenderControlFlags,
            int attackerWinningWars,
            int defenderWinningWars,
            int concludedWars,
            String concludedWarsByDefenderTier,
            String assignedWarTypes,
            String assignedAttackTypes,
            int payloadBytes,
            long elapsedNanos
    ) {
        Scorecard withElapsedNanos(long newElapsedNanos) {
            return new Scorecard(
                    edgeCount,
                    assignmentCount,
                    idleViableAttackers,
                    strongDefenderCoveragePct,
                    defenderCoverageByTier,
                    maxWarsPerAttacker,
                    avgAssignedCounterRisk,
                    terminalObjective,
                    attackerTerminalValue,
                    defenderTerminalValue,
                    attackerUnitLosses,
                    defenderUnitLosses,
                    attackerRebuyPreserved,
                    defenderRebuyPreserved,
                    attackerRebuyDestroyed,
                    defenderRebuyDestroyed,
                    attackerInfraDestroyed,
                    defenderInfraDestroyed,
                    attackerWiped,
                    defenderWiped,
                    attackerWipeRisk,
                    defenderWipeRisk,
                    activeWars,
                    attackerControlFlags,
                    defenderControlFlags,
                    attackerWinningWars,
                    defenderWinningWars,
                    concludedWars,
                    concludedWarsByDefenderTier,
                    assignedWarTypes,
                    assignedAttackTypes,
                    payloadBytes,
                    newElapsedNanos
            );
        }
    }

    private static String unitLossSummary(int[] losses) {
        StringBuilder builder = new StringBuilder();
        for (int unitIndex = 0; unitIndex < SimUnits.PURCHASABLE_UNITS.length; unitIndex++) {
            if (unitIndex > 0) {
                builder.append(';');
            }
            builder.append(SimUnits.PURCHASABLE_UNITS[unitIndex].name()).append(':').append(losses[unitIndex]);
        }
        return builder.toString();
    }

    private static <E extends Enum<E>> String enumCountSummary(E[] values, int[] counts) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (counts[index] <= 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(values[index].name()).append(':').append(counts[index]);
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static String tierCountSummary(int[] counts) {
        StringBuilder builder = new StringBuilder();
        for (TierSegment tier : TierSegment.values()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            int tierIndex = tier.ordinal();
            int count = tierIndex < counts.length ? counts[tierIndex] : 0;
            builder.append(tier.label).append(':').append(count);
        }
        return builder.toString();
    }

    private static void exhaustDailyBuys(DBNationSnapshot.Builder builder) {
        builder.unitBoughtToday(MilitaryUnit.SOLDIER, 250_000)
                .unitBoughtToday(MilitaryUnit.TANK, 25_000)
                .unitBoughtToday(MilitaryUnit.AIRCRAFT, 2_000)
                .unitBoughtToday(MilitaryUnit.SHIP, 400);
    }

    private static DBNationSnapshot nation(
            int nationId,
            int teamId,
            int offset,
            int cities,
            double militaryMultiplier,
            int freeOffSlots
    ) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .score(900.0d + cities * 45.0d + offset)
                .cities(cities)
                .nonInfraScoreBase(400.0d + cities * 35.0d)
                .cityInfra(uniformInfra(cities, 1_800.0d + (offset % 4) * 150.0d))
                .maxOff(freeOffSlots)
                .unit(MilitaryUnit.SOLDIER, scaled(250_000 + offset * 2_000, militaryMultiplier))
                .unit(MilitaryUnit.TANK, scaled(20_000 + offset * 150, militaryMultiplier))
                .unit(MilitaryUnit.AIRCRAFT, scaled(1_600 + offset * 20, militaryMultiplier))
                .unit(MilitaryUnit.SHIP, scaled(250 + offset * 4, militaryMultiplier))
                .unit(MilitaryUnit.MISSILE, militaryMultiplier < 0.30d ? 3 : 0)
                .unit(MilitaryUnit.NUKE, militaryMultiplier < 0.30d ? 1 : 0)
                .resource(link.locutus.discord.apiv1.enums.ResourceType.MONEY, 100_000_000d)
                .resource(link.locutus.discord.apiv1.enums.ResourceType.FOOD, 10_000_000d)
                .resource(link.locutus.discord.apiv1.enums.ResourceType.GASOLINE, 2_000_000d)
                .resource(link.locutus.discord.apiv1.enums.ResourceType.MUNITIONS, 2_000_000d)
                .resource(link.locutus.discord.apiv1.enums.ResourceType.STEEL, 2_000_000d)
                .resource(link.locutus.discord.apiv1.enums.ResourceType.ALUMINUM, 2_000_000d)
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

    private static int indexOf(int[] values, int target) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == target) {
                return index;
            }
        }
        return -1;
    }

    private static double combatStrength(DBNationSnapshot snapshot) {
        return snapshot.unit(MilitaryUnit.SOLDIER)
                + (40.0d * snapshot.unit(MilitaryUnit.TANK))
                + (120.0d * snapshot.unit(MilitaryUnit.AIRCRAFT))
                + (600.0d * snapshot.unit(MilitaryUnit.SHIP));
    }
}
