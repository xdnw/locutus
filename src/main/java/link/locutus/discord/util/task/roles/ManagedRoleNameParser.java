package link.locutus.discord.util.task.roles;

import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Role;

import java.util.Map;

final class ManagedRoleNameParser {
    private ManagedRoleNameParser() {
    }

    static String expectedAllianceRoleName(DBAlliance alliance) {
        return "AA " + alliance.getId() + " " + alliance.getName();
    }

    static String expectedAllianceRoleName(int allianceId) {
        DBAlliance alliance = DBAlliance.get(allianceId);
        if (alliance == null) {
            return null;
        }
        return expectedAllianceRoleName(alliance);
    }

    static Integer parseAllianceId(Role role) {
        return parseAllianceId(role.getName());
    }

    static Integer parseAllianceId(String roleName) {
        if (!roleName.startsWith("AA ")) {
            return null;
        }
        String[] split = roleName.split(" ");
        if (split.length < 2 || !MathMan.isInteger(split[1])) {
            return null;
        }
        return Integer.parseInt(split[1]);
    }

    static Map.Entry<Integer, Integer> parseCityRange(Role role) {
        return parseCityRange(role.getName());
    }

    static Map.Entry<Integer, Integer> parseCityRange(String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            return null;
        }
        return DiscordUtil.getCityRange(roleName);
    }

    static TaxRate parseTaxRate(Role role) {
        return parseTaxRate(role.getName());
    }

    static TaxRate parseTaxRate(String roleName) {
        String[] split = roleName.split("/");
        if (split.length != 2 || !MathMan.isInteger(split[0]) || !MathMan.isInteger(split[1])) {
            return null;
        }
        return new TaxRate(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
    }
}
