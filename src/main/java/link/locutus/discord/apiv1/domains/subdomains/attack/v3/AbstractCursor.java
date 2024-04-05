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
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.Map;
import java.util.Set;

public abstract class AbstractCursor implements IAttack {
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
            if (war.getAttacker_id() > war.getDefender_id()) {
                attacker_id = war.getAttacker_id();
                defender_id = war.getDefender_id();
            } else {
                attacker_id = war.getDefender_id();
                defender_id = war.getAttacker_id();
            }
        } else {
            if (war.getAttacker_id() > war.getDefender_id()) {
                attacker_id = war.getDefender_id();
                defender_id = war.getAttacker_id();
            } else {
                attacker_id = war.getAttacker_id();
                defender_id = war.getDefender_id();
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

    @Override
    public DBWar getWar() {
        if (war_cached == null) {
            Locutus lc = Locutus.imp();
            if (lc != null) {
                war_cached = lc.getWarDb().getWar(war_id);
            }
        }
        return war_cached;
    }

//    public Map<ResourceType, Double> getLosses2(boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings) {
//        double[] buffer = ResourceType.getBuffer();
//        addLosses(buffer, attacker, units, infra, consumption, includeLoot, includeBuildings);
//        return PW.resourcesToMap(buffer);
//    }

    @Override
    public double[] getLosses(double[] buffer, boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings) {
        if (units) {
            double[] unitLosses = getUnitLossCost(buffer, attacker);
        }
        if (includeLoot) {
            double[] loot = getLoot();
            if (loot != null) {
                if (attacker) {
                    ResourceType.subtract(buffer, loot);
                } else {
                    ResourceType.add(buffer, loot);
                }
            }
            else if (getMoney_looted() != 0) {
                int sign = (getVictor() == (attacker ? getAttacker_id() : getDefender_id())) ? -1 : 1;
                buffer[ResourceType.MONEY.ordinal()] += getMoney_looted() * sign;
            }
        }
        if (attacker ? getVictor() == getDefender_id() : getVictor() == getAttacker_id()) {
            if (infra && getInfra_destroyed_value() != 0) {
                buffer[ResourceType.MONEY.ordinal()] += getInfra_destroyed_value();
            }
        }

        if (consumption) {
            double mun = attacker ? getAtt_mun_used() : getDef_mun_used();
            double gas = attacker ? getAtt_gas_used() : getDef_gas_used();
            if (mun > 0) {
                buffer[ResourceType.MUNITIONS.ordinal()] += mun;
            }
            if (gas > 0) {
                buffer[ResourceType.GASOLINE.ordinal()] += gas;
            }
        }

        if (includeBuildings && !attacker) {
            buffer = getBuildingCost(buffer);
        }
        return buffer;
    }

    public abstract Map<Building, Integer> getBuildingsDestroyed2();

    public abstract double[] getBuildingCost(double[] buffer);
    public abstract Set<Integer> getCityIdsDamaged();

    public abstract int[] getUnitLosses(int[] buffer, boolean isAttacker);

    public abstract int getUnitLosses(MilitaryUnit unit, boolean attacker);

    public abstract double[] getUnitLossCost(double[] buffer, boolean isAttacker);

    public Map<MilitaryUnit, Integer> getUnitLosses2(boolean isAttacker) {
        int[] buffer = MilitaryUnit.getBuffer();
        return ArrayUtil.toMap(getUnitLosses(buffer, isAttacker), MilitaryUnit.values);
    }
}
