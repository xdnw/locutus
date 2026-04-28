package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.handlers.ActiveWarHandler;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
class StatEndpointsContractTest {
    @Test
    void statEndpointsAreRegisteredInStandalonePageHandler() {
        PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
        CommandGroup api = (CommandGroup) handler.getCommands().get("api");

        CommandCallable table = api.get("table");
        CommandCallable warsInvolving = api.get("warsInvolving");
        CommandCallable warsBetween = api.get("warsBetween");

        assertNotNull(table);
        assertNotNull(warsInvolving);
        assertNotNull(warsBetween);
    }

    @Test
    void warsInvolvingRendersOnlyMatchingActiveWarsSortedByWarId() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation sideNation = nation(101, "Side Nation");
            DBNation otherIncluded = nation(202, "Other Included");
            DBNation unrelated = nation(303, "Unrelated");
            seedNation(nationDb, sideNation);
            seedNation(nationDb, otherIncluded);
            seedNation(nationDb, unrelated);

            DBWar lowest = activeWar(9001, 101, 404);
            DBWar middle = activeWar(9002, 505, 202);
            DBWar highest = activeWar(9003, 303, 606);
            addActiveWar(warDb, lowest);
            addActiveWar(warDb, middle);
            addActiveWar(warDb, highest);

            List<DBWar> wars = StatEndpoints.warsInvolving(warDb,
                    Set.<NationOrAlliance>of(sideNation, otherIncluded),
                    null, null,
                    false,
                    null, null,
                    false, false);

            assertEquals(List.of(9001, 9002), wars.stream().map(DBWar::getWarId).toList());
        });
    }

    @Test
    void warsBetweenRendersOnlyCrossPopulationActiveWarsSortedByWarId() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation sideA = nation(101, "Side A");
            DBNation sideB = nation(202, "Side B");
            DBNation sideBTwo = nation(203, "Side B2");
            seedNation(nationDb, sideA);
            seedNation(nationDb, sideB);
            seedNation(nationDb, sideBTwo);

            DBWar crossOne = activeWar(9001, 101, 202);
            DBWar sameSide = activeWar(9002, 202, 203);
            DBWar crossTwo = activeWar(9003, 203, 101);
            addActiveWar(warDb, crossOne);
            addActiveWar(warDb, sameSide);
            addActiveWar(warDb, crossTwo);

            List<DBWar> wars = StatEndpoints.warsBetween(warDb,
                    Set.<NationOrAlliance>of(sideA),
                    Set.<NationOrAlliance>of(sideB, sideBTwo),
                    null, null,
                    false,
                    null, null,
                    false, false);

            assertEquals(List.of(9001, 9003), wars.stream().map(DBWar::getWarId).toList());
        });
    }

    @Test
    void warsInvolvingSupportsAllianceHistoryFiltersAndOffensiveDirection() throws Exception {
        withFixture((nationDb, warDb) -> {
            long now = System.currentTimeMillis();
            DBNation attacker = nation(101, "Attacker", 1);
            DBNation defender = nation(202, "Defender", 2);
            DBNation counter = nation(203, "Counter", 2);
            seedNation(nationDb, attacker);
            seedNation(nationDb, defender);
            seedNation(nationDb, counter);

            DBWar matching = war(9101, 101, 202, 1, 2, WarType.RAID, WarStatus.ATTACKER_VICTORY, now - TimeUnit.HOURS.toMillis(2));
            DBWar defensive = war(9102, 202, 101, 2, 1, WarType.RAID, WarStatus.ATTACKER_VICTORY, now - TimeUnit.HOURS.toMillis(2));
            DBWar wrongType = war(9103, 101, 203, 1, 2, WarType.ORD, WarStatus.ATTACKER_VICTORY, now - TimeUnit.HOURS.toMillis(2));
            DBWar wrongStatus = war(9104, 101, 203, 1, 2, WarType.RAID, WarStatus.PEACE, now - TimeUnit.HOURS.toMillis(2));
            DBWar outsideWindow = war(9105, 101, 203, 1, 2, WarType.RAID, WarStatus.ATTACKER_VICTORY, now - TimeUnit.DAYS.toMillis(4));
            addWar(warDb, matching);
            addWar(warDb, defensive);
            addWar(warDb, wrongType);
            addWar(warDb, wrongStatus);
            addWar(warDb, outsideWindow);

            List<DBWar> wars = StatEndpoints.warsInvolving(warDb,
                    Set.<NationOrAlliance>of(DBAlliance.getOrCreate(1)),
                    now - TimeUnit.DAYS.toMillis(1), now,
                    true,
                    Set.of(WarType.RAID), Set.of(WarStatus.ATTACKER_VICTORY),
                    true, false);

            assertEquals(List.of(9101), wars.stream().map(DBWar::getWarId).toList());
        });
    }

    @Test
    void warsBetweenSupportsAllianceDirectionFilters() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation sideA = nation(101, "Side A", 1);
            DBNation sideATwo = nation(102, "Side A2", 1);
            DBNation sideB = nation(201, "Side B", 2);
            seedNation(nationDb, sideA);
            seedNation(nationDb, sideATwo);
            seedNation(nationDb, sideB);

            DBWar sideAOffensive = activeWar(9201, 101, 201, 1, 2);
            DBWar sideBOffensive = activeWar(9202, 201, 101, 2, 1);
            DBWar sameSide = activeWar(9203, 101, 102, 1, 1);
            addActiveWar(warDb, sideAOffensive);
            addActiveWar(warDb, sideBOffensive);
            addActiveWar(warDb, sameSide);

            List<DBWar> wars = StatEndpoints.warsBetween(warDb,
                    Set.<NationOrAlliance>of(DBAlliance.getOrCreate(1)),
                    Set.<NationOrAlliance>of(DBAlliance.getOrCreate(2)),
                    null, null,
                    false,
                    null, null,
                    false, true);

            assertEquals(List.of(9202), wars.stream().map(DBWar::getWarId).toList());
        });
    }

    @Test
    void warsRejectContradictoryDirectionFlags() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation sideNation = nation(101, "Side Nation");
            seedNation(nationDb, sideNation);

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                    StatEndpoints.warsInvolving(warDb,
                            Set.<NationOrAlliance>of(sideNation),
                            null, null,
                            false,
                            null, null,
                            true, true));

            assertEquals("Cannot combine `onlyOffensiveWars` and `onlyDefensiveWars`", error.getMessage());
        });
    }

    private static void withFixture(FixtureBody body) throws Exception {
        Path tempDir = Files.createTempDirectory("stat-endpoints-contract-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);
        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();
        try (NationDB nationDb = new NationDB(); WarDB warDb = new WarDB("stat-endpoints-contract-" + System.nanoTime())) {
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

    @SuppressWarnings("unchecked")
    private static void seedNation(NationDB nationDb, DBNation nation) throws Exception {
        Field nationsByIdField = NationDB.class.getDeclaredField("nationsById");
        nationsByIdField.setAccessible(true);
        Map<Integer, DBNation> nationsById = (Map<Integer, DBNation>) nationsByIdField.get(nationDb);
        nationsById.put(nation.getNation_id(), nation);
    }

    private static DBNation nation(int id, String name) {
        return nation(id, name, id / 100);
    }

    private static DBNation nation(int id, String name, int allianceId) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNation_id(id);
        nation.setNation(name);
        nation.setAlliance_id(allianceId);
        nation.setScore(1_000d);
        nation.setCities(10);
        nation.setSoldiers(1_000);
        nation.setLastActive(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        nation.setDate(System.currentTimeMillis());
        return nation;
    }

    private static DBWar activeWar(int warId, int attackerId, int defenderId) {
        return activeWar(warId, attackerId, defenderId, Math.max(1, attackerId / 100), Math.max(1, defenderId / 100));
    }

    private static DBWar activeWar(int warId, int attackerId, int defenderId, int attackerAllianceId, int defenderAllianceId) {
        return new DBWar(
                warId,
                attackerId,
                defenderId,
                attackerAllianceId,
                defenderAllianceId,
                false,
                false,
                WarType.ORD,
                WarStatus.ACTIVE,
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
                10,
                10,
                0
        );
    }

    private static DBWar war(int warId, int attackerId, int defenderId,
                             int attackerAllianceId, int defenderAllianceId,
                             WarType warType, WarStatus status, long date) {
        return new DBWar(
                warId,
                attackerId,
                defenderId,
                attackerAllianceId,
                defenderAllianceId,
                false,
                false,
                warType,
                status,
                date,
                10,
                10,
                0
        );
    }

    private static void addWar(WarDB warDb, DBWar war) throws Exception {
        warDb.saveWars(List.of(war), true);
    }

    private static void addActiveWar(WarDB warDb, DBWar war) throws Exception {
        addWar(warDb, war);
        Field activeWarsField = WarDB.class.getDeclaredField("activeWars");
        activeWarsField.setAccessible(true);
        ActiveWarHandler activeWars = (ActiveWarHandler) activeWarsField.get(warDb);
        activeWars.addActiveWar(war);
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
