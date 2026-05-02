package link.locutus.discord.sim.planners;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayTrace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.lang.reflect.Proxy;
import java.util.zip.GZIPOutputStream;

public final class BlitzPlannerPipelineBenchmark {
    private static final int[] DEFAULT_POPULATIONS = {50, 100, 200};
    private static final int[] DEFAULT_HORIZONS = {72, 144, 360, 720};
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();
    private static final com.sun.management.ThreadMXBean THREAD_MX_BEAN = threadMxBean();

    private BlitzPlannerPipelineBenchmark() {
    }

    public static void main(String[] args) {
        BenchmarkConfig config = BenchmarkConfig.parse(args);

        System.out.println("slice,population,horizon,bucketSize,repetitions,bestMs,avgMs,pairs,score,replayJsonBytes,replayGzipBytes,allocatedBytes,heapDeltaBytes,fixture,attackerCount,defenderCount");
        if (config.profile()) {
            System.out.println("stage,slice,population,horizon,bucketSize,fixture,attackerCount,defenderCount,scope,calls,totalMs,maxMs,counters");
        }
        for (Fixture fixture : fixtures(config)) {
            int population = fixture.population();
            for (int horizon : config.horizons()) {
                for (Slice slice : config.slices()) {
                    Result best = null;
                    long totalNanos = 0L;
                    for (int repetition = 0; repetition < config.repetitions(); repetition++) {
                        Result result = fixture.run(slice, horizon, config.bucketSize(), config.projectionScoring(), config.profile());
                        totalNanos += result.elapsedNanos();
                        if (best == null || result.elapsedNanos() < best.elapsedNanos()) {
                            best = result;
                        }
                    }
                    double bestMs = best.elapsedNanos() / 1_000_000.0d;
                    double avgMs = (totalNanos / (double) config.repetitions()) / 1_000_000.0d;
                    System.out.printf(Locale.ROOT,
                            "%s,%d,%d,%d,%d,%.3f,%.3f,%d,%.3f,%d,%d,%d,%d,%s,%d,%d%n",
                            slice.cliName,
                            population,
                            horizon,
                            config.bucketSize(),
                            config.repetitions(),
                            bestMs,
                            avgMs,
                            best.pairCount(),
                            best.score(),
                            best.replayJsonBytes(),
                            best.replayGzipBytes(),
                            best.allocatedBytes(),
                            best.heapDeltaBytes(),
                            fixture.label(),
                            fixture.attackerCount(),
                            fixture.defenderCount());
                    if (config.profile() && best.profileSnapshot() != null) {
                        printProfile(slice, fixture, horizon, config.bucketSize(), best.profileSnapshot());
                    }
                }
            }
        }
    }

    private static List<Fixture> fixtures(BenchmarkConfig config) {
        if (config.hasLiveAllianceFixture()) {
            return List.of(Fixture.createLive(config));
        }
        List<Fixture> fixtures = new ArrayList<>();
        for (int population : DEFAULT_POPULATIONS) {
            if (population > config.maxPopulation()) {
                continue;
            }
            fixtures.add(Fixture.createSynthetic(population, config.localSearchBudgetMs(), config.localSearchMaxIterations()));
        }
        return fixtures;
    }

    private static void printProfile(Slice slice, Fixture fixture, int horizon, int bucketSize, PlannerProfiler.ProfileSnapshot snapshot) {
        for (PlannerProfiler.Scope scope : PlannerProfiler.Scope.values()) {
            PlannerProfiler.ScopeStats stats = snapshot.stats(scope);
            if (stats.calls() == 0L) {
                continue;
            }
            String counters = stats.counters().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(";"));
            System.out.printf(Locale.ROOT,
                    "stage,%s,%d,%d,%d,%s,%d,%d,%s,%d,%.3f,%.3f,%s%n",
                    slice.cliName,
                    fixture.population(),
                    horizon,
                    bucketSize,
                    fixture.label(),
                    fixture.attackerCount(),
                    fixture.defenderCount(),
                    scope.name(),
                    stats.calls(),
                    stats.totalMillis(),
                    stats.maxMillis(),
                    counters);
        }
    }

            static List<Slice> slices(String[] args, boolean hasLiveAllianceFixture) {
        String configured = option(args, "slices", System.getProperty("blitzPipelineBenchmark.slices"));
        if (configured == null || configured.isBlank()) {
                return hasLiveAllianceFixture
                    ? List.of(Slice.REPLAY)
                    : List.of(Slice.BLITZ, Slice.REPLAY, Slice.SCHEDULED, Slice.OPENING, Slice.LONG_HORIZON, Slice.DELTA);
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Slice::fromCliName)
                .toList();
    }

    private static int[] horizons(String[] args) {
        String configured = option(args, "horizons", System.getProperty("blitzPipelineBenchmark.horizons"));
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
        return value == null || value.isBlank() ? defaultValue : defaultValueIfNull(value, defaultValue);
    }

    private static int defaultValueIfNull(String value, int defaultValue) {
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static boolean optionBoolean(String[] args, String name, boolean defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static long optionLong(String[] args, String name, long defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Long.parseLong(value);
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

    private enum Slice {
        BLITZ("blitz"),
        REPLAY("replay"),
        SCHEDULED("scheduled"),
        SCHEDULED_DIRECT("scheduledDirect"),
        OPENING("opening"),
        LONG_HORIZON("longHorizon"),
        DELTA("delta");

        private final String cliName;

        Slice(String cliName) {
            this.cliName = cliName;
        }

        private static Slice fromCliName(String cliName) {
            for (Slice slice : values()) {
                if (slice.cliName.equalsIgnoreCase(cliName)) {
                    return slice;
                }
            }
            throw new IllegalArgumentException("Unknown slice: " + cliName);
        }
    }

    static final class BenchmarkConfig {
        private final int maxPopulation;
        private final int repetitions;
        private final int bucketSize;
        private final boolean profile;
        private final boolean projectionScoring;
        private final long localSearchBudgetMs;
        private final int localSearchMaxIterations;
        private final int[] horizons;
        private final List<Slice> slices;
        private final String attackerAlliances;
        private final String defenderAlliances;
        private final boolean clearVm;
        private final boolean clearBeige;

        private BenchmarkConfig(
                int maxPopulation,
                int repetitions,
                int bucketSize,
                boolean profile,
                boolean projectionScoring,
                long localSearchBudgetMs,
                int localSearchMaxIterations,
                int[] horizons,
                List<Slice> slices,
                String attackerAlliances,
                String defenderAlliances,
                boolean clearVm,
                boolean clearBeige
        ) {
            this.maxPopulation = maxPopulation;
            this.repetitions = repetitions;
            this.bucketSize = bucketSize;
            this.profile = profile;
            this.projectionScoring = projectionScoring;
            this.localSearchBudgetMs = localSearchBudgetMs;
            this.localSearchMaxIterations = localSearchMaxIterations;
            this.horizons = horizons;
            this.slices = slices;
            this.attackerAlliances = attackerAlliances;
            this.defenderAlliances = defenderAlliances;
            this.clearVm = clearVm;
            this.clearBeige = clearBeige;
        }

        static BenchmarkConfig parse(String[] args) {
            String attackerAlliances = blankToNull(option(args, "attackerAlliances", System.getProperty("blitzPipelineBenchmark.attackerAlliances")));
            String defenderAlliances = blankToNull(option(args, "defenderAlliances", System.getProperty("blitzPipelineBenchmark.defenderAlliances")));
            boolean hasLiveAllianceFixture = attackerAlliances != null || defenderAlliances != null;
            if (hasLiveAllianceFixture && (attackerAlliances == null || defenderAlliances == null)) {
                throw new IllegalArgumentException("attackerAlliances and defenderAlliances must be provided together");
            }
            return new BenchmarkConfig(
                    optionInt(args, "maxPopulation", Integer.getInteger("blitzPipelineBenchmark.maxPopulation", 200)),
                    Math.max(1, optionInt(args, "repetitions", Integer.getInteger("blitzPipelineBenchmark.repetitions", 1))),
                    Math.max(1, optionInt(args, "bucketSize", Integer.getInteger("blitzPipelineBenchmark.bucketSize", 24))),
                    optionBoolean(args, "profile", Boolean.parseBoolean(System.getProperty("blitzPipelineBenchmark.profile", "true"))),
                    optionBoolean(args, "projectionScoring", Boolean.parseBoolean(System.getProperty("blitzPipelineBenchmark.projectionScoring", "true"))),
                    Math.max(1L, optionLong(args, "localSearchBudgetMs", Long.getLong("blitzPipelineBenchmark.localSearchBudgetMs", SimTuning.DEFAULT_LOCAL_SEARCH_BUDGET_MS))),
                    Math.max(1, optionInt(args, "localSearchMaxIterations", Integer.getInteger("blitzPipelineBenchmark.localSearchMaxIterations", SimTuning.DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS))),
                    BlitzPlannerPipelineBenchmark.horizons(args),
                    BlitzPlannerPipelineBenchmark.slices(args, hasLiveAllianceFixture),
                    attackerAlliances,
                    defenderAlliances,
                    optionBoolean(args, "clearVm", Boolean.parseBoolean(System.getProperty("blitzPipelineBenchmark.clearVm", "false"))),
                    optionBoolean(args, "clearBeige", Boolean.parseBoolean(System.getProperty("blitzPipelineBenchmark.clearBeige", "false")))
            );
        }

        int maxPopulation() {
            return maxPopulation;
        }

        int repetitions() {
            return repetitions;
        }

        int bucketSize() {
            return bucketSize;
        }

        boolean profile() {
            return profile;
        }

        boolean projectionScoring() {
            return projectionScoring;
        }

        long localSearchBudgetMs() {
            return localSearchBudgetMs;
        }

        int localSearchMaxIterations() {
            return localSearchMaxIterations;
        }

        int[] horizons() {
            return horizons;
        }

        List<Slice> slices() {
            return slices;
        }

        List<String> sliceCliNames() {
            return slices.stream().map(slice -> slice.cliName).toList();
        }

        String attackerAlliances() {
            return attackerAlliances;
        }

        String defenderAlliances() {
            return defenderAlliances;
        }

        boolean clearVm() {
            return clearVm;
        }

        boolean clearBeige() {
            return clearBeige;
        }

        boolean hasLiveAllianceFixture() {
            return attackerAlliances != null;
        }
    }

    private record Fixture(
            String label,
            SimTuning tuning,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CompiledScenario scenario,
            CandidateEdgeTable longHorizonEdges,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        static Fixture createSynthetic(int population, long localSearchBudgetMs, int localSearchMaxIterations) {
            List<DBNationSnapshot> attackers = new ArrayList<>(population);
            List<DBNationSnapshot> defenders = new ArrayList<>(population);
            for (int index = 0; index < population; index++) {
                attackers.add(nation(1_000 + index, 1, index, 3));
                defenders.add(nation(200_000 + index, 2, population - index, 1));
            }
            return create("synthetic", attackers, defenders, denseEdges(population), localSearchBudgetMs, localSearchMaxIterations);
        }

        static Fixture createLive(BenchmarkConfig config) {
            Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
            Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
            Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS = false;

            try (LiveDatabases databases = loadLiveDatabases()) {
                return createLive(config, databases.nationDb());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load live alliance fixture", e);
            }
        }

        private static Fixture createLive(BenchmarkConfig config, NationDB nationDb) {
            List<String> attackerTokens = csvValues(config.attackerAlliances());
            List<String> defenderTokens = csvValues(config.defenderAlliances());
            Set<DBNation> attackerNations = resolveAllianceMembers(nationDb, attackerTokens, "attackerAlliances");
            Set<DBNation> defenderNations = resolveAllianceMembers(nationDb, defenderTokens, "defenderAlliances");
            List<DBNationSnapshot> attackers = liveSnapshots(attackerNations, config.clearVm(), config.clearBeige());
            List<DBNationSnapshot> defenders = liveSnapshots(defenderNations, config.clearVm(), config.clearBeige());
            if (attackers.isEmpty()) {
                throw new IllegalArgumentException("attackerAlliances resolved to zero nations");
            }
            if (defenders.isEmpty()) {
                throw new IllegalArgumentException("defenderAlliances resolved to zero nations");
            }
            CandidateEdgeTable openingEdges = openingEdges(attackers, defenders, config.localSearchBudgetMs(), config.localSearchMaxIterations());
            return create(
                    liveLabel(attackerTokens, defenderTokens),
                    attackers,
                    defenders,
                    openingEdges,
                    config.localSearchBudgetMs(),
                    config.localSearchMaxIterations()
            );
        }

        private static Fixture create(
                String label,
                List<DBNationSnapshot> attackers,
                List<DBNationSnapshot> defenders,
                CandidateEdgeTable longHorizonEdges,
                long localSearchBudgetMs,
                int localSearchMaxIterations
        ) {
            SimTuning tuning = new SimTuning(
                    SimTuning.DEFAULT_INTRA_TURN_PASSES,
                    SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
                    SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                    SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                    SimTuning.DEFAULT_POLICY_COOLDOWN_TURNS,
                    localSearchBudgetMs,
                    localSearchMaxIterations,
                    SimTuning.DEFAULT_CANDIDATES_PER_ATTACKER,
                    SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT,
                    SimTuning.DEFAULT_STATE_RESOLUTION_MODE,
                    SimTuning.DEFAULT_STOCHASTIC_SEED,
                    SimTuning.DEFAULT_STOCHASTIC_SAMPLE_COUNT
            );
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
            for (int index = 0; index < attackers.size(); index++) {
                attackerCaps[index] = OverrideSet.EMPTY.effectiveFreeOff(attackers.get(index));
                attackerStrengthRanks[index] = index;
                attackerNationIds[index] = scenario.attackerNationId(index);
            }
            for (int index = 0; index < defenders.size(); index++) {
                defenderCaps[index] = OverrideSet.EMPTY.effectiveFreeDef(defenders.get(index));
                defenderNationIds[index] = scenario.defenderNationId(index);
            }
            return new Fixture(
                    label,
                    tuning,
                    List.copyOf(sortedSnapshots(attackers)),
                    List.copyOf(sortedSnapshots(defenders)),
                    scenario,
                    longHorizonEdges,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds
            );
        }

        int population() {
            return Math.max(attackers.size(), defenders.size());
        }

        int attackerCount() {
            return attackers.size();
        }

        int defenderCount() {
            return defenders.size();
        }

        Result run(Slice slice, int horizonTurns, int bucketSizeTurns, boolean projectionScoring, boolean profile) {
            PlannerProfiler.Session session = profile ? new PlannerProfiler.Session() : null;
            long startAllocatedBytes = currentThreadAllocatedBytes();
            long startHeapBytes = usedHeapBytes();
            long startNanos = System.nanoTime();
            Outcome outcome = session == null
                    ? runSlice(slice, horizonTurns, bucketSizeTurns, projectionScoring)
                    : PlannerProfiler.withSession(session, () -> runSlice(slice, horizonTurns, bucketSizeTurns, projectionScoring));
            long elapsedNanos = System.nanoTime() - startNanos;
            long endAllocatedBytes = currentThreadAllocatedBytes();
            long endHeapBytes = usedHeapBytes();
            return new Result(
                elapsedNanos,
                outcome.pairCount(),
                outcome.score(),
                outcome.replayJsonBytes(),
                outcome.replayGzipBytes(),
                deltaOrUnsupported(startAllocatedBytes, endAllocatedBytes),
                endHeapBytes - startHeapBytes,
                session == null ? null : session.snapshot()
            );
        }

        private Outcome runSlice(Slice slice, int horizonTurns, int bucketSizeTurns, boolean projectionScoring) {
            return switch (slice) {
                case BLITZ -> runBlitz(horizonTurns);
                case REPLAY -> runReplay(horizonTurns);
                case SCHEDULED -> runScheduled(horizonTurns, bucketSizeTurns, ScheduledTargetPlanner.CarryStateMode.PROJECTION_STATE);
                case SCHEDULED_DIRECT -> runScheduled(horizonTurns, bucketSizeTurns, ScheduledTargetPlanner.CarryStateMode.DIRECT_CONFLICT);
                case OPENING -> runOpening();
                case LONG_HORIZON -> runLongHorizon(horizonTurns, projectionScoring);
                case DELTA -> runDelta();
            };
        }

        private Outcome runBlitz(int horizonTurns) {
            BlitzPlanner planner = new BlitzPlanner(tuning, TreatyProvider.NONE, OverrideSet.EMPTY, new DamageObjective());
            BlitzAssignment assignment = planner.assign(attackers, defenders, 0, List.of(), horizonTurns);
            return new Outcome(pairCount(assignment.assignment()), assignment.objectiveScore(), -1L, -1L);
        }

        private Outcome runReplay(int horizonTurns) {
            BlitzPlanner planner = new BlitzPlanner(tuning, TreatyProvider.NONE, OverrideSet.EMPTY, new DamageObjective());
            BlitzAssignment assignment = planner.assign(attackers, defenders, 0, List.of(), horizonTurns);
            BlitzReplayTrace trace = PlannerReplayProjector.capture(
                    tuning,
                    OverrideSet.EMPTY,
                    combined(attackers, defenders),
                    ids(attackers),
                    ids(defenders),
                    assignment.assignment(),
                    0,
                    horizonTurns
            );
            byte[] replayJson = WebUtil.GSON.toJson(trace).getBytes(StandardCharsets.UTF_8);
            return new Outcome(pairCount(assignment.assignment()), assignment.objectiveScore(), replayJson.length, gzipLength(replayJson));
        }

        private Outcome runScheduled(
            int horizonTurns,
            int bucketSizeTurns,
            ScheduledTargetPlanner.CarryStateMode carryStateMode
        ) {
            ScheduledTargetPlanner planner = new ScheduledTargetPlanner(
                tuning,
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                SnapshotActivityProvider.BASELINE,
                PlannerTransitionSemantics.NONE,
                carryStateMode
            );
            List<ScheduledAttacker> scheduledAttackers = attackers.stream()
                    .map(attacker -> new ScheduledAttacker(attacker, List.of(new AvailabilityWindow(0, Math.max(0, horizonTurns - 1)))))
                    .toList();
            ScheduledTargetPlan plan = planner.assign(scheduledAttackers, defenders, bucketSizeTurns);
            int pairCount = 0;
            for (ScheduledBucketAssignment bucket : plan.buckets()) {
                pairCount += pairCount(bucket.assignment().assignment());
            }
            return new Outcome(pairCount, plan.buckets().size(), -1L, -1L);
        }

        private Outcome runOpening() {
            CandidateEdgeTable edges = new CandidateEdgeTable();
            OpeningEvaluator.evaluate(
                    scenario,
                    tuning,
                    OverrideSet.EMPTY,
                    new DamageObjective(),
                    Arrays.copyOf(attackerCaps, attackerCaps.length),
                    Arrays.copyOf(defenderCaps, defenderCaps.length),
                    edges
            );
                    return new Outcome(edges.edgeCount(), edges.edgeCount(), -1L, -1L);
        }

        private Outcome runLongHorizon(int horizonTurns, boolean projectionScoring) {
            LongHorizonAssignmentOptimizer.Result result = LongHorizonAssignmentOptimizer.solveDetailed(
                    longHorizonEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    List.of(),
                    horizonTurns,
                    projectionScoring ? new LongHorizonAssignmentOptimizer.ProjectionScoringContext(new DamageObjective()) : null
            );
            ObjectiveValueSummary summary = result.projectedObjectiveSummary() != null
                    ? result.projectedObjectiveSummary()
                    : LongHorizonAssignmentOptimizer.projectedObjectiveSummary(
                            longHorizonEdges,
                            scenario,
                            attackerCaps,
                            defenderCaps,
                            horizonTurns,
                            result.assignment(),
                            new DamageObjective(),
                            attackerNationIds,
                            defenderNationIds
                    );
                        return new Outcome(pairCount(result.assignment()), summary.mean(), -1L, -1L);
        }

                private Outcome runDelta() {
                    Map<Integer, List<Integer>> assignment = PrimitiveAssignmentSolver.solveAssignment(
                        longHorizonEdges,
                        scenario.attackerCount(),
                        scenario.defenderCount(),
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        List.of()
                    );
                    int firstAttackerId = attackers.get(0).nationId();
                    int secondAttackerId = attackers.get(1).nationId();
                    List<Integer> firstTargets = assignment.getOrDefault(firstAttackerId, List.of());
                    List<Integer> secondTargets = assignment.getOrDefault(secondAttackerId, List.of());
                    if (firstTargets.isEmpty() || secondTargets.isEmpty()) {
                    return new Outcome(0, 0.0d, -1L, -1L);
                    }

                    PlannerAssignmentSession session = PlannerAssignmentSession.create(
                        assignment,
                        attackers,
                        defenders,
                        capByNationId(attackers, 3),
                        capByNationId(defenders, 3)
                    );
                    PlannerAssignmentChange change = PlannerAssignmentChange.pair(
                        firstAttackerId,
                        List.of(secondTargets.get(0)),
                        secondAttackerId,
                        List.of(firstTargets.get(0))
                    );
                    double delta = PlannerConflictExecutor.scoreAssignmentDelta(
                        tuning,
                        OverrideSet.EMPTY,
                        new DamageObjective(),
                        session,
                        change,
                        attackers,
                        defenders,
                        attackers.get(0).teamId()
                    );
                    return new Outcome(2, delta, -1L, -1L);
                }
    }

    private static long gzipLength(byte[] jsonBytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(jsonBytes.length);
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(out, 65_536)) {
                gzipOut.write(jsonBytes);
            }
            return out.size();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to gzip replay benchmark payload", e);
        }
    }

    private record Outcome(int pairCount, double score, long replayJsonBytes, long replayGzipBytes) {
    }

    private record Result(
            long elapsedNanos,
            int pairCount,
            double score,
            long replayJsonBytes,
            long replayGzipBytes,
            long allocatedBytes,
            long heapDeltaBytes,
            PlannerProfiler.ProfileSnapshot profileSnapshot
    ) {
    }

        private static CandidateEdgeTable openingEdges(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            long localSearchBudgetMs,
            int localSearchMaxIterations
        ) {
        SimTuning tuning = new SimTuning(
            SimTuning.DEFAULT_INTRA_TURN_PASSES,
            SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
            SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
            SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
            SimTuning.DEFAULT_POLICY_COOLDOWN_TURNS,
            localSearchBudgetMs,
            localSearchMaxIterations,
            SimTuning.DEFAULT_CANDIDATES_PER_ATTACKER,
            SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT,
            SimTuning.DEFAULT_STATE_RESOLUTION_MODE,
            SimTuning.DEFAULT_STOCHASTIC_SEED,
            SimTuning.DEFAULT_STOCHASTIC_SAMPLE_COUNT
        );
        CompiledScenario scenario = SCENARIO_COMPILER.compile(
            attackers,
            defenders,
            OverrideSet.EMPTY,
            TreatyProvider.NONE,
            Map.of()
        );
        int[] attackerCaps = new int[attackers.size()];
        int[] defenderCaps = new int[defenders.size()];
        for (int index = 0; index < attackers.size(); index++) {
            attackerCaps[index] = OverrideSet.EMPTY.effectiveFreeOff(attackers.get(index));
        }
        for (int index = 0; index < defenders.size(); index++) {
            defenderCaps[index] = OverrideSet.EMPTY.effectiveFreeDef(defenders.get(index));
        }
        CandidateEdgeTable edges = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
            scenario,
            tuning,
            OverrideSet.EMPTY,
            new DamageObjective(),
            Arrays.copyOf(attackerCaps, attackerCaps.length),
            Arrays.copyOf(defenderCaps, defenderCaps.length),
            edges
        );
        return edges;
        }

    private static CandidateEdgeTable denseEdges(int population) {
        CandidateEdgeTable edges = new CandidateEdgeTable(population * population);
        for (int attackerIndex = 0; attackerIndex < population; attackerIndex++) {
            for (int defenderIndex = 0; defenderIndex < population; defenderIndex++) {
                double distance = Math.abs(attackerIndex - defenderIndex);
                float score = (float) (120.0d - (0.35d * distance) + ((attackerIndex % 7) * 0.05d));
                float counterRisk = (float) ((defenderIndex % 9) / 9.0d);
                edges.add(attackerIndex, defenderIndex, score, counterRisk);
            }
        }
        return edges;
    }

    private static List<DBNationSnapshot> combined(Collection<DBNationSnapshot> attackers, Collection<DBNationSnapshot> defenders) {
        ArrayList<DBNationSnapshot> combined = new ArrayList<>(attackers.size() + defenders.size());
        combined.addAll(attackers);
        combined.addAll(defenders);
        combined.sort(Comparator.comparingInt(DBNationSnapshot::nationId));
        return combined;
    }

    private static List<DBNationSnapshot> sortedSnapshots(Collection<DBNationSnapshot> snapshots) {
        return snapshots.stream()
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static int[] ids(Collection<DBNationSnapshot> snapshots) {
        return snapshots.stream().mapToInt(DBNationSnapshot::nationId).sorted().toArray();
    }

    private static int pairCount(Map<Integer, List<Integer>> assignment) {
        int pairCount = 0;
        for (List<Integer> defenders : assignment.values()) {
            pairCount += defenders.size();
        }
        return pairCount;
    }

    private static Map<Integer, Integer> capByNationId(List<DBNationSnapshot> nations, int defaultCap) {
        return nations.stream().collect(Collectors.toMap(DBNationSnapshot::nationId, ignored -> defaultCap));
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

    private static List<String> csvValues(String configured) {
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static Set<DBNation> resolveAllianceMembers(NationDB nationDb, List<String> tokens, String optionName) {
        Set<Integer> allianceIds = new LinkedHashSet<>();
        for (String token : tokens) {
            DBAlliance alliance = resolveAlliance(nationDb, token);
            if (alliance == null) {
                throw new IllegalArgumentException("Unknown alliance in " + optionName + ": " + token);
            }
            allianceIds.add(alliance.getAlliance_id());
        }
        Set<DBNation> nations = new LinkedHashSet<>();
        for (DBNation nation : new link.locutus.discord.pnw.AllianceList(allianceIds).getNations(nationDb, false, 0, true)) {
            nations.add(nation);
        }
        return nations;
    }

    private static DBAlliance resolveAlliance(NationDB nationDb, String token) {
        if (token.chars().allMatch(Character::isDigit)) {
            return nationDb.getAlliance(Integer.parseInt(token));
        }
        DBAlliance direct = nationDb.getAllianceByName(token);
        if (direct != null) {
            return direct;
        }
        String normalized = token.replace('_', ' ').replace('+', ' ').trim();
        if (!normalized.equals(token)) {
            return nationDb.getAllianceByName(normalized);
        }
        return null;
    }

    private static List<DBNationSnapshot> liveSnapshots(Collection<DBNation> nations, boolean clearVm, boolean clearBeige) {
        return DBNationSnapshot.of(nations, Map.of()).stream()
                .map(snapshot -> {
                    if (!clearVm && !clearBeige) {
                        return snapshot;
                    }
                    DBNationSnapshot.Builder builder = snapshot.toBuilder();
                    if (clearVm) {
                        builder.vmTurns(0);
                    }
                    if (clearBeige) {
                        builder.beigeTurns(0);
                    }
                    return builder.build();
                })
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static NationDB loadNationDb() throws Exception {
        return new NationDB().load();
    }

    private record LiveDatabases(
            Field instanceField,
            Locutus previousInstance,
            NationDB nationDb,
            WarDB warDb
    ) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            Exception failure = null;
            try {
                if (warDb != null) {
                    warDb.close();
                }
            } catch (Exception e) {
                failure = e;
            }
            try {
                if (nationDb != null) {
                    nationDb.close();
                }
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
            }
            try {
                instanceField.set(null, previousInstance);
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static LiveDatabases loadLiveDatabases() throws Exception {
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);
        NationDB[] nationHolder = new NationDB[1];
        WarDB[] warHolder = new WarDB[1];
        instanceField.set(null, fakeLocutus(nationHolder, warHolder));
        try {
            NationDB nationDb = loadNationDb();
            nationHolder[0] = nationDb;
            WarDB warDb = new WarDB().load();
            warHolder[0] = warDb;
            return new LiveDatabases(instanceField, previousInstance, nationDb, warDb);
        } catch (Exception e) {
            instanceField.set(null, previousInstance);
            throw e;
        }
    }

    private static Locutus fakeLocutus(NationDB[] nationHolder, WarDB[] warHolder) throws Exception {
        Locutus locutus = (Locutus) allocateWithoutConstructor(Locutus.class);
        Object loader = Proxy.newProxyInstance(
                ILoader.class.getClassLoader(),
                new Class<?>[]{ILoader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNationDB", "getCachedNationDB" -> nationHolder[0];
                    case "getWarDB" -> warHolder[0];
                    case "resolveFully" -> proxy;
                    case "printStacktrace" -> "";
                    default -> defaultValue(method.getReturnType());
                });
        Field loaderField = Locutus.class.getDeclaredField("loader");
        loaderField.setAccessible(true);
        loaderField.set(locutus, loader);
        return locutus;
    }

    private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return unsafe.allocateInstance(type);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static String liveLabel(List<String> attackerTokens, List<String> defenderTokens) {
        return sanitizeLabel(String.join("+", attackerTokens)) + "_vs_" + sanitizeLabel(String.join("+", defenderTokens));
    }

    private static String sanitizeLabel(String value) {
        return value.replace(' ', '_').replace(',', '+');
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static com.sun.management.ThreadMXBean threadMxBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean threadBean)) {
            return null;
        }
        if (!threadBean.isThreadAllocatedMemorySupported()) {
            return null;
        }
        if (!threadBean.isThreadAllocatedMemoryEnabled()) {
            threadBean.setThreadAllocatedMemoryEnabled(true);
        }
        return threadBean;
    }

    private static long currentThreadAllocatedBytes() {
        if (THREAD_MX_BEAN == null) {
            return -1L;
        }
        return THREAD_MX_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long deltaOrUnsupported(long startValue, long endValue) {
        if (startValue < 0L || endValue < 0L) {
            return -1L;
        }
        return endValue - startValue;
    }
}