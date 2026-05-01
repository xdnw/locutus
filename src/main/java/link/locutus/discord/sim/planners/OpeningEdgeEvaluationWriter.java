package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;

final class OpeningEdgeEvaluationWriter {
    private OpeningEdgeEvaluationWriter() {
    }

    static void retainComponents(
            OpeningEvaluator.EdgeEvaluation evaluation,
            CandidateEdgeComponentPolicy componentPolicy
    ) {
        if (!Float.isFinite(evaluation.score())) {
            return;
        }
        CandidateEdgeComponentPolicy policy = componentPolicy == null
                ? CandidateEdgeComponentPolicy.none()
                : componentPolicy;
        evaluation.set(
                evaluation.score(),
                evaluation.preferredWarTypeId(),
                evaluation.firstAttackTypeId(),
                policy.retainImmediateHarm() ? evaluation.immediateHarm() : 0f,
                policy.retainSelfExposure() ? evaluation.selfExposure() : 0f,
                policy.retainResourceSwing() ? evaluation.resourceSwing() : 0f,
                policy.retainControlLeverage() ? evaluation.controlLeverage() : 0f,
                policy.retainFutureWarLeverage() ? evaluation.futureWarLeverage() : 0f
        );
    }
}