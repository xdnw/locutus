package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderEngine;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.pnw.PNWUser;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NationPlaceholdersRuntimeTest {
    private static final long REGISTERED_BETA_DISCORD_ID = 900000000000001L;

    @Test
    void parsesSelectionsAgainstInjectedSnapshotWithoutLocutusBootstrap() {
        TestNationSnapshot snapshot = new TestNationSnapshot(Set.of(
                nation(1, "Alpha", "LeaderA", 10),
                nation(2, "Beta", "LeaderB", 20),
                nation(3, "Gamma", "LeaderC", 10)));

        CommandRuntimeServices services = CommandRuntimeServices.builder(NationSnapshotService.fixed(snapshot)).build();
        PlaceholderEngine engine = new PlaceholderEngine(
            PWBindings.createDefaultStore(),
            PWBindings.createDefaultValidators(),
            PWBindings.createDefaultPermisser(),
            services);
        NationPlaceholders placeholders = new NationPlaceholders(
                engine.getStore(),
                engine.getValidators(),
                engine.getPermisser(),
            services);

        Set<String> names = placeholders.parseSet(engine.createLocals(), "Alpha,aa:10", snapshot, true)
                .stream()
                .map(DBNation::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("Alpha", "Gamma"), names);
    }

    @Test
    void bindingParsesSelectionsAgainstInjectedPlaceholderRegistryWithoutLocutusBootstrap() {
        TestNationSnapshot snapshot = new TestNationSnapshot(Set.of(
                nation(1, "Alpha", "LeaderA", 10),
                nation(2, "Beta", "LeaderB", 20),
                nation(3, "Gamma", "LeaderC", 10)));

        CommandRuntimeServices services = CommandRuntimeServices.builder(NationSnapshotService.fixed(snapshot)).build();
        PlaceholdersMap map = new PlaceholdersMap(
                PWBindings.createDefaultStore(),
                PWBindings.createDefaultValidators(),
                PWBindings.createDefaultPermisser(),
                services).initCommands();

        Set<String> names = PWBindings.nations(map.createLocals(), null, "Alpha,aa:10")
                .stream()
                .map(DBNation::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of("Alpha", "Gamma"), names);
    }

    @Test
    void parsesDiscordMentionsAgainstInjectedServicesWithoutLocutusBootstrap() {
    TestNationSnapshot snapshot = new TestNationSnapshot(Set.of(
        nation(1, "Alpha", "LeaderA", 10),
        nation(2, "Beta", "LeaderB", 20),
        nation(3, "Gamma", "LeaderC", 10)));

    CommandRuntimeServices services = CommandRuntimeServices.builder(NationSnapshotService.fixed(snapshot))
        .discordUserById(userId -> null)
        .discordRegistrationById(userId -> userId == REGISTERED_BETA_DISCORD_ID
            ? new PNWUser(2, userId, "Beta#0001")
            : null)
        .build();
    PlaceholderEngine engine = new PlaceholderEngine(
        PWBindings.createDefaultStore(),
        PWBindings.createDefaultValidators(),
        PWBindings.createDefaultPermisser(),
        services);
    NationPlaceholders placeholders = new NationPlaceholders(
        engine.getStore(),
        engine.getValidators(),
        engine.getPermisser(),
        services);

    Set<String> names = placeholders.parseSet(
            engine.createLocals(),
            "<@" + REGISTERED_BETA_DISCORD_ID + ">",
            snapshot,
            false)
        .stream()
        .map(DBNation::getName)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    assertEquals(Set.of("Beta"), names);
    }

    @Test
    void runtimeLookupParsesDiscordMentionsAgainstInjectedSnapshot() {
    TestNationSnapshot snapshot = new TestNationSnapshot(Set.of(
        nation(1, "Alpha", "LeaderA", 10),
        nation(2, "Beta", "LeaderB", 20),
        nation(3, "Gamma", "LeaderC", 10)));

    CommandRuntimeServices services = CommandRuntimeServices.builder(NationSnapshotService.fixed(snapshot))
        .discordUserById(userId -> null)
        .discordRegistrationById(userId -> userId == REGISTERED_BETA_DISCORD_ID
            ? new PNWUser(2, userId, "Beta#0001")
            : null)
        .build();

    DBNation mentionedNation = services.lookup().parseNation(
        snapshot,
        "<@" + REGISTERED_BETA_DISCORD_ID + ">",
        false,
        false,
        true,
        null);

    assertEquals("Beta", mentionedNation.getName());
    }

    private static DBNation nation(int id, String name, String leader, int allianceId) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNation_id(id);
        nation.setNation(name);
        nation.setLeader(leader);
        nation.setAlliance_id(allianceId);
        return nation;
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
