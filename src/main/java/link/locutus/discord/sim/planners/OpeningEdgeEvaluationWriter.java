package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;

final class OpeningEdgeEvaluationWriter {
    static final int RETAIN_IMMEDIATE_HARM = 1;
    static final int RETAIN_SELF_EXPOSURE = 1 << 1;
    static final int RETAIN_RESOURCE_SWING = 1 << 2;

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
        if (componentPolicy.retainSelfExposure()) {
            mask |= RETAIN_SELF_EXPOSURE;
        }
        if (componentPolicy.retainResourceSwing()) {
            mask |= RETAIN_RESOURCE_SWING;
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
                (componentMask & RETAIN_SELF_EXPOSURE) != 0 ? evaluation.selfExposure() : 0f,
                (componentMask & RETAIN_RESOURCE_SWING) != 0 ? evaluation.resourceSwing() : 0f,
                evaluation.controlLeverage(),
                evaluation.futureWarLeverage()
        );
    }
}
