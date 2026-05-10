package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.function.BiFunction;
import java.util.function.Function;

public enum WarCostMode {
    DEALT(true, false, true, false, true, true),
    DEALT_NO_SUBTRACT_LOOT(true, false, true, false, true, true),

    NET_DEALT(true, true, true, false, true, true),

    PROFIT(false, true, false, false, true, true),

    LOSSES(false, true, false, true, true, true),

    NET_LOSSES(true, true, false, true, true, true),

    ATTACKER_DEALT(true, false, true, false, false, true),
    DEFENDER_DEALT(true, false, true, false, true, false),
    ATTACKER_LOSSES(false, true, false, true, false, true),
    DEFENDER_LOSSES(false, true, false, true, true, false),
    ATTACKER_NET_DEALT(true, true, true, false, false, true),
    DEFENDER_NET_DEALT(true, true, true, false, true, false),
    ATTACKER_NET_LOSSES(true, true, false, true, false, true),
    DEFENDER_NET_LOSSES(true, true, false, true, true, false),
    ATTACKER_PROFIT(false, true, false, false, false, true),
    DEFENDER_PROFIT(false, true, false, false, true, false)

    ;

    private final boolean includeDealt;
    private final boolean includeReceived;
    private final boolean addDealt;
    private final boolean addReceived;
    private final boolean includeDefAttack;
    private final boolean includeOffAttacks;

    WarCostMode(boolean includeDealt, boolean includeReceived, boolean addDealt, boolean addReceived, boolean includeDefAttack, boolean includeOffAttacks) {
        this.includeDealt = includeDealt;
        this.includeReceived = includeReceived;
        this.addDealt = addDealt;
        this.addReceived = addReceived;
        this.includeDefAttack = includeDefAttack;
        this.includeOffAttacks = includeOffAttacks;
    }

    public boolean includeDealt() {
        return includeDealt;
    }

    public boolean includeReceived() {
        return includeReceived;
    }

    public boolean addDealt() {
        return addDealt;
    }

    public boolean addReceived() {
        return addReceived;
    }

    public boolean includeDefAttacks() {
        return includeDefAttack;
    }

    public boolean includeOffAttacks() {
        return includeOffAttacks;
    }

    public boolean isProfitLike() {
        return !includeDealt() && includeReceived() && !addReceived();
    }

    public TriFunction<Boolean, DBWar, AbstractCursor, Double> getAttackFunc(TriFunction<Boolean, DBWar, AbstractCursor, Double> valueFunc) {
        TriFunction<Boolean, DBWar, AbstractCursor, Double> delegate = getUncheckedAttackFunc(valueFunc);
        if (includesAllAttackOrigins()) {
            return delegate;
        }
        return (rankedSideIsWarAttacker, war, attack) ->
                includesAttack(war, attack) ? delegate.apply(rankedSideIsWarAttacker, war, attack) : 0d;
    }

    public TriFunction<Boolean, DBWar, AbstractCursor, Double> getUncheckedAttackFunc(TriFunction<Boolean, DBWar, AbstractCursor, Double> valueFunc) {
        if (includeDealt()) {
            if (includeReceived()) {
                if (addDealt()) {
                    if (addReceived()) {
                        return (rankedSideIsWarAttacker, war, attack) ->
                                valueFunc.apply(!rankedSideIsWarAttacker, war, attack)
                                        + valueFunc.apply(rankedSideIsWarAttacker, war, attack);
                    }
                    return (rankedSideIsWarAttacker, war, attack) ->
                            valueFunc.apply(!rankedSideIsWarAttacker, war, attack)
                                    - valueFunc.apply(rankedSideIsWarAttacker, war, attack);
                }
                if (addReceived()) {
                    return (rankedSideIsWarAttacker, war, attack) ->
                            valueFunc.apply(rankedSideIsWarAttacker, war, attack)
                                    - valueFunc.apply(!rankedSideIsWarAttacker, war, attack);
                }
                return (rankedSideIsWarAttacker, war, attack) ->
                        -valueFunc.apply(!rankedSideIsWarAttacker, war, attack)
                                - valueFunc.apply(rankedSideIsWarAttacker, war, attack);
            }
            if (addDealt()) {
                return (rankedSideIsWarAttacker, war, attack) -> valueFunc.apply(!rankedSideIsWarAttacker, war, attack);
            }
            return (rankedSideIsWarAttacker, war, attack) -> -valueFunc.apply(!rankedSideIsWarAttacker, war, attack);
        }
        if (includeReceived()) {
            if (addReceived()) {
                return valueFunc;
            }
            return (rankedSideIsWarAttacker, war, attack) -> -valueFunc.apply(rankedSideIsWarAttacker, war, attack);
        }
        return (rankedSideIsWarAttacker, war, attack) -> 0d;
    }

    public boolean includesAttack(DBWar war, AbstractCursor attack) {
        if (war == null) {
            return true;
        }
        if (attack.getAttacker_id() == war.getAttacker_id()) {
            return includeOffAttacks();
        }
        if (attack.getAttacker_id() == war.getDefender_id()) {
            return includeDefAttacks();
        }
        return true;
    }

    public boolean includesAllAttackOrigins() {
        return includeOffAttacks() && includeDefAttacks();
    }

    public BiFunction<Double, Double, Double> getCostFunc() {
        Function<Double, Double> applyDealt;
        Function<Double, Double> applyReceived;
        if (includeDealt()) {
            if (addDealt()) {
                applyDealt = value -> value;
            } else {
                applyDealt = value -> -value;
            }
        } else {
            applyDealt = value -> 0d;
        }
        if (includeReceived()) {
            if (addReceived()) {
                applyReceived = value -> value;
            } else {
                applyReceived = value -> -value;
            }
        } else {
            applyReceived = value -> 0d;
        }
        BiFunction<Double, Double, Double> applyBoth;
        if (applyDealt != null) {
            if (applyReceived != null) {
                applyBoth = (received, dealt) -> applyDealt.apply(dealt) + applyReceived.apply(received);
            } else {
                applyBoth = (received, dealt) -> applyDealt.apply(dealt);
            }
        } else if (applyReceived != null) {
            applyBoth = (received, dealt) -> applyReceived.apply(received);
        } else {
            applyBoth = (received, dealt) -> 0d;
        }
        return applyBoth;
    }

}
