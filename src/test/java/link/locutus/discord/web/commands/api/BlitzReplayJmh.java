package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.Turn1DeclarePolicy;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.LaterDeclarationScope;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.PlannerReplayProjector;
import link.locutus.discord.sim.planners.SidePlannerSettings;
import link.locutus.discord.sim.planners.SidePolicy;
import link.locutus.discord.sim.planners.providers.CompositeBlitzActivityModel;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzMilitaryRules;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayTrace;
import link.locutus.discord.web.commands.binding.value_types.BlitzSideMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 2)
@Fork(value = 1, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class BlitzReplayJmh {
    private static final String DEFAULT_ATTACKER_ALLIANCES = "Singularity";
    private static final String DEFAULT_DEFENDER_ALLIANCES = "The Knights Radiant";
    private static final Method BUILD_CONTEXT = declaredMethod(
            SimEndpoints.class,
            "buildBlitzPlanContext",
            WarDB.class,
            BlitzPlanRequest.class,
            Collection.class,
            Collection.class
    );

    @Benchmark
    public void endpointRunNoTrace(LiveReplayState state, Blackhole bh) {
        BlitzPlanResponse response = SimEndpoints.runBlitzPlan(state.warDb, state.noTraceRequest, state.attackers, state.defenders);
        bh.consume(response.assignmentPairs().length);
        bh.consume(response.objective());
    }

    @Benchmark
    public void endpointRunWithTrace(LiveReplayState state, Blackhole bh) {
        BlitzPlanResponse response = SimEndpoints.runBlitzPlan(state.warDb, state.traceRequest, state.attackers, state.defenders);
        BlitzReplayTrace trace = response.trace();
        bh.consume(response.assignmentPairs().length);
        bh.consume(trace == null ? -1 : trace.turnMetaLanes().length);
    }

    @Benchmark
    public void replayCaptureOpeningOnly(LiveReplayState state, Blackhole bh) {
        BlitzReplayTrace trace = PlannerReplayProjector.capture(
                state.tuning,
                state.overrides,
                state.combinedSnapshots,
                state.attackerNationIds,
                state.defenderNationIds,
                state.assignment.assignment(),
                state.assignment.initialWarTypeOrdinalsByPair(),
                List.of(),
                state.participantIds,
                state.existingWarPairs,
                state.currentTurn,
                state.horizonTurns
        );
        bh.consume(trace.turnMetaLanes().length);
    }

    @Benchmark
    public void replayCaptureOpposingSideOnly(LiveReplayState state, Blackhole bh) {
        BlitzReplayTrace trace = PlannerReplayProjector.capture(
                state.tuning,
                state.overrides,
                state.combinedSnapshots,
                state.attackerNationIds,
                state.defenderNationIds,
                state.assignment.assignment(),
                state.assignment.initialWarTypeOrdinalsByPair(),
                List.of(defenderPopulationLaterDeclarationScope(state)),
                state.participantIds,
                state.existingWarPairs,
                state.currentTurn,
                state.horizonTurns
        );
        bh.consume(trace.turnMetaLanes().length);
    }

    @Benchmark
    public void replayCaptureAttackerPopulationOnly(LiveReplayState state, Blackhole bh) {
        BlitzReplayTrace trace = PlannerReplayProjector.capture(
                state.tuning,
                state.overrides,
                state.combinedSnapshots,
                state.attackerNationIds,
                state.defenderNationIds,
                state.assignment.assignment(),
                state.assignment.initialWarTypeOrdinalsByPair(),
                List.of(attackerPopulationLaterDeclarationScope(state)),
                state.participantIds,
                state.existingWarPairs,
                state.currentTurn,
                state.horizonTurns
        );
        bh.consume(trace.turnMetaLanes().length);
    }

    @Benchmark
    public void replayCaptureFullAutonomous(LiveReplayState state, Blackhole bh) {
        BlitzReplayTrace trace = PlannerReplayProjector.capture(
                state.tuning,
                state.overrides,
                state.combinedSnapshots,
                state.attackerNationIds,
                state.defenderNationIds,
                state.assignment.assignment(),
                state.assignment.initialWarTypeOrdinalsByPair(),
                List.of(defenderPopulationLaterDeclarationScope(state), attackerPopulationLaterDeclarationScope(state)),
                state.participantIds,
                state.existingWarPairs,
                state.currentTurn,
                state.horizonTurns
        );
        bh.consume(trace.turnMetaLanes().length);
    }

    public static void main(String[] args) throws RunnerException, IOException {
        OptionsBuilder options = new OptionsBuilder();
        options.include("^" + Pattern.quote(BlitzReplayJmh.class.getName()) + ".*");
        options.shouldFailOnError(true);

        applyIntegerOption(System.getProperty("blitzReplayJmhWarmupIterations"), options::warmupIterations);
        applyIntegerOption(System.getProperty("blitzReplayJmhMeasurementIterations"), options::measurementIterations);
        applyIntegerOption(System.getProperty("blitzReplayJmhForks"), options::forks);

        String resultFile = System.getProperty("blitzReplayJmhResultFile");
        if (resultFile != null && !resultFile.isBlank()) {
            Path output = Path.of(resultFile).toAbsolutePath();
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            options.result(output.toString());
        }

        String resultFormat = System.getProperty("blitzReplayJmhResultFormat");
        if (resultFormat != null && !resultFormat.isBlank()) {
            options.resultFormat(ResultFormatType.valueOf(resultFormat.toUpperCase(Locale.ROOT)));
        }

        new Runner(options.build()).run();
    }

    @State(Scope.Benchmark)
    public static class LiveReplayState {
        private LiveDatabases databases;
        private Set<DBNation> attackers;
        private Set<DBNation> defenders;
        private WarDB warDb;
        private BlitzPlanRequest noTraceRequest;
        private BlitzPlanRequest traceRequest;
        private SimTuning tuning;
        private OverrideSet overrides;
        private int[] attackerNationIds;
        private int[] defenderNationIds;
        private int[] participantIds;
        private int[] existingWarPairs;
        private int currentTurn;
        private int horizonTurns;
        private List<DBNationSnapshot> attackerSnapshots;
        private List<DBNationSnapshot> defenderSnapshots;
        private List<DBNationSnapshot> combinedSnapshots;
        private List<DBNationSnapshot> opposingSideDeclarers;
        private List<DBNationSnapshot> opposingSideTargets;
        private List<DBNationSnapshot> openingSideDeclarers;
        private List<DBNationSnapshot> openingSideTargets;
        private BlitzAssignment assignment;
        private SidePolicy opposingSideDeclarerPolicy;
        private SidePolicy opposingSideTargetPolicy;
        private SidePolicy openingSideDeclarerPolicy;
        private SidePolicy openingSideTargetPolicy;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
            Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
            Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS = false;
            Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS = false;

            databases = loadLiveDatabases();
            warDb = databases.warDb();
            attackers = resolveAllianceMembers(databases.nationDb(), csvValues(System.getProperty("blitzReplayJmhAttackers", DEFAULT_ATTACKER_ALLIANCES)), "blitzReplayJmhAttackers");
            defenders = resolveAllianceMembers(databases.nationDb(), csvValues(System.getProperty("blitzReplayJmhDefenders", DEFAULT_DEFENDER_ALLIANCES)), "blitzReplayJmhDefenders");
            noTraceRequest = buildRequest(attackers, defenders, false);
            traceRequest = buildRequest(attackers, defenders, true);

            Object context = BUILD_CONTEXT.invoke(null, warDb, noTraceRequest, attackers, defenders);
            attackerSnapshots = castList(invokeAccessor(context, "attackerSnapshots"));
            defenderSnapshots = castList(invokeAccessor(context, "defenderSnapshots"));
            CompositeBlitzActivityModel activityModel = (CompositeBlitzActivityModel) invokeAccessor(context, "activityModel");
            overrides = (OverrideSet) invokeAccessor(context, "overrides");
            attackerNationIds = (int[]) invokeAccessor(context, "attackerNationIds");
            defenderNationIds = (int[]) invokeAccessor(context, "defenderNationIds");
            participantIds = (int[]) invokeAccessor(context, "participantIds");
            existingWarPairs = (int[]) invokeAccessor(context, "existingWarPairs");
            currentTurn = (Integer) invokeAccessor(context, "currentTurn");
            horizonTurns = (Integer) invokeAccessor(context, "horizonTurns");

            tuning = tuningForRequest(noTraceRequest);
            StrategicObjective objective = objectiveForRequest(noTraceRequest);
            BlitzPlanner planner = new BlitzPlanner(tuning, link.locutus.discord.sim.planners.TreatyProvider.NONE, overrides, objective, activityModel.snapshotProvider());
            assignment = planner.assign(
                    attackerSnapshots,
                    defenderSnapshots,
                    SidePolicy.legacy("acting", objective),
                    SidePolicy.legacyPassive("nonActing", objective),
                    currentTurn,
                    List.of(),
                    horizonTurns
            );

            combinedSnapshots = new ArrayList<>(attackerSnapshots.size() + defenderSnapshots.size());
            combinedSnapshots.addAll(attackerSnapshots);
            combinedSnapshots.addAll(defenderSnapshots);
            combinedSnapshots = List.copyOf(combinedSnapshots);
            opposingSideDeclarers = List.copyOf(defenderSnapshots);
            opposingSideTargets = List.copyOf(attackerSnapshots);
            openingSideDeclarers = List.copyOf(attackerSnapshots);
            openingSideTargets = List.copyOf(defenderSnapshots);
            opposingSideDeclarerPolicy = SidePolicy.legacy("laterDeclarerOpposingSide", objective);
            opposingSideTargetPolicy = SidePolicy.legacyPassive("laterTargetOpposingSide", objective);
            openingSideDeclarerPolicy = SidePolicy.legacy("laterDeclarerOpeningSide", objective);
            openingSideTargetPolicy = SidePolicy.legacyPassive("laterTargetOpeningSide", objective);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            if (databases != null) {
                databases.close();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<DBNationSnapshot> castList(Object value) {
        return (List<DBNationSnapshot>) value;
    }

    private static LaterDeclarationScope defenderPopulationLaterDeclarationScope(LiveReplayState state) {
        return new LaterDeclarationScope(
                ids(state.opposingSideDeclarers),
                ids(state.opposingSideTargets),
                state.opposingSideDeclarerPolicy,
                state.opposingSideTargetPolicy
        );
    }

    private static LaterDeclarationScope attackerPopulationLaterDeclarationScope(LiveReplayState state) {
        return new LaterDeclarationScope(
                ids(state.openingSideDeclarers),
                ids(state.openingSideTargets),
                state.openingSideDeclarerPolicy,
                state.openingSideTargetPolicy
        );
    }

    private static List<Integer> ids(List<DBNationSnapshot> snapshots) {
        return snapshots.stream()
                .mapToInt(DBNationSnapshot::nationId)
                .sorted()
                .boxed()
                .toList();
    }

    private static BlitzPlanRequest buildRequest(Collection<DBNation> attackers, Collection<DBNation> defenders, boolean captureTrace) {
        List<BlitzDraftEdit> edits = new ArrayList<>(attackers.size() + defenders.size());
        for (DBNation nation : combined(attackers, defenders)) {
            edits.add(routeOverrideEdit(nation));
        }
        return new BlitzPlanRequest(
                ids(attackers),
                ids(defenders),
                edits.toArray(BlitzDraftEdit[]::new),
                new BlitzPlannedWar[0],
                BlitzSideMode.ATTACKERS_ONLY.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(),
                Integer.getInteger("blitzReplayJmhObjectiveOrdinal", 0),
                null,
                Integer.getInteger("blitzReplayJmhHorizonTurns", 72),
                false,
                true,
                1L,
                null,
                new int[0],
                true,
                captureTrace
        );
    }

    private static BlitzDraftEdit routeOverrideEdit(DBNation nation) {
        return new BlitzDraftEdit(
                nation.getNation_id(),
                Boolean.TRUE,
                null,
                Integer.getInteger("blitzReplayJmhAvgInfraCents", 250_000),
                unitCountsForMmr(nation, System.getProperty("blitzReplayJmhUnitMmr", "5553")),
                null,
                0L,
                0L,
                0,
                0,
                null,
                Boolean.TRUE,
                Boolean.TRUE
        );
    }

    private static int[] unitCountsForMmr(DBNation nation, String mmrValue) {
        if (mmrValue == null || mmrValue.isBlank()) {
            return null;
        }
        BlitzMilitaryRules rules = BlitzMilitaryRules.instance();
        int[] units = new int[MilitaryUnit.values.length];
        for (MilitaryUnit unit : MilitaryUnit.values) {
            units[unit.ordinal()] = nation.getUnits(unit);
        }
        int[] slots = rules.mmrUnitOrdinals();
        int[] parts = mmrParts(mmrValue, slots.length);
        int researchBits = nation.getResearchBits(null);
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            int unitOrdinal = slots[slotIndex];
            int maxMmr = Math.max(1, rules.mmrMaxByUnitOrdinal()[unitOrdinal]);
            int cap = unitCapForMmr(nation.getCities(), researchBits, unitOrdinal, rules);
            units[unitOrdinal] = (int) Math.round(cap * (parts[slotIndex] / (double) maxMmr));
        }
        return units;
    }

    private static int unitCapForMmr(int cities, int researchBits, int unitOrdinal, BlitzMilitaryRules rules) {
        int maxMmr = rules.mmrMaxByUnitOrdinal()[unitOrdinal];
        int capPerBuilding = rules.capacityPerBuildingByUnitOrdinal()[unitOrdinal];
        return (maxMmr * capPerBuilding * cities) + unitCapacityResearchBonus(unitOrdinal, researchBits, rules);
    }

    private static int unitCapacityResearchBonus(int unitOrdinal, int researchBits, BlitzMilitaryRules rules) {
        int researchOrdinal = rules.capacityResearchOrdinalByUnitOrdinal()[unitOrdinal];
        if (researchOrdinal < 0) {
            return 0;
        }
        int bonus = rules.capacityResearchBonusByUnitOrdinal()[unitOrdinal];
        return bonus * readResearchBits(researchBits, researchOrdinal, rules.bitsPerResearchSlot());
    }

    private static int readResearchBits(int bits, int researchOrdinal, int bitsPerSlot) {
        int shift = researchOrdinal * bitsPerSlot;
        if (shift < 0 || shift >= Integer.SIZE) {
            return 0;
        }
        return (bits >>> shift) & ((1 << bitsPerSlot) - 1);
    }

    private static int[] mmrParts(String value, int slots) {
        int[] parts = new int[slots];
        String normalized = value.trim();
        for (int index = 0; index < slots; index++) {
            if (index >= normalized.length()) {
                parts[index] = 0;
                continue;
            }
            char c = normalized.charAt(index);
            parts[index] = Character.isDigit(c) ? Character.digit(c, 10) : 0;
        }
        return parts;
    }

    private static List<DBNation> combined(Collection<DBNation> attackers, Collection<DBNation> defenders) {
        ArrayList<DBNation> result = new ArrayList<>(attackers.size() + defenders.size());
        result.addAll(attackers);
        result.addAll(defenders);
        result.sort(Comparator.comparingInt(DBNation::getNation_id));
        return result;
    }

    private static String ids(Collection<DBNation> nations) {
        return nations.stream()
                .map(DBNation::getNation_id)
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static List<String> csvValues(String configured) {
        return java.util.Arrays.stream(configured.split(","))
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

    private static StrategicObjective objectiveForRequest(BlitzPlanRequest request) {
        Integer ordinal = request.objectiveOrdinal();
        if (ordinal == null) {
            return BlitzObjective.defaultObjective().objective();
        }
        return BlitzObjective.values()[ordinal].objective();
    }

    private static SimTuning tuningForRequest(BlitzPlanRequest request) {
        SidePlannerSettings plannerSettings = SidePolicy.legacy(objectiveForRequest(request)).planner();
        SimTuning defaults = SimTuning.defaults();
        return new SimTuning(
                defaults.intraTurnPasses(),
                turn1DeclarePolicyForRequest(request),
                defaults.wartimeActivityUplift(),
                defaults.activityActThreshold(),
                defaults.policyCooldownTurns(),
                defaults.localSearchBudgetMs(),
                defaults.localSearchMaxIterations(),
                plannerSettings.candidatesPerAttacker(),
                defaults.beigeTurnsOnDefeat(),
                defaults.stateResolutionMode(),
                request.stochasticSeed(),
                defaults.stochasticSampleCount()
        );
    }

    private static Turn1DeclarePolicy turn1DeclarePolicyForRequest(BlitzPlanRequest request) {
        Integer ordinal = request.turn1DeclarePolicyOrdinal();
        if (ordinal == null) {
            return SimTuning.DEFAULT_TURN1_DECLARE_POLICY;
        }
        return Turn1DeclarePolicy.values()[ordinal];
    }

    private static Object invokeAccessor(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Method declaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to bind method " + owner.getName() + "." + name, e);
        }
    }

    private static void applyIntegerOption(String value, java.util.function.IntConsumer consumer) {
        if (value == null || value.isBlank()) {
            return;
        }
        consumer.accept(Integer.parseInt(value));
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
            NationDB nationDb = new NationDB().load();
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
                }
        );
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
}