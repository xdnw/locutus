package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.function.Predicate;

public enum WarCostStat {
    WAR_VALUE,
    SOLDIER(MilitaryUnit.SOLDIER),
    TANK(MilitaryUnit.TANK),
    AIRCRAFT(MilitaryUnit.AIRCRAFT),
    SHIP(MilitaryUnit.SHIP),
    MISSILE(MilitaryUnit.MISSILE),
    NUKE(MilitaryUnit.NUKE),
    GROUND(AttackType.GROUND),
    VICTORY(AttackType.VICTORY),
    FORTIFY(AttackType.FORTIFY),
    A_LOOT(AttackType.A_LOOT),
    AIRSTRIKE_INFRA(AttackType.AIRSTRIKE_INFRA),
    AIRSTRIKE_SOLDIER(AttackType.AIRSTRIKE_SOLDIER),
    AIRSTRIKE_TANK(AttackType.AIRSTRIKE_TANK),
    AIRSTRIKE_MONEY(AttackType.AIRSTRIKE_MONEY),
    AIRSTRIKE_SHIP(AttackType.AIRSTRIKE_SHIP),
    AIRSTRIKE_AIRCRAFT(AttackType.AIRSTRIKE_AIRCRAFT),
    NAVAL_SHIP(AttackType.NAVAL),
    NAVAL_AIR(AttackType.NAVAL_AIR),
    NAVAL_GROUND(AttackType.NAVAL_GROUND),
    NAVAL_INFRA(AttackType.NAVAL_INFRA),
    PEACE(AttackType.PEACE),
    MISSILE_ATTACK(AttackType.MISSILE),
    NUKE_ATTACK(AttackType.NUKE),
    PROJECTILE_ATTACK(f -> switch (f) {
        case NUKE,MISSILE -> true;
        default -> false;
    }),
    MONEY(ResourceType.MONEY),
    FOOD(ResourceType.FOOD),
    COAL(ResourceType.COAL),
    OIL(ResourceType.OIL),
    URANIUM(ResourceType.URANIUM),
    IRON(ResourceType.IRON),
    BAUXITE(ResourceType.BAUXITE),
    LEAD(ResourceType.LEAD),
    GASOLINE(ResourceType.GASOLINE),
    MUNITIONS(ResourceType.MUNITIONS),
    STEEL(ResourceType.STEEL),
    ALUMINUM(ResourceType.ALUMINUM),
    ;

    private final MilitaryUnit unit;
    private final ResourceType resource;
    private final Predicate<AttackType> isAttack;

    WarCostStat() {
        this(null, null, null);
    }

    WarCostStat(MilitaryUnit unit, ResourceType resource, Predicate<AttackType> isAttack) {
        this.unit = unit;
        this.resource = resource;
        this.isAttack = isAttack;
    }

    WarCostStat(MilitaryUnit unit) {
        this(unit, null, null);
    }

    WarCostStat(ResourceType resource) {
        this(null, resource, null);
    }

    WarCostStat(AttackType attack) {
        this(null, null, attack == null ? null : f -> f == attack);
    }

    WarCostStat(Predicate<AttackType> isAttack) {
        this(null, null, isAttack);
    }

    public boolean isAttack() {
        return isAttack != null;
    }

    public MilitaryUnit unit() {
        return this.unit;
    }

    public ResourceType resource() {
        return this.resource;
    }

    public final TriFunction<Boolean, DBWar, AbstractCursor, Double> getFunction(boolean excludeUnits, boolean excludeInfra, boolean excludeConsumption, boolean excludeLoot, boolean excludeBuildings, WarCostMode mode) {
        if (unit != null) {
            return (rankedSideIsWarAttacker, war, attack) -> {
                boolean rankedSideIsAttackAttacker = rankedSideIsAttackAttacker(rankedSideIsWarAttacker, war, attack);
                return (double) (rankedSideIsAttackAttacker ? attack.getAttUnitLosses(unit()) : attack.getDefUnitLosses(unit()));
            };
        } else if (resource != null) {
            double[] rssBuffer = ResourceType.getBuffer();
            return (rankedSideIsWarAttacker, war, attack) -> {
                rssBuffer[resource.ordinal()] = 0;
                boolean rankedSideIsAttackAttacker = rankedSideIsAttackAttacker(rankedSideIsWarAttacker, war, attack);
                return attack.addLosses(
                        rssBuffer,
                        war,
                        rankedSideIsAttackAttacker,
                        !excludeUnits,
                        !excludeInfra,
                        !excludeConsumption,
                        includeLoot(mode, excludeLoot, rankedSideIsAttackAttacker),
                        !excludeBuildings
                )[resource.ordinal()];
            };
        } else if (isAttack != null) {
            return (rankedSideIsWarAttacker, war, attack) -> {
                if (!isAttack.test(attack.getAttack_type())) {
                    return 0d;
                }
                return rankedSideIsAttackAttacker(rankedSideIsWarAttacker, war, attack) ? 0d : 1d;
            };
        } else {
            return (rankedSideIsWarAttacker, war, attack) -> {
                boolean rankedSideIsAttackAttacker = rankedSideIsAttackAttacker(rankedSideIsWarAttacker, war, attack);
                return attack.getLossesConverted(
                        war,
                        rankedSideIsAttackAttacker,
                        !excludeUnits,
                        !excludeInfra,
                        !excludeConsumption,
                        includeLoot(mode, excludeLoot, rankedSideIsAttackAttacker),
                        !excludeBuildings
                );
            };
        }
    }

    private static boolean rankedSideIsAttackAttacker(boolean rankedSideIsWarAttacker, DBWar war, AbstractCursor attack) {
        if (war == null) {
            return rankedSideIsWarAttacker;
        }

        int rankedNationId = rankedSideIsWarAttacker ? war.getAttacker_id() : war.getDefender_id();
        if (rankedNationId == attack.getAttacker_id()) {
            return true;
        }
        if (rankedNationId == attack.getDefender_id()) {
            return false;
        }
        return rankedSideIsWarAttacker;
    }

    private static boolean includeLoot(WarCostMode mode, boolean excludeLoot, boolean rankedSideIsAttackAttacker) {
        if (excludeLoot) {
            return false;
        }
        return mode != WarCostMode.DEALT_NO_SUBTRACT_LOOT || !rankedSideIsAttackAttacker;
    }

}
