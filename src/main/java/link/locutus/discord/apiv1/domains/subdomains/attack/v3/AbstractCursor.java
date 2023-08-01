package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.BitBuffer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class AbstractCursor implements IAttack2 {
    protected DBWar war_cached;
    protected int war_attack_id;
    protected long date;
    protected int war_id;
    protected int attacker_id;
    protected int defender_id;

    public void load(DBAttack legacy) {
        war_attack_id = legacy.getWar_attack_id();
        date = Math.max(TimeUtil.getOrigin(), legacy.getDate());
        war_id = legacy.getWar_id();
        attacker_id = legacy.getAttacker_id();
        defender_id = legacy.getDefender_id();
    }

    @Override
    public abstract AttackType getAttack_type();
    @Override
    public abstract SuccessType getSuccess();
    public void load(WarAttack attack) {
        war_cached = null;
        war_attack_id = attack.getId();
        date = attack.getDate().toEpochMilli();
        long now =  System.currentTimeMillis();
        if (date > now) {
            System.err.println("Attack date is in the future: " + date);
            date = now;
        }
        war_id = attack.getWar_id();
        attacker_id = attack.getAtt_id();
        defender_id = attack.getDef_id();
    }

    public void serialze(BitBuffer output) {
        output.writeInt(war_attack_id);
        output.writeVarLong(date - TimeUtil.getOrigin());
        output.writeInt(war_id);
        output.writeBit(attacker_id > defender_id);
    }

    public void initialize(DBWar war, BitBuffer input) {
        war_cached = war;
        war_attack_id = input.readInt();
        date = input.readVarLong() + TimeUtil.getOrigin();
        war_id = input.readInt();
        boolean isAttackerGreater = input.readBit();
        if (isAttackerGreater) {
            if (war.attacker_id > war.defender_id) {
                attacker_id = war.attacker_id;
                defender_id = war.defender_id;
            } else {
                attacker_id = war.defender_id;
                defender_id = war.attacker_id;
            }
        } else {
            if (war.attacker_id > war.defender_id) {
                attacker_id = war.defender_id;
                defender_id = war.attacker_id;
            } else {
                attacker_id = war.attacker_id;
                defender_id = war.defender_id;
            }
        }
    }

    public void load(DBWar war, BitBuffer input) {

    }


    @Override
    public boolean isAttackerIdGreater() {
        return attacker_id > defender_id;
    }

    @Override
    public int getAttacker_id() {
        return attacker_id;
    }

    @Override
    public int getDefender_id() {
        return defender_id;
    }

    @Override
    public int getWar_attack_id() {
        return war_attack_id;
    }

    public long getDate() {
        return date;
    }

    @Override
    public int getWar_id() {
        return war_id;
    }

    public DBWar getWar() {
        if (war_cached == null) {
            Locutus lc = Locutus.imp();
            if (lc != null) {
                war_cached = lc.getWarDb().getWar(war_id);
            }
        }
        return war_cached;
    }

    @Override
    public Map<ResourceType, Double> getLosses(boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings) {
        Map<ResourceType, Double> losses = new HashMap<>();
        if (units) {
            Map<MilitaryUnit, Integer> unitLosses = getUnitLosses(attacker);
            for (Map.Entry<MilitaryUnit, Integer> entry : unitLosses.entrySet()) {
                MilitaryUnit unit = entry.getKey();
                int amt = entry.getValue();
                if (amt > 0) {
                    double[] cost = unit.getCost(amt);
                    for (ResourceType type : ResourceType.values) {
                        double rssCost = cost[type.ordinal()];
                        if (rssCost > 0) {
                            losses.put(type, losses.getOrDefault(type, 0d) + rssCost);
                        }
                    }
                }
            }
        }

        if (includeLoot) {
            double[] loot = getLoot();
            if (loot != null) {
                Map<ResourceType, Double> lootDouble = PnwUtil.resourcesToMap(loot);
                if (attacker) {
                    PnwUtil.subResourcesToA(losses, lootDouble);
                } else {
                    PnwUtil.addResourcesToA(losses, lootDouble);
                }
            }
            else if (getMoney_looted() != 0) {
                int sign = (getVictor() == (attacker ? getAttacker_id() : getDefender_id())) ? -1 : 1;
                losses.put(ResourceType.MONEY, losses.getOrDefault(ResourceType.MONEY, 0d) + getMoney_looted() * sign);
            }
        }
        if (attacker ? getVictor() == getDefender_id() : getVictor() == getAttacker_id()) {
            if (infra && getInfra_destroyed_value() != 0) {
                losses.put(ResourceType.MONEY, (losses.getOrDefault(ResourceType.MONEY, 0d) + getInfra_destroyed_value()));
            }
        }

        if (consumption) {
            double mun = attacker ? getAtt_mun_used() : getDef_mun_used();
            double gas = attacker ? getAtt_gas_used() : getDef_gas_used();
            if (mun > 0) {
                losses.put(ResourceType.MUNITIONS, (losses.getOrDefault(ResourceType.MUNITIONS, 0d) + mun));
            }
            if (gas > 0) {
                losses.put(ResourceType.GASOLINE, (losses.getOrDefault(ResourceType.GASOLINE, 0d) + gas));
            }
        }

        if (includeBuildings && !attacker) {
            for (Map.Entry<Building, Integer> entry : getBuildingsDestroyed().entrySet()) {
                Building building = entry.getKey();
                for (ResourceType type : ResourceType.values) {
                    double rssCost = building.cost(type);
                    if (rssCost > 0) {
                        losses.put(type, losses.getOrDefault(type, 0d) + rssCost * entry.getValue());
                    }
                }
            }

        }
        return losses;
    }

    public abstract Map<Building, Integer> getBuildingsDestroyed();
    public abstract Set<Integer> getCityIdsDamaged();
    public abstract Map<MilitaryUnit, Integer> getUnitLosses(boolean isAttacker);

    public abstract void addUnitLosses(int[] unitTotals, boolean isAttacker);
}
