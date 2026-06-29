package link.locutus.discord.sim;

import link.locutus.discord.sim.actions.SimAction;

/** Objective that reads strategic value from projected strategic totals. */
final class BlitzStrategicObjective implements StrategicObjective {
    private static final double ACTIONABLE_SLOT_WEIGHT = 1.5d;

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return CandidateEdgeComponentPolicy.harmOnly();
    }

    @Override
    public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
        return new CandidateEdgeAdmissionPolicy(
                CandidateEdgeAdmissionPolicy.DEFAULT_MINIMUM_VIABILITY_PROBE,
                true
        );
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        return (totals.ownValue() - totals.enemyValue())
                + (ACTIONABLE_SLOT_WEIGHT * StrategicValueTotals.slotBalanceOf(view, teamId));
    }

    @Override
    public double scoreTerminalComparison(StrategicValueView view, int teamId, int opposingTeamId) {
        return scoreTerminal(view, teamId) - scoreTerminal(view, opposingTeamId);
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
