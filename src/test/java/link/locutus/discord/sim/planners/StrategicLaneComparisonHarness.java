package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.planners.compile.CompiledActiveWar;
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
    private static final String CSV_HEADER = "family,lane,objective,horizon,attackers,defenders,edges,assignments,idleAttackersWithEdges,idleAttackersFreeSlot,idleAttackersFreeSlotPct,strongDefenderCoveragePct,defenderCoverageByTier,maxWarsPerAttacker,avgAssignedCounterRisk,terminalObjective,attackerTerminalValue,defenderTerminalValue,attackerUnitLosses,defenderUnitLosses,attackerUnitLossValue,defenderUnitLossValue,attackerLandAirLossValue,defenderLandAirLossValue,attackerRebuyPreserved,defenderRebuyPreserved,attackerRebuyDestroyed,defenderRebuyDestroyed,attackerInfraDestroyed,defenderInfraDestroyed,attackerWiped,defenderWiped,attackerWipeRisk,defenderWipeRisk,activeWars,attackerSuperiorityFlags,defenderSuperiorityFlags,superiorityBalancePct,attackerWinningWars,defenderWinningWars,turnsAtkControl,turnsDefControl,turnsNoControl,currentWarOutcomeFlips,concludedWars,concludedWarsByDefenderTier,assignedWarTypes,assignedAttackTypes,payloadBytes,bestMs,avgMs";

    private StrategicLaneComparisonHarness() {
    }

    public static void main(String[] args) {
        int horizonTurns = optionInt(args, "horizon", DEFAULT_HORIZON_TURNS);
        int repetitions = Math.max(1, optionInt(args, "repetitions", 1));
        int requestedPopulation = optionInt(args, "population", DEFAULT_POPULATION);
        System.out.print(renderCsv(horizonTurns, repetitions, requestedPopulation, ProjectionPolicyPath.DEFAULT));
    }

    static String renderCsv(int horizonTurns, int repetitions, int requestedPopulation, ProjectionPolicyPath projectionPolicyPath) {
        StringBuilder out = new StringBuilder();
        out.append(CSV_HEADER).append(System.lineSeparator());
        for (ScenarioFamily family : ScenarioFamily.values()) {
            Fixture fixture = family.fixture(requestedPopulation);
            for (Lane lane : Lane.values()) {
                for (BlitzObjective objective : lane.objectives()) {
                    Scorecard best = null;
                    long totalNanos = 0L;
                    for (int repetition = 0; repetition < repetitions; repetition++) {
                        long startNanos = System.nanoTime();
                        Scorecard scorecard = fixture.run(lane, objective, horizonTurns, projectionPolicyPath);
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
                            family.cliName,
                            lane.cliName,
                            objective.name(),
                            Integer.toString(horizonTurns),
                            Integer.toString(fixture.scenario.attackerCount()),
                            Integer.toString(fixture.scenario.defenderCount()),
                            Integer.toString(best.edgeCount()),
                            Integer.toString(best.assignmentCount()),
                            Integer.toString(best.idleAttackersWithEdges()),
                            Integer.toString(best.idleAttackersFreeSlot()),
                            formatDouble(best.idleAttackersFreeSlotPct(), 2),
                            formatDouble(best.strongDefenderCoveragePct(), 3),
                            tierCoverageSummary(best.defenderCoverageByTierCovered(), best.defenderCoverageByTierTotal()),
                            Integer.toString(best.maxWarsPerAttacker()),
                            formatDouble(best.avgAssignedCounterRisk(), 6),
                            formatDouble(best.terminalObjective(), 3),
                            formatDouble(best.attackerTerminalValue(), 3),
                            formatDouble(best.defenderTerminalValue(), 3),
                            unitLossSummary(best.attackerUnitLosses()),
                            unitLossSummary(best.defenderUnitLosses()),
                            formatDouble(best.attackerUnitLossValue(), 3),
                            formatDouble(best.defenderUnitLossValue(), 3),
                            formatDouble(best.attackerLandAirLossValue(), 3),
                            formatDouble(best.defenderLandAirLossValue(), 3),
                            formatDouble(best.attackerRebuyPreserved(), 3),
                            formatDouble(best.defenderRebuyPreserved(), 3),
                            formatDouble(best.attackerRebuyDestroyed(), 3),
                            formatDouble(best.defenderRebuyDestroyed(), 3),
                            formatDouble(best.attackerInfraDestroyed(), 3),
                            formatDouble(best.defenderInfraDestroyed(), 3),
                            Integer.toString(best.attackerWiped()),
                            Integer.toString(best.defenderWiped()),
                            Integer.toString(best.attackerWipeRisk()),
                            Integer.toString(best.defenderWipeRisk()),
                            Integer.toString(best.activeWars()),
                            Integer.toString(best.attackerSuperiorityFlags()),
                            Integer.toString(best.defenderSuperiorityFlags()),
                            formatDouble(best.superiorityBalancePct(), 1),
                            Integer.toString(best.attackerWinningWars()),
                            Integer.toString(best.defenderWinningWars()),
                            Integer.toString(best.turnsAttackerHeldNetControl()),
                            Integer.toString(best.turnsDefenderHeldNetControl()),
                            Integer.toString(best.turnsNoControl()),
                            Integer.toString(best.currentWarOutcomeFlips()),
                            Integer.toString(best.concludedWars()),
                            tierCountSummary(best.concludedWarsByDefenderTier()),
                            enumCountSummary(WarType.values, best.assignedWarTypeCounts()),
                            enumCountSummary(AttackType.values, best.assignedAttackTypeCounts()),
                            Integer.toString(best.payloadBytes()),
                            formatDouble(bestMs, 3),
                            formatDouble(avgMs, 3)
                    )).append(System.lineSeparator());
                }
            }
        }
        return out.toString();
    }

    enum ProjectionPolicyPath {
        DEFAULT,
        EXPLICIT_LEGACY
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
        ACTIVE_WAR_BARELY_WINNING("activeWarBarelyWinning", 8),
        ACTIVE_WAR_DECISIVE("activeWarDecisive", 8),
        SLOT_SATURATED_FRONT("slotSaturatedFront", 8),
        PARTIAL_RANGE_CONTROL("partialRangeControl", 10),
        UNWINNABLE_CONVENTIONAL("unwinnableConventional", 8),
        TIER_SPREAD_PARITY("tierSpreadParity", 10),
        DEMILITARIZED_BREAKOUT("demilitarizedBreakout", 8),
        CONTROL_UNATTAINABLE("controlUnattainable", 6),
        ALLY_SLOT_CONFLICT("allySlotConflict", 8);

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
                case ACTIVE_WAR_BARELY_WINNING -> Fixture.createSeededActiveWars(cliName, population,
                        this::barelyWinningActiveWarAttacker, this::barelyWinningActiveWarDefender, false);
                case ACTIVE_WAR_DECISIVE -> Fixture.createSeededActiveWars(cliName, population,
                        this::decisiveActiveWarAttacker, this::decisiveActiveWarDefender, true);
                case SLOT_SATURATED_FRONT -> Fixture.create(cliName, population, this::slotSaturatedAttacker, this::slotSaturatedDefender);
                case PARTIAL_RANGE_CONTROL -> Fixture.create(cliName, population, this::partialRangeAttacker, this::partialRangeDefender);
                case UNWINNABLE_CONVENTIONAL -> Fixture.create(cliName, population, this::unwinnableConventionalAttacker, this::severeDefender);
                case TIER_SPREAD_PARITY -> Fixture.create(cliName, population, this::tierSpreadAttacker, this::tierSpreadDefender);
                case DEMILITARIZED_BREAKOUT -> Fixture.create(cliName, population, this::demilitarizedBreakoutAttacker, this::parityDefender);
                case CONTROL_UNATTAINABLE -> Fixture.create(cliName, population, this::controlUnattainableAttacker, this::controlUnattainableDefender);
                case ALLY_SLOT_CONFLICT -> Fixture.create(cliName, population, this::allySlotConflictAttacker, this::allySlotConflictDefender);
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
            return exhaustDailyBuys(nation(10_000 + index, 1, index, 22 + index % 4, 0.95d, 3));
        }

        private DBNationSnapshot fullRebuyDefender(int index) {
            return nation(20_000 + index, 2, index, 22 + index % 4, 0.95d, 1);
        }

        private DBNationSnapshot imminentResetAttacker(int index) {
            DBNationSnapshot snapshot = nation(10_000 + index, 1, index, 21 + index % 4, 0.75d, 3);
            DBNationSnapshot.Builder builder = snapshot
                    .toBuilder()
                    .resetHourUtc((byte) 0);
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

        private static DBNationSnapshot activeWarParticipant(DBNationSnapshot source, int opponentNationId, boolean offensive) {
            DBNationSnapshot.Builder builder = source.toBuilder()
                    .activeOpponentNationId(opponentNationId);
            if (offensive) {
                builder.currentOffensiveWars(1);
            } else {
                builder.currentDefensiveWars(1);
            }
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
            return exhaustDailyBuys(builder.build());
        }

        /**
         * Realistic city-tier spread: 4 LOW (c13-c19), 4 MID (c21-c26), 2 HIGH (c30-c33).
         * Both sides at full military. Tests whether HIGH-tier nations are correctly prioritised
         * over MID/LOW-tier ones during assignment.
         */
        private DBNationSnapshot tierSpreadAttacker(int index) {
            int cities = switch (index % 10) {
                case 0 -> 13; case 1 -> 16; case 2 -> 18; case 3 -> 19; case 4 -> 21;
                case 5 -> 22; case 6 -> 24; case 7 -> 26; case 8 -> 30; default -> 33;
            };
            return nation(10_000 + index, 1, index, cities, 1.0d, 3);
        }

        private DBNationSnapshot tierSpreadDefender(int index) {
            int cities = switch (index % 10) {
                case 0 -> 13; case 1 -> 16; case 2 -> 18; case 3 -> 19; case 4 -> 21;
                case 5 -> 22; case 6 -> 24; case 7 -> 26; case 8 -> 30; default -> 33;
            };
            return nation(20_000 + index, 2, index, cities, 1.0d, 1);
        }

        /**
         * 2 fully-militarized attackers (c22-c23, 1.05x) + 6 demilitarized attackers (c20-c22, 0.08x)
         * vs 8 parity defenders (c20-c23, 1.0x). Correct behavior: only the 2 militarized attackers
         * should be assigned; demil attackers should remain idle.
         */
        private DBNationSnapshot demilitarizedBreakoutAttacker(int index) {
            if (index < 2) {
                return nation(10_000 + index, 1, index, 22 + index, 1.05d, 3);
            }
            return nation(10_000 + index, 1, index, 20 + index % 3, 0.08d, 3);
        }

        /**
         * Weak attackers (c17-c19, 0.45x) vs strong high-city defenders (c28-c31, 1.60x).
         * Control is unattainable at the group level. Tests that the planner suppresses
         * control-value chasing and avoids committing to pointlessly costly declarations.
         * multiplier >= 0.30 so no missiles/nukes; purely conventional.
         */
        private DBNationSnapshot controlUnattainableAttacker(int index) {
            return nation(10_000 + index, 1, index, 17 + index % 3, 0.45d, 3);
        }

        private DBNationSnapshot controlUnattainableDefender(int index) {
            return nation(20_000 + index, 2, index, 28 + index % 4, 1.60d, 1);
        }

        /**
         * 4 "busy" attackers already committed to 2 external wars (1 free slot each) +
         * 4 fully-free attackers (3 free slots). Defenders: 3 strong (c28-c30, 1.80x) +
         * 5 weak (c14-c17, 0.40x). Tests that free-slot capacity goes to high-value
         * targets and busy attackers' 1 remaining slot is not wasted on weak defenders.
         */
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
            return create(label, population, attackerFactory, defenderFactory, List.of());
        }

        static Fixture createSeededActiveWars(
                String label,
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
                CompiledActiveWar.ControlOwner favoredOwner = attackerInitiallyWinning
                        ? CompiledActiveWar.ControlOwner.ATTACKER
                        : CompiledActiveWar.ControlOwner.DEFENDER;
                CompiledActiveWar.ControlOwner otherOwner = attackerInitiallyWinning
                        ? CompiledActiveWar.ControlOwner.DEFENDER
                        : CompiledActiveWar.ControlOwner.ATTACKER;
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
                        decisive ? favoredOwner : CompiledActiveWar.ControlOwner.NONE,
                        decisive ? favoredOwner : otherOwner,
                        false,
                        false
                ));
            }
            return create(label, population, attackerFactory, defenderFactory, activeWars);
        }

        private static Fixture create(
                String label,
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
            CompiledScenario scenario = SCENARIO_COMPILER.compile(
                    attackers,
                    defenders,
                    OverrideSet.EMPTY,
                    TreatyProvider.NONE,
                    Map.of(),
                    activeWars
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

        Scorecard run(Lane lane, BlitzObjective blitzObjective, int horizonTurns, ProjectionPolicyPath projectionPolicyPath) {
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
                        projectionContext(objective, projectionPolicyPath)
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
                                projectionContext(objective, projectionPolicyPath),
                            attackerNationIds,
                            defenderNationIds
                    );
                        return scorecard(edges, result.assignment(), summary.mean(), horizonTurns, projectionPolicyPath);
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
            int horizonTurns,
            ProjectionPolicyPath projectionPolicyPath
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
            int idleAttackersWithEdges = idleAttackersWithEdges(edges, attackerCounts);
            int idleAttackersFreeSlot = idleAttackersFreeSlot(attackerCounts);
            double idleAttackersFreeSlotPct = idleAttackersFreeSlotPct(attackerCounts);
            TierCoverage defenderTierCoverage = defenderCoverageByTier(defenderCounts);
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
                    LongHorizonAssignmentOptimizer.horizonFactor(horizonTurns),
                    false,
                    projectionContext(BlitzObjective.NET_DAMAGE.objective(), projectionPolicyPath).attackerProjectionPolicies(),
                    projectionContext(BlitzObjective.NET_DAMAGE.objective(), projectionPolicyPath).defenderProjectionPolicies()
            ).projectionDiagnostics(edgeAssigned, attackerCounts, defenderCounts);
            return new Scorecard(
                    edges.edgeCount(),
                    assignmentCount,
                    idleAttackersWithEdges,
                    idleAttackersFreeSlot,
                    idleAttackersFreeSlotPct,
                    strongDefenderCoveragePct(defenderCounts),
                    defenderTierCoverage.covered(),
                    defenderTierCoverage.totals(),
                    maxWarsPerAttacker,
                    avgAssignedCounterRisk(edges, assignment),
                    terminalObjective,
                    diagnostics.attackerStrategicValue(),
                    diagnostics.defenderStrategicValue(),
                    diagnostics.attackerUnitLosses(),
                    diagnostics.defenderUnitLosses(),
                    unitLossValue(diagnostics.attackerUnitLosses(), true),
                    unitLossValue(diagnostics.defenderUnitLosses(), false),
                    landAirLossValue(diagnostics.attackerUnitLosses(), true),
                    landAirLossValue(diagnostics.defenderUnitLosses(), false),
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
                    diagnostics.attackerSuperiorityFlags(),
                    diagnostics.defenderSuperiorityFlags(),
                    superiorityBalancePct(diagnostics.attackerSuperiorityFlags(), diagnostics.defenderSuperiorityFlags()),
                    diagnostics.attackerWinningWars(),
                    diagnostics.defenderWinningWars(),
                    diagnostics.turnsAttackerHeldNetControl(),
                    diagnostics.turnsDefenderHeldNetControl(),
                    diagnostics.turnsNoControl(),
                    diagnostics.currentWarOutcomeFlips(),
                    diagnostics.concludedWars(),
                    diagnostics.concludedWarsByDefenderTier(),
                    warTypeCounts,
                    attackTypeCounts,
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

        private int idleAttackersWithEdges(CandidateEdgeTable edges, int[] attackerCounts) {
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

        private int idleAttackersFreeSlot(int[] attackerCounts) {
            int idle = 0;
            for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
                if (attackerCaps[attackerIndex] > 0 && attackerCounts[attackerIndex] == 0) {
                    idle++;
                }
            }
            return idle;
        }

        private double idleAttackersFreeSlotPct(int[] attackerCounts) {
            int freeSlotAttackers = 0;
            int idleFreeSlotAttackers = 0;
            for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
                if (attackerCaps[attackerIndex] <= 0) {
                    continue;
                }
                freeSlotAttackers++;
                if (attackerCounts[attackerIndex] == 0) {
                    idleFreeSlotAttackers++;
                }
            }
            return freeSlotAttackers == 0 ? 0d : 100.0d * idleFreeSlotAttackers / freeSlotAttackers;
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

        private TierCoverage defenderCoverageByTier(int[] defenderCounts) {
            int[] totals = new int[TierSegment.values().length];
            int[] covered = new int[TierSegment.values().length];
            for (int defenderIndex = 0; defenderIndex < defenders.size(); defenderIndex++) {
                int tierIndex = TierSegment.fromCities(defenders.get(defenderIndex).cities()).ordinal();
                totals[tierIndex]++;
                if (defenderCounts[defenderIndex] > 0) {
                    covered[tierIndex]++;
                }
            }
            return new TierCoverage(covered, totals);
        }

        private double unitLossValue(int[] losses, boolean attackerSide) {
            int researchBits = attackerSide
                    ? (attackers.isEmpty() ? 0 : attackers.get(0).researchBits())
                    : (defenders.isEmpty() ? 0 : defenders.get(0).researchBits());
            double value = 0d;
            for (int unitIndex = 0; unitIndex < SimUnits.PURCHASABLE_UNITS.length; unitIndex++) {
                MilitaryUnit unit = SimUnits.PURCHASABLE_UNITS[unitIndex];
                value += losses[unitIndex] * unit.getConvertedCost(researchBits);
            }
            return value;
        }

        private double landAirLossValue(int[] losses, boolean attackerSide) {
            int researchBits = attackerSide
                ? (attackers.isEmpty() ? 0 : attackers.get(0).researchBits())
                : (defenders.isEmpty() ? 0 : defenders.get(0).researchBits());
            double value = 0d;
            for (int unitIndex = 0; unitIndex < SimUnits.PURCHASABLE_UNITS.length; unitIndex++) {
                MilitaryUnit unit = SimUnits.PURCHASABLE_UNITS[unitIndex];
                if (unit != MilitaryUnit.SOLDIER && unit != MilitaryUnit.TANK && unit != MilitaryUnit.AIRCRAFT) {
                    continue;
                }
                value += losses[unitIndex] * unit.getConvertedCost(researchBits);
            }
            return value;
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

        private LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext(
                StrategicObjective objective,
                ProjectionPolicyPath projectionPolicyPath
        ) {
            return switch (projectionPolicyPath) {
            case DEFAULT -> LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(objective);
                case EXPLICIT_LEGACY -> new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        objective,
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic(),
                        SidePlannerSettings.DEFAULT_PROJECTED_AUDIT_LIMIT
                );
            };
        }
    }

        private record TierCoverage(int[] covered, int[] totals) {
        }

    private record Scorecard(
            int edgeCount,
            int assignmentCount,
            int idleAttackersWithEdges,
            int idleAttackersFreeSlot,
            double idleAttackersFreeSlotPct,
            double strongDefenderCoveragePct,
            int[] defenderCoverageByTierCovered,
            int[] defenderCoverageByTierTotal,
            int maxWarsPerAttacker,
            double avgAssignedCounterRisk,
            double terminalObjective,
            double attackerTerminalValue,
            double defenderTerminalValue,
            int[] attackerUnitLosses,
            int[] defenderUnitLosses,
            double attackerUnitLossValue,
            double defenderUnitLossValue,
            double attackerLandAirLossValue,
            double defenderLandAirLossValue,
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
            int attackerSuperiorityFlags,
            int defenderSuperiorityFlags,
            double superiorityBalancePct,
            int attackerWinningWars,
            int defenderWinningWars,
            int turnsAttackerHeldNetControl,
            int turnsDefenderHeldNetControl,
            int turnsNoControl,
            int currentWarOutcomeFlips,
            int concludedWars,
                int[] concludedWarsByDefenderTier,
                int[] assignedWarTypeCounts,
                int[] assignedAttackTypeCounts,
            int payloadBytes,
            long elapsedNanos
    ) {
        Scorecard withElapsedNanos(long newElapsedNanos) {
            return new Scorecard(
                    edgeCount,
                    assignmentCount,
                    idleAttackersWithEdges,
                    idleAttackersFreeSlot,
                    idleAttackersFreeSlotPct,
                    strongDefenderCoveragePct,
                    defenderCoverageByTierCovered,
                    defenderCoverageByTierTotal,
                    maxWarsPerAttacker,
                    avgAssignedCounterRisk,
                    terminalObjective,
                    attackerTerminalValue,
                    defenderTerminalValue,
                    attackerUnitLosses,
                    defenderUnitLosses,
                    attackerUnitLossValue,
                    defenderUnitLossValue,
                    attackerLandAirLossValue,
                    defenderLandAirLossValue,
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
                    attackerSuperiorityFlags,
                    defenderSuperiorityFlags,
                    superiorityBalancePct,
                    attackerWinningWars,
                    defenderWinningWars,
                    turnsAttackerHeldNetControl,
                    turnsDefenderHeldNetControl,
                    turnsNoControl,
                    currentWarOutcomeFlips,
                    concludedWars,
                    concludedWarsByDefenderTier,
                    assignedWarTypeCounts,
                    assignedAttackTypeCounts,
                    payloadBytes,
                    newElapsedNanos
            );
        }
    }

    private static String tierCoverageSummary(int[] covered, int[] totals) {
        StringBuilder builder = new StringBuilder();
        for (TierSegment tier : TierSegment.values()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            int tierIndex = tier.ordinal();
            int c = tierIndex < covered.length ? covered[tierIndex] : 0;
            int t = tierIndex < totals.length ? totals[tierIndex] : 0;
            builder.append(tier.label)
                    .append(':')
                    .append(c)
                    .append('/')
                    .append(t);
        }
        return builder.toString();
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

    private static DBNationSnapshot exhaustDailyBuys(DBNationSnapshot snapshot) {
        DBNationSnapshot.Builder builder = snapshot.toBuilder();
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
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
                .resource(ResourceType.MONEY, 8_000_000d + cities * 450_000d)
                .resource(ResourceType.FOOD, 200_000d + cities * 15_000d)
                .resource(ResourceType.GASOLINE, 80_000d + cities * 8_000d)
                .resource(ResourceType.MUNITIONS, 80_000d + cities * 8_000d)
                .resource(ResourceType.STEEL, 80_000d + cities * 8_000d)
                .resource(ResourceType.ALUMINUM, 80_000d + cities * 8_000d)
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

    private static double superiorityBalancePct(int attackerSuperiorityFlags, int defenderSuperiorityFlags) {
        int total = attackerSuperiorityFlags + defenderSuperiorityFlags;
        return total == 0 ? 50.0d : 100.0d * attackerSuperiorityFlags / total;
    }

}
