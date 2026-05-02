package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.StrategicEvaluationComponents;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.StrategicValueView;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningEvaluatorCoverageTest {
    private static final int ATTACKER_TEAM = 1000;
    private static final int DEFENDER_TEAM = 9999;
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();
    private static final StrategicObjective PRESSURE_ONLY_OBJECTIVE = new PressureOnlyObjective();
    private static final CandidateEdgeComponentPolicy SPARSE_COMPONENT_POLICY =
            new CandidateEdgeComponentPolicy(true, false, false, true, false);
    private static final StrategicObjective SPARSE_COMPONENT_OBJECTIVE =
            new SparseComponentObjective(SPARSE_COMPONENT_POLICY);

    @Test
    void rescuesViableDefenderCoverageBeyondPerAttackerTopK() {
        DBNationSnapshot attackerOne = buildNation(1, ATTACKER_TEAM, 1_550.0, 12, 6_500, 1_100, 1_250, 220);
        DBNationSnapshot attackerTwo = buildNation(2, ATTACKER_TEAM, 1_550.0, 12, 6_500, 1_100, 1_250, 220);
        DBNationSnapshot contestedHighThreat = buildNation(101, DEFENDER_TEAM, 1_850.0, 12, 6_000, 950, 1_050, 210);
        DBNationSnapshot viableSecondary = buildNation(102, DEFENDER_TEAM, 1_200.0, 11, 1_200, 160, 120, 24);

        OpeningEvaluator.EvaluatedEdge firstDefender = OpeningEvaluator.evaluateOpening(
                attackerOne,
                contestedHighThreat,
                PRESSURE_ONLY_OBJECTIVE,
                CandidateEdgeComponentPolicy.none()
        );
        OpeningEvaluator.EvaluatedEdge secondDefender = OpeningEvaluator.evaluateOpening(
                attackerOne,
                viableSecondary,
                PRESSURE_ONLY_OBJECTIVE,
                CandidateEdgeComponentPolicy.none()
        );

        assertTrue(Float.isFinite(firstDefender.score()), "Expected first defender to remain admitted");
        assertTrue(Float.isFinite(secondDefender.score()), "Expected second defender to remain admitted");

        int alternateDefenderNationId;
        if (firstDefender.score() >= secondDefender.score()) {
            alternateDefenderNationId = viableSecondary.nationId();
        } else {
            alternateDefenderNationId = contestedHighThreat.nationId();
        }

        CompiledScenario scenario = SCENARIO_COMPILER.compile(
                List.of(attackerOne, attackerTwo),
                List.of(contestedHighThreat, viableSecondary),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable out = new CandidateEdgeTable();

        OpeningEvaluator.evaluate(
                scenario,
                tuningWithCandidatesPerAttacker(1),
                OverrideSet.EMPTY,
                PRESSURE_ONLY_OBJECTIVE,
                new int[]{1, 1},
                new int[]{1, 1},
                out
        );

        boolean hasAlternateDefenderEdge = false;
        for (int edge = 0; edge < out.edgeCount(); edge++) {
            if (scenario.defenderNationId(out.defenderIndex(edge)) == alternateDefenderNationId) {
                hasAlternateDefenderEdge = true;
                break;
            }
        }

        assertTrue(
                hasAlternateDefenderEdge,
                "Coverage rescue should keep the non-preferred admitted defender when identical attackers converge on one top-1 target"
        );
    }

    @Test
    void controlObjectiveRewardsHigherProbeAttackerOnSameHighThreatDefender() {
        DBNationSnapshot strongAttacker = buildNation(11, ATTACKER_TEAM, 1_550.0, 12, 6_500, 1_100, 1_250, 220);
        DBNationSnapshot weakAttacker = buildNation(12, ATTACKER_TEAM, 1_250.0, 12, 4_900, 800, 900, 150);
        DBNationSnapshot highThreatDefender = buildNation(111, DEFENDER_TEAM, 1_850.0, 12, 6_000, 950, 1_050, 210);

        OpeningEvaluator.ProbeResult strongProbe = new OpeningEvaluator.ProbeResult();
        OpeningEvaluator.ProbeResult weakProbe = new OpeningEvaluator.ProbeResult();
        OpeningEvaluator.viabilityProbe(strongAttacker, highThreatDefender, strongProbe);
        OpeningEvaluator.viabilityProbe(weakAttacker, highThreatDefender, weakProbe);

        assertTrue(strongProbe.probe() >= 0.15f, "Expected strong attacker to remain admitted");
        assertTrue(weakProbe.probe() >= 0.15f, "Expected weak attacker to remain admitted for comparison");
        assertTrue(strongProbe.probe() > weakProbe.probe(), "Test setup must create a higher-probe strong opener");

        OpeningEvaluator.EvaluatedEdge strongEdge = OpeningEvaluator.evaluateOpening(
                strongAttacker,
                highThreatDefender,
                BlitzObjective.CONTROL.objective(),
                CandidateEdgeComponentPolicy.none()
        );
        OpeningEvaluator.EvaluatedEdge weakEdge = OpeningEvaluator.evaluateOpening(
                weakAttacker,
                highThreatDefender,
                BlitzObjective.CONTROL.objective(),
                CandidateEdgeComponentPolicy.none()
        );

        assertTrue(Float.isFinite(strongEdge.score()), "Expected strong attacker edge to remain admitted");
        assertTrue(Float.isFinite(weakEdge.score()), "Expected weak attacker edge to remain admitted");
        assertTrue(
                strongEdge.score() > weakEdge.score(),
                "Control scoring should prefer the stronger higher-probe opener on the same high-threat defender"
        );
    }

    @Test
    void singlePairCollectorMatchesDirectOpeningEvaluation() {
        DBNationSnapshot attacker = buildNation(21, ATTACKER_TEAM, 1_550.0, 12, 6_500, 1_100, 1_250, 220);
        DBNationSnapshot defender = buildNation(121, DEFENDER_TEAM, 1_850.0, 12, 6_000, 950, 1_050, 210);

        OpeningEvaluator.CandidateOpeningEvaluator candidateOpeningEvaluator =
                new OpeningEvaluator.CandidateOpeningEvaluator(PRESSURE_ONLY_OBJECTIVE, 3);
        OpeningEvaluator.EvaluatedEdge direct = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                PRESSURE_ONLY_OBJECTIVE,
                CandidateEdgeComponentPolicy.none()
        );
        OpeningEvaluator.EvaluatedEdge reusable = candidateOpeningEvaluator.evaluate(attacker, defender);

        assertTrue(Float.isFinite(direct.score()), "Expected direct single-pair evaluation to stay admitted");
        assertEquals(direct.score(), reusable.score(), 1.0e-5f);
        assertEquals(direct.preferredWarTypeId(), reusable.preferredWarTypeId());
        assertEquals(direct.firstAttackTypeId(), reusable.firstAttackTypeId());

        CompiledScenario scenario = SCENARIO_COMPILER.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable out = new CandidateEdgeTable();

        OpeningEvaluator.evaluate(
                scenario,
                tuningWithCandidatesPerAttacker(1),
                OverrideSet.EMPTY,
                PRESSURE_ONLY_OBJECTIVE,
                new int[]{1},
                new int[]{1},
                out
        );

        assertEquals(1, out.edgeCount(), "Expected single-pair collector to emit the admitted edge");
        assertEquals(direct.score(), out.scalarScore(0), 1.0e-5);
        assertEquals(direct.preferredWarTypeId(), out.preferredWarTypeId(0));
        assertEquals(direct.firstAttackTypeId(), out.bestAttackTypeId(0));
    }

    @Test
    void singlePairCollectorRetainsOnlyRequestedComponents() {
        DBNationSnapshot attacker = buildNation(31, ATTACKER_TEAM, 1_550.0, 12, 6_500, 1_100, 1_250, 220);
        DBNationSnapshot defender = buildNation(131, DEFENDER_TEAM, 1_850.0, 12, 6_000, 950, 1_050, 210);

        OpeningEvaluator.EvaluatedEdge raw = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                SPARSE_COMPONENT_OBJECTIVE,
                new CandidateEdgeComponentPolicy(true, true, true, true, true)
        );
        OpeningEvaluator.EvaluatedEdge direct = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                SPARSE_COMPONENT_OBJECTIVE,
                SPARSE_COMPONENT_POLICY
        );
        OpeningEvaluator.CandidateOpeningEvaluator candidateOpeningEvaluator =
                new OpeningEvaluator.CandidateOpeningEvaluator(SPARSE_COMPONENT_OBJECTIVE, 3);
        OpeningEvaluator.EvaluatedEdge reusable = candidateOpeningEvaluator.evaluate(attacker, defender);

        assertTrue(Float.isFinite(raw.score()), "Expected raw single-pair evaluation to stay admitted");
        assertTrue(
                Math.abs(raw.selfExposure()) > 1.0e-5f
                        || Math.abs(raw.resourceSwing()) > 1.0e-5f
                        || Math.abs(raw.futureWarLeverage()) > 1.0e-5f,
                "Test setup must produce at least one non-zero unretained component"
        );

        assertEquals(raw.immediateHarm(), direct.immediateHarm(), 1.0e-5f);
        assertEquals(0f, direct.selfExposure(), 1.0e-5f);
        assertEquals(0f, direct.resourceSwing(), 1.0e-5f);
        assertEquals(raw.controlLeverage(), direct.controlLeverage(), 1.0e-5f);
        assertEquals(0f, direct.futureWarLeverage(), 1.0e-5f);

        assertEquals(direct.score(), reusable.score(), 1.0e-5f);
        assertEquals(direct.preferredWarTypeId(), reusable.preferredWarTypeId());
        assertEquals(direct.firstAttackTypeId(), reusable.firstAttackTypeId());
        assertEquals(direct.immediateHarm(), reusable.immediateHarm(), 1.0e-5f);
        assertEquals(direct.selfExposure(), reusable.selfExposure(), 1.0e-5f);
        assertEquals(direct.resourceSwing(), reusable.resourceSwing(), 1.0e-5f);
        assertEquals(direct.controlLeverage(), reusable.controlLeverage(), 1.0e-5f);
        assertEquals(direct.futureWarLeverage(), reusable.futureWarLeverage(), 1.0e-5f);

        CompiledScenario scenario = SCENARIO_COMPILER.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable out = new CandidateEdgeTable();

        OpeningEvaluator.evaluate(
                scenario,
                tuningWithCandidatesPerAttacker(1),
                OverrideSet.EMPTY,
                SPARSE_COMPONENT_OBJECTIVE,
                new int[]{1},
                new int[]{1},
                out
        );

        assertEquals(1, out.edgeCount(), "Expected single-pair collector to emit the admitted edge");
        assertEquals(direct.score(), out.scalarScore(0), 1.0e-5f);
        assertEquals(direct.preferredWarTypeId(), out.preferredWarTypeId(0));
        assertEquals(direct.firstAttackTypeId(), out.bestAttackTypeId(0));
        assertTrue(out.retainsImmediateHarm());
        assertFalse(out.retainsSelfExposure());
        assertFalse(out.retainsResourceSwing());
        assertTrue(out.retainsControlLeverage());
        assertFalse(out.retainsFutureWarLeverage());
        assertEquals(direct.immediateHarm(), out.immediateHarm(0), 1.0e-5f);
        assertThrows(IllegalStateException.class, () -> out.selfExposure(0));
        assertThrows(IllegalStateException.class, () -> out.resourceSwing(0));
        assertEquals(direct.controlLeverage(), out.controlLeverage(0), 1.0e-5f);
        assertThrows(IllegalStateException.class, () -> out.futureWarLeverage(0));
    }

    @Test
    void outmatchedAttackerGetsEdgeUnderControlObjectiveDueToPositiveBaseline() {
        // Outmatched attacker: ~15% of normal military — probe < 0.15 against the strong defender.
        DBNationSnapshot outmatchedAttacker = buildNation(51, ATTACKER_TEAM, 820.0, 18,
                37_500, 3_000, 240, 37);
        // Strong defender with 1.55× military and thus non-trivial targetPressure.
        DBNationSnapshot strongDefender = buildNation(151, DEFENDER_TEAM, 1_990.0, 25,
                387_500, 31_000, 2_480, 387);

        // Verify the pair is below the primary admission threshold.
        OpeningEvaluator.ProbeResult probeResult = new OpeningEvaluator.ProbeResult();
        OpeningEvaluator.viabilityProbe(outmatchedAttacker, strongDefender, probeResult);
        assertTrue(
                probeResult.probe() < CandidateEdgeAdmissionPolicy.DEFAULT_MINIMUM_VIABILITY_PROBE,
                "Test setup must produce an outmatched pair below the primary admission threshold"
        );

        // NET_DAMAGE baseline is 0 (no harm − no exposure) → no edge for an outmatched attacker.
        OpeningEvaluator.EvaluatedEdge netDamageEdge = OpeningEvaluator.evaluateOpening(
                outmatchedAttacker,
                strongDefender,
                BlitzObjective.NET_DAMAGE.objective(),
                CandidateEdgeComponentPolicy.none()
        );
        assertFalse(
                Float.isFinite(netDamageEdge.score()),
                "NET_DAMAGE baseline is 0 for outmatched attacker — no edge expected"
        );

        // CONTROL baseline is 4.0 * targetPressure > 0 → edge should be produced via rollout
        // even though no single attack improves the score above that baseline.
        OpeningEvaluator.EvaluatedEdge controlEdge = OpeningEvaluator.evaluateOpening(
                outmatchedAttacker,
                strongDefender,
                BlitzObjective.CONTROL.objective(),
                CandidateEdgeComponentPolicy.none()
        );
        assertTrue(
                Float.isFinite(controlEdge.score()) && controlEdge.score() > 0f,
                "CONTROL baseline is 4.0*targetPressure > 0 — outmatched attacker should receive an edge"
        );

        // MINIMUM_DAMAGE_RECEIVED baseline is 0.35*harm − exposure = 0 → no edge.
        OpeningEvaluator.EvaluatedEdge avoidanceEdge = OpeningEvaluator.evaluateOpening(
                outmatchedAttacker,
                strongDefender,
                BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective(),
                CandidateEdgeComponentPolicy.none()
        );
        assertFalse(
                Float.isFinite(avoidanceEdge.score()),
                "MINIMUM_DAMAGE_RECEIVED baseline is 0 — outmatched attacker should get no edge"
        );
    }

    @Test
    void controlRolloutFallbackEdgeScoreIsBelowParityAttackersFullRolloutScore() {
        // Outmatched attacker: probe < 0.15, gets edge only via positive-baseline fallback.
        DBNationSnapshot outmatchedAttacker = buildNation(52, ATTACKER_TEAM, 820.0, 18,
                37_500, 3_000, 240, 37);
        // Parity attacker: probe >= 0.15, gets full improving rollout under CONTROL.
        DBNationSnapshot parityAttacker = buildNation(53, ATTACKER_TEAM, 1_970.0, 25,
                387_500, 31_000, 2_480, 387);
        // Shared strong defender.
        DBNationSnapshot strongDefender = buildNation(152, DEFENDER_TEAM, 1_990.0, 25,
                387_500, 31_000, 2_480, 387);

        OpeningEvaluator.ProbeResult outmatchedProbe = new OpeningEvaluator.ProbeResult();
        OpeningEvaluator.ProbeResult parityProbe = new OpeningEvaluator.ProbeResult();
        OpeningEvaluator.viabilityProbe(outmatchedAttacker, strongDefender, outmatchedProbe);
        OpeningEvaluator.viabilityProbe(parityAttacker, strongDefender, parityProbe);
        assertTrue(
                outmatchedProbe.probe() < CandidateEdgeAdmissionPolicy.DEFAULT_MINIMUM_VIABILITY_PROBE,
                "Test setup must produce outmatched probe below the primary admission threshold"
        );
        assertTrue(
                parityProbe.probe() >= CandidateEdgeAdmissionPolicy.DEFAULT_MINIMUM_VIABILITY_PROBE,
                "Test setup must produce parity probe at or above the primary admission threshold"
        );

        CompiledScenario scenario = SCENARIO_COMPILER.compile(
                List.of(outmatchedAttacker, parityAttacker),
                List.of(strongDefender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable out = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                tuningWithCandidatesPerAttacker(3),
                OverrideSet.EMPTY,
                BlitzObjective.CONTROL.objective(),
                new int[]{1, 1},
                new int[]{1},
                out
        );

        float fallbackScore = Float.NaN;
        float primaryScore = Float.NaN;
        for (int edge = 0; edge < out.edgeCount(); edge++) {
            int attackerNationId = scenario.attackerNationId(out.attackerIndex(edge));
            if (attackerNationId == outmatchedAttacker.nationId()) {
                fallbackScore = out.scalarScore(edge);
            } else if (attackerNationId == parityAttacker.nationId()) {
                primaryScore = out.scalarScore(edge);
            }
        }

        assertTrue(Float.isFinite(fallbackScore),
                "Outmatched attacker should receive a positive-baseline CONTROL edge");
        assertTrue(Float.isFinite(primaryScore),
                "Parity attacker should receive a full-rollout CONTROL edge");
        assertTrue(
                fallbackScore < primaryScore,
                "Positive-baseline fallback score should be below the full-rollout primary score"
        );
    }

    @Test
    void openingRolloutDoesNotPreferInfraWhenUnitDamageIsAvailable() {
        DBNationSnapshot attacker = buildNation(41, ATTACKER_TEAM, 1_550.0, 12, 6_500, 1_100, 1_250, 220)
            .toBuilder()
            .cityInfra(uniformInfra(12, 250.0))
            .build();
        DBNationSnapshot defender = buildNation(141, DEFENDER_TEAM, 1_550.0, 12, 6_000, 950, 1_050, 210)
            .toBuilder()
            .cityInfra(uniformInfra(12, 2_800.0))
            .build();

        OpeningEvaluator.EvaluatedEdge edge = OpeningEvaluator.evaluateOpening(
            attacker,
            defender,
            BlitzObjective.DAMAGE.objective(),
            CandidateEdgeComponentPolicy.none()
        );

        assertTrue(Float.isFinite(edge.score()), "Expected the militarized pair to remain admitted");
        assertTrue(edge.firstAttackTypeId() >= 0, "Expected rollout to choose an opening attack");
        assertFalse(
            edge.firstAttackTypeId() == AttackType.AIRSTRIKE_INFRA.ordinal(),
            "Opening rollout should value unit/control progress before raw infra damage"
        );
    }

    private static SimTuning tuningWithCandidatesPerAttacker(int candidatesPerAttacker) {
        SimTuning defaults = SimTuning.defaults();
        return new SimTuning(
                defaults.intraTurnPasses(),
                defaults.turn1DeclarePolicy(),
                defaults.wartimeActivityUplift(),
                defaults.activityActThreshold(),
                defaults.policyCooldownTurns(),
                defaults.localSearchBudgetMs(),
                defaults.localSearchMaxIterations(),
                candidatesPerAttacker,
                defaults.beigeTurnsOnDefeat(),
                defaults.stateResolutionMode(),
                defaults.stochasticSeed(),
                defaults.stochasticSampleCount()
        );
    }

    private static DBNationSnapshot buildNation(
            int nationId,
            int teamId,
            double score,
            int cities,
            int soldiers,
            int tanks,
            int aircraft,
            int ships
    ) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .score(score)
                .cities(cities)
                .nonInfraScoreBase(score)
                .cityInfra(uniformInfra(cities, 1_200.0))
                .maxOff(5)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .unit(MilitaryUnit.SOLDIER, soldiers)
                .unit(MilitaryUnit.TANK, tanks)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .unit(MilitaryUnit.SHIP, ships)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] result = new double[cities];
        for (int index = 0; index < cities; index++) {
            result[index] = infra;
        }
        return result;
    }

    private static final class PressureOnlyObjective implements StrategicObjective {
        @Override
        public double scoreTerminal(StrategicValueView view, int teamId) {
            return 0.0;
        }

        @Override
        public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
            return metrics.targetPressure() + (0.0001d * metrics.immediateHarm());
        }

        @Override
        public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
            return CandidateEdgeAdmissionPolicy.lowProbeSpecialists();
        }

        @Override
        public double scoreAction(SimWorld world, SimAction action, int teamId) {
            return 0.0;
        }
    }

    private static final class SparseComponentObjective implements StrategicObjective {
        private final CandidateEdgeComponentPolicy componentPolicy;

        private SparseComponentObjective(CandidateEdgeComponentPolicy componentPolicy) {
            this.componentPolicy = componentPolicy;
        }

        @Override
        public double scoreTerminal(StrategicValueView view, int teamId) {
            return 0.0;
        }

        @Override
        public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
            return metrics.immediateHarm()
                    + (2.0d * metrics.controlLeverage())
                    + (0.01d * metrics.futureWarLeverage())
                    - (0.05d * metrics.selfExposure());
        }

        @Override
        public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
            return CandidateEdgeAdmissionPolicy.lowProbeSpecialists();
        }

        @Override
        public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
            return componentPolicy;
        }

        @Override
        public double scoreAction(SimWorld world, SimAction action, int teamId) {
            return 0.0;
        }
    }
}
