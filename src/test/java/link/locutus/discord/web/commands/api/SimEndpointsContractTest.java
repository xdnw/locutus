package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
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
import link.locutus.discord.util.battle.BlitzWarningCode;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzLegalEdge;
import link.locutus.discord.web.commands.binding.value_types.BlitzNationRow;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
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
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(), List.of(attacker), List.of(attacker)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(99, BlitzRebuyMode.FULL_REBUYS.ordinal(), false, false), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(BlitzSideMode.ATTACKERS_ONLY.ordinal(), 99, false, false), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, requestWithHorizon(13), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(true, false), List.of(attacker), List.of(defender)));
            assertThrows(IllegalArgumentException.class,
                    () -> SimEndpoints.previewBlitzPlan(warDb, request(false, true), List.of(attacker), List.of(defender)));
        });
    }

    @Test
    void blitzPlanPreviewRejectsUnknownEditTargetsAndBadOrdinals() throws Exception {
        withFixture((nationDb, warDb) -> {
            DBNation attacker = nation(101, "Attacker", 1_000d);
            DBNation defender = nation(202, "Defender", 1_000d);
            BlitzDraftEdit unknownEdit = edit(303, null, null);
            BlitzDraftEdit badUnitArray = edit(101, new int[]{1, 2}, null);
            BlitzDraftEdit badPolicy = new BlitzDraftEdit(101, null, 999, null, null, null, 0L, 0L, 0, 0, null);
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
                    4
            );

            BlitzPlanResponse response = SimEndpoints.previewBlitzPlan(
                    warDb,
                    request(new BlitzDraftEdit[]{edit}, new BlitzPlannedWar[0]),
                    List.of(attacker),
                    List.of(defender)
            );

            assertArrayEquals(new int[]{101}, response.attackerNationIds());
            assertArrayEquals(new int[]{202}, response.defenderNationIds());
            BlitzNationRow attackerRow = row(response, 101);
            assertEquals(1234, attackerRow.unitsByMilitaryUnitOrdinal()[MilitaryUnit.AIRCRAFT.ordinal()]);
            assertEquals(15, attackerRow.unitsBoughtTodayByMilitaryUnitOrdinal()[MilitaryUnit.AIRCRAFT.ordinal()]);
            assertEquals(155_000, attackerRow.avgInfraCents());
            assertEquals(WarPolicy.PIRATE.ordinal(), attackerRow.policyOrdinal());
            assertEquals(1L, attackerRow.projectBits());
            assertEquals(2, attackerRow.researchBits());
            assertEquals(ActiveOverride.TRUE.ordinal(), attackerRow.activeOrdinal());

            BlitzLegalEdge edge = edge(response, 101, 202);
            assertFalse(edge.legal());
            assertTrue(contains(edge.blockedReasonOrdinals(), BlitzWarningCode.UPDECLARE_TOO_STRONG.ordinal()));
            assertTrue(containsWarning(response, BlitzWarningCode.UPDECLARE_TOO_STRONG));
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
                    null, buys, 0L, 0L, 0, 0, 6);

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

            assertEquals(1, response.existingWars().length);
            assertEquals(9001, response.existingWars()[0].warId());
            BlitzLegalEdge edge = edge(response, 101, 202);
            assertFalse(edge.legal());
            assertTrue(contains(edge.blockedReasonOrdinals(), BlitzWarningCode.ACTIVE_PAIR_CONFLICT.ordinal()));
            assertTrue(containsWarning(response, BlitzWarningCode.MANUAL_DECLARATION_REJECTED));
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

            assertEquals(1, response.assignments().length);
            assertEquals(101, response.assignments()[0].declarerNationId());
            assertEquals(202, response.assignments()[0].targetNationId());
            assertEquals(WarType.ORD.ordinal(), response.assignments()[0].warTypeOrdinal());
            assertEquals(BlitzAssignedWarSource.USER_PINNED.ordinal(), response.assignments()[0].sourceOrdinal());
            assertNotNull(response.objective());
            assertEquals(1, response.objective().sampleCount());
            assertNotNull(response.diagnostics());
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

            assertEquals(0, response.existingWars().length);
            assertEquals(attacker.getMaxOff(), row(response, 101).freeOffensiveSlots());
            assertEquals(1, response.assignments().length);
            assertEquals(101, response.assignments()[0].declarerNationId());
            assertEquals(202, response.assignments()[0].targetNationId());
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
            assertNotNull(response.trace().initialFrame());
            assertEquals(response.currentTurn(), response.trace().initialFrame().currentTurn());
            assertEquals(2, response.trace().initialFrame().nations().length);
            assertEquals(0, response.trace().initialFrame().wars().length);
            assertEquals(1, response.trace().deltas().length);
            assertEquals(1, response.trace().deltas()[0].declaredWars().length);
            assertEquals(101, response.trace().deltas()[0].declaredWars()[0].declarerNationId());
            assertEquals(202, response.trace().deltas()[0].declaredWars()[0].targetNationId());
            assertTrue(response.trace().deltas()[0].wars().length >= 1);
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

            assertEquals(1, response.assignments().length);
            assertEquals(202, response.assignments()[0].declarerNationId());
            assertEquals(101, response.assignments()[0].targetNationId());
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

            assertEquals(1, response.assignments().length);
            assertEquals(202, response.assignments()[0].declarerNationId());
            assertEquals(101, response.assignments()[0].targetNationId());
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

            assertEquals(1, response.assignments().length);
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
                BlitzSideMode.ATTACKERS_ONLY.ordinal(), BlitzRebuyMode.FULL_REBUYS.ordinal(), 6, false, true, 1L, 5,
                new int[0], runAssignment, captureTrace);
    }

    private static BlitzPlanRequest request(int sideModeOrdinal, int rebuyModeOrdinal, boolean runAssignment, boolean captureTrace) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
                sideModeOrdinal, rebuyModeOrdinal, 6, false, true, 1L, 5, new int[0], runAssignment, captureTrace);
    }

    private static BlitzPlanRequest requestWithHorizon(int horizonTurns) {
        return new BlitzPlanRequest("*", "*", new BlitzDraftEdit[0], new BlitzPlannedWar[0],
                BlitzSideMode.ATTACKERS_ONLY.ordinal(), BlitzRebuyMode.FULL_REBUYS.ordinal(), horizonTurns, false, true, 1L, 5,
                new int[0], false, false);
    }

    private static BlitzPlanRequest request(BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, BlitzSideMode.ATTACKERS_ONLY.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(), 6, true, true, 1L, 5, new int[0], false, false);
    }

    private static BlitzPlanRequest request(BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, BlitzSideMode sideMode) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, sideMode.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(), 6, true, true, 1L, 5, new int[0], false, false);
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
                BlitzRebuyMode.FULL_REBUYS.ordinal(), 6, includeExistingWars, true, 1L, 5, new int[0], true, captureTrace);
    }

    private static BlitzPlanRequest requestRunWithHorizon(BlitzSideMode sideMode, BlitzDraftEdit[] edits, BlitzPlannedWar[] plannedWars, boolean captureTrace, int horizonTurns) {
        return new BlitzPlanRequest("*", "*", edits, plannedWars, sideMode.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(), horizonTurns, true, true, 1L, 5, new int[0], true, captureTrace);
    }

    private static BlitzDraftEdit edit(int nationId, int[] units, int[] buys) {
        return new BlitzDraftEdit(nationId, null, null, null, units, buys, 0L, 0L, 0, 0, null);
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
        nation.setLastActive(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        nation.setDate(System.currentTimeMillis());
        return nation;
    }

    private static BlitzNationRow row(BlitzPlanResponse response, int nationId) {
        for (BlitzNationRow row : response.nations()) {
            if (row.nationId() == nationId) {
                return row;
            }
        }
        throw new AssertionError("Missing nation row " + nationId);
    }

    private static BlitzLegalEdge edge(BlitzPlanResponse response, int declarerId, int targetId) {
        for (BlitzLegalEdge edge : response.legalEdges()) {
            if (edge.declarerNationId() == declarerId && edge.targetNationId() == targetId) {
                return edge;
            }
        }
        throw new AssertionError("Missing legal edge " + declarerId + ":" + targetId);
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
        for (link.locutus.discord.util.battle.BlitzWarning warning : response.warnings()) {
            if (warning.code() == code) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAssignment(BlitzPlanResponse response, int declarerNationId, int targetNationId) {
        for (var assignment : response.assignments()) {
            if (assignment.declarerNationId() == declarerNationId && assignment.targetNationId() == targetNationId) {
                return true;
            }
        }
        return false;
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
