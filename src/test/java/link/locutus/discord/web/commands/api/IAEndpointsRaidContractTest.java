package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.war.RaidCommand;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.web.commands.binding.value_types.WebTarget;
import link.locutus.discord.web.commands.binding.value_types.WebTargets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Isolated
class IAEndpointsRaidContractTest {
    @Test
    void raidEndpointMatchesSharedRaidScorer() throws Exception {
        Path tempDir = Files.createTempDirectory("ia-raid-contract-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);

        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();

        try (NationDB nationDb = new NationDB(); WarDB warDb = new WarDB("ia-endpoint-raid-contract-" + System.nanoTime())) {
            DBNation attacker = nation(1, "Attacker", 1_000d, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
            DBNation defender = nation(2, "Defender", 1_000d, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8));

            seedNation(nationDb, attacker);
            seedNation(nationDb, defender);
            nationDb.saveLoot(List.of(LootEntry.forNation(defender.getNation_id(), System.currentTimeMillis(), loot(1_000_000d), NationLootType.ESTIMATE)), null);

            instanceField.set(null, fakeLocutus(nationDb, warDb));

            IAEndpoints endpoints = new IAEndpoints();
            ObjectLinkedOpenHashSet<DBNation> endpointCandidates = new ObjectLinkedOpenHashSet<>();
            endpointCandidates.add(defender);
            WebTargets endpointResult = endpoints.raid(null, attacker, null, attacker, endpointCandidates,
                    false, 0, 0, false, TimeUnit.DAYS.toMillis(7), -1_000_000d, 8);

            ObjectLinkedOpenHashSet<DBNation> scorerCandidates = new ObjectLinkedOpenHashSet<>();
            scorerCandidates.add(defender);
            List<Map.Entry<DBNation, Map.Entry<Double, Double>>> scorerResult = RaidCommand.getNations(
                    null,
                    attacker,
                    scorerCandidates,
                    false,
                    0,
                    -1,
                    false,
                    true,
                    Set.of(),
                    false,
                    false,
                    TimeUnit.DAYS.toMinutes(7),
                    attacker.getScore(),
                    -1_000_000d,
                    0,
                    false,
                    false,
                    8);

            assertEquals(scorerResult.size(), endpointResult.targets.size());
            if (!scorerResult.isEmpty()) {
                WebTarget webTarget = endpointResult.targets.get(0);
                Map.Entry<DBNation, Map.Entry<Double, Double>> scorerTarget = scorerResult.get(0);

                assertEquals(scorerTarget.getKey().getNation_id(), webTarget.id);
                assertEquals(scorerTarget.getValue().getKey(), webTarget.expected, 0.0001d);
                assertEquals(scorerTarget.getValue().getValue(), webTarget.actual, 0.0001d);
            }
            assertEquals(attacker.getNation_id(), endpointResult.self.id);
        } finally {
            instanceField.set(null, previousInstance);
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = previousDirectory;
            deleteRecursively(tempDir);
        }
    }

    private static DBNation nation(int id, String name, double score, long lastActiveMs) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNation_id(id);
        nation.setNation(name);
        nation.setAlliance_id(0);
        nation.setScore(score);
        nation.setCities(0);
        nation.setSoldiers(1);
        nation.setTanks(0);
        nation.setAircraft(0);
        nation.setShips(0);
        nation.setMissiles(0);
        nation.setNukes(0);
        nation.setSpies(0, null);
        nation.setLastActive(lastActiveMs);
        nation.setDate(System.currentTimeMillis());
        return nation;
    }

    private static double[] loot(double money) {
        double[] loot = ResourceType.getBuffer();
        loot[ResourceType.MONEY.ordinal()] = money;
        return loot;
    }

    @SuppressWarnings("unchecked")
    private static void seedNation(NationDB nationDb, DBNation nation) throws Exception {
        Field nationsByIdField = NationDB.class.getDeclaredField("nationsById");
        nationsByIdField.setAccessible(true);
        Map<Integer, DBNation> nationsById = (Map<Integer, DBNation>) nationsByIdField.get(nationDb);
        nationsById.put(nation.getNation_id(), nation);
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

    private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, type);
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}