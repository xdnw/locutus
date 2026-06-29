package link.locutus.discord.sim.ab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single-policy scenario scorecard over synthetic scenario families using only public production planner APIs.
 *
 * <pre>
 * .\gradlew.bat runTestMain -PmainClass=link.locutus.discord.sim.ab.ProductionPolicyScenarioHarness --no-daemon --console=plain "-PappArgs=--scenarios=parity,beigeRebuildBreakout --modes=projection --objectives=CONTROL"
 * </pre>
 */
public final class ProductionPolicyScenarioHarness {
    private static final int DEFAULT_HORIZON_TURNS = 72;
    private static final int DEFAULT_POPULATION = 0;
    private static final String CSV_HEADER = String.join(",",
            "family",
            "mode",
            "objective",
            "horizon",
            "attackerCount",
            "defenderCount",
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

    private ProductionPolicyScenarioHarness() {
    }

    public static void main(String[] args) {
        int horizonTurns = optionInt(args, "horizon", DEFAULT_HORIZON_TURNS);
        int repetitions = Math.max(1, optionInt(args, "repetitions", 1));
        int requestedPopulation = optionInt(args, "population", optionInt(args, "maxPopulation", DEFAULT_POPULATION));
        System.out.print(renderCsv(
                horizonTurns,
                repetitions,
                requestedPopulation,
                option(args, "scenarios"),
                option(args, "modes"),
                option(args, "objectives")
        ));
    }

    static String renderCsv(
            int horizonTurns,
            int repetitions,
            int requestedPopulation,
            String scenarioFilter,
            String modeFilter,
            String objectiveFilter
    ) {
        StringBuilder out = new StringBuilder();
        out.append(CSV_HEADER).append(System.lineSeparator());
        for (ProductionPolicyAbHarness.ScenarioFamily family : selectedScenarios(scenarioFilter)) {
            ProductionPolicyAbHarness.Fixture fixture = family.fixture(requestedPopulation);
            for (ProductionPolicyAbHarness.PolicyMode mode : selectedModes(modeFilter)) {
                for (link.locutus.discord.sim.BlitzObjective objective : selectedObjectives(objectiveFilter)) {
                    appendRow(out, family.cliName(), fixture, mode, objective, horizonTurns, repetitions);
                }
            }
        }
        return out.toString();
    }

    private static void appendRow(
            StringBuilder out,
            String familyName,
            ProductionPolicyAbHarness.Fixture fixture,
            ProductionPolicyAbHarness.PolicyMode mode,
            link.locutus.discord.sim.BlitzObjective objective,
            int horizonTurns,
            int repetitions
    ) {
        ProductionPolicyAbHarness.PolicySpec spec = ProductionPolicyAbHarness.PolicySpec.parse(
                mode.name().toLowerCase(Locale.ROOT) + ":" + objective.name(),
                objective.name()
        );

        ProductionPolicyAbHarness.PassScorecard best = null;
        long totalNanos = 0L;
        for (int repetition = 0; repetition < repetitions; repetition++) {
            long startNanos = System.nanoTime();
            ProductionPolicyAbHarness.PassScorecard scorecard = ProductionPolicyAbHarness.runPass(fixture, spec, spec, horizonTurns);
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
                mode.name().toLowerCase(Locale.ROOT),
                objective.name(),
                Integer.toString(horizonTurns),
                Integer.toString(fixture.attackers().size()),
                Integer.toString(fixture.defenders().size()),
                Integer.toString(best.assignmentPairs()),
                Integer.toString(best.idleAttackersFreeSlot()),
                formatDouble(best.idleAttackersFreeSlotPct(), 2),
                formatDouble(best.objectiveMean(), 3),
                formatDouble(best.objectiveP10(), 3),
                formatDouble(best.objectiveP50(), 3),
                formatDouble(best.objectiveP90(), 3),
                Integer.toString(best.diagnosticWarnings()),
                enumCountSummary(link.locutus.discord.apiv1.enums.WarType.values, best.assignedWarTypeCounts()),
                enumCountSummary(link.locutus.discord.apiv1.enums.AttackType.values, best.assignedAttackTypeCounts()),
                formatDouble(bestMs, 3),
                formatDouble(avgMs, 3)
        )).append(System.lineSeparator());
    }

    private static List<ProductionPolicyAbHarness.ScenarioFamily> selectedScenarios(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of(ProductionPolicyAbHarness.ScenarioFamily.values());
        }
        String[] names = filter.split(",");
        List<ProductionPolicyAbHarness.ScenarioFamily> families = new ArrayList<>(names.length);
        for (String name : names) {
            families.add(ProductionPolicyAbHarness.ScenarioFamily.parse(name.trim()));
        }
        return List.copyOf(families);
    }

    private static List<ProductionPolicyAbHarness.PolicyMode> selectedModes(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of(ProductionPolicyAbHarness.PolicyMode.values());
        }
        String[] names = filter.split(",");
        List<ProductionPolicyAbHarness.PolicyMode> modes = new ArrayList<>(names.length);
        for (String name : names) {
            modes.add(ProductionPolicyAbHarness.PolicyMode.parse(name.trim()));
        }
        return List.copyOf(modes);
    }

    private static List<link.locutus.discord.sim.BlitzObjective> selectedObjectives(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of(link.locutus.discord.sim.BlitzObjective.values());
        }
        String[] names = filter.split(",");
        List<link.locutus.discord.sim.BlitzObjective> objectives = new ArrayList<>(names.length);
        for (String name : names) {
            objectives.add(link.locutus.discord.sim.BlitzObjective.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        }
        return List.copyOf(objectives);
    }

    private static String formatDouble(double value, int scale) {
        return String.format(Locale.ROOT, "%1$." + scale + "f", value);
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