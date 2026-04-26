package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.WarStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimClockTest {

    @Test
    void dayPhaseAlignsToPerNationResetHour() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(6));
        SimNation resetAtMidnight = new SimNation(1, WarPolicy.FORTRESS, 0d, 100d, 2, (byte) 0);
        SimNation resetAtTenUtc = new SimNation(2, WarPolicy.TURTLE, 0d, 100d, 2, (byte) 10);

        world.addNation(resetAtMidnight);
        world.addNation(resetAtTenUtc);

        assertEquals(3, resetAtMidnight.dayPhaseTurn());
        assertEquals(10, resetAtTenUtc.dayPhaseTurn());

        world.stepTurnStart();
        assertEquals(4, resetAtMidnight.dayPhaseTurn());
        assertEquals(11, resetAtTenUtc.dayPhaseTurn());

        world.stepTurnStart();
        assertEquals(5, resetAtMidnight.dayPhaseTurn());
        assertEquals(0, resetAtTenUtc.dayPhaseTurn());
    }

    @Test
    void warExpiresExactlyOnTurnStartTPlusSixty() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(0));
        SimNation attacker = new SimNation(11, WarPolicy.FORTRESS, 0d, 100d, 2);
        SimNation defender = new SimNation(22, WarPolicy.TURTLE, 0d, 100d, 2);

        world.addNation(attacker);
        world.addNation(defender);
        world.addWar(new SimWar(301, 11, 22, WarType.ORD));

        for (int i = 0; i < 59; i++) {
            world.stepTurnStart();
        }

        SimWar war = world.requireWar(301);
        assertEquals(WarStatus.ACTIVE, war.status());
        assertEquals(0, war.startTurn());
        assertEquals(1, war.remainingTurns(world.currentTurn()));
        assertEquals(1, attacker.offSlotsUsed());
        assertEquals(1, defender.defSlotsUsed());

        world.stepTurnStart();

        assertEquals(WarStatus.EXPIRED, war.status());
        assertEquals(0, war.remainingTurns(world.currentTurn()));
        assertEquals(0, attacker.offSlotsUsed());
        assertEquals(0, defender.defSlotsUsed());
    }

    @Test
    void beigeCountdownTicksDownAtTurnStart() {
        SimWorld world = new SimWorld(SimTuning.defaults(), new SimClock(0));
        SimNation nation = new SimNation(99, WarPolicy.FORTRESS);
        world.addNation(nation);

        nation.applyBeigeTurns(3);
        assertEquals(3, nation.beigeTurns());

        world.stepTurnStart();
        assertEquals(2, nation.beigeTurns());

        world.stepTurnStart();
        world.stepTurnStart();
        world.stepTurnStart();
        assertEquals(0, nation.beigeTurns());
    }
}
