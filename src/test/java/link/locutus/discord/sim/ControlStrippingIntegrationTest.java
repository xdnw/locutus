package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.combat.ResolutionMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ControlStrippingIntegrationTest {

    @Test
    void liveResolveAttackClearsOutgoingBlockadeInOtherWarsAndNotifiesCallbacks() {
        List<Integer> blockadeChangedWars = new ArrayList<>();
        EconomyProvider trackingEconomy = new EconomyProvider() {
            @Override
            public void onVictoryLootTransferred(SimNation winner, SimNation loser, SimWar war, double transferredMoney) {
            }

            @Override
            public void onControlFlagChange(SimWar war) {
                blockadeChangedWars.add(war.warId());
            }
        };
        SimWorld world = new SimWorld(
                new SimTuning(ResolutionMode.MOST_LIKELY),
                new SimClock(),
                trackingEconomy,
                AllianceLootProvider.NO_OP
        );

        SimNation alpha = new SimNation(1, WarPolicy.PIRATE);
        SimNation beta = new SimNation(2, WarPolicy.TURTLE);
        SimNation gamma = new SimNation(3, WarPolicy.MONEYBAGS);
        alpha.setUnitCount(MilitaryUnit.SHIP, 200);
        beta.setUnitCount(MilitaryUnit.SHIP, 1);
        gamma.setUnitCount(MilitaryUnit.SHIP, 5);
        world.addNation(alpha);
        world.addNation(beta);
        world.addNation(gamma);

        SimWar alphaVsBeta = new SimWar(3001, 1, 2, WarType.ORD);
        SimWar betaVsGamma = new SimWar(3002, 2, 3, WarType.ORD);
        world.addWar(alphaVsBeta);
        world.addWar(betaVsGamma);

        world.applyControlFlagChanges(3002, 2, 0, 0, 1);
        blockadeChangedWars.clear();

        world.resolveAttack(3001, 1, AttackType.NAVAL);

        assertEquals(SimSide.ATTACKER, alphaVsBeta.blockadeOwner());
        assertNull(betaVsGamma.blockadeOwner());
        assertEquals(List.of(3001, 3002), blockadeChangedWars);
    }
}