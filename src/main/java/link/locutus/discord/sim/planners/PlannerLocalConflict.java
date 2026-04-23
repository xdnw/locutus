package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.SimClock;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.TeamScoreView;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.ControlFlagDelta;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.RandomSource;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.WarControlRules;
import link.locutus.discord.sim.combat.WarOutcomeMath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PlannerLocalConflict implements TeamScoreView {
    private static final AttackType[] CONVENTIONAL_ATTACK_SEQUENCE = {
        AttackType.GROUND,
        AttackType.AIRSTRIKE_INFRA,
        AttackType.NAVAL
    };
    private static final AttackType[] SPECIALIST_FIRST_ATTACK_SEQUENCE = {
        AttackType.NUKE,
        AttackType.MISSILE,
        AttackType.GROUND,
        AttackType.AIRSTRIKE_INFRA,
        AttackType.NAVAL
    };

    private final SimTuning tuning;
    private final RandomSource randomSource;
    private final ResolutionMode resolutionMode;
    private final PlannerTransitionSemantics transitionSemantics;
    private final AttackScratch attackScratch;
    private final MutableAttackResult attackResult;
    private final Map<Integer, LocalNation> nationsById;
    private final Map<Integer, LocalWar> warsById;
    private final Map<Long, LocalWar> warsByPair;
    private final LocalNationBuffers nationBuffers;
    private final LocalWarBuffers warBuffers;
    private final Deque<Mark> markStack;
    private int currentTurn;
    private int nextWarId = 1_000_000;

    enum ControlOwner {
        NONE,
        ATTACKER,
        DEFENDER
    }

    private PlannerLocalConflict(
            SimTuning tuning,
            Map<Integer, LocalNation> nationsById,
            LocalNationBuffers nationBuffers,
            int currentTurn,
            PlannerTransitionSemantics transitionSemantics,
            RandomSource randomSource
    ) {
        this.tuning = tuning;
        this.randomSource = randomSource;
        this.resolutionMode = requireStateResolutionMode(tuning);
        this.transitionSemantics = transitionSemantics;
        this.attackScratch = new AttackScratch();
        this.attackResult = new MutableAttackResult();
        this.nationsById = nationsById;
        this.nationBuffers = nationBuffers;
        this.warsById = new LinkedHashMap<>();
        this.warsByPair = new LinkedHashMap<>();
        this.warBuffers = new LocalWarBuffers();
        this.markStack = new ArrayDeque<>();
        this.currentTurn = currentTurn;
    }

    static PlannerLocalConflict create(
            OverrideSet overrides,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            SimTuning tuning
    ) {
        return create(overrides, attackers, defenders, tuning, PlannerTransitionSemantics.NONE);
    }

    static PlannerLocalConflict create(
            OverrideSet overrides,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders,
            SimTuning tuning,
            PlannerTransitionSemantics transitionSemantics
    ) {
        return createFromOrderedSnapshots(
                overrides,
                orderedUniqueNations(attackers, defenders),
                List.of(),
            0,
                tuning,
                transitionSemantics
        );
    }

    static PlannerLocalConflict create(
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            SimTuning tuning
    ) {
        return create(overrides, nations, tuning, PlannerTransitionSemantics.NONE);
    }

    static PlannerLocalConflict create(
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            SimTuning tuning,
            PlannerTransitionSemantics transitionSemantics
    ) {
        return createFromOrderedSnapshots(
                overrides,
                orderedUniqueNations(nations, List.of()),
                List.of(),
            0,
                tuning,
                transitionSemantics
        );
    }

    static PlannerLocalConflict createWithActiveWars(
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Collection<PlannerProjectedWar> activeWars,
            SimTuning tuning
    ) {
        return createWithActiveWars(overrides, nations, activeWars, 0, tuning, PlannerTransitionSemantics.NONE);
    }

    static PlannerLocalConflict createWithActiveWars(
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Collection<PlannerProjectedWar> activeWars,
            int currentTurn,
            SimTuning tuning,
            PlannerTransitionSemantics transitionSemantics
    ) {
        List<DBNationSnapshot> ordered = orderedUniqueNations(nations, List.of());
        List<DBNationSnapshot> baseSnapshots = stripProjectedWarOverlays(ordered, activeWars);
        return createFromOrderedSnapshots(overrides, baseSnapshots, activeWars, currentTurn, tuning, transitionSemantics);
    }

    static PlannerLocalConflict createWithActiveWars(
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Collection<PlannerProjectedWar> activeWars,
            SimTuning tuning,
            PlannerTransitionSemantics transitionSemantics
    ) {
        return createWithActiveWars(overrides, nations, activeWars, 0, tuning, transitionSemantics);
    }

    private static List<DBNationSnapshot> stripProjectedWarOverlays(
            List<DBNationSnapshot> snapshots,
            Collection<PlannerProjectedWar> activeWars
    ) {
        if (activeWars.isEmpty()) {
            return snapshots;
        }
        Map<Integer, DBNationSnapshot> byId = new LinkedHashMap<>(snapshots.size());
        for (DBNationSnapshot snapshot : snapshots) {
            byId.put(snapshot.nationId(), snapshot);
        }
        for (PlannerProjectedWar war : activeWars) {
            removeProjectedWarOverlay(byId, war.attackerNationId(), war.defenderNationId(), true);
            removeProjectedWarOverlay(byId, war.defenderNationId(), war.attackerNationId(), false);
        }
        return new ArrayList<>(byId.values());
    }

    private static void removeProjectedWarOverlay(
            Map<Integer, DBNationSnapshot> snapshotsById,
            int nationId,
            int opponentId,
            boolean offensiveSide
    ) {
        DBNationSnapshot snapshot = snapshotsById.get(nationId);
        if (snapshot == null) {
            return;
        }
        LinkedHashSet<Integer> activeOpponents = new LinkedHashSet<>(snapshot.activeOpponentNationIds());
        if (!activeOpponents.remove(opponentId)) {
            // Idempotent guard: if the overlay is already stripped, keep snapshot unchanged.
            return;
        }
        DBNationSnapshot.Builder builder = snapshot.toBuilder()
                .currentOffensiveWars(Math.max(0, snapshot.currentOffensiveWars() + (offensiveSide ? -1 : 0)))
                .currentDefensiveWars(Math.max(0, snapshot.currentDefensiveWars() + (offensiveSide ? 0 : -1)))
                .activeOpponentNationIds(activeOpponents);
        snapshotsById.put(nationId, builder.build());
    }

    private static PlannerLocalConflict createFromOrderedSnapshots(
            OverrideSet overrides,
            List<DBNationSnapshot> orderedSnapshots,
            Collection<PlannerProjectedWar> activeWars,
            int currentTurn,
            SimTuning tuning,
            PlannerTransitionSemantics transitionSemantics
    ) {
        PlannerTransitionSemantics effectiveTransitionSemantics = transitionSemantics == null
                ? PlannerTransitionSemantics.NONE
                : transitionSemantics;
        LocalNationBuffers nationBuffers = LocalNationBuffers.fromSnapshots(orderedSnapshots, overrides);
        Map<Integer, LocalNation> localNations = new LinkedHashMap<>();
        for (int index = 0; index < orderedSnapshots.size(); index++) {
            DBNationSnapshot snapshot = orderedSnapshots.get(index);
            localNations.put(snapshot.nationId(), LocalNation.of(snapshot, nationBuffers, index, currentTurn));
        }
        RandomSource randomSource = tuning.stateResolutionMode() == ResolutionMode.STOCHASTIC
                ? RandomSource.splittable(tuning.stochasticSeed())
                : null;
        PlannerLocalConflict conflict = new PlannerLocalConflict(
                tuning,
                localNations,
            nationBuffers,
            currentTurn,
                effectiveTransitionSemantics,
                randomSource
        );
        conflict.seedProjectedWars(activeWars);
        return conflict;
    }

    private static ResolutionMode requireStateResolutionMode(SimTuning tuning) {
        ResolutionMode mode = tuning.stateResolutionMode();
        if (mode == ResolutionMode.DETERMINISTIC_EV) {
            throw new IllegalStateException(
                    "DETERMINISTIC_EV is scoring-only and cannot drive planner local state transitions"
            );
        }
        return mode;
    }

    private static List<DBNationSnapshot> orderedUniqueNations(
            Collection<DBNationSnapshot> primary,
            Collection<DBNationSnapshot> secondary
    ) {
        LinkedHashMap<Integer, DBNationSnapshot> byId = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : primary) {
            byId.put(snapshot.nationId(), snapshot);
        }
        for (DBNationSnapshot snapshot : secondary) {
            byId.putIfAbsent(snapshot.nationId(), snapshot);
        }
        return new ArrayList<>(byId.values());
    }

    Mark mark() {
        Mark mark = new Mark(captureSnapshot());
        markStack.push(mark);
        return mark;
    }

    void apply(Mark mark) {
        requireTopMark(mark);
        markStack.pop();
    }

    void rollback(Mark mark) {
        requireTopMark(mark);
        markStack.pop();
        restoreSnapshot(mark.snapshot());
    }

    private void requireTopMark(Mark mark) {
        if (mark == null) {
            throw new IllegalArgumentException("mark cannot be null");
        }
        Mark current = markStack.peek();
        if (current == null) {
            throw new IllegalStateException("No active mark to apply/rollback");
        }
        if (current != mark) {
            throw new IllegalStateException("Marks must be applied/rolled back in LIFO order");
        }
    }

    private ConflictSnapshot captureSnapshot() {
        Map<Integer, LocalNationScalarSnapshot> nationScalars = new LinkedHashMap<>(nationsById.size());
        for (LocalNation nation : nationsById.values()) {
            nationScalars.put(nation.nationId(), nation.scalarSnapshot());
        }

        List<LocalWarRecord> warRecords = new ArrayList<>(warsById.size());
        for (LocalWar war : warsById.values()) {
            warRecords.add(new LocalWarRecord(
                    war.warId(),
                    war.warIndex(),
                    war.attacker.nationId(),
                    war.defender.nationId()
            ));
        }

        return new ConflictSnapshot(
                nextWarId,
            currentTurn,
                nationBuffers.snapshot(),
                nationScalars,
                warBuffers.snapshot(),
                warRecords
        );
    }

    private void restoreSnapshot(ConflictSnapshot snapshot) {
        nextWarId = snapshot.nextWarId();
        currentTurn = snapshot.currentTurn();
        nationBuffers.restore(snapshot.nationBufferSnapshot());
        for (Map.Entry<Integer, LocalNationScalarSnapshot> entry : snapshot.nationScalarsById().entrySet()) {
            LocalNation nation = nationsById.get(entry.getKey());
            if (nation != null) {
                nation.restoreScalarSnapshot(entry.getValue());
            }
        }

        warBuffers.restore(snapshot.warBufferSnapshot());
        warsById.clear();
        warsByPair.clear();
        for (LocalWarRecord warRecord : snapshot.warRecords()) {
            LocalNation attacker = requireNation(warRecord.attackerNationId());
            LocalNation defender = requireNation(warRecord.defenderNationId());
            LocalWar war = new LocalWar(warRecord.warId(), warRecord.warIndex(), attacker, defender, warBuffers);
            warsById.put(warRecord.warId(), war);
            warsByPair.put(pairKey(warRecord.attackerNationId(), warRecord.defenderNationId()), war);
        }
    }

    void applyAssignmentHorizon(Map<Integer, List<Integer>> assignment, int horizonTurns) {
        if (assignment.isEmpty()) {
            advanceTurns(horizonTurns);
            return;
        }
        Map<Long, LocalWar> declaredWars = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerId = entry.getKey();
            for (int defenderId : entry.getValue()) {
                LocalWar war = declareWar(attackerId, defenderId);
                declaredWars.put(pairKey(attackerId, defenderId), war);
            }
        }
        int turns = Math.max(1, horizonTurns);
        simulateDeclaredWarOpenings(assignment, declaredWars);
        for (int turn = 1; turn < turns; turn++) {
            advanceTurn();
            simulateDeclaredWarOpenings(assignment, declaredWars);
        }
    }

    void applyAssignmentOpenings(Map<Integer, List<Integer>> assignment) {
        if (assignment.isEmpty()) {
            return;
        }
        Map<Long, LocalWar> declaredWars = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerId = entry.getKey();
            for (int defenderId : entry.getValue()) {
                LocalWar war = declareWar(attackerId, defenderId);
                declaredWars.put(pairKey(attackerId, defenderId), war);
            }
        }
        simulateDeclaredWarOpenings(assignment, declaredWars);
    }

    void simulateDeclaredWar(int attackerNationId, int defenderNationId, WarType warType, int horizonTurns) {
        simulateDeclaredWar(attackerNationId, defenderNationId, warType, horizonTurns, PlannerExactValidatorScripts.DEFAULT);
    }

    void simulateDeclaredWar(
            int attackerNationId,
            int defenderNationId,
            WarType warType,
            int horizonTurns,
            PlannerExactValidatorScripts scripts
    ) {
        simulateDeclaredWarInternal(
                attackerNationId,
                defenderNationId,
                warType,
                horizonTurns,
                scripts,
                null,
                0
        );
    }

            void simulateDeclaredWar(
                int attackerNationId,
                int defenderNationId,
                WarType warType,
                int horizonTurns,
                PlannerCoordinationPolicy coordinationPolicy
            ) {
            PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
                ? PlannerCoordinationPolicy.NONE
                : coordinationPolicy;
            simulateDeclaredWar(
                attackerNationId,
                defenderNationId,
                warType,
                horizonTurns,
                effectiveCoordination.applyToDefaultScripts()
            );
            }

            void simulateDeclaredWar(
                int attackerNationId,
                int defenderNationId,
                WarType warType,
                int horizonTurns,
                PlannerExactValidatorScripts scripts,
                PlannerCoordinationPolicy coordinationPolicy
            ) {
            PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
                ? PlannerCoordinationPolicy.NONE
                : coordinationPolicy;
            simulateDeclaredWar(
                attackerNationId,
                defenderNationId,
                warType,
                horizonTurns,
                effectiveCoordination.applyTo(scripts)
            );
            }

    void simulateDeclaredWar(
            int attackerNationId,
            int defenderNationId,
            WarType warType,
            int horizonTurns,
            PlannerExactValidatorScripts scripts,
            TeamScoreObjective objective,
            int attackerTeamId
    ) {
        simulateDeclaredWarInternal(
                attackerNationId,
                defenderNationId,
                warType,
                horizonTurns,
                scripts,
                objective,
                attackerTeamId
        );
    }

            void simulateDeclaredWar(
                int attackerNationId,
                int defenderNationId,
                WarType warType,
                int horizonTurns,
                PlannerCoordinationPolicy coordinationPolicy,
                TeamScoreObjective objective,
                int attackerTeamId
            ) {
            PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
                ? PlannerCoordinationPolicy.NONE
                : coordinationPolicy;
            simulateDeclaredWar(
                attackerNationId,
                defenderNationId,
                warType,
                horizonTurns,
                effectiveCoordination.applyToDefaultScripts(),
                objective,
                attackerTeamId
            );
            }

            void simulateDeclaredWar(
                int attackerNationId,
                int defenderNationId,
                WarType warType,
                int horizonTurns,
                PlannerExactValidatorScripts scripts,
                PlannerCoordinationPolicy coordinationPolicy,
                TeamScoreObjective objective,
                int attackerTeamId
            ) {
            PlannerCoordinationPolicy effectiveCoordination = coordinationPolicy == null
                ? PlannerCoordinationPolicy.NONE
                : coordinationPolicy;
            simulateDeclaredWar(
                attackerNationId,
                defenderNationId,
                warType,
                horizonTurns,
                effectiveCoordination.applyTo(scripts),
                objective,
                attackerTeamId
            );
            }

    private void simulateDeclaredWarInternal(
            int attackerNationId,
            int defenderNationId,
            WarType warType,
            int horizonTurns,
            PlannerExactValidatorScripts scripts,
            TeamScoreObjective objective,
            int attackerTeamId
    ) {
        PlannerExactValidatorScripts effectiveScripts = scripts == null
                ? PlannerExactValidatorScripts.DEFAULT
                : scripts;
        if (!effectiveScripts.declareWarScript()) {
            return;
        }
        LocalWar war = declareWar(attackerNationId, defenderNationId, warType);
        int turns = Math.max(1, horizonTurns);
        for (int turn = 0; turn < turns; turn++) {
            if (turn > 0) {
                advanceTurn();
            }
            if (!war.isActive()) {
                return;
            }
            if (effectiveScripts.openerSequenceScript()) {
                if (objective == null) {
                    simulateOpeningAttacks(
                            war,
                            effectiveScripts.allowedAttackTypes(),
                            effectiveScripts.attackSequenceProfile(),
                            effectiveScripts.mapReserveScript() ? effectiveScripts.mapReserveFloor() : 0,
                            turns - turn,
                            effectiveScripts.idleWaitScript()
                    );
                } else {
                    simulateGreedyOpeningAttacks(
                            war,
                            effectiveScripts.allowedAttackTypes(),
                            effectiveScripts.mapReserveScript() ? effectiveScripts.mapReserveFloor() : 0,
                            objective,
                            attackerTeamId
                    );
                }
            }
            if (!war.isActive()) {
                return;
            }
            if (effectiveScripts.followUpAttackScript()) {
                if (objective == null) {
                    simulateFollowUpAttack(
                            war,
                            effectiveScripts.allowedAttackTypes(),
                            effectiveScripts.attackSequenceProfile(),
                            effectiveScripts.mapReserveScript() ? effectiveScripts.mapReserveFloor() : 0,
                            turns - turn,
                            effectiveScripts.idleWaitScript()
                    );
                } else {
                    simulateGreedyFollowUpAttack(
                            war,
                            effectiveScripts.allowedAttackTypes(),
                            effectiveScripts.mapReserveScript() ? effectiveScripts.mapReserveFloor() : 0,
                            objective,
                            attackerTeamId
                    );
                }
            }
            if (effectiveScripts.peaceOfferScript()) {
                attemptPeaceClose(war, effectiveScripts.mapReserveFloor());
            }
        }
    }

    PlannerProjectionResult project() {
        List<PlannerProjectedWar> activeWars = new ArrayList<>();
        for (LocalWar war : warsById.values()) {
            if (war.isActive()) {
                activeWars.add(war.project(currentTurn));
            }
        }
        Map<Integer, DBNationSnapshot> projected = new LinkedHashMap<>(nationsById.size());
        for (LocalNation nation : nationsById.values()) {
            projected.put(nation.nationId(), nation.toSnapshot(activeWars));
        }
        Map<Integer, PlannerCityInfraOverlay> cityInfraOverlays =
                LocalNationBuffers.exportCityInfraOverlays(nationsById.values());
        return new PlannerProjectionResult(projected, activeWars, cityInfraOverlays);
    }

    @Override
    public void forEachNation(NationScoreConsumer consumer) {
        for (LocalNation nation : nationsById.values()) {
            consumer.accept(nation.nationId(), nation.teamId(), nation.score());
        }
    }

    private void seedProjectedWars(Collection<PlannerProjectedWar> activeWars) {
        for (PlannerProjectedWar projectedWar : activeWars) {
            long pairKey = projectedWar.pairKey();
            if (warsByPair.containsKey(pairKey)) {
                throw new IllegalArgumentException("Duplicate projected war for pair " + pairKey);
            }
            LocalNation attacker = requireNation(projectedWar.attackerNationId());
            LocalNation defender = requireNation(projectedWar.defenderNationId());
            int warId = nextWarId++;
            int warIndex = warBuffers.allocate(projectedWar);
            LocalWar war = new LocalWar(warId, warIndex, attacker, defender, warBuffers);
            warsById.put(warId, war);
            warsByPair.put(pairKey, war);
        }
    }

    private List<LocalWar> activeWarsForNation(int nationId) {
        ArrayList<LocalWar> activeWars = new ArrayList<>();
        for (LocalWar war : warsById.values()) {
            if (!war.isActive()) {
                continue;
            }
            if (war.attackerNationId() == nationId || war.defenderNationId() == nationId) {
                activeWars.add(war);
            }
        }
        return activeWars;
    }

    private void simulateDeclaredWarOpenings(Map<Integer, List<Integer>> assignment, Map<Long, LocalWar> declaredWars) {
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerId = entry.getKey();
            for (int defenderId : entry.getValue()) {
                LocalWar war = declaredWars.get(pairKey(attackerId, defenderId));
                if (war != null && war.isActive()) {
                    simulateOpeningAttacks(war);
                }
            }
        }
    }

    private LocalWar declareWar(int attackerNationId, int defenderNationId) {
        return declareWar(attackerNationId, defenderNationId, WarType.ORD);
    }

    private LocalWar declareWar(int attackerNationId, int defenderNationId, WarType warType) {
        long pairKey = pairKey(attackerNationId, defenderNationId);
        LocalWar existing = warsByPair.get(pairKey);
        if (existing != null && existing.isActive()) {
            throw new IllegalStateException("Pair " + attackerNationId + " -> " + defenderNationId + " already has an active local war");
        }
        LocalNation attacker = requireNation(attackerNationId);
        LocalNation defender = requireNation(defenderNationId);
        int warId = nextWarId++;
        int warIndex = warBuffers.allocate(warType, currentTurn, LocalWar.INITIAL_MAPS, LocalWar.INITIAL_RESISTANCE);
        LocalWar war = new LocalWar(warId, warIndex, attacker, defender, warBuffers);
        warsById.put(warId, war);
        warsByPair.put(pairKey, war);
        return war;
    }

    private void simulateOpeningAttacks(LocalWar war) {
        simulateOpeningAttacks(
                war,
                java.util.EnumSet.allOf(AttackType.class),
                PlannerExactValidatorScripts.AttackSequenceProfile.CONVENTIONAL,
                0,
                1,
                false
        );
    }

    private void simulateOpeningAttacks(
            LocalWar war,
            Set<AttackType> allowedAttackTypes,
            PlannerExactValidatorScripts.AttackSequenceProfile attackSequenceProfile,
            int mapReserveFloor,
            int turnsRemaining,
            boolean allowIdleWait
    ) {
        if (!war.isActive()) {
            return;
        }
        int mapsAvailable = Math.max(0, war.attackerMapsValue() - Math.max(0, mapReserveFloor));
        if (shouldHoldForSpecialist(war, allowedAttackTypes, attackSequenceProfile, mapReserveFloor, turnsRemaining, allowIdleWait)) {
            return;
        }
        AttackType[] openingSequence = attackSequence(attackSequenceProfile);
        for (AttackType type : openingSequence) {
            if (!allowedAttackTypes.contains(type)) {
                continue;
            }
            if (!war.attacker.canUseAttackType(type)) {
                continue;
            }
            int mapCost = type.getMapUsed();
            if (mapsAvailable < mapCost) {
                continue;
            }
            int possible = mapsAvailable / mapCost;
            for (int i = 0; i < possible && mapsAvailable >= mapCost; i++) {
                if (!war.isActive()) {
                    break;
                }
                resolveAttack(war, type);
                mapsAvailable = Math.max(0, war.attackerMapsValue() - Math.max(0, mapReserveFloor));
            }
            if (!war.isActive()) {
                break;
            }
        }
    }

    private void simulateGreedyOpeningAttacks(
            LocalWar war,
            Set<AttackType> allowedAttackTypes,
            int mapReserveFloor,
            TeamScoreObjective objective,
            int attackerTeamId
    ) {
        if (!war.isActive()) {
            return;
        }
        int reserveFloor = Math.max(0, mapReserveFloor);
        while (war.isActive()) {
            int mapsAvailable = Math.max(0, war.attackerMapsValue() - reserveFloor);
            AttackType bestAttackType = chooseBestAttackType(war, allowedAttackTypes, mapsAvailable, objective, attackerTeamId);
            if (bestAttackType == null) {
                return;
            }
            resolveAttack(war, bestAttackType);
            if (war.attackerMapsValue() <= reserveFloor) {
                return;
            }
        }
    }

    private void simulateFollowUpAttack(
            LocalWar war,
            Set<AttackType> allowedAttackTypes,
            PlannerExactValidatorScripts.AttackSequenceProfile attackSequenceProfile,
            int mapReserveFloor,
            int turnsRemaining,
            boolean allowIdleWait
    ) {
        if (!war.isActive()) {
            return;
        }
        int mapsAvailable = Math.max(0, war.attackerMapsValue() - Math.max(0, mapReserveFloor));
        if (mapsAvailable <= 0) {
            return;
        }
        if (shouldHoldForSpecialist(war, allowedAttackTypes, attackSequenceProfile, mapReserveFloor, turnsRemaining, allowIdleWait)) {
            return;
        }
        AttackType[] followUpPreference = attackSequence(attackSequenceProfile);
        for (AttackType type : followUpPreference) {
            if (!allowedAttackTypes.contains(type)) {
                continue;
            }
            if (!war.attacker.canUseAttackType(type)) {
                continue;
            }
            if (mapsAvailable < type.getMapUsed()) {
                continue;
            }
            resolveAttack(war, type);
            return;
        }
    }

    private static AttackType[] attackSequence(PlannerExactValidatorScripts.AttackSequenceProfile attackSequenceProfile) {
        return attackSequenceProfile == PlannerExactValidatorScripts.AttackSequenceProfile.SPECIALIST_FIRST
                ? SPECIALIST_FIRST_ATTACK_SEQUENCE
                : CONVENTIONAL_ATTACK_SEQUENCE;
    }

    private static boolean shouldHoldForSpecialist(
            LocalWar war,
            Set<AttackType> allowedAttackTypes,
            PlannerExactValidatorScripts.AttackSequenceProfile attackSequenceProfile,
            int mapReserveFloor,
            int turnsRemaining,
            boolean allowIdleWait
    ) {
        if (attackSequenceProfile != PlannerExactValidatorScripts.AttackSequenceProfile.SPECIALIST_FIRST
                || !allowIdleWait
                || turnsRemaining <= 1) {
            return false;
        }
        int reserveFloor = Math.max(0, mapReserveFloor);
        int mapsAvailable = Math.max(0, war.attackerMapsValue() - reserveFloor);
        int reachableMaps = Math.max(
                0,
                Math.min(LocalWar.MAP_CAP, war.attackerMapsValue() + (turnsRemaining - 1)) - reserveFloor
        );
        for (AttackType type : SPECIALIST_FIRST_ATTACK_SEQUENCE) {
            if (!isSpecialistAttack(type)) {
                continue;
            }
            if (!allowedAttackTypes.contains(type) || !war.attacker.canUseAttackType(type)) {
                continue;
            }
            int mapCost = type.getMapUsed();
            if (mapsAvailable >= mapCost) {
                return false;
            }
            if (reachableMaps >= mapCost) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSpecialistAttack(AttackType type) {
        return type == AttackType.MISSILE || type == AttackType.NUKE;
    }

    private void simulateGreedyFollowUpAttack(
            LocalWar war,
            Set<AttackType> allowedAttackTypes,
            int mapReserveFloor,
            TeamScoreObjective objective,
            int attackerTeamId
    ) {
        if (!war.isActive()) {
            return;
        }
        int reserveFloor = Math.max(0, mapReserveFloor);
        int mapsAvailable = Math.max(0, war.attackerMapsValue() - reserveFloor);
        AttackType bestAttackType = chooseBestAttackType(war, allowedAttackTypes, mapsAvailable, objective, attackerTeamId);
        if (bestAttackType == null) {
            return;
        }
        resolveAttack(war, bestAttackType);
    }

    private AttackType chooseBestAttackType(
            LocalWar war,
            Set<AttackType> allowedAttackTypes,
            int mapsAvailable,
            TeamScoreObjective objective,
            int attackerTeamId
    ) {
        AttackType bestAttackType = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AttackType type : allowedAttackTypes) {
            if (!type.canDamage()) {
                continue;
            }
            if (!war.attacker.canUseAttackType(type)) {
                continue;
            }
            int mapCost = type.getMapUsed();
            if (mapCost <= 0 || mapCost > mapsAvailable) {
                continue;
            }
            Mark mark = mark();
            try {
                resolveAttack(war, type);
                double score = objective.scoreTerminal(this, attackerTeamId);
                if (bestAttackType == null
                        || score > bestScore
                        || (score == bestScore && type.ordinal() < bestAttackType.ordinal())) {
                    bestAttackType = type;
                    bestScore = score;
                }
            } finally {
                rollback(mark);
            }
        }
        return bestAttackType;
    }

    private void attemptPeaceClose(LocalWar war, int mapReserveFloor) {
        if (!war.isActive() || war.hasPendingPeaceOffer()) {
            return;
        }
        int reserveFloor = Math.max(0, mapReserveFloor);
        if (war.attackerMapsValue() > reserveFloor) {
            return;
        }
        try {
            war.offerPeace(LocalWar.Side.ATTACKER);
            war.acceptPeace(LocalWar.Side.DEFENDER);
        } catch (IllegalStateException ignored) {
            // Scripts are best-effort; illegal peace transitions are ignored.
        }
    }

    private void resolveAttack(LocalWar war, AttackType attackType) {
        if (!war.attacker.canUseAttackType(attackType)) {
            return;
        }
        if (attackType != AttackType.FORTIFY && war.hasPendingPeaceOffer()) {
            war.setStatus(WarStatus.ACTIVE);
        }
        if (resolutionMode == ResolutionMode.STOCHASTIC) {
            CombatKernel.resolveInto(
                    war,
                    attackType,
                    resolutionMode,
                    randomSource,
                    attackStreamKey(war.attacker.nationId(), war.defender.nationId(), attackType, war.warId()),
                    attackScratch,
                    attackResult
            );
        } else {
            CombatKernel.resolveInto(war, attackType, resolutionMode, attackScratch, attackResult);
        }

        if (attackResult.mapCost() > 0) {
            war.setAttackerMaps(Math.max(0, war.attackerMapsValue() - attackResult.mapCost()));
        }
        war.clearAttackerFortified();

        applyLosses(war.attacker, attackResult.attackerLosses());
        applyLosses(war.defender, attackResult.defenderLosses());
        if (attackResult.infraDestroyed() > 0) {
            war.defender.applyInfraDamage(attackResult.infraDestroyed());
        }
        if (attackResult.attackerResistanceDelta() < 0) {
            war.setAttackerResistance(
                    Math.max(0, war.attackerResistanceValue() - (int) Math.round(-attackResult.attackerResistanceDelta()))
            );
        }
        if (attackResult.defenderResistanceDelta() < 0) {
            war.setDefenderResistance(
                    Math.max(0, war.defenderResistanceValue() - (int) Math.round(-attackResult.defenderResistanceDelta()))
            );
        }
        if (attackResult.loot() > 0) {
            double transferred = war.defender.subtractResource(ResourceType.MONEY, attackResult.loot());
            if (transferred > 0) {
                war.attacker.addResource(ResourceType.MONEY, transferred);
            }
        }

        WarControlRules.reconcileAfterAttack(
                war,
                war.attacker,
                war.defender,
                attackResult.controlDelta(),
                this::activeWarsForNation,
                ignored -> { }
        );
        resolveDefeatIfNeeded(war);
    }

    private void applyLosses(LocalNation nation, int[] losses) {
        if (losses == null) {
            return;
        }
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            int loss = losses[unit.ordinal()];
            if (loss > 0) {
                nation.removeUnits(unit, loss);
            }
        }
    }

    private void resolveDefeatIfNeeded(LocalWar war) {
        if (!war.isActive()) {
            return;
        }
        if (war.attackerResistanceValue() <= 0 || war.defenderResistanceValue() <= 0) {
            boolean attackerLost = war.attackerResistanceValue() <= 0;
            LocalWar.Side winnerSide = attackerLost ? LocalWar.Side.DEFENDER : LocalWar.Side.ATTACKER;
            LocalNation winner = attackerLost ? war.defender : war.attacker;
            LocalNation loser = attackerLost ? war.attacker : war.defender;
            war.setStatus(attackerLost ? WarStatus.DEFENDER_VICTORY : WarStatus.ATTACKER_VICTORY);
            applyVictoryInfraDamage(war, winnerSide, winner, loser);
            loser.beigeTurns = Math.max(loser.beigeTurns, tuning.beigeTurnsOnDefeat());
            applyVictoryNationLoot(war, winnerSide, winner, loser);
        }
    }

    private void applyVictoryInfraDamage(LocalWar war, LocalWar.Side winnerSide, LocalNation winner, LocalNation loser) {
        double infraPercent = WarOutcomeMath.victoryInfraPercent(
                winner.infraAttackModifier(AttackType.VICTORY),
                loser.infraDefendModifier(AttackType.VICTORY),
                war.warType(),
                winnerSide == LocalWar.Side.ATTACKER
        );
        loser.applyVictoryInfraPercent(infraPercent);
    }

    private void applyVictoryNationLoot(LocalWar war, LocalWar.Side winnerSide, LocalNation winner, LocalNation loser) {
        double transferred = WarOutcomeMath.victoryNationLootTransferAmount(
                loser.resource(ResourceType.MONEY),
                winner.looterModifier(false),
                loser.lootModifier(),
                war.warType(),
                winnerSide == LocalWar.Side.ATTACKER
        );
        if (transferred <= 0d) {
            return;
        }
        double debited = loser.subtractResource(ResourceType.MONEY, transferred);
        if (debited > 0d) {
            winner.addResource(ResourceType.MONEY, debited);
        }
    }

    private void advanceTurn() {
        for (LocalNation nation : nationsById.values()) {
            nation.advanceTurn(transitionSemantics.pendingBuys(), transitionSemantics.policyCooldown());
        }
        for (LocalWar war : warsById.values()) {
            if (!war.isActive()) {
                continue;
            }
            war.setAttackerMaps(Math.min(LocalWar.MAP_CAP, war.attackerMapsValue() + 1));
            war.setDefenderMaps(Math.min(LocalWar.MAP_CAP, war.defenderMapsValue() + 1));
            if (war.hasExpiredAtTurnStart(currentTurn)) {
                war.setStatus(WarStatus.EXPIRED);
            }
            resolveDefeatIfNeeded(war);
        }
        currentTurn++;
    }

    private void advanceTurns(int turns) {
        for (int i = 0; i < turns; i++) {
            advanceTurn();
        }
    }

    private LocalNation requireNation(int nationId) {
        LocalNation nation = nationsById.get(nationId);
        if (nation == null) {
            throw new IllegalArgumentException("Unknown nation " + nationId);
        }
        return nation;
    }

    static long pairKey(int attackerId, int defenderId) {
        return ((long) attackerId << 32) | (defenderId & 0xFFFFFFFFL);
    }

    int currentTurn() {
        return currentTurn;
    }

    static final class Mark {
        private final ConflictSnapshot snapshot;

        private Mark(ConflictSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private ConflictSnapshot snapshot() {
            return snapshot;
        }
    }

    private record ConflictSnapshot(
            int nextWarId,
            int currentTurn,
            LocalNationBufferSnapshot nationBufferSnapshot,
            Map<Integer, LocalNationScalarSnapshot> nationScalarsById,
            LocalWarBufferSnapshot warBufferSnapshot,
            List<LocalWarRecord> warRecords
    ) {
    }

    private record LocalNationScalarSnapshot(
            int policyCooldownTurnsRemaining,
            int beigeTurns,
            int dayPhaseTurn,
            double score
    ) {
    }

    private record LocalWarRecord(
            int warId,
            int warIndex,
            int attackerNationId,
            int defenderNationId
    ) {
    }

    private record LocalNationBufferSnapshot(
            int[] unitsFlat,
            int[] unitBuysTodayFlat,
            int[] pendingBuysFlat,
            double[] resourcesFlat,
            double[] cityInfraFlat,
            Map<Integer, Map<Integer, Double>> touchedCityInfraByNationIndex
    ) {
    }

    private record LocalWarBufferSnapshot(
            WarType[] warTypes,
            int[] startTurn,
            WarStatus[] status,
            int[] attackerMaps,
            int[] defenderMaps,
            int[] attackerResistance,
            int[] defenderResistance,
            boolean[] attackerFortified,
            boolean[] defenderFortified,
            int[] groundControlOwner,
            int[] airSuperiorityOwner,
            int[] blockadeOwner,
            int size
    ) {
    }

    private static long attackStreamKey(int attackerId, int defenderId, AttackType type, int warId) {
        long hash = attackerId;
        hash = hash * 31L + defenderId;
        hash = hash * 31L + type.ordinal();
        hash = hash * 31L + warId;
        return hash;
    }

    private static final class LocalNationBuffers implements CombatKernel.PrimitiveNationBuffer {
        private static final int UNIT_STRIDE = MilitaryUnit.values.length;
        private static final int RESOURCE_STRIDE = ResourceType.values.length;

        private final int[] unitsFlat;
        private final int[] unitBuysTodayFlat;
        private final int[] pendingBuysFlat;
        private final double[] resourcesFlat;
        private final double[] cityInfraFlat;
        private final SpecialistCityProfile[] citySpecialistProfilesFlat;
        private final int[] unitBaseOffsets;
        private final int[] resourceBaseOffsets;
        private final int[] cityInfraBaseOffsets;
        private final int[] cityCounts;
        private final Map<Integer, Map<Integer, Double>> touchedCityInfraByNationIndex;

        private LocalNationBuffers(
                int[] unitsFlat,
            int[] unitBuysTodayFlat,
                int[] pendingBuysFlat,
                double[] resourcesFlat,
                double[] cityInfraFlat,
                SpecialistCityProfile[] citySpecialistProfilesFlat,
                int[] unitBaseOffsets,
                int[] resourceBaseOffsets,
                int[] cityInfraBaseOffsets,
                int[] cityCounts,
                Map<Integer, Map<Integer, Double>> touchedCityInfraByNationIndex
        ) {
            this.unitsFlat = unitsFlat;
            this.unitBuysTodayFlat = unitBuysTodayFlat;
            this.pendingBuysFlat = pendingBuysFlat;
            this.resourcesFlat = resourcesFlat;
            this.cityInfraFlat = cityInfraFlat;
            this.citySpecialistProfilesFlat = citySpecialistProfilesFlat;
            this.unitBaseOffsets = unitBaseOffsets;
            this.resourceBaseOffsets = resourceBaseOffsets;
            this.cityInfraBaseOffsets = cityInfraBaseOffsets;
            this.cityCounts = cityCounts;
            this.touchedCityInfraByNationIndex = touchedCityInfraByNationIndex;
        }

        static LocalNationBuffers fromSnapshots(List<DBNationSnapshot> snapshots, OverrideSet overrides) {
            int nationCount = snapshots.size();
            int[] unitBaseOffsets = new int[nationCount];
            int[] resourceBaseOffsets = new int[nationCount];
            int[] cityInfraBaseOffsets = new int[nationCount];
            int[] cityCounts = new int[nationCount];
            double[][] cityInfraByNation = new double[nationCount][];
            int totalCities = 0;
            for (int i = 0; i < nationCount; i++) {
                DBNationSnapshot snapshot = snapshots.get(i);
                double[] cityInfra = snapshot.cityInfra();
                cityInfraByNation[i] = cityInfra;
                cityInfraBaseOffsets[i] = totalCities;
                int cityCount = cityInfra.length;
                cityCounts[i] = cityCount;
                totalCities += cityCount;
                unitBaseOffsets[i] = i * UNIT_STRIDE;
                resourceBaseOffsets[i] = i * RESOURCE_STRIDE;
            }

            int[] unitsFlat = new int[nationCount * UNIT_STRIDE];
            int[] unitBuysTodayFlat = new int[nationCount * UNIT_STRIDE];
            int[] pendingBuysFlat = new int[nationCount * UNIT_STRIDE];
            double[] resourcesFlat = new double[nationCount * RESOURCE_STRIDE];
            double[] cityInfraFlat = new double[totalCities];
            SpecialistCityProfile[] citySpecialistProfilesFlat = new SpecialistCityProfile[totalCities];

            for (int i = 0; i < nationCount; i++) {
                DBNationSnapshot snapshot = snapshots.get(i);
                int unitBase = unitBaseOffsets[i];
                for (MilitaryUnit unit : MilitaryUnit.values) {
                    int count = snapshot.unit(unit);
                    if (SimUnits.isPurchasable(unit)) {
                        count = overrides.overrideUnitCount(snapshot, unit);
                    }
                    unitsFlat[unitBase + unit.ordinal()] = Math.max(0, count);
                    unitBuysTodayFlat[unitBase + unit.ordinal()] = Math.max(0, snapshot.unitsBoughtToday(unit));
                    pendingBuysFlat[unitBase + unit.ordinal()] = Math.max(0, snapshot.pendingBuysNextTurn(unit));
                }

                double[] resources = snapshot.resources();
                int resourceBase = resourceBaseOffsets[i];
                System.arraycopy(resources, 0, resourcesFlat, resourceBase, RESOURCE_STRIDE);

                double[] cityInfra = cityInfraByNation[i];
                int cityBase = cityInfraBaseOffsets[i];
                System.arraycopy(cityInfra, 0, cityInfraFlat, cityBase, cityCounts[i]);
                System.arraycopy(snapshot.citySpecialistProfiles(), 0, citySpecialistProfilesFlat, cityBase, cityCounts[i]);
            }

            return new LocalNationBuffers(
                    unitsFlat,
                    unitBuysTodayFlat,
                    pendingBuysFlat,
                    resourcesFlat,
                    cityInfraFlat,
                    citySpecialistProfilesFlat,
                    unitBaseOffsets,
                    resourceBaseOffsets,
                    cityInfraBaseOffsets,
                    cityCounts,
                    new LinkedHashMap<>()
            );
        }

        static Map<Integer, PlannerCityInfraOverlay> exportCityInfraOverlays(Collection<LocalNation> nations) {
            Map<Integer, PlannerCityInfraOverlay> overlays = new LinkedHashMap<>();
            for (LocalNation nation : nations) {
                Map<Integer, Double> byCity = nation.buffers.touchedCityInfraByNationIndex.get(nation.nationIndex);
                if (byCity == null || byCity.isEmpty()) {
                    continue;
                }
                overlays.put(nation.nationId, new PlannerCityInfraOverlay(nation.nationId, byCity));
            }
            return overlays;
        }

        LocalNationBufferSnapshot snapshot() {
            Map<Integer, Map<Integer, Double>> touchedCopy = new LinkedHashMap<>();
            for (Map.Entry<Integer, Map<Integer, Double>> entry : touchedCityInfraByNationIndex.entrySet()) {
                touchedCopy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
            }
            return new LocalNationBufferSnapshot(
                    java.util.Arrays.copyOf(unitsFlat, unitsFlat.length),
                    java.util.Arrays.copyOf(unitBuysTodayFlat, unitBuysTodayFlat.length),
                    java.util.Arrays.copyOf(pendingBuysFlat, pendingBuysFlat.length),
                    java.util.Arrays.copyOf(resourcesFlat, resourcesFlat.length),
                    java.util.Arrays.copyOf(cityInfraFlat, cityInfraFlat.length),
                    touchedCopy
            );
        }

        void restore(LocalNationBufferSnapshot snapshot) {
            if (snapshot.unitsFlat().length != unitsFlat.length
                    || snapshot.unitBuysTodayFlat().length != unitBuysTodayFlat.length
                    || snapshot.pendingBuysFlat().length != pendingBuysFlat.length
                    || snapshot.resourcesFlat().length != resourcesFlat.length
                    || snapshot.cityInfraFlat().length != cityInfraFlat.length) {
                throw new IllegalStateException("Local nation buffer snapshot shape mismatch");
            }
            System.arraycopy(snapshot.unitsFlat(), 0, unitsFlat, 0, unitsFlat.length);
            System.arraycopy(snapshot.unitBuysTodayFlat(), 0, unitBuysTodayFlat, 0, unitBuysTodayFlat.length);
            System.arraycopy(snapshot.pendingBuysFlat(), 0, pendingBuysFlat, 0, pendingBuysFlat.length);
            System.arraycopy(snapshot.resourcesFlat(), 0, resourcesFlat, 0, resourcesFlat.length);
            System.arraycopy(snapshot.cityInfraFlat(), 0, cityInfraFlat, 0, cityInfraFlat.length);

            touchedCityInfraByNationIndex.clear();
            for (Map.Entry<Integer, Map<Integer, Double>> entry : snapshot.touchedCityInfraByNationIndex().entrySet()) {
                touchedCityInfraByNationIndex.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
            }
        }

        void recordCityInfraOverlay(int nationIndex, int cityIndex, double absoluteInfra) {
            touchedCityInfraByNationIndex
                    .computeIfAbsent(nationIndex, ignored -> new LinkedHashMap<>())
                    .put(cityIndex, absoluteInfra);
        }

        int pendingBuys(int nationIndex, MilitaryUnit unit) {
            return pendingBuysFlat[unitBaseOffsets[nationIndex] + unit.ordinal()];
        }

        int unitsBoughtToday(int nationIndex, MilitaryUnit unit) {
            return unitBuysTodayFlat[unitBaseOffsets[nationIndex] + unit.ordinal()];
        }

        void setPendingBuys(int nationIndex, MilitaryUnit unit, int value) {
            pendingBuysFlat[unitBaseOffsets[nationIndex] + unit.ordinal()] = value;
        }

        void resetUnitBuysToday(int nationIndex) {
            int base = unitBaseOffsets[nationIndex];
            java.util.Arrays.fill(unitBuysTodayFlat, base, base + UNIT_STRIDE, 0);
        }

        double subtractResource(int nationIndex, ResourceType type, double amount) {
            int index = resourceBaseOffsets[nationIndex] + type.ordinal();
            double removed = Math.min(amount, resourcesFlat[index]);
            resourcesFlat[index] -= removed;
            return removed;
        }

        double resource(int nationIndex, ResourceType type) {
            return resourcesFlat[resourceBaseOffsets[nationIndex] + type.ordinal()];
        }

        void addResource(int nationIndex, ResourceType type, double amount) {
            resourcesFlat[resourceBaseOffsets[nationIndex] + type.ordinal()] += amount;
        }

        double[] copyResources(int nationIndex) {
            int base = resourceBaseOffsets[nationIndex];
            double[] copy = new double[RESOURCE_STRIDE];
            System.arraycopy(resourcesFlat, base, copy, 0, RESOURCE_STRIDE);
            return copy;
        }

        double[] copyCityInfra(int nationIndex) {
            int cityCount = cityCounts[nationIndex];
            int base = cityInfraBaseOffsets[nationIndex];
            double[] copy = new double[cityCount];
            System.arraycopy(cityInfraFlat, base, copy, 0, cityCount);
            return copy;
        }

        SpecialistCityProfile citySpecialistProfile(int nationIndex, int cityIndex) {
            return citySpecialistProfilesFlat[cityInfraBaseOffsets[nationIndex] + cityIndex];
        }

        @Override
        public int[] unitsFlat() {
            return unitsFlat;
        }

        @Override
        public int unitBaseOffset(int nationIndex) {
            return unitBaseOffsets[nationIndex];
        }

        @Override
        public double[] cityInfraFlat() {
            return cityInfraFlat;
        }

        @Override
        public int cityInfraBaseOffset(int nationIndex) {
            return cityInfraBaseOffsets[nationIndex];
        }

        @Override
        public int cityCount(int nationIndex) {
            return cityCounts[nationIndex];
        }
    }

    private static final class LocalNation implements CombatKernel.BufferBackedNationState {
        private static final Map.Entry<Integer, Integer> ZERO_DAMAGE_RANGE = Map.entry(0, 0);

        private final int nationId;
        private final int nationIndex;
        private final LocalNationBuffers buffers;
        private final int allianceId;
        private final int teamId;
        private final WarPolicy warPolicy;
        private final double nonInfraScoreBase;
        private final int maxOff;
        private final byte resetHourUtc;
        private final int baseCurrentOffensiveWars;
        private final int baseCurrentDefensiveWars;
        private final Set<Integer> baseActiveOpponentNationIds;
        private final int researchBitsValue;
        private final long projectBitsValue;
        private final boolean blitzkriegActive;
        private final double[] infraAttackModifiers;
        private final double[] infraDefendModifiers;
        private final double groundLooterModifier;
        private final double nonGroundLooterModifier;
        private final double lootModifierValue;
        private int policyCooldownTurnsRemaining;
        private int beigeTurns;
        private int dayPhaseTurn;
        private double score;

        private LocalNation(
                int nationId,
                int nationIndex,
                LocalNationBuffers buffers,
                int allianceId,
                int teamId,
                WarPolicy warPolicy,
                double nonInfraScoreBase,
                int maxOff,
                byte resetHourUtc,
                int baseCurrentOffensiveWars,
                int baseCurrentDefensiveWars,
                Set<Integer> baseActiveOpponentNationIds,
                int researchBitsValue,
                long projectBitsValue,
                boolean blitzkriegActive,
                double[] infraAttackModifiers,
                double[] infraDefendModifiers,
                double groundLooterModifier,
                double nonGroundLooterModifier,
                double lootModifierValue,
                int policyCooldownTurnsRemaining,
                int beigeTurns,
                int dayPhaseTurn
        ) {
            this.nationId = nationId;
            this.nationIndex = nationIndex;
            this.buffers = buffers;
            this.allianceId = allianceId;
            this.teamId = teamId;
            this.warPolicy = warPolicy;
            this.nonInfraScoreBase = nonInfraScoreBase;
            this.maxOff = maxOff;
            this.resetHourUtc = resetHourUtc;
            this.baseCurrentOffensiveWars = baseCurrentOffensiveWars;
            this.baseCurrentDefensiveWars = baseCurrentDefensiveWars;
            this.baseActiveOpponentNationIds = Set.copyOf(baseActiveOpponentNationIds);
            this.researchBitsValue = researchBitsValue;
            this.projectBitsValue = projectBitsValue;
            this.blitzkriegActive = blitzkriegActive;
            this.infraAttackModifiers = infraAttackModifiers;
            this.infraDefendModifiers = infraDefendModifiers;
            this.groundLooterModifier = groundLooterModifier;
            this.nonGroundLooterModifier = nonGroundLooterModifier;
            this.lootModifierValue = lootModifierValue;
            this.policyCooldownTurnsRemaining = policyCooldownTurnsRemaining;
            this.beigeTurns = beigeTurns;
            this.dayPhaseTurn = dayPhaseTurn;
            recalculateScore();
        }

        static LocalNation of(
                DBNationSnapshot snapshot,
                LocalNationBuffers buffers,
                int nationIndex,
                int currentTurn
        ) {
            return new LocalNation(
                    snapshot.nationId(),
                    nationIndex,
                    buffers,
                    snapshot.allianceId(),
                    snapshot.teamId(),
                    snapshot.warPolicy(),
                    snapshot.nonInfraScoreBase(),
                    snapshot.maxOff(),
                    snapshot.resetHourUtc(),
                    snapshot.currentOffensiveWars(),
                    snapshot.currentDefensiveWars(),
                    snapshot.activeOpponentNationIds(),
                    snapshot.researchBits(),
                    snapshot.projectBits(),
                    snapshot.blitzkriegActive(),
                    compileInfraAttackModifiers(snapshot),
                    compileInfraDefendModifiers(snapshot),
                    snapshot.looterModifier(true),
                    snapshot.looterModifier(false),
                    snapshot.lootModifier(),
                    snapshot.policyCooldownTurnsRemaining(),
                    snapshot.beigeTurns(),
                    SimClock.dayPhaseForTurn(0, currentTurn, snapshot.resetHourUtc())
            );
        }

        private static double[] compileInfraAttackModifiers(DBNationSnapshot snapshot) {
            double[] values = new double[AttackType.values.length];
            for (AttackType type : AttackType.values) {
                values[type.ordinal()] = snapshot.infraAttackModifier(type);
            }
            return values;
        }

        private static double[] compileInfraDefendModifiers(DBNationSnapshot snapshot) {
            double[] values = new double[AttackType.values.length];
            for (AttackType type : AttackType.values) {
                values[type.ordinal()] = snapshot.infraDefendModifier(type);
            }
            return values;
        }

        int teamId() {
            return teamId;
        }

        double score() {
            return score;
        }

        LocalNationScalarSnapshot scalarSnapshot() {
            return new LocalNationScalarSnapshot(
                    policyCooldownTurnsRemaining,
                    beigeTurns,
                    dayPhaseTurn,
                    score
            );
        }

        void restoreScalarSnapshot(LocalNationScalarSnapshot snapshot) {
            this.policyCooldownTurnsRemaining = snapshot.policyCooldownTurnsRemaining();
            this.beigeTurns = snapshot.beigeTurns();
            this.dayPhaseTurn = snapshot.dayPhaseTurn();
            this.score = snapshot.score();
        }

        void advanceTurn(boolean materializePendingBuys, boolean decrementPolicyCooldown) {
            dayPhaseTurn = (dayPhaseTurn + 1) % 12;
            if (dayPhaseTurn == 0) {
                buffers.resetUnitBuysToday(nationIndex);
            }
            if (beigeTurns > 0) {
                beigeTurns--;
            }
            if (decrementPolicyCooldown && policyCooldownTurnsRemaining > 0) {
                policyCooldownTurnsRemaining--;
            }
            if (materializePendingBuys) {
                materializePendingBuys();
            }
        }

        private void materializePendingBuys() {
            boolean changed = false;
            for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                int pending = buffers.pendingBuys(nationIndex, unit);
                if (pending <= 0) {
                    continue;
                }
                int index = buffers.unitBaseOffset(nationIndex) + unit.ordinal();
                int[] unitsFlat = buffers.unitsFlat();
                unitsFlat[index] += pending;
                buffers.setPendingBuys(nationIndex, unit, 0);
                changed = true;
            }
            if (changed) {
                recalculateScore();
            }
        }

        void removeUnits(MilitaryUnit unit, int requested) {
            int index = buffers.unitBaseOffset(nationIndex) + unit.ordinal();
            int[] unitsFlat = buffers.unitsFlat();
            unitsFlat[index] = Math.max(0, unitsFlat[index] - requested);
            recalculateScore();
        }

        void applyInfraDamage(double totalInfraDestroyed) {
            int cityCount = buffers.cityCount(nationIndex);
            if (totalInfraDestroyed <= 0d || cityCount == 0) {
                return;
            }
            double[] cityInfraFlat = buffers.cityInfraFlat();
            int cityBase = buffers.cityInfraBaseOffset(nationIndex);
            double remaining = totalInfraDestroyed;
            while (remaining > 0d) {
                int maxIdx = 0;
                for (int i = 1; i < cityCount; i++) {
                    if (cityInfraFlat[cityBase + i] > cityInfraFlat[cityBase + maxIdx]) {
                        maxIdx = i;
                    }
                }
                int cityIndex = cityBase + maxIdx;
                double current = cityInfraFlat[cityIndex];
                if (current <= 0d) {
                    break;
                }
                double removed = Math.min(current, remaining);
                cityInfraFlat[cityIndex] = current - removed;
                buffers.recordCityInfraOverlay(nationIndex, maxIdx, cityInfraFlat[cityIndex]);
                remaining -= removed;
            }
            recalculateScore();
        }

        void applyVictoryInfraPercent(double infraDestroyedPercent) {
            int cityCount = buffers.cityCount(nationIndex);
            int infraPercentMilli = WarOutcomeMath.victoryInfraPercentMilli(infraDestroyedPercent);
            if (infraPercentMilli <= 0 || cityCount == 0) {
                return;
            }
            double[] cityInfraFlat = buffers.cityInfraFlat();
            int cityBase = buffers.cityInfraBaseOffset(nationIndex);
            boolean changed = false;
            for (int i = 0; i < cityCount; i++) {
                int cityIndex = cityBase + i;
                int beforeCents = Math.max(0, (int) Math.round(cityInfraFlat[cityIndex] * 100d));
                int afterCents = WarOutcomeMath.victoryInfraAfterCents(beforeCents, infraPercentMilli);
                double afterInfra = afterCents * 0.01d;
                if (cityInfraFlat[cityIndex] != afterInfra) {
                    cityInfraFlat[cityIndex] = afterInfra;
                    buffers.recordCityInfraOverlay(nationIndex, i, afterInfra);
                    changed = true;
                }
            }
            if (changed) {
                recalculateScore();
            }
        }

        double subtractResource(ResourceType type, double amount) {
            return buffers.subtractResource(nationIndex, type, amount);
        }

        void addResource(ResourceType type, double amount) {
            buffers.addResource(nationIndex, type, amount);
        }

        double resource(ResourceType type) {
            return buffers.resource(nationIndex, type);
        }

        DBNationSnapshot toSnapshot(List<PlannerProjectedWar> activeWars) {
            int offensiveWars = baseCurrentOffensiveWars;
            int defensiveWars = baseCurrentDefensiveWars;
            Set<Integer> opponents = new java.util.LinkedHashSet<>(baseActiveOpponentNationIds);
            for (PlannerProjectedWar war : activeWars) {
                if (war.attackerNationId() == nationId) {
                    offensiveWars++;
                    opponents.add(war.defenderNationId());
                } else if (war.defenderNationId() == nationId) {
                    defensiveWars++;
                    opponents.add(war.attackerNationId());
                }
            }
            DBNationSnapshot.Builder builder = DBNationSnapshot.synthetic(nationId)
                    .allianceId(allianceId)
                    .teamId(teamId)
                    .score(score)
                    .cities(cities())
                    .currentOffensiveWars(offensiveWars)
                    .currentDefensiveWars(defensiveWars)
                    .maxOff(maxOff)
                    .warPolicy(warPolicy)
                    .resources(buffers.copyResources(nationIndex))
                    .nonInfraScoreBase(nonInfraScoreBase)
                    .cityInfra(buffers.copyCityInfra(nationIndex))
                    .resetHourUtc(resetHourUtc)
                    .activeOpponentNationIds(opponents)
                    .policyCooldownTurnsRemaining(policyCooldownTurnsRemaining)
                    .beigeTurns(beigeTurns)
                    .researchBits(researchBitsValue)
                    .projectBits(projectBitsValue)
                    .blitzkriegActive(blitzkriegActive)
                    .looterModifiers(groundLooterModifier, nonGroundLooterModifier)
                    .lootModifier(lootModifierValue)
                    .infraModifiers(infraAttackModifiers, infraDefendModifiers);
            for (MilitaryUnit unit : MilitaryUnit.values) {
                builder.unit(unit, getUnits(unit));
                int boughtToday = buffers.unitsBoughtToday(nationIndex, unit);
                if (boughtToday > 0) {
                    builder.unitBoughtToday(unit, boughtToday);
                }
                int pending = buffers.pendingBuys(nationIndex, unit);
                if (pending > 0) {
                    builder.pendingBuyNextTurn(unit, pending);
                }
            }
            return builder.build();
        }

        private void recalculateScore() {
            double total = nonInfraScoreBase;
            int cityBase = buffers.cityInfraBaseOffset(nationIndex);
            int cityCount = buffers.cityCount(nationIndex);
            double[] cityInfraFlat = buffers.cityInfraFlat();
            for (int i = 0; i < cityCount; i++) {
                total += cityInfraFlat[cityBase + i] / 40d;
            }
            for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                int count = getUnits(unit);
                if (count > 0) {
                    total += unit.getScore(count);
                }
            }
            score = total;
        }

        @Override
        public int nationId() {
            return nationId;
        }

        @Override
        public LocalNationBuffers nationBuffer() {
            return buffers;
        }

        @Override
        public int nationIndex() {
            return nationIndex;
        }

        @Override
        public int researchBits() {
            return researchBitsValue;
        }

        @Override
        public Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
            if (cityIndex < 0 || cityIndex >= buffers.cityCount(nationIndex)) {
                return ZERO_DAMAGE_RANGE;
            }
            double infra = cityInfra(cityIndex);
            return buffers.citySpecialistProfile(nationIndex, cityIndex).missileDamage(infra, this::hasProject);
        }

        @Override
        public Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
            if (cityIndex < 0 || cityIndex >= buffers.cityCount(nationIndex)) {
                return ZERO_DAMAGE_RANGE;
            }
            double infra = cityInfra(cityIndex);
            return buffers.citySpecialistProfile(nationIndex, cityIndex).nukeDamage(infra, this::hasProject);
        }

        @Override
        public double infraAttackModifier(AttackType type) {
            return infraAttackModifiers[type.ordinal()];
        }

        @Override
        public double infraDefendModifier(AttackType type) {
            return infraDefendModifiers[type.ordinal()];
        }

        @Override
        public double looterModifier(boolean ground) {
            return ground ? groundLooterModifier : nonGroundLooterModifier;
        }

        @Override
        public double lootModifier() {
            return lootModifierValue;
        }

        @Override
        public boolean isBlitzkrieg() {
            return blitzkriegActive;
        }

        boolean canUseAttackType(AttackType type) {
            return switch (type) {
                case GROUND -> getUnits(MilitaryUnit.SOLDIER) > 0 || getUnits(MilitaryUnit.TANK) > 0;
                case AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY,
                        AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT -> getUnits(MilitaryUnit.AIRCRAFT) > 0;
                case NAVAL, NAVAL_INFRA, NAVAL_AIR, NAVAL_GROUND -> getUnits(MilitaryUnit.SHIP) > 0;
                case MISSILE -> getUnits(MilitaryUnit.MISSILE) > 0 && hasProject(Projects.MISSILE_LAUNCH_PAD);
                case NUKE -> getUnits(MilitaryUnit.NUKE) > 0 && hasProject(Projects.NUCLEAR_RESEARCH_FACILITY);
                default -> true;
            };
        }

        @Override
        public boolean hasProject(Project project) {
            return (projectBitsValue & (1L << project.ordinal())) != 0;
        }
    }

    private static final class LocalWarBuffers implements CombatKernel.PrimitiveWarBuffer {
        private static final int OWNER_NONE = 0;
        private static final int OWNER_ATTACKER = 1;
        private static final int OWNER_DEFENDER = 2;
        private static final int INITIAL_CAPACITY = 16;

        private WarType[] warTypes = new WarType[INITIAL_CAPACITY];
        private int[] startTurn = new int[INITIAL_CAPACITY];
        private WarStatus[] status = new WarStatus[INITIAL_CAPACITY];
        private int[] attackerMaps = new int[INITIAL_CAPACITY];
        private int[] defenderMaps = new int[INITIAL_CAPACITY];
        private int[] attackerResistance = new int[INITIAL_CAPACITY];
        private int[] defenderResistance = new int[INITIAL_CAPACITY];
        private boolean[] attackerFortified = new boolean[INITIAL_CAPACITY];
        private boolean[] defenderFortified = new boolean[INITIAL_CAPACITY];
        private int[] groundControlOwner = new int[INITIAL_CAPACITY];
        private int[] airSuperiorityOwner = new int[INITIAL_CAPACITY];
        private int[] blockadeOwner = new int[INITIAL_CAPACITY];
        private int size;

        int allocate(WarType warType, int warStartTurn, int initialMaps, int initialResistance) {
            int warIndex = size;
            ensureCapacity(warIndex + 1);
            size++;
            warTypes[warIndex] = warType;
            startTurn[warIndex] = warStartTurn;
            status[warIndex] = WarStatus.ACTIVE;
            attackerMaps[warIndex] = initialMaps;
            defenderMaps[warIndex] = initialMaps;
            attackerResistance[warIndex] = initialResistance;
            defenderResistance[warIndex] = initialResistance;
            attackerFortified[warIndex] = false;
            defenderFortified[warIndex] = false;
            groundControlOwner[warIndex] = OWNER_NONE;
            airSuperiorityOwner[warIndex] = OWNER_NONE;
            blockadeOwner[warIndex] = OWNER_NONE;
            return warIndex;
        }

        int allocate(PlannerProjectedWar projectedWar) {
            int warIndex = size;
            ensureCapacity(warIndex + 1);
            size++;
            warTypes[warIndex] = projectedWar.warType();
            startTurn[warIndex] = projectedWar.startTurn();
            status[warIndex] = projectedWar.status();
            attackerMaps[warIndex] = projectedWar.attackerMaps();
            defenderMaps[warIndex] = projectedWar.defenderMaps();
            attackerResistance[warIndex] = projectedWar.attackerResistance();
            defenderResistance[warIndex] = projectedWar.defenderResistance();
            attackerFortified[warIndex] = projectedWar.attackerFortified();
            defenderFortified[warIndex] = projectedWar.defenderFortified();
            groundControlOwner[warIndex] = ownerCode(projectedWar.groundControlOwner());
            airSuperiorityOwner[warIndex] = ownerCode(projectedWar.airSuperiorityOwner());
            blockadeOwner[warIndex] = ownerCode(projectedWar.blockadeOwner());
            return warIndex;
        }

        WarStatus status(int warIndex) {
            requireWarIndex(warIndex);
            return status[warIndex];
        }

        void setStatus(int warIndex, WarStatus value) {
            requireWarIndex(warIndex);
            status[warIndex] = value == null ? WarStatus.ACTIVE : value;
        }

        int startTurnValue(int warIndex) {
            requireWarIndex(warIndex);
            return startTurn[warIndex];
        }

        boolean isActive(int warIndex) {
            requireWarIndex(warIndex);
            return status[warIndex].isActive();
        }

        int attackerMapsValue(int warIndex) {
            requireWarIndex(warIndex);
            return attackerMaps[warIndex];
        }

        void setAttackerMaps(int warIndex, int value) {
            requireWarIndex(warIndex);
            attackerMaps[warIndex] = value;
        }

        int defenderMapsValue(int warIndex) {
            requireWarIndex(warIndex);
            return defenderMaps[warIndex];
        }

        void setDefenderMaps(int warIndex, int value) {
            requireWarIndex(warIndex);
            defenderMaps[warIndex] = value;
        }

        int attackerResistanceValue(int warIndex) {
            requireWarIndex(warIndex);
            return attackerResistance[warIndex];
        }

        void setAttackerResistance(int warIndex, int value) {
            requireWarIndex(warIndex);
            attackerResistance[warIndex] = value;
        }

        int defenderResistanceValue(int warIndex) {
            requireWarIndex(warIndex);
            return defenderResistance[warIndex];
        }

        void setDefenderResistance(int warIndex, int value) {
            requireWarIndex(warIndex);
            defenderResistance[warIndex] = value;
        }

        void setGroundControlOwner(int warIndex, int owner) {
            requireWarIndex(warIndex);
            groundControlOwner[warIndex] = owner;
        }

        void setAirSuperiorityOwner(int warIndex, int owner) {
            requireWarIndex(warIndex);
            airSuperiorityOwner[warIndex] = owner;
        }

        void setBlockadeOwner(int warIndex, int owner) {
            requireWarIndex(warIndex);
            blockadeOwner[warIndex] = owner;
        }

        void setAttackerFortified(int warIndex, boolean value) {
            requireWarIndex(warIndex);
            attackerFortified[warIndex] = value;
        }

        void setDefenderFortified(int warIndex, boolean value) {
            requireWarIndex(warIndex);
            defenderFortified[warIndex] = value;
        }

        void clearAttackerFortified(int warIndex) {
            requireWarIndex(warIndex);
            attackerFortified[warIndex] = false;
        }

        private void ensureCapacity(int targetSize) {
            if (targetSize <= warTypes.length) {
                return;
            }
            int newCapacity = Math.max(targetSize, warTypes.length * 2);
            warTypes = java.util.Arrays.copyOf(warTypes, newCapacity);
            startTurn = java.util.Arrays.copyOf(startTurn, newCapacity);
            status = java.util.Arrays.copyOf(status, newCapacity);
            attackerMaps = java.util.Arrays.copyOf(attackerMaps, newCapacity);
            defenderMaps = java.util.Arrays.copyOf(defenderMaps, newCapacity);
            attackerResistance = java.util.Arrays.copyOf(attackerResistance, newCapacity);
            defenderResistance = java.util.Arrays.copyOf(defenderResistance, newCapacity);
            attackerFortified = java.util.Arrays.copyOf(attackerFortified, newCapacity);
            defenderFortified = java.util.Arrays.copyOf(defenderFortified, newCapacity);
            groundControlOwner = java.util.Arrays.copyOf(groundControlOwner, newCapacity);
            airSuperiorityOwner = java.util.Arrays.copyOf(airSuperiorityOwner, newCapacity);
            blockadeOwner = java.util.Arrays.copyOf(blockadeOwner, newCapacity);
        }

        private void requireWarIndex(int warIndex) {
            if (warIndex < 0 || warIndex >= size) {
                throw new IndexOutOfBoundsException(warIndex);
            }
        }

        private static int ownerCode(ControlOwner owner) {
            return switch (owner) {
                case ATTACKER -> OWNER_ATTACKER;
                case DEFENDER -> OWNER_DEFENDER;
                default -> OWNER_NONE;
            };
        }

        LocalWarBufferSnapshot snapshot() {
            return new LocalWarBufferSnapshot(
                    java.util.Arrays.copyOf(warTypes, warTypes.length),
                    java.util.Arrays.copyOf(startTurn, startTurn.length),
                    java.util.Arrays.copyOf(status, status.length),
                    java.util.Arrays.copyOf(attackerMaps, attackerMaps.length),
                    java.util.Arrays.copyOf(defenderMaps, defenderMaps.length),
                    java.util.Arrays.copyOf(attackerResistance, attackerResistance.length),
                    java.util.Arrays.copyOf(defenderResistance, defenderResistance.length),
                    java.util.Arrays.copyOf(attackerFortified, attackerFortified.length),
                    java.util.Arrays.copyOf(defenderFortified, defenderFortified.length),
                    java.util.Arrays.copyOf(groundControlOwner, groundControlOwner.length),
                    java.util.Arrays.copyOf(airSuperiorityOwner, airSuperiorityOwner.length),
                    java.util.Arrays.copyOf(blockadeOwner, blockadeOwner.length),
                    size
            );
        }

        void restore(LocalWarBufferSnapshot snapshot) {
            this.warTypes = java.util.Arrays.copyOf(snapshot.warTypes(), snapshot.warTypes().length);
            this.startTurn = java.util.Arrays.copyOf(snapshot.startTurn(), snapshot.startTurn().length);
            this.status = java.util.Arrays.copyOf(snapshot.status(), snapshot.status().length);
            this.attackerMaps = java.util.Arrays.copyOf(snapshot.attackerMaps(), snapshot.attackerMaps().length);
            this.defenderMaps = java.util.Arrays.copyOf(snapshot.defenderMaps(), snapshot.defenderMaps().length);
            this.attackerResistance = java.util.Arrays.copyOf(snapshot.attackerResistance(), snapshot.attackerResistance().length);
            this.defenderResistance = java.util.Arrays.copyOf(snapshot.defenderResistance(), snapshot.defenderResistance().length);
            this.attackerFortified = java.util.Arrays.copyOf(snapshot.attackerFortified(), snapshot.attackerFortified().length);
            this.defenderFortified = java.util.Arrays.copyOf(snapshot.defenderFortified(), snapshot.defenderFortified().length);
            this.groundControlOwner = java.util.Arrays.copyOf(snapshot.groundControlOwner(), snapshot.groundControlOwner().length);
            this.airSuperiorityOwner = java.util.Arrays.copyOf(snapshot.airSuperiorityOwner(), snapshot.airSuperiorityOwner().length);
            this.blockadeOwner = java.util.Arrays.copyOf(snapshot.blockadeOwner(), snapshot.blockadeOwner().length);
            this.size = snapshot.size();
        }

        private static ControlOwner controlOwner(int ownerCode) {
            return switch (ownerCode) {
                case OWNER_ATTACKER -> ControlOwner.ATTACKER;
                case OWNER_DEFENDER -> ControlOwner.DEFENDER;
                default -> ControlOwner.NONE;
            };
        }

        @Override
        public WarType warType(int warIndex) {
            requireWarIndex(warIndex);
            return warTypes[warIndex];
        }

        @Override
        public boolean attackerHasAirControl(int warIndex) {
            requireWarIndex(warIndex);
            return airSuperiorityOwner[warIndex] == OWNER_ATTACKER;
        }

        @Override
        public boolean defenderHasAirControl(int warIndex) {
            requireWarIndex(warIndex);
            return airSuperiorityOwner[warIndex] == OWNER_DEFENDER;
        }

        @Override
        public boolean attackerHasGroundControl(int warIndex) {
            requireWarIndex(warIndex);
            return groundControlOwner[warIndex] == OWNER_ATTACKER;
        }

        @Override
        public boolean defenderHasGroundControl(int warIndex) {
            requireWarIndex(warIndex);
            return groundControlOwner[warIndex] == OWNER_DEFENDER;
        }

        @Override
        public boolean attackerFortified(int warIndex) {
            requireWarIndex(warIndex);
            return attackerFortified[warIndex];
        }

        @Override
        public boolean defenderFortified(int warIndex) {
            requireWarIndex(warIndex);
            return defenderFortified[warIndex];
        }

        @Override
        public int attackerMaps(int warIndex) {
            return attackerMapsValue(warIndex);
        }

        @Override
        public int defenderMaps(int warIndex) {
            return defenderMapsValue(warIndex);
        }

        @Override
        public int attackerResistance(int warIndex) {
            return attackerResistanceValue(warIndex);
        }

        @Override
        public int defenderResistance(int warIndex) {
            return defenderResistanceValue(warIndex);
        }

        @Override
        public int blockadeOwner(int warIndex) {
            requireWarIndex(warIndex);
            return switch (blockadeOwner[warIndex]) {
                case OWNER_ATTACKER -> CombatKernel.AttackContext.BLOCKADE_ATTACKER;
                case OWNER_DEFENDER -> CombatKernel.AttackContext.BLOCKADE_DEFENDER;
                default -> CombatKernel.AttackContext.BLOCKADE_NONE;
            };
        }
    }

    private static final class LocalWar implements CombatKernel.BufferBackedAttackContext, WarControlRules.MutableWarControlState {
        private static final int INITIAL_RESISTANCE = 100;
        private static final int INITIAL_MAPS = 6;
        private static final int INITIAL_TURNS = 60;
        private static final int MAP_CAP = 12;

        private final int warId;
        private final int warIndex;
        private final LocalNation attacker;
        private final LocalNation defender;
        private final LocalWarBuffers warBuffers;

        private LocalWar(
                int warId,
                int warIndex,
                LocalNation attacker,
                LocalNation defender,
                LocalWarBuffers warBuffers
        ) {
            this.warId = warId;
            this.warIndex = warIndex;
            this.attacker = attacker;
            this.defender = defender;
            this.warBuffers = warBuffers;
        }

        private enum Side {
            ATTACKER,
            DEFENDER
        }

        int warId() {
            return warId;
        }

        @Override
        public boolean isActive() {
            return warBuffers.isActive(warIndex);
        }

        int startTurn() {
            return warBuffers.startTurnValue(warIndex);
        }

        @Override
        public WarType warType() {
            return warBuffers.warType(warIndex);
        }

        public int attackerNationId() {
            return attacker.nationId();
        }

        public int defenderNationId() {
            return defender.nationId();
        }

        void setStatus(WarStatus status) {
            warBuffers.setStatus(warIndex, status);
        }

        boolean hasExpiredAtTurnStart(int currentTurn) {
            return currentTurn >= startTurn() + INITIAL_TURNS - 1;
        }

        int attackerMapsValue() {
            return warBuffers.attackerMapsValue(warIndex);
        }

        void setAttackerMaps(int value) {
            warBuffers.setAttackerMaps(warIndex, value);
        }

        int defenderMapsValue() {
            return warBuffers.defenderMapsValue(warIndex);
        }

        void setDefenderMaps(int value) {
            warBuffers.setDefenderMaps(warIndex, value);
        }

        int attackerResistanceValue() {
            return warBuffers.attackerResistanceValue(warIndex);
        }

        void setAttackerResistance(int value) {
            warBuffers.setAttackerResistance(warIndex, value);
        }

        int defenderResistanceValue() {
            return warBuffers.defenderResistanceValue(warIndex);
        }

        void setDefenderResistance(int value) {
            warBuffers.setDefenderResistance(warIndex, value);
        }

        void setGroundControlOwner(Side side) {
            warBuffers.setGroundControlOwner(warIndex, ownerCode(side));
        }

        void setAirSuperiorityOwner(Side side) {
            warBuffers.setAirSuperiorityOwner(warIndex, ownerCode(side));
        }

        void setBlockadeOwner(Side side) {
            warBuffers.setBlockadeOwner(warIndex, ownerCode(side));
        }

        @Override
        public Integer groundControlNationId() {
            return controlNationId(warBuffers.groundControlOwner[warIndex]);
        }

        @Override
        public Integer airSuperiorityNationId() {
            return controlNationId(warBuffers.airSuperiorityOwner[warIndex]);
        }

        @Override
        public Integer blockadeNationId() {
            return controlNationId(warBuffers.blockadeOwner[warIndex]);
        }

        @Override
        public void setGroundControlNationId(Integer nationId) {
            warBuffers.setGroundControlOwner(warIndex, ownerCode(nationId));
        }

        @Override
        public void setAirSuperiorityNationId(Integer nationId) {
            warBuffers.setAirSuperiorityOwner(warIndex, ownerCode(nationId));
        }

        @Override
        public void setBlockadeNationId(Integer nationId) {
            warBuffers.setBlockadeOwner(warIndex, ownerCode(nationId));
        }

        void clearAttackerFortified() {
            warBuffers.clearAttackerFortified(warIndex);
        }

        boolean hasPendingPeaceOffer() {
            WarStatus status = warBuffers.status(warIndex);
            return status == WarStatus.ATTACKER_OFFERED_PEACE || status == WarStatus.DEFENDER_OFFERED_PEACE;
        }

        void offerPeace(Side offeringSide) {
            if (!isActive()) {
                throw new IllegalStateException("Cannot offer peace on inactive war " + warId);
            }
            if (hasPendingPeaceOffer()) {
                throw new IllegalStateException("Peace offer already pending for war " + warId);
            }
            warBuffers.setStatus(
                    warIndex,
                    offeringSide == Side.ATTACKER ? WarStatus.ATTACKER_OFFERED_PEACE : WarStatus.DEFENDER_OFFERED_PEACE
            );
        }

        void acceptPeace(Side acceptingSide) {
            if (!isActive()) {
                throw new IllegalStateException("Cannot accept peace on inactive war " + warId);
            }
            WarStatus pending = warBuffers.status(warIndex);
            if (pending != WarStatus.ATTACKER_OFFERED_PEACE && pending != WarStatus.DEFENDER_OFFERED_PEACE) {
                throw new IllegalStateException("No peace offer to accept for war " + warId);
            }
            if ((pending == WarStatus.ATTACKER_OFFERED_PEACE && acceptingSide == Side.ATTACKER)
                    || (pending == WarStatus.DEFENDER_OFFERED_PEACE && acceptingSide == Side.DEFENDER)) {
                throw new IllegalStateException("Peace offer must be accepted by the opposite side for war " + warId);
            }
            setStatus(WarStatus.PEACE);
        }

        PlannerProjectedWar project(int currentTurn) {
            return new PlannerProjectedWar(
                    attacker.nationId(),
                    defender.nationId(),
                    warBuffers.warType(warIndex),
                    startTurn(),
                    warBuffers.status(warIndex),
                    attackerMapsValue(),
                    defenderMapsValue(),
                    attackerResistanceValue(),
                    defenderResistanceValue(),
                    LocalWarBuffers.controlOwner(warBuffers.groundControlOwner[warIndex]),
                    LocalWarBuffers.controlOwner(warBuffers.airSuperiorityOwner[warIndex]),
                    LocalWarBuffers.controlOwner(warBuffers.blockadeOwner[warIndex]),
                    warBuffers.attackerFortified(warIndex),
                    warBuffers.defenderFortified(warIndex)
            );
        }

        private static int ownerCode(Side side) {
            return side == Side.ATTACKER ? LocalWarBuffers.OWNER_ATTACKER : LocalWarBuffers.OWNER_DEFENDER;
        }

        private Integer controlNationId(int ownerCode) {
            return switch (ownerCode) {
                case LocalWarBuffers.OWNER_ATTACKER -> attacker.nationId();
                case LocalWarBuffers.OWNER_DEFENDER -> defender.nationId();
                default -> null;
            };
        }

        private int ownerCode(Integer nationId) {
            if (nationId == null) {
                return LocalWarBuffers.OWNER_NONE;
            }
            if (nationId == attacker.nationId()) {
                return LocalWarBuffers.OWNER_ATTACKER;
            }
            if (nationId == defender.nationId()) {
                return LocalWarBuffers.OWNER_DEFENDER;
            }
            throw new IllegalArgumentException("Nation " + nationId + " is not in local war " + warId);
        }

        @Override
        public CombatKernel.NationState attacker() {
            return attacker;
        }

        @Override
        public CombatKernel.NationState defender() {
            return defender;
        }

        @Override
        public CombatKernel.PrimitiveWarBuffer warBuffer() {
            return warBuffers;
        }

        @Override
        public int warIndex() {
            return warIndex;
        }
    }
}
