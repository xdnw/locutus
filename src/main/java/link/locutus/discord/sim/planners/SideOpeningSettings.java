package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.StrategicObjective;

import java.util.Arrays;

public record SideOpeningSettings(
        double[] warTypeWeights,
        double[] attackTypeWeights,
        CandidateEdgeAdmissionPolicy admissionPolicy
) {
    public SideOpeningSettings {
        if (warTypeWeights == null || warTypeWeights.length != WarType.values.length) {
            throw new IllegalArgumentException("warTypeWeights must match WarType.values length");
        }
        if (attackTypeWeights == null || attackTypeWeights.length != AttackType.values.length) {
            throw new IllegalArgumentException("attackTypeWeights must match AttackType.values length");
        }
        for (double weight : warTypeWeights) {
            if (!Double.isFinite(weight) || weight < 0d) {
                throw new IllegalArgumentException("warTypeWeights must be finite and non-negative");
            }
        }
        for (double weight : attackTypeWeights) {
            if (!Double.isFinite(weight) || weight < 0d) {
                throw new IllegalArgumentException("attackTypeWeights must be finite and non-negative");
            }
        }
        if (admissionPolicy == null) {
            throw new IllegalArgumentException("admissionPolicy must not be null");
        }
        warTypeWeights = Arrays.copyOf(warTypeWeights, warTypeWeights.length);
        attackTypeWeights = Arrays.copyOf(attackTypeWeights, attackTypeWeights.length);
    }

    public static SideOpeningSettings legacy(StrategicObjective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        double[] warTypeWeights = new double[WarType.values.length];
        Arrays.fill(warTypeWeights, 1d);
        double[] attackTypeWeights = new double[AttackType.values.length];
        Arrays.fill(attackTypeWeights, 1d);
        return new SideOpeningSettings(warTypeWeights, attackTypeWeights, objective.candidateEdgeAdmissionPolicy());
    }

    public double warTypeWeight(WarType warType) {
        return warTypeWeights[warType.ordinal()];
    }

    public double attackTypeWeight(AttackType attackType) {
        return attackTypeWeights[attackType.ordinal()];
    }

    public double minimumViabilityProbe() {
        return admissionPolicy.minimumViabilityProbe();
    }
}