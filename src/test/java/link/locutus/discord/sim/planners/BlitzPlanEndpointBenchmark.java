package link.locutus.discord.sim.planners;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.api.SimEndpoints;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzMilitaryRules;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzSideMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Endpoint-shaped benchmark for the frontend blitz route.
 *
 * <p>This intentionally calls the same {@link SimEndpoints#runBlitzPlan} path used by the
 * command endpoint after selector binding, and can synthesize the route override edits produced
 * by the React page for params such as {@code forceActive=true}, {@code clearBeige=true},
 * {@code clearVM=true}, {@code avgInfra=2500}, and {@code unitMmr=5553}.</p>
 */
public final class BlitzPlanEndpointBenchmark {
    private static final String DEFAULT_ATTACKER_ALLIANCES = "Singularity";
    private static final String DEFAULT_DEFENDER_ALLIANCES = "The Knights Radiant";

    private BlitzPlanEndpointBenchmark() {
    }

    public static void main(String[] args) {
        Config config = Config.parse(args);
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS = false;

        try (LiveDatabases databases = loadLiveDatabases()) {
            Set<DBNation> attackers = resolveAllianceMembers(databases.nationDb(), csvValues(config.attackerAlliances()), "attackerAlliances");
            Set<DBNation> defenders = resolveAllianceMembers(databases.nationDb(), csvValues(config.defenderAlliances()), "defenderAlliances");
            BlitzPlanRequest request = request(config, attackers, defenders);

            System.out.println("phase,attackers,defenders,edits,horizon,captureTrace,responseJsonBytes,responseGzipBytes,elapsedMs");
            PlannerProfiler.Session session = new PlannerProfiler.Session();
            long runStart = System.nanoTime();
            BlitzPlanResponse response = PlannerProfiler.withSession(session, () -> invokeRunBlitzPlan(databases.warDb(), request, attackers, defenders));
            long runElapsed = System.nanoTime() - runStart;

            long jsonStart = System.nanoTime();
            byte[] json = WebUtil.GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
            long jsonElapsed = System.nanoTime() - jsonStart;
            long gzipBytes = gzipLength(json);

            System.out.printf(Locale.ROOT,
                    "run,%d,%d,%d,%d,%s,%d,%d,%.3f%n",
                    attackers.size(),
                    defenders.size(),
                    request.edits().length,
                    request.horizonTurns(),
                    request.captureTrace(),
                    json.length,
                    gzipBytes,
                    runElapsed / 1_000_000.0d);
            System.out.printf(Locale.ROOT,
                    "serialize,%d,%d,%d,%d,%s,%d,%d,%.3f%n",
                    attackers.size(),
                    defenders.size(),
                    request.edits().length,
                    request.horizonTurns(),
                    request.captureTrace(),
                    json.length,
                    gzipBytes,
                    jsonElapsed / 1_000_000.0d);
            printProfile(session.snapshot());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run endpoint-shaped blitz benchmark", e);
        }
    }

    private static void printProfile(PlannerProfiler.ProfileSnapshot snapshot) {
        System.out.println("stage,scope,calls,totalMs,maxMs,counters");
        for (PlannerProfiler.Scope scope : PlannerProfiler.Scope.values()) {
            PlannerProfiler.ScopeStats stats = snapshot.stats(scope);
            if (stats.calls() == 0L) {
                continue;
            }
            String counters = stats.counters().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(";"));
            System.out.printf(Locale.ROOT,
                    "stage,%s,%d,%.3f,%.3f,%s%n",
                    scope.name(),
                    stats.calls(),
                    stats.totalMillis(),
                    stats.maxMillis(),
                    counters);
        }
    }

    private static BlitzPlanResponse invokeRunBlitzPlan(
            WarDB warDb,
            BlitzPlanRequest request,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders
    ) {
        try {
            Method method = SimEndpoints.class.getDeclaredMethod(
                    "runBlitzPlan",
                    WarDB.class,
                    BlitzPlanRequest.class,
                    Collection.class,
                    Collection.class
            );
            method.setAccessible(true);
            return (BlitzPlanResponse) method.invoke(null, warDb, request, attackers, defenders);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke SimEndpoints.runBlitzPlan", e);
        }
    }

    private static BlitzPlanRequest request(Config config, Collection<DBNation> attackers, Collection<DBNation> defenders) {
        List<BlitzDraftEdit> edits = new ArrayList<>(attackers.size() + defenders.size());
        if (config.routeOverrides()) {
            for (DBNation nation : combined(attackers, defenders)) {
                edits.add(routeOverrideEdit(nation, config));
            }
        }
        return new BlitzPlanRequest(
                ids(attackers),
                ids(defenders),
                edits.toArray(BlitzDraftEdit[]::new),
                new BlitzPlannedWar[0],
                BlitzSideMode.ATTACKERS_ONLY.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(),
                config.objectiveOrdinal(),
                null,
                config.horizonTurns(),
                config.includeExistingWars(),
                true,
                1L,
                null,
                new int[0],
                true,
                config.captureTrace()
        );
    }

    private static BlitzDraftEdit routeOverrideEdit(DBNation nation, Config config) {
        return new BlitzDraftEdit(
                nation.getNation_id(),
                config.forceActive(),
                null,
                config.avgInfraCents(),
                unitCountsForMmr(nation, config.unitMmr()),
                null,
                0L,
                0L,
                0,
                0,
                null,
                config.clearBeige(),
                config.clearVm()
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

    private static long gzipLength(byte[] jsonBytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(jsonBytes.length);
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(out, 65_536)) {
                gzipOut.write(jsonBytes);
            }
            return out.size();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to gzip benchmark payload", e);
        }
    }

    private record Config(
            String attackerAlliances,
            String defenderAlliances,
            int horizonTurns,
            boolean captureTrace,
            boolean includeExistingWars,
            boolean routeOverrides,
            Boolean forceActive,
            Boolean clearBeige,
            Boolean clearVm,
            Integer avgInfraCents,
            String unitMmr,
            Integer objectiveOrdinal
    ) {
        static Config parse(String[] args) {
            return new Config(
                    option(args, "attackerAlliances", DEFAULT_ATTACKER_ALLIANCES),
                    option(args, "defenderAlliances", DEFAULT_DEFENDER_ALLIANCES),
                    optionInt(args, "horizonTurns", 72),
                    optionBoolean(args, "captureTrace", true),
                    optionBoolean(args, "includeExistingWars", false),
                    optionBoolean(args, "routeOverrides", true),
                    optionNullableBoolean(args, "forceActive", true),
                    optionNullableBoolean(args, "clearBeige", true),
                    optionNullableBoolean(args, "clearVm", true),
                    optionInt(args, "avgInfraCents", 250_000),
                    option(args, "unitMmr", "5553"),
                    optionNullableInt(args, "objectiveOrdinal", 0)
            );
        }
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

    private static int optionInt(String[] args, String name, int defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static Integer optionNullableInt(String[] args, String name, Integer defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? defaultValue : Integer.parseInt(value);
    }

    private static boolean optionBoolean(String[] args, String name, boolean defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static Boolean optionNullableBoolean(String[] args, String name, Boolean defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? defaultValue : Boolean.parseBoolean(value);
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
}
