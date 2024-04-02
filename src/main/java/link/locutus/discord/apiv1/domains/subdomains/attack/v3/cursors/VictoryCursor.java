package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.CityInfraDamage;
import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.FailedCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VictoryCursor extends FailedCursor {
    public boolean hasLoot = false;
    public double[] looted = ResourceType.getBuffer();
    private int loot_percent_cents;
    private Map<Integer, Integer> city_infra_before_cents = new Int2IntOpenHashMap();
    private int infra_destroyed_percent_cents;
    private int infra_destroyed_cents;
    private long infra_destroyed_value_cents;

    @Override
    public AttackType getAttack_type() {
        return AttackType.VICTORY;
    }

    @Override
    public SuccessType getSuccess() {
        return SuccessType.PYRRHIC_VICTORY;
    }

    @Override
    public double[] getLoot() {
        return hasLoot ? looted : null;
    }

    @Override
    public double getLootPercent() {
        return loot_percent_cents * 0.01d;
    }

    @Override
    public double getInfra_destroyed_value() {
        if (infra_destroyed_value_cents > 0) {
            return infra_destroyed_value_cents * 0.01d;
        } else if (!city_infra_before_cents.isEmpty() && infra_destroyed_percent_cents > 0) {
            double pct = (1 - infra_destroyed_percent_cents * 0.01);
            for (Map.Entry<Integer, Integer> entry : city_infra_before_cents.entrySet()) {
                int before = entry.getValue();
                int after = (int) Math.round(before * pct);
                if (after < before) {
                    double value = PW.City.Infra.calculateInfra(after * 0.01, before * 0.01);
                    infra_destroyed_cents += (before - after);
                    infra_destroyed_value_cents += (value * 100);
                }
            }
        }
        return infra_destroyed_value_cents * 0.01;
    }

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        this.hasLoot = legacy.loot != null && !ResourceType.isZero(legacy.loot);
        this.loot_percent_cents = (int) Math.round(legacy.getLootPercent() * 100);
        if (hasLoot) {
            this.looted = legacy.loot;
        } else if (legacy.getMoney_looted() > 0) {
            hasLoot = true;
            Arrays.fill(looted, 0);
            looted[ResourceType.MONEY.ordinal()] = legacy.getMoney_looted();
        }
        city_infra_before_cents.clear();
        this.infra_destroyed_cents = (int) Math.round(legacy.getInfra_destroyed() * 100);
        infra_destroyed_percent_cents = (int) Math.round(legacy.infraPercent_cached * 100);
        infra_destroyed_value_cents = (long) Math.round(legacy.getInfra_destroyed_value() * 100);
    }

    @Override
    public double getInfra_destroyed() {
        return infra_destroyed_cents * 0.01;
    }

    @Override
    public double getInfra_destroyed_percent() {
        return infra_destroyed_percent_cents * 0.01;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);

        List<CityInfraDamage> infraBefore = attack.getCities_infra_before();
        infra_destroyed_percent_cents = (int) (attack.getInfra_destroyed_percentage() * 100);
        city_infra_before_cents.clear();
        infra_destroyed_value_cents = 0;
        infra_destroyed_cents = 0;

        if (infraBefore != null && !infraBefore.isEmpty() && infra_destroyed_percent_cents > 0) {
            for (CityInfraDamage cityInfraDamage : infraBefore) {
                double before = cityInfraDamage.getInfrastructure();
                double after = before * (1 - attack.getInfra_destroyed_percentage());
                double value = PW.City.Infra.calculateInfra(after, before);
                infra_destroyed_cents += (before - after) * 100;
                infra_destroyed_value_cents += (value * 100);
                city_infra_before_cents.put(cityInfraDamage.getId(), (int) (before * 100));
            }
        }

        // loot
        looted[ResourceType.MONEY.ordinal()] = attack.getMoney_looted();
        looted[ResourceType.COAL.ordinal()] = attack.getCoal_looted();
        looted[ResourceType.OIL.ordinal()] = attack.getOil_looted();
        looted[ResourceType.URANIUM.ordinal()] = attack.getUranium_looted();
        looted[ResourceType.IRON.ordinal()] = attack.getIron_looted();
        looted[ResourceType.BAUXITE.ordinal()] = attack.getBauxite_looted();
        looted[ResourceType.LEAD.ordinal()] = attack.getLead_looted();
        looted[ResourceType.GASOLINE.ordinal()] = attack.getGasoline_looted();
        looted[ResourceType.MUNITIONS.ordinal()] = attack.getMunitions_looted();
        looted[ResourceType.STEEL.ordinal()] = attack.getSteel_looted();
        looted[ResourceType.ALUMINUM.ordinal()] = attack.getAluminum_looted();
        looted[ResourceType.FOOD.ordinal()] = attack.getFood_looted();

        hasLoot = !ResourceType.isZero(looted);

        if (hasLoot) {
            // get war
            DBWar war = getWar();

            if (war != null) {
                DBNation attacker = war.getNation(true);
                DBNation defender = war.getNation(false);

                double baseLoot = 0.1 * war.getWarType().lootModifier();
                double modifier = 1;
                if (attacker != null) {
                    modifier += attacker.looterModifier(false) - 1;
                }
                if (defender != null) {
                    modifier += defender.lootModifier() - 1;
                }

                loot_percent_cents = (int) Math.round(100 * baseLoot * modifier);
            }

        } else {
            loot_percent_cents = 0;
        }
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        // add current
        output.writeBit(hasLoot);
        if (hasLoot) {
            output.writeVarInt(loot_percent_cents);
            output.writeVarLong(infra_destroyed_value_cents);

            output.writeBit(!city_infra_before_cents.isEmpty());
            if (!city_infra_before_cents.isEmpty()) {
                output.writeVarInt(infra_destroyed_percent_cents);
                output.writeBits(city_infra_before_cents.size(), 7);
                for (Map.Entry<Integer, Integer> entry : city_infra_before_cents.entrySet()) {
                    output.writeVarInt(entry.getKey());
                    output.writeVarInt(entry.getValue());
                }
            }

            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                double amt = looted[type.ordinal()];
                output.writeBit(amt > 0);
                if (amt > 0) output.writeVarLong((long) (amt * 100));
            }
        }
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        // load resources
        hasLoot = input.readBit();

        city_infra_before_cents.clear();

        if (hasLoot) {
            loot_percent_cents = input.readVarInt();
            infra_destroyed_value_cents = input.readVarLong();

            if (input.readBit()) {
                infra_destroyed_percent_cents = input.readVarInt();
                long size = input.readBits(7);
                for (int i = 0; i < size; i++) {
                    int cityId = input.readVarInt();
                    int infra = input.readVarInt();
                    city_infra_before_cents.put(cityId, infra);
                    infra_destroyed_cents += Math.round(infra * (infra_destroyed_percent_cents * 0.01));
                }
            } else {
                infra_destroyed_cents = 0;
                infra_destroyed_percent_cents = 0;
            }

            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                if (input.readBit()) looted[type.ordinal()] = input.readVarLong() * 0.01d;
                else looted[type.ordinal()] = 0;
            }
        } else {
            infra_destroyed_value_cents = 0;
            infra_destroyed_percent_cents = 0;
            loot_percent_cents = 0;
            infra_destroyed_cents = 0;
        }
    }

    @Override
    public Set<Integer> getCityIdsDamaged() {
        return city_infra_before_cents == null ? Collections.emptySet() : city_infra_before_cents.keySet();
    }
}
