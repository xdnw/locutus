package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;

import java.util.Objects;

final class OpeningCandidateAdmission {
    private final CandidateEdgeAdmissionPolicy admissionPolicy;
    private final OpeningEvaluator.ProbeResult probeResult = new OpeningEvaluator.ProbeResult();
    private final OpeningEvaluator.SpecialistProbeResult specialistProbeResult = new OpeningEvaluator.SpecialistProbeResult();
    private final OpeningEvaluator.ViabilityProbeEvaluator viabilityProbeEvaluator = new OpeningEvaluator.ViabilityProbeEvaluator();
    private final OpeningEvaluator.SpecialistProbeEvaluator specialistProbeEvaluator = new OpeningEvaluator.SpecialistProbeEvaluator();

    OpeningCandidateAdmission(CandidateEdgeAdmissionPolicy admissionPolicy) {
        this.admissionPolicy = Objects.requireNonNull(admissionPolicy, "admissionPolicy");
    }

    boolean admit(DBNationSnapshot attacker, DBNationSnapshot defender) {
        return OpeningEvaluator.admitCandidate(
                attacker,
                defender,
                admissionPolicy,
                probeResult,
                specialistProbeResult,
                viabilityProbeEvaluator,
                specialistProbeEvaluator
        );
    }

    boolean admitPositiveOpeningBaseline() {
        return admissionPolicy.admitPositiveOpeningBaseline();
    }

    float probe() {
        return probeResult.probe();
    }

    byte preferredWarTypeId() {
        return probeResult.preferredWarTypeId();
    }

    byte bestAttackTypeId() {
        return probeResult.bestAttackTypeId();
    }
}
