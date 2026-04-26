package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.AcceptPeaceAction;
import link.locutus.discord.sim.actions.DeclareWarAction;
import link.locutus.discord.sim.actions.OfferPeaceAction;
import link.locutus.discord.sim.actions.SimAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotReleaseBetweenPassesTest {

    @Test
    void endedWarReleasesSlotsBeforeSecondDeclarePass() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(1, WarPolicy.FORTRESS, 0d, 100d, 1);
        SimNation defenderA = new SimNation(2, WarPolicy.TURTLE, 0d, 100d, 2);
        SimNation defenderB = new SimNation(3, WarPolicy.TURTLE, 0d, 100d, 2);

        world.addNation(attacker);
        world.addNation(defenderA);
        world.addNation(defenderB);

        world.addWar(new SimWar(700, 1, 2, WarType.ORD));
        world.apply(new OfferPeaceAction(700, 1));

        world.stepTurn(
                List.of(new AcceptPeaceAction(700, 2)),
                List.of(new DeclareWarAction(701, 1, 3, WarType.ORD))
        );

        assertEquals(WarStatus.PEACE, world.requireWar(700).status());
        assertEquals(WarStatus.ACTIVE, world.requireWar(701).status());
        assertEquals(1, attacker.offSlotsUsed());
        assertEquals(1, defenderB.defSlotsUsed());
        assertEquals(0, defenderA.defSlotsUsed());
    }

    @Test
    void preActionsExecuteBeforeDeclaresWithinSamePass() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(10, WarPolicy.FORTRESS, 0d, 100d, 1);
        SimNation defenderA = new SimNation(20, WarPolicy.TURTLE, 0d, 100d, 2);
        SimNation defenderB = new SimNation(30, WarPolicy.TURTLE, 0d, 100d, 2);

        world.addNation(attacker);
        world.addNation(defenderA);
        world.addNation(defenderB);

        world.addWar(new SimWar(800, 10, 20, WarType.ORD));
        world.apply(new OfferPeaceAction(800, 10));

        List<SimAction> mixedOrderPass = List.of(
                new DeclareWarAction(801, 10, 30, WarType.ORD),
                new AcceptPeaceAction(800, 20)
        );
        world.stepTurn(List.of(mixedOrderPass));

        assertEquals(WarStatus.PEACE, world.requireWar(800).status());
        assertEquals(WarStatus.ACTIVE, world.requireWar(801).status());
    }

    @Test
    void declaresExecuteBeforeAttacksWithinSamePass() {
        SimWorld world = new SimWorld();
        SimNation attacker = new SimNation(40, WarPolicy.FORTRESS, 0d, 100d, 2);
        SimNation defender = new SimNation(50, WarPolicy.TURTLE, 0d, 100d, 2);
        world.addNation(attacker);
        world.addNation(defender);

        List<SimAction> mixedOrderPass = List.of(
                new AttackAction(900, 40, AttackType.FORTIFY),
                new DeclareWarAction(900, 40, 50, WarType.ORD)
        );
        world.stepTurn(List.of(mixedOrderPass));

        assertTrue(world.requireWar(900).attackerFortified());
    }

    @Test
    void stepTurnRejectsPassCountAboveTuningLimit() {
        SimTuning tuning = new SimTuning(
                1,
                Turn1DeclarePolicy.BOTH_FREE,
                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                60,
                250,
                500,
                8,
                24
        );
        SimWorld world = new SimWorld(tuning);

        world.addNation(new SimNation(60, WarPolicy.FORTRESS, 0d, 100d, 2));
        world.addNation(new SimNation(70, WarPolicy.TURTLE, 0d, 100d, 2));

        assertThrows(
                IllegalArgumentException.class,
            () -> world.stepTurn(List.of(
                List.of(new DeclareWarAction(901, 60, 70, WarType.ORD)),
                List.of(new OfferPeaceAction(901, 60))
            ))
        );
    }

    @Test
    void stepTurnIgnoresEmptyPassesWhenCountingAgainstTuningLimit() {
        SimTuning tuning = new SimTuning(
                1,
                Turn1DeclarePolicy.BOTH_FREE,
                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                60,
                250,
                500,
                8,
                24
        );
        SimWorld world = new SimWorld(tuning);

        world.addNation(new SimNation(80, WarPolicy.FORTRESS, 0d, 100d, 2));
        world.addNation(new SimNation(90, WarPolicy.TURTLE, 0d, 100d, 2));

        world.stepTurn(List.of(List.of(), List.of(new DeclareWarAction(902, 80, 90, WarType.ORD))));
        assertEquals(WarStatus.ACTIVE, world.requireWar(902).status());
    }

    @Test
    void twoListStepTurnDoesNotForceSecondEmptyPassAgainstLimit() {
        SimTuning tuning = new SimTuning(
                1,
                Turn1DeclarePolicy.BOTH_FREE,
                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                60,
                250,
                500,
                8,
                24
        );
        SimWorld world = new SimWorld(tuning);

        world.addNation(new SimNation(100, WarPolicy.FORTRESS, 0d, 100d, 2));
        world.addNation(new SimNation(110, WarPolicy.TURTLE, 0d, 100d, 2));

        world.stepTurn(List.of(new DeclareWarAction(903, 100, 110, WarType.ORD)), List.of());
        assertEquals(WarStatus.ACTIVE, world.requireWar(903).status());
    }
}
