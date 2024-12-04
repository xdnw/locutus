package link.locutus.discord.apiv3.csv.header;

import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;

public class CityHeaderReader extends DataReader<CityHeader>{
    public CityHeaderReader(CityHeader header, long date) {
        super(header, date);
    }

    public void clear() {
        cached = null;
    }

    private DBCity cached;

    public DBCity getCity() {
        int cityId = header.city_id.get();
        if (cached != null && cached.getId() == cityId) {
            return cached;
        }
        DBCity city = new SimpleDBCity(header.nation_id.get());
        city.setId(cityId);
        city.setCreated(header.date_created.get());
        city.setInfra(header.infrastructure.get());
        city.setLand(header.land.get());

        byte[] buildings = city.getBuildings3();
        buildings[Buildings.OIL_POWER.ordinal()] += header.oil_power_plants.get();
        buildings[Buildings.WIND_POWER.ordinal()] += header.wind_power_plants.get();
        buildings[Buildings.COAL_POWER.ordinal()] += header.coal_power_plants.get();
        buildings[Buildings.NUCLEAR_POWER.ordinal()] += header.nuclear_power_plants.get();
        buildings[Buildings.COAL_MINE.ordinal()] += header.coal_mines.get();
        buildings[Buildings.OIL_WELL.ordinal()] += header.oil_wells.get();
        buildings[Buildings.URANIUM_MINE.ordinal()] += header.uranium_mines.get();
        buildings[Buildings.IRON_MINE.ordinal()] += header.iron_mines.get();
        buildings[Buildings.LEAD_MINE.ordinal()] += header.lead_mines.get();
        buildings[Buildings.BAUXITE_MINE.ordinal()] += header.bauxite_mines.get();
        buildings[Buildings.FARM.ordinal()] += header.farms.get();
        buildings[Buildings.POLICE_STATION.ordinal()] += header.police_stations.get();
        buildings[Buildings.HOSPITAL.ordinal()] += header.hospitals.get();
        buildings[Buildings.RECYCLING_CENTER.ordinal()] += header.recycling_centers.get();
        buildings[Buildings.SUBWAY.ordinal()] += header.subway.get();
        buildings[Buildings.SUPERMARKET.ordinal()] += header.supermarkets.get();
        buildings[Buildings.BANK.ordinal()] += header.banks.get();
        buildings[Buildings.MALL.ordinal()] += header.shopping_malls.get();
        buildings[Buildings.STADIUM.ordinal()] += header.stadiums.get();
        buildings[Buildings.GAS_REFINERY.ordinal()] += header.oil_refineries.get();
        buildings[Buildings.ALUMINUM_REFINERY.ordinal()] += header.aluminum_refineries.get();
        buildings[Buildings.STEEL_MILL.ordinal()] += header.steel_mills.get();
        buildings[Buildings.MUNITIONS_FACTORY.ordinal()] += header.munitions_factories.get();
        buildings[Buildings.BARRACKS.ordinal()] += header.barracks.get();
        buildings[Buildings.FACTORY.ordinal()] += header.factories.get();
        buildings[Buildings.HANGAR.ordinal()] += header.hangars.get();
        buildings[Buildings.DRYDOCK.ordinal()] += header.drydocks.get();
        return cached = city;
    }
}
