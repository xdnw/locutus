package link.locutus.discord.sim.planners;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.api.SimEndpoints;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzMilitaryRules;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzSideMode;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.Turn1DeclarePolicy;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import link.locutus.discord.sim.planners.providers.CompositeBlitzActivityModel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Endpoint-shaped benchmark for the frontend blitz route.
 *
 * <p>This intentionally calls the same {@link SimEndpoints#runBlitzPlan} path used by the
 * command endpoint after selector binding, and can synthesize the route override edits produced
 * by the React page for params such as {@code forceActive=true}, {@code clearBeige=true},
 * {@code clearVM=true}, {@code avgInfra=2500}, and {@code unitMmr=5553}.</p>
 */
public final class BlitzPlanEndpointBenchmark {
    private static final String DEFAULT_ATTACKER_ALLIANCES = "Singularity";
    private static final String DEFAULT_DEFENDER_ALLIANCES = "The Knights Radiant";

    private BlitzPlanEndpointBenchmark() {
    }

    public static void main(String[] args) {
        Config config = Config.parse(args);
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS = false;

        try (LiveDatabases databases = loadLiveDatabases()) {
            Set<DBNation> attackers = resolveAllianceMembers(databases.nationDb(), csvValues(config.attackerAlliances()), "attackerAlliances");
            Set<DBNation> defenders = resolveAllianceMembers(databases.nationDb(), csvValues(config.defenderAlliances()), "defenderAlliances");
            BlitzPlanRequest request = request(config, attackers, defenders);

            System.out.println("phase,attackers,defenders,edits,horizon,captureTrace,responseJsonBytes,responseGzipBytes,elapsedMs");
            PlannerProfiler.Session session = new PlannerProfiler.Session();
            long runStart = System.nanoTime();
            BlitzPlanResponse response = PlannerProfiler.withSession(session, () -> invokeRunBlitzPlan(databases.warDb(), request, attackers, defenders));
            long runElapsed = System.nanoTime() - runStart;

            long jsonStart = System.nanoTime();
            byte[] json = WebUtil.GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
            long jsonElapsed = System.nanoTime() - jsonStart;
            long gzipBytes = gzipLength(json);

            System.out.printf(Locale.ROOT,
                    "run,%d,%d,%d,%d,%s,%d,%d,%.3f%n",
                    attackers.size(),
                    defenders.size(),
                    request.edits().length,
                    request.horizonTurns(),
                    request.captureTrace(),
                    json.length,
                    gzipBytes,
                    runElapsed / 1_000_000.0d);
            System.out.printf(Locale.ROOT,
                    "serialize,%d,%d,%d,%d,%s,%d,%d,%.3f%n",
                    attackers.size(),
                    defenders.size(),
                    request.edits().length,
                    request.horizonTurns(),
                    request.captureTrace(),
                    json.length,
                    gzipBytes,
                    jsonElapsed / 1_000_000.0d);
            if (config.printAssignmentSummary()) {
                printAssignmentSummary(response, config);
            }
            if (config.printCandidateSummary()) {
                printCandidateSummary(databases.warDb(), request, attackers, defenders, response);
            }
            printProfile(session.snapshot());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run endpoint-shaped blitz benchmark", e);
        }
    }

    private static void printAssignmentSummary(BlitzPlanResponse response, Config config) {
        int participantCount = response.participantIds().length;
        int[] offensiveAssignments = new int[participantCount];
        int[] defensiveAssignments = new int[participantCount];
        int[] assignmentPairs = response.assignmentPairs();
        for (int pairIndex = 0; pairIndex + 1 < assignmentPairs.length; pairIndex += 2) {
            offensiveAssignments[assignmentPairs[pairIndex]]++;
            defensiveAssignments[assignmentPairs[pairIndex + 1]]++;
        }

        List<ParticipantAssignmentRow> attackers = new ArrayList<>();
        List<ParticipantAssignmentRow> defenders = new ArrayList<>();
        int[] scalarLanes = response.plannerScalarLanes();
        for (int participantIndex = 0; participantIndex < response.plannerNationCount(); participantIndex++) {
            int scalarOffset = participantIndex * 17;
            ParticipantAssignmentRow row = new ParticipantAssignmentRow(
                    response.participantIds()[participantIndex],
                    response.participantNames()[participantIndex],
                    scalarLanes[scalarOffset + 1],
                    scalarLanes[scalarOffset + 9],
                    scalarLanes[scalarOffset + 10],
                    scalarLanes[scalarOffset + 11],
                    offensiveAssignments[participantIndex],
                    defensiveAssignments[participantIndex]
            );
            if (scalarLanes[scalarOffset] == 0) {
                attackers.add(row);
            } else if (scalarLanes[scalarOffset] == 1) {
                defenders.add(row);
            }
        }

        attackers.sort(ParticipantAssignmentRow.HIGH_CITY_ORDER);
        defenders.sort(ParticipantAssignmentRow.HIGH_CITY_ORDER);

        System.out.printf(Locale.ROOT,
                "assignmentSummary,objectiveOrdinal=%d,assignmentPairs=%d,plannerNationCount=%d%n",
                config.objectiveOrdinal(),
                assignmentPairs.length / 2,
                response.plannerNationCount());

        List<ParticipantAssignmentRow> highCityIdleAttackers = attackers.stream()
                .filter(row -> row.cityCount() >= 40 && row.offensiveAssignments() == 0)
                .toList();
        System.out.printf(Locale.ROOT, "highCityIdleAttackers,count=%d%n", highCityIdleAttackers.size());
        highCityIdleAttackers.stream().limit(25).forEach(row -> System.out.printf(Locale.ROOT,
                "idleAttacker,id=%d,name=%s,cities=%d,freeOff=%d,maxOff=%d,offAssigned=%d,defAssigned=%d%n",
                row.nationId(), csvSafe(row.name()), row.cityCount(), row.freeOff(), row.maxOff(), row.offensiveAssignments(), row.defensiveAssignments()));

        for (int nationId : new int[]{379867, 590133}) {
            attackers.stream().filter(row -> row.nationId() == nationId).findFirst().ifPresent(row -> System.out.printf(Locale.ROOT,
                    "highlightAttacker,id=%d,name=%s,cities=%d,freeOff=%d,maxOff=%d,offAssigned=%d,defAssigned=%d%n",
                    row.nationId(), csvSafe(row.name()), row.cityCount(), row.freeOff(), row.maxOff(), row.offensiveAssignments(), row.defensiveAssignments()));
        }

        System.out.println("topAttackersByCity,id,name,cities,freeOff,maxOff,offAssigned,defAssigned");
        attackers.stream().limit(25).forEach(row -> System.out.printf(Locale.ROOT,
                "%d,%s,%d,%d,%d,%d,%d%n",
                row.nationId(), csvSafe(row.name()), row.cityCount(), row.freeOff(), row.maxOff(), row.offensiveAssignments(), row.defensiveAssignments()));

        System.out.println("topDefendersByCity,id,name,cities,freeDef,defAssigned,offAssigned");
        defenders.stream().limit(25).forEach(row -> System.out.printf(Locale.ROOT,
                "%d,%s,%d,%d,%d,%d%n",
                row.nationId(), csvSafe(row.name()), row.cityCount(), row.freeDef(), row.defensiveAssignments(), row.offensiveAssignments()));

        List<ParticipantAssignmentRow> topUntargetedDefenders = defenders.stream()
                .filter(row -> row.defensiveAssignments() == 0)
                .limit(25)
                .toList();
        System.out.printf(Locale.ROOT, "topUntargetedDefenders,count=%d%n", topUntargetedDefenders.size());
        topUntargetedDefenders.forEach(row -> System.out.printf(Locale.ROOT,
                "untargetedDefender,id=%d,name=%s,cities=%d,freeDef=%d,offAssigned=%d%n",
                row.nationId(), csvSafe(row.name()), row.cityCount(), row.freeDef(), row.offensiveAssignments()));
    }

    private static String csvSafe(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace(',', ';');
    }

    @SuppressWarnings("unchecked")
    private static void printCandidateSummary(
            WarDB warDb,
            BlitzPlanRequest request,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders,
            BlitzPlanResponse response
    ) {
        try {
            Method buildContext = SimEndpoints.class.getDeclaredMethod(
                    "buildBlitzPlanContext",
                    WarDB.class,
                    BlitzPlanRequest.class,
                    Collection.class,
                    Collection.class
            );
            buildContext.setAccessible(true);
            Object context = buildContext.invoke(null, warDb, request, attackers, defenders);

            List<DBNationSnapshot> declarers = (List<DBNationSnapshot>) invokeAccessor(context, "attackerSnapshots");
            List<DBNationSnapshot> targets = (List<DBNationSnapshot>) invokeAccessor(context, "defenderSnapshots");
            OverrideSet overrides = (OverrideSet) invokeAccessor(context, "overrides");
            int currentTurn = (Integer) invokeAccessor(context, "currentTurn");
            CompositeBlitzActivityModel activityModel = (CompositeBlitzActivityModel) invokeAccessor(context, "activityModel");

            SimTuning tuning = tuningForRequest(request);
            StrategicObjective objective = objectiveForRequest(request);
            SnapshotActivityProvider activityProvider = activityModel.snapshotProvider().withWartimeUplift(tuning.wartimeActivityUplift());
            ArrayList<DBNationSnapshot> combined = new ArrayList<>(declarers.size() + targets.size());
            combined.addAll(declarers);
            combined.addAll(targets);
            CompiledScenario scenario = new ScenarioCompiler().compile(
                    declarers,
                    targets,
                    overrides,
                    TreatyProvider.NONE,
                    PlannerSimSupport.compileActivityWeights(activityProvider, currentTurn, overrides, combined)
            );

            int[] attackerCaps = new int[scenario.attackerCount()];
            int[] defenderCaps = new int[scenario.defenderCount()];
            for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
                attackerCaps[attackerIndex] = Math.max(0, scenario.attackerFreeOffSlots(attackerIndex));
            }
            for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
                defenderCaps[defenderIndex] = Math.max(0, scenario.defenderFreeDefSlots(defenderIndex));
            }

            CandidateEdgeTable candidateEdges = new CandidateEdgeTable();
            OpeningEvaluator.evaluate(
                    scenario,
                    tuning,
                    overrides,
                    objective,
                    attackerCaps.clone(),
                    defenderCaps.clone(),
                    candidateEdges
            );

            int[] outgoingCandidateCounts = new int[scenario.attackerCount()];
            int[] incomingCandidateCounts = new int[scenario.defenderCount()];
            double[] outgoingScoreSums = new double[scenario.attackerCount()];
            double[] incomingScoreSums = new double[scenario.defenderCount()];
            float[] outgoingScoreMax = new float[scenario.attackerCount()];
            float[] incomingScoreMax = new float[scenario.defenderCount()];
            java.util.Arrays.fill(outgoingScoreMax, Float.NEGATIVE_INFINITY);
            java.util.Arrays.fill(incomingScoreMax, Float.NEGATIVE_INFINITY);
            for (int edgeIndex = 0; edgeIndex < candidateEdges.edgeCount(); edgeIndex++) {
                int attackerIndex = candidateEdges.attackerIndex(edgeIndex);
                int defenderIndex = candidateEdges.defenderIndex(edgeIndex);
                float score = candidateEdges.scalarScore(edgeIndex);
                outgoingCandidateCounts[attackerIndex]++;
                incomingCandidateCounts[defenderIndex]++;
                outgoingScoreSums[attackerIndex] += score;
                incomingScoreSums[defenderIndex] += score;
                outgoingScoreMax[attackerIndex] = Math.max(outgoingScoreMax[attackerIndex], score);
                incomingScoreMax[defenderIndex] = Math.max(incomingScoreMax[defenderIndex], score);
            }

            int[] offensiveAssignments = new int[response.participantIds().length];
            int[] defensiveAssignments = new int[response.participantIds().length];
            int[] assignmentPairs = response.assignmentPairs();
            for (int pairIndex = 0; pairIndex + 1 < assignmentPairs.length; pairIndex += 2) {
                offensiveAssignments[assignmentPairs[pairIndex]]++;
                defensiveAssignments[assignmentPairs[pairIndex + 1]]++;
            }

            System.out.printf(Locale.ROOT,
                    "candidateSummary,objectiveOrdinal=%d,candidateEdges=%d,attackers=%d,defenders=%d%n",
                    request.objectiveOrdinal(),
                    candidateEdges.edgeCount(),
                    scenario.attackerCount(),
                    scenario.defenderCount());

            for (int nationId : new int[]{379867, 590133}) {
                int attackerIndex = attackerIndexByNationId(scenario, nationId);
                if (attackerIndex >= 0) {
                    DBNationSnapshot attacker = scenario.attacker(attackerIndex);
                    System.out.printf(Locale.ROOT,
                            "highlightAttackerCandidates,id=%d,name=%s,cities=%d,outgoingCandidates=%d,maxScore=%.3f,avgScore=%.3f,freeOff=%d%n",
                            nationId,
                            csvSafe(participantName(response, nationId)),
                            attacker.cities(),
                            outgoingCandidateCounts[attackerIndex],
                            maxScore(outgoingScoreMax[attackerIndex]),
                            averageScore(outgoingScoreSums[attackerIndex], outgoingCandidateCounts[attackerIndex]),
                            attackerCaps[attackerIndex]);
                    printTopOutgoingEdges(candidateEdges, scenario, response, attackerIndex, 8);
                }
            }

            List<AttackerCandidateRow> idleHighCityAttackers = new ArrayList<>();
            for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
                DBNationSnapshot attacker = scenario.attacker(attackerIndex);
                if (attacker.cities() >= 40) {
                    idleHighCityAttackers.add(new AttackerCandidateRow(
                            attacker.nationId(),
                            participantName(response, attacker.nationId()),
                            attacker.cities(),
                            outgoingCandidateCounts[attackerIndex],
                                maxScore(outgoingScoreMax[attackerIndex]),
                                averageScore(outgoingScoreSums[attackerIndex], outgoingCandidateCounts[attackerIndex]),
                            assignedOffenses(response, scenario.attackerNationId(attackerIndex)),
                            attackerCaps[attackerIndex]
                    ));
                }
            }
            idleHighCityAttackers.sort(AttackerCandidateRow.ORDER);
            idleHighCityAttackers.stream()
                    .filter(row -> row.assignedOffenses() == 0)
                    .limit(25)
                    .forEach(row -> System.out.printf(Locale.ROOT,
                            "idleAttackerCandidates,id=%d,name=%s,cities=%d,outgoingCandidates=%d,maxScore=%.3f,avgScore=%.3f,assignedOff=%d,freeOff=%d%n",
                            row.nationId(), csvSafe(row.name()), row.cityCount(), row.outgoingCandidates(), row.maxScore(), row.avgScore(), row.assignedOffenses(), row.freeOff()));

                idleHighCityAttackers.stream()
                    .filter(row -> row.assignedOffenses() == 0)
                    .limit(10)
                    .forEach(row -> printIdleAttackerOptionSummary(
                        row,
                        scenario,
                        candidateEdges,
                        response,
                        defensiveAssignments,
                        defenderCaps
                    ));

            List<DefenderCandidateRow> topDefenders = new ArrayList<>();
            for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
                DBNationSnapshot defender = scenario.defender(defenderIndex);
                topDefenders.add(new DefenderCandidateRow(
                        defender.nationId(),
                    participantName(response, defender.nationId()),
                        defender.cities(),
                        incomingCandidateCounts[defenderIndex],
                        maxScore(incomingScoreMax[defenderIndex]),
                        averageScore(incomingScoreSums[defenderIndex], incomingCandidateCounts[defenderIndex]),
                        assignedDefenses(response, scenario.defenderNationId(defenderIndex)),
                        defenderCaps[defenderIndex]
                ));
            }
            topDefenders.sort(DefenderCandidateRow.ORDER);
            topDefenders.stream().limit(25).forEach(row -> System.out.printf(Locale.ROOT,
                    "topDefenderCandidates,id=%d,name=%s,cities=%d,incomingCandidates=%d,maxScore=%.3f,avgScore=%.3f,assignedDef=%d,freeDef=%d%n",
                    row.nationId(), csvSafe(row.name()), row.cityCount(), row.incomingCandidates(), row.maxScore(), row.avgScore(), row.assignedDefenses(), row.freeDef()));

            topDefenders.stream()
                    .filter(row -> row.assignedDefenses() == 0)
                    .limit(3)
                    .forEach(row -> {
                        int defenderIndex = defenderIndexByNationId(scenario, row.nationId());
                        if (defenderIndex >= 0) {
                            printTopIncomingEdges(candidateEdges, scenario, response, defenderIndex, 8);
                        }
                    });

            topDefenders.stream()
                    .filter(row -> row.assignedDefenses() == 0)
                    .limit(10)
                    .forEach(row -> printUntargetedDefenderSourceSummary(
                            row,
                            scenario,
                            candidateEdges,
                            response,
                            offensiveAssignments
                    ));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to print candidate summary", e);
        }
    }

    private static void printIdleAttackerOptionSummary(
            AttackerCandidateRow row,
            CompiledScenario scenario,
            CandidateEdgeTable candidateEdges,
            BlitzPlanResponse response,
            int[] defensiveAssignments,
            int[] defenderCaps
    ) {
        int attackerIndex = attackerIndexByNationId(scenario, row.nationId());
        if (attackerIndex < 0) {
            return;
        }
        int untargetedCandidateCount = 0;
        int openSlotCandidateCount = 0;
        int bestUntargetedDefenderId = -1;
        float bestUntargetedScore = Float.NEGATIVE_INFINITY;
        for (int edgeIndex = 0; edgeIndex < candidateEdges.edgeCount(); edgeIndex++) {
            if (candidateEdges.attackerIndex(edgeIndex) != attackerIndex) {
                continue;
            }
            int defenderIndex = candidateEdges.defenderIndex(edgeIndex);
            int defenderNationId = scenario.defenderNationId(defenderIndex);
            int participantIndex = participantIndex(response, defenderNationId);
            int defenderAssigned = participantIndex < 0 ? 0 : defensiveAssignments[participantIndex];
            if (defenderAssigned == 0) {
                untargetedCandidateCount++;
                float score = candidateEdges.scalarScore(edgeIndex);
                if (score > bestUntargetedScore) {
                    bestUntargetedScore = score;
                    bestUntargetedDefenderId = defenderNationId;
                }
            }
            if (defenderAssigned < defenderCaps[defenderIndex]) {
                openSlotCandidateCount++;
            }
        }
        System.out.printf(Locale.ROOT,
                "idleAttackerOptions,id=%d,name=%s,untargetedCandidates=%d,openSlotCandidates=%d,bestUntargetedDefenderId=%d,bestUntargetedDefenderName=%s,bestUntargetedScore=%.3f%n",
                row.nationId(),
                csvSafe(row.name()),
                untargetedCandidateCount,
                openSlotCandidateCount,
                bestUntargetedDefenderId,
                csvSafe(participantName(response, bestUntargetedDefenderId)),
                maxScore(bestUntargetedScore));
    }

    private static void printUntargetedDefenderSourceSummary(
            DefenderCandidateRow row,
            CompiledScenario scenario,
            CandidateEdgeTable candidateEdges,
            BlitzPlanResponse response,
            int[] offensiveAssignments
    ) {
        int defenderIndex = defenderIndexByNationId(scenario, row.nationId());
        if (defenderIndex < 0) {
            return;
        }
        int idleSourceCount = 0;
        int activeSourceCount = 0;
        int bestIdleAttackerId = -1;
        float bestIdleScore = Float.NEGATIVE_INFINITY;
        for (int edgeIndex = 0; edgeIndex < candidateEdges.edgeCount(); edgeIndex++) {
            if (candidateEdges.defenderIndex(edgeIndex) != defenderIndex) {
                continue;
            }
            int attackerNationId = scenario.attackerNationId(candidateEdges.attackerIndex(edgeIndex));
            int participantIndex = participantIndex(response, attackerNationId);
            int attackerAssigned = participantIndex < 0 ? 0 : offensiveAssignments[participantIndex];
            if (attackerAssigned == 0) {
                idleSourceCount++;
                float score = candidateEdges.scalarScore(edgeIndex);
                if (score > bestIdleScore) {
                    bestIdleScore = score;
                    bestIdleAttackerId = attackerNationId;
                }
            } else {
                activeSourceCount++;
            }
        }
        System.out.printf(Locale.ROOT,
                "untargetedDefenderSources,id=%d,name=%s,idleCandidateAttackers=%d,activeCandidateAttackers=%d,bestIdleAttackerId=%d,bestIdleAttackerName=%s,bestIdleScore=%.3f%n",
                row.nationId(),
                csvSafe(row.name()),
                idleSourceCount,
                activeSourceCount,
                bestIdleAttackerId,
                csvSafe(participantName(response, bestIdleAttackerId)),
                maxScore(bestIdleScore));
    }

    private static double averageScore(double sum, int count) {
        return count <= 0 ? 0d : sum / count;
    }

    private static double maxScore(float value) {
        return value == Float.NEGATIVE_INFINITY ? 0d : value;
    }

    private static void printTopOutgoingEdges(
            CandidateEdgeTable candidateEdges,
            CompiledScenario scenario,
            BlitzPlanResponse response,
            int attackerIndex,
            int limit
    ) {
        int[] edgeIndexes = matchingEdgeIndexes(candidateEdges, attackerIndex, true);
        sortEdgeIndexesByScore(candidateEdges, edgeIndexes);
        DBNationSnapshot attacker = scenario.attacker(attackerIndex);
        System.out.printf(Locale.ROOT,
                "topOutgoingEdges,attackerId=%d,attackerName=%s,cities=%d,count=%d%n",
                attacker.nationId(), csvSafe(participantName(response, attacker.nationId())), attacker.cities(), Math.min(limit, edgeIndexes.length));
        for (int order = 0; order < edgeIndexes.length && order < limit; order++) {
            printEdge(candidateEdges, scenario, response, edgeIndexes[order], true, order + 1);
        }
    }

    private static void printTopIncomingEdges(
            CandidateEdgeTable candidateEdges,
            CompiledScenario scenario,
            BlitzPlanResponse response,
            int defenderIndex,
            int limit
    ) {
        int[] edgeIndexes = matchingEdgeIndexes(candidateEdges, defenderIndex, false);
        sortEdgeIndexesByScore(candidateEdges, edgeIndexes);
        DBNationSnapshot defender = scenario.defender(defenderIndex);
        System.out.printf(Locale.ROOT,
                "topIncomingEdges,defenderId=%d,defenderName=%s,cities=%d,count=%d%n",
                defender.nationId(), csvSafe(participantName(response, defender.nationId())), defender.cities(), Math.min(limit, edgeIndexes.length));
        for (int order = 0; order < edgeIndexes.length && order < limit; order++) {
            printEdge(candidateEdges, scenario, response, edgeIndexes[order], false, order + 1);
        }
    }

    private static void printEdge(
            CandidateEdgeTable candidateEdges,
            CompiledScenario scenario,
            BlitzPlanResponse response,
            int edgeIndex,
            boolean outgoing,
            int rank
    ) {
        int attackerIndex = candidateEdges.attackerIndex(edgeIndex);
        int defenderIndex = candidateEdges.defenderIndex(edgeIndex);
        int attackerNationId = scenario.attackerNationId(attackerIndex);
        int defenderNationId = scenario.defenderNationId(defenderIndex);
        System.out.printf(Locale.ROOT,
                "edge,rank=%d,attackerId=%d,attackerName=%s,defenderId=%d,defenderName=%s,score=%.3f,immediateHarm=%.3f,selfExposure=%.3f,controlLeverage=%.3f,futureWarLeverage=%.3f,counterRisk=%.3f,assigned=%s,direction=%s%n",
                rank,
                attackerNationId,
                csvSafe(participantName(response, attackerNationId)),
                defenderNationId,
                csvSafe(participantName(response, defenderNationId)),
                candidateEdges.scalarScore(edgeIndex),
                candidateEdges.retainsImmediateHarm() ? candidateEdges.immediateHarm(edgeIndex) : 0f,
                candidateEdges.retainsSelfExposure() ? candidateEdges.selfExposure(edgeIndex) : 0f,
                candidateEdges.retainsControlLeverage() ? candidateEdges.controlLeverage(edgeIndex) : 0f,
                candidateEdges.retainsFutureWarLeverage() ? candidateEdges.futureWarLeverage(edgeIndex) : 0f,
                candidateEdges.counterRisk(edgeIndex),
                assignmentContains(response, attackerNationId, defenderNationId),
                outgoing ? "outgoing" : "incoming");
    }

    private static int[] matchingEdgeIndexes(CandidateEdgeTable candidateEdges, int index, boolean matchAttacker) {
        int count = 0;
        for (int edgeIndex = 0; edgeIndex < candidateEdges.edgeCount(); edgeIndex++) {
            if ((matchAttacker ? candidateEdges.attackerIndex(edgeIndex) : candidateEdges.defenderIndex(edgeIndex)) == index) {
                count++;
            }
        }
        int[] matches = new int[count];
        int cursor = 0;
        for (int edgeIndex = 0; edgeIndex < candidateEdges.edgeCount(); edgeIndex++) {
            if ((matchAttacker ? candidateEdges.attackerIndex(edgeIndex) : candidateEdges.defenderIndex(edgeIndex)) == index) {
                matches[cursor++] = edgeIndex;
            }
        }
        return matches;
    }

    private static void sortEdgeIndexesByScore(CandidateEdgeTable candidateEdges, int[] edgeIndexes) {
        for (int i = 1; i < edgeIndexes.length; i++) {
            int cursor = i;
            while (cursor > 0 && candidateEdges.scalarScore(edgeIndexes[cursor]) > candidateEdges.scalarScore(edgeIndexes[cursor - 1])) {
                int swap = edgeIndexes[cursor - 1];
                edgeIndexes[cursor - 1] = edgeIndexes[cursor];
                edgeIndexes[cursor] = swap;
                cursor--;
            }
        }
    }

    private static boolean assignmentContains(BlitzPlanResponse response, int attackerNationId, int defenderNationId) {
        int attackerParticipant = participantIndex(response, attackerNationId);
        int defenderParticipant = participantIndex(response, defenderNationId);
        if (attackerParticipant < 0 || defenderParticipant < 0) {
            return false;
        }
        int[] pairs = response.assignmentPairs();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (pairs[index] == attackerParticipant && pairs[index + 1] == defenderParticipant) {
                return true;
            }
        }
        return false;
    }

    private static Object invokeAccessor(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static int attackerIndexByNationId(CompiledScenario scenario, int nationId) {
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            if (scenario.attackerNationId(attackerIndex) == nationId) {
                return attackerIndex;
            }
        }
        return -1;
    }

    private static int defenderIndexByNationId(CompiledScenario scenario, int nationId) {
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            if (scenario.defenderNationId(defenderIndex) == nationId) {
                return defenderIndex;
            }
        }
        return -1;
    }

    private static int assignedOffenses(BlitzPlanResponse response, int nationId) {
        int participantIndex = participantIndex(response, nationId);
        if (participantIndex < 0) {
            return 0;
        }
        int count = 0;
        int[] pairs = response.assignmentPairs();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (pairs[index] == participantIndex) {
                count++;
            }
        }
        return count;
    }

    private static int assignedDefenses(BlitzPlanResponse response, int nationId) {
        int participantIndex = participantIndex(response, nationId);
        if (participantIndex < 0) {
            return 0;
        }
        int count = 0;
        int[] pairs = response.assignmentPairs();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (pairs[index + 1] == participantIndex) {
                count++;
            }
        }
        return count;
    }

    private static int participantIndex(BlitzPlanResponse response, int nationId) {
        int[] participantIds = response.participantIds();
        for (int index = 0; index < participantIds.length; index++) {
            if (participantIds[index] == nationId) {
                return index;
            }
        }
        return -1;
    }

    private static String participantName(BlitzPlanResponse response, int nationId) {
        int participantIndex = participantIndex(response, nationId);
        if (participantIndex < 0) {
            return "";
        }
        return response.participantNames()[participantIndex];
    }

    private static StrategicObjective objectiveForRequest(BlitzPlanRequest request) {
        Integer ordinal = request.objectiveOrdinal();
        if (ordinal == null) {
            return BlitzObjective.defaultObjective().objective();
        }
        return BlitzObjective.values()[ordinal].objective();
    }

    private static SimTuning tuningForRequest(BlitzPlanRequest request) {
        SidePlannerSettings plannerSettings = SidePolicy.legacy(objectiveForRequest(request)).planner();
        SimTuning defaults = SimTuning.defaults();
        return new SimTuning(
            defaults.intraTurnPasses(),
            turn1DeclarePolicyForRequest(request),
            defaults.wartimeActivityUplift(),
            defaults.activityActThreshold(),
            defaults.policyCooldownTurns(),
            defaults.localSearchBudgetMs(),
            defaults.localSearchMaxIterations(),
            plannerSettings.candidatesPerAttacker(),
            defaults.beigeTurnsOnDefeat(),
            defaults.stateResolutionMode(),
            request.stochasticSeed(),
            defaults.stochasticSampleCount()
        );
    }

    private static Turn1DeclarePolicy turn1DeclarePolicyForRequest(BlitzPlanRequest request) {
        Integer ordinal = request.turn1DeclarePolicyOrdinal();
        if (ordinal == null) {
            return SimTuning.DEFAULT_TURN1_DECLARE_POLICY;
        }
        return Turn1DeclarePolicy.values()[ordinal];
    }

    private static void printProfile(PlannerProfiler.ProfileSnapshot snapshot) {
        System.out.println("stage,scope,calls,totalMs,maxMs,counters");
        for (PlannerProfiler.Scope scope : PlannerProfiler.Scope.values()) {
            PlannerProfiler.ScopeStats stats = snapshot.stats(scope);
            if (stats.calls() == 0L) {
                continue;
            }
            String counters = stats.counters().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(";"));
            System.out.printf(Locale.ROOT,
                    "stage,%s,%d,%.3f,%.3f,%s%n",
                    scope.name(),
                    stats.calls(),
                    stats.totalMillis(),
                    stats.maxMillis(),
                    counters);
        }
    }

    private static BlitzPlanResponse invokeRunBlitzPlan(
            WarDB warDb,
            BlitzPlanRequest request,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders
    ) {
        try {
            Method method = SimEndpoints.class.getDeclaredMethod(
                    "runBlitzPlan",
                    WarDB.class,
                    BlitzPlanRequest.class,
                    Collection.class,
                    Collection.class
            );
            method.setAccessible(true);
            return (BlitzPlanResponse) method.invoke(null, warDb, request, attackers, defenders);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke SimEndpoints.runBlitzPlan", e);
        }
    }

    private static BlitzPlanRequest request(Config config, Collection<DBNation> attackers, Collection<DBNation> defenders) {
        List<BlitzDraftEdit> edits = new ArrayList<>(attackers.size() + defenders.size());
        if (config.routeOverrides()) {
            for (DBNation nation : combined(attackers, defenders)) {
                edits.add(routeOverrideEdit(nation, config));
            }
        }
        return new BlitzPlanRequest(
                ids(attackers),
                ids(defenders),
                edits.toArray(BlitzDraftEdit[]::new),
                new BlitzPlannedWar[0],
                BlitzSideMode.ATTACKERS_ONLY.ordinal(),
                BlitzRebuyMode.FULL_REBUYS.ordinal(),
                config.objectiveOrdinal(),
                null,
                config.horizonTurns(),
                config.includeExistingWars(),
                true,
                1L,
                null,
                new int[0],
                true,
                config.captureTrace()
        );
    }

    private static BlitzDraftEdit routeOverrideEdit(DBNation nation, Config config) {
        return new BlitzDraftEdit(
                nation.getNation_id(),
                config.forceActive(),
                null,
                config.avgInfraCents(),
                unitCountsForMmr(nation, config.unitMmr()),
                null,
                0L,
                0L,
                0,
                0,
                null,
                config.clearBeige(),
                config.clearVm()
        );
    }

    private static int[] unitCountsForMmr(DBNation nation, String mmrValue) {
        if (mmrValue == null || mmrValue.isBlank()) {
            return null;
        }
        BlitzMilitaryRules rules = BlitzMilitaryRules.instance();
        int[] units = new int[MilitaryUnit.values.length];
        for (MilitaryUnit unit : MilitaryUnit.values) {
            units[unit.ordinal()] = nation.getUnits(unit);
        }
        int[] slots = rules.mmrUnitOrdinals();
        int[] parts = mmrParts(mmrValue, slots.length);
        int researchBits = nation.getResearchBits(null);
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
            int unitOrdinal = slots[slotIndex];
            int maxMmr = Math.max(1, rules.mmrMaxByUnitOrdinal()[unitOrdinal]);
            int cap = unitCapForMmr(nation.getCities(), researchBits, unitOrdinal, rules);
            units[unitOrdinal] = (int) Math.round(cap * (parts[slotIndex] / (double) maxMmr));
        }
        return units;
    }

    private static int unitCapForMmr(int cities, int researchBits, int unitOrdinal, BlitzMilitaryRules rules) {
        int maxMmr = rules.mmrMaxByUnitOrdinal()[unitOrdinal];
        int capPerBuilding = rules.capacityPerBuildingByUnitOrdinal()[unitOrdinal];
        return (maxMmr * capPerBuilding * cities) + unitCapacityResearchBonus(unitOrdinal, researchBits, rules);
    }

    private static int unitCapacityResearchBonus(int unitOrdinal, int researchBits, BlitzMilitaryRules rules) {
        int researchOrdinal = rules.capacityResearchOrdinalByUnitOrdinal()[unitOrdinal];
        if (researchOrdinal < 0) {
            return 0;
        }
        int bonus = rules.capacityResearchBonusByUnitOrdinal()[unitOrdinal];
        return bonus * readResearchBits(researchBits, researchOrdinal, rules.bitsPerResearchSlot());
    }

    private static int readResearchBits(int bits, int researchOrdinal, int bitsPerSlot) {
        int shift = researchOrdinal * bitsPerSlot;
        if (shift < 0 || shift >= Integer.SIZE) {
            return 0;
        }
        return (bits >>> shift) & ((1 << bitsPerSlot) - 1);
    }

    private static int[] mmrParts(String value, int slots) {
        int[] parts = new int[slots];
        String normalized = value.trim();
        for (int index = 0; index < slots; index++) {
            if (index >= normalized.length()) {
                parts[index] = 0;
                continue;
            }
            char c = normalized.charAt(index);
            parts[index] = Character.isDigit(c) ? Character.digit(c, 10) : 0;
        }
        return parts;
    }

    private static List<DBNation> combined(Collection<DBNation> attackers, Collection<DBNation> defenders) {
        ArrayList<DBNation> result = new ArrayList<>(attackers.size() + defenders.size());
        result.addAll(attackers);
        result.addAll(defenders);
        result.sort(Comparator.comparingInt(DBNation::getNation_id));
        return result;
    }

    private static String ids(Collection<DBNation> nations) {
        return nations.stream()
                .map(DBNation::getNation_id)
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static List<String> csvValues(String configured) {
        return java.util.Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static Set<DBNation> resolveAllianceMembers(NationDB nationDb, List<String> tokens, String optionName) {
        Set<Integer> allianceIds = new LinkedHashSet<>();
        for (String token : tokens) {
            DBAlliance alliance = resolveAlliance(nationDb, token);
            if (alliance == null) {
                throw new IllegalArgumentException("Unknown alliance in " + optionName + ": " + token);
            }
            allianceIds.add(alliance.getAlliance_id());
        }
        Set<DBNation> nations = new LinkedHashSet<>();
        for (DBNation nation : new link.locutus.discord.pnw.AllianceList(allianceIds).getNations(nationDb, false, 0, true)) {
            nations.add(nation);
        }
        return nations;
    }

    private static DBAlliance resolveAlliance(NationDB nationDb, String token) {
        if (token.chars().allMatch(Character::isDigit)) {
            return nationDb.getAlliance(Integer.parseInt(token));
        }
        DBAlliance direct = nationDb.getAllianceByName(token);
        if (direct != null) {
            return direct;
        }
        String normalized = token.replace('_', ' ').replace('+', ' ').trim();
        if (!normalized.equals(token)) {
            return nationDb.getAllianceByName(normalized);
        }
        return null;
    }

    private static long gzipLength(byte[] jsonBytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(jsonBytes.length);
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(out, 65_536)) {
                gzipOut.write(jsonBytes);
            }
            return out.size();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to gzip benchmark payload", e);
        }
    }

    private record Config(
            String attackerAlliances,
            String defenderAlliances,
            int horizonTurns,
            boolean captureTrace,
            boolean includeExistingWars,
            boolean routeOverrides,
            boolean printAssignmentSummary,
            boolean printCandidateSummary,
            Boolean forceActive,
            Boolean clearBeige,
            Boolean clearVm,
            Integer avgInfraCents,
            String unitMmr,
            Integer objectiveOrdinal
    ) {
        static Config parse(String[] args) {
            return new Config(
                    option(args, "attackerAlliances", DEFAULT_ATTACKER_ALLIANCES),
                    option(args, "defenderAlliances", DEFAULT_DEFENDER_ALLIANCES),
                    optionInt(args, "horizonTurns", 72),
                    optionBoolean(args, "captureTrace", true),
                    optionBoolean(args, "includeExistingWars", false),
                    optionBoolean(args, "routeOverrides", true),
                        optionBoolean(args, "printAssignmentSummary", true),
                    optionBoolean(args, "printCandidateSummary", true),
                    optionNullableBoolean(args, "forceActive", true),
                    optionNullableBoolean(args, "clearBeige", true),
                    optionNullableBoolean(args, "clearVm", true),
                    optionInt(args, "avgInfraCents", 250_000),
                    option(args, "unitMmr", "5553"),
                    optionNullableInt(args, "objectiveOrdinal", 0)
            );
        }
    }

                private record ParticipantAssignmentRow(
                    int nationId,
                    String name,
                    int cityCount,
                    int freeOff,
                    int freeDef,
                    int maxOff,
                    int offensiveAssignments,
                    int defensiveAssignments
                ) {
                private static final Comparator<ParticipantAssignmentRow> HIGH_CITY_ORDER = Comparator
                    .comparingInt(ParticipantAssignmentRow::cityCount).reversed()
                    .thenComparingInt(ParticipantAssignmentRow::offensiveAssignments)
                    .thenComparingInt(ParticipantAssignmentRow::nationId);
                }

                private record AttackerCandidateRow(
                    int nationId,
                    String name,
                    int cityCount,
                    int outgoingCandidates,
                        double maxScore,
                        double avgScore,
                    int assignedOffenses,
                    int freeOff
                ) {
                private static final Comparator<AttackerCandidateRow> ORDER = Comparator
                    .comparingInt(AttackerCandidateRow::cityCount).reversed()
                    .thenComparingInt(AttackerCandidateRow::assignedOffenses)
                    .thenComparingInt(AttackerCandidateRow::nationId);
                }

                private record DefenderCandidateRow(
                    int nationId,
                    String name,
                    int cityCount,
                    int incomingCandidates,
                        double maxScore,
                        double avgScore,
                    int assignedDefenses,
                    int freeDef
                ) {
                private static final Comparator<DefenderCandidateRow> ORDER = Comparator
                    .comparingInt(DefenderCandidateRow::cityCount).reversed()
                    .thenComparingInt(DefenderCandidateRow::assignedDefenses)
                    .thenComparingInt(DefenderCandidateRow::nationId);
                }

    private static String option(String[] args, String name, String defaultValue) {
        String prefix = "--" + name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }

    private static int optionInt(String[] args, String name, int defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static Integer optionNullableInt(String[] args, String name, Integer defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? defaultValue : Integer.parseInt(value);
    }

    private static boolean optionBoolean(String[] args, String name, boolean defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private static Boolean optionNullableBoolean(String[] args, String name, Boolean defaultValue) {
        String value = option(args, name, null);
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    private record LiveDatabases(
            Field instanceField,
            Locutus previousInstance,
            NationDB nationDb,
            WarDB warDb
    ) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            Exception failure = null;
            try {
                if (warDb != null) {
                    warDb.close();
                }
            } catch (Exception e) {
                failure = e;
            }
            try {
                if (nationDb != null) {
                    nationDb.close();
                }
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
            }
            try {
                instanceField.set(null, previousInstance);
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static LiveDatabases loadLiveDatabases() throws Exception {
        Field instanceField = Locutus.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Locutus previousInstance = (Locutus) instanceField.get(null);
        NationDB[] nationHolder = new NationDB[1];
        WarDB[] warHolder = new WarDB[1];
        instanceField.set(null, fakeLocutus(nationHolder, warHolder));
        try {
            NationDB nationDb = new NationDB().load();
            nationHolder[0] = nationDb;
            WarDB warDb = new WarDB().load();
            warHolder[0] = warDb;
            return new LiveDatabases(instanceField, previousInstance, nationDb, warDb);
        } catch (Exception e) {
            instanceField.set(null, previousInstance);
            throw e;
        }
    }

    private static Locutus fakeLocutus(NationDB[] nationHolder, WarDB[] warHolder) throws Exception {
        Locutus locutus = (Locutus) allocateWithoutConstructor(Locutus.class);
        Object loader = Proxy.newProxyInstance(
                ILoader.class.getClassLoader(),
                new Class<?>[]{ILoader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNationDB", "getCachedNationDB" -> nationHolder[0];
                    case "getWarDB" -> warHolder[0];
                    case "resolveFully" -> proxy;
                    case "printStacktrace" -> "";
                    default -> defaultValue(method.getReturnType());
                });
        Field loaderField = Locutus.class.getDeclaredField("loader");
        loaderField.setAccessible(true);
        loaderField.set(locutus, loader);
        return locutus;
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
}
