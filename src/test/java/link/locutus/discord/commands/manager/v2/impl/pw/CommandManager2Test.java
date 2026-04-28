package link.locutus.discord.commands.manager.v2.impl.pw;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandManager2Test {
    @Test
    void manualWebOptionsIncludeBlitzEnums() {
        Map<String, Object> options = new LinkedHashMap<>();

        CommandManager2.addManualEnumOptions(options);

        assertOptionValues(options, "BlitzSideMode", List.of("ATTACKERS_ONLY", "DEFENDERS_ONLY", "BOTH"));
        assertOptionValues(options, "BlitzRebuyMode", List.of("CURRENT_BUYS", "FULL_REBUYS", "NO_REBUYS"));
        assertOptionValues(options, "BlitzObjective", List.of("NET_DAMAGE", "DAMAGE", "MINIMUM_DAMAGE_RECEIVED", "CONTROL", "BALANCED"));
        assertOptionValues(options, "BlitzAssignedWarSource", List.of("USER_PINNED", "PLANNER"));
        assertOptionValues(options, "Turn1DeclarePolicy", List.of("ATTACKERS_OPEN_THEN_FREE_DEFENDERS_COUNTER", "BOTH_FREE"));
        assertOptionValues(options, "BlitzWarningCode", List.of("BELOW_WAR_RANGE", "ABOVE_WAR_RANGE", "UPDECLARE_TOO_STRONG", "DEFENDER_SLOTTED", "DEFENDER_SPY_SLOTTED", "ATTACKER_INACTIVE", "ATTACKER_VM", "DEFENDER_INACTIVE", "DEFENDER_VM", "ATTACKER_AT_MAX_OFFENSIVES", "UNKNOWN_NATION", "DUPLICATE_NATION", "NATION_ON_BOTH_SIDES", "ACTIVE_PAIR_CONFLICT", "BEIGE_DEFENDER", "TREATY_BLOCKED", "MANUAL_DECLARATION_REJECTED", "MANUAL_DECLARATION_FORCED", "OVERRIDE_INVALID", "ALLIANCE_MISMATCH", "TARGET_NOT_ENEMY", "DUPLICATE_SHEET_COLUMN"));
        assertOptionValues(options, "PlannerDiagnosticCode", List.of("RESET_HOUR_FALLBACK"));
        assertOptionValues(options, "PlannerDiagnosticSeverity", List.of("WARNING"));
        assertOptionValues(options, "PlannerDiagnosticNationRole", List.of("ATTACKER", "DEFENDER"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void assertOptionValues(Map<String, Object> options, String key, List<String> expected) {
        assertEquals(expected, asMap(options.get(key)).get("options"));
    }
}
