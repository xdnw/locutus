package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.combat.ResolutionMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimWorldActorExecutionTest {

    @Test
    void deterministicActorExecutionDecidesOncePerPass() {
        SimWorld world = new SimWorld(new SimTuning(
                1,
                SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                SimTuning.DEFAULT_POLICY_COOLDOWN_TURNS,
                SimTuning.DEFAULT_PEACE_OFFER_LIFETIME_TURNS,
                SimTuning.DEFAULT_MAP_RESERVE_LIFETIME_TURNS,
                SimTuning.DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                SimTuning.DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                SimTuning.DEFAULT_CANDIDATES_PER_ATTACKER,
                SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT,
                ResolutionMode.MOST_LIKELY,
                11L
        ));
        world.addNation(new SimNation(1, WarPolicy.FORTRESS));
        world.addNation(new SimNation(2, WarPolicy.TURTLE));
        world.declareWar(101, 1, 2, WarType.ORD);

        AtomicInteger decideCalls = new AtomicInteger();
        Actor actor = new CountingAttackActor(decideCalls, 1);

        world.stepTurn(Map.of(1, actor), Objective.DAMAGE);

        assertEquals(1, decideCalls.get(), "MOST_LIKELY should call decide once for the pass");
        assertEquals(4, world.requireWar(101).attackerMaps(), "Turn start regens 1 MAP before the single ground attack spends 3");
    }

    @Test
    void stochasticActorExecutionReentersAfterEachAppliedAction() {
        SimWorld world = new SimWorld(new SimTuning(
                1,
                SimTuning.DEFAULT_TURN1_DECLARE_POLICY,
                SimTuning.DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                SimTuning.DEFAULT_ACTIVITY_ACT_THRESHOLD,
                SimTuning.DEFAULT_POLICY_COOLDOWN_TURNS,
                SimTuning.DEFAULT_PEACE_OFFER_LIFETIME_TURNS,
                SimTuning.DEFAULT_MAP_RESERVE_LIFETIME_TURNS,
                SimTuning.DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                SimTuning.DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                SimTuning.DEFAULT_CANDIDATES_PER_ATTACKER,
                SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT,
                ResolutionMode.STOCHASTIC,
                22L
        ));
        world.addNation(new SimNation(1, WarPolicy.FORTRESS));
        world.addNation(new SimNation(2, WarPolicy.TURTLE));
        world.declareWar(202, 1, 2, WarType.ORD);

        AtomicInteger decideCalls = new AtomicInteger();
        Actor actor = new CountingAttackActor(decideCalls, 2);

        world.stepTurn(Map.of(1, actor), Objective.DAMAGE);

        assertEquals(3, decideCalls.get(), "STOCHASTIC should re-enter after each applied attack until the actor passes");
        assertEquals(1, world.requireWar(202).attackerMaps(), "Turn start regens 1 MAP before two ground attacks spend 6 total MAP");
    }

    @Test
    void deterministicEvCannotDriveSimWorldState() {
        SimWorld world = new SimWorld(new SimTuning(ResolutionMode.DETERMINISTIC_EV));
        world.addNation(new SimNation(1, WarPolicy.FORTRESS));
        world.addNation(new SimNation(2, WarPolicy.TURTLE));
        world.declareWar(303, 1, 2, WarType.ORD);

        assertThrows(IllegalStateException.class, () -> world.resolveAttack(303, 1, AttackType.GROUND));
    }

    private static final class CountingAttackActor implements Actor {
        private final AtomicInteger decideCalls;
        private final int actionsBeforePass;

        private CountingAttackActor(AtomicInteger decideCalls, int actionsBeforePass) {
            this.decideCalls = decideCalls;
            this.actionsBeforePass = actionsBeforePass;
        }

        @Override
        public List<SimAction> decide(SimWorld world, SimNation self, DecisionContext ctx) {
            int call = decideCalls.incrementAndGet();
            if (call > actionsBeforePass) {
                return List.of();
            }
            return List.of(new AttackAction(activeWarId(world, self.nationId()), self.nationId(), AttackType.GROUND));
        }

        private static int activeWarId(SimWorld world, int nationId) {
            for (int warId = 1; warId <= 1_000; warId++) {
                try {
                    SimWar war = world.requireWar(warId);
                    if (war.isActive() && war.attackerNationId() == nationId) {
                        return warId;
                    }
                } catch (IllegalArgumentException ignored) {
                    // keep scanning the small synthetic test range
                }
            }
            throw new IllegalStateException("No active war found for nation " + nationId);
        }
    }
}