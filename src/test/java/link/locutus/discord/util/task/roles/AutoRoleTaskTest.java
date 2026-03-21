package link.locutus.discord.util.task.roles;

import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

class AutoRoleTaskTest {
    @Test
    void selectPreferredAllianceRolePrefersRoleWithMostAssignments() {
        Role heavilyAssigned = role(100L, "AA 7 Legacy");
        Role correctlyNamed = role(200L, "AA 7 Aurora");

    Role selected = AutoRoleRoleDirectory.selectPreferredAllianceRole(
                List.of(correctlyNamed, heavilyAssigned),
                "AA 7 Aurora",
                Map.of(heavilyAssigned, 5, correctlyNamed, 0));

        assertSame(heavilyAssigned, selected);
    }

    @Test
    void selectPreferredAllianceRolePrefersCorrectNameOnTie() {
        Role incorrect = role(100L, "AA 7 Legacy");
        Role correct = role(200L, "AA 7 Aurora");

        Role selected = AutoRoleRoleDirectory.selectPreferredAllianceRole(
                List.of(incorrect, correct),
                "AA 7 Aurora",
                Map.of());

        assertSame(correct, selected);
    }

    @Test
    void selectPreferredAllianceRoleFallsBackToLowestIdOnFullTie() {
        Role earlier = role(100L, "AA 7 Legacy");
        Role later = role(200L, "AA 7 Legacy");

        Role selected = AutoRoleRoleDirectory.selectPreferredAllianceRole(
                List.of(later, earlier),
                "AA 7 Aurora",
                Map.of());

        assertSame(earlier, selected);
    }

    private static Role role(long id, String name) {
        return (Role) Proxy.newProxyInstance(
                Role.class.getClassLoader(),
                new Class<?>[]{Role.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIdLong" -> id;
                    case "getName" -> name;
                    case "hashCode" -> Long.hashCode(id);
                    case "equals" -> proxy == args[0];
                    case "toString" -> name + "#" + id;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
