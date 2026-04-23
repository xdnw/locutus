package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.actions.AcceptPeaceAction;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.OfferPeaceAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeaceOfferAcceptStateTest {

    private static void addPair(SimWorld world, int attackerId, int defenderId) {
        world.addNation(new SimNation(attackerId, WarPolicy.FORTRESS));
        world.addNation(new SimNation(defenderId, WarPolicy.TURTLE));
    }

    @Test
    void oppositeSideCanAcceptPeaceOfferAndWarTransitionsToPeace() {
        SimWorld world = new SimWorld(new SimTuning(60, 6, 24));
        addPair(world, 1, 2);
        SimWar war = new SimWar(101, 1, 2, WarType.ORD);
        world.addWar(war);

        world.apply(new OfferPeaceAction(101, 1));
        assertTrue(war.hasPendingPeaceOffer());
        assertEquals(WarStatus.ATTACKER_OFFERED_PEACE, war.status());

        world.apply(new AcceptPeaceAction(101, 2));
        assertEquals(WarStatus.PEACE, war.status());
        assertFalse(war.hasPendingPeaceOffer());
    }

    @Test
    void sameSideCannotAcceptItsOwnPeaceOffer() {
        SimWorld world = new SimWorld(new SimTuning(60, 6, 24));
        addPair(world, 11, 22);
        SimWar war = new SimWar(102, 11, 22, WarType.ORD);
        world.addWar(war);

        world.apply(new OfferPeaceAction(102, 11));
        assertThrows(IllegalStateException.class, () -> world.apply(new AcceptPeaceAction(102, 11)));
        assertEquals(WarStatus.ATTACKER_OFFERED_PEACE, war.status());
    }

    @Test
    void pendingPeaceOfferCannotBeOverwrittenBySecondOffer() {
        SimWorld world = new SimWorld(new SimTuning(60, 6, 24));
        addPair(world, 31, 41);
        SimWar war = new SimWar(104, 31, 41, WarType.ORD);
        world.addWar(war);

        world.apply(new OfferPeaceAction(104, 31));
        assertThrows(IllegalStateException.class, () -> world.apply(new OfferPeaceAction(104, 41)));
        assertEquals(WarStatus.ATTACKER_OFFERED_PEACE, war.status());
    }

    @Test
    void endedWarRejectsFurtherPeaceActions() {
        SimWorld world = new SimWorld(new SimTuning(60, 6, 24));
        addPair(world, 100, 200);
        SimWar war = new SimWar(105, 100, 200, WarType.ORD);
        world.addWar(war);

        world.apply(new OfferPeaceAction(105, 100));
        world.apply(new AcceptPeaceAction(105, 200));

        assertThrows(IllegalStateException.class, () -> world.apply(new OfferPeaceAction(105, 100)));
        assertThrows(IllegalStateException.class, () -> world.apply(new AcceptPeaceAction(105, 200)));
    }

    @Test
    void realAttackClearsPendingPeaceOfferBackToActive() {
        SimWorld world = new SimWorld(new SimTuning(60, 6, 24));
        addPair(world, 301, 302);
        SimWar war = new SimWar(106, 301, 302, WarType.ORD);
        world.addWar(war);
        world.requireNation(301).setUnitCount(MilitaryUnit.SOLDIER, 5_000);
        world.requireNation(302).setUnitCount(MilitaryUnit.SOLDIER, 5_000);

        world.apply(new OfferPeaceAction(106, 301));
        assertEquals(WarStatus.ATTACKER_OFFERED_PEACE, war.status());

        world.apply(new AttackAction(106, 301, AttackType.GROUND));

        assertEquals(WarStatus.ACTIVE, war.status());
        assertFalse(war.hasPendingPeaceOffer());
    }
}
