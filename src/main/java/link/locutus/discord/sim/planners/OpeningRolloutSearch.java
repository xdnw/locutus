package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.ResolutionMode;

final class OpeningRolloutSearch {
    private final int maxActionBudget;
    private final OpeningEvaluator.PairAttackContext context = new OpeningEvaluator.PairAttackContext();
    private final AttackScratch scratch = new AttackScratch();
    private final MutableAttackResult result = new MutableAttackResult();
    private final MutableAttackResult bestResult = new MutableAttackResult();
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
            StrategicObjective objective,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        evaluate(
            attacker,
            defender,
            objective,
            null,
            OpeningMetricSummary.defenderControlPressure(defender),
            actionBudget,
            out
        );
        }

    void evaluate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            StrategicObjective objective,
            SideOpeningSettings openingSettings,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        evaluate(
                attacker,
                defender,
                objective,
                openingSettings,
                OpeningMetricSummary.defenderControlPressure(defender),
                actionBudget,
                out
        );
    }

        void evaluate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            StrategicObjective objective,
            SideOpeningSettings openingSettings,
            double targetPressure,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
        ) {
        out.clear();
        OpeningEvaluator.OpeningBaseline baseline = OpeningEvaluator.OpeningBaseline.from(attacker, defender, targetPressure);
        int effectiveActionBudget = Math.max(1, Math.min(maxActionBudget, actionBudget));

        for (WarType warType : OpeningEvaluator.OPENING_WAR_TYPES) {
            evaluateWarType(attacker, defender, baseline, warType, objective, openingSettings, effectiveActionBudget, out);
        }
    }

    private void evaluateWarType(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            OpeningEvaluator.OpeningBaseline baseline,
            WarType warType,
            StrategicObjective objective,
            SideOpeningSettings openingSettings,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        context.bind(attacker, defender, warType);

        byte firstAttackTypeId = (byte) -1;
        byte fallbackAttackTypeId = (byte) -1;
        currentMetrics.set(0d, 0d, 0d, 0d, 0d, baseline.targetPressure());
        float currentScore = scoreObjective(objective, attacker.teamId(), currentMetrics, warType, null, openingSettings);

        for (int action = 0; action < actionBudget; action++) {
            float bestNextScore = currentScore;
            AttackType bestType = null;
            bestMetrics.copyFrom(currentMetrics);

            for (AttackType type : OpeningEvaluator.OPENING_ATTACK_TYPES) {
                if (!OpeningEvaluator.isLegalOpeningAttack(context.attacker(), context.attackerMaps(), type)) {
                    continue;
                }
                if (fallbackAttackTypeId < 0) {
                    fallbackAttackTypeId = (byte) type.ordinal();
                }
                CombatKernel.resolveInto(context, type, ResolutionMode.DETERMINISTIC_EV, scratch, result);
                OpeningRolloutMetricProjector.project(
                        baseline,
                        context,
                        currentMetrics,
                        result,
                        projectedMetrics
                );
                    AttackType openingAttackType = firstAttackTypeId < 0 ? type : AttackType.values[firstAttackTypeId];
                    float projectedScore = scoreObjective(
                        objective,
                        attacker.teamId(),
                        projectedMetrics,
                        warType,
                        openingAttackType,
                        openingSettings
                    );
                if (projectedScore > bestNextScore) {
                    bestNextScore = projectedScore;
                    bestType = type;
                    bestMetrics.copyFrom(projectedMetrics);
                    bestResult.copyFrom(result);
                }
            }

            if (bestType == null) {
                break;
            }

            context.applyExpectedResult(bestType, bestResult);
            currentMetrics.copyFrom(bestMetrics);
            currentScore = bestNextScore;
            if (firstAttackTypeId < 0) {
                firstAttackTypeId = (byte) bestType.ordinal();
            }
        }

        // Emit if the score is positive and beats whatever the best war type found so far.
        // Primary admitted candidates may still have no improving attack over the declaration
        // baseline; those keep a legal first attack while the cheap low-probe fallback lives in
        // OpeningEvaluator, outside the full rollout path.
        if (firstAttackTypeId < 0) {
            // Zero-action baseline is positive but no improving attack was found.
            firstAttackTypeId = fallbackAttackTypeId;
            if (firstAttackTypeId < 0) {
                return; // no legal attacks at all — cannot declare
            }
            currentScore = scoreObjective(
                    objective,
                    attacker.teamId(),
                    currentMetrics,
                    warType,
                    AttackType.values[firstAttackTypeId],
                    openingSettings
            );
        }
        if (!Float.isFinite(currentScore) || currentScore <= 0f || currentScore <= out.score()) {
            return;
        }
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

    private float scoreObjective(
            StrategicObjective objective,
            int attackerTeamId,
            OpeningMetricVector metrics,
            WarType warType,
            AttackType openingAttackType,
            SideOpeningSettings openingSettings
    ) {
        float baseScore = (float) objective.scoreOpening(
            metrics.immediateHarm(),
            metrics.selfExposure(),
            metrics.resourceSwing(),
            metrics.controlLeverage(),
            metrics.futureWarLeverage(),
            metrics.targetPressure(),
            attackerTeamId
        );
        if (openingSettings == null) {
            return baseScore;
        }
        double weightedScore = baseScore * openingSettings.warTypeWeight(warType);
        if (openingAttackType != null) {
            weightedScore *= openingSettings.attackTypeWeight(openingAttackType);
        }
        return (float) weightedScore;
    }
}
