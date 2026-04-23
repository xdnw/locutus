package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.BuyUnitsAction;
import link.locutus.discord.sim.actions.DeclareWarAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreDriftTest {

    @Test
    void declareRangeUpdatesAfterUnitLosses() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(1, WarPolicy.FORTRESS, 0d, 100d, 2);
        SimNation defender = new SimNation(2, WarPolicy.TURTLE, 0d, 100d, 2);
        defender.setUnitCount(MilitaryUnit.SHIP, 160); // +160 score, initially above attacker's max war range (250)

        world.addNation(attacker);
        world.addNation(defender);

        assertFalse(world.canDeclareWar(1, 2));

        world.applyUnitLosses(2, Map.of(MilitaryUnit.SHIP, 10)); // drops defender score to 250, now in range

        assertTrue(world.canDeclareWar(1, 2));
        world.apply(new DeclareWarAction(501, 1, 2, WarType.ORD));
        assertFalse(world.canDeclareWar(1, 2));
    }

    @Test
    void buyUnitsRecomputesScoreAndCanCloseDeclareWindow() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(10, WarPolicy.PIRATE, 0d, 100d, 2);
        SimNation defender = new SimNation(20, WarPolicy.TURTLE, 1_000_000d, 245d, 2);
        defender.addResource(ResourceType.STEEL, 500d);

        world.addNation(attacker);
        world.addNation(defender);

        assertTrue(world.canDeclareWar(10, 20));

        world.apply(new BuyUnitsAction(20, Map.of(MilitaryUnit.SHIP, 6))); // +6 score pushes defender above attacker's max range (250)

        assertFalse(world.canDeclareWar(10, 20));
    }

    @Test
    void neighborContextUsesUpdatedDeclareRangeAfterScoreDrift() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS, 0d, 100d, 2));

        SimNation inRangeAfterLoss = new SimNation(2, WarPolicy.TURTLE, 0d, 100d, 2);
        inRangeAfterLoss.setUnitCount(MilitaryUnit.SHIP, 160);
        world.addNation(inRangeAfterLoss);

        SimNation stableNeighbor = new SimNation(3, WarPolicy.TURTLE, 0d, 180d, 2);
        world.addNation(stableNeighbor);

        AtomicReference<Set<Integer>> neighborsSeen = new AtomicReference<>(Set.of());
        Actor actor = (simWorld, self, ctx) -> {
            neighborsSeen.set(ctx.neighborNationsInRange());
            return List.of();
        };

        world.stepTurn(Map.of(1, actor), Objective.DAMAGE);
        assertEquals(Set.of(3), neighborsSeen.get());

        world.applyUnitLosses(2, Map.of(MilitaryUnit.SHIP, 10));
        world.stepTurn(Map.of(1, actor), Objective.DAMAGE);

        assertEquals(Set.of(2, 3), neighborsSeen.get());
    }

    @Test
    void buyUnitsRejectsNonBuyablePseudoUnits() {
        SimWorld world = new SimWorld();
        SimNation nation = new SimNation(30, WarPolicy.FORTRESS, 0d, 100d, 2);
        world.addNation(nation);

        assertThrows(IllegalArgumentException.class, () -> world.apply(new BuyUnitsAction(30, Map.of(MilitaryUnit.MONEY, 1))));
        assertThrows(IllegalArgumentException.class, () -> world.apply(new BuyUnitsAction(30, Map.of(MilitaryUnit.INFRASTRUCTURE, 1))));
    }
}
