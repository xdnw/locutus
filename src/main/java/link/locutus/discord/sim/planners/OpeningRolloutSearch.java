package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.ResolutionMode;

final class OpeningRolloutSearch {
    private final int maxActionBudget;
    private final OpeningEvaluator.PairAttackContext context = new OpeningEvaluator.PairAttackContext();
    private final AttackScratch scratch = new AttackScratch();
    private final MutableAttackResult result = new MutableAttackResult();
    private final OpeningMetricVector.Mutable currentMetrics = new OpeningMetricVector.Mutable();
    private final OpeningMetricVector.Mutable bestMetrics = new OpeningMetricVector.Mutable();
    private final OpeningMetricVector.Mutable projectedMetrics = new OpeningMetricVector.Mutable();

    OpeningRolloutSearch(int actionBudget) {
        this.maxActionBudget = Math.max(1, actionBudget);
    }

    int maxActionBudget() {
        return maxActionBudget;
    }

    void evaluate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            TeamScoreObjective objective,
            int actionBudget,
            float viabilityProbe,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        out.clear();
        OpeningEvaluator.OpeningBaseline baseline = OpeningEvaluator.OpeningBaseline.from(attacker, defender, viabilityProbe);
        int effectiveActionBudget = Math.max(1, Math.min(maxActionBudget, actionBudget));

        for (WarType warType : OpeningEvaluator.OPENING_WAR_TYPES) {
            evaluateWarType(attacker, defender, baseline, warType, objective, effectiveActionBudget, out);
        }
    }

    private void evaluateWarType(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            OpeningEvaluator.OpeningBaseline baseline,
            WarType warType,
            TeamScoreObjective objective,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        context.bind(attacker, defender, warType);

        byte firstAttackTypeId = (byte) -1;
        currentMetrics.set(0d, 0d, 0d, 0d, 0d, baseline.targetPressure());
        float currentScore = scoreObjective(objective, attacker.teamId(), currentMetrics);

        for (int action = 0; action < actionBudget; action++) {
            float bestNextScore = currentScore;
            AttackType bestType = null;
            bestMetrics.copyFrom(currentMetrics);

            for (AttackType type : OpeningEvaluator.OPENING_ATTACK_TYPES) {
                if (!OpeningEvaluator.isLegalOpeningAttack(context.attacker(), context.attackerMaps(), type)) {
                    continue;
                }
                CombatKernel.resolveInto(context, type, ResolutionMode.DETERMINISTIC_EV, scratch, result);
                OpeningRolloutMetricProjector.project(
                        baseline,
                        context,
                        currentMetrics,
                        result,
                        projectedMetrics
                );
                float projectedScore = scoreObjective(objective, attacker.teamId(), projectedMetrics);
                if (projectedScore > bestNextScore) {
                    bestNextScore = projectedScore;
                    bestType = type;
                    bestMetrics.copyFrom(projectedMetrics);
                }
            }

            if (bestType == null) {
                break;
            }

            CombatKernel.resolveInto(context, bestType, ResolutionMode.DETERMINISTIC_EV, scratch, result);
            context.applyExpectedResult(bestType, result);
            currentMetrics.copyFrom(bestMetrics);
            currentScore = bestNextScore;
            if (firstAttackTypeId < 0) {
                firstAttackTypeId = (byte) bestType.ordinal();
            }
        }

        if (firstAttackTypeId < 0 || !Float.isFinite(currentScore)) {
            return;
        }

        if (currentScore > out.score()) {
            out.set(
                    currentScore,
                    (byte) warType.ordinal(),
                    firstAttackTypeId,
                    (float) currentMetrics.immediateHarm(),
                    (float) currentMetrics.selfExposure(),
                    (float) currentMetrics.resourceSwing(),
                    (float) currentMetrics.controlLeverage(),
                    (float) currentMetrics.futureWarLeverage()
            );
        }
    }

    private float scoreObjective(
            TeamScoreObjective objective,
            int attackerTeamId,
            OpeningMetricVector metrics
    ) {
        return (float) objective.scoreOpening(metrics, attackerTeamId);
    }
}