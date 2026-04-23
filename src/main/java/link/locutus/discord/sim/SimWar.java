package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.combat.WarControlRules;
import link.locutus.discord.sim.combat.state.BasicWarStateView;
import link.locutus.discord.sim.combat.state.WarStateView;

import java.util.Objects;

public final class SimWar implements WarControlRules.MutableWarControlState {
    public static final int INITIAL_RESISTANCE = 100;
    public static final int INITIAL_TURNS_REMAINING = 60;
    public static final int INITIAL_MAPS = 6;
    public static final int MAP_CAP = 12;

    private final int warId;
    private final int attackerNationId;
    private final int defenderNationId;
    private final WarType warType;
    private boolean attackerFortified;
    private boolean defenderFortified;
    private int attackerResistance = INITIAL_RESISTANCE;
    private int defenderResistance = INITIAL_RESISTANCE;
    private int attackerMaps = INITIAL_MAPS;
    private int defenderMaps = INITIAL_MAPS;
    private int attackerReservedMaps;
    private int defenderReservedMaps;
    private int startTurn = -1;
    private WarStatus status = WarStatus.ACTIVE;
    private boolean slotsReleased;

    // Control flags: null means no one has the control, otherwise ATTACKER or DEFENDER holds it
    private SimSide groundControlOwner = null;
    private SimSide airSuperiorityOwner = null;
    private SimSide blockadeOwner = null;

    public SimWar(int warId, int attackerNationId, int defenderNationId, WarType warType) {
        this(warId, attackerNationId, defenderNationId, warType, -1);
    }

    private SimWar(int warId, int attackerNationId, int defenderNationId, WarType warType, int startTurn) {
        if (attackerNationId == defenderNationId) {
            throw new IllegalArgumentException("A war requires two distinct nations");
        }
        this.warId = warId;
        this.attackerNationId = attackerNationId;
        this.defenderNationId = defenderNationId;
        this.warType = Objects.requireNonNull(warType, "warType");
        this.startTurn = startTurn;
    }

    public int warId() {
        return warId;
    }

    public int attackerNationId() {
        return attackerNationId;
    }

    public int defenderNationId() {
        return defenderNationId;
    }

    public WarType warType() {
        return warType;
    }

    public boolean attackerFortified() {
        return attackerFortified;
    }

    public boolean defenderFortified() {
        return defenderFortified;
    }

    public int attackerResistance() {
        return attackerResistance;
    }

    public int defenderResistance() {
        return defenderResistance;
    }

    public int startTurn() {
        return requireBoundStartTurn();
    }

    public int remainingTurns(int currentTurn) {
        int elapsed = Math.max(0, currentTurn - requireBoundStartTurn());
        return Math.max(0, INITIAL_TURNS_REMAINING - elapsed);
    }

    public int attackerMaps() {
        return attackerMaps;
    }

    public int defenderMaps() {
        return defenderMaps;
    }

    public int attackerReservedMaps() {
        return attackerReservedMaps;
    }

    public int defenderReservedMaps() {
        return defenderReservedMaps;
    }

    public int attackerSpendableMaps() {
        return attackerMaps - attackerReservedMaps;
    }

    public int defenderSpendableMaps() {
        return defenderMaps - defenderReservedMaps;
    }

    public WarStatus status() {
        return status;
    }

    public boolean hasPendingPeaceOffer() {
        return status == WarStatus.ATTACKER_OFFERED_PEACE || status == WarStatus.DEFENDER_OFFERED_PEACE;
    }

    public boolean isActive() {
        return status.isActive();
    }

    public SimSide groundControlOwner() {
        return groundControlOwner;
    }

    public SimSide airSuperiorityOwner() {
        return airSuperiorityOwner;
    }

    public SimSide blockadeOwner() {
        return blockadeOwner;
    }

    @Override
    public Integer groundControlNationId() {
        return controlNationId(groundControlOwner);
    }

    @Override
    public Integer airSuperiorityNationId() {
        return controlNationId(airSuperiorityOwner);
    }

    @Override
    public Integer blockadeNationId() {
        return controlNationId(blockadeOwner);
    }

    @Override
    public void setGroundControlNationId(Integer nationId) {
        groundControlOwner = controlSide(nationId);
    }

    @Override
    public void setAirSuperiorityNationId(Integer nationId) {
        airSuperiorityOwner = controlSide(nationId);
    }

    @Override
    public void setBlockadeNationId(Integer nationId) {
        blockadeOwner = controlSide(nationId);
    }

    /**
        * Apply direct same-war control ownership changes without cross-war reconciliation.
        * This is a compatibility/setup helper for callers that need to seed war state explicitly.
     * 
     * @param actorNationId the nation performing the attack
     * @param groundControlDelta +1 if attacker gained ground, -1 if defender gained ground, 0 for no change
     * @param airSuperiorityDelta similar semantics
     * @param blockadeDelta similar semantics
     * @return true if blockade state changed (triggering EconomyProvider callback)
     */
    public boolean applyControlFlagChanges(
            int actorNationId,
            int groundControlDelta,
            int airSuperiorityDelta,
            int blockadeDelta
    ) {
        ensureActive();
        int defenderNationId = actorNationId == this.attackerNationId ? this.defenderNationId : this.attackerNationId;
        return WarControlRules.applySameWarDelta(
                this,
                actorNationId,
                defenderNationId,
                link.locutus.discord.sim.combat.ControlFlagDelta.of(
                        groundControlDelta,
                        airSuperiorityDelta,
                        blockadeDelta,
                        false,
                        false,
                        false
                )
        );
    }

    boolean shouldReleaseSlots() {
        return !status.isActive() && !slotsReleased;
    }

    void markSlotsReleased() {
        slotsReleased = true;
    }

    public SimSide sideForNation(int nationId) {
        if (nationId == attackerNationId) {
            return SimSide.ATTACKER;
        }
        if (nationId == defenderNationId) {
            return SimSide.DEFENDER;
        }
        throw new IllegalArgumentException("Nation " + nationId + " is not in war " + warId);
    }

    public void applyAttack(int actorNationId, AttackType attackType) {
        Objects.requireNonNull(attackType, "attackType");
        ensureActive();
        SimSide actorSide = sideForNation(actorNationId);
        clearMapReserve(actorSide);
        if (attackType == AttackType.FORTIFY) {
            setFortified(actorSide, true);
            return;
        }
        if (hasPendingPeaceOffer()) {
            status = WarStatus.ACTIVE;
        }
        setFortified(actorSide, false);
    }

    public void reserveMaps(int actorNationId, int mapsToReserve) {
        ensureActive();
        if (mapsToReserve <= 0) {
            throw new IllegalArgumentException("mapsToReserve must be > 0");
        }
        SimSide actorSide = sideForNation(actorNationId);
        int mapsAvailable = maps(actorSide);
        if (mapsToReserve > mapsAvailable) {
            throw new IllegalStateException("Cannot reserve " + mapsToReserve + " MAPs with only " + mapsAvailable + " available");
        }
        setReserve(actorSide, mapsToReserve);
    }

    public void releaseReservedMaps(int actorNationId) {
        ensureActive();
        clearMapReserve(sideForNation(actorNationId));
    }

    void regenerateMaps() {
        if (!isActive()) {
            return;
        }
        attackerMaps = Math.min(MAP_CAP, attackerMaps + 1);
        defenderMaps = Math.min(MAP_CAP, defenderMaps + 1);
    }

    public void offerPeace(int actorNationId) {
        ensureActive();
        SimSide actorSide = sideForNation(actorNationId);
        if (hasPendingPeaceOffer()) {
            throw new IllegalStateException("Peace offer already pending in war " + warId);
        }
        status = actorSide == SimSide.ATTACKER
                ? WarStatus.ATTACKER_OFFERED_PEACE
                : WarStatus.DEFENDER_OFFERED_PEACE;
    }

    public void acceptPeace(int actorNationId) {
        ensureActive();
        SimSide actorSide = sideForNation(actorNationId);
        if (!hasPendingPeaceOffer()) {
            throw new IllegalStateException("No peace offer to accept for war " + warId);
        }
        WarStatus pendingStatus = status;
        if ((pendingStatus == WarStatus.ATTACKER_OFFERED_PEACE && actorSide == SimSide.ATTACKER)
                || (pendingStatus == WarStatus.DEFENDER_OFFERED_PEACE && actorSide == SimSide.DEFENDER)) {
            throw new IllegalStateException("Peace offer must be accepted by the opposite side in war " + warId);
        }
        status = WarStatus.PEACE;
    }

    void reduceResistance(int nationId, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        ensureActive();
        SimSide side = sideForNation(nationId);
        if (side == SimSide.ATTACKER) {
            attackerResistance = Math.max(0, attackerResistance - amount);
        } else {
            defenderResistance = Math.max(0, defenderResistance - amount);
        }
    }

    void markAttackerVictory() {
        ensureActive();
        status = WarStatus.ATTACKER_VICTORY;
    }

    void markDefenderVictory() {
        ensureActive();
        status = WarStatus.DEFENDER_VICTORY;
    }

    void bindStartTurn(int currentTurn) {
        if (currentTurn < 0) {
            throw new IllegalArgumentException("currentTurn must be >= 0");
        }
        if (startTurn >= 0 && startTurn != currentTurn) {
            throw new IllegalStateException("War " + warId + " already bound to start turn " + startTurn);
        }
        startTurn = currentTurn;
    }

    void expireIfNeeded(int currentTurn) {
        if (!isActive()) {
            return;
        }
        if (currentTurn >= expirationTurnStart()) {
            status = WarStatus.EXPIRED;
        }
    }

    /**
     * Build a WarStateView from the perspective of the war's attacker.
     * Use {@link #asWarStateViewFor(SimSide)} when the actor may be the defender.
     */
    public WarStateView asWarStateView() {
        return asWarStateViewFor(SimSide.ATTACKER);
    }

    /**
     * Build a WarStateView from the perspective of the given actor side.
     * When the defender is performing an attack (e.g. a counter), the resolver still
     * expects an "attacker" slot that corresponds to the nation actually attacking, so
     * control-flag ownership and fortify flags must be flipped accordingly.
     */
    public WarStateView asWarStateViewFor(SimSide actorSide) {
        boolean actorIsAttacker = actorSide == SimSide.ATTACKER;
        int blockade;
        if (blockadeOwner == null) {
            blockade = WarStateView.BLOCKADE_NONE;
        } else if (blockadeOwner == SimSide.ATTACKER) {
            blockade = actorIsAttacker ? WarStateView.BLOCKADE_ATTACKER : WarStateView.BLOCKADE_DEFENDER;
        } else {
            blockade = actorIsAttacker ? WarStateView.BLOCKADE_DEFENDER : WarStateView.BLOCKADE_ATTACKER;
        }
        boolean resolverAttHasAir  = actorIsAttacker ? (airSuperiorityOwner == SimSide.ATTACKER) : (airSuperiorityOwner == SimSide.DEFENDER);
        boolean resolverDefHasAir  = actorIsAttacker ? (airSuperiorityOwner == SimSide.DEFENDER) : (airSuperiorityOwner == SimSide.ATTACKER);
        boolean resolverAttHasGround = actorIsAttacker ? (groundControlOwner == SimSide.ATTACKER) : (groundControlOwner == SimSide.DEFENDER);
        boolean resolverDefFortified = actorIsAttacker ? defenderFortified : attackerFortified;
        boolean resolverAttFortified = actorIsAttacker ? attackerFortified : defenderFortified;
        int resolverAttMaps = actorIsAttacker ? attackerSpendableMaps() : defenderSpendableMaps();
        int resolverDefMaps = actorIsAttacker ? defenderSpendableMaps() : attackerSpendableMaps();
        int resolverAttRes  = actorIsAttacker ? attackerResistance : defenderResistance;
        int resolverDefRes  = actorIsAttacker ? defenderResistance : attackerResistance;
        return new BasicWarStateView(
                warType,
            actorIsAttacker,
                resolverAttHasAir,
                resolverDefHasAir,
                resolverAttHasGround,
                false, // defenderHasGroundControl not tracked separately
                resolverAttFortified,
                resolverDefFortified,
                resolverAttMaps,
                resolverDefMaps,
                resolverAttRes,
                resolverDefRes,
                blockade
        );
    }

    void spendMaps(int actorNationId, int amount) {
        if (amount <= 0) return;
        SimSide side = sideForNation(actorNationId);
        if (side == SimSide.ATTACKER) {
            if (amount > attackerMaps) {
                throw new IllegalStateException("Not enough MAPs for attacker in war " + warId + ": need " + amount + ", have " + attackerMaps);
            }
            attackerMaps -= amount;
        } else {
            if (amount > defenderMaps) {
                throw new IllegalStateException("Not enough MAPs for defender in war " + warId + ": need " + amount + ", have " + defenderMaps);
            }
            defenderMaps -= amount;
        }
    }

    private int maps(SimSide side) {
        return side == SimSide.ATTACKER ? attackerMaps : defenderMaps;
    }

    private void setReserve(SimSide side, int reserveAmount) {
        if (side == SimSide.ATTACKER) {
            attackerReservedMaps = reserveAmount;
        } else {
            defenderReservedMaps = reserveAmount;
        }
    }

    private void clearMapReserve(SimSide side) {
        if (side == SimSide.ATTACKER) {
            attackerReservedMaps = 0;
        } else {
            defenderReservedMaps = 0;
        }
    }

    private void setFortified(SimSide side, boolean fortified) {
        if (side == SimSide.ATTACKER) {
            attackerFortified = fortified;
        } else {
            defenderFortified = fortified;
        }
    }

    private void ensureActive() {
        if (!status.isActive()) {
            throw new IllegalStateException("War " + warId + " is no longer active: " + status);
        }
    }

    private int requireBoundStartTurn() {
        if (startTurn < 0) {
            throw new IllegalStateException("War " + warId + " has not been bound to a start turn");
        }
        return startTurn;
    }

    private int expirationTurnStart() {
        return requireBoundStartTurn() + INITIAL_TURNS_REMAINING - 1;
    }

    private static SimSide opposite(SimSide side) {
        return side == SimSide.ATTACKER ? SimSide.DEFENDER : SimSide.ATTACKER;
    }

    private Integer controlNationId(SimSide owner) {
        if (owner == null) {
            return null;
        }
        return owner == SimSide.ATTACKER ? attackerNationId : defenderNationId;
    }

    private SimSide controlSide(Integer nationId) {
        if (nationId == null) {
            return null;
        }
        return sideForNation(nationId);
    }

    /** Returns a deep copy of this war's mutable state. Identity (warId, nationIds, warType) is shared. */
    public SimWar deepCopy() {
        SimWar copy = new SimWar(warId, attackerNationId, defenderNationId, warType, startTurn);
        copy.attackerFortified = this.attackerFortified;
        copy.defenderFortified = this.defenderFortified;
        copy.attackerResistance = this.attackerResistance;
        copy.defenderResistance = this.defenderResistance;
        copy.attackerMaps = this.attackerMaps;
        copy.defenderMaps = this.defenderMaps;
        copy.attackerReservedMaps = this.attackerReservedMaps;
        copy.defenderReservedMaps = this.defenderReservedMaps;
        copy.status = this.status;
        copy.slotsReleased = this.slotsReleased;
        copy.groundControlOwner = this.groundControlOwner;
        copy.airSuperiorityOwner = this.airSuperiorityOwner;
        copy.blockadeOwner = this.blockadeOwner;
        return copy;
    }
}
