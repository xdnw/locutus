package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.planners.compile.CompiledScenario;

/**
 * Primitive long-horizon control view over a candidate edge table.
 *
 * <p>Edge-indexed assignment scoring is dense: per-edge
 * arrays are indexed by edge ordinal, attacker/defender counts are indexed by
 * scenario slot, and there is no per-pair {@code Map} lookup in the hot loop.
 * Callers feed dense per-attacker/per-defender assigned counts and a per-edge
 * "edge carries flow" boolean buffer; this owner returns objective scalars and
 * exact slot marginal scores without rebuilding any collection.
 */
final class LongHorizonControlProjection implements LongHorizonMarginalScorer {
    private final link.locutus.discord.sim.StrategicObjective projectionObjective;
    private final SideOpeningSettings attackerOpeningSettings;
    private final SideOpeningSettings defenderOpeningSettings;
    private final CandidateEdgeTable edges;
    private final CompiledScenario scenario;
    private final int[] defenderCaps;
    private final int[] attackerStrengthRanks;
    private final int horizonTurns;
    private final double horizonFactor;
    private final boolean includeSlotDenial;
    private final SidePlannerSettings attackerPlannerSettings;
    private final SidePlannerSettings defenderPlannerSettings;
    private final SideProjectionPolicies attackerProjectionPolicies;
    private final SideProjectionPolicies defenderProjectionPolicies;
    private final LongHorizonAssignmentScoringModel assignmentScoringModel;
    private final LongHorizonCounterOpportunityModel counterOpportunityModel;
    private final LongHorizonForwardProjection forwardProjection;
    private final int[] attackerCaps;

    private LongHorizonControlProjection(
            link.locutus.discord.sim.StrategicObjective projectionObjective,
            SideOpeningSettings attackerOpeningSettings,
            SideOpeningSettings defenderOpeningSettings,
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int horizonTurns,
            double horizonFactor,
            boolean includeSlotDenial,
            SidePlannerSettings attackerPlannerSettings,
            SidePlannerSettings defenderPlannerSettings,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies,
            LongHorizonAssignmentScoringModel assignmentScoringModel,
            LongHorizonCounterOpportunityModel counterOpportunityModel,
            LongHorizonForwardProjection forwardProjection,
            int[] attackerCaps
    ) {
        this.projectionObjective = projectionObjective;
        this.attackerOpeningSettings = attackerOpeningSettings;
        this.defenderOpeningSettings = defenderOpeningSettings;
        this.edges = edges;
        this.scenario = scenario;
        this.defenderCaps = java.util.Arrays.copyOf(defenderCaps, defenderCaps.length);
        this.attackerStrengthRanks = attackerStrengthRanks == null ? null : java.util.Arrays.copyOf(attackerStrengthRanks, attackerStrengthRanks.length);
        this.horizonTurns = horizonTurns;
        this.horizonFactor = horizonFactor;
        this.includeSlotDenial = includeSlotDenial;
        this.attackerPlannerSettings = attackerPlannerSettings;
        this.defenderPlannerSettings = defenderPlannerSettings;
        this.attackerProjectionPolicies = attackerProjectionPolicies;
        this.defenderProjectionPolicies = defenderProjectionPolicies;
        this.assignmentScoringModel = assignmentScoringModel;
        this.counterOpportunityModel = counterOpportunityModel;
        this.forwardProjection = forwardProjection;
        this.attackerCaps = java.util.Arrays.copyOf(attackerCaps, attackerCaps.length);
    }

    static LongHorizonControlProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor
    ) {
        return create(edges, scenario, attackerCaps, defenderCaps, horizonTurns, horizonFactor, false);
    }

    static LongHorizonControlProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int horizonTurns,
            double horizonFactor
    ) {
        return create(edges, scenario, attackerCaps, defenderCaps, attackerStrengthRanks, horizonTurns, horizonFactor, false);
    }

    static LongHorizonControlProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor,
            boolean includeSlotDenial
    ) {
            return create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                null,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                null,
                null,
                null,
                SidePlannerSettings.legacy(),
                SidePlannerSettings.legacy(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
            );
            }

            static LongHorizonControlProjection create(
                CandidateEdgeTable edges,
                CompiledScenario scenario,
                int[] attackerCaps,
                int[] defenderCaps,
                int[] attackerStrengthRanks,
                int horizonTurns,
                double horizonFactor,
                boolean includeSlotDenial
            ) {
            return create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                null,
                null,
                null,
                SidePlannerSettings.legacy(),
                SidePlannerSettings.legacy(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
            );
            }

            static LongHorizonControlProjection create(
                CandidateEdgeTable edges,
                CompiledScenario scenario,
                int[] attackerCaps,
                int[] defenderCaps,
                int horizonTurns,
                double horizonFactor,
                boolean includeSlotDenial,
                SideProjectionPolicies attackerProjectionPolicies,
                SideProjectionPolicies defenderProjectionPolicies
            ) {
            return create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                null,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                null,
                null,
                null,
                SidePlannerSettings.legacy(),
                SidePlannerSettings.legacy(),
                attackerProjectionPolicies,
                defenderProjectionPolicies
            );
            }

            static LongHorizonControlProjection create(
                CandidateEdgeTable edges,
                CompiledScenario scenario,
                int[] attackerCaps,
                int[] defenderCaps,
                int[] attackerStrengthRanks,
                int horizonTurns,
                double horizonFactor,
                boolean includeSlotDenial,
                SidePlannerSettings attackerPlannerSettings,
                SidePlannerSettings defenderPlannerSettings,
                SideProjectionPolicies attackerProjectionPolicies,
                SideProjectionPolicies defenderProjectionPolicies
            ) {
            return create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                null,
                null,
                null,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies
            );
            }

            static LongHorizonControlProjection create(
                CandidateEdgeTable edges,
                CompiledScenario scenario,
                int[] attackerCaps,
                int[] defenderCaps,
                int horizonTurns,
                double horizonFactor,
                boolean includeSlotDenial,
                link.locutus.discord.sim.StrategicObjective projectionObjective,
                SideOpeningSettings attackerOpeningSettings,
                SideOpeningSettings defenderOpeningSettings,
                SidePlannerSettings attackerPlannerSettings,
                SidePlannerSettings defenderPlannerSettings,
                SideProjectionPolicies attackerProjectionPolicies,
                SideProjectionPolicies defenderProjectionPolicies
            ) {
            return create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                null,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                projectionObjective,
                attackerOpeningSettings,
                defenderOpeningSettings,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies
            );
            }

            static LongHorizonControlProjection create(
                CandidateEdgeTable edges,
                CompiledScenario scenario,
                int[] attackerCaps,
                int[] defenderCaps,
                int horizonTurns,
                double horizonFactor,
                boolean includeSlotDenial,
                SidePlannerSettings attackerPlannerSettings,
                SidePlannerSettings defenderPlannerSettings,
                SideProjectionPolicies attackerProjectionPolicies,
                SideProjectionPolicies defenderProjectionPolicies
            ) {
            return create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                null,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                null,
                null,
                null,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies
            );
            }

            static LongHorizonControlProjection create(
                CandidateEdgeTable edges,
                CompiledScenario scenario,
                int[] attackerCaps,
                int[] defenderCaps,
                int[] attackerStrengthRanks,
                int horizonTurns,
                double horizonFactor,
                boolean includeSlotDenial,
                link.locutus.discord.sim.StrategicObjective projectionObjective,
                SideOpeningSettings attackerOpeningSettings,
                SideOpeningSettings defenderOpeningSettings,
                SidePlannerSettings attackerPlannerSettings,
                SidePlannerSettings defenderPlannerSettings,
                SideProjectionPolicies attackerProjectionPolicies,
                SideProjectionPolicies defenderProjectionPolicies
            ) {
            LongHorizonForwardProjection forwardProjection = LongHorizonForwardProjection.create(
                edges,
                scenario,
                attackerCaps,
                horizonTurns,
                horizonFactor,
                projectionObjective,
                attackerOpeningSettings,
                defenderOpeningSettings,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies
            );
        return new LongHorizonControlProjection(
            projectionObjective,
            attackerOpeningSettings,
            defenderOpeningSettings,
            edges,
            scenario,
            defenderCaps,
            attackerStrengthRanks,
            horizonTurns,
            horizonFactor,
            includeSlotDenial,
            attackerPlannerSettings,
            defenderPlannerSettings,
            attackerProjectionPolicies,
            defenderProjectionPolicies,
            LongHorizonAssignmentScoringModel.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                attackerPlannerSettings
            ),
                forwardProjection.counterOpportunityModel(),
                forwardProjection,
                attackerCaps
        );
    }

    static LongHorizonControlProjection createScorerOnly(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor
    ) {
        return createScorerOnly(edges, scenario, attackerCaps, defenderCaps, horizonTurns, horizonFactor, false);
    }

    static LongHorizonControlProjection createScorerOnly(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int horizonTurns,
            double horizonFactor
    ) {
        return createScorerOnly(edges, scenario, attackerCaps, defenderCaps, attackerStrengthRanks, horizonTurns, horizonFactor, false, SidePlannerSettings.legacy());
    }

    static LongHorizonControlProjection createScorerOnly(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor,
            boolean includeSlotDenial
    ) {
        return createScorerOnly(
            edges,
            scenario,
            attackerCaps,
            defenderCaps,
            null,
            horizonTurns,
            horizonFactor,
            includeSlotDenial,
            SidePlannerSettings.legacy()
        );
        }

        static LongHorizonControlProjection createScorerOnly(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int horizonTurns,
            double horizonFactor,
            boolean includeSlotDenial,
            SidePlannerSettings attackerPlannerSettings
        ) {
        return new LongHorizonControlProjection(
            null,
            null,
            null,
            edges,
            scenario,
            defenderCaps,
            attackerStrengthRanks,
            horizonTurns,
            horizonFactor,
            includeSlotDenial,
            attackerPlannerSettings,
            SidePlannerSettings.legacy(),
            SideProjectionPolicies.heuristic(),
            SideProjectionPolicies.heuristic(),
            LongHorizonAssignmentScoringModel.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                attackerPlannerSettings
            ),
                LongHorizonForwardProjection.counterOpportunityModel(scenario, horizonTurns, horizonFactor),
                null,
                attackerCaps
        );
    }

            LongHorizonControlProjection sameSettingsFullVariant(
                CandidateEdgeTable variantEdges,
                int[] variantAttackerCaps,
                int[] variantDefenderCaps,
                int[] variantAttackerStrengthRanks
            ) {
            return create(
                variantEdges,
                scenario,
                variantAttackerCaps,
                variantDefenderCaps,
                variantAttackerStrengthRanks,
                horizonTurns,
                horizonFactor,
                includeSlotDenial,
                projectionObjective,
                attackerOpeningSettings,
                defenderOpeningSettings,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies
            );
            }

    /**
     * Computes the long-horizon objective scalar from a dense edge-assignment buffer
     * and dense per-side counts. Allocates nothing.
     */
    double assignmentScoreDense(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        return assignmentScoringModel.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts, counterOpportunityModel, attackerCaps);
    }

    @Override
    public double edgeScore(int edgeIndex) {
        return assignmentScoringModel.edgeScore(edgeIndex);
    }

    @Override
    public double attackerCommitmentMarginalScore(int attackerIndex, int assignedBefore) {
        return assignmentScoringModel.attackerCommitmentMarginalScore(attackerIndex, assignedBefore);
    }

    @Override
    public double attackerIdlePressureMarginalScore(int attackerIndex) {
        return assignmentScoringModel.attackerIdlePressureMarginalScore(attackerIndex);
    }

    @Override
    public double attackerCounterOpportunityMarginalScore(int attackerIndex, int assignedBefore) {
        return counterOpportunityModel.attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore, attackerCaps);
    }

    double projectedObjectiveScore(
            link.locutus.discord.sim.StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        requireForwardProjection();
        return forwardProjection.projectedObjectiveScore(objective, teamId, edgeAssigned, attackerCounts, defenderCounts);
    }

    LongHorizonForwardProjection.ProjectedEvaluation projectedEvaluation(
            link.locutus.discord.sim.StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        requireForwardProjection();
        return forwardProjection.projectedEvaluation(objective, teamId, edgeAssigned, attackerCounts, defenderCounts);
    }

        LongHorizonForwardProjection.ProjectedFeedbackEvaluation projectedFeedbackEvaluation(
            link.locutus.discord.sim.StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
        ) {
        requireForwardProjection();
        return forwardProjection.projectedFeedbackEvaluation(
            objective,
            teamId,
            edgeAssigned,
            attackerCounts,
            defenderCounts,
            forwardProjection.defaultMidHorizonTurns()
        );
        }

    LongHorizonForwardProjection.ProjectionDiagnostics projectionDiagnostics(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        requireForwardProjection();
        return forwardProjection.projectionDiagnostics(edgeAssigned, attackerCounts, defenderCounts);
    }

    int[] realizedCounterIncidence(boolean[] edgeAssigned, int[] attackerCounts, int[] defenderCounts) {
        requireForwardProjection();
        return forwardProjection.realizedCounterIncidence(edgeAssigned, attackerCounts, defenderCounts);
    }

    LongHorizonForwardProjection.MidHorizonSnapshot snapshotMidHorizonState(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        requireForwardProjection();
        return forwardProjection.snapshotMidHorizonState(
                edgeAssigned,
                attackerCounts,
                defenderCounts,
                forwardProjection.defaultMidHorizonTurns()
        );
    }

    @Override
    public double defenderPressureMarginalScore(int defenderIndex, int assignedBefore) {
        return assignmentScoringModel.defenderPressureMarginalScore(defenderIndex, assignedBefore);
    }

    private void requireForwardProjection() {
        if (forwardProjection == null) {
            throw new IllegalStateException("Terminal projection is unavailable on scorer-only long-horizon projection");
        }
    }
}
