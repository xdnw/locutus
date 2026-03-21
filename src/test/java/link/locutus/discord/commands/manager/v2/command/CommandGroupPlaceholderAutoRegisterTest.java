package link.locutus.discord.commands.manager.v2.command;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.PWCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeServices;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationSnapshotService;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandGroupPlaceholderAutoRegisterTest {
    @TempDir
    Path tempDir;

    @Test
    void registerCommandsWithMappingResolvesLegacyPlaceholderFieldNamesThroughRegistry() {
        Fixture fixture = Fixture.create();
        PlaceholdersMap map = fixture.createMap();
        CommandGroup commands = CommandGroup.createRoot(fixture.store, fixture.validators);

        commands.registerCommandsWithMapping(LegacyWarAliasMapping.class);

        CommandCallable callable = commands.getCallable(List.of("selection_alias", "add", "war"));
        assertNotNull(callable);

        ParametricCallable<?> parametric = (ParametricCallable<?>) callable;
        assertEquals("addSelectionAlias", parametric.getMethod().getName());
        assertSame(map.get(DBWar.class), parametric.getObject());
    }

    @Test
    void savePojoEmitsLogicalPlaceholderKeysInsteadOfStaticFieldNames() throws IOException {
        Fixture fixture = Fixture.create();
        PlaceholdersMap map = fixture.createMap();
        CommandGroup commands = CommandGroup.createRoot(fixture.store, fixture.validators);
        commands.registerMethod(map.get(DBWar.class), List.of("selection_alias", "add"), "addSelectionAlias", "war");

        commands.savePojo(tempDir.toFile(), "test", "GeneratedCommands");

        String generated = Files.readString(tempDir.resolve("test").resolve("GeneratedCommands.java"));
        assertTrue(generated.contains("field=\"" + AutoRegisterSupport.PLACEHOLDER_FIELD_PREFIX + DBWar.class.getName() + "\""));
    }

    @SuppressWarnings("unused")
    private static final class LegacyWarAliasMapping {
        public static class selection_alias {
            public static class add {
                @AutoRegister(clazz = PlaceholdersMap.class, method = "addSelectionAlias", field = "WARS")
                public static class war extends CommandRef {
                    public static final war cmd = new war();

                    public war name(String value) {
                        return set("name", value);
                    }

                    public war wars(String value) {
                        return set("wars", value);
                    }
                }
            }
        }
    }

    private static final class Fixture {
        private final ValueStore store;
        private final ValidatorStore validators;
        private final PermissionHandler permisser;
        private final CommandRuntimeServices services;

        private Fixture(ValueStore store, ValidatorStore validators, PermissionHandler permisser,
                CommandRuntimeServices services) {
            this.store = store;
            this.validators = validators;
            this.permisser = permisser;
            this.services = services;
        }

        private static Fixture create() {
            TestNationSnapshot snapshot = new TestNationSnapshot(Set.of(
                    nation(1, "Alpha", "LeaderA", 10),
                    nation(2, "Beta", "LeaderB", 20),
                    nation(3, "Gamma", "LeaderC", 10)));
            Set<DBAlliance> alliances = Set.of(
                    alliance(10, "Rose"),
                    alliance(20, "Eclipse"));
            CommandRuntimeServices services = CommandRuntimeServices.builder(NationSnapshotService.fixed(snapshot))
                    .alliances(() -> alliances)
                    .build();

            ValueStore store = PWBindings.createDefaultStore();
            new PWCompleter().register(store);
            return new Fixture(
                    store,
                    PWBindings.createDefaultValidators(),
                    PWBindings.createDefaultPermisser(),
                    services);
        }

        private PlaceholdersMap createMap() {
            return new PlaceholdersMap(store, validators, permisser, services).initCommands();
        }

        private static DBNation nation(int id, String name, String leader, int allianceId) {
            SimpleDBNation nation = new SimpleDBNation(new DBNationData());
            nation.setNation_id(id);
            nation.setNation(name);
            nation.setLeader(leader);
            nation.setAlliance_id(allianceId);
            return nation;
        }

        private static DBAlliance alliance(int id, String name) {
            return new DBAlliance(id, name, "", "", "", "", "", 0L, NationColor.GRAY,
                    (Int2ObjectOpenHashMap<byte[]>) null);
        }
    }

    private static final class TestNationSnapshot implements INationSnapshot {
        private final Map<Integer, DBNation> nationsById;

        private TestNationSnapshot(Collection<DBNation> nations) {
            this.nationsById = new LinkedHashMap<>();
            for (DBNation nation : nations) {
                nationsById.put(nation.getId(), nation);
            }
        }

        @Override
        public DBNation getNationById(int id) {
            return nationsById.get(id);
        }

        @Override
        public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
            return nationsById.values().stream()
                    .filter(nation -> alliances.contains(nation.getAlliance_id()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public DBNation getNationByLeader(String input) {
            return nationsById.values().stream()
                    .filter(nation -> nation.getLeader().equalsIgnoreCase(input))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public DBNation getNationByName(String input) {
            return nationsById.values().stream()
                    .filter(nation -> nation.getName().equalsIgnoreCase(input))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Set<DBNation> getAllNations() {
            return new LinkedHashSet<>(nationsById.values());
        }

        @Override
        public Set<DBNation> getNationsByBracket(int taxId) {
            return Set.of();
        }

        @Override
        public Set<DBNation> getNationsByAlliance(int id) {
            return nationsById.values().stream()
                    .filter(nation -> nation.getAlliance_id() == id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Set<DBNation> getNationsByColor(NationColor color) {
            return Set.of();
        }
    }
}