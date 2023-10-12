package link.locutus.discord.event.city;

import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CityBuildingChangeEvent extends CityChangeEvent {
    private final Map<Building, Integer> change;

    public CityBuildingChangeEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
        this.change = new HashMap<>();
        for (Building building : Buildings.values()) {
            int diff = current.get(building) - previous.get(building);
            if (diff > 0) {
                change.put(building, diff);
            }
        }
    }

    public Map<Building, Integer> getChange() {
        return change;
    }
}
