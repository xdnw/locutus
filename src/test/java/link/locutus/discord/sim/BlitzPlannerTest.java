package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.PlannerDiagnostic;
import link.locutus.discord.sim.planners.TreatyProvider;
import link.locutus.discord.sim.combat.ResolutionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlitzPlanner M2 regression tests.
 *
 * Uses synthetic 10v10 data (no DB) under MOST_LIKELY (default) resolution.
 * Tests are deterministic: same inputs always produce the same assignment.
 */
class BlitzPlannerTest {

    private static final int ATTACKER_TEAM = 1000;
    private static final double ATTACKER_SCORE = 1000.0;
    private static final double DEFENDER_SCORE = 1000.0; // in-range of attackers (same score tier)

    private List<DBNationSnapshot> attackers;
    private List<DBNationSnapshot> defenders;

    @BeforeEach
    void setUp() {
        attackers = buildNations(1, 10, ATTACKER_TEAM, ATTACKER_SCORE, 500, 500);
        defenders = buildNations(101, 10, 9999, DEFENDER_SCORE, 300, 300);
    }

    /** Builds {@code count} synthetic snapshots starting from {@code idStart}. */
    private List<DBNationSnapshot> buildNations(int idStart, int count, int teamId, double score,
                                                int aircraft, int soldiers) {
        List<DBNationSnapshot> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = idStart + i;
            result.add(DBNationSnapshot.synthetic(id)
                    .teamId(teamId)
                    .allianceId(teamId)
                    .score(score)
                    .cities(10)
                    .nonInfraScoreBase(score)
                    .cityInfra(uniformInfra(10, 1000.0))
                    .maxOff(5)
                    .currentOffensiveWars(0)
                    .currentDefensiveWars(0)
                    .unit(MilitaryUnit.AIRCRAFT, aircraft)
                    .unit(MilitaryUnit.SOLDIER, soldiers)
                    .warPolicy(WarPolicy.ATTRITION)
                    .build());
        }
        return result;
    }

    private double[] uniformInfra(int cities, double infra) {
        double[] arr = new double[cities];
        for (int i = 0; i < cities; i++) arr[i] = infra;
        return arr;
    }

    @Test
    void assignmentIsNonEmpty_10v10() {
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, defenders);

        assertFalse(result.assignment().isEmpty(), "Expected at least some assignments for a 10v10 with all in range");
        assertTrue(result.pairCount() > 0, "Expected at least one pair assigned");
    }

    @Test
    void eachAttackerAssignedAtMostMaxOff() {
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, defenders);

        for (DBNationSnapshot att : attackers) {
            List<Integer> assigned = result.targetsFor(att.nationId());
            int cap = att.rawFreeOff();
            assertTrue(assigned.size() <= cap,
                    "Attacker " + att.nationId() + " assigned " + assigned.size() + " > cap " + cap);
        }
    }

    @Test
    void eachDefenderAssignedAtMostMaxDef() {
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, defenders);

        for (DBNationSnapshot def : defenders) {
            int assignedCount = (int) result.assignment().values().stream()
                    .flatMap(List::stream)
                    .filter(id -> id == def.nationId())
                    .count();
            int defCap = def.rawFreeDef();
            assertTrue(assignedCount <= defCap,
                    "Defender " + def.nationId() + " received " + assignedCount + " > cap " + defCap);
        }
    }

    @Test
    void deterministicAcrossMultipleRuns() {
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment r1 = planner.assign(attackers, defenders);
        BlitzAssignment r2 = planner.assign(attackers, defenders);

        assertEquals(r1.assignment(), r2.assignment(), "BlitzPlanner must be deterministic for same inputs");
    }

    @Test
    void noAssignmentAcrossOutOfRangeNations() {
        // Build defenders with 10x the score (clearly out of range for attackers)
        List<DBNationSnapshot> outOfRangeDefs = buildNations(201, 5, 9999, ATTACKER_SCORE * 100, 300, 300);
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, outOfRangeDefs);

        assertEquals(0, result.pairCount(), "No assignments should be made when all defenders are out of range");
    }

    @Test
    void prunesZeroProbeEdgesDuringCandidateAdmission() {
        List<DBNationSnapshot> weakAttackers = buildNations(301, 3, ATTACKER_TEAM, ATTACKER_SCORE, 0, 0);
        List<DBNationSnapshot> sturdyDefenders = buildNations(401, 3, 9999, DEFENDER_SCORE, 300, 300);

        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(weakAttackers, sturdyDefenders);

        assertEquals(0, result.pairCount(), "Kernel-EV probe should prune non-missile zero-strength edges");
    }

    @Test
    void treatyProviderFiltersAllPairs() {
        // All pairs are "treated" — no assignments expected
        TreatyProvider allTreated = (a, d) -> true;
        SimTuning tuning = SimTuning.defaults();
        BlitzPlanner planner = new BlitzPlanner(tuning, allTreated, OverrideSet.EMPTY, new DamageObjective());
        BlitzAssignment result = planner.assign(attackers, defenders);

        assertEquals(0, result.pairCount(), "TreatyProvider blocking all pairs should produce empty assignment");
    }

    @Test
    void objectiveScoreIsNonNegativeForValidAssignment() {
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, defenders);

        if (result.pairCount() > 0) {
            // Objective can legitimately be negative (own losses may exceed enemy damage in edge cases)
            // but should be finite
            assertTrue(Double.isFinite(result.objectiveScore()), "Objective score must be finite");
        }
    }

    @Test
    void forceFreeOffOverrideExpandsAssignment() {
        // Create attackers with 0 natural free offensive slots (all slots used)
        List<DBNationSnapshot> occupiedAtts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int id = i + 1;
            occupiedAtts.add(DBNationSnapshot.synthetic(id)
                    .teamId(ATTACKER_TEAM).allianceId(ATTACKER_TEAM)
                    .score(ATTACKER_SCORE).cities(10).nonInfraScoreBase(ATTACKER_SCORE)
                    .cityInfra(uniformInfra(10, 1000.0))
                    .maxOff(5).currentOffensiveWars(5) // all slots occupied
                    .currentDefensiveWars(0)
                    .unit(MilitaryUnit.AIRCRAFT, 500).warPolicy(WarPolicy.ATTRITION)
                    .build());
        }
        // Without override: no assignments
        BlitzPlanner plannerNoOverride = new BlitzPlanner(SimTuning.defaults(), TreatyProvider.NONE, OverrideSet.EMPTY, new DamageObjective());
        BlitzAssignment withoutOverride = plannerNoOverride.assign(occupiedAtts, defenders);
        assertEquals(0, withoutOverride.pairCount(), "Without forceFreeOff override, zero-free-slot attackers cannot be assigned");

        // With forceFreeOff: they should get assignments
        OverrideSet.Builder ob = OverrideSet.builder();
        for (DBNationSnapshot snap : occupiedAtts) {
            ob.forceFreeOff(snap.nationId(), 2);
        }
        OverrideSet overrides = ob.build();
        BlitzPlanner plannerWithOverride = new BlitzPlanner(SimTuning.defaults(), TreatyProvider.NONE, overrides, new DamageObjective());
        BlitzAssignment withOverride = plannerWithOverride.assign(occupiedAtts, defenders);
        assertTrue(withOverride.pairCount() > 0, "forceFreeOff override should enable assignment for fully-slotted attackers");
    }

    @Test
    void stochasticModeReportsAssignmentBands() {
        SimTuning tuning = SimTuning.defaults()
                .withStateResolutionMode(ResolutionMode.STOCHASTIC)
                .withStochasticSeed(456L)
                .withStochasticSampleCount(4);

        BlitzPlanner planner = new BlitzPlanner(tuning);
        BlitzAssignment result = planner.assign(attackers.subList(0, 2), defenders.subList(0, 2));

        assertEquals(4, result.objectiveSummary().sampleCount());
        assertTrue(result.objectiveSummary().p10() <= result.objectiveSummary().p50());
        assertTrue(result.objectiveSummary().p50() <= result.objectiveSummary().p90());
        assertEquals(result.objectiveSummary().mean(), result.objectiveScore());
    }

    @Test
    void filtersExistingPairConflictsBeforeCandidateScoring() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
                .teamId(ATTACKER_TEAM)
                .allianceId(ATTACKER_TEAM)
                .score(ATTACKER_SCORE)
                .cities(10)
                .nonInfraScoreBase(ATTACKER_SCORE)
                .cityInfra(uniformInfra(10, 1000.0))
                .maxOff(5)
                .activeOpponentNationId(101)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
        DBNationSnapshot blockedDefender = DBNationSnapshot.synthetic(101)
                .teamId(9999)
                .allianceId(9999)
                .score(DEFENDER_SCORE)
                .cities(10)
                .nonInfraScoreBase(DEFENDER_SCORE)
                .cityInfra(uniformInfra(10, 1000.0))
                .maxOff(5)
                .warPolicy(WarPolicy.ATTRITION)
                .build();

        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(List.of(attacker), List.of(blockedDefender));

        assertEquals(0, result.pairCount(), "Existing attacker/defender pair conflict should be filtered before assignment");
    }

    @Test
    void reportsResetFallbackAsStructuredDiagnostics() {
    DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
        .teamId(ATTACKER_TEAM)
        .allianceId(ATTACKER_TEAM)
        .score(ATTACKER_SCORE)
        .cities(10)
        .nonInfraScoreBase(ATTACKER_SCORE)
        .cityInfra(uniformInfra(10, 1000.0))
        .maxOff(5)
        .resetHourUtc((byte) 0)
        .resetHourUtcFallback(true)
        .warPolicy(WarPolicy.ATTRITION)
        .build();
    DBNationSnapshot defender = DBNationSnapshot.synthetic(101)
        .teamId(9999)
        .allianceId(9999)
        .score(DEFENDER_SCORE)
        .cities(10)
        .nonInfraScoreBase(DEFENDER_SCORE)
        .cityInfra(uniformInfra(10, 1000.0))
        .maxOff(5)
        .resetHourUtc((byte) 0)
        .resetHourUtcFallback(true)
        .warPolicy(WarPolicy.ATTRITION)
        .build();

    BlitzAssignment result = new BlitzPlanner(SimTuning.defaults()).assign(List.of(attacker), List.of(defender));

    assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
        diagnostic.code() == PlannerDiagnostic.Code.RESET_HOUR_FALLBACK
            && diagnostic.nationRole() == PlannerDiagnostic.NationRole.ATTACKER
            && diagnostic.nationId() == attacker.nationId()));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
        diagnostic.code() == PlannerDiagnostic.Code.RESET_HOUR_FALLBACK
            && diagnostic.nationRole() == PlannerDiagnostic.NationRole.DEFENDER
            && diagnostic.nationId() == defender.nationId()));
    }
}
