package link.locutus.discord.db.entities.conflict;

import com.google.gson.JsonArray;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.util.PnwUtil;

import java.util.Map;
import java.util.function.Function;

import static link.locutus.discord.db.entities.conflict.ConflictColumn.header;
import static link.locutus.discord.db.entities.conflict.ConflictColumn.ranking;

public class DamageStatGroup {
    public final double[] totalCost = ResourceType.getBuffer();
    public final double[] consumption = ResourceType.getBuffer();
    public final double[] loot = ResourceType.getBuffer();
    public final int[] units = new int[MilitaryUnit.values.length];
    public final char[] buildings = new char[Buildings.values().length];
    private long infraCents = 0;

    public static Map<ConflictColumn, Function<DamageStatGroup, Object>> createHeader() {
        Map<ConflictColumn, Function<DamageStatGroup, Object>> map = new Object2ObjectLinkedOpenHashMap<>();
        map.put(ranking("loss_value"), p -> (long) PnwUtil.convertedTotal(p.totalCost));
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            map.put(header("loss_" + type.name().toLowerCase()), p -> (long) p.totalCost[type.ordinal()]);
        }
        map.put(header("consume_gas"), p -> (long) p.consumption[ResourceType.GASOLINE.ordinal()]);
        map.put(header("consume_mun"), p -> (long) p.consumption[ResourceType.MUNITIONS.ordinal()]);
        map.put(header("consume_value"), p -> (long) PnwUtil.convertedTotal(p.consumption));
        map.put(ranking("loot_value"), p -> (long) PnwUtil.convertedTotal(p.loot));
        for (MilitaryUnit unit : MilitaryUnit.values) {
            if (unit == MilitaryUnit.SPIES || unit == MilitaryUnit.INFRASTRUCTURE || unit == MilitaryUnit.MONEY) continue;
            String name = unit.name().toLowerCase() + "_loss";
            ConflictColumn col = unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE ? header(name) : ranking(name);
            map.put(col, p -> p.units[unit.ordinal()]);
            map.put(header(unit.name().toLowerCase() + "_loss_value"), p -> (long) (unit.getConvertedCost() * p.units[unit.ordinal()]));
        }
        map.put(ranking("unit_loss_value"), p -> {
            double total = 0;
            for (int i = 0; i < p.units.length; i++) {
                int amt = p.units[i];
                if (amt > 0) {
                    total += MilitaryUnit.values[i].getConvertedCost() * amt;
                }
            }
            return total;
        });
        map.put(header("building_loss"), p -> {
            int total = 0;
            for (char c : p.buildings) {
                total += c;
            }
            return total;
        });
        map.put(header("building_loss_value"), p -> {
            double total = 0;
            for (int i = 0; i < p.buildings.length; i++) {
                int amt = p.buildings[i];
                if (amt > 0) {
                    total += Buildings.get(i).getNMarketCost(amt);
                }
            }
            return Math.round(total);
        });
        map.put(ranking("infra_loss"), p -> (long) (p.infraCents * 0.01));
        return map;
    }

    public void apply(AbstractCursor attack, boolean isAttacker) {
        attack.getLosses(totalCost, isAttacker, true, true, true, true, true);
        attack.getLosses(consumption, isAttacker, false, false, true, false, false);
        attack.getLosses(loot, isAttacker, false, false, false, true, false);
        attack.getUnitLosses(units, isAttacker);
        if (!isAttacker) {
            infraCents += Math.round(attack.getInfra_destroyed_value() * 100);
            attack.addBuildingsDestroyed(buildings);
        }
    }

    public double getInfra() {
        return infraCents * 0.01;
    }

    public double getTotalConverted() {
        return PnwUtil.convertedTotal(totalCost);
    }
}
