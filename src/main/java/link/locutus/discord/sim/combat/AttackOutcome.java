package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;

import java.util.Arrays;
import java.util.Objects;

public record AttackOutcome(
        SuccessType success,
        ResolutionMode mode,
        int[] attackerLosses,
        int[] defenderLosses,
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
    public AttackOutcome {
        Objects.requireNonNull(success, "success");
        Objects.requireNonNull(mode, "mode");

        if (mode == ResolutionMode.DETERMINISTIC_EV) {
            if (attackerLosses != null || defenderLosses != null) {
                throw new IllegalArgumentException("DETERMINISTIC_EV outcomes must not contain discrete loss arrays");
            }
            requireSized(attackerLossesEv, "attackerLossesEv");
            requireSized(defenderLossesEv, "defenderLossesEv");
        } else {
            requireSized(attackerLosses, "attackerLosses");
            requireSized(defenderLosses, "defenderLosses");
            if (attackerLossesEv != null || defenderLossesEv != null) {
                throw new IllegalArgumentException("Discrete outcomes must not contain EV loss arrays");
            }
        }

        if (mapCost < 0) {
            throw new IllegalArgumentException("mapCost must be >= 0");
        }

        attackerResistanceDelta = clampResistanceDelta(attackerResistanceDelta);
        defenderResistanceDelta = clampResistanceDelta(defenderResistanceDelta);

        attackerLosses = attackerLosses == null ? null : attackerLosses.clone();
        defenderLosses = defenderLosses == null ? null : defenderLosses.clone();
        attackerLossesEv = attackerLossesEv == null ? null : attackerLossesEv.clone();
        defenderLossesEv = defenderLossesEv == null ? null : defenderLossesEv.clone();
        consumption = consumption == null ? new double[ResourceType.values.length] : consumption.clone();
        controlDelta = controlDelta == null ? ControlFlagDelta.NONE : controlDelta;
    }

    public static AttackOutcome discrete(
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
        if (mode == ResolutionMode.DETERMINISTIC_EV) {
            throw new IllegalArgumentException("Use expected(...) for DETERMINISTIC_EV outcomes");
        }
        return new AttackOutcome(
                success,
                mode,
                attackerLosses,
                defenderLosses,
                null,
                null,
                infraDestroyed,
                loot,
                attackerResistanceDelta,
                defenderResistanceDelta,
                mapCost,
                consumption,
                controlDelta
        );
    }

    public static AttackOutcome expected(
            SuccessType successHint,
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
        return new AttackOutcome(
                successHint,
                ResolutionMode.DETERMINISTIC_EV,
                null,
                null,
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

    public boolean isDiscrete() {
        return mode != ResolutionMode.DETERMINISTIC_EV;
    }

    public boolean isExpectedValue() {
        return mode == ResolutionMode.DETERMINISTIC_EV;
    }

    public int attackerLoss(MilitaryUnit unit) {
        requireDiscrete();
        return attackerLosses[unit.ordinal()];
    }

    public int defenderLoss(MilitaryUnit unit) {
        requireDiscrete();
        return defenderLosses[unit.ordinal()];
    }

    public double attackerLossEv(MilitaryUnit unit) {
        return isExpectedValue() ? attackerLossesEv[unit.ordinal()] : attackerLosses[unit.ordinal()];
    }

    public double defenderLossEv(MilitaryUnit unit) {
        return isExpectedValue() ? defenderLossesEv[unit.ordinal()] : defenderLosses[unit.ordinal()];
    }

    @Override
    public int[] attackerLosses() {
        return attackerLosses == null ? null : attackerLosses.clone();
    }

    @Override
    public int[] defenderLosses() {
        return defenderLosses == null ? null : defenderLosses.clone();
    }

    @Override
    public double[] attackerLossesEv() {
        return attackerLossesEv == null ? null : attackerLossesEv.clone();
    }

    @Override
    public double[] defenderLossesEv() {
        return defenderLossesEv == null ? null : defenderLossesEv.clone();
    }

    @Override
    public double[] consumption() {
        return consumption.clone();
    }

    private void requireDiscrete() {
        if (!isDiscrete()) {
            throw new IllegalStateException("This AttackOutcome only has expected-value losses");
        }
    }

    private static void requireSized(int[] values, String name) {
        if (values == null || values.length != MilitaryUnit.values.length) {
            throw new IllegalArgumentException(name + " must be sized to MilitaryUnit.values.length");
        }
    }

    private static void requireSized(double[] values, String name) {
        if (values == null || values.length != MilitaryUnit.values.length) {
            throw new IllegalArgumentException(name + " must be sized to MilitaryUnit.values.length");
        }
    }

    private static double clampResistanceDelta(double value) {
        if (value < -100d) return -100d;
        if (value > 100d) return 100d;
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttackOutcome other)) return false;
        return success == other.success
                && mode == other.mode
                && Double.compare(infraDestroyed, other.infraDestroyed) == 0
                && Double.compare(loot, other.loot) == 0
                && Double.compare(attackerResistanceDelta, other.attackerResistanceDelta) == 0
                && Double.compare(defenderResistanceDelta, other.defenderResistanceDelta) == 0
                && mapCost == other.mapCost
                && Arrays.equals(attackerLosses, other.attackerLosses)
                && Arrays.equals(defenderLosses, other.defenderLosses)
                && Arrays.equals(attackerLossesEv, other.attackerLossesEv)
                && Arrays.equals(defenderLossesEv, other.defenderLossesEv)
            && Arrays.equals(consumption, other.consumption)
                && controlDelta.equals(other.controlDelta);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                success,
                mode,
                infraDestroyed,
                loot,
                attackerResistanceDelta,
                defenderResistanceDelta,
                mapCost,
                controlDelta
        );
        result = 31 * result + Arrays.hashCode(attackerLosses);
        result = 31 * result + Arrays.hashCode(defenderLosses);
        result = 31 * result + Arrays.hashCode(attackerLossesEv);
        result = 31 * result + Arrays.hashCode(defenderLossesEv);
        result = 31 * result + Arrays.hashCode(consumption);
        return result;
    }
}
