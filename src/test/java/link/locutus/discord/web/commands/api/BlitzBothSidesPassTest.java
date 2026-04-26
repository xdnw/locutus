package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.web.commands.binding.value_types.BlitzAssignedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzSideMode;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Isolated
class BlitzBothSidesPassTest {
    @Test
    void blitzPlanIsRegistered() {
        PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
        CommandGroup api = (CommandGroup) handler.getCommands().get("api");
        CommandCallable blitzPlan = api.get("blitzPlan");
        assertNotNull(blitzPlan);
    }

    @Test
    void forcedInactiveNationLosesDeclarePriorityInBothMode() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation nationOne = nation(101, "Nation One");
            DBNation nationTwo = nation(202, "Nation Two");

            BlitzDraftEdit[] edits = {
                    new BlitzDraftEdit(101, false, null, null, null, null, 0L, 0L, 0, 0, null)
            };

            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    request(edits),
                    List.of(nationOne),
                    List.of(nationTwo)
            );

            assertEquals(1, response.assignments().length);
            assertEquals(202, response.assignments()[0].declarerNationId());
            assertEquals(101, response.assignments()[0].targetNationId());
        });
    }

    @Test
    void identicalRequestsProduceIdenticalAssignmentsInBothMode() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation nationOne = nation(101, "Nation One");
            nationOne.setAircraft(500);
            DBNation nationTwo = nation(202, "Nation Two");
            nationTwo.setAircraft(500);

            BlitzPlanRequest request = request(new BlitzDraftEdit[0]);
            BlitzPlanResponse first = SimEndpoints.runBlitzPlan(warDb, request, List.of(nationOne), List.of(nationTwo));
            BlitzPlanResponse second = SimEndpoints.runBlitzPlan(warDb, request, List.of(nationOne), List.of(nationTwo));

            assertArrayEquals(first.assignments(), second.assignments());
        });
    }

    @Test
    void bothModeExcludesReciprocalAssignmentsForSameNationPair() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation nationOne = nation(101, "Nation One");
            nationOne.setAircraft(500);
            DBNation nationTwo = nation(202, "Nation Two");
            nationTwo.setAircraft(500);

            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    request(new BlitzDraftEdit[0]),
                    List.of(nationOne),
                    List.of(nationTwo)
            );

            assertEquals(1, response.assignments().length);
            assertFalse(containsAssignment(response.assignments(), 101, 202) && containsAssignment(response.assignments(), 202, 101));
        });
    }

    private static BlitzPlanRequest request(BlitzDraftEdit[] edits) {
        return new BlitzPlanRequest(
                "*",
                "*",
                edits,
                new BlitzPlannedWar[0],
                BlitzSideMode.BOTH.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(),
                6,
                true,
                true,
                1L,
                5,
                new int[0],
                true,
                false
        );
    }

    private static boolean containsAssignment(BlitzAssignedWar[] assignments, int declarerNationId, int targetNationId) {
        for (BlitzAssignedWar assignment : assignments) {
            if (assignment.declarerNationId() == declarerNationId && assignment.targetNationId() == targetNationId) {
                return true;
            }
        }
        return false;
    }

    private static DBNation nation(int id, String name) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNation_id(id);
        nation.setNation(name);
        nation.setAlliance_id(id / 100);
        nation.setScore(1_000d);
        nation.setCities(10);
        nation.setSoldiers(10_000);
        nation.setTanks(500);
        nation.setAircraft(200);
        nation.setShips(10);
        nation.setMissiles(0);
        nation.setNukes(0);
        nation.setSpies(0, null);
        nation.setLastActive(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        nation.setDate(System.currentTimeMillis());
        return nation;
    }

    private static void withFixture(FixtureBody body) throws Exception {
        Path tempDir = Files.createTempDirectory("blitz-both-sides-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);
        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();
        try (NationDB nationDb = new NationDB(); WarDB warDb = new WarDB("blitz-both-sides-" + System.nanoTime())) {
            instanceField.set(null, fakeLocutus(nationDb, warDb));
            body.run(nationDb, warDb);
        } finally {
            instanceField.set(null, previousInstance);
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = previousDirectory;
            deleteRecursively(tempDir);
        }
    }

    private static Locutus fakeLocutus(NationDB nationDb, WarDB warDb) throws Exception {
        Locutus locutus = (Locutus) allocateWithoutConstructor(Locutus.class);
        Object loader = Proxy.newProxyInstance(
                ILoader.class.getClassLoader(),
                new Class<?>[]{ILoader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNationDB", "getCachedNationDB" -> nationDb;
                    case "getWarDB" -> warDb;
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

    private static void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @FunctionalInterface
    private interface FixtureBody {
        void run(NationDB nationDb, WarDB warDb) throws Exception;
    }
}
