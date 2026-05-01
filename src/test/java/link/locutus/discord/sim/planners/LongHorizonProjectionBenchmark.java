package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local synthetic harness for long-horizon projection/assignment tiers.
 *
 * <p>Run with Gradle's test-main task, for example:</p>
 *
 * <pre>
 * .\gradlew.bat runTestMain -PmainClass=link.locutus.discord.sim.planners.LongHorizonProjectionBenchmark --no-daemon --console=plain
 * </pre>
 *
 * <p>Optional args: {@code --maxPopulation=50}, {@code --repetitions=1},
 * {@code --projectionScoring=false}, and comma-separated {@code --horizons=72,144}.
 * Matching system properties with the {@code longHorizonBenchmark.} prefix are also accepted.</p>
 */
public final class LongHorizonProjectionBenchmark {
    private static final int[] DEFAULT_POPULATIONS = {50, 100, 200};
    private static final int[] DEFAULT_HORIZONS = {72, 144, 360, 720};

    private LongHorizonProjectionBenchmark() {
    }

    public static void main(String[] args) {
        int maxPopulation = optionInt(args, "maxPopulation", Integer.getInteger("longHorizonBenchmark.maxPopulation", 200));
        int repetitions = Math.max(1, optionInt(args, "repetitions", Integer.getInteger("longHorizonBenchmark.repetitions", 3)));
        boolean projectionScoring = optionBoolean(args, "projectionScoring", Boolean.parseBoolean(System.getProperty("longHorizonBenchmark.projectionScoring", "true")));
        int[] horizons = horizons(args);
        System.out.printf(Locale.ROOT,
                "population,horizon,edges,repetitions,projectionScoring,bestMs,avgMs,pairs,score%n");
        for (int population : DEFAULT_POPULATIONS) {
            if (population > maxPopulation) {
                continue;
            }
            Fixture fixture = Fixture.create(population);
            for (int horizon : horizons) {
                Result bestResult = null;
                long totalNanos = 0L;
                for (int repetition = 0; repetition < repetitions; repetition++) {
                    long startNanos = System.nanoTime();
                    Result result = fixture.run(horizon, projectionScoring);
                    long elapsedNanos = System.nanoTime() - startNanos;
                    totalNanos += elapsedNanos;
                    if (bestResult == null || elapsedNanos < bestResult.elapsedNanos()) {
                        bestResult = result.withElapsedNanos(elapsedNanos);
                    }
                }
                double bestMs = bestResult.elapsedNanos() / 1_000_000.0d;
                double avgMs = (totalNanos / (double) repetitions) / 1_000_000.0d;
                System.out.printf(Locale.ROOT,
                        "%d,%d,%d,%d,%s,%.3f,%.3f,%d,%.3f%n",
                        population,
                        horizon,
                        fixture.edges.edgeCount(),
                        repetitions,
                        projectionScoring,
                        bestMs,
                        avgMs,
                        bestResult.pairCount(),
                        bestResult.score());
            }
        }
    }

    private static int[] horizons(String[] args) {
        String configured = option(args, "horizons", System.getProperty("longHorizonBenchmark.horizons"));
        if (configured == null || configured.isBlank()) {
            return DEFAULT_HORIZONS;
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    private static int optionInt(String[] args, String name, int defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static boolean optionBoolean(String[] args, String name, boolean defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static String option(String[] args, String name, String defaultValue) {
        String prefix = "--" + name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }

    private record Fixture(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        static Fixture create(int population) {
            List<DBNationSnapshot> attackers = new ArrayList<>(population);
            List<DBNationSnapshot> defenders = new ArrayList<>(population);
            for (int index = 0; index < population; index++) {
                attackers.add(nation(1_000 + index, 1, index, 3));
                defenders.add(nation(200_000 + index, 2, population - index, 1));
            }
            CompiledScenario scenario = new ScenarioCompiler().compile(
                    attackers,
                    defenders,
                    OverrideSet.EMPTY,
                    TreatyProvider.NONE,
                    Map.of()
            );
            CandidateEdgeTable edges = new CandidateEdgeTable(population * population);
            for (int attackerIndex = 0; attackerIndex < population; attackerIndex++) {
                for (int defenderIndex = 0; defenderIndex < population; defenderIndex++) {
                    double distance = Math.abs(attackerIndex - defenderIndex);
                    float score = (float) (120.0d - (0.35d * distance) + ((attackerIndex % 7) * 0.05d));
                    float counterRisk = (float) ((defenderIndex % 9) / 9.0d);
                    edges.add(attackerIndex, defenderIndex, score, counterRisk);
                }
            }
            int[] attackerCaps = new int[population];
            int[] defenderCaps = new int[population];
            int[] attackerStrengthRanks = new int[population];
            int[] attackerNationIds = new int[population];
            int[] defenderNationIds = new int[population];
            Arrays.fill(attackerCaps, 3);
            Arrays.fill(defenderCaps, 3);
            for (int index = 0; index < population; index++) {
                attackerStrengthRanks[index] = index;
                attackerNationIds[index] = scenario.attackerNationId(index);
                defenderNationIds[index] = scenario.defenderNationId(index);
            }
            return new Fixture(scenario, edges, attackerCaps, defenderCaps, attackerStrengthRanks, attackerNationIds, defenderNationIds);
        }

        Result run(int horizonTurns, boolean projectionScoring) {
            LongHorizonAssignmentOptimizer.Result assignmentResult = projectionScoring
                    ? LongHorizonAssignmentOptimizer.solveDetailed(
                            edges,
                            scenario,
                            attackerCaps,
                            defenderCaps,
                            attackerStrengthRanks,
                            attackerNationIds,
                            defenderNationIds,
                            List.of(),
                            horizonTurns,
                            new LongHorizonAssignmentOptimizer.ProjectionScoringContext(new DamageObjective())
                    )
                    : LongHorizonAssignmentOptimizer.solveDetailed(
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
            Map<Integer, List<Integer>> assignment = assignmentResult.assignment();
            ScoreSummary summary = assignmentResult.projectedObjectiveSummary() != null
                    ? assignmentResult.projectedObjectiveSummary()
                    : LongHorizonAssignmentOptimizer.projectedObjectiveSummary(
                            edges,
                            scenario,
                            attackerCaps,
                            defenderCaps,
                            horizonTurns,
                            assignment,
                            new DamageObjective(),
                            attackerNationIds,
                            defenderNationIds
                    );
            int pairCount = 0;
            for (List<Integer> targets : assignment.values()) {
                pairCount += targets.size();
            }
            return new Result(0L, pairCount, summary.mean());
        }
    }

    private record Result(long elapsedNanos, int pairCount, double score) {
        Result withElapsedNanos(long newElapsedNanos) {
            return new Result(newElapsedNanos, pairCount, score);
        }
    }

    private static DBNationSnapshot nation(int nationId, int teamId, int offset, int maxOff) {
        int cities = 18 + (offset % 8);
        int aircraft = 1_500 + offset * 3;
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .score(1_000.0d + offset)
                .cities(cities)
                .nonInfraScoreBase(500.0d + cities * 50.0d)
                .cityInfra(uniformInfra(cities, 1_800.0d + (offset % 5) * 100.0d))
                .maxOff(maxOff)
                .unit(MilitaryUnit.SOLDIER, 250_000 + offset * 100)
                .unit(MilitaryUnit.TANK, 20_000 + offset * 20)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .unit(MilitaryUnit.SHIP, 200 + offset)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] values = new double[cities];
        Arrays.fill(values, infra);
        return values;
    }
}
