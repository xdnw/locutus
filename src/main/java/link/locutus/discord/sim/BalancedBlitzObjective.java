package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that blends damage, exposure, control, resources, and follow-up leverage. */
final class BalancedBlitzObjective implements StrategicObjective {

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return CandidateEdgeComponentPolicy.harmOnly();
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return CandidateEdgeAdmissionPolicy.positiveOpeningBaseline();
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        return (totals.ownValue() - totals.enemyValue()) + StrategicValueTotals.slotBalanceOf(view, teamId);
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
