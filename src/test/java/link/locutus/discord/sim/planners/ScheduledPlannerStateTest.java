package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.SimTuning;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledPlannerStateTest {
    @Test
    void appliesUnitOverridesOnceAtSeedAndCarriesMutatedCountsForward() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(1)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 100)
                .unit(MilitaryUnit.TANK, 10)
                .unit(MilitaryUnit.AIRCRAFT, 30)
                .unit(MilitaryUnit.SHIP, 3)
                .build();
        DBNationSnapshot defenderOne = DBNationSnapshot.synthetic(2)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 8_000)
                .unit(MilitaryUnit.TANK, 150)
                .unit(MilitaryUnit.AIRCRAFT, 300)
                .unit(MilitaryUnit.SHIP, 2)
                .build();
        DBNationSnapshot defenderTwo = DBNationSnapshot.synthetic(3)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 7_000)
                .unit(MilitaryUnit.TANK, 120)
                .unit(MilitaryUnit.AIRCRAFT, 250)
                .unit(MilitaryUnit.SHIP, 2)
                .build();

        OverrideSet overrides = OverrideSet.builder()
                .units(attacker.nationId(), Map.of(
                        MilitaryUnit.SOLDIER, 12_000,
                        MilitaryUnit.TANK, 400,
                        MilitaryUnit.AIRCRAFT, 800,
                        MilitaryUnit.SHIP, 6
                ))
                .build();

        ScheduledPlannerState state = ScheduledPlannerState.seed(overrides, List.of(attacker, defenderOne, defenderTwo));

        DBNationSnapshot seededAttacker = state.snapshotsFor(List.of(attacker.nationId())).get(0);
        assertEquals(12_000, seededAttacker.unit(MilitaryUnit.SOLDIER));

        state = state.advance(SimTuning.defaults(), Map.of(attacker.nationId(), List.of(defenderOne.nationId())), 1);
        int soldiersAfterFirstBucket = state.snapshotsFor(List.of(attacker.nationId())).get(0).unit(MilitaryUnit.SOLDIER);

        state = state.advance(SimTuning.defaults(), Map.of(attacker.nationId(), List.of(defenderTwo.nationId())), 1);
        DBNationSnapshot attackerAfterSecondBucket = state.snapshotsFor(List.of(attacker.nationId())).get(0);

        assertTrue(attackerAfterSecondBucket.unit(MilitaryUnit.SOLDIER) <= soldiersAfterFirstBucket);
        assertEquals(2, attackerAfterSecondBucket.currentOffensiveWars());
    }

    @Test
    void carriedWarStateAdvancesThroughNextBucketViaSharedLocalConflict() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(11)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 12_000)
                .unit(MilitaryUnit.TANK, 400)
                .unit(MilitaryUnit.AIRCRAFT, 800)
                .unit(MilitaryUnit.SHIP, 6)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(12)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_200, 1_100, 1_000})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 200)
                .unit(MilitaryUnit.AIRCRAFT, 500)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

        ScheduledPlannerState state = ScheduledPlannerState.seed(OverrideSet.EMPTY, List.of(attacker, defender));
        state = state.advance(SimTuning.defaults(), Map.of(attacker.nationId(), List.of(defender.nationId())), 1);

        PlannerProjectedWar firstBucketWar = onlyProjectedWar(state);
        assertEquals(WarType.ORD, firstBucketWar.warType());
        assertEquals(attacker.nationId(), firstBucketWar.attackerNationId());
        assertEquals(defender.nationId(), firstBucketWar.defenderNationId());
        assertEquals(0, firstBucketWar.startTurn());
        assertEquals(1, state.snapshotsFor(List.of(attacker.nationId())).get(0).currentOffensiveWars());

        state = state.advance(SimTuning.defaults(), Map.of(), 1);

        PlannerProjectedWar carriedWar = onlyProjectedWar(state);
        assertEquals(firstBucketWar.warType(), carriedWar.warType());
        assertEquals(firstBucketWar.startTurn(), carriedWar.startTurn());
        assertEquals(firstBucketWar.remainingTurns(0) - 1, carriedWar.remainingTurns(state.currentTurn()));
        assertEquals(Math.min(12, firstBucketWar.attackerMaps() + 1), carriedWar.attackerMaps());
        assertEquals(Math.min(12, firstBucketWar.defenderMaps() + 1), carriedWar.defenderMaps());
        assertEquals(firstBucketWar.attackerResistance(), carriedWar.attackerResistance());
        assertEquals(firstBucketWar.defenderResistance(), carriedWar.defenderResistance());
        assertEquals(firstBucketWar.groundControlOwner(), carriedWar.groundControlOwner());
        assertEquals(firstBucketWar.airSuperiorityOwner(), carriedWar.airSuperiorityOwner());
        assertEquals(firstBucketWar.blockadeOwner(), carriedWar.blockadeOwner());
        assertEquals(firstBucketWar.attackerFortified(), carriedWar.attackerFortified());
        assertEquals(firstBucketWar.defenderFortified(), carriedWar.defenderFortified());
        assertEquals(1, state.snapshotsFor(List.of(attacker.nationId())).get(0).currentOffensiveWars());
    }

    @Test
    void optionalPolicyCooldownAndPendingBuysAdvanceWhenEnabled() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(21)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .policyCooldownTurnsRemaining(3)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .pendingBuyNextTurn(MilitaryUnit.SOLDIER, 250)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(22)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .build();

        ScheduledPlannerState state = ScheduledPlannerState.seed(OverrideSet.EMPTY, List.of(attacker, defender));
        state = state.advance(
                SimTuning.defaults(),
                Map.of(),
                1,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );

        DBNationSnapshot projected = state.snapshotsFor(List.of(attacker.nationId())).get(0);
        assertEquals(2, projected.policyCooldownTurnsRemaining());
        assertEquals(1_250, projected.unit(MilitaryUnit.SOLDIER));
        assertEquals(0, projected.pendingBuysNextTurn(MilitaryUnit.SOLDIER));
    }

    @Test
    void pendingBuysDoNotAffectScoreBeforeMaterialization() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(23)
                .teamId(1)
                .allianceId(10)
                .score(0)
                .maxOff(3)
                .nonInfraScoreBase(0)
                .cityInfra(new double[0])
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .pendingBuyNextTurn(MilitaryUnit.SOLDIER, 500)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(24)
                .teamId(2)
                .allianceId(20)
                .score(0)
                .maxOff(3)
                .nonInfraScoreBase(0)
                .cityInfra(new double[0])
                .warPolicy(WarPolicy.ATTRITION)
                .build();

        ScheduledPlannerState state = ScheduledPlannerState.seed(OverrideSet.EMPTY, List.of(attacker, defender));
        state = state.advance(
                SimTuning.defaults(),
                Map.of(),
                0,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );

        DBNationSnapshot projected = state.snapshotsFor(List.of(attacker.nationId())).get(0);
        assertEquals(MilitaryUnit.SOLDIER.getScore(1_000), projected.score());
        assertEquals(500, projected.pendingBuysNextTurn(MilitaryUnit.SOLDIER));
    }

    @Test
    void unitBuyUsageCarriesAcrossBucketsAndResetsOnDerivedResetBoundary() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(25)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .resetHourUtc((byte) 10)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .unitBoughtToday(MilitaryUnit.SOLDIER, 300)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(26)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .build();

        ScheduledPlannerState state = ScheduledPlannerState.seed(OverrideSet.EMPTY, List.of(attacker, defender));
        state = state.advance(
                SimTuning.defaults(),
                Map.of(),
                1,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );

        DBNationSnapshot afterOneTurn = state.snapshotsFor(List.of(attacker.nationId())).get(0);
        assertEquals(300, afterOneTurn.unitsBoughtToday(MilitaryUnit.SOLDIER));

        state = state.advance(
                SimTuning.defaults(),
                Map.of(),
                4,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );

        DBNationSnapshot afterResetBoundary = state.snapshotsFor(List.of(attacker.nationId())).get(0);
        assertEquals(0, afterResetBoundary.unitsBoughtToday(MilitaryUnit.SOLDIER));
    }

    @Test
    void seededCurrentTurnControlsResetBoundaryForCarriedBuyUsage() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(27)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .resetHourUtc((byte) 10)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .unitBoughtToday(MilitaryUnit.SOLDIER, 300)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(28)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 1_000)
                .build();

        ScheduledPlannerState state = ScheduledPlannerState.seed(
                OverrideSet.EMPTY,
                List.of(attacker, defender),
                List.of(),
                3
        );
        state = state.advance(
                SimTuning.defaults(),
                Map.of(),
                2,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );

        DBNationSnapshot afterCrossingReset = state.snapshotsFor(List.of(attacker.nationId())).get(0);
        assertEquals(0, afterCrossingReset.unitsBoughtToday(MilitaryUnit.SOLDIER));
    }

    @Test
        void optionalPeaceOfferStatusCarriesWhenEnabled() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(31)
                .teamId(1)
                .allianceId(10)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(32)
                .teamId(2)
                .allianceId(20)
                .score(1_000)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_100, 1_000, 900})
                .warPolicy(WarPolicy.ATTRITION)
                .build();

        PlannerProjectedWar activeWar = new PlannerProjectedWar(
                attacker.nationId(),
                defender.nationId(),
                WarType.ORD,
                0,
                WarStatus.ATTACKER_OFFERED_PEACE,
                6,
                6,
                100,
                100,
                PlannerLocalConflict.ControlOwner.NONE,
                PlannerLocalConflict.ControlOwner.NONE,
                PlannerLocalConflict.ControlOwner.NONE,
                false,
                false
        );

        ScheduledPlannerState state = ScheduledPlannerState.seed(
                OverrideSet.EMPTY,
                List.of(attacker, defender),
                List.of(activeWar)
        );
        state = state.advance(
                SimTuning.defaults(),
                Map.of(),
                1,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );

        PlannerProjectedWar carried = onlyProjectedWar(state);
        assertEquals(WarStatus.ATTACKER_OFFERED_PEACE, carried.status());

        state = state.advance(
                SimTuning.defaults(),
                Map.of(),
                1,
                PlannerTransitionSemantics.ALL_OPTIONAL
        );

        PlannerProjectedWar carriedAgain = onlyProjectedWar(state);
                assertEquals(WarStatus.ATTACKER_OFFERED_PEACE, carriedAgain.status());
    }

    @Test
    void carriesSparseTouchedCityInfraOverlaysAcrossBuckets() {
        DBNationSnapshot attacker = DBNationSnapshot.synthetic(41)
                .teamId(1)
                .allianceId(10)
                .score(1_200)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_600, 1_400, 1_200})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 14_000)
                .unit(MilitaryUnit.TANK, 500)
                .unit(MilitaryUnit.AIRCRAFT, 1_000)
                .unit(MilitaryUnit.SHIP, 6)
                .build();
        DBNationSnapshot defender = DBNationSnapshot.synthetic(42)
                .teamId(2)
                .allianceId(20)
                .score(1_100)
                .maxOff(3)
                .nonInfraScoreBase(700)
                .cityInfra(new double[]{1_700, 1_500, 1_300})
                .warPolicy(WarPolicy.ATTRITION)
                .unit(MilitaryUnit.SOLDIER, 9_000)
                .unit(MilitaryUnit.TANK, 300)
                .unit(MilitaryUnit.AIRCRAFT, 650)
                .unit(MilitaryUnit.SHIP, 4)
                .build();

        ScheduledPlannerState state = ScheduledPlannerState.seed(OverrideSet.EMPTY, List.of(attacker, defender));

        state = state.advance(
                SimTuning.defaults(),
                Map.of(attacker.nationId(), List.of(defender.nationId())),
                1
        );

        DBNationSnapshot defenderAfterFirstBucket = state.snapshotsFor(List.of(defender.nationId())).get(0);
        PlannerCityInfraOverlay firstOverlay = state.cityInfraOverlaysByNation().get(defender.nationId());
        assertNotNull(firstOverlay);
        assertFalse(firstOverlay.isEmpty());
        assertFalse(state.cityInfraOverlaysByNation().containsKey(attacker.nationId()));

        double[] defenderCityInfra = defenderAfterFirstBucket.cityInfra();
        for (int i = 0; i < firstOverlay.size(); i++) {
            assertEquals(firstOverlay.cityInfraValueAt(i), defenderCityInfra[firstOverlay.cityIndexAt(i)]);
        }

        state = state.advance(SimTuning.defaults(), Map.of(), 1);

        PlannerCityInfraOverlay carriedOverlay = state.cityInfraOverlaysByNation().get(defender.nationId());
        assertNotNull(carriedOverlay);
        assertEquals(firstOverlay, carriedOverlay);

        DBNationSnapshot defenderAfterSecondBucket = state.snapshotsFor(List.of(defender.nationId())).get(0);
        assertArrayEquals(defenderAfterFirstBucket.cityInfra(), defenderAfterSecondBucket.cityInfra());
    }

    private static PlannerProjectedWar onlyProjectedWar(ScheduledPlannerState state) {
        assertEquals(1, state.activePlannedWars().size());
        return state.activePlannedWars().values().iterator().next();
    }
}
