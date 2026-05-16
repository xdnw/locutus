package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;

final class OpeningEdgeEvaluationWriter {
    static final int RETAIN_IMMEDIATE_HARM = 1;

    private OpeningEdgeEvaluationWriter() {
    }

    static int componentMask(CandidateEdgeComponentPolicy componentPolicy) {
        if (componentPolicy == null) {
            return 0;
        }
        int mask = 0;
        if (componentPolicy.retainImmediateHarm()) {
            mask |= RETAIN_IMMEDIATE_HARM;
        }
        return mask;
    }

    static void retainComponents(OpeningEvaluator.EdgeEvaluation evaluation, int componentMask) {
        if (!Float.isFinite(evaluation.score()) || componentMask == 0) {
            return;
        }
        evaluation.set(
                evaluation.score(),
                evaluation.preferredWarTypeId(),
                evaluation.firstAttackTypeId(),
                (componentMask & RETAIN_IMMEDIATE_HARM) != 0 ? evaluation.immediateHarm() : 0f,
                evaluation.resourceSwing()
        );
    }
}
