package link.locutus.discord.event.city;

import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBCity;

import java.util.HashMap;
import java.util.Map;

public class CityBuildingChangeEvent extends CityChangeEvent {
    private final Map<Building, Integer> change;
    private final String reason;

    public CityBuildingChangeEvent(int nation, DBCity previous, DBCity current, String reason) {
        super(nation, previous, current);
        this.change = new HashMap<>();
        for (Building building : Buildings.values()) {
            int diff = current.getBuilding(building) - previous.getBuilding(building);
            if (diff > 0) {
                change.put(building, diff);
            }
        }
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public Map<Building, Integer> getChange() {
        return change;
    }
}
