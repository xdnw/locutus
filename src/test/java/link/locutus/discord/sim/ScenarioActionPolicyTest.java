package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.DeclareWarAction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioActionPolicyTest {

    @Test
    void blocksDeclaresWhenPolicyDisallows() {
        ScenarioActionPolicy.NationActionPolicy blockedDeclares = new ScenarioActionPolicy.NationActionPolicy(
                false,
                true,
                true,
                true,
                EnumSet.allOf(AttackType.class)
        );
        SimWorld world = new SimWorld(
                SimTuning.defaults(),
                EconomyProvider.NO_OP,
                AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE,
                ResetTimeProvider.FROM_NATION,
                ScenarioActionPolicy.fixed(blockedDeclares)
        );
        world.addNation(new SimNation(1, WarPolicy.ATTRITION));
        world.addNation(new SimNation(2, WarPolicy.ATTRITION));

        assertThrows(IllegalStateException.class,
                () -> world.apply(new DeclareWarAction(101, 1, 2, WarType.ORD)));
    }

    @Test
    void blocksAttackTypesOutsideAllowlist() {
        ScenarioActionPolicy.NationActionPolicy noGround = new ScenarioActionPolicy.NationActionPolicy(
                true,
                true,
                true,
                true,
                EnumSet.of(AttackType.AIRSTRIKE_INFRA)
        );
        SimWorld world = new SimWorld(
                SimTuning.defaults(),
                EconomyProvider.NO_OP,
                AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE,
                ResetTimeProvider.FROM_NATION,
                ScenarioActionPolicy.fixed(noGround)
        );
        world.addNation(new SimNation(1, WarPolicy.ATTRITION));
        world.addNation(new SimNation(2, WarPolicy.ATTRITION));
        world.declareWar(101, 1, 2, WarType.ORD);

        assertThrows(IllegalStateException.class,
                () -> world.apply(new AttackAction(101, 1, AttackType.GROUND)));
    }

    @Test
    void resolvesPoliciesPerTeam() {
        ScenarioActionPolicy.NationActionPolicy denyDeclares = new ScenarioActionPolicy.NationActionPolicy(
                false,
                true,
                true,
                true,
                EnumSet.allOf(AttackType.class)
        );
        SimWorld world = new SimWorld(
                SimTuning.defaults(),
                EconomyProvider.NO_OP,
                AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE,
                ResetTimeProvider.FROM_NATION,
                ScenarioActionPolicy.perTeam(Map.of(1, denyDeclares), ScenarioActionPolicy.NationActionPolicy.allowAll())
        );
        world.addNation(new SimNation(1, WarPolicy.ATTRITION));
        world.addNation(new SimNation(2, WarPolicy.ATTRITION));

        assertThrows(IllegalStateException.class,
                () -> world.apply(new DeclareWarAction(101, 1, 2, WarType.ORD)));
    }

        @Test
        void snapshotResolveMatchesPerTeamPolicy() {
                ScenarioActionPolicy.NationActionPolicy denyDeclares = new ScenarioActionPolicy.NationActionPolicy(
                                false,
                                true,
                                true,
                                true,
                                EnumSet.allOf(AttackType.class)
                );
                ScenarioActionPolicy policy = ScenarioActionPolicy.perTeam(
                                Map.of(2, denyDeclares),
                                ScenarioActionPolicy.NationActionPolicy.allowAll()
                );

                ScenarioActionPolicy.NationActionPolicy teamPolicy = ScenarioActionPolicy.resolveSnapshot(policy, 99, 2);
                ScenarioActionPolicy.NationActionPolicy defaultPolicy = ScenarioActionPolicy.resolveSnapshot(policy, 99, 1);

                assertEquals(denyDeclares, teamPolicy);
                assertEquals(ScenarioActionPolicy.NationActionPolicy.allowAll(), defaultPolicy);
        }

        @Test
        void snapshotResolveFailsClosedForWorldOnlyPolicy() {
                ScenarioActionPolicy worldOnlyPolicy = (world, nation) -> ScenarioActionPolicy.NationActionPolicy.allowAll();

                ScenarioActionPolicy.NationActionPolicy resolved = ScenarioActionPolicy.resolveSnapshot(worldOnlyPolicy, 1, 1);

                assertFalse(resolved.allowDeclares());
                assertFalse(resolved.allowBuys());
                assertFalse(resolved.allowPeace());
                assertFalse(resolved.allowMapReservations());
                assertTrue(resolved.allowedAttackTypes().isEmpty());
        }
}
