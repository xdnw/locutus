package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.util.battle.BlitzWarning;
import link.locutus.discord.util.battle.BlitzWarningCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlitzPlanWarningTest {
    @Test
    void mapsInternalWarningToDetailFreeWireWarning() {
        BlitzWarning warning = new BlitzWarning(BlitzWarningCode.BEIGE_DEFENDER, 1, 2, 3, "server-only detail");

        BlitzPlanWarning wireWarning = new BlitzPlanWarning(warning);

        assertEquals(BlitzWarningCode.BEIGE_DEFENDER.ordinal(), wireWarning.codeOrdinal());
        assertEquals(BlitzWarningCode.BEIGE_DEFENDER, wireWarning.code());
        assertEquals(1, wireWarning.attackerNationId());
        assertEquals(2, wireWarning.defenderNationId());
        assertEquals(3, wireWarning.warId());
    }

    @Test
    void rejectsUnknownWarningOrdinals() {
        assertThrows(IllegalArgumentException.class, () -> new BlitzPlanWarning(-1, 0, 0, 0));
    }
}