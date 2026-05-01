package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.handlers.ActiveWarHandler;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet.ActiveOverride;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.BlitzWarningCode;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayTrace;
import link.locutus.discord.web.commands.binding.value_types.BlitzSideMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzAssignedWarSource;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class SimEndpointsContractTest {
    @Test
    void simEndpointsAreRegisteredInStandalonePageHandler() {
        PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
        CommandGroup api = (CommandGroup) handler.getCommands().get("api");

        CommandCallable simBlitz = api.get("simBlitz");
        CommandCallable blitzPlan = api.get("blitzPlan");
        CommandCallable simAdhoc = api.get("simAdhoc");
        CommandCallable simSchedule = api.get("simSchedule");

        assertNotNull(simBlitz);
        assertNotNull(blitzPlan);
        assertNotNull(simAdhoc);
        assertNotNull(simSchedule);
    }

    @Test
    void blitzPlanPreviewRejectsStructuralInvalidRequests() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            DBNation defender = nation(202, "Defender", 1_000d);

            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(), List.of(), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(), List.of(attacker), List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(99, BlitzRebuyMode.FULL_REBUYS.ordinal(), false, false), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(BlitzSideMode.ATTACKERS_ONLY.ordinal(), 99, false, false), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, requestWithObjective(999), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, requestWithTurn1DeclarePolicy(999), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, requestWithHorizon(721), List.of(attacker), List.of(defender)));
                assertEquals(720,
                    SimEndpoints.previewBlitzPlan(warDb, requestWithHorizon(720), List.of(attacker), List.of(defender)).horizonTurns());
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(true, false), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(false, true), List.of(attacker), List.of(defender)));
        });
    }

    @Test
    void blitzPlanPreviewRejectsOverlappingResolvedPopulationsWithNamedError() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(), List.of(attacker), List.of(attacker)));

            assertEquals("Nations cannot be on both blitz sides: Attacker (#101)", error.getMessage());
        });
    }

    @Test
    void blitzPlanPreviewRejectsUnknownEditTargetsAndBadOrdinals() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            DBNation defender = nation(202, "Defender", 1_000d);
            BlitzDraftEdit unknownEdit = edit(303, null, null);
            BlitzDraftEdit badUnitArray = edit(101, new int[]{1, 2}, null);
            BlitzDraftEdit badPolicy = new BlitzDraftEdit(101, null, 999, null, null, null, 0L, 0L, 0, 0, null, null, null);
            BlitzPlannedWar badWarType = new BlitzPlannedWar(101, 202, 999, true);

            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(new BlitzDraftEdit[]{unknownEdit}, new BlitzPlannedWar[0]), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(new BlitzDraftEdit[]{badUnitArray}, new BlitzPlannedWar[0]), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(new BlitzDraftEdit[]{badPolicy}, new BlitzPlannedWar[0]), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(new BlitzDraftEdit[0], new BlitzPlannedWar[]{badWarType}), List.of(attacker), List.of(defender)));
        });
    }

    @Test
    void blitzPlanPreviewHydratesEditedNationRowsAndLegalEdges() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 900d);
            attacker.setCities(10);
            attacker.setAircraft(50);
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setCities(10);
            defender.setAircraft(10_000);
            int[] units = zeros();
            units[MilitaryUnit.AIRCRAFT.ordinal()] = 1234;
            int[] buys = zeros();
            buys[MilitaryUnit.AIRCRAFT.ordinal()] = 15;
            BlitzDraftEdit edit = new BlitzDraftEdit(
                    101,
                    true,
                    WarPolicy.PIRATE.ordinal(),
                    155_000,
                    units,
                    buys,
                    1L,
                    0L,
                    2,
                    0,
                        4,
                        null,
                        null
            );

            BlitzPlanResponse response = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(new BlitzDraftEdit[]{edit}, new BlitzPlannedWar[0]),
                    List.of(attacker),
                    List.of(defender)
            );

            assertEquals(2, response.plannerNationCount());
            assertArrayEquals(new int[]{101, 202}, response.participantIds());
            BlitzNationRow attackerRow = row(response, 101);
            assertEquals(1234, attackerRow.unitsByMilitaryUnitOrdinal()[MilitaryUnit.AIRCRAFT.ordinal()]);
            assertEquals(15, attackerRow.unitsBoughtTodayByMilitaryUnitOrdinal()[MilitaryUnit.AIRCRAFT.ordinal()]);
            assertEquals(155_000, attackerRow.avgInfraCents());
            assertEquals(WarPolicy.PIRATE.ordinal(), attackerRow.policyOrdinal());
            assertEquals(1L, attackerRow.projectBits());
            assertEquals(2, attackerRow.researchBits());
            assertEquals(ActiveOverride.TRUE.ordinal(), attackerRow.activeOrdinal());

            assertFalse(containsWarning(response, BlitzWarningCode.UPDECLARE_TOO_STRONG));
        });
    }

    @Test
    void blitzPlanPreviewUses5553CapacityByDefaultAndObservedBuildingsWhenDisabled() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setCities(10);
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setCities(10);

            BlitzPlanResponse assumed = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(true),
                    List.of(attacker),
                    List.of(defender)
            );
            BlitzPlanResponse observed = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(false),
                    List.of(attacker),
                    List.of(defender)
            );

            int soldier = MilitaryUnit.SOLDIER.ordinal();
            assertTrue(row(assumed, 101).unitCapsByMilitaryUnitOrdinal()[soldier] > row(observed, 101).unitCapsByMilitaryUnitOrdinal()[soldier]);
        });
    }

    @Test
    void blitzPlanDraftSnapshotsExpandAverageInfraAndRebuyEditsAtBoundary() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setCities(3);
            int[] buys = zeros();
            buys[MilitaryUnit.SOLDIER.ordinal()] = 2500;
                BlitzDraftEdit edit = new BlitzDraftEdit(101, null, WarPolicy.PIRATE.ordinal(), 175_000,
                    null, buys, 0L, 0L, 0, 0, 6, null, null);

            DBNationSnapshot snapshot = SimEndpoints.draftSnapshots(
                    List.of(attacker),
                    Map.of(101, edit),
                    Map.of()
            ).get(0);

            assertEquals(3, snapshot.cityInfraCount());
            for (double infra : snapshot.cityInfra()) {
                assertEquals(1_750d, infra, 0.0001d);
            }
            assertEquals(2500, snapshot.unitsBoughtToday(MilitaryUnit.SOLDIER));
            assertEquals(WarPolicy.PIRATE, snapshot.warPolicy());
            assertEquals(6, snapshot.resetHourUtc());
            assertFalse(snapshot.resetHourUtcFallback());
        });
    }

    @Test
    void blitzPlanPreviewMarksPlannedWarsAndExistingWarsAsContext() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            DBNation defender = nation(202, "Defender", 10_000d);
            DBWar activeWar = new DBWar(9001, 101, 202, 1, 2, false, false, WarType.ORD, WarStatus.ACTIVE,
                    System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), 10, 10, 0);
            warDb.saveWars(List.of(activeWar), true);
            addActiveWar(warDb, activeWar);

            BlitzPlannedWar plannedWar = new BlitzPlannedWar(101, 202, WarType.ORD.ordinal(), true);
            BlitzPlanResponse response = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}),
                    List.of(attacker),
                    List.of(defender)
            );

            assertEquals(1, existingWarCount(response));
            assertEquals(9001, existingWar(response, 0).warId());
            assertTrue(containsPairLockout(response, 101, 202, -1));
            assertFalse(containsPairLockout(response, 202, 101, -1));
            assertTrue(containsWarning(response, BlitzWarningCode.MANUAL_DECLARATION_REJECTED));
        });
    }

    @Test
    void blitzPlanPreviewExposesRecentSameOpponentLockoutsWhenExistingWarsAreHidden() throws Exception {
        withFixture((nationDb, warDb) -> {
            int currentTurn = 100;
            DBNation attacker = nation(101, "Attacker", 1_000d);
            DBNation recentDefender = nation(202, "Recent Defender", 1_000d);
            DBNation oldDefender = nation(303, "Old Defender", 1_000d);
            DBWar recentWar = new DBWar(9002, 101, 202, 1, 2, false, false, WarType.ORD, WarStatus.PEACE,
                    TimeUtil.getTimeFromTurn(currentTurn - 11), 10, 10, 0);
            DBWar oldWar = new DBWar(9003, 101, 303, 1, 3, false, false, WarType.ORD, WarStatus.PEACE,
                    TimeUtil.getTimeFromTurn(currentTurn - 12), 10, 10, 0);
            warDb.saveWars(List.of(recentWar, oldWar), true);

            BlitzPlanResponse response = SimEndpoints.previewBlitzPlan(
                    warDb,
                    requestAtTurn(currentTurn, false),
                    List.of(attacker),
                    List.of(recentDefender, oldDefender)
            );

            assertEquals(0, existingWarCount(response));
            assertTrue(containsPairLockout(response, 101, 202, 1));
            assertFalse(containsPairLockout(response, 202, 101, 1));
            assertFalse(containsPairLockout(response, 101, 303, 1));
        });
    }

    @Test
    void blitzPlanPreviewAndRunApplyForceActiveOverridesToLegalEdgesAndManualDeclarations() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setCities(10);
            attacker.setAircraft(500);
            attacker.setLastActive(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5));
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setCities(10);
            defender.setAircraft(200);
            BlitzPlannedWar plannedWar = new BlitzPlannedWar(101, 202, WarType.ORD.ordinal(), true);

            BlitzPlanResponse blocked = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}),
                    List.of(attacker),
                    List.of(defender)
            );

            assertTrue(containsWarning(blocked, BlitzWarningCode.MANUAL_DECLARATION_REJECTED));

            BlitzDraftEdit forceActiveEdit = new BlitzDraftEdit(101, true, null, null, null, null, 0L, 0L, 0, 0, null, null, null);
            BlitzPlanResponse cleared = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(new BlitzDraftEdit[]{forceActiveEdit}, new BlitzPlannedWar[]{plannedWar}),
                    List.of(attacker),
                    List.of(defender)
            );

            assertFalse(containsWarning(cleared, BlitzWarningCode.MANUAL_DECLARATION_REJECTED));

            BlitzPlanResponse runResponse = SimEndpoints.runBlitzPlan(
                    warDb,
                    requestRun(new BlitzDraftEdit[]{forceActiveEdit}, new BlitzPlannedWar[]{plannedWar}, false),
                    List.of(attacker),
                    List.of(defender)
            );

            assertEquals(1, assignmentCount(runResponse));
            assertTrue(containsAssignment(runResponse, 101, 202));
        });
    }

    @Test
    void blitzPlanPreviewAppliesVmAndBeigeClearsToRowsAndLegalEdges() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setLeaving_vm(link.locutus.discord.util.TimeUtil.getTurn() + 4);
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setColor(NationColor.BEIGE);
            defender.setBeigeTimer(link.locutus.discord.util.TimeUtil.getTurn() + 3);
            BlitzPlannedWar plannedWar = new BlitzPlannedWar(101, 202, WarType.ORD.ordinal(), true);

            BlitzPlanResponse blocked = SimEndpoints.previewBlitzPlan(
                warDb,
                request(new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}),
                List.of(attacker),
                List.of(defender)
            );

            assertTrue(containsWarning(blocked, BlitzWarningCode.MANUAL_DECLARATION_REJECTED));
            assertEquals(4, row(blocked, 101).vmTurns());
            assertEquals(3, row(blocked, 202).beigeTurns());

            BlitzDraftEdit clearAttackerVm = new BlitzDraftEdit(101, null, null, null,
                null, null, 0L, 0L, 0, 0, null, null, true);
            BlitzDraftEdit clearDefenderBeige = new BlitzDraftEdit(202, null, null, null,
                null, null, 0L, 0L, 0, 0, null, true, null);

            BlitzPlanResponse cleared = SimEndpoints.previewBlitzPlan(
                warDb,
                request(new BlitzDraftEdit[]{clearAttackerVm, clearDefenderBeige}, new BlitzPlannedWar[]{plannedWar}),
                List.of(attacker),
                List.of(defender)
            );

            assertFalse(containsWarning(cleared, BlitzWarningCode.MANUAL_DECLARATION_REJECTED));
            assertEquals(0, row(cleared, 101).vmTurns());
            assertEquals(0, row(cleared, 202).beigeTurns());
        });
    }

    @Test
    void blitzPlanRunReturnsPinnedManualDeclarationsAndObjective() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setCities(10);
            attacker.setAircraft(500);
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setCities(10);
            defender.setAircraft(200);
            BlitzPlannedWar plannedWar = new BlitzPlannedWar(101, 202, WarType.ORD.ordinal(), true);

            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    requestRun(new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}, false),
                    List.of(attacker),
                    List.of(defender)
            );

            assertEquals(1, assignmentCount(response));
            AssignmentView assignment = assignment(response, 0);
            assertEquals(101, assignment.declarerNationId());
            assertEquals(202, assignment.targetNationId());
            assertEquals(WarType.ORD.ordinal(), assignment.warTypeOrdinal());
            assertEquals(BlitzAssignedWarSource.USER_PINNED.ordinal(), assignment.sourceOrdinal());
            assertTrue(assignment.initialAttackTypeOrdinal() >= 0);
            assertNotNull(response.objective());
            assertEquals(1, response.objective().sampleCount());
            assertNotNull(response.diagnosticLanes());
            assertEquals(0, response.diagnosticLanes().length % 4);
        });
    }

    @Test
    void blitzPlanRunIgnoresLiveWarSlotsWhenExistingWarsAreExcluded() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setCities(10);
            attacker.setAircraft(500);
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setCities(10);
            defender.setAircraft(200);
            for (int index = 0; index < attacker.getMaxOff(); index++) {
                DBWar activeWar = new DBWar(9100 + index, 101, 301 + index, 1, 2, false, false, WarType.ORD, WarStatus.ACTIVE,
                        System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), 10, 10, 0);
                warDb.saveWars(List.of(activeWar), true);
                addActiveWar(warDb, activeWar);
            }

            BlitzPlannedWar plannedWar = new BlitzPlannedWar(101, 202, WarType.ORD.ordinal(), true);
            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    requestRun(new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}, false, false),
                    List.of(attacker),
                    List.of(defender)
            );

            assertEquals(0, existingWarCount(response));
            assertEquals(attacker.getMaxOff(), row(response, 101).freeOffensiveSlots());
            assertEquals(1, assignmentCount(response));
            AssignmentView assignment = assignment(response, 0);
            assertEquals(101, assignment.declarerNationId());
            assertEquals(202, assignment.targetNationId());
            assertTrue(assignment.initialAttackTypeOrdinal() >= 0);
        });
    }

    @Test
    void blitzPlanRunRemovesDismissedExistingWarsFromSlotAccounting() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setCities(10);
            attacker.setAircraft(500);
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setCities(10);
            defender.setAircraft(200);
            int[] excludedWarIds = new int[attacker.getMaxOff()];
            for (int index = 0; index < attacker.getMaxOff(); index++) {
                int warId = 9200 + index;
                excludedWarIds[index] = warId;
                DBWar activeWar = new DBWar(warId, 101, 401 + index, 1, 2, false, false, WarType.ORD, WarStatus.ACTIVE,
                        System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), 10, 10, 0);
                warDb.saveWars(List.of(activeWar), true);
                addActiveWar(warDb, activeWar);
            }

            BlitzPlannedWar plannedWar = new BlitzPlannedWar(101, 202, WarType.ORD.ordinal(), true);
            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    requestRunWithExcludedWars(new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}, excludedWarIds),
                    List.of(attacker),
                    List.of(defender)
            );

            assertEquals(0, existingWarCount(response));
            assertEquals(attacker.getMaxOff(), row(response, 101).freeOffensiveSlots());
            assertEquals(1, assignmentCount(response));
            AssignmentView assignment = assignment(response, 0);
            assertEquals(101, assignment.declarerNationId());
            assertEquals(202, assignment.targetNationId());
        });
    }

    @Test
        void blitzPlanRunEmitsReplayTraceWhenRequested() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            attacker.setCities(10);
            attacker.setAircraft(500);
            DBNation defender = nation(202, "Defender", 1_000d);
            defender.setCities(10);
            defender.setAircraft(200);

            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                warDb,
                requestRunWithHorizon(BlitzSideMode.ATTACKERS_ONLY, new BlitzDraftEdit[0], new BlitzPlannedWar[0], true, 1),
                List.of(attacker),
                List.of(defender)
            );

            assertNotNull(response.trace());
            assertEquals(response.currentTurn(), response.trace().startTurn());
            assertEquals(1, traceTurnCount(response.trace()));
            TurnSlice turn = traceTurn(response.trace(), 0);
            assertEquals(2, turn.declaredWarPairs().length);
            assertEquals(3, turn.declaredWarLanes().length);
            assertEquals(101, response.participantIds()[turn.declaredWarPairs()[0]]);
            assertEquals(202, response.participantIds()[turn.declaredWarPairs()[1]]);
            assertEquals(1, turn.summaryScalarLanes()[0]);
            assertEquals(0, turn.changedWarIndexes().length);
        });
    }

    @Test
    void blitzPlanRunRespectsDefendersOnlyDirection() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attackerSideNation = nation(101, "Original Attacker", 1_000d);
            attackerSideNation.setCities(10);
            DBNation defenderSideNation = nation(202, "Original Defender", 1_000d);
            defenderSideNation.setCities(10);
            BlitzPlannedWar plannedWar = new BlitzPlannedWar(202, 101, WarType.ORD.ordinal(), true);

            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    requestRun(BlitzSideMode.DEFENDERS_ONLY, new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}, false),
                    List.of(attackerSideNation),
                    List.of(defenderSideNation)
            );

            assertEquals(1, assignmentCount(response));
            assertTrue(containsAssignment(response, 202, 101));
        });
    }

    @Test
    void blitzPlanRunBothModeAcceptsEitherDirectedSidePair() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attackerSideNation = nation(101, "Original Attacker", 1_000d);
            attackerSideNation.setCities(10);
            DBNation defenderSideNation = nation(202, "Original Defender", 1_000d);
            defenderSideNation.setCities(10);
            BlitzPlannedWar plannedWar = new BlitzPlannedWar(202, 101, WarType.ORD.ordinal(), true);

            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    requestRun(BlitzSideMode.BOTH, new BlitzDraftEdit[0], new BlitzPlannedWar[]{plannedWar}, false),
                    List.of(attackerSideNation),
                    List.of(defenderSideNation)
            );

            assertEquals(1, assignmentCount(response));
            assertTrue(containsAssignment(response, 202, 101));
        });
    }

    @Test
    void blitzPlanRunBothModeExcludesReciprocalAssignmentsForSameNationPair() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attackerSideNation = nation(101, "Original Attacker", 1_000d);
            attackerSideNation.setCities(10);
            attackerSideNation.setAircraft(500);
            DBNation defenderSideNation = nation(202, "Original Defender", 1_000d);
            defenderSideNation.setCities(10);
            defenderSideNation.setAircraft(500);

            BlitzPlanResponse response = SimEndpoints.runBlitzPlan(
                    warDb,
                    requestRun(BlitzSideMode.BOTH, new BlitzDraftEdit[0], new BlitzPlannedWar[0], false),
                    List.of(attackerSideNation),
                    List.of(defenderSideNation)
            );

            assertEquals(1, assignmentCount(response));
            assertFalse(containsAssignment(response, 101, 202) && containsAssignment(response, 202, 101));
        });
    }

    @Test
    void blitzPlanPreviewRejectsReciprocalPinnedWarsInBothMode() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attackerSideNation = nation(101, "Original Attacker", 1_000d);
            attackerSideNation.setCities(10);
            DBNation defenderSideNation = nation(202, "Original Defender", 1_000d);
            defenderSideNation.setCities(10);
            BlitzPlannedWar forward = new BlitzPlannedWar(101, 202, WarType.ORD.ordinal(), true);
            BlitzPlannedWar reverse = new BlitzPlannedWar(202, 101, WarType.ORD.ordinal(), true);

            BlitzPlanResponse response = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(new BlitzDraftEdit[0], new BlitzPlannedWar[]{forward, reverse}, BlitzSideMode.BOTH),
                    List.of(attackerSideNation),
                    List.of(defenderSideNation)
            );

            assertTrue(containsWarning(response, BlitzWarningCode.MANUAL_DECLARATION_REJECTED));
        });
    }

    private static BlitzPlanRequest request() {
        return request(true);
    }

    private static BlitzPlanRequest request(boolean assume5553Buildings) {
        return new BlitzPlanRequest(
                "*",
                "*",
                new BlitzDraftEdit[0],
                new BlitzPlannedWar[0],
                BlitzSideMode.ATTACKERS_ONLY.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(),
                null,
                null,
                6,
                true,
                assume5553Buildings,
                1L,
                5,
                new int[0],
                false,
                false
        );
    }

    private static BlitzPlanRequest request(boolean runAssignment, boolean captureTrace) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
            BlitzSideMode.ATTACKERS_ONLY.ordinal(), BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, 6, false, true, 1L, 5,
                new int[0], runAssignment, captureTrace);
    }

    private static BlitzPlanRequest request(int sideModeOrdinal, int rebuyModeOrdinal, boolean runAssignment, boolean captureTrace) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
            sideModeOrdinal, rebuyModeOrdinal, null, null, 6, false, true, 1L, 5, new int[0], runAssignment, captureTrace);
    }

    private static BlitzPlanRequest requestWithHorizon(int horizonTurns) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
            BlitzSideMode.ATTACKERS_ONLY.ordinal(), BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, horizonTurns, false, true, 1L, 5,
                new int[0], false, false);
    }

    private static BlitzPlanRequest requestWithObjective(Integer objectiveOrdinal) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
            BlitzSideMode.ATTACKERS_ONLY.ordinal(), BlitzRebuyMode.FULL_REBUYS.ordinal(), objectiveOrdinal, null, 6, false, true, 1L, 5,
                new int[0], false, false);
    }

    private static BlitzPlanRequest requestWithTurn1DeclarePolicy(Integer turn1DeclarePolicyOrdinal) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
            BlitzSideMode.ATTACKERS_ONLY.ordinal(), BlitzRebuyMode.FULL_REBUYS.ordinal(), null, turn1DeclarePolicyOrdinal, 6, false, true, 1L, 5,
                new int[0], false, false);
    }

    private static BlitzPlanRequest requestAtTurn(int currentTurn, boolean includeExistingWars) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
            BlitzSideMode.ATTACKERS_ONLY.ordinal(), BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, 6, includeExistingWars, true, 1L, currentTurn,
                new int[0], false, false);
    }

    private static BlitzPlanRequest request(BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, BlitzSideMode.ATTACKERS_ONLY.ordinal(),
            BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, 6, true, true, 1L, 5, new int[0], false, false);
    }

    private static BlitzPlanRequest request(BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, BlitzSideMode sideMode) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, sideMode.ordinal(),
            BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, 6, true, true, 1L, 5, new int[0], false, false);
    }

    private static BlitzPlanRequest requestRun(BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, boolean captureTrace) {
        return requestRun(BlitzSideMode.ATTACKERS_ONLY, edits, plannedWars, captureTrace);
    }

    private static BlitzPlanRequest requestRun(BlitzSideMode sideMode, BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, boolean captureTrace) {
        return requestRun(sideMode, edits, plannedWars, captureTrace, true);
    }

    private static BlitzPlanRequest requestRun(BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, boolean captureTrace, boolean includeExistingWars) {
        return requestRun(BlitzSideMode.ATTACKERS_ONLY, edits, plannedWars, captureTrace, includeExistingWars);
    }

    private static BlitzPlanRequest requestRun(BlitzSideMode sideMode, BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, boolean captureTrace, boolean includeExistingWars) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, sideMode.ordinal(),
            BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, 6, includeExistingWars, true, 1L, 5, new int[0], true, captureTrace);
    }

    private static BlitzPlanRequest requestRunWithHorizon(BlitzSideMode sideMode, BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, boolean captureTrace, int horizonTurns) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, sideMode.ordinal(),
            BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, horizonTurns, true, true, 1L, 5, new int[0], true, captureTrace);
    }

    private static BlitzPlanRequest requestRunWithExcludedWars(BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, int[] excludedWarIds) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, BlitzSideMode.ATTACKERS_ONLY.ordinal(),
            BlitzRebuyMode.FULL_REBUYS.ordinal(), null, null, 6, true, true, 1L, 5, excludedWarIds, true, false);
    }

    private static BlitzDraftEdit edit(int nationId, int[] units, int[] buys) {
        return new BlitzDraftEdit(nationId, null, null, null, units, buys, 0L, 0L, 0, 0, null, null, null);
    }

    private static int[] zeros() {
        return new int[MilitaryUnit.values().length];
    }

    private static DBNation nation(int id, String name, double score) {
        SimpleDBNation nation = new SimpleDBNation(new DBNationData());
        nation.setNation_id(id);
        nation.setNation(name);
        nation.setAlliance_id(id / 100);
        nation.setScore(score);
        nation.setCities(1);
        nation.setSoldiers(1);
        nation.setTanks(0);
        nation.setAircraft(0);
        nation.setShips(0);
        nation.setMissiles(0);
        nation.setNukes(0);
        nation.setSpies(0, null);
        nation.setColor(NationColor.GRAY);
        nation.setLastActive(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        nation.setDate(System.currentTimeMillis());
        return nation;
    }

    private static BlitzNationRow row(BlitzPlanResponse response, int nationId) {
        int participantIndex = participantIndex(response, nationId);
        if (participantIndex < 0 || participantIndex >= response.plannerNationCount()) {
            throw new AssertionError("Missing nation row " + nationId);
        }
        int scalarOffset = participantIndex * 17;
        int bitOffset = participantIndex * 3;
        int unitBlock = MilitaryUnit.values().length;
        int unitOffset = participantIndex * unitBlock * 3;
        int[] units = new int[unitBlock];
        int[] unitCaps = new int[unitBlock];
        int[] unitsBoughtToday = new int[unitBlock];
        System.arraycopy(response.plannerUnitLanes(), unitOffset, units, 0, unitBlock);
        System.arraycopy(response.plannerUnitLanes(), unitOffset + unitBlock, unitCaps, 0, unitBlock);
        System.arraycopy(response.plannerUnitLanes(), unitOffset + (2 * unitBlock), unitsBoughtToday, 0, unitBlock);
        long projectBits = (response.plannerBitLanes()[bitOffset] & 0xffffffffL)
            | ((response.plannerBitLanes()[bitOffset + 1] & 0xffffffffL) << 32);
        return new BlitzNationRow(
                nationId,
                units,
                unitCaps,
                unitsBoughtToday,
                response.plannerScalarLanes()[scalarOffset + 2],
                response.plannerScalarLanes()[scalarOffset + 3],
                response.plannerScalarLanes()[scalarOffset + 4],
                response.plannerScalarLanes()[scalarOffset + 9],
                response.plannerScalarLanes()[scalarOffset + 10],
                response.plannerScalarLanes()[scalarOffset + 12],
                projectBits,
                response.plannerBitLanes()[bitOffset + 2],
                response.plannerScalarLanes()[scalarOffset + 13]
        );
    }

    private static int participantIndex(BlitzPlanResponse response, int nationId) {
        for (int index = 0; index < response.participantIds().length; index++) {
            if (response.participantIds()[index] == nationId) {
                return index;
            }
        }
        return -1;
    }

    private static boolean contains(int[] values, int expected) {
        for (int value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWarning(BlitzPlanResponse response, BlitzWarningCode code) {
        for (int offset = 0; offset + 3 < response.warningLanes().length; offset += 4) {
            if (response.warningLanes()[offset] == code.ordinal()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPairLockout(BlitzPlanResponse response, int declarerNationId, int targetNationId, int expectedLockoutValue) {
        for (int offset = 0; offset + 1 < response.pairLockoutPairs().length; offset += 2) {
            int declarerIndex = response.pairLockoutPairs()[offset];
            int targetIndex = response.pairLockoutPairs()[offset + 1];
            int lockoutValue = response.pairLockoutLanes()[offset / 2];
            if (response.participantIds()[declarerIndex] == declarerNationId
                    && response.participantIds()[targetIndex] == targetNationId
                    && lockoutValue == expectedLockoutValue) {
                return true;
            }
        }
        return false;
    }

    private static int assignmentCount(BlitzPlanResponse response) {
        return response.assignmentPairs().length / 2;
    }

    private static AssignmentView assignment(BlitzPlanResponse response, int assignmentIndex) {
        int pairOffset = assignmentIndex * 2;
        int laneOffset = assignmentIndex * 3;
        return new AssignmentView(
                response.participantIds()[response.assignmentPairs()[pairOffset]],
                response.participantIds()[response.assignmentPairs()[pairOffset + 1]],
                response.assignmentLanes()[laneOffset],
                response.assignmentLanes()[laneOffset + 1],
                response.assignmentLanes()[laneOffset + 2]
        );
    }

    private static boolean containsAssignment(BlitzPlanResponse response, int declarerNationId, int targetNationId) {
        for (int assignmentIndex = 0; assignmentIndex < assignmentCount(response); assignmentIndex++) {
            AssignmentView assignment = assignment(response, assignmentIndex);
            if (assignment.declarerNationId() == declarerNationId && assignment.targetNationId() == targetNationId) {
                return true;
            }
        }
        return false;
    }

    private static int existingWarCount(BlitzPlanResponse response) {
        return response.existingWarPairs().length / 2;
    }

    private static ExistingWarView existingWar(BlitzPlanResponse response, int warIndex) {
        int pairOffset = warIndex * 2;
        int laneOffset = warIndex * 5;
        return new ExistingWarView(
                response.existingWarLanes()[laneOffset],
                response.participantIds()[response.existingWarPairs()[pairOffset]],
                response.participantIds()[response.existingWarPairs()[pairOffset + 1]],
                response.existingWarLanes()[laneOffset + 1],
                response.existingWarLanes()[laneOffset + 2]
        );
    }

    private static int traceTurnCount(BlitzReplayTrace trace) {
        return trace.turnMetaLanes().length / 12;
    }

    private static TurnSlice traceTurn(BlitzReplayTrace trace, int turnIndex) {
        int metaOffset = turnIndex * 12;
        int nextMetaOffset = metaOffset + 12;
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
        int changedNationEntryEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset] : trace.changedNationIndexes().length;
        int changedNationLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 1] : trace.changedNationLanes().length;
        int changedWarEntryEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 2] : trace.changedWarIndexes().length;
        int changedWarLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 3] : trace.changedWarLanes().length;
        int declaredWarPairEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 4] : trace.declaredWarPairs().length;
        int declaredWarLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 5] : trace.declaredWarLanes().length;
        int concludedWarLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 6] : trace.concludedWarLanes().length;
        int summaryScalarLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 7] : trace.summaryScalarLanes().length;
        int summaryWarTypeLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 8] : trace.summaryWarTypeCounts().length;
        int summaryAttackOutcomeLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 9] : trace.summaryAttackOutcomeCounts().length;
        int summaryUnitLossLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 10] : trace.summaryUnitLossCounts().length;
        int summaryInfraLossLaneEnd = turnIndex + 1 < traceTurnCount(trace) ? trace.turnMetaLanes()[nextMetaOffset + 11] : trace.summaryInfraLossCents().length;
        return new TurnSlice(
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

    private record BlitzNationRow(
            int nationId,
            int[] unitsByMilitaryUnitOrdinal,
            int[] unitCapsByMilitaryUnitOrdinal,
            int[] unitsBoughtTodayByMilitaryUnitOrdinal,
            int avgInfraCents,
            int beigeTurns,
            int vmTurns,
            int freeOffensiveSlots,
            int freeDefensiveSlots,
            int policyOrdinal,
            long projectBits,
            int researchBits,
            int activeOrdinal
    ) {
    }

    private record AssignmentView(
            int declarerNationId,
            int targetNationId,
            int warTypeOrdinal,
            int sourceOrdinal,
            int initialAttackTypeOrdinal
    ) {
    }

    private record ExistingWarView(
            int warId,
            int attackerNationId,
            int defenderNationId,
            int startTurn,
            int turnsLeft
    ) {
    }

    private record TurnSlice(
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

    private static void withFixture(FixtureBody body) throws Exception {
        Path tempDir = Files.createTempDirectory("blitz-plan-contract-");
        String previousDirectory = Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY;
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);
        Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = tempDir.toString();
        try (NationDB nationDb = new NationDB(); WarDB warDb = new WarDB("blitz-plan-contract-" + System.nanoTime())) {
            instanceField.set(null, fakeLocutus(nationDb, warDb));
            body.run(nationDb, warDb);
        } finally {
            instanceField.set(null, previousInstance);
            Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY = previousDirectory;
            deleteRecursively(tempDir);
        }
    }

    private static Locutus fakeLocutus(NationDB nationDb, WarDB warDb) throws Exception {
        Locutus locutus = (Locutus) allocateWithoutConstructor(Locutus.class);
        Object loader = Proxy.newProxyInstance(
                ILoader.class.getClassLoader(),
                new Class<?>[]{ILoader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNationDB", "getCachedNationDB" -> nationDb;
                    case "getWarDB" -> warDb;
                    case "resolveFully" -> proxy;
                    case "printStacktrace" -> "";
                    default -> defaultValue(method.getReturnType());
                });
        Field loaderField = Locutus.class.getDeclaredField("loader");
        loaderField.setAccessible(true);
        loaderField.set(locutus, loader);
        return locutus;
    }

    private static void addActiveWar(WarDB warDb, DBWar war) throws Exception {
        Field activeWarsField = WarDB.class.getDeclaredField("activeWars");
        activeWarsField.setAccessible(true);
        ActiveWarHandler activeWars = (ActiveWarHandler) activeWarsField.get(warDb);
        activeWars.addActiveWar(war);
    }

    private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return unsafe.allocateInstance(type);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @FunctionalInterface
    private interface FixtureBody {
        void run(NationDB nationDb, WarDB warDb) throws Exception;
    }
}
