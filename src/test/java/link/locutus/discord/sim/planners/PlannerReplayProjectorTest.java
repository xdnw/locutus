package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.Turn1DeclarePolicy;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayTrace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerReplayProjectorTest {
        private static final Map<BlitzReplayTrace, int[]> PARTICIPANT_IDS_BY_TRACE = Collections.synchronizedMap(new IdentityHashMap<>());

    @Test
    void replayUsesPlannerSelectedWarTypeForAssignedWars() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot defender = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 250)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(attacker),
                List.of(defender),
                Map.of(attacker.nationId(), List.of(defender.nationId())),
                Map.of(pairKey(attacker.nationId(), defender.nationId()), WarType.ATT.ordinal()),
                List.of(),
                List.of(),
                BlitzObjective.NET_DAMAGE.objective(),
                SimTuning.defaults(),
                1
        );

        assertEquals(
                WarType.ATT.ordinal(),
                declaredWarTypeOrdinal(trace, attacker.nationId(), defender.nationId())
        );
    }

    @Test
    void defaultPolicyAllowsFreeDefenderCounterAfterInitialOpen() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot hitDefender = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 250)
                .build();
        DBNationSnapshot unhitDefender = nation(203, 2)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(attacker),
                List.of(hitDefender, unhitDefender),
                Map.of(attacker.nationId(), List.of(hitDefender.nationId())),
                Map.of(),
                List.of(unhitDefender),
                List.of(attacker),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults(),
                1
        );

        assertTrue(
                hasDeclaredWar(trace, unhitDefender.nationId(), attacker.nationId()),
                "The default policy should let defender-side nations that remain free after the opening counter on turn 1"
        );
    }

    @Test
    void turn1CounterPolicyAllowsUnhitDefenderCounterOnInitialTurn() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot hitDefender = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 250)
                .build();
        DBNationSnapshot unhitDefender = nation(203, 2)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(attacker),
                List.of(hitDefender, unhitDefender),
                Map.of(attacker.nationId(), List.of(hitDefender.nationId())),
                Map.of(),
                List.of(unhitDefender),
                List.of(attacker),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults().withTurn1DeclarePolicy(Turn1DeclarePolicy.BOTH_FREE),
                1
        );

        assertTrue(
                hasDeclaredWar(trace, unhitDefender.nationId(), attacker.nationId()),
                "The BOTH_FREE policy should allow unhit defender-side nations to counter during the initial turn"
        );
    }

    @Test
    void unhitDefendersCounterAfterInitialBlitzTurn() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot hitDefender = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 250)
                .build();
        DBNationSnapshot unhitDefender = nation(203, 2)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(attacker),
                List.of(hitDefender, unhitDefender),
                Map.of(attacker.nationId(), List.of(hitDefender.nationId())),
                Map.of(),
                List.of(unhitDefender),
                List.of(attacker),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults(),
                2
        );

        assertTrue(
                hasDeclaredWar(trace, unhitDefender.nationId(), attacker.nationId()),
                "An unhit defender-side nation should counter after the initial blitz turn"
        );
    }

    @Test
    void hitDefendersDoNotCreateReciprocalCounterWars() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot hitDefender = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(attacker),
                List.of(hitDefender),
                Map.of(attacker.nationId(), List.of(hitDefender.nationId())),
                Map.of(),
                List.of(hitDefender),
                List.of(attacker),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults(),
                2
        );

        assertFalse(
                hasDeclaredWar(trace, hitDefender.nationId(), attacker.nationId()),
                "A defender already hit by the blitz should not create a reciprocal counter war"
        );
    }

    @Test
    void hitDefendersCanCounterDifferentTargetsAfterInitialTurn() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot otherAttacker = nation(102, 1)
                .unit(MilitaryUnit.AIRCRAFT, 700)
                .build();
        DBNationSnapshot hitDefender = nation(202, 2)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(attacker, otherAttacker),
                List.of(hitDefender),
                Map.of(attacker.nationId(), List.of(hitDefender.nationId())),
                Map.of(),
                List.of(hitDefender),
                List.of(attacker, otherAttacker),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults(),
                2
        );

        assertTrue(
                hasDeclaredWar(trace, hitDefender.nationId(), otherAttacker.nationId()),
                "A defender hit by one attacker should still be able to counter a different legal target after turn 1"
        );
        assertFalse(
                hasDeclaredWar(trace, hitDefender.nationId(), attacker.nationId()),
                "The active same-pair guard should still block reciprocal declarations against the attacker that already hit it"
        );
    }

    @Test
    void autonomousCounterWarsKeepAttackingAfterDeclarationTurn() {
        DBNationSnapshot counterDeclarer = nation(202, 2)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 1_200)
                .build();
        DBNationSnapshot target = nation(101, 1)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(target),
                List.of(counterDeclarer),
                Map.of(),
                Map.of(),
                List.of(counterDeclarer),
                List.of(target),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults().withTurn1DeclarePolicy(Turn1DeclarePolicy.BOTH_FREE),
                8
        );

        int declaredTurn = declaredTurn(trace, counterDeclarer.nationId(), target.nationId());
        assertTrue(declaredTurn >= 0, "Counter war should be declared during the replay");
        assertTrue(
                hasAttacksAfterTurn(trace, declaredTurn, false),
                "Counter wars must keep executing daily attacks after their declaration turn instead of idling outside the assignment map"
        );
    }

    @Test
    void freeCounterDeclarerOutranksSaturatedPeerForLimitedTarget() {
        DBNationSnapshot freeDeclarer = nation(301, 2)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();
        DBNationSnapshot saturatedDeclarer = DBNationSnapshot.synthetic(302)
                .teamId(2)
                .allianceId(2)
                .score(1_000.0)
                .cities(10)
                .nonInfraScoreBase(1_000.0)
                .cityInfra(uniformInfra(10, 1_000.0))
                .maxOff(5)
                .currentOffensiveWars(0)
                .currentDefensiveWars(2)
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();
        DBNationSnapshot scarceTarget = DBNationSnapshot.synthetic(101)
                .teamId(1)
                .allianceId(1)
                .score(1_000.0)
                .cities(10)
                .nonInfraScoreBase(1_000.0)
                .cityInfra(uniformInfra(10, 1_000.0))
                .maxOff(5)
                .currentOffensiveWars(0)
                .currentDefensiveWars(2)
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.AIRCRAFT, 600)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(scarceTarget),
                List.of(freeDeclarer, saturatedDeclarer),
                Map.of(),
                Map.of(),
                List.of(saturatedDeclarer, freeDeclarer),
                List.of(scarceTarget),
                BlitzObjective.CONTROL.objective(),
                SimTuning.defaults().withTurn1DeclarePolicy(Turn1DeclarePolicy.BOTH_FREE),
                1
        );

        assertTrue(
                hasDeclaredWar(trace, freeDeclarer.nationId(), scarceTarget.nationId()),
                "Free counter declarer should win the only available defensive slot before the saturated peer"
        );
        assertFalse(
                hasDeclaredWar(trace, saturatedDeclarer.nationId(), scarceTarget.nationId()),
                "Saturated counter declarer must not consume a defensive slot the free peer can fill"
        );
    }

    @Test
    void counterPlannerDistributesComparableLongHorizonCountersBeforeMaxingOneDeclarer() {
        DBNationSnapshot slotRichDeclarer = nation(301, 2)
                .maxOff(3)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot peerDeclarerOne = nation(302, 2)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot peerDeclarerTwo = nation(303, 2)
                .maxOff(1)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot targetOne = scarceTarget(101);
        DBNationSnapshot targetTwo = scarceTarget(102);
        DBNationSnapshot targetThree = scarceTarget(103);

        BlitzReplayTrace trace = capture(
                List.of(targetOne, targetTwo, targetThree),
                List.of(slotRichDeclarer, peerDeclarerOne, peerDeclarerTwo),
                Map.of(),
                Map.of(),
                List.of(slotRichDeclarer, peerDeclarerOne, peerDeclarerTwo),
                List.of(targetOne, targetTwo, targetThree),
                BlitzObjective.CONTROL.objective(),
                SimTuning.defaults().withTurn1DeclarePolicy(Turn1DeclarePolicy.BOTH_FREE),
                72
        );

        assertEquals(3, firstWaveDeclaredWarCount(trace));
        assertEquals(3, firstWaveDistinctDeclarerCount(trace),
                "Comparable positive-control counter edges should be assigned globally instead of maxing the first slot-rich declarer");
        assertEquals(1, firstWaveDeclaredWarCount(trace, slotRichDeclarer.nationId()),
                "The slot-rich declarer should not monopolize comparable counter targets");
    }

    @Test
    void counterPlannerDoesNotForceUnviableDeclarerForDistribution() {
        DBNationSnapshot viableDeclarer = nation(301, 2)
                .maxOff(3)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
        DBNationSnapshot unviableDeclarer = nation(302, 2)
                .maxOff(1)
                .build();
        DBNationSnapshot targetOne = scarceTarget(101);
        DBNationSnapshot targetTwo = scarceTarget(102);
        DBNationSnapshot targetThree = scarceTarget(103);

        BlitzReplayTrace trace = capture(
                List.of(targetOne, targetTwo, targetThree),
                List.of(viableDeclarer, unviableDeclarer),
                Map.of(),
                Map.of(),
                List.of(viableDeclarer, unviableDeclarer),
                List.of(targetOne, targetTwo, targetThree),
                BlitzObjective.CONTROL.objective(),
                SimTuning.defaults().withTurn1DeclarePolicy(Turn1DeclarePolicy.BOTH_FREE),
                72
        );

        assertEquals(3, firstWaveDeclaredWarCount(trace, viableDeclarer.nationId()));
        assertEquals(0, firstWaveDeclaredWarCount(trace, unviableDeclarer.nationId()),
                "Distribution pressure must not manufacture declarations for rejected counter edges");
    }

    @Test
    void slotOnlyExistingWarsReleaseSlotsWithoutProjectedWarState() {
        DBNationSnapshot nation = nation(101, 1)
                .currentDefensiveWars(3)
                .slotOnlyDefensiveWarReleaseTurns(new int[]{1})
                .build();

        PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                OverrideSet.EMPTY,
                List.of(nation),
                List.of(),
                0,
                SimTuning.defaults(),
                PlannerTransitionSemantics.NONE
        );

        assertEquals(0, conflict.project().snapshotsById().get(101).rawFreeDef());

        conflict.applyReplayTurn(Map.of(), true);

        PlannerProjectionResult projection = conflict.project();
        assertEquals(1, projection.snapshotsById().get(101).rawFreeDef());
        assertTrue(projection.activeWars().isEmpty());
    }

    @Test
    void replayCaptureDoesNotCallProjectionExportForNationDiffs() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                .build();
        DBNationSnapshot defender = nation(202, 2)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();

        PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                OverrideSet.EMPTY,
                List.of(attacker, defender),
                List.of(),
                0,
                SimTuning.defaults(),
                PlannerTransitionSemantics.REPLAY
        );

        BlitzReplayTrace trace = PlannerReplayProjector.capture(
                conflict,
                new int[]{attacker.nationId()},
                Map.of(attacker.nationId(), List.of(defender.nationId())),
                List.of(),
                List.of(),
                BlitzObjective.DAMAGE.objective(),
                2
        );

        assertEquals(0, conflict.projectionExportCount());
        assertTrue(turnCount(trace) > 0);
        assertTrue(turn(trace, 0).changedNationIndexes().length > 0);
    }

    @Test
    void replayMaterializesProjectedDailyRebuysAcrossTurns() {
        DBNationSnapshot nation = nation(101, 1)
                .unit(MilitaryUnit.SOLDIER, 0)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(nation),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults(),
                2
        );

        assertTrue(maxProjectedUnits(trace, List.of(nation), List.of(), nation.nationId(), MilitaryUnit.SOLDIER) > 0,
                "Replay should queue and materialize projected daily rebuys instead of leaving depleted nations dead for the whole trace");
    }

    @Test
    void compactTraceUsesNationIndexesAndSummaryLanes() {
        DBNationSnapshot attacker = nation(101, 1)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                .build();
        DBNationSnapshot defender = nation(202, 2)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .build();

        BlitzReplayTrace trace = capture(
                List.of(attacker),
                List.of(defender),
                Map.of(attacker.nationId(), List.of(defender.nationId())),
                Map.of(),
                List.of(),
                List.of(),
                BlitzObjective.DAMAGE.objective(),
                SimTuning.defaults(),
                1
        );

        assertEquals(0, trace.startTurn());
        assertEquals(1, turnCount(trace));
        TurnSlice turn = turn(trace, 0);
        assertEquals(2, turn.declaredWarPairs().length);
        assertEquals(3, turn.declaredWarLanes().length);
        assertEquals(1, turn.summaryScalarLanes()[0]);
        assertTrue(turn.changedNationIndexes().length > 0);
        assertTrue(turn.changedNationIndexes()[0] >= 0);
        assertEquals(2 * WarType.values.length, turn.summaryWarTypeCounts().length);
        assertEquals(2 * AttackType.values().length * SuccessType.values.length, turn.summaryAttackOutcomeCounts().length);
        assertTrue(sum(turn.summaryAttackOutcomeCounts()) > 0 || sum(turn.summaryUnitLossCounts()) > 0);
    }

    private static BlitzReplayTrace capture(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Map<Integer, List<Integer>> assignment,
            Map<Long, Integer> warTypeOrdinalsByPair,
            List<DBNationSnapshot> counterDeclarers,
            List<DBNationSnapshot> counterTargets,
            link.locutus.discord.sim.TeamScoreObjective counterObjective,
            SimTuning tuning,
            int horizonTurns
    ) {
        List<DBNationSnapshot> nations = new ArrayList<>(attackers.size() + defenders.size());
        nations.addAll(attackers);
        nations.addAll(defenders);
        BlitzReplayTrace trace = PlannerReplayProjector.capture(
                tuning,
                OverrideSet.EMPTY,
                nations,
                ids(attackers),
                ids(defenders),
                assignment,
                warTypeOrdinalsByPair,
                counterDeclarers,
                counterTargets,
                counterObjective,
                0,
                horizonTurns
        );
        PARTICIPANT_IDS_BY_TRACE.put(trace, ids(nations));
        return trace;
    }

    private static int[] ids(List<DBNationSnapshot> snapshots) {
        return snapshots.stream().mapToInt(DBNationSnapshot::nationId).sorted().toArray();
    }

    private static boolean hasDeclaredWar(BlitzReplayTrace trace, int declarerNationId, int targetNationId) {
        return declaredTurn(trace, declarerNationId, targetNationId) >= 0;
    }

    private static int declaredWarTypeOrdinal(BlitzReplayTrace trace, int declarerNationId, int targetNationId) {
                int[] participantIds = participantIds(trace);
                for (int turnIndex = 0; turnIndex < turnCount(trace); turnIndex++) {
                        TurnSlice turn = turn(trace, turnIndex);
                        for (int offset = 0; offset + 1 < turn.declaredWarPairs().length; offset += 2) {
                                int declarerIndex = turn.declaredWarPairs()[offset];
                                int targetIndex = turn.declaredWarPairs()[offset + 1];
                                if (participantIds[declarerIndex] == declarerNationId && participantIds[targetIndex] == targetNationId) {
                                        return turn.declaredWarLanes()[(offset / 2) * 3 + 2] & 0x3F;
                }
            }
        }
        return -1;
    }

    private static int firstWaveDeclaredWarCount(BlitzReplayTrace trace) {
                TurnSlice turn = firstDeclaredTurn(trace);
                return turn == null ? 0 : turn.declaredWarPairs().length / 2;
    }

    private static int firstWaveDeclaredWarCount(BlitzReplayTrace trace, int declarerNationId) {
                TurnSlice turn = firstDeclaredTurn(trace);
        if (turn == null) {
            return 0;
        }
                int[] participantIds = participantIds(trace);
        int count = 0;
                for (int offset = 0; offset + 1 < turn.declaredWarPairs().length; offset += 2) {
                        if (participantIds[turn.declaredWarPairs()[offset]] == declarerNationId) {
                count++;
            }
        }
        return count;
    }

    private static int firstWaveDistinctDeclarerCount(BlitzReplayTrace trace) {
                TurnSlice turn = firstDeclaredTurn(trace);
        if (turn == null) {
            return 0;
        }
                int[] participantIds = participantIds(trace);
        Set<Integer> declarers = new LinkedHashSet<>();
                for (int offset = 0; offset + 1 < turn.declaredWarPairs().length; offset += 2) {
                        declarers.add(participantIds[turn.declaredWarPairs()[offset]]);
        }
        return declarers.size();
    }

        private static TurnSlice firstDeclaredTurn(BlitzReplayTrace trace) {
                for (int turnIndex = 0; turnIndex < turnCount(trace); turnIndex++) {
                        TurnSlice turn = turn(trace, turnIndex);
                        if (turn.declaredWarPairs().length > 0) {
                return turn;
            }
        }
        return null;
    }

    private static int declaredTurn(BlitzReplayTrace trace, int declarerNationId, int targetNationId) {
                int[] participantIds = participantIds(trace);
                for (int turnIndex = 0; turnIndex < turnCount(trace); turnIndex++) {
                        TurnSlice turn = turn(trace, turnIndex);
                        for (int offset = 0; offset + 1 < turn.declaredWarPairs().length; offset += 2) {
                                int declarerIndex = turn.declaredWarPairs()[offset];
                                int targetIndex = turn.declaredWarPairs()[offset + 1];
                                if (participantIds[declarerIndex] == declarerNationId && participantIds[targetIndex] == targetNationId) {
                                        return turn.absoluteTurn();
                }
            }
        }
        return -1;
    }

    private static boolean hasAttacksAfterTurn(BlitzReplayTrace trace, int turn, boolean attackerSide) {
        int sideOffset = attackerSide ? 0 : AttackType.values().length * SuccessType.values.length;
        int span = AttackType.values().length * SuccessType.values.length;
                for (int turnIndex = 0; turnIndex < turnCount(trace); turnIndex++) {
                        TurnSlice delta = turn(trace, turnIndex);
                        if (delta.absoluteTurn() <= turn) {
                continue;
            }
            int[] counts = delta.summaryAttackOutcomeCounts();
            for (int i = 0; i < span; i++) {
                if (counts[sideOffset + i] > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int maxProjectedUnits(
            BlitzReplayTrace trace,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            int nationId,
            MilitaryUnit unit
    ) {
        List<DBNationSnapshot> combined = new ArrayList<>(attackers.size() + defenders.size());
        combined.addAll(attackers);
        combined.addAll(defenders);
        combined.sort(java.util.Comparator.comparingInt(DBNationSnapshot::nationId));
        int nationIndex = -1;
        int current = 0;
        for (int i = 0; i < combined.size(); i++) {
            if (combined.get(i).nationId() == nationId) {
                nationIndex = i;
                                current = combined.get(i).unit(unit);
                break;
            }
        }
        int max = current;
                for (int turnIndex = 0; turnIndex < turnCount(trace); turnIndex++) {
                        TurnSlice delta = turn(trace, turnIndex);
            int laneOffset = 0;
            for (int i = 0; i < delta.changedNationIndexes().length; i++) {
                int changedIndex = delta.changedNationIndexes()[i];
                int mask = delta.changedNationMasks()[i];
                if ((mask & PlannerReplayProjector.NATION_MASK_AVG_INFRA_CENTS) != 0) {
                    laneOffset++;
                }
                if ((mask & PlannerReplayProjector.NATION_MASK_UNIT_COUNTS) != 0) {
                    if (changedIndex == nationIndex) {
                        current = delta.changedNationLanes()[laneOffset + unit.ordinal()];
                        max = Math.max(max, current);
                    }
                    laneOffset += MilitaryUnit.values.length;
                }
            }
        }
        return max;
    }

    private static int turnCount(BlitzReplayTrace trace) {
        return trace.turnMetaLanes().length / PlannerReplayProjector.TURN_META_BLOCK_SIZE;
    }

    private static TurnSlice turn(BlitzReplayTrace trace, int turnIndex) {
        int metaOffset = turnIndex * PlannerReplayProjector.TURN_META_BLOCK_SIZE;
        int nextMetaOffset = metaOffset + PlannerReplayProjector.TURN_META_BLOCK_SIZE;
        int changedNationEntryStart = trace.turnMetaLanes()[metaOffset];
        int changedNationLaneStart = trace.turnMetaLanes()[metaOffset + 1];
        int changedWarEntryStart = trace.turnMetaLanes()[metaOffset + 2];
        int changedWarLaneStart = trace.turnMetaLanes()[metaOffset + 3];
        int declaredWarPairStart = trace.turnMetaLanes()[metaOffset + 4];
        int declaredWarLaneStart = trace.turnMetaLanes()[metaOffset + 5];
        int concludedWarLaneStart = trace.turnMetaLanes()[metaOffset + 6];
        int summaryScalarLaneStart = trace.turnMetaLanes()[metaOffset + 7];
        int summaryWarTypeLaneStart = trace.turnMetaLanes()[metaOffset + 8];
        int summaryAttackOutcomeLaneStart = trace.turnMetaLanes()[metaOffset + 9];
        int summaryUnitLossLaneStart = trace.turnMetaLanes()[metaOffset + 10];
        int summaryInfraLossLaneStart = trace.turnMetaLanes()[metaOffset + 11];

        int changedNationEntryEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset]
                : trace.changedNationIndexes().length;
        int changedNationLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 1]
                : trace.changedNationLanes().length;
        int changedWarEntryEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 2]
                : trace.changedWarIndexes().length;
        int changedWarLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 3]
                : trace.changedWarLanes().length;
        int declaredWarPairEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 4]
                : trace.declaredWarPairs().length;
        int declaredWarLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 5]
                : trace.declaredWarLanes().length;
        int concludedWarLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 6]
                : trace.concludedWarLanes().length;
        int summaryScalarLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 7]
                : trace.summaryScalarLanes().length;
        int summaryWarTypeLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 8]
                : trace.summaryWarTypeCounts().length;
        int summaryAttackOutcomeLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 9]
                : trace.summaryAttackOutcomeCounts().length;
        int summaryUnitLossLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 10]
                : trace.summaryUnitLossCounts().length;
        int summaryInfraLossLaneEnd = turnIndex + 1 < turnCount(trace)
                ? trace.turnMetaLanes()[nextMetaOffset + 11]
                : trace.summaryInfraLossCents().length;

        return new TurnSlice(
                trace.startTurn() + turnIndex + 1,
                java.util.Arrays.copyOfRange(trace.changedNationIndexes(), changedNationEntryStart, changedNationEntryEnd),
                java.util.Arrays.copyOfRange(trace.changedNationMasks(), changedNationEntryStart, changedNationEntryEnd),
                java.util.Arrays.copyOfRange(trace.changedNationLanes(), changedNationLaneStart, changedNationLaneEnd),
                java.util.Arrays.copyOfRange(trace.changedWarIndexes(), changedWarEntryStart, changedWarEntryEnd),
                java.util.Arrays.copyOfRange(trace.changedWarMasks(), changedWarEntryStart, changedWarEntryEnd),
                java.util.Arrays.copyOfRange(trace.changedWarLanes(), changedWarLaneStart, changedWarLaneEnd),
                java.util.Arrays.copyOfRange(trace.declaredWarPairs(), declaredWarPairStart, declaredWarPairEnd),
                java.util.Arrays.copyOfRange(trace.declaredWarLanes(), declaredWarLaneStart, declaredWarLaneEnd),
                java.util.Arrays.copyOfRange(trace.concludedWarLanes(), concludedWarLaneStart, concludedWarLaneEnd),
                java.util.Arrays.copyOfRange(trace.summaryScalarLanes(), summaryScalarLaneStart, summaryScalarLaneEnd),
                java.util.Arrays.copyOfRange(trace.summaryWarTypeCounts(), summaryWarTypeLaneStart, summaryWarTypeLaneEnd),
                java.util.Arrays.copyOfRange(trace.summaryAttackOutcomeCounts(), summaryAttackOutcomeLaneStart, summaryAttackOutcomeLaneEnd),
                java.util.Arrays.copyOfRange(trace.summaryUnitLossCounts(), summaryUnitLossLaneStart, summaryUnitLossLaneEnd),
                java.util.Arrays.copyOfRange(trace.summaryInfraLossCents(), summaryInfraLossLaneStart, summaryInfraLossLaneEnd)
        );
    }

    private static int[] participantIds(BlitzReplayTrace trace) {
        int[] participantIds = PARTICIPANT_IDS_BY_TRACE.get(trace);
        if (participantIds == null) {
            throw new AssertionError("Missing participant ids for replay trace");
        }
        return participantIds;
    }

    private record TurnSlice(
            int absoluteTurn,
            int[] changedNationIndexes,
            int[] changedNationMasks,
            int[] changedNationLanes,
            int[] changedWarIndexes,
            int[] changedWarMasks,
            int[] changedWarLanes,
            int[] declaredWarPairs,
            int[] declaredWarLanes,
            int[] concludedWarLanes,
            int[] summaryScalarLanes,
            int[] summaryWarTypeCounts,
            int[] summaryAttackOutcomeCounts,
            int[] summaryUnitLossCounts,
            int[] summaryInfraLossCents
    ) {
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static long pairKey(int declarerNationId, int targetNationId) {
        return ((long) declarerNationId << 32) ^ (targetNationId & 0xffffffffL);
    }

    private static DBNationSnapshot.Builder nation(int nationId, int teamId) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .score(1_000.0)
                .cities(10)
                .nonInfraScoreBase(1_000.0)
                .cityInfra(uniformInfra(10, 1_000.0))
                .maxOff(5)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .warPolicy(WarPolicy.ATTRITION);
    }

    private static DBNationSnapshot scarceTarget(int nationId) {
        return nation(nationId, 1)
                .currentDefensiveWars(2)
                .unit(MilitaryUnit.SOLDIER, 20_000)
                .unit(MilitaryUnit.TANK, 2_000)
                .unit(MilitaryUnit.AIRCRAFT, 900)
                .build();
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] values = new double[cities];
        java.util.Arrays.fill(values, infra);
        return values;
    }
}
