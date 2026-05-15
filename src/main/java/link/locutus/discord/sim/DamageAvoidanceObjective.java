package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that favors low-exposure declares while still requiring useful damage. */
final class DamageAvoidanceObjective implements StrategicObjective {

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return new CandidateEdgeComponentPolicy(true, true, false);
    }

    @Override
    public double scoreOpening(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage,
            double targetPressure,
            int teamId
    ) {
        return (0.35d * immediateHarm) - selfExposure;
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        return totals.ownValue()
                - (0.35d * totals.enemyValue())
                + (0.35d * StrategicValueTotals.slotBalanceOf(view, teamId));
    }

    @Override
    public boolean usesWarSlotDenial() {
        return true;
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        return 0.0;
    }

}
