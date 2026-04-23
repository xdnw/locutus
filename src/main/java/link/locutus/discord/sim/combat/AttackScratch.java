package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;

/**
 * Caller-owned reusable buffers for hot combat resolution paths.
 */
public final class AttackScratch {
    final double[] odds;
    final double[] attackerLossesWork;
    final double[] defenderLossesWork;
    final int[] attackerLossesInt;
    final int[] defenderLossesInt;
    final double[] consumption;

    public AttackScratch() {
        this.odds = new double[SuccessType.values.length];
        this.attackerLossesWork = new double[MilitaryUnit.values.length];
        this.defenderLossesWork = new double[MilitaryUnit.values.length];
        this.attackerLossesInt = new int[MilitaryUnit.values.length];
        this.defenderLossesInt = new int[MilitaryUnit.values.length];
        this.consumption = new double[ResourceType.values.length];
    }
}
