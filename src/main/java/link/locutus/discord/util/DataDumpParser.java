package link.locutus.discord.util;

import com.google.common.io.LineReader;
import com.politicsandwar.graphql.model.BBGame;
import com.ptsmods.mysqlw.query.QueryCondition;
import de.erichseifert.gral.io.data.CSVReader;
import de.siegmar.fastcsv.reader.*;
import de.siegmar.fastcsv.writer.CsvWriter;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.update.LootEstimateTracker;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.util.IndexedStringMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import rocker.grant.nation;
import rocker.index;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class DataDumpParser {
    private final Field headerField;

    private final Map<Project, String> projectMap = new HashMap<>();

    public DataDumpParser() throws NoSuchFieldException {
        this.headerField = NamedCsvReader.class.getDeclaredField("header");
        headerField.setAccessible(true);

        projectMap.put(Projects.BAUXITEWORKS, "bauxiteworks_np");
        projectMap.put(Projects.INTELLIGENCE_AGENCY, "intelligence_agency_np");
        projectMap.put(Projects.IRON_WORKS, "ironworks_np");
        projectMap.put(Projects.GREEN_TECHNOLOGIES, "green_technologies_np");
        for (Project project : Projects.values) {
            projectMap.putIfAbsent(project, project.getApiName() + "_np");
        }
    }

    public static void main(String[] args) throws IOException, ParseException, NoSuchFieldException, IllegalAccessException, SQLException, LoginException, InterruptedException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;

        Locutus.create().start();

        // loop over all the subdirectories on that url index
        DataDumpParser instance = new DataDumpParser();

        List<File> nationFiles = instance.load("https://politicsandwar.com/data/nations/", new File("data/nations"));
        List<File> cityFiles = instance.load("https://politicsandwar.com/data/cities/", new File("data/cities"));

        Map<Long, File> nationFilesByDate = new LinkedHashMap<>();
        Map<Long, File> cityFilesByDate = new LinkedHashMap<>();
        for (File file : nationFiles) nationFilesByDate.put(instance.getDate(file), file);
        for (File file : cityFiles) cityFilesByDate.put(instance.getDate(file), file);


        long minDate = instance.getDate(nationFiles.get(0));

        // get loot info
//        Map<Integer, LootEntry> loot = Locutus.imp().getNationDB().getNationLootMap();
//
//        // get trades
//        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(minDate);
//
//        // get bank records
//        List<Transaction2> transactions = Locutus.imp().getBankDB().getToNationTransactions(minDate);
//
//        // get attacks
//        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(minDate);
//
//        // baseball
//        List<BBGame> games = Locutus.imp().getBaseballDB().getBaseballGames(f -> f.where(QueryCondition.greater("date", minDate)));
//
//        // previous
//        Map<Integer, DBNation> nationsPrevious = new HashMap<>();
//        Map<Integer, Map<Integer, DBCity>> citiesPrevious = new HashMap<>();
//
//        LootEstimateTracker estimator = new LootEstimateTracker(false, 0L, () -> new HashMap<>(), f -> {}, Locutus.imp().getNationDB()::getNation);

//        instance.parseCitiesFile(new File("data/cities/cities-2022-04-21.csv"));

        long start = System.currentTimeMillis();
        for (Map.Entry<Long, File> entry : nationFilesByDate.entrySet()) {
            long date = entry.getKey();
            if (date < TimeUtil.YYYY_MM_DD_FORMAT.parse("2022-04-21").toInstant().toEpochMilli()) continue;
            File nationFile = entry.getValue();
            File cityFile = cityFilesByDate.get(date);
            if (cityFile == null) continue;

            Map<Integer, DBNation> nations = instance.parseNationFile(nationFile);
            Map<Integer, Map<Integer, DBCity>> cities = instance.parseCitiesFile(cityFile);

            // infra
            // buildings
            // land
            // projects
            // units
            // cities


        }
//        instance.parseCitiesFile(new File("data/cities/cities-2022-04-21.csv"));
//        instance.parseNationFile(new File("data/nations/nations-2022-04-21.csv"));

        long diff = System.currentTimeMillis() - start;
        System.out.println("Diff " + diff + "ms");
    }

    public Map<Integer, Map<Integer, DBCity>> parseCitiesFile(File file) throws IOException, NoSuchFieldException, IllegalAccessException {
        Map<Integer, Map<Integer, DBCity>> result = new Int2ObjectOpenHashMap<>();
        readAll(file, new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
            @Override
            public void acceptThrows(List<String> headerList, CloseableIterator<CsvRow> rows) throws NoSuchFieldException, IllegalAccessException, ParseException {
                CityHeader header = loadHeader(new CityHeader(), headerList);
                while (rows.hasNext()) {
                    CsvRow row = rows.next();
                    int nationId = Integer.parseInt(row.getField(header.nation_id));
                    DBNation nation = DBNation.byId(nationId);
                    if (nation != null) {
                        DBCity city = loadCity(header, row);
//                        if (city != null) {
//                            result.computeIfAbsent(nationId, k -> new Int2ObjectOpenHashMap<>()).put(city.id, city);
//                        }
                    }
                }
            }
        });
        return result;
    }

    public void readAll(File file, ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>> onEach) throws IOException, IllegalAccessException, NoSuchFieldException {
        try (CsvReader reader = CsvReader.builder().fieldSeparator(',').quoteCharacter('"').build(file.toPath())) {
            try (CloseableIterator<CsvRow> iter = reader.iterator()) {
                CsvRow header = iter.next();
                List<String> fields = header.getFields();
                onEach.accept(fields, iter);
            }
        }
    }

    public Map<Integer, DBNation> parseNationFile(File file) throws IOException, ParseException, NoSuchFieldException, IllegalAccessException {
        Map<Integer, DBNation> result = new Int2ObjectOpenHashMap<>();
        readAll(file, new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
            @Override
            public void acceptThrows(List<String> headerList, CloseableIterator<CsvRow> rows) throws NoSuchFieldException, IllegalAccessException, ParseException {
                NationHeader header = loadHeader(new NationHeader(), headerList);
                while (rows.hasNext()) {
                    CsvRow row = rows.next();
                    DBNation nation = loadNation(header, row);
                    if (nation != null) {
                        result.put(nation.getId(), nation);
                    }
                }
            }
        });
        return result;
    }

    public long getDate(File file) throws ParseException {
        String dateStr = file.getName().replace("nations-", "").replace("cities-", "").replace(".csv", "");
        return TimeUtil.YYYY_MM_DD_FORMAT.parse(dateStr).toInstant().toEpochMilli();
    }

    public List<File> load(String url, File savePath) throws IOException {
        List<File> files = new ArrayList<>();
        Document dom = Jsoup.parse(FileUtil.readStringFromURL(url));
        for (Element a : dom.select("a")) {
            String subUrl = a.attr("href");
            if (subUrl != null && subUrl.contains(".zip")) {
                String fileUrl = url + subUrl;
                File saveAs = new File(savePath, subUrl.replace(".zip", ""));
                files.add(saveAs);
                if (saveAs.exists()) continue;

                download(fileUrl, saveAs);
                System.out.println(subUrl);
            }
        }
        return files;
    }

    private void download(String fileUrl, File savePath) throws IOException {
        System.out.println("Saving " + savePath);
        byte[] bytes = FileUtil.readBytesFromUrl(fileUrl);
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = in.getNextEntry();
            byte[] data = in.readNBytes((int) entry.getSize());
            FileUtils.writeByteArrayToFile(savePath, data);
        }
    }

    public <T> T loadHeader(T instance, List<String> headerStr) throws NoSuchFieldException, IllegalAccessException {
        for (int i = 0; i < headerStr.size(); i++) {
            String columnName = headerStr.get(i);
            if (i == 0) columnName = columnName.replaceAll("[^a-z_]", "");
            Field field = instance.getClass().getDeclaredField(columnName);
            field.set(instance, i);
        }
        return instance;
    }

    private Map<Integer, Long> cityDateCache = new Int2LongOpenHashMap();

    public DBCity loadCity(CityHeader header, CsvRow row) throws ParseException {
        DBCity city = new DBCity();
        city.id = Integer.parseInt(row.getField(header.city_id));
        Long createdCached = cityDateCache.get(city.id);
        if (createdCached == null) {
            createdCached = TimeUtil.YYYY_MM_DD_FORMAT.parse(row.getField(header.date_created)).toInstant().toEpochMilli();
            cityDateCache.put(city.id, createdCached);
        }
        city.created = createdCached;
        city.infra = Double.parseDouble(row.getField(header.infrastructure));
        city.land = Double.parseDouble(row.getField(header.land));

        city.buildings[Buildings.OIL_POWER.ordinal()] += Byte.parseByte(row.getField(header.oil_power_plants));
        city.buildings[Buildings.WIND_POWER.ordinal()] += Byte.parseByte(row.getField(header.wind_power_plants));
        city.buildings[Buildings.COAL_POWER.ordinal()] += Byte.parseByte(row.getField(header.coal_power_plants));
        city.buildings[Buildings.NUCLEAR_POWER.ordinal()] += Byte.parseByte(row.getField(header.nuclear_power_plants));
        city.buildings[Buildings.COAL_MINE.ordinal()] += Byte.parseByte(row.getField(header.coal_mines));
        city.buildings[Buildings.OIL_WELL.ordinal()] += Byte.parseByte(row.getField(header.oil_wells));
        city.buildings[Buildings.URANIUM_MINE.ordinal()] += Byte.parseByte(row.getField(header.uranium_mines));
        city.buildings[Buildings.IRON_MINE.ordinal()] += Byte.parseByte(row.getField(header.iron_mines));
        city.buildings[Buildings.LEAD_MINE.ordinal()] += Byte.parseByte(row.getField(header.lead_mines));
        city.buildings[Buildings.BAUXITE_MINE.ordinal()] += Byte.parseByte(row.getField(header.bauxite_mines));
        city.buildings[Buildings.FARM.ordinal()] += Byte.parseByte(row.getField(header.farms));
        city.buildings[Buildings.POLICE_STATION.ordinal()] += Byte.parseByte(row.getField(header.police_stations));
        city.buildings[Buildings.HOSPITAL.ordinal()] += Byte.parseByte(row.getField(header.hospitals));
        city.buildings[Buildings.RECYCLING_CENTER.ordinal()] += Byte.parseByte(row.getField(header.recycling_centers));
        city.buildings[Buildings.SUBWAY.ordinal()] += Byte.parseByte(row.getField(header.subway));
        city.buildings[Buildings.SUPERMARKET.ordinal()] += Byte.parseByte(row.getField(header.supermarkets));
        city.buildings[Buildings.BANK.ordinal()] += Byte.parseByte(row.getField(header.banks));
        city.buildings[Buildings.MALL.ordinal()] += Byte.parseByte(row.getField(header.shopping_malls));
        city.buildings[Buildings.STADIUM.ordinal()] += Byte.parseByte(row.getField(header.stadiums));
        city.buildings[Buildings.GAS_REFINERY.ordinal()] += Byte.parseByte(row.getField(header.oil_refineries));
        city.buildings[Buildings.ALUMINUM_REFINERY.ordinal()] += Byte.parseByte(row.getField(header.aluminum_refineries));
        city.buildings[Buildings.STEEL_MILL.ordinal()] += Byte.parseByte(row.getField(header.steel_mills));
        city.buildings[Buildings.MUNITIONS_FACTORY.ordinal()] += Byte.parseByte(row.getField(header.munitions_factories));
        city.buildings[Buildings.BARRACKS.ordinal()] += Byte.parseByte(row.getField(header.barracks));
        city.buildings[Buildings.FACTORY.ordinal()] += Byte.parseByte(row.getField(header.factories));
        city.buildings[Buildings.HANGAR.ordinal()] += Byte.parseByte(row.getField(header.hangars));
        city.buildings[Buildings.DRYDOCK.ordinal()] += Byte.parseByte(row.getField(header.drydocks));

        return city;
    }

    public DBNation loadNation(NationHeader header, CsvRow row) throws ParseException {
        DBNation nation = new DBNation();
        nation.setNation_id(Integer.parseInt(row.getField(header.nation_id)));

        DBNation existing = DBNation.byId(nation.getNation_id());
        if (existing == null) {
            return null;
        }
        nation.setDate(existing.getDate());

//        nation.setDate(TimeUtil.YYYY_MM_DD_HH_MM_SS.parse(row.getField(header.date_created)).toInstant().toEpochMilli());
        nation.setContinent(Continent.parseV3(row.getField(header.continent)));
        nation.setColor(NationColor.valueOf(row.getField(header.color).toUpperCase()));
        nation.setAlliance_id(Integer.parseInt(row.getField(header.alliance_id)));
        nation.setPosition(Rank.byId(Integer.parseInt(row.getField(header.alliance_position))));
        nation.setSoldiers(Integer.parseInt(row.getField(header.soldiers)));
        nation.setTanks(Integer.parseInt(row.getField(header.tanks)));
        nation.setAircraft(Integer.parseInt(row.getField(header.aircraft)));
        nation.setShips(Integer.parseInt(row.getField(header.ships)));
        nation.setMissiles(Integer.parseInt(row.getField(header.missiles)));
        nation.setNukes(Integer.parseInt(row.getField(header.nukes)));
        nation.setDomesticPolicy(DomesticPolicy.parse(row.getField(header.domestic_policy)));
        nation.setWarPolicy(WarPolicy.parse(row.getField(header.war_policy)));
        if (Integer.parseInt(row.getField(header.vm_turns)) > 0) nation.setLeaving_vm(Integer.MAX_VALUE);

        checkProject(nation, row, header.ironworks_np,Projects.IRON_WORKS);
        checkProject(nation, row, header.bauxiteworks_np,Projects.BAUXITEWORKS);
        checkProject(nation, row, header.arms_stockpile_np,Projects.ARMS_STOCKPILE);
        checkProject(nation, row, header.emergency_gasoline_reserve_np,Projects.EMERGENCY_GASOLINE_RESERVE);
        checkProject(nation, row, header.mass_irrigation_np,Projects.MASS_IRRIGATION);
        checkProject(nation, row, header.international_trade_center_np,Projects.INTERNATIONAL_TRADE_CENTER);
        checkProject(nation, row, header.missile_launch_pad_np,Projects.MISSILE_LAUNCH_PAD);
        checkProject(nation, row, header.nuclear_research_facility_np,Projects.NUCLEAR_RESEARCH_FACILITY);
        checkProject(nation, row, header.iron_dome_np,Projects.IRON_DOME);
        checkProject(nation, row, header.vital_defense_system_np,Projects.VITAL_DEFENSE_SYSTEM);
        checkProject(nation, row, header.intelligence_agency_np,Projects.INTELLIGENCE_AGENCY);
        checkProject(nation, row, header.center_for_civil_engineering_np,Projects.CENTER_FOR_CIVIL_ENGINEERING);
        checkProject(nation, row, header.propaganda_bureau_np,Projects.PROPAGANDA_BUREAU);
        checkProject(nation, row, header.uranium_enrichment_program_np,Projects.URANIUM_ENRICHMENT_PROGRAM);
        checkProject(nation, row, header.urban_planning_np,Projects.URBAN_PLANNING);
        checkProject(nation, row, header.advanced_urban_planning_np,Projects.ADVANCED_URBAN_PLANNING);
        checkProject(nation, row, header.space_program_np,Projects.SPACE_PROGRAM);
        checkProject(nation, row, header.moon_landing_np,Projects.MOON_LANDING);
        checkProject(nation, row, header.spy_satellite_np,Projects.SPY_SATELLITE);
        checkProject(nation, row, header.pirate_economy_np,Projects.PIRATE_ECONOMY);
        checkProject(nation, row, header.recycling_initiative_np,Projects.RECYCLING_INITIATIVE);
        checkProject(nation, row, header.telecommunications_satellite_np,Projects.TELECOMMUNICATIONS_SATELLITE);
        checkProject(nation, row, header.green_technologies_np,Projects.GREEN_TECHNOLOGIES);
        checkProject(nation, row, header.clinical_research_center_np,Projects.CLINICAL_RESEARCH_CENTER);
        checkProject(nation, row, header.specialized_police_training_program_np,Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM);
        checkProject(nation, row, header.arable_land_agency_np,Projects.ARABLE_LAND_AGENCY);
        checkProject(nation, row, header.advanced_engineering_corps_np,Projects.ADVANCED_ENGINEERING_CORPS);
        checkProject(nation, row, header.government_support_agency_np,Projects.GOVERNMENT_SUPPORT_AGENCY);
        checkProject(nation, row, header.research_and_development_center_np,Projects.RESEARCH_AND_DEVELOPMENT_CENTER);
        checkProject(nation, row, header.resource_production_center_np,Projects.RESOURCE_PRODUCTION_CENTER);
        checkProject(nation, row, header.metropolitan_planning_np,Projects.METROPOLITAN_PLANNING);
        checkProject(nation, row, header.military_salvage_np,Projects.MILITARY_SALVAGE);
        checkProject(nation, row, header.fallout_shelter_np,Projects.FALLOUT_SHELTER);

        checkProject(nation, row, header.ironworks_np, Projects.IRON_WORKS);

        return nation;
    }

    private void checkProject(DBNation nation, CsvRow row, int index, Project project) {
        if (index <= 0) return;
        if (Objects.equals(row.getField(index), "1")) nation.setProject(project);
    }

    // nation_id,nation_name,leader_name,date_created,continent,latitude,longitude,leader_title,nation_title,score,population,flag_url,color,beige_turns_remaining,portrait_url,cities,gdp,currency,wars_won,wars_lost,alliance,alliance_id,alliance_position,soldiers,tanks,aircraft,ships,missiles,nukes,domestic_policy,war_policy,projects,ironworks_np,bauxiteworks_np,arms_stockpile_np,emergency_gasoline_reserve_np,mass_irrigation_np,international_trade_center_np,missile_launch_pad_np,nuclear_research_facility_np,iron_dome_np,vital_defense_system_np,intelligence_agency_np,center_for_civil_engineering_np,propaganda_bureau_np,uranium_enrichment_program_np,urban_planning_np,advanced_urban_planning_np,space_program_np,moon_landing_np,spy_satellite_np,pirate_economy_np,recycling_initiative_np,telecommunications_satellite_np,green_technologies_np,clinical_research_center_np,specialized_police_training_program_np,arable_land_agency_np,advanced_engineering_corps_np,vm_turns,government_support_agency_np,research_and_development_center_np,resource_production_center_np,metropolitan_planning_np,military_salvage_np,fallout_shelter_np
    public static class NationHeader {
        public int nation_id;
        public int nation_name;
        public int leader_name;
        public int date_created;
        public int continent;
        public int latitude;
        public int longitude;
        public int leader_title;
        public int nation_title;
        public int score;
        public int population;
        public int flag_url;
        public int color;
        public int beige_turns_remaining;
        public int portrait_url;
        public int cities;
        public int gdp;
        public int currency;
        public int wars_won;
        public int wars_lost;
        public int alliance;
        public int alliance_id;
        public int alliance_position;
        public int soldiers;
        public int tanks;
        public int aircraft;
        public int ships;
        public int missiles;
        public int nukes;
        public int domestic_policy;
        public int war_policy;
        public int projects;
        public int ironworks_np;
        public int bauxiteworks_np;
        public int arms_stockpile_np;
        public int emergency_gasoline_reserve_np;
        public int mass_irrigation_np;
        public int international_trade_center_np;
        public int missile_launch_pad_np;
        public int nuclear_research_facility_np;
        public int iron_dome_np;
        public int vital_defense_system_np;
        public int intelligence_agency_np;
        public int center_for_civil_engineering_np;
        public int propaganda_bureau_np;
        public int uranium_enrichment_program_np;
        public int urban_planning_np;
        public int advanced_urban_planning_np;
        public int space_program_np;
        public int moon_landing_np;
        public int spy_satellite_np;
        public int pirate_economy_np;
        public int recycling_initiative_np;
        public int telecommunications_satellite_np;
        public int green_technologies_np;
        public int clinical_research_center_np;
        public int specialized_police_training_program_np;
        public int arable_land_agency_np;
        public int advanced_engineering_corps_np;
        public int vm_turns;
        public int government_support_agency_np;
        public int research_and_development_center_np;
        public int resource_production_center_np;
        public int metropolitan_planning_np;
        public int military_salvage_np;
        public int fallout_shelter_np;
    }


    // city_id,nation_id,date_created,name,capital,infrastructure,maxinfra,land,oil_power_plants,wind_power_plants,coal_power_plants,nuclear_power_plants,coal_mines,oil_wells,uranium_mines,iron_mines,lead_mines,bauxite_mines,farms,police_stations,hospitals,recycling_centers,subway,supermarkets,banks,shopping_malls,stadiums,oil_refineries,aluminum_refineries,steel_mills,munitions_factories,barracks,factories,hangars,drydocks,last_nuke_date
    public static class CityHeader {
        public int city_id;
        public int nation_id;
        public int date_created;
        public int name;
        public int capital;
        public int infrastructure;
        public int maxinfra;
        public int land;
        public int oil_power_plants;
        public int wind_power_plants;
        public int coal_power_plants;
        public int nuclear_power_plants;
        public int coal_mines;
        public int oil_wells;
        public int uranium_mines;
        public int iron_mines;
        public int lead_mines;
        public int bauxite_mines;
        public int farms;
        public int police_stations;
        public int hospitals;
        public int recycling_centers;
        public int subway;
        public int supermarkets;
        public int banks;
        public int shopping_malls;
        public int stadiums;
        public int oil_refineries;
        public int aluminum_refineries;
        public int steel_mills;
        public int munitions_factories;
        public int barracks;
        public int factories;
        public int hangars;
        public int drydocks;
        public int last_nuke_date;
    }
}
