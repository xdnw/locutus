package link.locutus.discord.web.commands.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RankingEntityType;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RankingKind;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RankingSectionKind;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.RankingSectionRange;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.web.commands.WM;
import link.locutus.discord.web.commands.binding.value_types.WebRankingResult;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class RankingEndpointsContractTest {
    @Test
    void rankingEndpointsAreRegisteredInStandalonePageHandler() {
        PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
        CommandGroup api = (CommandGroup) handler.getCommands().get("api");

        CommandCallable allianceMetric = api.get("allianceMetricRanking");
        CommandCallable allianceAttribute = api.get("allianceAttributeRanking");
        CommandCallable allianceDelta = api.get("allianceMetricDeltaRanking");
        CommandCallable allianceLoot = api.get("allianceLootRanking");
        CommandCallable nationAttribute = api.get("nationAttributeRanking");
        CommandCallable producer = api.get("producerRanking");
        CommandCallable recruitment = api.get("recruitmentRanking");
        CommandCallable warStatusByAlliance = api.get("warStatusRankingByAlliance");
        CommandCallable warStatusByNation = api.get("warStatusRankingByNation");
        CommandCallable warCostRanking = api.get("warCostRanking");
        CommandCallable warRanking = api.get("warRanking");
        CommandCallable attackTypeRanking = api.get("attackTypeRanking");

        assertNotNull(allianceMetric);
        assertNotNull(allianceAttribute);
        assertNotNull(allianceDelta);
        assertNotNull(allianceLoot);
        assertNotNull(nationAttribute);
        assertNotNull(producer);
        assertNotNull(recruitment);
        assertNotNull(warStatusByAlliance);
        assertNotNull(warStatusByNation);
        assertNotNull(warCostRanking);
        assertNotNull(warRanking);
        assertNotNull(attackTypeRanking);
    }

    @Test
    void generatedWebCommandRefsExposeWarRankingEndpoints() {
        assertNotNull(WM.api.warCostRanking.cmd);
        assertNotNull(WM.api.warRanking.cmd);
        assertNotNull(WM.api.attackTypeRanking.cmd);
    }

    @Test
    void allianceMetricEndpointReturnsFlatStructuredRankingPayload() throws Exception {
        Path tempDir = Files.createTempDirectory("ranking-endpoint-contract-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);

        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();

        try (NationDB nationDb = new NationDB()) {
            DBAlliance alpha = nationDb.getOrCreateAlliance(1);
            DBAlliance beta = nationDb.getOrCreateAlliance(2);

            nationDb.addAllianceMetric(alpha, AllianceMetric.SCORE, 4L, 5d, false);
            nationDb.addAllianceMetric(beta, AllianceMetric.SCORE, 4L, 10d, false);

            instanceField.set(null, fakeLocutus(nationDb));

            RankingEndpoints endpoints = new RankingEndpoints();
            WebRankingResult result = endpoints.allianceMetricRanking(
                    AllianceMetric.SCORE,
                    Set.of(alpha, beta),
                    false,
                    Set.of(alpha)
            );

            assertEquals(RankingKind.ALLIANCE_METRIC, result.kind());
            assertEquals(RankingEntityType.ALLIANCE, result.key1Type());
            assertNull(result.key2Type());
            assertEquals(2, result.rowCount());
            assertEquals(List.of(2L, 1L), result.key1Ids());
            assertEquals(List.of(), result.key2Ids());
            assertEquals(1, result.valueColumns().size());
            assertEquals(List.of(10d, 5d), result.valueColumns().get(0).values());
            assertEquals(List.of(new RankingSectionRange(RankingSectionKind.ALLIANCES, 0, 2)), result.sectionRanges());
            assertEquals(List.of(1L), result.highlightedKey1Ids());
            assertNotNull(result.asOfMs());

            String json = new ObjectMapper().writeValueAsString(result);
            assertTrue(json.contains("\"kind\":\"ALLIANCE_METRIC\""));
            assertTrue(json.contains("\"key1Ids\":[2,1]"));
            assertTrue(json.contains("\"values\":[10.0,5.0]"));
            assertTrue(json.contains("\"highlightedKey1Ids\":[1]"));
            assertFalse(json.contains("responseKey"));
            assertFalse(json.contains("sections"));
            assertFalse(json.contains("entityKey"));
            assertFalse(json.contains("sortValue"));
        } finally {
            instanceField.set(null, previousInstance);
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = previousDirectory;
            deleteRecursively(tempDir);
        }
    }

    private static Locutus fakeLocutus(NationDB nationDb) throws Exception {
        Locutus locutus = (Locutus) allocateWithoutConstructor(Locutus.class);
        Object loader = Proxy.newProxyInstance(
                ILoader.class.getClassLoader(),
                new Class<?>[]{ILoader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNationDB", "getCachedNationDB" -> nationDb;
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
