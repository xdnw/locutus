package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.war.RaidCommand;
import link.locutus.discord.commands.war.WarTargetFinder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.entities.city.SimpleDBCity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class WarEndpointsContractTest {
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

                WarEndpoints endpoints = new WarEndpoints();
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

    @Test
    void damageEndpointUsesSharedDamageFinder() throws Exception {
        Path tempDir = Files.createTempDirectory("war-damage-contract-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);

        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();

        try (NationDB nationDb = new NationDB(); WarDB warDb = new WarDB("war-endpoint-damage-contract-" + System.nanoTime())) {
            DBNation attacker = nation(1, "Attacker", 1_000d, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
            attacker.setCities(10);
            DBNation highTarget = nation(2, "HighTarget", 1_000d, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
            highTarget.setCities(4);
            highTarget.setPosition(Rank.MEMBER);
            highTarget.setColor(NationColor.BLUE);
            DBNation lowTarget = nation(3, "LowTarget", 1_000d, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
            lowTarget.setCities(4);
            lowTarget.setPosition(Rank.MEMBER);
            lowTarget.setColor(NationColor.BLUE);

            seedNation(nationDb, attacker);
            seedNation(nationDb, highTarget);
            seedNation(nationDb, lowTarget);
            seedCities(nationDb, highTarget.getNation_id(), 101, 1_200d, 102, 900d);
            seedCities(nationDb, lowTarget.getNation_id(), 201, 700d, 202, 650d);

            instanceField.set(null, fakeLocutus(nationDb, warDb));

            WarEndpoints endpoints = new WarEndpoints();
            ObjectLinkedOpenHashSet<DBNation> candidates = new ObjectLinkedOpenHashSet<>();
            candidates.add(highTarget);
            candidates.add(lowTarget);
            WebTargets endpointResult = endpoints.damage(null, attacker, null, attacker, candidates,
                    false, false, false, false, false, false, false, false, null, null, 1);

            assertEquals(1, endpointResult.targets.size());
            WebTarget webTarget = endpointResult.targets.get(0);
            assertEquals(highTarget.getNation(), webTarget.nation);
            assertTrue(webTarget.expected > 0);
            assertEquals(webTarget.expected, webTarget.actual, 0.0001d);
            assertTrue(webTarget.strength > 0);
        } finally {
            instanceField.set(null, previousInstance);
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = previousDirectory;
            deleteRecursively(tempDir);
        }
    }

    @Test
    void unprotectedEndpointUsesSharedCounterSplit() throws Exception {
        Path tempDir = Files.createTempDirectory("war-unprotected-contract-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);

        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();

        try (NationDB nationDb = new NationDB(); WarDB warDb = new WarDB("war-endpoint-unprotected-contract-" + System.nanoTime())) {
            DBNation attacker = nation(1, "Attacker", 1_000d, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
            attacker.setCities(10);
            attacker.setPosition(Rank.MEMBER);
            attacker.setColor(NationColor.BLUE);
            DBNation target = nation(2, "Target", 1_000d, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
            target.setCities(4);
            target.setPosition(Rank.MEMBER);
            target.setColor(NationColor.BLUE);

            seedNation(nationDb, attacker);
            seedNation(nationDb, target);

            instanceField.set(null, fakeLocutus(nationDb, warDb));

                ObjectLinkedOpenHashSet<DBNation> candidates = new ObjectLinkedOpenHashSet<>();
                candidates.add(target);
                WarTargetFinder.CounterChanceContext counterContext = WarTargetFinder.buildCounterChanceContext(
                    null,
                    candidates,
                    true,
                    false,
                    Set.of(attacker),
                    false,
                    false,
                    true);
                assertEquals(Math.pow(attacker.getStrength(), 3), counterContext.blitzStrength(), 0.0001d);

            WarEndpoints endpoints = new WarEndpoints();
            WebTargets endpointResult = endpoints.unprotected(null, attacker, null, null, attacker, candidates,
                    false, false, true, 1.2, 1.2, 1);

            assertEquals(1, endpointResult.targets.size());
            WebTarget webTarget = endpointResult.targets.get(0);
            assertEquals(target.getNation_id(), webTarget.id);
            assertTrue(Double.isFinite(webTarget.strength));
        } finally {
            instanceField.set(null, previousInstance);
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = previousDirectory;
            deleteRecursively(tempDir);
        }
    }

    @Test
    void treasureAndBountyEndpointsUseSharedAggregation() throws Exception {
        Path tempDir = Files.createTempDirectory("war-treasure-bounty-contract-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);

        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();

        try (NationDB nationDb = new NationDB(); WarDB warDb = new WarDB("war-endpoint-treasure-bounty-contract-" + System.nanoTime())) {
            DBNation attacker = nation(1, "Attacker", 1_000d, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
            attacker.setCities(10);
            DBNation highTarget = nation(2, "HighTarget", 1_000d, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2));
            highTarget.setCities(4);
            DBNation lowTarget = nation(3, "LowTarget", 1_000d, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2));
            lowTarget.setCities(4);

            seedNation(nationDb, attacker);
            seedNation(nationDb, highTarget);
            seedNation(nationDb, lowTarget);
            seedTreasures(nationDb, highTarget.getNation_id(), new DBTreasure(1, "Ruby", null, 7, null, highTarget.getNation_id(), System.currentTimeMillis(), 0L));
            seedTreasures(nationDb, lowTarget.getNation_id(), new DBTreasure(2, "Sapphire", null, 3, null, lowTarget.getNation_id(), System.currentTimeMillis(), 0L));
            warDb.addBounty(new DBBounty(1, System.currentTimeMillis(), highTarget.getNation_id(), 0, WarType.NUCLEAR, 1_500_000L));

            instanceField.set(null, fakeLocutus(nationDb, warDb));

            WarEndpoints endpoints = new WarEndpoints();

            WebTargets treasureResult = endpoints.treasure(null, attacker, null, attacker, false, false, 1);
            assertEquals(1, treasureResult.targets.size());
            WebTarget treasureTarget = treasureResult.targets.get(0);
            assertEquals(highTarget.getNation_id(), treasureTarget.id);
            assertEquals(7d, treasureTarget.expected, 0.0001d);
            assertEquals(7d, treasureTarget.actual, 0.0001d);
            assertEquals(1d, treasureTarget.strength, 0.0001d);

            WebTargets bountyResult = endpoints.bounty(null, attacker, null, attacker, false, false, null, 5);
            assertEquals(1, bountyResult.targets.size());
            WebTarget bountyTarget = bountyResult.targets.get(0);
            assertEquals(highTarget.getNation_id(), bountyTarget.id);
            assertEquals(1_500_000d, bountyTarget.expected, 0.0001d);
            assertEquals(1_500_000d, bountyTarget.actual, 0.0001d);
            assertEquals(1d, bountyTarget.strength, 0.0001d);
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

    private static void seedCities(NationDB nationDb, int nationId, Object... cityIdInfraPairs) throws Exception {
        if (cityIdInfraPairs.length % 2 != 0) {
            throw new IllegalArgumentException("City data must be provided as id/infra pairs");
        }

        Field citiesByNationField = NationDB.class.getDeclaredField("citiesByNation");
        citiesByNationField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, Object> citiesByNation = (Map<Integer, Object>) citiesByNationField.get(nationDb);
        if (citiesByNation == null) {
            citiesByNation = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
            citiesByNationField.set(nationDb, citiesByNation);
        }

        ObjectOpenHashSet<SimpleDBCity> cities = new ObjectOpenHashSet<>();
        for (int i = 0; i < cityIdInfraPairs.length; i += 2) {
            int cityId = (Integer) cityIdInfraPairs[i];
            double infra = (Double) cityIdInfraPairs[i + 1];
            SimpleDBCity dbCity = new SimpleDBCity(nationId);
            dbCity.setId(cityId);
            dbCity.setCreated(System.currentTimeMillis());
            dbCity.setFetched(System.currentTimeMillis());
            dbCity.setInfra_cents((int) Math.round(infra * 100));
            dbCity.setLand_cents((int) Math.round(infra * 100));
            dbCity.setPowered(true);
            cities.add(dbCity);
        }
        citiesByNation.put(nationId, cities);
    }

    private static void seedTreasures(NationDB nationDb, int nationId, DBTreasure... treasures) throws Exception {
        Field treasuresByNationField = NationDB.class.getDeclaredField("treasuresByNation");
        treasuresByNationField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, Set<DBTreasure>> treasuresByNation = (Map<Integer, Set<DBTreasure>>) treasuresByNationField.get(nationDb);
        if (treasuresByNation == null) {
            treasuresByNation = new HashMap<>();
            treasuresByNationField.set(nationDb, treasuresByNation);
        }
        Set<DBTreasure> treasureSet = new ObjectOpenHashSet<>();
        for (DBTreasure treasure : treasures) {
            treasureSet.add(treasure);
        }
        treasuresByNation.put(nationId, treasureSet);
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