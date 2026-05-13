package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.StrategicObjective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Symmetric A/B strategic scorecard over the synthetic scenario families.
 *
 * <pre>
 * .\gradlew.bat runTestMain -PmainClass=link.locutus.discord.sim.planners.HeadToHeadComparisonHarness --no-daemon --console=plain "-PappArgs=--policyA=legacy:projectedObjective:NET_DAMAGE --policyB=control:projectedObjective:CONTROL --scenarios=parity,mixedStrongDefenders"
 * </pre>
 */
public final class HeadToHeadComparisonHarness {
    private static final int DEFAULT_HORIZON_TURNS = 72;
    private static final int DEFAULT_POPULATION = 0;
    private static final String CSV_HEADER = "family,horizon,attackerCount,defenderCount,"
            + "attackerPolicyName,defenderPolicyName,pass,"
            + "attackerObjective,defenderObjective,attackerLane,defenderLane,"
            + "attackerAssignmentCount,attackerIdleViable,attackerIdleViablePct,"
            + "attackerStrongDefenderCoveragePct,attackerDefenderCoverageByTier,"
            + "attackerMaxWarsPerNation,attackersAtCap,attackersAtTwoWars,attackerCapSaturationPct,attackerWarCountHistogram,respondingSideLaterDeclarationCapPressurePct,attackerAvgAssignedCounterRisk,"
            + "attackerTerminalObjective,attackerTerminalAssetValue,defenderTerminalAssetValue,"
            + "attackerUnitLossValue,defenderUnitLossValue,attackerLandAirLossValue,defenderLandAirLossValue,"
            + "attackerInfraDestroyed,defenderInfraDestroyed,attackerWiped,defenderWiped,"
            + "attackerWipeRisk,defenderWipeRisk,activeWars,attackerWinningWars,defenderWinningWars,"
            + "turnsAtkControl,turnsDefControl,turnsNoControl,currentWarOutcomeFlips,"
            + "concludedWars,respondingSideLaterDeclarations,openingSideLaterDeclarations,respondingSideLaterDeclarationsThrottled,"
            + "assignedWarTypes,assignedAttackTypes,bestMs,avgMs";

    private HeadToHeadComparisonHarness() {
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
        for (StrategicLaneComparisonHarness.ScenarioFamily family : selectedScenarios(scenarioFilter)) {
            StrategicLaneComparisonHarness.Fixture fixture = family.fixture(requestedPopulation);
            appendPass(out, family, fixture, policyA, policyB, "AvsB", horizonTurns, repetitions);
            appendPass(out, family, fixture.reversed(family.name() + "Reversed"), policyB, policyA, "BvsA",
                    horizonTurns, repetitions);
        }
        return out.toString();
    }

    private static void appendPass(
            StringBuilder out,
            StrategicLaneComparisonHarness.ScenarioFamily family,
            StrategicLaneComparisonHarness.Fixture fixture,
            PolicySpec attackerSpec,
            PolicySpec defenderSpec,
            String pass,
            int horizonTurns,
            int repetitions
    ) {
        StrategicLaneComparisonHarness.Scorecard best = null;
        long totalNanos = 0L;
        for (int repetition = 0; repetition < repetitions; repetition++) {
            long startNanos = System.nanoTime();
            StrategicLaneComparisonHarness.Scorecard scorecard = runFixture(fixture, attackerSpec, defenderSpec, horizonTurns);
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
                familyName(family),
                Integer.toString(horizonTurns),
                Integer.toString(fixture.scenario().attackerCount()),
                Integer.toString(fixture.scenario().defenderCount()),
                attackerSpec.name(),
                defenderSpec.name(),
                pass,
                attackerSpec.objective().name(),
                defenderSpec.objective().name(),
                attackerSpec.lane().name(),
                defenderSpec.lane().name(),
                Integer.toString(best.assignmentCount()),
                Integer.toString(best.idleAttackersFreeSlot()),
                formatDouble(best.idleAttackersFreeSlotPct(), 2),
                formatDouble(best.strongDefenderCoveragePct(), 3),
                tierCoverageSummary(best.defenderCoverageByTierCovered(), best.defenderCoverageByTierTotal()),
                Integer.toString(best.maxWarsPerAttacker()),
                Integer.toString(best.attackersAtCap()),
                Integer.toString(best.attackersAtTwoWars()),
                formatDouble(best.attackerCapSaturationPct(), 2),
                warCountHistogramSummary(best.attackerWarCountHistogram()),
                formatDouble(best.respondingSideLaterDeclarationCapPressurePct(), 2),
                formatDouble(best.avgAssignedCounterRisk(), 6),
                formatDouble(best.terminalObjective(), 3),
                formatDouble(best.attackerTerminalValue(), 3),
                formatDouble(best.defenderTerminalValue(), 3),
                formatDouble(best.attackerUnitLossValue(), 3),
                formatDouble(best.defenderUnitLossValue(), 3),
                formatDouble(best.attackerLandAirLossValue(), 3),
                formatDouble(best.defenderLandAirLossValue(), 3),
                formatDouble(best.attackerInfraDestroyed(), 3),
                formatDouble(best.defenderInfraDestroyed(), 3),
                Integer.toString(best.attackerWiped()),
                Integer.toString(best.defenderWiped()),
                Integer.toString(best.attackerWipeRisk()),
                Integer.toString(best.defenderWipeRisk()),
                Integer.toString(best.activeWars()),
                Integer.toString(best.attackerWinningWars()),
                Integer.toString(best.defenderWinningWars()),
                Integer.toString(best.turnsAttackerHeldNetControl()),
                Integer.toString(best.turnsDefenderHeldNetControl()),
                Integer.toString(best.turnsNoControl()),
                Integer.toString(best.currentWarOutcomeFlips()),
                Integer.toString(best.concludedWars()),
                Integer.toString(best.respondingSideLaterDeclarations()),
                Integer.toString(best.openingSideLaterDeclarations()),
                Integer.toString(best.respondingSideLaterDeclarationsThrottled()),
                enumCountSummary(link.locutus.discord.apiv1.enums.WarType.values, best.assignedWarTypeCounts()),
                enumCountSummary(link.locutus.discord.apiv1.enums.AttackType.values, best.assignedAttackTypeCounts()),
                formatDouble(bestMs, 3),
                formatDouble(avgMs, 3)
        )).append(System.lineSeparator());
    }

    private static StrategicLaneComparisonHarness.Scorecard runFixture(
            StrategicLaneComparisonHarness.Fixture fixture,
            PolicySpec attackerSpec,
            PolicySpec defenderSpec,
            int horizonTurns
    ) {
        SidePolicy attackerPolicy = attackerSpec.actingPolicy();
        SidePolicy defenderPolicy = defenderSpec.defendingPolicy();
        LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext =
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.fromSidePolicies(
                        attackerPolicy.objective(),
                        attackerPolicy,
                        defenderPolicy
                );
        return fixture.run(attackerSpec.lane(), attackerPolicy.objective(), projectionContext, horizonTurns);
    }

    record PolicySpec(
            String name,
            StrategicLaneComparisonHarness.Lane lane,
            BlitzObjective objective,
            int projectedAuditLimit,
            int maxLaterDeclarationsPerTurn,
            double[] warTypeWeights,
            double[] attackTypeWeights,
            Double minimumViabilityProbe,
            Boolean allowLegalSpecialistFallback,
            Boolean admitPositiveOpeningBaseline,
            boolean objectiveDrivenAttackChoice
    ) {
        static PolicySpec parse(String value, String fallbackName) {
            String effective = value == null || value.isBlank()
                    ? fallbackName + ":projectedObjective:NET_DAMAGE"
                    : value;
            String[] parts = effective.split(":", 4);
            if (parts.length < 3 || parts.length > 4) {
                throw new IllegalArgumentException("Policy must be NAME:lane:objective[:flags], got: " + effective);
            }
            int projectedAuditLimit = SidePlannerSettings.DEFAULT_PROJECTED_AUDIT_LIMIT;
            int maxLaterDeclarationsPerTurn = SidePlannerSettings.DEFAULT_MAX_LATER_DECLARATIONS_PER_TURN;
            double[] warTypeWeights = neutralWarTypeWeights();
            double[] attackTypeWeights = neutralAttackTypeWeights();
            Double minimumViabilityProbe = null;
            Boolean allowLegalSpecialistFallback = null;
            Boolean admitPositiveOpeningBaseline = null;
            boolean objectiveDrivenAttackChoice = false;
            if (parts.length == 4 && !parts[3].isBlank()) {
                for (String flag : parts[3].split(";")) {
                    String[] kv = flag.split("=", 2);
                    if (kv.length == 2 && kv[0].equalsIgnoreCase("audit")) {
                        projectedAuditLimit = Integer.parseInt(kv[1]);
                    } else if (kv.length == 2 && kv[0].equalsIgnoreCase("laterCap")) {
                        maxLaterDeclarationsPerTurn = Integer.parseInt(kv[1]);
                    } else if (kv.length == 2 && kv[0].equalsIgnoreCase("war")) {
                        parseWeights(kv[1], WarType.values, warTypeWeights, "war");
                    } else if (kv.length == 2 && kv[0].equalsIgnoreCase("attack")) {
                        parseWeights(kv[1], AttackType.values, attackTypeWeights, "attack");
                    } else if (kv.length == 2 && kv[0].equalsIgnoreCase("minProbe")) {
                        minimumViabilityProbe = Double.parseDouble(kv[1]);
                    } else if (kv.length == 2 && kv[0].equalsIgnoreCase("specialists")) {
                        allowLegalSpecialistFallback = Boolean.parseBoolean(kv[1]);
                    } else if (kv.length == 2 && kv[0].equalsIgnoreCase("positiveBaseline")) {
                        admitPositiveOpeningBaseline = Boolean.parseBoolean(kv[1]);
                    } else if (kv.length == 2 && kv[0].equalsIgnoreCase("attackPolicy")) {
                        if (!kv[1].equalsIgnoreCase("heuristic") && !kv[1].equalsIgnoreCase("objective")) {
                            throw new IllegalArgumentException("attackPolicy must be heuristic or objective");
                        }
                        objectiveDrivenAttackChoice = kv[1].equalsIgnoreCase("objective");
                    } else {
                        throw new IllegalArgumentException("Unknown policy flag: " + flag);
                    }
                }
            }
            return new PolicySpec(
                    parts[0],
                    StrategicLaneComparisonHarness.Lane.parse(parts[1]),
                    BlitzObjective.valueOf(parts[2].toUpperCase(Locale.ROOT)),
                    projectedAuditLimit,
                    maxLaterDeclarationsPerTurn,
                    warTypeWeights,
                    attackTypeWeights,
                    minimumViabilityProbe,
                    allowLegalSpecialistFallback,
                    admitPositiveOpeningBaseline,
                    objectiveDrivenAttackChoice
            );
        }

        SidePolicy actingPolicy() {
            StrategicObjective strategicObjective = objective.objective();
            SidePolicy legacy = SidePolicy.legacy(name, strategicObjective);
            SideOpeningSettings opening = openingSettings(strategicObjective);
            return new SidePolicy(
                    legacy.name(),
                    legacy.objective(),
                        legacy.planner()
                            .withProjectedAuditLimit(projectedAuditLimit)
                            .withMaxLaterDeclarationsPerTurn(maxLaterDeclarationsPerTurn),
                    opening,
                    projection(strategicObjective, opening, legacy.projection()),
                    legacy.turnActor(),
                    true
            );
        }

        SidePolicy defendingPolicy() {
            StrategicObjective strategicObjective = objective.objective();
            SidePolicy legacy = SidePolicy.legacyPassive(name, strategicObjective);
            SideOpeningSettings opening = openingSettings(strategicObjective);
            return new SidePolicy(
                    legacy.name(),
                    legacy.objective(),
                        legacy.planner()
                            .withProjectedAuditLimit(projectedAuditLimit)
                            .withMaxLaterDeclarationsPerTurn(maxLaterDeclarationsPerTurn),
                    opening,
                    projection(strategicObjective, opening, legacy.projection()),
                    legacy.turnActor(),
                    false
            );
        }

        private SideProjectionPolicies projection(
                StrategicObjective strategicObjective,
                SideOpeningSettings opening,
                SideProjectionPolicies legacyProjection
        ) {
            return objectiveDrivenAttackChoice
                    ? SideProjectionPolicies.objectiveDriven(strategicObjective, opening)
                    : legacyProjection;
        }

        private SideOpeningSettings openingSettings(StrategicObjective strategicObjective) {
            CandidateEdgeAdmissionPolicy baseAdmission = strategicObjective.candidateEdgeAdmissionPolicy();
            if (baseAdmission == null) {
                baseAdmission = CandidateEdgeAdmissionPolicy.defaultPolicy();
            }
            CandidateEdgeAdmissionPolicy admissionPolicy = new CandidateEdgeAdmissionPolicy(
                    minimumViabilityProbe == null ? baseAdmission.minimumViabilityProbe() : minimumViabilityProbe,
                    allowLegalSpecialistFallback == null
                            ? baseAdmission.allowLegalSpecialistFallback()
                            : allowLegalSpecialistFallback,
                    admitPositiveOpeningBaseline == null
                            ? baseAdmission.admitPositiveOpeningBaseline()
                            : admitPositiveOpeningBaseline
            );
            return new SideOpeningSettings(warTypeWeights, attackTypeWeights, admissionPolicy);
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
            throw new IllegalArgumentException("Unknown " + flagName + " value: " + name);
        }
    }

    private static List<StrategicLaneComparisonHarness.ScenarioFamily> selectedScenarios(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of(StrategicLaneComparisonHarness.ScenarioFamily.values());
        }
        String[] names = filter.split(",");
        List<StrategicLaneComparisonHarness.ScenarioFamily> families = new ArrayList<>(names.length);
        for (String name : names) {
            families.add(StrategicLaneComparisonHarness.ScenarioFamily.parse(name.trim()));
        }
        return List.copyOf(families);
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

    private static String familyName(StrategicLaneComparisonHarness.ScenarioFamily family) {
        return family.cliName();
    }

    private static String formatDouble(double value, int scale) {
        return String.format(Locale.ROOT, "%1$." + scale + "f", value);
    }

    private static String tierCoverageSummary(int[] covered, int[] totals) {
        StringBuilder builder = new StringBuilder();
        for (StrategicLaneComparisonHarness.TierSegment tier : StrategicLaneComparisonHarness.TierSegment.values()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            int tierIndex = tier.ordinal();
            int c = tierIndex < covered.length ? covered[tierIndex] : 0;
            int t = tierIndex < totals.length ? totals[tierIndex] : 0;
            builder.append(tier.name().toLowerCase(Locale.ROOT)).append(':').append(c).append('/').append(t);
        }
        return builder.toString();
    }

    private static String warCountHistogramSummary(int[] histogram) {
        if (histogram.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int wars = 0; wars < histogram.length; wars++) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(wars).append(':').append(histogram[wars]);
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
}
