package link.locutus.discord.util.task.roles;

import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRoleRoleDirectoryTest {
    @Test
    void scanGroupsManagedRolesByKeyAndMarksDuplicates() {
        String smallCityRange = DiscordUtil.cityRangeToString(Map.entry(1, 3));
        String largeCityRange = DiscordUtil.cityRangeToString(Map.entry(4, 5));

        AutoRoleRoleDirectory.Snapshot snapshot = AutoRoleRoleDirectory.scan(List.of(
                role(130L, "AA 7 Aurora"),
                role(110L, "AA 7 Legacy"),
                role(90L, "AA 3 Borealis"),
                role(215L, smallCityRange),
                role(210L, smallCityRange),
                role(220L, largeCityRange),
                role(321L, "25/25"),
                role(320L, "25/25"),
                role(330L, "50/50"),
                role(500L, "general")
        ));

        assertEquals(List.of(90L, 110L, 130L), snapshot.allianceRoles().stream()
                .map(entry -> entry.role().getIdLong())
                .toList());
        assertEquals(List.of(3, 7, 7), snapshot.allianceRoles().stream()
                .map(AutoRoleRoleDirectory.AllianceRole::allianceId)
                .toList());
        assertFalse(snapshot.allianceRoles().get(0).duplicateKey());
        assertTrue(snapshot.allianceRoles().get(1).duplicateKey());
        assertTrue(snapshot.allianceRoles().get(2).duplicateKey());

        assertEquals(List.of(210L, 215L, 220L), snapshot.cityRoles().stream()
                .map(entry -> entry.role().getIdLong())
                .toList());
        assertEquals(List.of(1, 1, 4), snapshot.cityRoles().stream()
                .map(AutoRoleRoleDirectory.CityRole::rangeStart)
                .toList());
        assertTrue(snapshot.cityRoles().get(0).duplicateKey());
        assertTrue(snapshot.cityRoles().get(1).duplicateKey());
        assertFalse(snapshot.cityRoles().get(2).duplicateKey());

        assertEquals(List.of(320L, 321L, 330L), snapshot.taxRoles().stream()
                .map(entry -> entry.role().getIdLong())
                .toList());
        assertEquals(List.of(25, 25, 50), snapshot.taxRoles().stream()
                .map(AutoRoleRoleDirectory.TaxRole::moneyRate)
                .toList());
        assertTrue(snapshot.taxRoles().get(0).duplicateKey());
        assertTrue(snapshot.taxRoles().get(1).duplicateKey());
        assertFalse(snapshot.taxRoles().get(2).duplicateKey());
    }

    @Test
    void scanIgnoresUnrelatedDiscordRoles() {
        AutoRoleRoleDirectory.Snapshot snapshot = AutoRoleRoleDirectory.scan(List.of(
                role(100L, "general"),
                role(200L, "raids"),
                role(300L, "economy")
        ));

        assertTrue(snapshot.allianceRoles().isEmpty());
        assertTrue(snapshot.cityRoles().isEmpty());
        assertTrue(snapshot.taxRoles().isEmpty());
    }

        @Test
        void taskStateUsesDeterministicTaxRoleKeys() {
                Role lowerId = role(100L, "25/25");
                Role higherId = role(200L, "25/25");
                Role distinct = role(300L, "50/50");

                AutoRoleRoleDirectory.TaskState taskState = AutoRoleRoleDirectory.taskState(guild(List.of(
                                higherId,
                                distinct,
                                lowerId
                )));

                assertSame(lowerId, taskState.taxRoles().get(new AutoRoleRoleDirectory.TaxRoleKey(25, 25)));
                assertSame(distinct, taskState.taxRoles().get(new AutoRoleRoleDirectory.TaxRoleKey(50, 50)));
        }

    private static Role role(long id, String name) {
        return (Role) Proxy.newProxyInstance(
                Role.class.getClassLoader(),
                new Class<?>[]{Role.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIdLong" -> id;
                    case "getName" -> name;
                    case "getColorRaw" -> 0;
                    case "hashCode" -> Long.hashCode(id);
                    case "equals" -> proxy == args[0];
                    case "toString" -> name + "#" + id;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

        private static Guild guild(List<Role> roles) {
                return (Guild) Proxy.newProxyInstance(
                                Guild.class.getClassLoader(),
                                new Class<?>[]{Guild.class},
                                (proxy, method, args) -> switch (method.getName()) {
                                        case "getRoles" -> roles;
                                        case "getMembers" -> List.of();
                                        case "hashCode" -> 1;
                                        case "equals" -> proxy == args[0];
                                        case "toString" -> "guild";
                                        default -> throw new UnsupportedOperationException(method.getName());
                                });
        }
}
