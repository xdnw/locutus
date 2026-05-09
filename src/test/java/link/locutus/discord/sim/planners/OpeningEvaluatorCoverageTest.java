package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.StrategicAssetValue;
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
    void capabilityDamageCanOutrankCostlierShipLossWhenAirPowerMattersMore() {
        DBNationSnapshot aircraftFront = buildNation(701, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 2_600, 32);
        DBNationSnapshot navalFront = buildNation(702, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 300, 260);

        double[] aircraftLosses = new double[MilitaryUnit.values.length];
        aircraftLosses[MilitaryUnit.AIRCRAFT.ordinal()] = 90d;
        double[] shipLosses = new double[MilitaryUnit.values.length];
        shipLosses[MilitaryUnit.SHIP.ordinal()] = 18d;

        double aircraftCost = StrategicAssetValue.unitValue(MilitaryUnit.AIRCRAFT, 90d, aircraftFront.researchBits());
        double shipCost = StrategicAssetValue.unitValue(MilitaryUnit.SHIP, 18d, navalFront.researchBits());
        double aircraftDamage = OpeningMetricSummary.expectedCapabilityDamage(
                aircraftFront,
                aircraftLosses,
                false,
                false,
                false,
                false,
                false
        );
        double shipDamage = OpeningMetricSummary.expectedCapabilityDamage(
                navalFront,
                shipLosses,
                false,
                false,
                false,
                false,
                false
        );

        assertTrue(
                shipCost > aircraftCost,
                "Test setup must keep the ship loss more expensive under converted-cost pricing"
                        + " (shipCost=" + shipCost + ", aircraftCost=" + aircraftCost + ')'
        );
        assertTrue(
                aircraftDamage > shipDamage,
                "Opening capability damage should be able to price concentrated aircraft losses above costlier ship losses"
                        + " (aircraftDamage=" + aircraftDamage + ", shipDamage=" + shipDamage + ')'
        );
    }

    @Test
    void capabilityDamagePricesHoldabilityLossSeparatelyFromRawUnitLoss() {
        DBNationSnapshot airHolder = buildNation(703, ATTACKER_TEAM, 1_650.0, 26,
                18_000, 1_200, 120, 10);
        double[] losses = new double[MilitaryUnit.values.length];
        losses[MilitaryUnit.AIRCRAFT.ordinal()] = 120d;

        double withoutHoldPenalty = OpeningMetricSummary.expectedCapabilityDamage(
                airHolder,
                losses,
                false,
                false,
                false,
                false,
                false
        );
        double withHoldPenalty = OpeningMetricSummary.expectedCapabilityDamage(
                airHolder,
                losses,
                false,
                false,
                false,
                true,
                false
        );

        assertTrue(withHoldPenalty > withoutHoldPenalty, "Losing the units needed to hold air control should add explicit opening exposure/harm beyond the raw unit loss");
    }

    @Test
    void slotCapabilityValueCanOutrankCostlierShipStackWhenAirPowerIsBroaderThreat() {
        DBNationSnapshot aircraftFront = buildNation(704, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 2_600, 32);
        DBNationSnapshot navalFront = buildNation(705, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 300, 260);

        double aircraftCost = StrategicAssetValue.unitValue(MilitaryUnit.AIRCRAFT, 90d, aircraftFront.researchBits());
        double shipCost = StrategicAssetValue.unitValue(MilitaryUnit.SHIP, 18d, navalFront.researchBits());

        assertTrue(shipCost > aircraftCost, "Test setup must keep the ship stack more expensive under converted-cost pricing");
        assertTrue(
                PlannerStrategicValue.slotCapabilityValue(aircraftFront) > PlannerStrategicValue.slotCapabilityValue(navalFront),
                "Slot capability should price broader air-heavy future pressure above a costlier but narrower ship-heavy stack"
        );
    }

    @Test
    void defenderControlPressureUsesSlotCapabilityBonusNotLocalStrategicValueTotals() {
        DBNationSnapshot aircraftFront = buildNation(706, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 2_600, 32);
        DBNationSnapshot navalFront = buildNation(707, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 300, 260);

        assertTrue(
                OpeningMetricSummary.defenderControlPressure(aircraftFront)
                        > OpeningMetricSummary.defenderControlPressure(navalFront),
                "Opening pressure should inherit the slot-capability bonus instead of favoring a narrower costlier ship stack"
        );
    }

    @Test
    void plannerStrategicValueCanOutrankCostlierShipStackWithCapabilityLane() {
        DBNationSnapshot aircraftFront = buildNation(708, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 2_600, 32);
        DBNationSnapshot navalFront = buildNation(709, ATTACKER_TEAM, 2_150.0, 34,
                40_000, 2_400, 120, 320);
        double aircraftStrategicValue = PlannerStrategicValue.localStrategicValue(aircraftFront);
        double navalStrategicValue = PlannerStrategicValue.localStrategicValue(navalFront);
        assertTrue(
                aircraftStrategicValue > navalStrategicValue,
                "Planner strategic value should now prefer broader combat capability over a narrower ship-heavy stack"
                        + " (aircraftStrategicValue=" + aircraftStrategicValue
                        + ", navalStrategicValue=" + navalStrategicValue + ')'
        );
    }

    @Test
    void plannerStrategicValueRetainsRebuildRunwaySeparateFromReplacementCost() {
        DBNationSnapshot fresh = buildNation(710, ATTACKER_TEAM, 1_850.0, 28,
                24_000, 1_800, 1_700, 70);
        int aircraftDailyCap = fresh.dailyBuyCap(MilitaryUnit.AIRCRAFT);
        assertTrue(aircraftDailyCap > 0, "Test setup must provide positive aircraft rebuy capacity");

        DBNationSnapshot exhausted = fresh.toBuilder()
                .unitBoughtToday(MilitaryUnit.AIRCRAFT, aircraftDailyCap)
                .build();

        double freshReplacement = StrategicAssetValue.replacementMilitaryValue(
                fresh::unit,
                fresh::pendingBuysNextTurn,
                fresh.researchBits()
        );
        double exhaustedReplacement = StrategicAssetValue.replacementMilitaryValue(
                exhausted::unit,
                exhausted::pendingBuysNextTurn,
                exhausted.researchBits()
        );

        assertEquals(freshReplacement, exhaustedReplacement, 1.0e-9,
                "Replacement cost should stay constant when only current-day buy exhaustion changes");
        assertTrue(
                PlannerStrategicValue.localStrategicValue(fresh)
                        > PlannerStrategicValue.localStrategicValue(exhausted),
                "Planner strategic value should retain explicit rebuild-runway signal after replacement cost is split out"
        );
    }

    @Test
    void capabilityVectorUpdateMovesSharedReducersAndOpeningPressureTogether() {
        DBNationSnapshot baseline = buildNation(711, ATTACKER_TEAM, 1_950.0, 30,
                28_000, 2_000, 900, 90);
        DBNationSnapshot strongerAirFront = baseline.toBuilder()
                .unit(MilitaryUnit.AIRCRAFT, 1_800)
                .build();

        StrategicCapabilityVector baselineVector = PlannerStrategicValue.capabilityVector(baseline);
        StrategicCapabilityVector strongerVector = PlannerStrategicValue.capabilityVector(strongerAirFront);

        assertTrue(
                StrategicCapabilityReducer.slotCapabilityValue(strongerVector)
                        > StrategicCapabilityReducer.slotCapabilityValue(baselineVector),
                "The shared capability reducer should raise slot capability when the vector gains real combat strength"
        );
        assertTrue(
                StrategicCapabilityReducer.strategicMilitaryValue(strongerVector, PlannerStrategicValue.localRelevance(strongerAirFront))
                        > StrategicCapabilityReducer.strategicMilitaryValue(baselineVector, PlannerStrategicValue.localRelevance(baseline)),
                "The shared capability reducer should raise strategic military value from the same vector update"
        );
        assertTrue(
                OpeningMetricSummary.defenderControlPressure(strongerAirFront)
                        > OpeningMetricSummary.defenderControlPressure(baseline),
                "Opening pressure should now inherit the same capability-vector increase rather than a parallel helper formula"
        );
    }

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
    void spilloverCanAddHighPriorityDefenderWhileCoverageRemainsBelowTarget() {
        StrategicObjective objective = BlitzObjective.CONTROL.objective();
        DBNationSnapshot strongAttacker = buildNation(13, ATTACKER_TEAM, 2_050.0, 27, 450_000, 36_000, 2_900, 420);
        DBNationSnapshot flexibleAttacker = buildNation(14, ATTACKER_TEAM, 820.0, 18, 37_500, 3_000, 240, 37);
        DBNationSnapshot highPriorityDefender = buildNation(112, DEFENDER_TEAM, 1_990.0, 25, 387_500, 31_000, 2_480, 387);
        DBNationSnapshot softerDefender = buildNation(113, DEFENDER_TEAM, 1_200.0, 11, 1_200, 160, 120, 24);

        OpeningEvaluator.EvaluatedEdge strongAttackerHighPriority = OpeningEvaluator.evaluateOpening(
                strongAttacker,
                highPriorityDefender,
                objective,
                CandidateEdgeComponentPolicy.none()
        );
        OpeningEvaluator.EvaluatedEdge strongAttackerSofter = OpeningEvaluator.evaluateOpening(
                strongAttacker,
                softerDefender,
                objective,
                CandidateEdgeComponentPolicy.none()
        );
        OpeningEvaluator.EvaluatedEdge flexibleAttackerHighPriority = OpeningEvaluator.evaluateOpening(
                flexibleAttacker,
                highPriorityDefender,
                objective,
                CandidateEdgeComponentPolicy.none()
        );
        OpeningEvaluator.EvaluatedEdge flexibleAttackerSofter = OpeningEvaluator.evaluateOpening(
                flexibleAttacker,
                softerDefender,
                objective,
                CandidateEdgeComponentPolicy.none()
        );

        assertTrue(Float.isFinite(strongAttackerHighPriority.score()), "Expected high-priority defender to be admitted for the strong attacker");
        assertTrue(Float.isFinite(strongAttackerSofter.score()), "Expected softer defender to be admitted for the strong attacker");
        assertTrue(Float.isFinite(flexibleAttackerHighPriority.score()), "Expected high-priority defender to remain admitted for the flexible attacker");
        assertTrue(Float.isFinite(flexibleAttackerSofter.score()), "Expected softer defender to remain admitted for the flexible attacker");
        assertTrue(
                strongAttackerHighPriority.score() > strongAttackerSofter.score(),
                "Test setup must make the strong attacker emit the high-priority defender first"
        );
        assertTrue(
                flexibleAttackerSofter.score() > flexibleAttackerHighPriority.score(),
                "Test setup must leave the high-priority defender for the spillover path on the second attacker"
        );

        CompiledScenario scenario = SCENARIO_COMPILER.compile(
                List.of(strongAttacker, flexibleAttacker),
                List.of(highPriorityDefender, softerDefender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable out = new CandidateEdgeTable();

        OpeningEvaluator.evaluate(
                scenario,
                tuningWithCandidatesPerAttacker(1),
                OverrideSet.EMPTY,
                objective,
                new int[]{2, 2},
                new int[]{1, 1},
                out
        );

        boolean hasStrongAttackerHighPriority = false;
        boolean hasFlexibleAttackerSofter = false;
        boolean hasFlexibleAttackerHighPriority = false;
        for (int edge = 0; edge < out.edgeCount(); edge++) {
            int attackerNationId = scenario.attackerNationId(out.attackerIndex(edge));
            int defenderNationId = scenario.defenderNationId(out.defenderIndex(edge));
            if (attackerNationId == strongAttacker.nationId() && defenderNationId == highPriorityDefender.nationId()) {
                hasStrongAttackerHighPriority = true;
            }
            if (attackerNationId == flexibleAttacker.nationId() && defenderNationId == softerDefender.nationId()) {
                hasFlexibleAttackerSofter = true;
            }
            if (attackerNationId == flexibleAttacker.nationId() && defenderNationId == highPriorityDefender.nationId()) {
                hasFlexibleAttackerHighPriority = true;
            }
        }

        assertTrue(hasStrongAttackerHighPriority, "Expected the high-priority defender to keep the strong attacker's primary edge");
        assertTrue(hasFlexibleAttackerSofter, "Expected the second attacker to keep its softer primary edge");
        assertTrue(
                hasFlexibleAttackerHighPriority,
                "Spillover should still emit a high-priority defender while that defender remains below the global coverage target"
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
    void snapshotTargetPressureRewardsHighCapacityDefendersButKeepsBonusBounded() {
        DBNationSnapshot highCapacityDefender = buildNation(161, DEFENDER_TEAM, 2_250.0, 52,
                387_500, 31_000, 2_480, 387);
        DBNationSnapshot mediumCapacityDefender = buildNation(162, DEFENDER_TEAM, 1_900.0, 44,
                300_000, 24_000, 1_920, 300);

        double highCapacityRawPressure = OpeningMetricSummary.defenderControlPressure(
                OpeningMetricSummary.groundStrength(
                        highCapacityDefender.unit(MilitaryUnit.SOLDIER),
                        highCapacityDefender.unit(MilitaryUnit.TANK),
                        false
                ),
                highCapacityDefender.unit(MilitaryUnit.AIRCRAFT),
                highCapacityDefender.unit(MilitaryUnit.SHIP)
        );
        double mediumCapacityRawPressure = OpeningMetricSummary.defenderControlPressure(
                OpeningMetricSummary.groundStrength(
                        mediumCapacityDefender.unit(MilitaryUnit.SOLDIER),
                        mediumCapacityDefender.unit(MilitaryUnit.TANK),
                        false
                ),
                mediumCapacityDefender.unit(MilitaryUnit.AIRCRAFT),
                mediumCapacityDefender.unit(MilitaryUnit.SHIP)
        );

        double highCapacityPressure = OpeningMetricSummary.defenderControlPressure(highCapacityDefender);
        double mediumCapacityPressure = OpeningMetricSummary.defenderControlPressure(mediumCapacityDefender);

        assertTrue(highCapacityPressure > highCapacityRawPressure,
                "Snapshot-backed pressure should add strategic-capacity bonus for high-capacity defenders");
        assertTrue(mediumCapacityPressure > mediumCapacityRawPressure,
                "Snapshot-backed pressure should add strategic-capacity bonus for medium-capacity defenders too");
        assertTrue(highCapacityPressure > mediumCapacityPressure,
                "Higher-capacity strategically stronger defenders should carry more target pressure");
        assertTrue(highCapacityPressure - highCapacityRawPressure < 4.5d,
                "Strategic-capacity pressure bonus should stay bounded and not swamp the base military pressure");
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

        @Test
        void coveragePriorityCollectorPrefersHigherPriorityStrongDefenderLane() {
                OpeningEvaluator.CoveragePriorityCollector collector =
                                new OpeningEvaluator.CoveragePriorityCollector(1, CandidateEdgeComponentPolicy.none());

                collector.consider(50f, 1, 101, (byte) 0, (byte) 0, 150f, 0f, 0f, 0f, 0f, 0f, 0f);
                collector.consider(75f, 2, 102, (byte) 0, (byte) 0, 100f, 0f, 0f, 0f, 0f, 0f, 0f);
                collector.sortSelectedDescending();

                assertEquals(1, collector.size());
                int selectedIndex = collector.sortedIndexAt(0);
                assertEquals(2, collector.attackerIndexAt(selectedIndex));
                assertEquals(102, collector.defenderIndexAt(selectedIndex));
                assertEquals(75f, collector.priorityAt(selectedIndex), 1.0e-5f);
        }

        @Test
        void defenderCoverageRescueCanEmitPriorityCollectorEdge() {
                CandidateEdgeTable out = new CandidateEdgeTable();
                long[][] emittedPairWordsByAttacker = new long[2][1];
                int[] defenderCoverageCounts = new int[1];
                OpeningEvaluator.CoveragePriorityCollector collector =
                                new OpeningEvaluator.CoveragePriorityCollector(1, CandidateEdgeComponentPolicy.none());
                collector.consider(75f, 1, 0, (byte) 0, (byte) 0, 42f, 0f, 0f, 0f, 0f, 0f, 0f);
                collector.sortSelectedDescending();

                boolean emitted = OpeningDefenderCoverageRescue.emitSelectedEdge(
                                collector,
                                collector.sortedIndexAt(0),
                                out,
                                emittedPairWordsByAttacker,
                                defenderCoverageCounts
                );

                assertTrue(emitted);
                assertEquals(1, out.edgeCount());
                assertEquals(1, out.attackerIndex(0));
                assertEquals(0, out.defenderIndex(0));
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
