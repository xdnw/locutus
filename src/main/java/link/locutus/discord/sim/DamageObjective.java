package link.locutus.discord.sim;
import link.locutus.discord.sim.actions.SimAction;

/**
 * Damage objective: maximize net strategic asset damage dealt.
 *
 * Terminal score = Σ(own-team strategic value remaining) − Σ(enemy-team strategic value remaining)
 *
 * This deliberately does not use nation score, which is a war-range mechanic rather than an
 * expected-value metric.
 *
 * Step-wise scoring is unsupported here (returns 0.0); use metrics-backed objectives once
 * SimMetrics is added in a later milestone.
 */
public class DamageObjective implements StrategicObjective {

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return CandidateEdgeComponentPolicy.harmExposureOnly();
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
        // Step-wise scoring requires SimMetrics (future milestone); no-op for now.
        return 0.0;
    }
}
