package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;

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
    GROUND_ATTACK(AttackType.GROUND),
    VICTORY_ATTACK(AttackType.VICTORY),
    FORTIFY_ATTACK(AttackType.FORTIFY),
    A_LOOT_ATTACK(AttackType.A_LOOT),
    AIRSTRIKE_INFRA_ATTACK(AttackType.AIRSTRIKE_INFRA),
    AIRSTRIKE_SOLDIER_ATTACK(AttackType.AIRSTRIKE_SOLDIER),
    AIRSTRIKE_TANK_ATTACK(AttackType.AIRSTRIKE_TANK),
    AIRSTRIKE_MONEY_ATTACK(AttackType.AIRSTRIKE_MONEY),
    AIRSTRIKE_SHIP_ATTACK(AttackType.AIRSTRIKE_SHIP),
    AIRSTRIKE_AIRCRAFT_ATTACK(AttackType.AIRSTRIKE_AIRCRAFT),
    NAVAL_ATTACK(AttackType.NAVAL),
    PEACE_ATTACK(AttackType.PEACE),
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

    public final BiFunction<Boolean, AbstractCursor, Double> getFunction(boolean excludeUnits, boolean excludeInfra, boolean excludeConsumption, boolean excludeLoot, boolean excludeBuildings) {
        if (unit != null) {
            return (attacker, attack) -> (double) attack.getUnitLosses(unit(), !attacker);
        } else if (resource != null) {
            double[] rssBuffer = ResourceType.getBuffer();
            Arrays.fill(rssBuffer, 0);
            return (attacker, attack) -> {
                rssBuffer[resource.ordinal()] = 0;
                return attack.getLosses(rssBuffer, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings)[resource.ordinal()];
            };
        } else if (attack != null) {
            return (attacker, attack) -> attack.getAttack_type() == attack() ? 1d : 0d;
        } else {
            double[] rssBuffer = ResourceType.getBuffer();
            return (attacker, attack) -> {
                Arrays.fill(rssBuffer, 0);
                return attack.getLossesConverted(rssBuffer, attacker, !excludeUnits, !excludeInfra, !excludeConsumption, !excludeLoot, !excludeBuildings);
            };
        }
    }
}
