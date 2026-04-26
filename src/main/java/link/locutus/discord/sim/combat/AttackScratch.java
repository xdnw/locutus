package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.AttackType;

/**
 * Caller-owned reusable buffers for hot combat resolution paths.
 */
public final class AttackScratch implements AttackType.CasualtyRangeWriter {
    final double[] odds;
    final double[] attackerLossesWork;
    final double[] defenderLossesWork;
    final int[] attackerLossesInt;
    final int[] defenderLossesInt;
    final double[] consumption;
    final double[] casualtyScalars;
    final int[] attackerRangeUnits;
    final int[] attackerRangeMin;
    final int[] attackerRangeMax;
    final int[] defenderRangeUnits;
    final int[] defenderRangeMin;
    final int[] defenderRangeMax;
    int attackerRangeCount;
    int defenderRangeCount;

    static final int INFRA_SCALAR = 0;
    static final int LOOT_SCALAR = 1;

    public AttackScratch() {
        this.odds = new double[SuccessType.values.length];
        this.attackerLossesWork = new double[MilitaryUnit.values.length];
        this.defenderLossesWork = new double[MilitaryUnit.values.length];
        this.attackerLossesInt = new int[MilitaryUnit.values.length];
        this.defenderLossesInt = new int[MilitaryUnit.values.length];
        this.consumption = new double[ResourceType.values.length];
        this.casualtyScalars = new double[2];
        this.attackerRangeUnits = new int[MilitaryUnit.values.length];
        this.attackerRangeMin = new int[MilitaryUnit.values.length];
        this.attackerRangeMax = new int[MilitaryUnit.values.length];
        this.defenderRangeUnits = new int[MilitaryUnit.values.length];
        this.defenderRangeMin = new int[MilitaryUnit.values.length];
        this.defenderRangeMax = new int[MilitaryUnit.values.length];
    }

    @Override
    public void clearCasualtyRanges() {
        attackerRangeCount = 0;
        defenderRangeCount = 0;
    }

    @Override
    public void putAttackerRange(MilitaryUnit unit, int min, int max) {
        attackerRangeCount = putRange(
                attackerRangeUnits,
                attackerRangeMin,
                attackerRangeMax,
                attackerRangeCount,
                unit,
                min,
                max
        );
    }

    @Override
    public void putDefenderRange(MilitaryUnit unit, int min, int max) {
        defenderRangeCount = putRange(
                defenderRangeUnits,
                defenderRangeMin,
                defenderRangeMax,
                defenderRangeCount,
                unit,
                min,
                max
        );
    }

    private static int putRange(
            int[] units,
            int[] mins,
            int[] maxes,
            int count,
            MilitaryUnit unit,
            int min,
            int max
    ) {
        if (unit == null || (min == 0 && max == 0)) {
            return count;
        }
        int unitOrdinal = unit.ordinal();
        int normalizedMin = Math.max(0, min);
        int normalizedMax = Math.max(normalizedMin, max);
        for (int i = 0; i < count; i++) {
            if (units[i] == unitOrdinal) {
                mins[i] = normalizedMin;
                maxes[i] = normalizedMax;
                return count;
            }
        }
        units[count] = unitOrdinal;
        mins[count] = normalizedMin;
        maxes[count] = normalizedMax;
        return count + 1;
    }
}
