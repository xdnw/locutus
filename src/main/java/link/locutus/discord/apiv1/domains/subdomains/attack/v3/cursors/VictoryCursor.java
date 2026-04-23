package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.CityInfraDamage;
import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.FailedCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VictoryCursor extends FailedCursor {
    public boolean hasLoot = false;
    public double[] looted = null;
    private int loot_percent_cents;
    private final Map<Integer, Integer> city_infra_before_cents = new Int2IntOpenHashMap();
    private int infra_percent_decimal; // * 0.001
    private int infra_destroyed_cents;
    private long infra_destroyed_value_cents;

    @Override
    public double[] addDefLoot(double[] buffer) {
        if (hasLoot) {
            ResourceType.add(buffer, looted);
        }
        return buffer;
    }

    @Override
    public double[] addAttLoot(double[] buffer) {
        if (hasLoot) {
            ResourceType.subtract(buffer, looted);
        }
        return buffer;
    }

    @Override
    public double getAttLootValue() {
        return -getDefLootValue();
    }

    @Override
    public double getDefLootValue() {
        return hasLoot ? ResourceType.convertedTotal(looted) : 0;
    }

    @Override
    public double[] addInfraCosts(double[] buffer) {
        buffer[ResourceType.MONEY.ordinal()] += getInfra_destroyed_value();
        return buffer;
    }

    public double[] addDefLosses(double[] buffer, DBWar war) {
        if (hasLoot) ResourceType.add(buffer, looted);
        addInfraCosts(buffer);
        return buffer;
    }
    public double[] addAttLosses(double[] buffer, DBWar war) {
        if (hasLoot) ResourceType.subtract(buffer, looted);
        return buffer;
    }

    public double getAttLossValue(DBWar war) {
        return hasLoot ? -ResourceType.convertedTotal(looted) : 0;
    }
    public double getDefLossValue(DBWar war) {
        return (hasLoot ? ResourceType.convertedTotal(looted) : 0)
                + getInfra_destroyed_value();
    }

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
        } else if (!city_infra_before_cents.isEmpty() && infra_percent_decimal > 0) {
            WarOutcomeMath.VictoryInfraTotals totals = WarOutcomeMath.victoryInfraTotals(
                    city_infra_before_cents.values(),
                    infra_percent_decimal
            );
            infra_destroyed_cents = totals.infraDestroyedCents();
            infra_destroyed_value_cents = totals.infraDestroyedValueCents();
        }
        return infra_destroyed_value_cents * 0.01;
    }

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        this.hasLoot = legacy.loot != null && !ResourceType.isZero(legacy.loot);
        this.loot_percent_cents = (int) ArrayUtil.toCents(legacy.getLootPercent());
        if (hasLoot) {
            this.looted = legacy.loot;
            if (this.looted == null) this.hasLoot = false;
        } else if (legacy.getMoney_looted() > 0) {
            hasLoot = true;
            if (looted == null) looted = ResourceType.getBuffer();
            Arrays.fill(looted, 0);
            looted[ResourceType.MONEY.ordinal()] = legacy.getMoney_looted();
        } else if (this.looted != null) {
            Arrays.fill(looted, 0);
        }
        city_infra_before_cents.clear();
        this.infra_destroyed_cents = (int) ArrayUtil.toCents(legacy.getInfra_destroyed());
        infra_percent_decimal = WarOutcomeMath.victoryInfraPercentMilli(legacy.infraPercent_cached);
        infra_destroyed_value_cents = (long) ArrayUtil.toCents(legacy.getInfra_destroyed_value());
    }

    @Override
    public double getInfra_destroyed() {
        return infra_destroyed_cents * 0.01;
    }

    @Override
    public double getInfra_destroyed_percent() {
        return infra_percent_decimal * 0.001;
    }

    @Override
    public void load(WarAttack attack, WarDB db) {
        super.load(attack, db);

        DBWar war = getWar(db);
        List<CityInfraDamage> infraBefore = attack.getCities_infra_before();
        infra_percent_decimal = WarOutcomeMath.victoryInfraPercentMilli(attack.getInfra_destroyed_percentage());
        if (war != null) {
            boolean winnerIsOriginalAttacker = war.getAttacker_id() == getAttacker_id();
            DBNation winner = winnerIsOriginalAttacker ? war.getNation(true) : war.getNation(false);
            DBNation loser = winnerIsOriginalAttacker ? war.getNation(false) : war.getNation(true);
            infra_percent_decimal = WarOutcomeMath.victoryInfraPercentMilli(
                WarOutcomeMath.victoryInfraPercent(
                    winner == null ? 1.0 : winner.infraAttackModifier(AttackType.VICTORY),
                    loser == null ? 1.0 : loser.infraDefendModifier(AttackType.VICTORY),
                    war.getWarType(),
                    winnerIsOriginalAttacker
                )
            );
        }
        city_infra_before_cents.clear();
        infra_destroyed_value_cents = 0;
        infra_destroyed_cents = 0;

        if (infraBefore != null && !infraBefore.isEmpty() && infra_percent_decimal > 0) {
            if (infraBefore.size() == 1) {
                Object elem0 = infraBefore.get(0);
                if (elem0 instanceof List) {
                    infraBefore = (List<CityInfraDamage>) elem0;
                }
            }
            for (CityInfraDamage cityInfraDamage : infraBefore) {
                double before = cityInfraDamage.getInfrastructure();
                city_infra_before_cents.put(cityInfraDamage.getId(), Math.max(0, (int) Math.round(before * 100d)));
            }
            WarOutcomeMath.VictoryInfraTotals totals = WarOutcomeMath.victoryInfraTotals(
                    city_infra_before_cents.values(),
                    infra_percent_decimal
            );
            infra_destroyed_cents = totals.infraDestroyedCents();
            infra_destroyed_value_cents = totals.infraDestroyedValueCents();
        }

        // loot
        if (looted == null) looted = ResourceType.getBuffer();
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

        if (ResourceType.isZero(looted)) {
            hasLoot = false;
        } else {
            hasLoot = true;
        }

        if (hasLoot) {
            if (war != null) {
                boolean winnerIsOriginalAttacker = war.getAttacker_id() == getAttacker_id();
                DBNation winner = winnerIsOriginalAttacker ? war.getNation(true) : war.getNation(false);
                DBNation loser = winnerIsOriginalAttacker ? war.getNation(false) : war.getNation(true);
                loot_percent_cents = (int) Math.round(100 * WarOutcomeMath.victoryNationLootPercent(
                        winner == null ? 1.0 : winner.looterModifier(false),
                        loser == null ? 1.0 : loser.lootModifier(),
                        war.getWarType(),
                        winnerIsOriginalAttacker
                ));
            }

        } else {
            loot_percent_cents = 0;
        }
    }

    @Override
    public String toString() {
        return "VictoryCursor{" +
                "hasLoot=" + hasLoot +
                ", looted=" + (hasLoot ? Arrays.toString(looted) : "") +
                ", loot_percent_cents=" + loot_percent_cents +
                ", city_infra_before_cents=" + city_infra_before_cents +
                ", infra_percent_decimal=" + infra_percent_decimal +
                ", infra_destroyed_cents=" + infra_destroyed_cents +
                ", infra_destroyed_value_cents=" + infra_destroyed_value_cents +
                ", war_attack_id=" + war_attack_id +
                ", date=" + date +
                ", war_id=" + war_id +
                ", attacker_id=" + attacker_id +
                ", defender_id=" + defender_id +
                '}';
    }

    public void loadLegacy(DBWar war, BitBuffer input) {
        super.load(war, input);
        // load resources
        boolean newHasLoot = input.readBit();

        city_infra_before_cents.clear();

        if (newHasLoot) {
            hasLoot = newHasLoot;
            loot_percent_cents = input.readVarInt();
            infra_destroyed_value_cents = input.readVarLong();

            if (input.readBit()) {
                infra_percent_decimal = input.readVarInt() * 10;
                long size = input.readBits(7);
                for (int i = 0; i < size; i++) {
                    int cityId = input.readVarInt();
                    int infra = input.readVarInt();
                    city_infra_before_cents.put(cityId, infra);
                    infra_destroyed_cents += Math.round(infra * (infra_percent_decimal * 0.001));
                }
            } else {
                infra_destroyed_cents = 0;
                infra_percent_decimal = 0;
            }

            if (looted == null) looted = ResourceType.getBuffer();
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                if (input.readBit()) looted[type.ordinal()] = input.readVarLong() * 0.01d;
                else looted[type.ordinal()] = 0;
            }
            if (ResourceType.isZero(looted)) {
                hasLoot = false;
            }
        } else {
            if (hasLoot) {
                if (looted != null) Arrays.fill(looted, 0);
                hasLoot = false;
            }
            infra_destroyed_value_cents = 0;
            infra_percent_decimal = 0;
            loot_percent_cents = 0;
            infra_destroyed_cents = 0;
        }
    }

    @Override
    public void serialize(BitBuffer output) {
        super.serialize(output);
        // add current
        output.writeVarLong(infra_destroyed_value_cents);

        output.writeBit(!city_infra_before_cents.isEmpty());
        if (!city_infra_before_cents.isEmpty()) {
            output.writeVarInt(infra_percent_decimal);
            output.writeBits(city_infra_before_cents.size(), 7);
            for (Map.Entry<Integer, Integer> entry : city_infra_before_cents.entrySet()) {
                output.writeVarInt(entry.getKey());
                output.writeVarInt(entry.getValue());
            }
        }

        if (hasLoot && looted == null) {// Shouldn't occur unless this has been incorrectly initialized, handle it anyway
            hasLoot = false;
        }
        output.writeBit(hasLoot);
        if (hasLoot) {
            output.writeVarInt(loot_percent_cents);
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                double amt = looted[type.ordinal()];
                if (amt >= 0.01d) {
                    output.writeBit(true);
                    output.writeVarLong((long) (amt * 100));
                } else {
                    output.writeBit(false);
                }
            }
        }
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);

        infra_destroyed_value_cents = input.readVarLong();
        city_infra_before_cents.clear();
        if (input.readBit()) {
            infra_percent_decimal = input.readVarInt();
            long size = input.readBits(7);
            for (int i = 0; i < size; i++) {
                int cityId = input.readVarInt();
                int infra = input.readVarInt();
                city_infra_before_cents.put(cityId, infra);
                infra_destroyed_cents += Math.round(infra * (infra_percent_decimal * 0.001));
            }
        } else {
            infra_destroyed_cents = 0;
            infra_percent_decimal = 0;
        }

        // load resources
        boolean newHasLoot = input.readBit();
        if (newHasLoot) {
            hasLoot = newHasLoot;
            loot_percent_cents = input.readVarInt();
            if (looted == null) looted = ResourceType.getBuffer();
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                if (input.readBit()) looted[type.ordinal()] = input.readVarLong() * 0.01d;
                else looted[type.ordinal()] = 0;
            }
        } else {
            if (hasLoot) {
                if (looted != null) Arrays.fill(looted, 0);
                hasLoot = false;
            }
            loot_percent_cents = 0;
            infra_destroyed_cents = 0;
        }
    }

    @Override
    public Set<Integer> getCityIdsDamaged() {
        return city_infra_before_cents == null ? Collections.emptySet() : city_infra_before_cents.keySet();
    }
}
