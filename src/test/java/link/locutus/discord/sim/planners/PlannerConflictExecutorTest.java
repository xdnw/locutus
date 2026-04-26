package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlannerConflictExecutorTest {

    @Test
    void plannerLocalConflictRollbackRestoresProjectionState() {
        DBNationSnapshot attacker = nation(201, 1)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .unit(MilitaryUnit.SHIP, 6)
                .build();
        DBNationSnapshot defender = nation(202, 2)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );

        PlannerProjectionResult before = conflict.project();
        PlannerLocalConflict.Mark mark = conflict.mark();

        conflict.applyAssignmentOpenings(Map.of(attacker.nationId(), List.of(defender.nationId())));
        PlannerProjectionResult mutated = conflict.project();
        assertFalse(mutated.activeWars().isEmpty());

        conflict.rollback(mark);
        PlannerProjectionResult afterRollback = conflict.project();

        assertTrue(afterRollback.activeWars().isEmpty());
        assertEquals(
                before.snapshotsById().get(attacker.nationId()).unit(MilitaryUnit.SOLDIER),
                afterRollback.snapshotsById().get(attacker.nationId()).unit(MilitaryUnit.SOLDIER)
        );
        assertEquals(
                before.snapshotsById().get(defender.nationId()).cityInfra()[0],
                afterRollback.snapshotsById().get(defender.nationId()).cityInfra()[0],
                1e-9
        );
        assertNull(afterRollback.cityInfraOverlaysByNation().get(defender.nationId()));
    }

    @Test
    void plannerLocalConflictApplyCommitsMutationsPastMark() {
        DBNationSnapshot attacker = nation(203, 1)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .unit(MilitaryUnit.SHIP, 6)
                .build();
        DBNationSnapshot defender = nation(204, 2)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );

        PlannerLocalConflict.Mark mark = conflict.mark();
        conflict.applyAssignmentOpenings(Map.of(attacker.nationId(), List.of(defender.nationId())));
        conflict.apply(mark);

        PlannerProjectionResult committed = conflict.project();
        assertFalse(committed.activeWars().isEmpty());
    }

    @Test
    void plannerLocalConflictMarksMustBeAppliedInLifoOrder() {
        DBNationSnapshot attacker = nation(205, 1)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .unit(MilitaryUnit.SHIP, 6)
                .build();
        DBNationSnapshot defender = nation(206, 2)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );

        PlannerLocalConflict.Mark outer = conflict.mark();
        PlannerLocalConflict.Mark inner = conflict.mark();

        assertThrows(IllegalStateException.class, () -> conflict.apply(outer));
        conflict.rollback(inner);
        conflict.apply(outer);
    }

    @Test
    void deterministicEvCannotDrivePlannerAssignmentScoring() {
        DBNationSnapshot attacker = nation(1, 1)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot defender = nation(2, 2)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 500)
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> PlannerConflictExecutor.scoreAssignment(
                        new SimTuning(ResolutionMode.DETERMINISTIC_EV),
                        OverrideSet.builder().build(),
                        new DamageObjective(),
                        Map.of(attacker.nationId(), List.of(defender.nationId())),
                        List.of(attacker),
                        List.of(defender)
                )
        );
    }

    @Test
    void deterministicEvCannotDrivePlannerProjection() {
        DBNationSnapshot attacker = nation(11, 1)
                .unit(MilitaryUnit.SOLDIER, 10_000)
                .unit(MilitaryUnit.TANK, 450)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot defender = nation(12, 2)
                .unit(MilitaryUnit.SOLDIER, 8_500)
                .unit(MilitaryUnit.TANK, 250)
                .unit(MilitaryUnit.AIRCRAFT, 450)
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> PlannerConflictExecutor.projectAssignmentHorizon(
                        new SimTuning(ResolutionMode.DETERMINISTIC_EV),
                        OverrideSet.builder().build(),
                        List.of(attacker, defender),
                        Map.of(attacker.nationId(), List.of(defender.nationId())),
                        2
                )
        );
    }

    @Test
    void deterministicEvCannotDrivePlannerProjectionStateHorizon() {
        DBNationSnapshot attacker = nation(13, 1)
                .unit(MilitaryUnit.SOLDIER, 10_000)
                .unit(MilitaryUnit.TANK, 450)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot defender = nation(14, 2)
                .unit(MilitaryUnit.SOLDIER, 8_500)
                .unit(MilitaryUnit.TANK, 250)
                .unit(MilitaryUnit.AIRCRAFT, 450)
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> PlannerConflictExecutor.projectAssignmentStateHorizon(
                        new SimTuning(ResolutionMode.DETERMINISTIC_EV),
                        OverrideSet.builder().build(),
                        List.of(attacker, defender),
                        Map.of(attacker.nationId(), List.of(defender.nationId())),
                        2
                )
        );
    }

    @Test
    void deterministicEvCannotDrivePlannerProjectionStateHorizonWithTransitionSemantics() {
        DBNationSnapshot attacker = nation(15, 1)
                .unit(MilitaryUnit.SOLDIER, 10_000)
                .unit(MilitaryUnit.TANK, 450)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot defender = nation(16, 2)
                .unit(MilitaryUnit.SOLDIER, 8_500)
                .unit(MilitaryUnit.TANK, 250)
                .unit(MilitaryUnit.AIRCRAFT, 450)
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> PlannerConflictExecutor.projectAssignmentStateHorizon(
                        new SimTuning(ResolutionMode.DETERMINISTIC_EV),
                        OverrideSet.builder().build(),
                        List.of(attacker, defender),
                        Map.of(attacker.nationId(), List.of(defender.nationId())),
                        2,
                        PlannerTransitionSemantics.ALL_OPTIONAL
                )
        );
    }

    @Test
    void deterministicEvCannotDriveDeclaredWarValidationScripts() {
        DBNationSnapshot attacker = nation(17, 1)
                .unit(MilitaryUnit.SOLDIER, 10_000)
                .unit(MilitaryUnit.TANK, 450)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot defender = nation(18, 2)
                .unit(MilitaryUnit.SOLDIER, 8_500)
                .unit(MilitaryUnit.TANK, 250)
                .unit(MilitaryUnit.AIRCRAFT, 450)
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> PlannerConflictExecutor.evaluateDeclaredWar(
                        new SimTuning(ResolutionMode.DETERMINISTIC_EV),
                        OverrideSet.builder().build(),
                        new DamageObjective(),
                        attacker,
                        defender,
                        2,
                        PlannerExactValidatorScripts.DEFAULT
                )
        );
    }

    @Test
    void exactValidatorScriptsSplitRebuildAndPolicyCooldownSemantics() {
        DBNationSnapshot attacker = nation(19, 1)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .policyCooldownTurnsRemaining(3)
                .pendingBuyNextTurn(MilitaryUnit.SOLDIER, 250)
                .build();
        DBNationSnapshot defender = nation(20, 2)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .build();

        PlannerExactValidatorScripts rebuildOnly = new PlannerExactValidatorScripts(
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                PlannerExactValidatorScripts.AttackSequenceProfile.CONVENTIONAL,
                0,
                EnumSet.noneOf(AttackType.class)
        );
        assertEquals(new PlannerTransitionSemantics(false, true, false), rebuildOnly.transitionSemantics());

        PlannerLocalConflict rebuildConflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY),
                rebuildOnly.transitionSemantics()
        );
        rebuildConflict.simulateDeclaredWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                2,
                rebuildOnly
        );
        DBNationSnapshot rebuildProjected = rebuildConflict.project().snapshotsById().get(attacker.nationId());
        assertNotNull(rebuildProjected);
        assertEquals(1_250, rebuildProjected.unit(MilitaryUnit.SOLDIER));
        assertEquals(3, rebuildProjected.policyCooldownTurnsRemaining());
        assertEquals(0, rebuildProjected.pendingBuysNextTurn(MilitaryUnit.SOLDIER));

        PlannerExactValidatorScripts policyOnly = new PlannerExactValidatorScripts(
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                PlannerExactValidatorScripts.AttackSequenceProfile.CONVENTIONAL,
                0,
                EnumSet.noneOf(AttackType.class)
        );
        assertEquals(new PlannerTransitionSemantics(true, false, false), policyOnly.transitionSemantics());

        PlannerLocalConflict policyConflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY),
                policyOnly.transitionSemantics()
        );
        policyConflict.simulateDeclaredWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                2,
                policyOnly
        );
        DBNationSnapshot policyProjected = policyConflict.project().snapshotsById().get(attacker.nationId());
        assertNotNull(policyProjected);
        assertEquals(1_000, policyProjected.unit(MilitaryUnit.SOLDIER));
        assertEquals(2, policyProjected.policyCooldownTurnsRemaining());
        assertEquals(250, policyProjected.pendingBuysNextTurn(MilitaryUnit.SOLDIER));
    }

    @Test
    void exactValidatorTurnSemanticsRollbackRestoresPendingBuysAndCooldown() {
        DBNationSnapshot attacker = nation(21, 1)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .policyCooldownTurnsRemaining(3)
                .unitBoughtToday(MilitaryUnit.SOLDIER, 200)
                .pendingBuyNextTurn(MilitaryUnit.SOLDIER, 250)
                .build();
        DBNationSnapshot defender = nation(22, 2)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .build();

        PlannerExactValidatorScripts scripts = new PlannerExactValidatorScripts(
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                PlannerExactValidatorScripts.AttackSequenceProfile.CONVENTIONAL,
                0,
                EnumSet.noneOf(AttackType.class)
        );
        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY),
                scripts.transitionSemantics()
        );

        PlannerLocalConflict.Mark mark = conflict.mark();
        conflict.simulateDeclaredWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                2,
                scripts
        );

        DBNationSnapshot mutated = conflict.project().snapshotsById().get(attacker.nationId());
        assertNotNull(mutated);
        assertEquals(1_250, mutated.unit(MilitaryUnit.SOLDIER));
        assertEquals(3, mutated.policyCooldownTurnsRemaining());
        assertEquals(200, mutated.unitsBoughtToday(MilitaryUnit.SOLDIER));
        assertEquals(0, mutated.pendingBuysNextTurn(MilitaryUnit.SOLDIER));

        conflict.rollback(mark);

        DBNationSnapshot restored = conflict.project().snapshotsById().get(attacker.nationId());
        assertNotNull(restored);
        assertEquals(1_000, restored.unit(MilitaryUnit.SOLDIER));
        assertEquals(3, restored.policyCooldownTurnsRemaining());
        assertEquals(200, restored.unitsBoughtToday(MilitaryUnit.SOLDIER));
        assertEquals(250, restored.pendingBuysNextTurn(MilitaryUnit.SOLDIER));
    }

    @Test
    void plannerProjectionPreservesBeigeTurnsAndDerivedDailyBuyBonus() {
        DBNationSnapshot attacker = nation(23, 1)
                .cities(5)
                .beigeTurns(3)
                .build();
        DBNationSnapshot defender = nation(24, 2)
                .cities(5)
                .build();

        PlannerProjectionResult projection = PlannerConflictExecutor.projectAssignmentHorizon(
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                List.of(attacker, defender),
                Map.of(),
                1
        );

        DBNationSnapshot projected = projection.snapshotsById().get(attacker.nationId());
        assertNotNull(projected);
        assertEquals(2, projected.beigeTurns());
        assertFalse(projected.hasActiveWars());
        int projectedBaseCap = MilitaryUnit.SOLDIER.getMaxPerDay(
                projected.cities(),
                projected::hasProject,
                research -> research.getLevel(projected.researchBits())
        );
        assertEquals((int) Math.round(projectedBaseCap * 1.15d), projected.dailyBuyCap(MilitaryUnit.SOLDIER));
    }

    @Test
    void snapshotBeigeDailyBuyBonusTurnsOffWhenActiveWarsExist() {
        DBNationSnapshot idleBeigeNation = nation(25, 1)
                .cities(5)
                .beigeTurns(2)
                .build();
        DBNationSnapshot activeWarBeigeNation = idleBeigeNation.toBuilder()
                .currentOffensiveWars(1)
                .activeOpponentNationId(999)
                .build();

        int baseCap = MilitaryUnit.SOLDIER.getMaxPerDay(
                idleBeigeNation.cities(),
                idleBeigeNation::hasProject,
                research -> research.getLevel(idleBeigeNation.researchBits())
        );

        assertEquals((int) Math.round(baseCap * 1.15d), idleBeigeNation.dailyBuyCap(MilitaryUnit.SOLDIER));
        assertEquals(baseCap, activeWarBeigeNation.dailyBuyCap(MilitaryUnit.SOLDIER));
    }

    @Test
    void changedBundleExtractionKeepsConnectedComponentAndScoresExactly() {
        DBNationSnapshot attackerOne = nation(31, 1)
                .unit(MilitaryUnit.SOLDIER, 12_500)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 950)
                .build();
        DBNationSnapshot attackerTwo = nation(32, 1)
                .unit(MilitaryUnit.SOLDIER, 11_500)
                .unit(MilitaryUnit.TANK, 450)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot unrelatedAttacker = nation(33, 1)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 500)
                .build();

        DBNationSnapshot defenderOne = nation(34, 2)
                .unit(MilitaryUnit.SOLDIER, 9_500)
                .unit(MilitaryUnit.TANK, 320)
                .unit(MilitaryUnit.AIRCRAFT, 650)
                .build();
        DBNationSnapshot defenderTwo = nation(35, 2)
                .unit(MilitaryUnit.SOLDIER, 8_800)
                .unit(MilitaryUnit.TANK, 280)
                .unit(MilitaryUnit.AIRCRAFT, 620)
                .build();
        DBNationSnapshot unrelatedDefender = nation(36, 2)
                .unit(MilitaryUnit.SOLDIER, 8_200)
                .unit(MilitaryUnit.TANK, 240)
                .unit(MilitaryUnit.AIRCRAFT, 580)
                .build();

        List<DBNationSnapshot> attackers = List.of(attackerOne, attackerTwo, unrelatedAttacker);
        List<DBNationSnapshot> defenders = List.of(defenderOne, defenderTwo, unrelatedDefender);

        Map<Integer, List<Integer>> currentAssignment = Map.of(
                attackerOne.nationId(), List.of(defenderOne.nationId()),
                attackerTwo.nationId(), List.of(defenderOne.nationId()),
                unrelatedAttacker.nationId(), List.of(unrelatedDefender.nationId())
        );
        Map<Integer, List<Integer>> candidateAssignment = Map.of(
                attackerOne.nationId(), List.of(defenderTwo.nationId()),
                attackerTwo.nationId(), List.of(defenderOne.nationId()),
                unrelatedAttacker.nationId(), List.of(unrelatedDefender.nationId())
        );
        PlannerAssignmentChange candidateChange = PlannerAssignmentChange.single(
                attackerOne.nationId(),
                List.of(defenderTwo.nationId())
        );

        PlannerConflictBundle bundle = PlannerConflictBundle.extract(
                currentAssignment,
                candidateChange,
                attackers,
                defenders
        );

        assertEquals(List.of(attackerOne.nationId(), attackerTwo.nationId()), nationIds(bundle.attackers()));
        assertEquals(List.of(defenderOne.nationId(), defenderTwo.nationId()), nationIds(bundle.defenders()));
        assertEquals(
                Map.of(
                        attackerOne.nationId(), List.of(defenderOne.nationId()),
                        attackerTwo.nationId(), List.of(defenderOne.nationId())
                ),
                bundle.currentAssignment().toAssignmentMap()
        );
        assertEquals(
                Map.of(
                        attackerOne.nationId(), List.of(defenderTwo.nationId()),
                        attackerTwo.nationId(), List.of(defenderOne.nationId())
                ),
                bundle.candidateAssignment().toAssignmentMap()
        );

        double fullCurrentScore = PlannerConflictExecutor.scoreAssignment(
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new DamageObjective(),
                currentAssignment,
                attackers,
                defenders
        );
        double fullCandidateScore = PlannerConflictExecutor.scoreAssignment(
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new DamageObjective(),
                candidateAssignment,
                attackers,
                defenders
        );

        assertEquals(
                fullCandidateScore - fullCurrentScore,
                PlannerConflictExecutor.scoreAssignmentDelta(
                        new SimTuning(ResolutionMode.MOST_LIKELY),
                        OverrideSet.EMPTY,
                        new DamageObjective(),
                        currentAssignment,
                        candidateChange,
                        attackers,
                        defenders,
                        attackerOne.teamId()
                ),
                1e-9
        );

        PlannerAssignmentSession session = PlannerAssignmentSession.create(
                currentAssignment,
                attackers,
                defenders,
                Map.of(
                        attackerOne.nationId(), 1,
                        attackerTwo.nationId(), 1,
                        unrelatedAttacker.nationId(), 1
                ),
                Map.of(
                        defenderOne.nationId(), 2,
                        defenderTwo.nationId(), 1,
                        unrelatedDefender.nationId(), 1
                )
        );
        PlannerConflictBundle sessionBundle = PlannerConflictBundle.extract(
                session,
                candidateChange,
                attackers,
                defenders
        );
        assertEquals(bundle.attackers(), sessionBundle.attackers());
        assertEquals(bundle.defenders(), sessionBundle.defenders());
        assertEquals(bundle.currentAssignment(), sessionBundle.currentAssignment());
        assertEquals(bundle.candidateAssignment(), sessionBundle.candidateAssignment());
        assertEquals(
                fullCandidateScore - fullCurrentScore,
                PlannerConflictExecutor.scoreAssignmentDelta(
                        new SimTuning(ResolutionMode.MOST_LIKELY),
                        OverrideSet.EMPTY,
                        new DamageObjective(),
                        session,
                        candidateChange,
                        attackers,
                        defenders,
                        attackerOne.teamId()
                ),
                1e-9
        );
    }

    @Test
    void projectionStateHorizonCarriesSparseTouchedCityOverlayAcrossAdvances() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.SOLDIER, 14_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                .unit(MilitaryUnit.SHIP, 6)
                .build();
        DBNationSnapshot defender = nation(102, 2)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 650)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

        SimTuning tuning = new SimTuning(ResolutionMode.MOST_LIKELY);
        PlannerProjectionState state = PlannerConflictExecutor.projectAssignmentStateHorizon(
                tuning,
                OverrideSet.EMPTY,
                List.of(attacker, defender),
                Map.of(attacker.nationId(), List.of(defender.nationId())),
                1
        );

        PlannerProjectionResult firstProjection = state.toProjectionResult();
        PlannerCityInfraOverlay firstOverlay = firstProjection.cityInfraOverlaysByNation().get(defender.nationId());
        assertNotNull(firstOverlay);
        assertFalse(firstOverlay.isEmpty());

        PlannerProjectionState carried = state.advance(tuning, Map.of(), 1);
        PlannerProjectionResult secondProjection = carried.toProjectionResult();
        PlannerCityInfraOverlay carriedOverlay = secondProjection.cityInfraOverlaysByNation().get(defender.nationId());

        assertNotNull(carriedOverlay);
        assertEquals(firstOverlay, carriedOverlay);
    }

    @Test
    void snapshotDerivesPlannerCombatProfileFromPolicyAndProjects() {
        // PIRATE_ECONOMY has ordinal 19 so projectBits = 1L << 19
        long pirateEconomyBit = 1L << Projects.PIRATE_ECONOMY.ordinal();
        DBNationSnapshot attacker = nation(1, 1)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .warPolicy(WarPolicy.PIRATE)
                .projectBits(pirateEconomyBit)
                .researchBits(0b11111) // level-1 all research
                .build();

        // Snapshot should carry combat bits and policy-derived combat profile
        assertEquals(pirateEconomyBit, attacker.projectBits());
        assertEquals(0b11111, attacker.researchBits());
        assertEquals(1.4, attacker.looterModifier(false), 1e-9);
        assertEquals(1.45, attacker.looterModifier(true), 1e-9);
        assertEquals(1.0, attacker.lootModifier(), 1e-9);
        assertEquals(1.0, attacker.infraAttackModifier(AttackType.GROUND), 1e-9);
        assertEquals(1.0, attacker.infraDefendModifier(AttackType.GROUND), 1e-9);
        assertFalse(attacker.blitzkriegActive());
    }

        @Test
        void snapshotDerivesMaxOffFromProjectBitsWhenNotExplicitlySet() {
                long pirateBits = (1L << Projects.PIRATE_ECONOMY.ordinal())
                                | (1L << Projects.ADVANCED_PIRATE_ECONOMY.ordinal());
                DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
                                .teamId(1)
                                .allianceId(1)
                                .score(1_000)
                                .cities(3)
                                .nonInfraScoreBase(700)
                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                .warPolicy(WarPolicy.ATTRITION)
                                .projectBits(pirateBits)
                                .build();

                assertEquals(7, attacker.maxOff());
                assertEquals(7, attacker.rawFreeOff());
        }

    @Test
    void snapshotRoundTripPreservesResearchAndProjectBits() {
        long bits = (1L << 5) | (1L << 19);
        DBNationSnapshot original = nation(1, 1)
                .unit(MilitaryUnit.SOLDIER, 10_000)
                                .warPolicy(WarPolicy.BLITZKRIEG)
                                .blitzkriegActive(true)
                .projectBits(bits)
                .researchBits(0x1F) // first research at level 1
                                .lootModifier(1.23)
                                .looterModifiers(1.34, 1.12)
                                .infraModifiers(uniformInfra(1.07), uniformInfra(0.93))
                .build();

        // projectBits and researchBits survive a projection round-trip
        var result = PlannerConflictExecutor.projectAssignmentHorizon(
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.builder().build(),
                List.of(original),
                Map.of(),
                1
        );
        DBNationSnapshot projected = result.snapshotsById().get(original.nationId());
        assertEquals(bits, projected.projectBits(), "projectBits must survive round-trip");
        assertEquals(0x1F, projected.researchBits(), "researchBits must survive round-trip");
                assertEquals(1.23, projected.lootModifier(), 1e-9, "loot modifier must survive round-trip");
                assertEquals(1.34, projected.looterModifier(true), 1e-9, "ground looter modifier must survive round-trip");
                assertEquals(1.12, projected.looterModifier(false), 1e-9, "non-ground looter modifier must survive round-trip");
                assertEquals(1.07, projected.infraAttackModifier(AttackType.GROUND), 1e-9, "infra attack profile must survive round-trip");
                assertEquals(0.93, projected.infraDefendModifier(AttackType.GROUND), 1e-9, "infra defend profile must survive round-trip");
                assertTrue(projected.blitzkriegActive(), "blitz flag must survive round-trip");
    }

        @Test
        void damageObjectiveRetainsOnlyHarmAndExposureCandidateComponents() {
                CandidateEdgeComponentPolicy policy = new DamageObjective().candidateEdgeComponentPolicy();
                assertEquals(CandidateEdgeComponentPolicy.harmExposureOnly(), policy);

        DBNationSnapshot attacker = nation(301, 1)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .unit(MilitaryUnit.SHIP, 6)
                .build();
        DBNationSnapshot defender = nation(302, 2)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.5f, defender.nationId(), 0.5f)
        );

        CandidateEdgeTable table = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new DamageObjective(),
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                table
        );

        assertEquals(1, table.edgeCount());
        assertEquals(WarType.ATT.ordinal(), table.preferredWarTypeId(0));
        assertTrue(table.retainsImmediateHarm());
        assertTrue(table.retainsSelfExposure());
        assertFalse(table.retainsResourceSwing());
        assertFalse(table.retainsControlLeverage());
        assertFalse(table.retainsFutureWarLeverage());

        OpeningEvaluator.EvaluatedEdge expected = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new DamageObjective(),
                policy
        );

        assertEquals(expected.preferredWarTypeId(), table.preferredWarTypeId(0));
        assertEquals(expected.firstAttackTypeId(), table.bestAttackTypeId(0));
        assertEquals(expected.immediateHarm(), table.immediateHarm(0), 1e-5f);
        assertEquals(expected.selfExposure(), table.selfExposure(0), 1e-5f);
    }

    @Test
    void exactValidatorRetainsNonDamageComponentsForCustomObjective() {
        DBNationSnapshot attacker = nation(303, 1)
                .unit(MilitaryUnit.SOLDIER, 65_000)
                .unit(MilitaryUnit.TANK, 3_500)
                .unit(MilitaryUnit.AIRCRAFT, 2_200)
                .unit(MilitaryUnit.SHIP, 18)
                .resource(ResourceType.MONEY, 0d)
                .warPolicy(WarPolicy.PIRATE)
                .projectBits(1L << Projects.PIRATE_ECONOMY.ordinal())
                .build();
        DBNationSnapshot defender = nation(304, 2)
                .unit(MilitaryUnit.SOLDIER, 5_500)
                .unit(MilitaryUnit.TANK, 220)
                .unit(MilitaryUnit.AIRCRAFT, 120)
                .unit(MilitaryUnit.SHIP, 3)
                .resource(ResourceType.MONEY, 5_000_000d)
                .warPolicy(WarPolicy.ATTRITION)
                .build();

        CandidateEdgeComponentPolicy allComponents = new CandidateEdgeComponentPolicy(true, true, true, true, true);
        DamageObjective objective = new RetainedComponentDamageObjective(allComponents);
        PlannerExactValidatorScripts groundOnlyScripts = new PlannerExactValidatorScripts(
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                PlannerExactValidatorScripts.AttackSequenceProfile.CONVENTIONAL,
                3,
                EnumSet.of(AttackType.GROUND)
        );

        PlannerConflictExecutor.DeclaredWarEvaluation evaluation = PlannerConflictExecutor.evaluateDeclaredWarDetailed(
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                objective,
                attacker,
                defender,
                1,
                allComponents,
                groundOnlyScripts
        );

        assertTrue(Double.isFinite(evaluation.objectiveScore()));
        assertTrue(evaluation.resourceSwing() > 0d);
        assertTrue(evaluation.controlLeverage() > 0d);
        assertTrue(evaluation.futureWarLeverage() > 0d);
    }

    @Test
    void openingEvaluatorRetainsRequestedComponentsForCustomObjective() {
        DBNationSnapshot attacker = nation(305, 1)
                .unit(MilitaryUnit.SOLDIER, 65_000)
                .unit(MilitaryUnit.TANK, 3_500)
                .unit(MilitaryUnit.AIRCRAFT, 2_200)
                .unit(MilitaryUnit.SHIP, 18)
                .resource(ResourceType.MONEY, 0d)
                .warPolicy(WarPolicy.PIRATE)
                .projectBits(1L << Projects.PIRATE_ECONOMY.ordinal())
                .build();
        DBNationSnapshot defender = nation(306, 2)
                .unit(MilitaryUnit.SOLDIER, 5_500)
                .unit(MilitaryUnit.TANK, 220)
                .unit(MilitaryUnit.AIRCRAFT, 120)
                .unit(MilitaryUnit.SHIP, 3)
                .resource(ResourceType.MONEY, 5_000_000d)
                .warPolicy(WarPolicy.ATTRITION)
                .build();

        CandidateEdgeComponentPolicy allComponents = new CandidateEdgeComponentPolicy(true, true, true, true, true);
        DamageObjective objective = new RetainedComponentDamageObjective(allComponents);
        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.5f, defender.nationId(), 0.5f)
        );

        CandidateEdgeTable table = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                objective,
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                table
        );

        assertEquals(1, table.edgeCount());
        assertTrue(table.retainsImmediateHarm());
        assertTrue(table.retainsSelfExposure());
        assertTrue(table.retainsResourceSwing());
        assertTrue(table.retainsControlLeverage());
        assertTrue(table.retainsFutureWarLeverage());

        OpeningEvaluator.EvaluatedEdge expected = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                objective,
                allComponents
        );

        assertEquals(expected.preferredWarTypeId(), table.preferredWarTypeId(0));
        assertEquals(expected.firstAttackTypeId(), table.bestAttackTypeId(0));
        assertEquals(expected.immediateHarm(), table.immediateHarm(0), 1e-5f);
        assertEquals(expected.selfExposure(), table.selfExposure(0), 1e-5f);
        assertEquals(expected.resourceSwing(), table.resourceSwing(0), 1e-5f);
        assertEquals(expected.controlLeverage(), table.controlLeverage(0), 1e-5f);
        assertEquals(expected.futureWarLeverage(), table.futureWarLeverage(0), 1e-5f);
    }

    @Test
    void openingEvaluatorRetainsOnlyRequestedSparseComponentSubset() {
        DBNationSnapshot attacker = nation(321, 1)
                .unit(MilitaryUnit.SOLDIER, 65_000)
                .unit(MilitaryUnit.TANK, 3_500)
                .unit(MilitaryUnit.AIRCRAFT, 2_200)
                .unit(MilitaryUnit.SHIP, 18)
                .resource(ResourceType.MONEY, 0d)
                .warPolicy(WarPolicy.PIRATE)
                .projectBits(1L << Projects.PIRATE_ECONOMY.ordinal())
                .build();
        DBNationSnapshot defender = nation(322, 2)
                .unit(MilitaryUnit.SOLDIER, 5_500)
                .unit(MilitaryUnit.TANK, 220)
                .unit(MilitaryUnit.AIRCRAFT, 120)
                .unit(MilitaryUnit.SHIP, 3)
                .resource(ResourceType.MONEY, 5_000_000d)
                .warPolicy(WarPolicy.ATTRITION)
                .build();

        CandidateEdgeComponentPolicy sparsePolicy = new CandidateEdgeComponentPolicy(false, false, true, false, true);
        DamageObjective objective = new RetainedComponentDamageObjective(sparsePolicy);
        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.5f, defender.nationId(), 0.5f)
        );

        CandidateEdgeTable table = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                objective,
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                table
        );

        assertEquals(1, table.edgeCount());
        assertFalse(table.retainsImmediateHarm());
        assertFalse(table.retainsSelfExposure());
        assertTrue(table.retainsResourceSwing());
        assertFalse(table.retainsControlLeverage());
        assertTrue(table.retainsFutureWarLeverage());

        OpeningEvaluator.EvaluatedEdge expected = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                objective,
                sparsePolicy
        );

        assertEquals(expected.preferredWarTypeId(), table.preferredWarTypeId(0));
        assertEquals(expected.firstAttackTypeId(), table.bestAttackTypeId(0));
        assertEquals(expected.resourceSwing(), table.resourceSwing(0), 1e-5f);
        assertEquals(expected.futureWarLeverage(), table.futureWarLeverage(0), 1e-5f);
    }

    @Test
    void candidateEdgeComponentsRetainOnlyRequestedArrays() {
        CandidateEdgeComponents components = new CandidateEdgeComponents(
                2,
                new CandidateEdgeComponentPolicy(false, true, false, true, false)
        );

        assertFalse(components.retainsImmediateHarm());
        assertTrue(components.retainsSelfExposure());
        assertFalse(components.retainsResourceSwing());
        assertTrue(components.retainsControlLeverage());
        assertFalse(components.retainsFutureWarLeverage());

        components.set(0, 10f, 20f, 30f, 40f, 50f);
        components.set(1, 11f, 21f, 31f, 41f, 51f);

        assertEquals(20f, components.selfExposure(0), 1e-5f);
        assertEquals(40f, components.controlLeverage(0), 1e-5f);
        assertThrows(IllegalStateException.class, () -> components.immediateHarm(0));
        assertThrows(IllegalStateException.class, () -> components.resourceSwing(0));
        assertThrows(IllegalStateException.class, () -> components.futureWarLeverage(0));

        components.swap(0, 1);
        assertEquals(21f, components.selfExposure(0), 1e-5f);
        assertEquals(41f, components.controlLeverage(0), 1e-5f);
        assertEquals(20f, components.selfExposure(1), 1e-5f);
        assertEquals(40f, components.controlLeverage(1), 1e-5f);
    }

    @Test
    void openingEvaluatorScalarizationUsesObjectiveOwnedMetrics() {
        DBNationSnapshot attacker = nation(317, 1)
                .unit(MilitaryUnit.SOLDIER, 65_000)
                .unit(MilitaryUnit.TANK, 3_500)
                .unit(MilitaryUnit.AIRCRAFT, 2_200)
                .unit(MilitaryUnit.SHIP, 18)
                .resource(ResourceType.MONEY, 0d)
                .warPolicy(WarPolicy.PIRATE)
                .projectBits(1L << Projects.PIRATE_ECONOMY.ordinal())
                .build();
        DBNationSnapshot defender = nation(318, 2)
                .unit(MilitaryUnit.SOLDIER, 5_500)
                .unit(MilitaryUnit.TANK, 220)
                .unit(MilitaryUnit.AIRCRAFT, 120)
                .unit(MilitaryUnit.SHIP, 3)
                .resource(ResourceType.MONEY, 5_000_000d)
                .warPolicy(WarPolicy.ATTRITION)
                .build();

        DamageObjective objective = new LeverageWeightedDamageObjective();
        CandidateEdgeComponentPolicy allComponents = new CandidateEdgeComponentPolicy(true, true, true, true, true);
        OpeningEvaluator.EvaluatedEdge evaluation = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                objective,
                allComponents
        );

        assertEquals(
                objective.scoreOpening(
                        new OpeningMetricVector(
                                evaluation.immediateHarm(),
                                evaluation.selfExposure(),
                                evaluation.resourceSwing(),
                                evaluation.controlLeverage(),
                                evaluation.futureWarLeverage()
                        ),
                        attacker.teamId()
                ),
                evaluation.score(),
                1e-5f
        );
        assertTrue(evaluation.controlLeverage() > 0f || evaluation.futureWarLeverage() > 0f);
    }

    @Test
    void openingEvaluatorRolloutAccumulatesMoreValueThanSingleStep() {
        DBNationSnapshot attacker = nation(313, 1)
                .unit(MilitaryUnit.SOLDIER, 42_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .build();
        DBNationSnapshot defender = nation(314, 2)
                .unit(MilitaryUnit.SOLDIER, 24_000)
                .unit(MilitaryUnit.TANK, 1_200)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .build();

        OpeningEvaluator.EvaluatedEdge oneStep = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new DamageObjective(),
                CandidateEdgeComponentPolicy.harmExposureOnly(),
                1
        );
        OpeningEvaluator.EvaluatedEdge threeStep = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new DamageObjective(),
                CandidateEdgeComponentPolicy.harmExposureOnly(),
                3
        );

        assertTrue(threeStep.score() >= oneStep.score());
        assertTrue(threeStep.immediateHarm() > oneStep.immediateHarm());
    }

    @Test
    void openingEvaluatorRoutesStrongProbesToDeepOpeningBudget() {
        DBNationSnapshot attacker = nation(413, 1)
                .unit(MilitaryUnit.SOLDIER, 42_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .build();
        DBNationSnapshot defender = nation(414, 2)
                .unit(MilitaryUnit.SOLDIER, 24_000)
                .unit(MilitaryUnit.TANK, 1_200)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .build();

        OpeningEvaluator.ProbeResult probeResult = new OpeningEvaluator.ProbeResult();
        OpeningEvaluator.viabilityProbe(attacker, defender, probeResult);
        assertEquals(3, OpeningEvaluator.actionBudgetForProbe(probeResult.probe()));

        OpeningEvaluator.EvaluatedEdge routed = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new DamageObjective(),
                CandidateEdgeComponentPolicy.harmExposureOnly()
        );
        OpeningEvaluator.EvaluatedEdge threeStep = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new DamageObjective(),
                CandidateEdgeComponentPolicy.harmExposureOnly(),
                3
        );

        assertEquals(threeStep.score(), routed.score(), 1e-5f);
        assertEquals(threeStep.firstAttackTypeId(), routed.firstAttackTypeId());
    }

    @Test
    void openingEvaluatorRoutesWeakAdmittedEdgesToSingleStepBudget() {
        DBNationSnapshot attacker = nation(415, 1)
                .unit(MilitaryUnit.SOLDIER, 120)
                .unit(MilitaryUnit.TANK, 8)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .build();
        DBNationSnapshot defender = nation(416, 2)
                .unit(MilitaryUnit.SOLDIER, 48_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .unit(MilitaryUnit.SHIP, 60)
                .build();

        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.5f, defender.nationId(), 0.5f)
        );

        OpeningEvaluator.ProbeResult probeResult = OpeningEvaluator.viabilityProbe(scenario, 0, 0);
        assertEquals(1, OpeningEvaluator.actionBudgetForProbe(probeResult.probe()));

        AdmissionFloorDamageObjective lowFloorObjective = new AdmissionFloorDamageObjective(new CandidateEdgeAdmissionPolicy(0.0d));
        OpeningEvaluator.EvaluatedEdge routed = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                lowFloorObjective,
                CandidateEdgeComponentPolicy.harmExposureOnly()
        );

        OpeningEvaluator.EvaluatedEdge oneStep = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                lowFloorObjective,
                CandidateEdgeComponentPolicy.harmExposureOnly(),
                1
        );

        assertEquals(oneStep.score(), routed.score(), 1e-5f);
        assertEquals(oneStep.firstAttackTypeId(), routed.firstAttackTypeId());
        assertEquals(oneStep.preferredWarTypeId(), routed.preferredWarTypeId());
    }

    @Test
    void evaluateOpeningRejectsEdgesBelowObjectiveAdmissionFloor() {
        DBNationSnapshot attacker = nation(423, 1)
                .unit(MilitaryUnit.SOLDIER, 120)
                .unit(MilitaryUnit.TANK, 8)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .build();
        DBNationSnapshot defender = nation(424, 2)
                .unit(MilitaryUnit.SOLDIER, 48_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .unit(MilitaryUnit.SHIP, 60)
                .build();

        OpeningEvaluator.EvaluatedEdge rejected = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new DamageObjective(),
                CandidateEdgeComponentPolicy.harmExposureOnly()
        );

        assertFalse(Float.isFinite(rejected.score()));
        assertEquals(-1, rejected.firstAttackTypeId());
        assertEquals(-1, rejected.preferredWarTypeId());
    }

    @Test
    void openingEvaluatorCarriesPreferredAttritionWarType() {
        DBNationSnapshot attacker = nation(315, 1)
                .unit(MilitaryUnit.SOLDIER, 25_000)
                .unit(MilitaryUnit.TANK, 1_500)
                .unit(MilitaryUnit.AIRCRAFT, 1_300)
                .unit(MilitaryUnit.SHIP, 10)
                .build();
        DBNationSnapshot defender = nation(316, 2)
                .unit(MilitaryUnit.SOLDIER, 10_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 350)
                .unit(MilitaryUnit.SHIP, 2)
                .build();

        OpeningEvaluator.EvaluatedEdge evaluation = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new DamageObjective(),
                CandidateEdgeComponentPolicy.harmExposureOnly()
        );

        assertEquals(WarType.ATT.ordinal(), evaluation.preferredWarTypeId());
    }

    @Test
        void lowProbeAdmissionPolicyCanKeepWeakConventionalEdges() {
        DBNationSnapshot attacker = nation(307, 1)
                .unit(MilitaryUnit.SOLDIER, 120)
                .unit(MilitaryUnit.TANK, 8)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .build();
        DBNationSnapshot defender = nation(308, 2)
                .unit(MilitaryUnit.SOLDIER, 48_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .unit(MilitaryUnit.SHIP, 60)
                .build();

        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.5f, defender.nationId(), 0.5f)
        );

        OpeningEvaluator.ProbeResult probeResult = OpeningEvaluator.viabilityProbe(scenario, 0, 0);
        assertTrue(
                probeResult.probe() < CandidateEdgeAdmissionPolicy.defaultPolicy().minimumViabilityProbe(),
                "Fixture should exercise the low-probe admission floor"
        );

        CandidateEdgeTable defaultTable = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new DamageObjective(),
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                defaultTable
        );
        assertEquals(0, defaultTable.edgeCount());

        CandidateEdgeTable specialistTable = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new AdmissionFloorDamageObjective(new CandidateEdgeAdmissionPolicy(0.0d)),
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                specialistTable
        );

        OpeningEvaluator.EvaluatedEdge expected = OpeningEvaluator.evaluateOpening(
                attacker,
                defender,
                new AdmissionFloorDamageObjective(new CandidateEdgeAdmissionPolicy(0.0d)),
                CandidateEdgeComponentPolicy.harmExposureOnly()
        );
                if (expected.firstAttackTypeId() < 0) {
                        assertEquals(0, specialistTable.edgeCount());
                } else {
                        assertEquals(1, specialistTable.edgeCount());
                        assertEquals(expected.preferredWarTypeId(), specialistTable.preferredWarTypeId(0));
                        assertEquals(expected.firstAttackTypeId(), specialistTable.bestAttackTypeId(0));
                        assertTrue(Double.isFinite(specialistTable.scalarScore(0)));
                }
    }

    @Test
    void objectiveAdmissionFloorControlsSpecialistFallback() {
        DBNationSnapshot attacker = nation(309, 1)
                .unit(MilitaryUnit.SOLDIER, 120)
                .unit(MilitaryUnit.TANK, 8)
                .unit(MilitaryUnit.AIRCRAFT, 0)
                .unit(MilitaryUnit.SHIP, 0)
                .unit(MilitaryUnit.MISSILE, 1)
                .projectBits(1L << Projects.MISSILE_LAUNCH_PAD.ordinal())
                .build();
        DBNationSnapshot defender = nation(310, 2)
                .unit(MilitaryUnit.SOLDIER, 48_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .unit(MilitaryUnit.SHIP, 60)
                .build();

        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.5f, defender.nationId(), 0.5f)
        );

        OpeningEvaluator.ProbeResult probeResult = OpeningEvaluator.viabilityProbe(scenario, 0, 0);
        assertTrue(probeResult.probe() < 0.30f, "Fixture should stay below the custom floor");

        CandidateEdgeTable blockedTable = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new AdmissionFloorDamageObjective(new CandidateEdgeAdmissionPolicy(0.30d)),
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                blockedTable
        );

        assertEquals(0, blockedTable.edgeCount(), "Missile presence alone should not bypass the generic probe floor");

        CandidateEdgeTable specialistTable = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new AdmissionFloorDamageObjective(new CandidateEdgeAdmissionPolicy(0.30d, true)),
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                specialistTable
        );

        assertEquals(0, specialistTable.edgeCount(), "Single-step opening evaluation only admits specialist fallback when the specialist action is legal on the opening MAP budget");
    }

    @Test
    void specialistFallbackRequiresProjectLegality() {
        DBNationSnapshot attacker = nation(311, 1)
                .unit(MilitaryUnit.SOLDIER, 120)
                .unit(MilitaryUnit.TANK, 8)
                .unit(MilitaryUnit.MISSILE, 1)
                .build();
        DBNationSnapshot defender = nation(312, 2)
                .unit(MilitaryUnit.SOLDIER, 48_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .unit(MilitaryUnit.SHIP, 60)
                .build();

        ScenarioCompiler compiler = new ScenarioCompiler();
        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.5f, defender.nationId(), 0.5f)
        );

        CandidateEdgeTable specialistTable = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                new SimTuning(ResolutionMode.MOST_LIKELY),
                OverrideSet.EMPTY,
                new AdmissionFloorDamageObjective(CandidateEdgeAdmissionPolicy.lowProbeSpecialists()),
                new int[]{attacker.rawFreeOff()},
                new int[]{defender.rawFreeDef()},
                specialistTable
        );

        assertEquals(0, specialistTable.edgeCount(), "Specialist fallback should require the unlocking project, not just stocked units");
    }

    @Test
        void plannerLocalNationUsesSpecialistCityProfiles() throws Exception {
                DBNationSnapshot attacker = nation(313, 1).build();
        SpecialistCityProfile specialistProfile = new SpecialistCityProfile(2_200d, 220, 95, 15, 6d, 4d);
        DBNationSnapshot defender = nation(314, 2)
                .unit(MilitaryUnit.SOLDIER, 25_000)
                .cityInfra(new double[]{1_800d, 1_200d, 900d})
                .citySpecialistProfiles(new SpecialistCityProfile[]{
                        specialistProfile,
                        SpecialistCityProfile.DEFAULT,
                        SpecialistCityProfile.DEFAULT
                })
                .build();

        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );
        java.lang.reflect.Field nationsByIdField = PlannerLocalConflict.class.getDeclaredField("nationsById");
        nationsByIdField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, Object> nationsById = (Map<Integer, Object>) nationsByIdField.get(conflict);
        Object localDefender = nationsById.get(defender.nationId());

        java.lang.reflect.Method cityMissileDamage = localDefender.getClass().getDeclaredMethod("cityMissileDamage", int.class);
        cityMissileDamage.setAccessible(true);
        java.lang.reflect.Method cityNukeDamage = localDefender.getClass().getDeclaredMethod("cityNukeDamage", int.class);
        cityNukeDamage.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map.Entry<Integer, Integer> actualMissile = (Map.Entry<Integer, Integer>) cityMissileDamage.invoke(localDefender, 0);
        @SuppressWarnings("unchecked")
        Map.Entry<Integer, Integer> actualNuke = (Map.Entry<Integer, Integer>) cityNukeDamage.invoke(localDefender, 0);
        Map.Entry<Integer, Integer> expectedMissile = specialistProfile.missileDamage(defender.cityInfra()[0], project -> false);
        Map.Entry<Integer, Integer> expectedNuke = specialistProfile.nukeDamage(defender.cityInfra()[0], project -> false);

        assertEquals(expectedMissile.getKey(), actualMissile.getKey());
        assertEquals(expectedMissile.getValue(), actualMissile.getValue());
        assertEquals(expectedNuke.getKey(), actualNuke.getKey());
        assertEquals(expectedNuke.getValue(), actualNuke.getValue());
    }

    @Test
    void plannerLocalExecutionRequiresMissileProjectLegality() {
        DBNationSnapshot attacker = nation(315, 1)
                .unit(MilitaryUnit.MISSILE, 1)
                .build();
        DBNationSnapshot defender = nation(316, 2)
                .cityInfra(new double[]{1_800d, 1_200d, 900d})
                .build();

        PlannerLocalConflict conflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );
        PlannerExactValidatorScripts missileOnlyScripts = new PlannerExactValidatorScripts(
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                PlannerExactValidatorScripts.AttackSequenceProfile.CONVENTIONAL,
                0,
                EnumSet.of(AttackType.MISSILE)
        );

        conflict.simulateDeclaredWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                1,
                missileOnlyScripts,
                new DamageObjective(),
                attacker.teamId()
        );

        DBNationSnapshot projectedDefender = conflict.project().snapshotsById().get(defender.nationId());
        assertEquals(defender.cityInfra()[0], projectedDefender.cityInfra()[0]);
    }

    @Test
    void plannerLocalDefeatAppliesSharedVictoryLootPercent() throws Exception {
        DBNationSnapshot attacker = nation(319, 1)
                .resource(ResourceType.MONEY, 100_000d)
                .looterModifiers(1.0d, 1.4d)
                .build();
        DBNationSnapshot defender = nation(320, 2)
                .resource(ResourceType.MONEY, 1_000_000d)
                .lootModifier(1.2d)
                .cityInfra(new double[]{1_000d, 800d})
                .build();
        PlannerProjectedWar activeWar = new PlannerProjectedWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                0,
                WarStatus.ACTIVE,
                6,
                6,
                100,
                0,
                PlannerLocalConflict.ControlOwner.NONE,
                PlannerLocalConflict.ControlOwner.NONE,
                PlannerLocalConflict.ControlOwner.NONE,
                false,
                false
        );
        PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                OverrideSet.EMPTY,
                List.of(attacker, defender),
                List.of(activeWar),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );

        Field warsByIdField = PlannerLocalConflict.class.getDeclaredField("warsById");
        warsByIdField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, Object> warsById = (Map<Integer, Object>) warsByIdField.get(conflict);
        Object localWar = warsById.values().iterator().next();

        Method resolveDefeatIfNeeded = PlannerLocalConflict.class.getDeclaredMethod("resolveDefeatIfNeeded", localWar.getClass());
        resolveDefeatIfNeeded.setAccessible(true);
        resolveDefeatIfNeeded.invoke(conflict, localWar);

        DBNationSnapshot projectedAttacker = conflict.project().snapshotsById().get(attacker.nationId());
        DBNationSnapshot projectedDefender = conflict.project().snapshotsById().get(defender.nationId());
        double expectedLootPercent = WarOutcomeMath.victoryNationLootPercent(1.4d, 1.2d, WarType.ORD, true);
        double expectedTransferred = 1_000_000d * expectedLootPercent;

        assertEquals(100_000d + expectedTransferred, projectedAttacker.resource(ResourceType.MONEY), 1e-9);
        assertEquals(1_000_000d - expectedTransferred, projectedDefender.resource(ResourceType.MONEY), 1e-9);
        assertEquals(980d, projectedDefender.cityInfra()[0], 1e-9);
        assertEquals(784d, projectedDefender.cityInfra()[1], 1e-9);
        assertTrue(conflict.project().activeWars().isEmpty());
    }

    @Test
    void specialistFirstScriptsCanSaveMapsForMissileStrikeWithoutObjectiveScoring() {
        DBNationSnapshot attacker = nation(317, 1)
                .unit(MilitaryUnit.SOLDIER, 120)
                .unit(MilitaryUnit.TANK, 8)
                .unit(MilitaryUnit.MISSILE, 1)
                .projectBits(1L << Projects.MISSILE_LAUNCH_PAD.ordinal())
                .build();
        DBNationSnapshot defender = nation(318, 2)
                .unit(MilitaryUnit.SOLDIER, 48_000)
                .unit(MilitaryUnit.TANK, 2_400)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .unit(MilitaryUnit.SHIP, 60)
                .cityInfra(new double[]{1_800d, 1_200d, 900d})
                .build();

        PlannerExactValidatorScripts conventionalScripts = new PlannerExactValidatorScripts(
                true,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                PlannerExactValidatorScripts.AttackSequenceProfile.CONVENTIONAL,
                0,
                EnumSet.of(AttackType.GROUND, AttackType.MISSILE)
        );
        PlannerLocalConflict conventionalConflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );
        conventionalConflict.simulateDeclaredWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                3,
                conventionalScripts
        );

        PlannerLocalConflict specialistConflict = PlannerLocalConflict.create(
                OverrideSet.EMPTY,
                List.of(attacker),
                List.of(defender),
                new SimTuning(ResolutionMode.MOST_LIKELY)
        );
        specialistConflict.simulateDeclaredWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                3,
                conventionalScripts,
                PlannerCoordinationPolicy.resetWindowSpecialistHold()
        );

        DBNationSnapshot conventionalAttacker = conventionalConflict.project().snapshotsById().get(attacker.nationId());
        DBNationSnapshot conventionalDefender = conventionalConflict.project().snapshotsById().get(defender.nationId());
        DBNationSnapshot specialistAttacker = specialistConflict.project().snapshotsById().get(attacker.nationId());
        DBNationSnapshot specialistDefender = specialistConflict.project().snapshotsById().get(defender.nationId());

        assertEquals(1, conventionalAttacker.unit(MilitaryUnit.MISSILE));
        assertEquals(0, specialistAttacker.unit(MilitaryUnit.MISSILE));
        assertEquals(defender.cityInfra()[0], conventionalDefender.cityInfra()[0], 1e-9);
        assertTrue(specialistDefender.cityInfra()[0] < defender.cityInfra()[0]);
    }

        private static double[] uniformInfra(double value) {
                double[] values = new double[AttackType.values.length];
                java.util.Arrays.fill(values, value);
                return values;
        }

        private static final class RetainedComponentDamageObjective extends DamageObjective {
                private final CandidateEdgeComponentPolicy policy;

                private RetainedComponentDamageObjective(CandidateEdgeComponentPolicy policy) {
                        this.policy = policy;
                }

                @Override
                public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
                        return policy;
                }
        }

        private static final class AdmissionFloorDamageObjective extends DamageObjective {
                private final CandidateEdgeAdmissionPolicy policy;

                private AdmissionFloorDamageObjective(CandidateEdgeAdmissionPolicy policy) {
                        this.policy = policy;
                }

                @Override
                public CandidateEdgeAdmissionPolicy candidateEdgeAdmissionPolicy() {
                        return policy;
                }
        }

        private static final class LeverageWeightedDamageObjective extends DamageObjective {
                @Override
                public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
                        return new CandidateEdgeComponentPolicy(true, true, true, true, true);
                }

                @Override
                public double scoreOpening(OpeningMetricVector metrics, int teamId) {
                        return metrics.immediateHarm()
                                - metrics.selfExposure()
                                + (4d * metrics.controlLeverage())
                                + (3d * metrics.futureWarLeverage())
                                + (0.000001d * metrics.resourceSwing());
                }
        }

        private static DBNationSnapshot.Builder nation(int nationId, int teamId) {
                return DBNationSnapshot.synthetic(nationId)
                                .teamId(teamId)
                                .allianceId(teamId)
                                .score(1_000)
                                .cities(3)
                                .maxOff(3)
                                .nonInfraScoreBase(700)
                                .cityInfra(new double[]{1_200, 1_100, 1_000})
                                .warPolicy(WarPolicy.ATTRITION);
        }

        private static List<Integer> nationIds(List<DBNationSnapshot> snapshots) {
                return snapshots.stream().map(DBNationSnapshot::nationId).toList();
        }
}