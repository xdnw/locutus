package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.MMRInt;

public interface IMutableCity extends ICity {
    public void setBuilding(Building building, int amount);

    default IMutableCity setOptimalPower(Continent continent) {
        int nuclear = 0;
        int coal = 0;
        int oil = 0;
        int wind = 0;
        double infra = getInfra();
        while (infra > 500 || (getInfra() > 2000 && infra > 250)) {
            nuclear++;
            infra -= Buildings.NUCLEAR_POWER.getInfraMax();
        }
        while (infra > 250) {
            if (continent.canBuild(Buildings.COAL_POWER)) {
                coal++;
                infra -= Buildings.COAL_POWER.getInfraMax();
            } else if (continent.canBuild(Buildings.OIL_POWER)) {
                oil++;
                infra -= Buildings.OIL_POWER.getInfraMax();
            } else {
                break;
            }
        }
        while (infra > 0) {
            wind++;
            infra -= Buildings.WIND_POWER.getInfraMax();
        }
        setBuilding(Buildings.NUCLEAR_POWER, nuclear);
        setBuilding(Buildings.COAL_POWER, coal);
        setBuilding(Buildings.OIL_POWER, oil);
        setBuilding(Buildings.WIND_POWER, wind);
        return this;
    }

    default IMutableCity setMMR(MMRInt mmr) {
        setBuilding(Buildings.BARRACKS, (int) Math.round(mmr.getBarracks()));
        setBuilding(Buildings.FACTORY, (int) Math.round(mmr.getFactory()));
        setBuilding(Buildings.HANGAR, (int) Math.round(mmr.getHangar()));
        setBuilding(Buildings.DRYDOCK, (int) Math.round(mmr.getDrydock()));
        return this;
    }

    default void setPowerBuildings(ICity origin) {
        setBuilding(Buildings.COAL_POWER, origin.getBuilding(Buildings.COAL_POWER));
        setBuilding(Buildings.OIL_POWER, origin.getBuilding(Buildings.OIL_POWER));
        setBuilding(Buildings.NUCLEAR_POWER, origin.getBuilding(Buildings.NUCLEAR_POWER));
        setBuilding(Buildings.WIND_POWER, origin.getBuilding(Buildings.WIND_POWER));
    }

    default void setMilitaryBuildings(ICity origin) {
        setBuilding(Buildings.BARRACKS, origin.getBuilding(Buildings.BARRACKS));
        setBuilding(Buildings.FACTORY, origin.getBuilding(Buildings.FACTORY));
        setBuilding(Buildings.HANGAR, origin.getBuilding(Buildings.HANGAR));
        setBuilding(Buildings.DRYDOCK, origin.getBuilding(Buildings.DRYDOCK));
    }
}
