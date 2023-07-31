package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.bytes.Byte2ByteArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.io.BitBuffer;

import java.util.HashMap;
import java.util.Map;

public abstract class UnitCursor extends DamageCursor {
    private int att_mun_used_cents;
    private int att_gas_used_cents;
    private int def_mun_used_cents;
    private int def_gas_used_cents;
    private boolean has_salvage;

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        att_mun_used_cents = (int) Math.round(legacy.getAtt_mun_used() * 100);
        def_mun_used_cents = (int) Math.round(legacy.getDef_mun_used() * 100);
        att_gas_used_cents = (int) Math.round(legacy.getAtt_gas_used() * 100);
        def_gas_used_cents = (int) Math.round(legacy.getDef_gas_used() * 100);
        has_salvage = false;
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);

        output.writeBit(att_mun_used_cents > 0);
        if (att_mun_used_cents > 0) {
            output.writeVarInt(att_mun_used_cents);

            output.writeBit(att_gas_used_cents > 0);
            if (att_gas_used_cents > 0) {
                output.writeVarInt(att_gas_used_cents);
            }
        }
        // 5 bits for length

        output.writeBit(def_mun_used_cents > 0);
        if (def_mun_used_cents > 0) {
            output.writeVarInt(def_mun_used_cents);

            output.writeBit(def_gas_used_cents > 0);
            if (def_gas_used_cents > 0) {
                output.writeVarInt(def_gas_used_cents);
            }
        }

        output.writeBit(has_salvage);
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        if (input.readBit()) {
            att_mun_used_cents = (int) input.readVarInt();
            if (input.readBit()) {
                att_gas_used_cents = (int) input.readVarInt();
            }
        }

        if (input.readBit()) {
            def_mun_used_cents = (int) input.readVarInt();
            if (input.readBit()) {
                def_gas_used_cents = (int) input.readVarInt();
            }
        }

        has_salvage = input.readBit();
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);

        att_mun_used_cents = (int) (attack.getAtt_mun_used() * 100);
        def_mun_used_cents = (int) (attack.getDef_mun_used() * 100);
        att_gas_used_cents = (int) (attack.getAtt_gas_used() * 100);
        def_gas_used_cents = (int) (attack.getDef_gas_used() * 100);

        has_salvage = (success != SuccessType.UTTER_FAILURE && attack.getMilitary_salvage_aluminum() > 0 && attack.getMilitary_salvage_steel() > 0);
    }


    @Override
    public double getAtt_gas_used() {
        return att_gas_used_cents * 0.01;
    }

    @Override
    public double getAtt_mun_used() {
        return att_mun_used_cents * 0.01;
    }

    @Override
    public double getDef_gas_used() {
        return def_gas_used_cents * 0.01;
    }

    @Override
    public double getDef_mun_used() {
        return def_mun_used_cents * 0.01;
    }

    @Override
    public Map<MilitaryUnit, Integer> getUnitLosses2(boolean isAttacker) {
        Map<MilitaryUnit, Integer> losses = new Object2ObjectOpenHashMap<>(getUnits().length);
        for (MilitaryUnit unit : getUnits()) {
            losses.put(unit, getUnitLosses(unit, isAttacker));
        }
        return losses;
    }

    @Override
    public void addUnitLosses(int[] unitTotals, boolean isAttacker) {
        for (MilitaryUnit unit : getUnits()) {
            unitTotals[unit.ordinal()] += getUnitLosses(unit, isAttacker);
        }
    }

    @Override
    public Map<ResourceType, Double> getLosses(boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings) {
        Map<ResourceType, Double> losses = new HashMap<>();
        if (units) {
            Map<MilitaryUnit, Integer> unitLosses = getUnitLosses2(attacker);
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
                    losses = PnwUtil.subResourcesToA(losses, lootDouble);
                } else {
                    losses = PnwUtil.addResourcesToA(losses, lootDouble);
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

        if (includeBuildings) {
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
}
