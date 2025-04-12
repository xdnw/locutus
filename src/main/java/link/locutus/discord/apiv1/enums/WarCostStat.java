package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.Arrays;
import java.util.function.BiFunction;

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
    NAVAL(AttackType.NAVAL),
    PEACE(AttackType.PEACE),
    MISSILE_ATTACK(AttackType.MISSILE),
    NUKE_ATTACK(AttackType.NUKE),
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
    private final AttackType attack;

    WarCostStat() {
        this(null, null, null);
    }

    WarCostStat(MilitaryUnit unit, ResourceType resource, AttackType type) {
        this.unit = unit;
        this.resource = resource;
        this.attack = type;
    }

    WarCostStat(MilitaryUnit unit) {
        this(unit, null, null);
    }

    WarCostStat(ResourceType resource) {
        this(null, resource, null);
    }

    WarCostStat(AttackType attack) {
        this(null, null, attack);
    }

    public MilitaryUnit unit() {
        return this.unit;
    }

    public ResourceType resource() {
        return this.resource;
    }

    public AttackType attack() {
        return this.attack;
    }

    public final TriFunction<Boolean, DBWar, AbstractCursor, Double> getFunction(boolean excludeUnits, boolean excludeInfra, boolean excludeConsumption, boolean excludeLoot, boolean excludeBuildings, WarCostMode mode) {
        if (unit != null) {
            if (!mode.includeOffAttacks()) {
                return (attacker, war, attack) -> (double) (attacker ? attack.getAttUnitLosses(unit()) : 0);
            } else if (!mode.includeDefAttacks()) {
                return (attacker, war, attack) -> (double) (attacker ? 0 : attack.getDefUnitLosses(unit()));
            }
            return (attacker, war, attack) -> (double) (attacker ? attack.getAttUnitLosses(unit()) : attack.getDefUnitLosses(unit()));
        } else if (resource != null) {
            double[] rssBuffer = ResourceType.getBuffer();
            if (!mode.includeOffAttacks()) {
                return (attacker, war, attack) -> {
                    rssBuffer[resource.ordinal()] = 0;
                    return !attacker ? 0 : attack.addLosses(rssBuffer, war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings)[resource.ordinal()];
                };
            } else if (!mode.includeDefAttacks()) {
                return (attacker, war, attack) -> {
                    rssBuffer[resource.ordinal()] = 0;
                    return attacker ? 0 : attack.addLosses(rssBuffer, war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings)[resource.ordinal()];
                };
            }
            if (mode == WarCostMode.DEALT_NO_SUBTRACT_LOOT && !excludeLoot) {
                return (attacker, war, attack) -> {
                    rssBuffer[resource.ordinal()] = 0;
                    return attack.addLosses(rssBuffer, war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !attacker, !excludeBuildings)[resource.ordinal()];
                };
            }
            return (attacker, war, attack) -> {
                rssBuffer[resource.ordinal()] = 0;
                return attack.addLosses(rssBuffer, war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings)[resource.ordinal()];
            };
        } else if (attack != null) {
            if (!mode.includeDefAttacks()) {
                throw new IllegalArgumentException("Cannot count defender attacks for attack type: " + attack() + " (as defenders are not attacking)");
            }
            return (attacker, war, attack) -> attack.getAttack_type() == attack() ? 1d : 0d;
        } else {
            if (!mode.includeOffAttacks()) {
                return (attacker, war, attack) -> {
                    return !attacker ? 0 : attack.getLossesConverted(war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings);
                };
            } else if (!mode.includeDefAttacks()) {
                return (attacker, war, attack) -> {
                    return attacker ? 0 : attack.getLossesConverted(war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings);
                };
            }
            if (mode == WarCostMode.DEALT_NO_SUBTRACT_LOOT && !excludeLoot) {
                return (attacker, war, attack) -> {
                    return attack.getLossesConverted(war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !attacker, !excludeBuildings);
                };
            }
            return (attacker, war, attack) -> {
                return attack.getLossesConverted(war, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings);
            };
        }
    }
}
