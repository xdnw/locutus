package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.actions.SetPolicyAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyCooldownTest {

    @Test
    void setPolicyStartsConfiguredCooldownAndBlocksFurtherChangesUntilExpiry() {
        SimWorld world = new SimWorld(SimTuning.defaults());
        SimNation nation = new SimNation(7, WarPolicy.FORTRESS);
        world.addNation(nation);

        world.apply(new SetPolicyAction(7, WarPolicy.PIRATE));

        assertEquals(WarPolicy.PIRATE, nation.policy());
        assertEquals(60, nation.policyCooldownTurnsRemaining());
        assertThrows(IllegalStateException.class, () -> world.apply(new SetPolicyAction(7, WarPolicy.TURTLE)));

        for (int i = 0; i < 59; i++) {
            world.stepTurnStart();
        }

        assertEquals(1, nation.policyCooldownTurnsRemaining());
        assertThrows(IllegalStateException.class, () -> world.apply(new SetPolicyAction(7, WarPolicy.BLITZKRIEG)));

        world.stepTurnStart();

        assertEquals(0, nation.policyCooldownTurnsRemaining());
        world.apply(new SetPolicyAction(7, WarPolicy.BLITZKRIEG));
        assertEquals(WarPolicy.BLITZKRIEG, nation.policy());
        assertEquals(60, nation.policyCooldownTurnsRemaining());
    }

    @Test
    void setPolicyRejectsNoopPolicyChanges() {
        SimWorld world = new SimWorld(SimTuning.defaults());
        world.addNation(new SimNation(9, WarPolicy.TURTLE));

        assertThrows(IllegalArgumentException.class, () -> world.apply(new SetPolicyAction(9, WarPolicy.TURTLE)));
    }

    @Test
    void setPolicyUsesCooldownFromTuning() {
        SimWorld world = new SimWorld(new SimTuning(3));
        SimNation nation = new SimNation(11, WarPolicy.FORTRESS);
        world.addNation(nation);

        world.apply(new SetPolicyAction(11, WarPolicy.MONEYBAGS));
        assertEquals(3, nation.policyCooldownTurnsRemaining());

        world.stepTurnStart();
        world.stepTurnStart();
        assertThrows(IllegalStateException.class, () -> world.apply(new SetPolicyAction(11, WarPolicy.PIRATE)));

        world.stepTurnStart();
        world.apply(new SetPolicyAction(11, WarPolicy.PIRATE));
        assertEquals(3, nation.policyCooldownTurnsRemaining());
    }
}
