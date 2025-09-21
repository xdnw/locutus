package link.locutus.discord.db.entities.city;

import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.column.BuildingColumn;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.nation.GlobalDataWrapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

import static link.locutus.discord.apiv1.core.Utility.unsupported;
import static link.locutus.discord.apiv1.enums.city.building.Buildings.BUILDINGS;

public class BytesDBCity extends DBCity {
    private final GlobalDataWrapper<CityHeader> wrapper;
    private final int offset;

    public BytesDBCity(GlobalDataWrapper<CityHeader> wrapper, int offset) {
        this.wrapper = wrapper;
        this.offset = offset;
    }

    private static BiFunction<Building, CityHeader, BuildingColumn> hasBuilding = new BiFunction<Building, CityHeader, BuildingColumn>() {
        Function<CityHeader, BuildingColumn>[] byBuilding;

        private void set(CityHeader header, Function<CityHeader, BuildingColumn> supplier) {
            BuildingColumn col = supplier.apply(header);
            byBuilding[col.getBuilding().ordinal()] = supplier::apply;
        }

        @Override
        public BuildingColumn apply(Building project, CityHeader header) {
            if (byBuilding == null) {
                byBuilding = new Function[Arrays.stream(Buildings.values()).mapToInt(Building::ordinal).max().orElse(0) + 1];
                set(header, f -> f.oil_power_plants);
                set(header, f -> f.wind_power_plants);
                set(header, f -> f.coal_power_plants);
                set(header, f -> f.nuclear_power_plants);
                set(header, f -> f.coal_mines);
                set(header, f -> f.oil_wells);
                set(header, f -> f.uranium_mines);
                set(header, f -> f.iron_mines);
                set(header, f -> f.lead_mines);
                set(header, f -> f.bauxite_mines);
                set(header, f -> f.farms);
                set(header, f -> f.police_stations);
                set(header, f -> f.hospitals);
                set(header, f -> f.recycling_centers);
                set(header, f -> f.subway);
                set(header, f -> f.supermarkets);
                set(header, f -> f.banks);
                set(header, f -> f.shopping_malls);
                set(header, f -> f.stadiums);
                set(header, f -> f.oil_refineries);
                set(header, f -> f.aluminum_refineries);
                set(header, f -> f.steel_mills);
                set(header, f -> f.munitions_factories);
                set(header, f -> f.barracks);
                set(header, f -> f.factories);
                set(header, f -> f.hangars);
                set(header, f -> f.drydocks);
                hasBuilding = (a, b) -> byBuilding[a.ordinal()].apply(b);
            }
            return byBuilding[project.ordinal()].apply(header);
        }
    };

    private <T> T getSafe(ColumnInfo<DBCity, T> get) {
        try {
            if (get.getOffset() == -1) return get.getDefault();
            return get.read(this.wrapper.data, get.getOffset() + this.offset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T get(ColumnInfo<DBCity, T> get) {
        try {
            return get.read(this.wrapper.data, get.getOffset() + this.offset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getBuildingOrdinal(int ordinal) {
        return get(hasBuilding.apply(BUILDINGS[ordinal], wrapper.header));
    }

    @Override
    public int getNumBuildings() {
        int count = 0;
        for (Building building : Buildings.values()) {
            count += get(hasBuilding.apply(building, wrapper.header));
        }
        return count;
    }

    public int getId() {
        return get(wrapper.header.city_id);
    }

    public int getNation_id() {
        return get(wrapper.header.nation_id);
    }

    public long getCreated() {
        return get(wrapper.header.date_created);
    }

    public long getFetched() {
        return wrapper.date;
    }

    public int getLand_cents() {
        return (int) Math.round(get(wrapper.header.land) * 100);
    }

    public int getInfra_cents() {
        return (int) Math.round(get(wrapper.header.infrastructure) * 100);
    }

    public boolean isPowered() {
        return getSafe(wrapper.header.powered);
    }

    public byte[] getBuildings3() {
        byte[] buildings = new byte[Buildings.values().length];
        for (Building building : Buildings.values()) {
            buildings[building.ordinal()] = get(hasBuilding.apply(building, wrapper.header)).byteValue();
        }
        return buildings;
    }

    public int getNuke_turn() {
        return get(wrapper.header.last_nuke_date);
    }

    // readonly
    public void setCreated(long created) {
        throw unsupported();
    }
    public void setFetched(long fetched) {
        throw unsupported();
    }
    public void setLand_cents(int land_cents) {
        throw unsupported();
    }
    public void setInfra_cents(int infra_cents) {
        throw unsupported();
    }
    public void setBuildings3(byte[] buildings3) {
        throw unsupported();
    }
    public void setPowered(boolean powered) {
        throw unsupported();
    }

    public void setId(int id) {
        throw unsupported();
    }

    public void setNation_id(int nation_id) {
        throw unsupported();
    }


    public void setNuke_turn(int nuke_turn) {
        throw unsupported();
    }
}
