package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.TeamScoreObjective;

import java.util.Collection;
import java.util.List;
import java.util.Map;

final class PlannerConflictExecutor {
    private PlannerConflictExecutor() {
    }

    static PlannerProjectionResult projectAssignmentHorizon(
            SimTuning tuning,
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns
    ) {
        return projectAssignmentHorizon(
                tuning,
                overrides,
                nations,
                assignment,
                horizonTurns,
                PlannerTransitionSemantics.NONE
        );
    }

    static PlannerProjectionResult projectAssignmentHorizon(
            SimTuning tuning,
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns,
            PlannerTransitionSemantics transitionSemantics
    ) {
        return projectAssignmentStateHorizon(
                tuning,
                overrides,
                nations,
                assignment,
                horizonTurns,
                transitionSemantics
        ).toProjectionResult();
    }

    static PlannerProjectionState projectAssignmentStateHorizon(
            SimTuning tuning,
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns
    ) {
        return projectAssignmentStateHorizon(
                tuning,
                overrides,
                nations,
                assignment,
                horizonTurns,
                PlannerTransitionSemantics.NONE
        );
    }

    static PlannerProjectionState projectAssignmentStateHorizon(
            SimTuning tuning,
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns,
            PlannerTransitionSemantics transitionSemantics
    ) {
        PlannerProjectionState seed = PlannerProjectionState.seed(overrides, nations);
        return seed.advance(tuning, assignment, horizonTurns, transitionSemantics);
    }

    static double scoreAssignment(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            Map<Integer, List<Integer>> assignment,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders
    ) {
        if (assignment.isEmpty()) {
            return 0.0;
        }
        PlannerLocalConflict conflict = PlannerLocalConflict.create(overrides, attackers, defenders, tuning);
        int attackerTeamId = attackers.isEmpty() ? 1 : attackers.get(0).teamId();
        conflict.applyAssignmentOpenings(assignment);
        return objective.scoreTerminal(conflict, attackerTeamId);
    }

    static double scoreAssignment(
            PlannerLocalConflict conflict,
            TeamScoreObjective objective,
            int attackerTeamId,
            Map<Integer, List<Integer>> assignment
    ) {
        if (assignment.isEmpty()) {
            return 0.0;
        }

        PlannerLocalConflict.Mark mark = conflict.mark();
        try {
            conflict.applyAssignmentOpenings(assignment);
            return objective.scoreTerminal(conflict, attackerTeamId);
        } finally {
            conflict.rollback(mark);
        }
    }

    static double scoreAssignmentDelta(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            Map<Integer, List<Integer>> currentAssignment,
            Map<Integer, List<Integer>> candidateAssignment,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int attackerTeamId
    ) {
        PlannerConflictBundle bundle = PlannerConflictBundle.extract(
                currentAssignment,
                candidateAssignment,
                attackers,
                defenders
        );
        if (bundle.isEmpty()) {
            return 0.0;
        }

        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                overrides,
                bundle.attackers(),
                bundle.defenders(),
                tuning
        );
        double currentScore = scoreAssignment(conflict, objective, attackerTeamId, bundle.currentAssignment());
        double candidateScore = scoreAssignment(conflict, objective, attackerTeamId, bundle.candidateAssignment());
        return candidateScore - currentScore;
    }

    static double evaluateDeclaredWar(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int horizonTurns
    ) {
        PlannerExactValidatorScripts scripts = PlannerExactValidatorScripts.DEFAULT;
        return evaluateDeclaredWarInternal(
                tuning,
                overrides,
                objective,
                attacker,
                defender,
                horizonTurns,
                scripts,
                PlannerTransitionSemantics.NONE
        );
    }

    static double evaluateDeclaredWar(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int horizonTurns,
            PlannerExactValidatorScripts scripts
    ) {
        PlannerExactValidatorScripts effectiveScripts = scripts == null
                ? PlannerExactValidatorScripts.DEFAULT
                : scripts;
        return evaluateDeclaredWarInternal(
            tuning,
            overrides,
            objective,
            attacker,
            defender,
            horizonTurns,
            effectiveScripts,
            effectiveScripts.transitionSemantics()
        );
    }

            static double evaluateDeclaredWar(
                SimTuning tuning,
                OverrideSet overrides,
                TeamScoreObjective objective,
                DBNationSnapshot attacker,
                DBNationSnapshot defender,
                int horizonTurns,
                PlannerCoordinationPolicy coordinationPolicy
            ) {
            PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
                ? PlannerCoordinationPolicy.NONE
                : coordinationPolicy;
            return evaluateDeclaredWar(
                tuning,
                overrides,
                objective,
                attacker,
                defender,
                horizonTurns,
                effectiveCoordination.applyToDefaultScripts()
            );
            }

            static double evaluateDeclaredWar(
                SimTuning tuning,
                OverrideSet overrides,
                TeamScoreObjective objective,
                DBNationSnapshot attacker,
                DBNationSnapshot defender,
                int horizonTurns,
                PlannerExactValidatorScripts scripts,
                PlannerCoordinationPolicy coordinationPolicy
            ) {
            PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
                ? PlannerCoordinationPolicy.NONE
                : coordinationPolicy;
            return evaluateDeclaredWar(
                tuning,
                overrides,
                objective,
                attacker,
                defender,
                horizonTurns,
                effectiveCoordination.applyTo(scripts)
            );
            }

    private static double evaluateDeclaredWarInternal(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int horizonTurns,
            PlannerExactValidatorScripts effectiveScripts,
            PlannerTransitionSemantics transitionSemantics
    ) {
            if (!effectiveScripts.declareWarScript()) {
                return Double.NEGATIVE_INFINITY;
            }
            PlannerLocalConflict conflict = PlannerLocalConflict.create(
                overrides,
                List.of(attacker),
                List.of(defender),
                tuning,
                transitionSemantics
            );
            conflict.simulateDeclaredWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                horizonTurns,
                effectiveScripts,
                objective,
                attacker.teamId()
            );
            return objective.scoreTerminal(conflict, attacker.teamId());
    }

    static DeclaredWarEvaluation evaluateDeclaredWarDetailed(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int horizonTurns,
            CandidateEdgeComponentPolicy componentPolicy,
            PlannerExactValidatorScripts scripts
    ) {
        PlannerExactValidatorScripts effectiveScripts = scripts == null
                ? PlannerExactValidatorScripts.DEFAULT
                : scripts;
        PlannerTransitionSemantics transitionSemantics = effectiveScripts.transitionSemantics();
        if (!effectiveScripts.declareWarScript()) {
            return DeclaredWarEvaluation.scoreOnly(Double.NEGATIVE_INFINITY);
        }
        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                overrides,
                List.of(attacker),
                List.of(defender),
                tuning,
                transitionSemantics
        );
        conflict.simulateDeclaredWar(
            attacker.nationId(),
            defender.nationId(),
            WarType.ORD,
            horizonTurns,
            effectiveScripts,
            objective,
            attacker.teamId()
        );

        double objectiveScore = objective.scoreTerminal(conflict, attacker.teamId());
        CandidateEdgeComponentPolicy policy = componentPolicy == null
                ? CandidateEdgeComponentPolicy.none()
                : componentPolicy;
        if (!policy.retainsAny()) {
            return DeclaredWarEvaluation.scoreOnly(objectiveScore);
        }

        PlannerProjectionResult projection = conflict.project();
        DBNationSnapshot projectedAttacker = projection.snapshotsById().get(attacker.nationId());
        DBNationSnapshot projectedDefender = projection.snapshotsById().get(defender.nationId());
        if (projectedAttacker == null || projectedDefender == null) {
            return DeclaredWarEvaluation.scoreOnly(objectiveScore);
        }

        PlannerProjectedWar projectedWar = projectedWar(projection.activeWars(), attacker.nationId(), defender.nationId());

        double immediateHarm = policy.retainImmediateHarm()
                ? defender.score() - projectedDefender.score()
                : 0.0;
        double selfExposure = policy.retainSelfExposure()
            ? attacker.score() - projectedAttacker.score()
                : 0.0;
        double resourceSwing = policy.retainResourceSwing()
                ? projectedAttacker.resource(ResourceType.MONEY) - attacker.resource(ResourceType.MONEY)
                : 0.0;
        double controlLeverage = policy.retainControlLeverage()
            ? controlLeverage(projectedWar)
                : 0.0;
        double futureWarLeverage = policy.retainFutureWarLeverage()
            ? futureWarLeverage(attacker, projectedAttacker, defender, projectedDefender, projectedWar)
                : 0.0;

        return new DeclaredWarEvaluation(
                objectiveScore,
            immediateHarm,
            selfExposure,
            resourceSwing,
            controlLeverage,
            futureWarLeverage
        );
    }

        static DeclaredWarEvaluation evaluateDeclaredWarDetailed(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int horizonTurns,
            CandidateEdgeComponentPolicy componentPolicy,
            PlannerCoordinationPolicy coordinationPolicy
        ) {
        PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
            ? PlannerCoordinationPolicy.NONE
            : coordinationPolicy;
        return evaluateDeclaredWarDetailed(
            tuning,
            overrides,
            objective,
            attacker,
            defender,
            horizonTurns,
            componentPolicy,
            effectiveCoordination.applyToDefaultScripts()
        );
        }

        static DeclaredWarEvaluation evaluateDeclaredWarDetailed(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int horizonTurns,
            CandidateEdgeComponentPolicy componentPolicy,
            PlannerExactValidatorScripts scripts,
            PlannerCoordinationPolicy coordinationPolicy
        ) {
        PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
            ? PlannerCoordinationPolicy.NONE
            : coordinationPolicy;
        return evaluateDeclaredWarDetailed(
            tuning,
            overrides,
            objective,
            attacker,
            defender,
            horizonTurns,
            componentPolicy,
            effectiveCoordination.applyTo(scripts)
        );
        }

    private static double totalInfra(DBNationSnapshot snapshot) {
        double total = 0.0;
        for (double cityInfra : snapshot.cityInfra()) {
            total += cityInfra;
        }
        return total;
    }

    private static PlannerProjectedWar projectedWar(
            List<PlannerProjectedWar> activeWars,
            int attackerNationId,
            int defenderNationId
    ) {
        for (PlannerProjectedWar war : activeWars) {
            if (war.attackerNationId() == attackerNationId && war.defenderNationId() == defenderNationId) {
                return war;
            }
        }
        return null;
    }

    private static double controlLeverage(PlannerProjectedWar war) {
        if (war == null) {
            return 0.0;
        }
        return OpeningMetricSummary.controlLeverage(
                war.groundControlOwner() == PlannerLocalConflict.ControlOwner.ATTACKER,
                war.airSuperiorityOwner() == PlannerLocalConflict.ControlOwner.ATTACKER,
                war.blockadeOwner() == PlannerLocalConflict.ControlOwner.ATTACKER
        );
    }

    private static double futureWarLeverage(
            DBNationSnapshot initialAttacker,
            DBNationSnapshot projectedAttacker,
            DBNationSnapshot initialDefender,
            DBNationSnapshot projectedDefender,
            PlannerProjectedWar projectedWar
    ) {
        boolean attackerHasAirControl = projectedWar != null
                && projectedWar.airSuperiorityOwner() == PlannerLocalConflict.ControlOwner.ATTACKER;
        boolean defenderHasAirControl = projectedWar != null
                && projectedWar.airSuperiorityOwner() == PlannerLocalConflict.ControlOwner.DEFENDER;
        int defenderResistance = projectedWar == null ? 0 : projectedWar.defenderResistance();
        return OpeningMetricSummary.futureWarLeverage(
                OpeningMetricSummary.groundStrength(
                        initialAttacker.unit(MilitaryUnit.SOLDIER),
                        initialAttacker.unit(MilitaryUnit.TANK),
                        defenderHasAirControl
                ),
                OpeningMetricSummary.groundStrength(
                        projectedAttacker.unit(MilitaryUnit.SOLDIER),
                        projectedAttacker.unit(MilitaryUnit.TANK),
                        defenderHasAirControl
                ),
                OpeningMetricSummary.groundStrength(
                        initialDefender.unit(MilitaryUnit.SOLDIER),
                        initialDefender.unit(MilitaryUnit.TANK),
                        attackerHasAirControl
                ),
                OpeningMetricSummary.groundStrength(
                        projectedDefender.unit(MilitaryUnit.SOLDIER),
                        projectedDefender.unit(MilitaryUnit.TANK),
                        attackerHasAirControl
                ),
                initialAttacker.unit(MilitaryUnit.AIRCRAFT),
                projectedAttacker.unit(MilitaryUnit.AIRCRAFT),
                initialDefender.unit(MilitaryUnit.AIRCRAFT),
                projectedDefender.unit(MilitaryUnit.AIRCRAFT),
                initialAttacker.unit(MilitaryUnit.SHIP),
                projectedAttacker.unit(MilitaryUnit.SHIP),
                initialDefender.unit(MilitaryUnit.SHIP),
                projectedDefender.unit(MilitaryUnit.SHIP),
                totalInfra(initialDefender),
                totalInfra(projectedDefender),
                defenderResistance
        );
    }

    record DeclaredWarEvaluation(
            double objectiveScore,
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double futureWarLeverage
    ) {
        static DeclaredWarEvaluation scoreOnly(double objectiveScore) {
            return new DeclaredWarEvaluation(objectiveScore, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
