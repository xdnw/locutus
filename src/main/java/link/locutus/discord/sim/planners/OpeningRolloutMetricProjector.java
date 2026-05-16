package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.MutableAttackResult;

final class OpeningRolloutMetricProjector {
    private OpeningRolloutMetricProjector() {
    }

    static void project(
            OpeningEvaluator.OpeningBaseline baseline,
            CombatKernel.AttackContext context,
            AttackType attackType,
            OpeningMetricVector currentMetrics,
            MutableAttackResult result,
            OpeningMetricVector.Mutable out
    ) {
        boolean attackerHadAirControl = context.attackerHasAirControl();
        boolean defenderHadGroundSuperiority = context.defenderHasGroundSuperiority();
        boolean defenderHadAirControl = context.defenderHasAirControl();
        int blockadeOwner = context.blockadeOwner();
        double immediateHarm = currentMetrics.immediateHarm()
            + OpeningMetricSummary.immediateHarm(
                    baseline.defenderSnapshot(),
                    result,
                attackerHadAirControl,
                    attackerHadAirControl,
                defenderHadGroundSuperiority,
                defenderHadAirControl,
                blockadeOwner == CombatKernel.AttackContext.BLOCKADE_DEFENDER
            );
        double resourceSwing = currentMetrics.resourceSwing()
            + AttackObjectiveComponentMapper.resourceSwingForObjective(
                attackType,
                result.loot()
            );
        out.set(
                immediateHarm,
                resourceSwing
        );
    }
}
