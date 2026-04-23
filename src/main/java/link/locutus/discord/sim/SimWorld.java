package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AcceptPeaceAction;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.actions.BuyUnitsAction;
import link.locutus.discord.sim.actions.DeclareWarAction;
import link.locutus.discord.sim.actions.OfferPeaceAction;
import link.locutus.discord.sim.actions.ReleaseMapAction;
import link.locutus.discord.sim.actions.ReserveMapAction;
import link.locutus.discord.sim.actions.SetPolicyAction;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.LiveAttackContext;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.RandomSource;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.combat.WarControlRules;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.sim.input.NationInit;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.actions.SimActionPhase;
import link.locutus.discord.sim.actions.WaitAction;
import link.locutus.discord.util.PW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SimWorld {
    private static final int MAX_STOCHASTIC_ACTIONS_PER_PHASE = 64;

    private final SimTuning tuning;
    private final EconomyProvider economyProvider;
    private final AllianceLootProvider allianceLootProvider;
    private final ActivityProvider activityProvider;
    private final ResetTimeProvider resetTimeProvider;
    private final ScenarioActionPolicy scenarioActionPolicy;
    private final SimClock clock;
    private final RandomSource randomSource;
    private SimNationArrayStore nationStore = SimNationArrayStore.empty();
    private final Map<Integer, SimNation> nationsById = new LinkedHashMap<>();
    private final Map<Integer, SimWar> warsById = new LinkedHashMap<>();
    private WarParticipationIndex warParticipationIndex = new WarParticipationIndex();
    private DeclareRangeIndex declareRangeIndex = new DeclareRangeIndex();
    private final Map<Integer, Integer> actionIndexByNationThisTurn = new HashMap<>();
    private final AttackScratch attackScratch = new AttackScratch();
    private final MutableAttackResult attackResult = new MutableAttackResult();
    private final LiveAttackContext liveAttackContext = new LiveAttackContext();

    public SimWorld() {
        this(SimTuning.defaults(), new SimClock(), EconomyProvider.NO_OP, AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE, ResetTimeProvider.FROM_NATION, ScenarioActionPolicy.ALLOW_ALL);
    }

    public SimWorld(SimTuning tuning) {
        this(tuning, new SimClock(), EconomyProvider.NO_OP, AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE, ResetTimeProvider.FROM_NATION, ScenarioActionPolicy.ALLOW_ALL);
    }

    public SimWorld(SimTuning tuning, SimClock clock) {
        this(tuning, clock, EconomyProvider.NO_OP, AllianceLootProvider.NO_OP,
                ActivityProvider.BASELINE, ResetTimeProvider.FROM_NATION, ScenarioActionPolicy.ALLOW_ALL);
    }

    public SimWorld(
            SimTuning tuning,
            SimClock clock,
            EconomyProvider economyProvider,
            AllianceLootProvider allianceLootProvider
    ) {
        this(tuning, clock, economyProvider, allianceLootProvider,
                ActivityProvider.BASELINE, ResetTimeProvider.FROM_NATION, ScenarioActionPolicy.ALLOW_ALL);
    }

    public SimWorld(
            SimTuning tuning,
            SimClock clock,
            EconomyProvider economyProvider,
            AllianceLootProvider allianceLootProvider,
            ActivityProvider activityProvider,
            ResetTimeProvider resetTimeProvider
    ) {
        this(
                tuning,
                clock,
                economyProvider,
                allianceLootProvider,
                activityProvider,
                resetTimeProvider,
                ScenarioActionPolicy.ALLOW_ALL
        );
    }

    public SimWorld(
            SimTuning tuning,
            SimClock clock,
            EconomyProvider economyProvider,
            AllianceLootProvider allianceLootProvider,
            ActivityProvider activityProvider,
            ResetTimeProvider resetTimeProvider,
            ScenarioActionPolicy scenarioActionPolicy
    ) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.allianceLootProvider = Objects.requireNonNull(allianceLootProvider, "allianceLootProvider");
        this.activityProvider = Objects.requireNonNull(activityProvider, "activityProvider");
        this.resetTimeProvider = Objects.requireNonNull(resetTimeProvider, "resetTimeProvider");
        this.scenarioActionPolicy = Objects.requireNonNull(scenarioActionPolicy, "scenarioActionPolicy");
        this.randomSource = RandomSource.splittable(tuning.stochasticSeed());
    }

    public SimWorld(
            SimTuning tuning,
            EconomyProvider economyProvider,
            AllianceLootProvider allianceLootProvider
    ) {
        this(tuning, new SimClock(), economyProvider, allianceLootProvider,
                ActivityProvider.BASELINE, ResetTimeProvider.FROM_NATION, ScenarioActionPolicy.ALLOW_ALL);
    }

    public SimWorld(
            SimTuning tuning,
            EconomyProvider economyProvider,
            AllianceLootProvider allianceLootProvider,
            ActivityProvider activityProvider,
            ResetTimeProvider resetTimeProvider
    ) {
        this(tuning, new SimClock(), economyProvider, allianceLootProvider, activityProvider, resetTimeProvider, ScenarioActionPolicy.ALLOW_ALL);
    }

    public SimWorld(
            SimTuning tuning,
            EconomyProvider economyProvider,
            AllianceLootProvider allianceLootProvider,
            ActivityProvider activityProvider,
            ResetTimeProvider resetTimeProvider,
            ScenarioActionPolicy scenarioActionPolicy
    ) {
        this(tuning, new SimClock(), economyProvider, allianceLootProvider, activityProvider, resetTimeProvider, scenarioActionPolicy);
    }

    public SimTuning tuning() {
        return tuning;
    }

    public int currentTurn() {
        return clock.currentTurn();
    }

    public int currentHourUtc() {
        return clock.currentHourUtc();
    }

    public ActivityProvider activityProvider() {
        return activityProvider;
    }

    public ResetTimeProvider resetTimeProvider() {
        return resetTimeProvider;
    }

    /**
     * Check if a nation is active enough to act during this turn.
     * Activity is baseline plus wartime uplift if the nation has active wars.
     * 
     * @param nation the nation to check
     * @return the effective activity level [0, 1]
     */
    public double effectiveActivityAt(SimNation nation) {
        double baseActivity = activityProvider.activityAt(nation, currentTurn());
        boolean hasActiveWar = warParticipationIndex.hasActiveWar(nation.nationId());
        double uplift = hasActiveWar ? tuning.wartimeActivityUplift() : 0.0;
        return Math.min(1.0, baseActivity + uplift);
    }

    /**
     * Apply control flag changes to a war and invoke the provider callback if blockade changed.
     * This is called after an attack resolves and may have shifted control flags.
     * 
     * @param warId the war ID
     * @param actorNationId the nation that attacked
     * @param groundControlDelta +1/-1/0 control change
     * @param airSuperiorityDelta similar
     * @param blockadeDelta similar
     */
    public void applyControlFlagChanges(
            int warId,
            int actorNationId,
            int groundControlDelta,
            int airSuperiorityDelta,
            int blockadeDelta
    ) {
        SimWar war = requireWar(warId);
        boolean blockadeChanged = war.applyControlFlagChanges(actorNationId, groundControlDelta, airSuperiorityDelta, blockadeDelta);
        if (blockadeChanged) {
            economyProvider.onControlFlagChange(war);
        }
    }

    @Deprecated(forRemoval = false)
    public void addNation(SimNation nation) {
        Objects.requireNonNull(nation, "nation");
        int nationId = nation.nationId();
        if (nationsById.containsKey(nationId)) {
            throw new IllegalArgumentException("Nation already exists: " + nationId);
        }
        int nationIndex = nationStore.add(nation.snapshot());
        attachNation(nation, nationIndex);
        declareRangeIndex.onNationAdded(nation.nationId(), nation.score());
        nation.initializeDayPhaseTurn(clock.dayPhaseForResetHour(nation.resetHourUtc()));
    }

    public void addNation(NationInit init) {
        addNation(new SimNation(init));
    }

    public SimNation requireNation(int nationId) {
        SimNation nation = nationsById.get(nationId);
        if (nation == null) {
            throw new IllegalArgumentException("Unknown nationId: " + nationId);
        }
        return nation;
    }

    /** Returns the nation with the given ID, or null if not registered in this world. */
    public SimNation findNation(int nationId) {
        return nationsById.get(nationId);
    }

    public Iterable<SimNation> nations() {
        return nationsById.values();
    }

    /** Returns the active wars that involve the given nation, in insertion order. */
    public List<SimWar> activeWarsForNation(int nationId) {
        List<Integer> warIds = warParticipationIndex.activeWarIdsForNation(nationId);
        if (warIds.isEmpty()) {
            return List.of();
        }
        ArrayList<SimWar> activeWars = new ArrayList<>(warIds.size());
        for (int warId : warIds) {
            SimWar war = warsById.get(warId);
            if (war != null && war.isActive()) {
                activeWars.add(war);
            }
        }
        return List.copyOf(activeWars);
    }

    public void addWar(SimWar war) {
        Objects.requireNonNull(war, "war");
        if (warsById.containsKey(war.warId())) {
            throw new IllegalArgumentException("War already exists: " + war.warId());
        }
        war.bindStartTurn(currentTurn());
        SimNation attacker = requireNation(war.attackerNationId());
        SimNation defender = requireNation(war.defenderNationId());
        attacker.occupyOffensiveSlot();
        defender.occupyDefensiveSlot();
        warsById.put(war.warId(), war);
        warParticipationIndex.onWarAdded(war);
    }

    public SimWar requireWar(int warId) {
        SimWar war = warsById.get(warId);
        if (war == null) {
            throw new IllegalArgumentException("Unknown warId: " + warId);
        }
        return war;
    }

    public void apply(SimAction action) {
        SimAction nonNullAction = Objects.requireNonNull(action, "action");
        ensureActionAllowed(nonNullAction);
        nonNullAction.apply(this);
        cleanupEndedWars();
    }

    private void ensureActionAllowed(SimAction action) {
        Integer actorNationId = actorNationId(action);
        if (actorNationId == null) {
            return;
        }
        SimNation actor = requireNation(actorNationId);
        ScenarioActionPolicy.NationActionPolicy policy = scenarioActionPolicy.resolve(this, actor);

        if (action instanceof DeclareWarAction) {
            if (!policy.allowDeclares()) {
                throw new IllegalStateException("Scenario policy blocks declares for nation " + actorNationId);
            }
            return;
        }
        if (action instanceof BuyUnitsAction) {
            if (!policy.allowBuys()) {
                throw new IllegalStateException("Scenario policy blocks buys for nation " + actorNationId);
            }
            return;
        }
        if (action instanceof OfferPeaceAction || action instanceof AcceptPeaceAction) {
            if (!policy.allowPeace()) {
                throw new IllegalStateException("Scenario policy blocks peace actions for nation " + actorNationId);
            }
            return;
        }
        if (action instanceof ReserveMapAction || action instanceof ReleaseMapAction) {
            if (!policy.allowMapReservations()) {
                throw new IllegalStateException("Scenario policy blocks map reservations for nation " + actorNationId);
            }
            return;
        }
        if (action instanceof AttackAction attackAction && !policy.allowsAttack(attackAction.attackType())) {
            throw new IllegalStateException(
                    "Scenario policy blocks attack type " + attackAction.attackType() + " for nation " + actorNationId
            );
        }
    }

    private static Integer actorNationId(SimAction action) {
        if (action instanceof DeclareWarAction declareWarAction) {
            return declareWarAction.attackerNationId();
        }
        if (action instanceof BuyUnitsAction buyUnitsAction) {
            return buyUnitsAction.nationId();
        }
        if (action instanceof OfferPeaceAction offerPeaceAction) {
            return offerPeaceAction.actorNationId();
        }
        if (action instanceof AcceptPeaceAction acceptPeaceAction) {
            return acceptPeaceAction.actorNationId();
        }
        if (action instanceof ReserveMapAction reserveMapAction) {
            return reserveMapAction.actorNationId();
        }
        if (action instanceof ReleaseMapAction releaseMapAction) {
            return releaseMapAction.actorNationId();
        }
        if (action instanceof AttackAction attackAction) {
            return attackAction.actorNationId();
        }
        if (action instanceof SetPolicyAction setPolicyAction) {
            return setPolicyAction.nationId();
        }
        if (action instanceof WaitAction waitAction) {
            return waitAction.nationId();
        }
        return null;
    }

    private void ensureDeclaresAllowed(int nationId) {
        ScenarioActionPolicy.NationActionPolicy policy = scenarioActionPolicy.resolve(this, requireNation(nationId));
        if (!policy.allowDeclares()) {
            throw new IllegalStateException("Scenario policy blocks declares for nation " + nationId);
        }
    }

    private void ensureBuysAllowed(int nationId) {
        ScenarioActionPolicy.NationActionPolicy policy = scenarioActionPolicy.resolve(this, requireNation(nationId));
        if (!policy.allowBuys()) {
            throw new IllegalStateException("Scenario policy blocks buys for nation " + nationId);
        }
    }

    private void ensureReserveAllowed(int nationId) {
        ScenarioActionPolicy.NationActionPolicy policy = scenarioActionPolicy.resolve(this, requireNation(nationId));
        if (!policy.allowMapReservations()) {
            throw new IllegalStateException("Scenario policy blocks map reservations for nation " + nationId);
        }
    }

    private void ensureAttackAllowed(int nationId, AttackType attackType) {
        ScenarioActionPolicy.NationActionPolicy policy = scenarioActionPolicy.resolve(this, requireNation(nationId));
        if (!policy.allowsAttack(attackType)) {
            throw new IllegalStateException("Scenario policy blocks attack type " + attackType + " for nation " + nationId);
        }
    }

    public void reserveMaps(int warId, int actorNationId, int mapsToReserve) {
        ensureReserveAllowed(actorNationId);
        SimWar war = requireWar(warId);
        war.reserveMaps(actorNationId, mapsToReserve);
    }

    public void releaseReservedMaps(int warId, int actorNationId) {
        ensureReserveAllowed(actorNationId);
        requireWar(warId).releaseReservedMaps(actorNationId);
    }

    /**
     * Resolve a combat attack action, applying all outcome effects to sim state.
     * FORTIFY is handled as a special case (no resolver invoked; just sets fortify flag).
      * For all other attack types: binds the live war/nation state into the shared
      * combat kernel, resolves into reusable scratch/output buffers, then applies unit
      * losses, infra damage, resistance deltas, MAP spending, loot transfer, and control
      * flag updates. Calls defeat resolution if resistance hits 0.
     */
    public void resolveAttack(int warId, int actorNationId, AttackType attackType) {
        Objects.requireNonNull(attackType, "attackType");
        ensureAttackAllowed(actorNationId, attackType);
        SimWar war = requireWar(warId);

        // FORTIFY: handled by SimWar directly (sets fortify flag, no combat math)
        if (attackType == AttackType.FORTIFY) {
            war.applyAttack(actorNationId, attackType);
            return;
        }

        SimSide actorSide = war.sideForNation(actorNationId);
        int defenderNationId = actorSide == SimSide.ATTACKER ? war.defenderNationId() : war.attackerNationId();

        SimNation attackerNation = requireNation(actorNationId);
        SimNation defenderNation = requireNation(defenderNationId);
        liveAttackContext.bind(attackerNation, defenderNation, war, actorSide);

        ResolutionMode resolutionMode = stateResolutionMode();
        long streamKey = attackStreamKey(actorNationId);
        if (resolutionMode == ResolutionMode.STOCHASTIC) {
            CombatKernel.resolveInto(liveAttackContext, attackType, resolutionMode, randomSource, streamKey, attackScratch, attackResult);
        } else {
            CombatKernel.resolveInto(liveAttackContext, attackType, resolutionMode, attackScratch, attackResult);
        }

        // 1. Spend MAPs (must happen before applyAttack clears the reserve)
        if (attackResult.mapCost() > 0) {
            war.spendMaps(actorNationId, attackResult.mapCost());
        }

        // 2. Clear map reserve and fortify flag via existing SimWar path
        war.applyAttack(actorNationId, attackType);

        // 3. Apply unit losses to both sides
        int[] attLosses = attackResult.attackerLosses();
        int[] defLosses = attackResult.defenderLosses();
        MilitaryUnit[] allUnits = MilitaryUnit.values;
        for (int i = 0; i < attLosses.length; i++) {
            if (attLosses[i] > 0 && SimUnits.isPurchasable(allUnits[i])) {
                attackerNation.removeUnits(allUnits[i], attLosses[i]);
            }
        }
        for (int i = 0; i < defLosses.length; i++) {
            if (defLosses[i] > 0 && SimUnits.isPurchasable(allUnits[i])) {
                defenderNation.removeUnits(allUnits[i], defLosses[i]);
            }
        }

        // 4. Apply infra damage to the defender in this attack (per-city, max-first per SimNation semantics)
        if (attackResult.infraDestroyed() > 0) {
            defenderNation.applyInfraDamage(attackResult.infraDestroyed());
        }

        // 5. Apply resistance deltas (deltas from resolver are signed; reduceResistance takes a positive amount)
        double attResDelta = attackResult.attackerResistanceDelta();
        double defResDelta = attackResult.defenderResistanceDelta();
        if (attResDelta < 0) {
            war.reduceResistance(actorNationId, (int) Math.round(-attResDelta));
        }
        if (defResDelta < 0) {
            war.reduceResistance(defenderNationId, (int) Math.round(-defResDelta));
        }

        // 6. Transfer loot: scalar money transfer from defender to attacker as a placeholder
        //    (full per-resource loot vector is a TODO when AttackOutcome carries a resource vector)
        if (attackResult.loot() > 0) {
            double transferred = defenderNation.subtractResource(ResourceType.MONEY, attackResult.loot());
            if (transferred > 0) {
                attackerNation.addResource(ResourceType.MONEY, transferred);
            }
        }

        // 7. Control flag changes and any cross-war stripping caused by the new local state.
        WarControlRules.reconcileAfterAttack(
            war,
            attackerNation,
            defenderNation,
            attackResult.controlDelta(),
            this::activeWarsForNation,
            economyProvider::onControlFlagChange
        );

        // 8. Check for defeat now that resistance may have reached 0
        resolveDefeatIfNeeded(war);
    }

    public void stepTurnStart() {
        applyTurnStartBookkeeping();
        clock.advanceTurn();
    }

    public void stepTurn(List<? extends SimAction> firstPassActions, List<? extends SimAction> secondPassActions) {
        ArrayList<List<? extends SimAction>> passes = new ArrayList<>(2);
        if (firstPassActions != null && !firstPassActions.isEmpty()) {
            passes.add(firstPassActions);
        }
        if (secondPassActions != null && !secondPassActions.isEmpty()) {
            passes.add(secondPassActions);
        }
        stepTurn(passes);
    }

    public void stepTurn(List<? extends List<? extends SimAction>> actionPasses) {
        applyTurnStartBookkeeping();
        List<List<? extends SimAction>> safePasses = normalizedPasses(actionPasses);
        if (safePasses.size() > tuning.intraTurnPasses()) {
            throw new IllegalArgumentException(
                    "Pass count " + safePasses.size() + " exceeds tuning.intraTurnPasses=" + tuning.intraTurnPasses()
            );
        }
        for (List<? extends SimAction> passActions : safePasses) {
            applyPassPhaseOrdered(passActions);
        }
        clock.advanceTurn();
    }

    public void stepTurn(Map<Integer, ? extends Actor> actors, Objective objective) {
        Objects.requireNonNull(actors, "actors");
        Objects.requireNonNull(objective, "objective");

        applyTurnStartBookkeeping();
        ResolutionMode mode = stateResolutionMode();
        for (int passIndex = 0; passIndex < tuning.intraTurnPasses(); passIndex++) {
            if (mode == ResolutionMode.STOCHASTIC) {
                applyActorPassStochastic(actors, objective);
            } else {
                applyActorPassDeterministic(actors, objective);
            }
        }
        clock.advanceTurn();
    }

    private static List<List<? extends SimAction>> normalizedPasses(List<? extends List<? extends SimAction>> actionPasses) {
        if (actionPasses == null || actionPasses.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<List<? extends SimAction>> normalized = new ArrayList<>(actionPasses.size());
        for (List<? extends SimAction> pass : actionPasses) {
            if (pass == null || pass.isEmpty()) {
                continue;
            }
            normalized.add(pass);
        }
        return normalized;
    }

    private void applyActorPassDeterministic(Map<Integer, ? extends Actor> actors, Objective objective) {
        List<SimAction> passActions = new ArrayList<>();
        for (SimNation nation : nationsById.values()) {
            Actor actor = actors.get(nation.nationId());
            if (actor == null || !shouldAct(nation)) {
                continue;
            }
            List<SimAction> decided = actor.decide(this, nation, buildDecisionContext(nation, objective));
            if (decided == null || decided.isEmpty()) {
                continue;
            }
            passActions.addAll(decided);
        }
        applyPassPhaseOrdered(passActions);
    }

    private void applyActorPassStochastic(Map<Integer, ? extends Actor> actors, Objective objective) {
        Map<Integer, List<SimAction>> pendingByNation = new HashMap<>();
        for (SimActionPhase phase : SimActionPhase.values()) {
            for (SimNation nation : nationsById.values()) {
                Actor actor = actors.get(nation.nationId());
                if (actor == null || !shouldAct(nation)) {
                    continue;
                }
                int applied = 0;
                while (applied < MAX_STOCHASTIC_ACTIONS_PER_PHASE) {
                    List<SimAction> decided = pendingByNation.get(nation.nationId());
                    if (decided == null) {
                        decided = actor.decide(this, nation, buildDecisionContext(nation, objective));
                        if (hasActionAtOrAfterPhase(decided, phase)) {
                            pendingByNation.put(nation.nationId(), decided);
                        }
                    }
                    SimAction next = firstActionForPhase(decided, phase);
                    if (next == null) {
                        if (!hasActionAfterPhase(decided, phase)) {
                            pendingByNation.remove(nation.nationId());
                        }
                        break;
                    }
                    apply(next);
                    applied++;
                    pendingByNation.remove(nation.nationId());
                }
                if (applied == MAX_STOCHASTIC_ACTIONS_PER_PHASE) {
                    throw new IllegalStateException(
                            "Actor exceeded per-phase stochastic action budget for nation " + nation.nationId()
                    );
                }
            }
        }
    }

    private DecisionContext buildDecisionContext(SimNation nation, Objective objective) {
        return new DecisionContext(this, currentTurn(), neighborNationsInRange(nation.nationId()), objective);
    }

    private Set<Integer> neighborNationsInRange(int nationId) {
        java.util.LinkedHashSet<Integer> neighbors = new java.util.LinkedHashSet<>();
        SimNation nation = requireNation(nationId);
        for (int candidateNationId : declareRangeIndex.nationIdsInWarRange(nation.score(), nationId)) {
            if (canDeclareWar(nationId, candidateNationId)) {
                neighbors.add(candidateNationId);
            }
        }
        return Collections.unmodifiableSet(neighbors);
    }

    private boolean shouldAct(SimNation nation) {
        return effectiveActivityAt(nation) >= tuning.activityActThreshold();
    }

    private static SimAction firstActionForPhase(List<SimAction> decided, SimActionPhase phase) {
        if (decided == null || decided.isEmpty()) {
            return null;
        }
        for (SimAction action : decided) {
            if (action == null) {
                throw new IllegalArgumentException("Actor returned null action");
            }
            if (action.phase() == phase) {
                return action;
            }
        }
        return null;
    }

    private static boolean hasActionAtOrAfterPhase(List<SimAction> decided, SimActionPhase phase) {
        if (decided == null || decided.isEmpty()) {
            return false;
        }
        for (SimAction action : decided) {
            if (action == null) {
                throw new IllegalArgumentException("Actor returned null action");
            }
            if (action.phase().ordinal() >= phase.ordinal()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActionAfterPhase(List<SimAction> decided, SimActionPhase phase) {
        if (decided == null || decided.isEmpty()) {
            return false;
        }
        for (SimAction action : decided) {
            if (action == null) {
                throw new IllegalArgumentException("Actor returned null action");
            }
            if (action.phase().ordinal() > phase.ordinal()) {
                return true;
            }
        }
        return false;
    }

    public boolean canDeclareWar(int attackerNationId, int defenderNationId) {
        SimNation attacker = requireNation(attackerNationId);
        SimNation defender = requireNation(defenderNationId);
        if (attackerNationId == defenderNationId) {
            return false;
        }
        if (defender.beigeTurns() > 0) {
            return false;
        }
        if (attacker.freeOffensiveSlots() <= 0 || defender.freeDefensiveSlots() <= 0) {
            return false;
        }
        if (!isInWarRange(attacker.score(), defender.score())) {
            return false;
        }
        if (warParticipationIndex.hasActivePairConflict(attackerNationId, defenderNationId)) {
            return false;
        }
        if (warParticipationIndex.isSameOpponentLockoutActive(attackerNationId, defenderNationId, currentTurn())) {
            return false;
        }
        return true;
    }

    public void declareWar(int warId, int attackerNationId, int defenderNationId, WarType warType) {
        Objects.requireNonNull(warType, "warType");
        ensureDeclaresAllowed(attackerNationId);
        if (!canDeclareWar(attackerNationId, defenderNationId)) {
            throw new IllegalStateException("Illegal declare: attacker=" + attackerNationId + ", defender=" + defenderNationId);
        }
        requireNation(attackerNationId).clearBeigeTurns();
        addWar(new SimWar(warId, attackerNationId, defenderNationId, warType));
    }

    public void buyUnits(int nationId, Map<MilitaryUnit, Integer> unitBuys) {
        ensureBuysAllowed(nationId);
        SimNation nation = requireNation(nationId);
        Objects.requireNonNull(unitBuys, "unitBuys");

        int[] projectedBoughtToday = new int[SimNationArrayStore.PURCHASABLE_COUNT];
        int[] projectedPending = new int[SimNationArrayStore.PURCHASABLE_COUNT];
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            int idx = SimUnits.purchasableIndex(unit);
            projectedBoughtToday[idx] = nation.unitsBoughtToday(unit);
            projectedPending[idx] = nation.pendingBuys(unit);
        }

        double[] totalCost = ResourceType.getBuffer();
        for (Map.Entry<MilitaryUnit, Integer> entry : unitBuys.entrySet()) {
            MilitaryUnit unit = Objects.requireNonNull(entry.getKey(), "unitBuys key");
            Integer qty = Objects.requireNonNull(entry.getValue(), "unitBuys value");
            int unitIdx = SimUnits.purchasableIndex(unit);
            if (unitIdx < 0) {
                throw new IllegalArgumentException("BuyUnitsAction does not support " + unit);
            }
            if (qty <= 0) {
                throw new IllegalArgumentException("Buy quantity must be > 0 for " + unit);
            }

            int nextBoughtToday = projectedBoughtToday[unitIdx] + qty;
            if (nextBoughtToday > nation.dailyBuyCap(unit)) {
                throw new IllegalStateException("Daily buy cap exceeded for " + unit + " on nation " + nationId);
            }

            int nextPending = projectedPending[unitIdx] + qty;
            if (nation.units(unit) + nextPending > nation.unitCap(unit)) {
                throw new IllegalStateException("Unit cap exceeded for " + unit + " on nation " + nationId);
            }

            double[] unitCost = economyProvider.unitBuyCostPerUnit(nation, unit);
            if (unitCost == null || unitCost.length != ResourceType.values.length) {
                throw new IllegalStateException("EconomyProvider returned invalid resource vector for " + unit + " on nation " + nationId);
            }
            double[] unitCostCopy = unitCost.clone();
            for (ResourceType resource : ResourceType.values) {
                int idx = resource.ordinal();
                double component = unitCostCopy[idx];
                if (Double.isNaN(component) || Double.isInfinite(component) || component < 0d) {
                    throw new IllegalStateException("EconomyProvider returned invalid cost component for " + unit + " on nation " + nationId + ", resource=" + resource);
                }
                totalCost[idx] += component * qty;
            }
            projectedBoughtToday[unitIdx] = nextBoughtToday;
            projectedPending[unitIdx] = nextPending;
        }

        if (!nation.canAfford(totalCost)) {
            throw new IllegalStateException(
                    "Insufficient resources for nation " + nationId
                            + ": required=" + ResourceType.toString(totalCost)
                            + ", available=" + ResourceType.toString(nation.resources())
            );
        }

        nation.spendResources(totalCost);

        for (Map.Entry<MilitaryUnit, Integer> entry : unitBuys.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            int qty = entry.getValue();
            nation.queueUnitBuy(unit, qty);
        }
    }

    public void applyInfraDamage(int nationId, double infraDestroyed) {
        if (infraDestroyed <= 0d) return;
        requireNation(nationId).applyInfraDamage(infraDestroyed);
    }

    public void applyUnitLosses(int nationId, Map<MilitaryUnit, Integer> losses) {
        SimNation nation = requireNation(nationId);
        Objects.requireNonNull(losses, "losses");
        for (Map.Entry<MilitaryUnit, Integer> entry : losses.entrySet()) {
            MilitaryUnit unit = Objects.requireNonNull(entry.getKey(), "losses key");
            Integer qty = Objects.requireNonNull(entry.getValue(), "losses value");
            if (qty <= 0) {
                throw new IllegalArgumentException("Loss quantity must be > 0 for " + unit);
            }
            nation.removeUnits(unit, qty);
        }
    }

    private void applyTurnStartBookkeeping() {
        actionIndexByNationThisTurn.clear();
        for (SimNation nation : nationsById.values()) {
            if (nation.advanceDayPhaseTurn()) {
                nation.resetUnitBuysToday();
            }
            nation.decrementBeigeTurns();
            nation.decrementPolicyCooldown();
        }
        for (SimWar war : warsById.values()) {
            war.regenerateMaps();
            war.expireIfNeeded(clock.currentTurn());
            resolveDefeatIfNeeded(war);
        }
        cleanupEndedWars();
        for (SimNation nation : nationsById.values()) {
            nation.materializePendingBuys();
        }
    }

    private void applyPassPhaseOrdered(List<? extends SimAction> passActions) {
        List<? extends SimAction> safeActions = passActions == null ? Collections.emptyList() : passActions;
        List<SimAction> preActions = new ArrayList<>();
        List<SimAction> declareActions = new ArrayList<>();
        List<SimAction> attackActions = new ArrayList<>();

        for (SimAction action : safeActions) {
            if (action == null) {
                throw new IllegalArgumentException("Pass contains null action");
            }
            SimActionPhase phase = action.phase();
            if (phase == SimActionPhase.PRE_ACTION) {
                preActions.add(action);
            } else if (phase == SimActionPhase.DECLARE) {
                declareActions.add(action);
            } else if (phase == SimActionPhase.ATTACK) {
                attackActions.add(action);
            } else {
                throw new IllegalStateException("Unhandled action phase: " + phase);
            }
        }

        for (SimAction action : preActions) {
            apply(action);
        }
        for (SimAction action : declareActions) {
            apply(action);
        }
        for (SimAction action : attackActions) {
            apply(action);
        }
    }

    private void resolveDefeatIfNeeded(SimWar war) {
        if (!war.isActive()) {
            return;
        }
        if (war.attackerResistance() <= 0 && war.defenderResistance() <= 0) {
            throw new IllegalStateException("Simultaneous defeat state is not yet modeled for war " + war.warId());
        }
        if (war.attackerResistance() <= 0) {
            concludeVictory(war, SimSide.DEFENDER);
            return;
        }
        if (war.defenderResistance() <= 0) {
            concludeVictory(war, SimSide.ATTACKER);
        }
    }

    private void concludeVictory(SimWar war, SimSide winnerSide) {
        SimNation winner;
        SimNation loser;
        if (winnerSide == SimSide.ATTACKER) {
            war.markAttackerVictory();
            winner = requireNation(war.attackerNationId());
            loser = requireNation(war.defenderNationId());
        } else {
            war.markDefenderVictory();
            winner = requireNation(war.defenderNationId());
            loser = requireNation(war.attackerNationId());
        }
        loser.applyVictoryInfraPercent(
                WarOutcomeMath.victoryInfraPercent(
                        winner.infraAttackModifier(AttackType.VICTORY),
                        loser.infraDefendModifier(AttackType.VICTORY),
                        war.warType(),
                        winnerSide == SimSide.ATTACKER
                )
        );

        loser.applyBeigeTurns(tuning.beigeTurnsOnDefeat());
        double transferredMoney = WarOutcomeMath.victoryNationLootTransferAmount(
                loser.resource(ResourceType.MONEY),
                winner.looterModifier(false),
                loser.lootModifier(),
                war.warType(),
                winnerSide == SimSide.ATTACKER
        );
        if (transferredMoney > 0d) {
            double debited = loser.subtractResource(ResourceType.MONEY, transferredMoney);
            if (debited > 0d) {
                winner.addResource(ResourceType.MONEY, debited);
                transferredMoney = debited;
            } else {
                transferredMoney = 0d;
            }
        }
        economyProvider.onVictoryLootTransferred(winner, loser, war, transferredMoney);
        allianceLootProvider.applyAllianceLoot(winner, loser, war);
    }

    private void cleanupEndedWars() {
        for (SimWar war : warsById.values()) {
            if (!war.shouldReleaseSlots()) {
                continue;
            }
            warParticipationIndex.onWarEnded(war, currentTurn());
            SimNation attacker = requireNation(war.attackerNationId());
            SimNation defender = requireNation(war.defenderNationId());
            attacker.releaseOffensiveSlot();
            defender.releaseDefensiveSlot();
            war.markSlotsReleased();
        }
    }

    private static boolean isInWarRange(double attackerScore, double defenderScore) {
        double min = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double max = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        return defenderScore >= min && defenderScore <= max;
    }

    /**
     * Returns a deep copy of this SimWorld sharing all providers and tuning (which are stateless
     * or effectively immutable). Nations and wars are fully independent copies.
     * Use this for local-search forks in BlitzPlanner — each candidate is evaluated on the fork,
     * then discarded without affecting the original world.
     *
      * <p>Nation state is world-owned SoA storage: static nation layout is shared across forks,
      * while mutable nation buffers are copied eagerly into the child. War state still deep-copies
      * per-war objects, which keeps current planner forks isolated without reintroducing per-nation
      * clone loops.</p>
     */
    public SimWorld fork() {
        SimWorld copy = new SimWorld(tuning, clock.deepCopy(), economyProvider, allianceLootProvider,
                activityProvider, resetTimeProvider, scenarioActionPolicy);
        copy.nationStore = nationStore.deepCopy();
        for (int i = 0; i < copy.nationStore.size(); i++) {
            SimNation nationCopy = new SimNation(copy.nationStore, i);
            copy.attachNation(nationCopy, i);
        }
        for (SimWar war : warsById.values()) {
            copy.warsById.put(war.warId(), war.deepCopy());
        }
        copy.warParticipationIndex = warParticipationIndex.deepCopy();
        copy.declareRangeIndex = declareRangeIndex.deepCopy();
        copy.actionIndexByNationThisTurn.putAll(actionIndexByNationThisTurn);
        return copy;
    }

    private void attachNation(SimNation nation, int nationIndex) {
        nation.bind(nationStore, nationIndex);
        nation.setScoreListener(this::onNationScoreChanged);
        nationsById.put(nation.nationId(), nation);
    }

    private void onNationScoreChanged(int nationId, double previousScore, double currentScore) {
        declareRangeIndex.onNationScoreChanged(nationId, previousScore, currentScore);
    }

    public ResolutionMode stateResolutionMode() {
        ResolutionMode mode = tuning.stateResolutionMode();
        if (mode == ResolutionMode.DETERMINISTIC_EV) {
            throw new IllegalStateException("DETERMINISTIC_EV is scoring-only and cannot drive SimWorld state transitions");
        }
        return mode;
    }

    private long attackStreamKey(int actorNationId) {
        int actionIndex = actionIndexByNationThisTurn.merge(actorNationId, 1, Integer::sum) - 1;
        // 16 bits for turn (bits 48-63), 32 bits for nation ID (bits 16-47), 16 bits for action index (bits 0-15).
        // Nation IDs in P&W can exceed 65535, so the previous 16-bit packing would alias IDs like 1 and 65537.
        long turnBits  = ((long) currentTurn()   & 0xFFFFL)       << 48;
        long nationBits = ((long) actorNationId  & 0xFFFF_FFFFL)  << 16;
        long actionBits =  (long) actionIndex    & 0xFFFFL;
        return turnBits | nationBits | actionBits;
    }

}
