package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
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
    private double currentImmediateHarm;
    private double currentResourceSwing;
    private double bestImmediateHarm;
    private double bestResourceSwing;

    OpeningRolloutSearch(int actionBudget) {
        this.maxActionBudget = Math.max(1, actionBudget);
    }

    int maxActionBudget() {
        return maxActionBudget;
    }

    void evaluate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        evaluate(
            attacker,
            defender,
            null,
            OpeningMetricSummary.defenderControlPressure(defender),
            actionBudget,
            out
        );
        }

    void evaluate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            SideOpeningSettings openingSettings,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        evaluate(
                attacker,
                defender,
                openingSettings,
                OpeningMetricSummary.defenderControlPressure(defender),
                actionBudget,
                out
        );
    }

        void evaluate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            SideOpeningSettings openingSettings,
            double targetPressure,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
        ) {
        out.clear();
        OpeningEvaluator.OpeningBaseline baseline = OpeningEvaluator.OpeningBaseline.from(attacker, defender, targetPressure);
        int effectiveActionBudget = Math.max(1, Math.min(maxActionBudget, actionBudget));

        for (WarType warType : OpeningEvaluator.OPENING_WAR_TYPES) {
            evaluateWarType(attacker, defender, baseline, warType, openingSettings, effectiveActionBudget, out);
        }
    }

    private void evaluateWarType(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            OpeningEvaluator.OpeningBaseline baseline,
            WarType warType,
            SideOpeningSettings openingSettings,
            int actionBudget,
            OpeningEvaluator.EdgeEvaluation out
    ) {
        context.bind(attacker, defender, warType);

        byte firstAttackTypeId = (byte) -1;
        byte fallbackAttackTypeId = (byte) -1;
        currentImmediateHarm = 0d;
        currentResourceSwing = 0d;
        double currentScore = scoreObjective(currentImmediateHarm, currentResourceSwing, warType, null, openingSettings);

        for (int action = 0; action < actionBudget; action++) {
            double bestNextScore = currentScore;
            AttackType bestType = null;
            bestImmediateHarm = currentImmediateHarm;
            bestResourceSwing = currentResourceSwing;

            for (AttackType type : OpeningEvaluator.OPENING_ATTACK_TYPES) {
                if (!OpeningEvaluator.isLegalOpeningAttack(context.attacker(), context.attackerMaps(), type)) {
                    continue;
                }
                if (fallbackAttackTypeId < 0) {
                    fallbackAttackTypeId = (byte) type.ordinal();
                }
                CombatKernel.resolveInto(context, type, ResolutionMode.DETERMINISTIC_EV, scratch, result);
                double projectedImmediateHarm = currentImmediateHarm
                        + OpeningEvaluator.projectedImmediateHarm(baseline, context, result);
                double projectedResourceSwing = currentResourceSwing
                        + AttackObjectiveComponentMapper.resourceSwingForObjective(type, result.loot());
                AttackType openingAttackType = firstAttackTypeId < 0 ? type : AttackType.values[firstAttackTypeId];
                double projectedScore = scoreObjective(
                        projectedImmediateHarm,
                        projectedResourceSwing,
                        warType,
                        openingAttackType,
                        openingSettings
                );
                if (projectedScore > bestNextScore) {
                    bestNextScore = projectedScore;
                    bestType = type;
                    bestImmediateHarm = projectedImmediateHarm;
                    bestResourceSwing = projectedResourceSwing;
                    bestResult.copyFrom(result);
                }
            }

            if (bestType == null) {
                break;
            }

            context.applyExpectedResult(bestType, bestResult);
            currentImmediateHarm = bestImmediateHarm;
            currentResourceSwing = bestResourceSwing;
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
                    currentImmediateHarm,
                    currentResourceSwing,
                    warType,
                    AttackType.values[firstAttackTypeId],
                    openingSettings
            );
        }
        if (!Double.isFinite(currentScore) || currentScore <= 0d || currentScore <= out.score()) {
            return;
        }
        out.set(
                (float) currentScore,
                (byte) warType.ordinal(),
                firstAttackTypeId,
                (float) currentImmediateHarm,
                (float) currentResourceSwing
        );
    }

    private double scoreObjective(
            double immediateHarm,
            double resourceSwing,
            WarType warType,
            AttackType openingAttackType,
            SideOpeningSettings openingSettings
    ) {
        return OpeningEvaluator.scoreShellHeuristic(
            immediateHarm,
            resourceSwing,
            warType,
            openingAttackType,
            openingSettings
        );
    }
}
