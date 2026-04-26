package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;

import java.util.Arrays;
import java.util.Objects;

/**
 * Caller-owned mutable attack output for allocation-free planner hot paths.
 *
 * <p>Contract: discrete fields ({@link #attackerLosses()}, {@link #defenderLosses()},
 * resistance deltas, infra, loot, control deltas) represent state-transition outputs.
 * Expected-value loss arrays ({@link #attackerLossesEv()}, {@link #defenderLossesEv()})
 * are ranking-only outputs and must not be treated as authoritative mutable state.</p>
 */
public final class MutableAttackResult {
    private SuccessType success = SuccessType.UTTER_FAILURE;
    private ResolutionMode mode = ResolutionMode.MOST_LIKELY;
    private final int[] attackerLosses = new int[MilitaryUnit.values.length];
    private final int[] defenderLosses = new int[MilitaryUnit.values.length];
    private final double[] attackerLossesEv = new double[MilitaryUnit.values.length];
    private final double[] defenderLossesEv = new double[MilitaryUnit.values.length];
    private double infraDestroyed;
    private double loot;
    private double attackerResistanceDelta;
    private double defenderResistanceDelta;
    private int mapCost;
    private final double[] consumption = new double[ResourceType.values.length];
    private ControlFlagDelta controlDelta = ControlFlagDelta.NONE;

    void setDiscrete(
            SuccessType success,
            ResolutionMode mode,
            int[] attackerLosses,
            int[] defenderLosses,
            double infraDestroyed,
            double loot,
            double attackerResistanceDelta,
            double defenderResistanceDelta,
            int mapCost,
            double[] consumption,
            ControlFlagDelta controlDelta
    ) {
        this.success = Objects.requireNonNull(success, "success");
        this.mode = Objects.requireNonNull(mode, "mode");
        System.arraycopy(attackerLosses, 0, this.attackerLosses, 0, this.attackerLosses.length);
        System.arraycopy(defenderLosses, 0, this.defenderLosses, 0, this.defenderLosses.length);
        Arrays.fill(this.attackerLossesEv, 0d);
        Arrays.fill(this.defenderLossesEv, 0d);
        this.infraDestroyed = infraDestroyed;
        this.loot = loot;
        this.attackerResistanceDelta = attackerResistanceDelta;
        this.defenderResistanceDelta = defenderResistanceDelta;
        this.mapCost = mapCost;
        copyConsumption(consumption);
        this.controlDelta = controlDelta == null ? ControlFlagDelta.NONE : controlDelta;
    }

    void setExpected(
            SuccessType success,
            double[] attackerLossesEv,
            double[] defenderLossesEv,
            double infraDestroyed,
            double loot,
            double attackerResistanceDelta,
            double defenderResistanceDelta,
            int mapCost,
            double[] consumption,
            ControlFlagDelta controlDelta
    ) {
        this.success = Objects.requireNonNull(success, "success");
        this.mode = ResolutionMode.DETERMINISTIC_EV;
        System.arraycopy(attackerLossesEv, 0, this.attackerLossesEv, 0, this.attackerLossesEv.length);
        System.arraycopy(defenderLossesEv, 0, this.defenderLossesEv, 0, this.defenderLossesEv.length);
        Arrays.fill(this.attackerLosses, 0);
        Arrays.fill(this.defenderLosses, 0);
        this.infraDestroyed = infraDestroyed;
        this.loot = loot;
        this.attackerResistanceDelta = attackerResistanceDelta;
        this.defenderResistanceDelta = defenderResistanceDelta;
        this.mapCost = mapCost;
        copyConsumption(consumption);
        this.controlDelta = controlDelta == null ? ControlFlagDelta.NONE : controlDelta;
    }

    private void copyConsumption(double[] source) {
        if (source == null) {
            Arrays.fill(this.consumption, 0d);
            return;
        }
        int len = Math.min(source.length, this.consumption.length);
        System.arraycopy(source, 0, this.consumption, 0, len);
        if (len < this.consumption.length) {
            Arrays.fill(this.consumption, len, this.consumption.length, 0d);
        }
    }

    public SuccessType success() {
        return success;
    }

    public ResolutionMode mode() {
        return mode;
    }

    public int[] attackerLosses() {
        return attackerLosses;
    }

    public int[] defenderLosses() {
        return defenderLosses;
    }

    public double[] attackerLossesEv() {
        return attackerLossesEv;
    }

    public double[] defenderLossesEv() {
        return defenderLossesEv;
    }

    public double infraDestroyed() {
        return infraDestroyed;
    }

    public double loot() {
        return loot;
    }

    public double attackerResistanceDelta() {
        return attackerResistanceDelta;
    }

    public double defenderResistanceDelta() {
        return defenderResistanceDelta;
    }

    public int mapCost() {
        return mapCost;
    }

    public double[] consumption() {
        return consumption;
    }

    public ControlFlagDelta controlDelta() {
        return controlDelta;
    }

    public AttackOutcome toAttackOutcome() {
        if (mode == ResolutionMode.DETERMINISTIC_EV) {
            return AttackOutcome.expected(
                    success,
                    attackerLossesEv,
                    defenderLossesEv,
                    infraDestroyed,
                    loot,
                    attackerResistanceDelta,
                    defenderResistanceDelta,
                    mapCost,
                    consumption,
                    controlDelta
            );
        }
        return AttackOutcome.discrete(
                success,
                mode,
                attackerLosses,
                defenderLosses,
                infraDestroyed,
                loot,
                attackerResistanceDelta,
                defenderResistanceDelta,
                mapCost,
                consumption,
                controlDelta
        );
    }
}
