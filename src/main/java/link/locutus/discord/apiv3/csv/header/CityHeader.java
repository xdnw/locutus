package link.locutus.discord.apiv3.csv.header;

import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.csv.column.BooleanColumn;
import link.locutus.discord.apiv3.csv.column.DoubleColumn;
import link.locutus.discord.apiv3.csv.column.IntColumn;
import link.locutus.discord.apiv3.csv.column.LongColumn;
import link.locutus.discord.apiv3.csv.column.StringColumn;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.util.TimeUtil;

import java.text.ParseException;

// city_id,nation_id,date_created,name,capital,infrastructure,maxinfra,land,oil_power_plants,wind_power_plants,coal_power_plants,nuclear_power_plants,coal_mines,oil_wells,uranium_mines,iron_mines,lead_mines,bauxite_mines,farms,police_stations,hospitals,recycling_centers,subway,supermarkets,banks,shopping_malls,stadiums,oil_refineries,aluminum_refineries,steel_mills,munitions_factories,barracks,factories,hangars,drydocks,last_nuke_date
public class CityHeader extends DataHeader<DBCity> {

    public CityHeader(long date, Dictionary dict) {
        super(date, dict);
    }

    //    public int city_id;
    public final IntColumn<DBCity> city_id = new IntColumn<>(this, DBCity::setId);
//    public int nation_id;
    public final IntColumn<DBCity> nation_id = new IntColumn<>(this, DBCity::setNation_id);
//    public int date_created; // TimeUtil.YYYY_MM_DD_FORMAT.parse(
    public final LongColumn<DBCity> date_created = new LongColumn<>(this, DBCity::setDateCreated) {
        @Override
        public Long read(String string) {
            try {
                return TimeUtil.YYYY_MM_DD_FORMAT.parse(string).getTime();
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    };
//    public int name; // string ignore
    public final StringColumn<DBCity> name = new StringColumn<>(this, null);
//    public int capital; // string ignore
    public final StringColumn<DBCity> capital = new StringColumn<>(this, null);
//    public int infrastructure; // double
    public final DoubleColumn<DBCity> infrastructure = new DoubleColumn<>(this, DBCity::setInfra);
//    public int maxinfra; // double ignore
    public final DoubleColumn<DBCity> maxinfra = new DoubleColumn<>(this, null);
//    public int land; // double
    public final DoubleColumn<DBCity> land = new DoubleColumn<>(this, DBCity::setLand);
//    public int oil_power_plants; // int
    public final IntColumn<DBCity> oil_power_plants = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.OIL_POWER, value));
//    public int wind_power_plants; // int
    public final IntColumn<DBCity> wind_power_plants = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.WIND_POWER, value));
//    public int coal_power_plants; // int
    public final IntColumn<DBCity> coal_power_plants = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.COAL_POWER, value));
//    public int nuclear_power_plants; // int
    public final IntColumn<DBCity> nuclear_power_plants = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.NUCLEAR_POWER, value));
//    public int coal_mines; // int
    public final IntColumn<DBCity> coal_mines = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.COAL_MINE, value));
//    public int oil_wells; // int
    public final IntColumn<DBCity> oil_wells = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.OIL_WELL, value));
//    public int uranium_mines;
    public final IntColumn<DBCity> uranium_mines = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.URANIUM_MINE, value));
//    public int iron_mines;
    public final IntColumn<DBCity> iron_mines = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.IRON_MINE, value));
//    public int lead_mines;
    public final IntColumn<DBCity> lead_mines = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.LEAD_MINE, value));
//    public int bauxite_mines;
    public final IntColumn<DBCity> bauxite_mines = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.BAUXITE_MINE, value));
//    public int farms;
    public final IntColumn<DBCity> farms = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.FARM, value));
//    public int police_stations;
    public final IntColumn<DBCity> police_stations = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.POLICE_STATION, value));
//    public int hospitals;
    public final IntColumn<DBCity> hospitals = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.HOSPITAL, value));
//    public int recycling_centers;
    public final IntColumn<DBCity> recycling_centers = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.RECYCLING_CENTER, value));
//    public int subway;
    public final IntColumn<DBCity> subway = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.SUBWAY, value));
//    public int supermarkets;
    public final IntColumn<DBCity> supermarkets = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.SUPERMARKET, value));
//    public int banks;
    public final IntColumn<DBCity> banks = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.BANK, value));
//    public int shopping_malls;
    public final IntColumn<DBCity> shopping_malls = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.MALL, value));
//    public int stadiums;
    public final IntColumn<DBCity> stadiums = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.STADIUM, value));
//    public int oil_refineries;
    public final IntColumn<DBCity> oil_refineries = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.GAS_REFINERY, value));
//    public int aluminum_refineries;
    public final IntColumn<DBCity> aluminum_refineries = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.ALUMINUM_REFINERY, value));
//    public int steel_mills;
    public final IntColumn<DBCity> steel_mills = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.STEEL_MILL, value));
//    public int munitions_factories;
    public final IntColumn<DBCity> munitions_factories = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.MUNITIONS_FACTORY, value));
//    public int barracks;
    public final IntColumn<DBCity> barracks = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.BARRACKS, value));
//    public int factories;
    public final IntColumn<DBCity> factories = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.FACTORY, value));
//    public int hangars;
    public final IntColumn<DBCity> hangars = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.HANGAR, value));
//    public int drydocks;
    public final IntColumn<DBCity> drydocks = new IntColumn<>(this, (city, value) -> city.setBuilding(Buildings.DRYDOCK, value));
//    public int last_nuke_date; // long, TimeUtil.YYYY_MM_DD_FORMAT.parse(
    public final LongColumn<DBCity> last_nuke_date = new LongColumn<>(this, (city, value) -> {
        if (value > 0) {
            long turn = TimeUtil.getTurn(value);
            city.setNuke_turn(turn);
        }
    }) {
        @Override
        public Long read(String string) {
            try {
                return TimeUtil.YYYY_MM_DD_FORMAT.parse(string).getTime();
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    };
//    public int powered;  // boolean
    public final BooleanColumn<DBCity> powered = new BooleanColumn<>(this, DBCity::setPowered);

    private DBCity cached;

    @Override
    public void clear() {
        cached = null;
    }

    public DBCity getCity() {
        int cityId = this.city_id.get();
        if (cached != null && cached.id == cityId) {
            return cached;
        }
        DBCity city = new DBCity(this.nation_id.get());
        city.id = cityId;
        city.created = this.date_created.get();
        city.setInfra(this.infrastructure.get());
        city.setLand(this.land.get());

        byte[] buildings = city.buildings3;
        buildings[Buildings.OIL_POWER.ordinal()] += this.oil_power_plants.get();
        buildings[Buildings.WIND_POWER.ordinal()] += this.wind_power_plants.get();
        buildings[Buildings.COAL_POWER.ordinal()] += this.coal_power_plants.get();
        buildings[Buildings.NUCLEAR_POWER.ordinal()] += this.nuclear_power_plants.get();
        buildings[Buildings.COAL_MINE.ordinal()] += this.coal_mines.get();
        buildings[Buildings.OIL_WELL.ordinal()] += this.oil_wells.get();
        buildings[Buildings.URANIUM_MINE.ordinal()] += this.uranium_mines.get();
        buildings[Buildings.IRON_MINE.ordinal()] += this.iron_mines.get();
        buildings[Buildings.LEAD_MINE.ordinal()] += this.lead_mines.get();
        buildings[Buildings.BAUXITE_MINE.ordinal()] += this.bauxite_mines.get();
        buildings[Buildings.FARM.ordinal()] += this.farms.get();
        buildings[Buildings.POLICE_STATION.ordinal()] += this.police_stations.get();
        buildings[Buildings.HOSPITAL.ordinal()] += this.hospitals.get();
        buildings[Buildings.RECYCLING_CENTER.ordinal()] += this.recycling_centers.get();
        buildings[Buildings.SUBWAY.ordinal()] += this.subway.get();
        buildings[Buildings.SUPERMARKET.ordinal()] += this.supermarkets.get();
        buildings[Buildings.BANK.ordinal()] += this.banks.get();
        buildings[Buildings.MALL.ordinal()] += this.shopping_malls.get();
        buildings[Buildings.STADIUM.ordinal()] += this.stadiums.get();
        buildings[Buildings.GAS_REFINERY.ordinal()] += this.oil_refineries.get();
        buildings[Buildings.ALUMINUM_REFINERY.ordinal()] += this.aluminum_refineries.get();
        buildings[Buildings.STEEL_MILL.ordinal()] += this.steel_mills.get();
        buildings[Buildings.MUNITIONS_FACTORY.ordinal()] += this.munitions_factories.get();
        buildings[Buildings.BARRACKS.ordinal()] += this.barracks.get();
        buildings[Buildings.FACTORY.ordinal()] += this.factories.get();
        buildings[Buildings.HANGAR.ordinal()] += this.hangars.get();
        buildings[Buildings.DRYDOCK.ordinal()] += this.drydocks.get();
        return cached = city;
    }
}
