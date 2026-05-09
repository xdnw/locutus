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
    public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
        return metrics.immediateHarm() - metrics.selfExposure();
    }

    @Override
    public double scoreTerminal(StrategicValueView view, int teamId) {
        StrategicValueTotals totals = StrategicValueTotals.of(view, teamId);
        double score = totals.ownValue() - totals.enemyValue();
        if (view instanceof TeamWarControlView controlView) {
            score += controlView.controlCompositeScoreForTeam(
                    teamId,
                    new TeamWarControlView.ControlComponentWeights(1.0d, 1.0d, 0.0d, 1.0d, 1.0d, 1.0d)
            );
        }
        return score;
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
