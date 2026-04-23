package link.locutus.discord.sim;
import link.locutus.discord.sim.actions.SimAction;

/**
 * Damage objective: maximize net damage dealt (in score-equivalent units).
 *
 * Terminal score = Σ(own-team score remaining) − Σ(enemy-team score remaining)
 *
 * This is a proxy: own score goes up when units/infra are preserved; enemy score goes down
 * when damage is dealt. Without stored baselines the absolute value is meaningless across
 * scenarios, but it orders assignments correctly within a single sim run.
 *
 * Step-wise scoring is unsupported here (returns 0.0); use metrics-backed objectives once
 * SimMetrics is added in a later milestone.
 */
public class DamageObjective implements TeamScoreObjective {

    @Override
    public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
        return CandidateEdgeComponentPolicy.harmExposureOnly();
    }

    @Override
    public double scoreOpening(OpeningMetricVector metrics, int teamId) {
        return metrics.immediateHarm() - metrics.selfExposure();
    }

    @Override
    public double scoreTerminal(TeamScoreView view, int teamId) {
        final double[] totals = new double[2];
        view.forEachNation((nationId, nationTeamId, score) -> {
            if (nationTeamId == teamId) {
                totals[0] += score;
            } else {
                totals[1] += score;
            }
        });
        return totals[0] - totals[1];
    }

    @Override
    public double scoreAction(SimWorld world, SimAction action, int teamId) {
        // Step-wise scoring requires SimMetrics (future milestone); no-op for now.
        return 0.0;
    }
}
