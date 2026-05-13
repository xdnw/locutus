package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;

/**
 * Attack selector that scores each resolved attack candidate through the side objective.
 */
public final class ObjectiveDrivenAttackChoicePolicy implements AttackChoicePolicy {
    private static final int DEFAULT_TEAM_ID = 0;

    private final StrategicObjective objective;
    private final SideOpeningSettings openingSettings;
    private final int teamId;
    private final OpeningMetricVector.Mutable metrics = new OpeningMetricVector.Mutable();

    public ObjectiveDrivenAttackChoicePolicy(StrategicObjective objective, SideOpeningSettings openingSettings) {
        this(objective, openingSettings, DEFAULT_TEAM_ID);
    }

    public ObjectiveDrivenAttackChoicePolicy(
            StrategicObjective objective,
            SideOpeningSettings openingSettings,
            int teamId
    ) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        this.objective = objective;
        this.openingSettings = openingSettings == null ? SideOpeningSettings.legacy(objective) : openingSettings;
        this.teamId = teamId;
    }

    @Override
    public AttackType chooseAttackType(AttackChoiceContext context) {
        AttackType bestAttackType = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AttackType attackType : context.attackTypes()) {
            AttackCandidate candidate = context.candidateFactory().candidate(attackType);
            if (!candidate.legal()) {
                continue;
            }
            int mapCost = candidate.mapCost();
            if (mapCost <= 0 || mapCost > context.mapsAvailable()) {
                continue;
            }
            double score = scoreCandidate(attackType, candidate);
            if (bestAttackType == null
                    || score > bestScore
                    || (score == bestScore && attackType.ordinal() < bestAttackType.ordinal())) {
                bestAttackType = attackType;
                bestScore = score;
            }
        }
        return bestScore > 0d ? bestAttackType : null;
    }

    double scoreCandidate(AttackType attackType, AttackCandidate candidate) {
        metrics.set(
                Math.max(0d, candidate.defenderUnitDamage()),
                Math.max(0d, candidate.attackerUnitDamage()),
                0d,
                controlLeverage(candidate.controlDelta()),
                tacticalMomentum(candidate.defenderResistanceDelta()),
                forceWindowLeverage(candidate.defenderResistanceDelta()),
                0d
        );
        return objective.scoreOpening(metrics, teamId) * openingSettings.attackTypeWeight(attackType);
    }

    private static double controlLeverage(SuperiorityFlagDelta controlDelta) {
        SuperiorityFlagDelta delta = controlDelta == null ? SuperiorityFlagDelta.NONE : controlDelta;
        double leverage = 0d;
        leverage += positiveFlag(delta.groundSuperiority());
        leverage += positiveFlag(delta.airSuperiority());
        leverage += positiveFlag(delta.blockade());
        leverage += delta.clearGroundSuperiority() ? 1d : 0d;
        leverage += delta.clearAirSuperiority() ? 1d : 0d;
        leverage += delta.clearBlockade() ? 1d : 0d;
        return leverage;
    }

    private static double positiveFlag(int value) {
        return value > 0 ? 1d : 0d;
    }

    private static double tacticalMomentum(double defenderResistanceDelta) {
        return clamp01(-defenderResistanceDelta / 100d);
    }

    private static double forceWindowLeverage(double defenderResistanceDelta) {
        return tacticalMomentum(defenderResistanceDelta);
    }

    private static double clamp01(double value) {
        if (value <= 0d) {
            return 0d;
        }
        if (value >= 1d) {
            return 1d;
        }
        return value;
    }
}
