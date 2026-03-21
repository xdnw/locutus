package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderEngine;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.entities.DBAlliance;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlliancePlaceholdersRuntimeTest {
    @Test
    void parsesWildcardAgainstInjectedAllianceRuntimeWithoutLocutusBootstrap() {
        Set<DBAlliance> alliances = Set.of(
                alliance(10, "Rose"),
                alliance(20, "Eclipse"));
        CommandRuntimeServices services = CommandRuntimeServices.builder(modifier -> null)
                .alliances(() -> alliances)
                .build();

        PlaceholderEngine engine = new PlaceholderEngine(
                PWBindings.createDefaultStore(),
                PWBindings.createDefaultValidators(),
                PWBindings.createDefaultPermisser(),
                services);
        AlliancePlaceholders placeholders = new AlliancePlaceholders(
                engine.getStore(),
                engine.getValidators(),
                engine.getPermisser(),
                services);

        Set<Integer> ids = placeholders.parseSet(engine.createLocals(), "*", null)
                .stream()
                .map(DBAlliance::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(Set.of(10, 20), ids);
    }

        private static DBAlliance alliance(int id, String name) {
                return new DBAlliance(id, name, "", "", "", "", "", 0L, NationColor.GRAY,
                                (Int2ObjectOpenHashMap<byte[]>) null);
        }
}
