package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicAssetValue;
import link.locutus.discord.sim.TeamScoreObjective;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        return scoreAssignment(tuning, overrides, objective, assignment, attackers, defenders, Map.of());
    }

    static double scoreAssignment(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            Map<Integer, List<Integer>> assignment,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        if (assignment.isEmpty()) {
            return 0.0;
        }
        Map<Integer, StrategicAssetValue.StrategicRelevance> strategicRelevanceByNationId =
                PlannerLocalConflict.strategicRelevanceByNationId(attackers, defenders);
        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                overrides,
                attackers,
                defenders,
                tuning,
                PlannerTransitionSemantics.NONE,
                strategicRelevanceByNationId
        );
        int attackerTeamId = attackers.isEmpty() ? 1 : attackers.get(0).teamId();
        return scoreAssignment(
                conflict,
                objective,
                attackerTeamId,
                orderedAssignmentView(assignment, attackers, warTypeOrdinalsByPair)
        );
    }

    static double scoreAssignment(
            PlannerLocalConflict conflict,
            TeamScoreObjective objective,
            int attackerTeamId,
            PlannerConflictBundle.PlannerAssignmentView assignment
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_SCORE)) {
            PlannerProfiler.addCounter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_SCORE, "assignmentEntries", assignment.edgeCount());
            if (assignment.isEmpty()) {
                return 0.0;
            }

            PlannerLocalConflict.Mark mark = conflict.mark();
            try {
                conflict.evaluateAssignmentOpenings(assignment);
                return objective.scoreTerminal(conflict, attackerTeamId);
            } finally {
                conflict.rollback(mark);
            }
        }
    }

    static double scoreAssignmentDelta(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            Map<Integer, List<Integer>> currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int attackerTeamId
    ) {
        return scoreAssignmentDelta(
                tuning,
                overrides,
                objective,
                currentAssignment,
                candidateChange,
                attackers,
                defenders,
                attackerTeamId,
                Map.of()
        );
    }

    static double scoreAssignmentDelta(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            Map<Integer, List<Integer>> currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int attackerTeamId,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        Map<Integer, StrategicAssetValue.StrategicRelevance> strategicRelevanceByNationId =
                PlannerLocalConflict.strategicRelevanceByNationId(attackers, defenders);
        PlannerConflictBundle bundle = PlannerConflictBundle.extract(
                currentAssignment,
                candidateChange,
                attackers,
                defenders,
                warTypeOrdinalsByPair
        );
        return scoreAssignmentDelta(
                tuning,
                overrides,
                objective,
                attackerTeamId,
                bundle,
                strategicRelevanceByNationId,
                externalStrategicValueByTeam(
                        tuning,
                        overrides,
                        currentAssignment,
                        attackers,
                        defenders,
                        bundle,
                        strategicRelevanceByNationId,
                        warTypeOrdinalsByPair
                )
        );
    }

    static double scoreAssignmentDelta(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            PlannerAssignmentSession currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int attackerTeamId
    ) {
        return scoreAssignmentDelta(
                tuning,
                overrides,
                objective,
                currentAssignment,
                candidateChange,
                attackers,
                defenders,
                attackerTeamId,
                Map.of()
        );
    }

    static double scoreAssignmentDelta(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            PlannerAssignmentSession currentAssignment,
            PlannerAssignmentChange candidateChange,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            int attackerTeamId,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        Map<Integer, StrategicAssetValue.StrategicRelevance> strategicRelevanceByNationId =
                PlannerLocalConflict.strategicRelevanceByNationId(attackers, defenders);
        PlannerConflictBundle bundle = PlannerConflictBundle.extract(
                currentAssignment,
                candidateChange,
                attackers,
                defenders,
                warTypeOrdinalsByPair
        );
        return scoreAssignmentDelta(
                tuning,
                overrides,
                objective,
                attackerTeamId,
                bundle,
                strategicRelevanceByNationId,
                externalStrategicValueByTeam(
                        tuning,
                        overrides,
                        currentAssignment.toAssignmentMap(),
                        attackers,
                        defenders,
                        bundle,
                        strategicRelevanceByNationId,
                        warTypeOrdinalsByPair
                )
        );
    }

    private static double scoreAssignmentDelta(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            int attackerTeamId,
            PlannerConflictBundle bundle,
            Map<Integer, StrategicAssetValue.StrategicRelevance> strategicRelevanceByNationId,
            ExternalStrategicContext externalStrategicContext
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_DELTA)) {
            if (bundle.isEmpty()) {
                return 0.0;
            }

            PlannerProfiler.addCounter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_DELTA, "attackers", bundle.attackers().size());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_DELTA, "defenders", bundle.defenders().size());
            PlannerLocalConflict conflict = PlannerLocalConflict.create(
                    overrides,
                    bundle.attackers(),
                    bundle.defenders(),
                    tuning,
                    PlannerTransitionSemantics.NONE,
                    strategicRelevanceByNationId,
                    externalStrategicContext.valueByTeam(),
                    externalStrategicContext.warControls()
            );
            PlannerConflictBundle.PlannerAssignmentView currentAssignment = bundle.currentAssignment();
            PlannerConflictBundle.PlannerAssignmentView candidateAssignment = bundle.candidateAssignment();
            if (currentAssignment.equals(candidateAssignment)) {
                return 0.0;
            }

            int sharedPrefixEdges = currentAssignment.commonPrefixEdgeCount(candidateAssignment);
            PlannerProfiler.addCounter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_DELTA, "sharedPrefixEdges", sharedPrefixEdges);
            if (sharedPrefixEdges == 0) {
                double currentScore = scoreAssignment(conflict, objective, attackerTeamId, currentAssignment);
                double candidateScore = scoreAssignment(conflict, objective, attackerTeamId, candidateAssignment);
                return candidateScore - currentScore;
            }

            PlannerConflictBundle.PlannerAssignmentView sharedPrefix = currentAssignment.prefixEdges(sharedPrefixEdges);
            PlannerConflictBundle.PlannerAssignmentView currentSuffix = currentAssignment.suffixEdges(sharedPrefixEdges);
            PlannerConflictBundle.PlannerAssignmentView candidateSuffix = candidateAssignment.suffixEdges(sharedPrefixEdges);

            PlannerLocalConflict.Mark sharedMark = conflict.mark();
            try {
                conflict.evaluateAssignmentOpenings(sharedPrefix);
                double currentScore = scoreAssignmentFromCurrentState(conflict, objective, attackerTeamId, currentSuffix);
                double candidateScore = scoreAssignmentFromCurrentState(conflict, objective, attackerTeamId, candidateSuffix);
                return candidateScore - currentScore;
            } finally {
                conflict.rollback(sharedMark);
            }
        }
    }

    private static double scoreAssignmentFromCurrentState(
            PlannerLocalConflict conflict,
            TeamScoreObjective objective,
            int attackerTeamId,
            PlannerConflictBundle.PlannerAssignmentView suffixAssignment
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_SCORE)) {
            PlannerProfiler.addCounter(PlannerProfiler.Scope.EXACT_ASSIGNMENT_SCORE, "assignmentEntries", suffixAssignment.edgeCount());
            PlannerLocalConflict.Mark mark = conflict.mark();
            try {
                conflict.evaluateAssignmentOpenings(suffixAssignment);
                return objective.scoreTerminal(conflict, attackerTeamId);
            } finally {
                conflict.rollback(mark);
            }
        }
    }

    private static PlannerConflictBundle.PlannerAssignmentView orderedAssignmentView(
            Map<Integer, List<Integer>> assignment,
            Collection<DBNationSnapshot> attackers,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        Map<Integer, List<Integer>> orderedAssignment = orderAssignmentByAttackers(assignment, attackers);
        if (orderedAssignment.isEmpty()) {
            return PlannerConflictBundle.PlannerAssignmentView.empty();
        }
        return PlannerConflictBundle.PlannerAssignmentView.fromOrderedAssignment(
                orderedAssignment,
                List.copyOf(orderedAssignment.keySet()),
                warTypeOrdinalsByPair
        );
    }

    private static Map<Integer, List<Integer>> orderAssignmentByAttackers(
            Map<Integer, List<Integer>> assignment,
            Collection<DBNationSnapshot> attackers
    ) {
        if (assignment.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Integer, List<Integer>> ordered = new LinkedHashMap<>(assignment.size());
        for (DBNationSnapshot attacker : attackers) {
            List<Integer> defenderIds = assignment.get(attacker.nationId());
            if (defenderIds != null && !defenderIds.isEmpty()) {
                ordered.put(attacker.nationId(), defenderIds);
            }
        }
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            List<Integer> defenderIds = entry.getValue();
            if (!ordered.containsKey(entry.getKey()) && defenderIds != null && !defenderIds.isEmpty()) {
                ordered.put(entry.getKey(), defenderIds);
            }
        }
        return ordered.isEmpty() ? Map.of() : ordered;
    }

    private static ExternalStrategicContext externalStrategicValueByTeam(
            SimTuning tuning,
            OverrideSet overrides,
            Map<Integer, List<Integer>> currentAssignment,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            PlannerConflictBundle bundle,
            Map<Integer, StrategicAssetValue.StrategicRelevance> strategicRelevanceByNationId,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        if ((attackers == null || attackers.isEmpty()) && (defenders == null || defenders.isEmpty())) {
            return ExternalStrategicContext.empty();
        }
        Map<Integer, DBNationSnapshot> localById = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : bundle.attackers()) {
            localById.put(snapshot.nationId(), snapshot);
        }
        for (DBNationSnapshot snapshot : bundle.defenders()) {
            localById.put(snapshot.nationId(), snapshot);
        }
        Set<Integer> countedNationIds = new LinkedHashSet<>(localById.keySet());
        List<DBNationSnapshot> externalAttackers = externalSnapshots(attackers, countedNationIds);
        List<DBNationSnapshot> externalDefenders = externalSnapshots(defenders, countedNationIds);
        Map<Integer, List<Integer>> externalAssignment = externalAssignment(
                currentAssignment,
                externalAttackers,
                externalDefenders
        );
        if (!externalAssignment.isEmpty()) {
            return projectedExternalStrategicValueByTeam(
                    tuning,
                    overrides,
                    externalAssignment,
                    externalAttackers,
                    externalDefenders,
                    strategicRelevanceByNationId,
                    warTypeOrdinalsByPair
            );
        }
        LinkedHashMap<Integer, Double> totals = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : attackers) {
            accumulateExternalStrategicValue(countedNationIds, totals, snapshot, strategicRelevanceByNationId);
        }
        for (DBNationSnapshot snapshot : defenders) {
            accumulateExternalStrategicValue(countedNationIds, totals, snapshot, strategicRelevanceByNationId);
        }
        return new ExternalStrategicContext(Map.copyOf(totals), List.of());
    }

    private static List<DBNationSnapshot> externalSnapshots(
            Collection<DBNationSnapshot> snapshots,
            Set<Integer> localNationIds
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<DBNationSnapshot> external = new java.util.ArrayList<>(snapshots.size());
        for (DBNationSnapshot snapshot : snapshots) {
            if (snapshot != null && !localNationIds.contains(snapshot.nationId())) {
                external.add(snapshot);
            }
        }
        return List.copyOf(external);
    }

    private static Map<Integer, List<Integer>> externalAssignment(
            Map<Integer, List<Integer>> currentAssignment,
            List<DBNationSnapshot> externalAttackers,
            List<DBNationSnapshot> externalDefenders
    ) {
        if (currentAssignment == null || currentAssignment.isEmpty()
                || externalAttackers.isEmpty()
                || externalDefenders.isEmpty()) {
            return Map.of();
        }
        Set<Integer> externalAttackerIds = new LinkedHashSet<>();
        for (DBNationSnapshot attacker : externalAttackers) {
            externalAttackerIds.add(attacker.nationId());
        }
        Set<Integer> externalDefenderIds = new LinkedHashSet<>();
        for (DBNationSnapshot defender : externalDefenders) {
            externalDefenderIds.add(defender.nationId());
        }
        LinkedHashMap<Integer, List<Integer>> filtered = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : currentAssignment.entrySet()) {
            Integer attackerId = entry.getKey();
            if (attackerId == null || !externalAttackerIds.contains(attackerId)) {
                continue;
            }
            List<Integer> defenderIds = entry.getValue();
            if (defenderIds == null || defenderIds.isEmpty()) {
                continue;
            }
            java.util.ArrayList<Integer> keptDefenders = new java.util.ArrayList<>(defenderIds.size());
            for (Integer defenderId : defenderIds) {
                if (defenderId != null && externalDefenderIds.contains(defenderId)) {
                    keptDefenders.add(defenderId);
                }
            }
            if (!keptDefenders.isEmpty()) {
                filtered.put(attackerId, List.copyOf(keptDefenders));
            }
        }
        return filtered.isEmpty() ? Map.of() : Map.copyOf(filtered);
    }

    private static ExternalStrategicContext projectedExternalStrategicValueByTeam(
            SimTuning tuning,
            OverrideSet overrides,
            Map<Integer, List<Integer>> externalAssignment,
            List<DBNationSnapshot> externalAttackers,
            List<DBNationSnapshot> externalDefenders,
            Map<Integer, StrategicAssetValue.StrategicRelevance> strategicRelevanceByNationId,
            Map<Long, Integer> warTypeOrdinalsByPair
    ) {
        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                overrides,
                externalAttackers,
                externalDefenders,
                tuning,
                PlannerTransitionSemantics.NONE,
                strategicRelevanceByNationId
        );
        conflict.evaluateAssignmentOpenings(
                orderedAssignmentView(externalAssignment, externalAttackers, warTypeOrdinalsByPair)
        );
        LinkedHashMap<Integer, Double> totals = new LinkedHashMap<>();
        conflict.forEachNationStrategicValue((nationId, teamId, value) ->
                totals.merge(teamId, value, Double::sum)
        );
        return new ExternalStrategicContext(
                totals.isEmpty() ? Map.of() : Map.copyOf(totals),
                conflict.externalWarControlsSnapshot()
        );
    }

    private record ExternalStrategicContext(
            Map<Integer, Double> valueByTeam,
            List<PlannerLocalConflict.ExternalWarControl> warControls
    ) {
        static ExternalStrategicContext empty() {
            return new ExternalStrategicContext(Map.of(), List.of());
        }
    }

    private static void accumulateExternalStrategicValue(
            Set<Integer> countedNationIds,
            Map<Integer, Double> totals,
            DBNationSnapshot snapshot,
            Map<Integer, StrategicAssetValue.StrategicRelevance> strategicRelevanceByNationId
    ) {
        if (snapshot == null || !countedNationIds.add(snapshot.nationId())) {
            return;
        }
        totals.merge(
                snapshot.teamId(),
                PlannerLocalConflict.strategicValue(
                        snapshot,
                        strategicRelevanceByNationId.getOrDefault(
                                snapshot.nationId(),
                                StrategicAssetValue.StrategicRelevance.DEFAULT
                        )
                ),
                Double::sum
        );
    }

    static double evaluateDeclaredWar(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            int horizonTurns
    ) {
        return evaluateDeclaredWar(
                tuning,
                overrides,
                objective,
                attacker,
                defender,
                horizonTurns,
                PlannerExactValidatorScripts.DEFAULT
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
        if (isBlockedDeclareState(attacker, defender)) {
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
        if (isBlockedDeclareState(attacker, defender)) {
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
                ? strategicAssetValue(defender, attacker) - strategicAssetValue(projectedDefender, projectedAttacker)
                : 0.0;
        double selfExposure = policy.retainSelfExposure()
            ? strategicAssetValue(attacker, defender) - strategicAssetValue(projectedAttacker, projectedDefender)
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

    private static boolean isBlockedDeclareState(DBNationSnapshot attacker, DBNationSnapshot defender) {
        return attacker.vmTurns() > 0 || defender.vmTurns() > 0 || defender.beigeTurns() > 0;
    }

    private static double strategicAssetValue(DBNationSnapshot snapshot, DBNationSnapshot opponent) {
        StrategicAssetValue.StrategicRelevance relevance = StrategicAssetValue.relevanceForWarRange(
                snapshot.cities(),
                snapshot.score(),
                snapshot.activeOpponentNationIds().size(),
                opponent == null ? 0 : 1,
                index -> opponent == null ? 0d : opponent.score()
        );
        StrategicAssetValue.ActiveWarContext activeWarContext = StrategicAssetValue.ActiveWarContext.fromSlots(
                snapshot.currentOffensiveWars(),
                snapshot.maxOff(),
                snapshot.currentDefensiveWars(),
                snapshot.activeOpponentNationIds().size()
        );
        return StrategicAssetValue.contextualMilitaryValue(
                snapshot::unit,
                snapshot::pendingBuysNextTurn,
                snapshot::unitsBoughtToday,
                snapshot::dailyBuyCap,
                snapshot.researchBits(),
                activeWarContext,
                relevance
        ).totalValue() + StrategicAssetValue.infrastructureValue(snapshot.cityInfraRaw(), activeWarContext, relevance);
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
