package link.locutus.discord.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarSlotRulesTest {

    @Test
    void defensiveSlotCapIsSharedAcrossHelpers() {
        assertEquals(3, WarSlotRules.defensiveSlotCap());
        assertEquals(3, WarSlotRules.freeDefensiveSlots(0));
        assertEquals(1, WarSlotRules.freeDefensiveSlots(2));
        assertEquals(0, WarSlotRules.freeDefensiveSlots(3));
    }

    @Test
    void clampFreeDefensiveSlotsBoundsInputs() {
        assertEquals(0, WarSlotRules.clampFreeDefensiveSlots(-1));
        assertEquals(2, WarSlotRules.clampFreeDefensiveSlots(2));
        assertEquals(3, WarSlotRules.clampFreeDefensiveSlots(6));
    }
}