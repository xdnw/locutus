package link.locutus.discord.apiv3.csv.header;

import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.csv.column.*;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.util.TimeUtil;

import java.text.ParseException;

// city_id,nation_id,date_created,name,capital,infrastructure,maxinfra,land,oil_power_plants,wind_power_plants,coal_power_plants,nuclear_power_plants,coal_mines,oil_wells,uranium_mines,iron_mines,lead_mines,bauxite_mines,farms,police_stations,hospitals,recycling_centers,subway,supermarkets,banks,shopping_malls,stadiums,oil_refineries,aluminum_refineries,steel_mills,munitions_factories,barracks,factories,hangars,drydocks,last_nuke_date
public class CityHeader extends DataHeader<DBCity> {

    public CityHeader(Dictionary dict) {
        super(dict);
    }

    //    public int city_id;
    public final IntColumn<DBCity> city_id = new IntColumn<>(this, DBCity::setId);
//    public int nation_id;
    public final IntColumn<DBCity> nation_id = new IntColumn<>(this, DBCity::setNation_id);
//    public int date_created; // TimeUtil.YYYY_MM_DD_FORMAT.parse(
    public final DayColumn<DBCity> date_created = new DayColumn<>(this, DBCity::setDateCreated);
//    public int name; // string ignore
    public final StringColumn<DBCity> name = new StringColumn<>(this, null);
//    public int capital; // string ignore
    public final StringColumn<DBCity> capital = new StringColumn<>(this, null);
//    public int infrastructure; // double
    public final DoubleIntColumn<DBCity> infrastructure = new DoubleIntColumn<>(this, DBCity::setInfra);
//    public int maxinfra; // double ignore
    public final DoubleIntColumn<DBCity> maxinfra = new DoubleIntColumn<>(this, null);
//    public int land; // double
    public final DoubleIntColumn<DBCity> land = new DoubleIntColumn<>(this, DBCity::setLand);
//    public int oil_power_plants; // int
    public final BuildingColumn oil_power_plants = new BuildingColumn(this, Buildings.OIL_POWER);
//    public int wind_power_plants; // int
    public final BuildingColumn wind_power_plants = new BuildingColumn(this, Buildings.WIND_POWER);
//    public int coal_power_plants; // int
    public final BuildingColumn coal_power_plants = new BuildingColumn(this, Buildings.COAL_POWER);
//    public int nuclear_power_plants; // int
    public final BuildingColumn nuclear_power_plants = new BuildingColumn(this, Buildings.NUCLEAR_POWER);
//    public int coal_mines; // int
    public final BuildingColumn coal_mines = new BuildingColumn(this, Buildings.COAL_MINE);
//    public int oil_wells; // int
    public final BuildingColumn oil_wells = new BuildingColumn(this, Buildings.OIL_WELL);
//    public int uranium_mines;
    public final BuildingColumn uranium_mines = new BuildingColumn(this, Buildings.URANIUM_MINE);
//    public int iron_mines;
    public final BuildingColumn iron_mines = new BuildingColumn(this, Buildings.IRON_MINE);
//    public int lead_mines;
    public final BuildingColumn lead_mines = new BuildingColumn(this, Buildings.LEAD_MINE);
//    public int bauxite_mines;
    public final BuildingColumn bauxite_mines = new BuildingColumn(this, Buildings.BAUXITE_MINE);
//    public int farms;
    public final BuildingColumn farms = new BuildingColumn(this, Buildings.FARM);
//    public int police_stations;
    public final BuildingColumn police_stations = new BuildingColumn(this, Buildings.POLICE_STATION);
//    public int hospitals;
    public final BuildingColumn hospitals = new BuildingColumn(this, Buildings.HOSPITAL);
//    public int recycling_centers;
    public final BuildingColumn recycling_centers = new BuildingColumn(this, Buildings.RECYCLING_CENTER);
//    public int subway;
    public final BuildingColumn subway = new BuildingColumn(this, Buildings.SUBWAY);
//    public int supermarkets;
    public final BuildingColumn supermarkets = new BuildingColumn(this, Buildings.SUPERMARKET);
//    public int banks;
    public final BuildingColumn banks = new BuildingColumn(this, Buildings.BANK);
//    public int shopping_malls;
    public final BuildingColumn shopping_malls = new BuildingColumn(this, Buildings.MALL);
//    public int stadiums;
    public final BuildingColumn stadiums = new BuildingColumn(this, Buildings.STADIUM);
//    public int oil_refineries;
    public final BuildingColumn oil_refineries = new BuildingColumn(this, Buildings.GAS_REFINERY);
//    public int aluminum_refineries;
    public final BuildingColumn aluminum_refineries = new BuildingColumn(this, Buildings.ALUMINUM_REFINERY);
//    public int steel_mills;
    public final BuildingColumn steel_mills = new BuildingColumn(this, Buildings.STEEL_MILL);
//    public int munitions_factories;
    public final BuildingColumn munitions_factories = new BuildingColumn(this, Buildings.MUNITIONS_FACTORY);
//    public int barracks;
    public final BuildingColumn barracks = new BuildingColumn(this, Buildings.BARRACKS);
//    public int factories;
    public final BuildingColumn factories = new BuildingColumn(this, Buildings.FACTORY);
//    public int hangars;
    public final BuildingColumn hangars = new BuildingColumn(this, Buildings.HANGAR);
//    public int drydocks;
    public final BuildingColumn drydocks = new BuildingColumn(this, Buildings.DRYDOCK);
//    public int last_nuke_date; // long, TimeUtil.YYYY_MM_DD_FORMAT.parse(
    public final IntColumn<DBCity> last_nuke_date = new IntColumn<>(this, (city, value) -> {
        if (value > 0) {
            long turn = TimeUtil.getTurn(value.longValue() + TimeUtil.getTurn(TimeUtil.getOrigin()));
            city.setNuke_turn(turn);
        }
    }) {
        @Override
        public Integer read(String string) {
            try {
                long timeMs = TimeUtil.YYYY_MM_DD_FORMAT.parse(string).getTime();
                return timeMs <= 0 ? 0 : (int) (TimeUtil.getTurn(timeMs) - TimeUtil.getTurn(TimeUtil.getOrigin()));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    };
//    public int powered;  // boolean
    public final BooleanColumn<DBCity> powered = new BooleanColumn<>(this, DBCity::setPowered).alias("");
}
