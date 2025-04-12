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

    public TriFunction<Boolean, DBWar, AbstractCursor, Double> getAttackFunc(TriFunction<Boolean, DBWar, AbstractCursor, Double> valueFunc) {
        TriFunction<Boolean, DBWar, AbstractCursor, Double> applyDealt;
        TriFunction<Boolean, DBWar, AbstractCursor, Double> applyReceived;
        if (includeDealt()) {
            if (addDealt()) {
                applyDealt = (isAttacker, war, attack) -> valueFunc.apply(!isAttacker, war, attack);
            } else {
                applyDealt = (isAttacker, war, attack) -> -valueFunc.apply(!isAttacker, war, attack);
            }
        } else {
            applyDealt = null;
        }
        if (includeReceived()) {
            if (addReceived()) {
                applyReceived = (isAttacker, war, attack) -> valueFunc.apply(isAttacker, war, attack);
            } else {
                applyReceived = (isAttacker, war, attack) -> -valueFunc.apply(isAttacker, war, attack);
            }
        } else {
            applyReceived = null;
        }

        TriFunction<Boolean, DBWar, AbstractCursor, Double> applyBoth;
        if (applyDealt != null) {
            if (applyReceived != null) {
                applyBoth = (isAttacker, war, attack) -> applyDealt.apply(isAttacker, war, attack) + applyReceived.apply(isAttacker, war, attack);
            } else {
                applyBoth = applyDealt;
            }
        } else if (applyReceived != null) {
            applyBoth = applyReceived;
        } else {
            applyBoth = (isAttacker, war, attack) -> 0d;
        }
        return applyBoth;
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
