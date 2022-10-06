package link.locutus.discord.apiv3;

import com.politicsandwar.graphql.model.BBGame;
import com.politicsandwar.graphql.model.WarAttack;
import com.politicsandwar.graphql.model.WarAttackResponseProjection;
import de.siegmar.fastcsv.reader.*;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.event.bank.TransactionEvent;
import link.locutus.discord.event.baseball.BaseballGameEvent;
import link.locutus.discord.event.trade.TradeCreateEvent;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.TriConsumer;
import link.locutus.discord.util.update.LootEstimateTracker;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DataDumpParser {

    private Map<Long, File> nationFilesByDay;
    private Map<Long, File> cityFilesByDay;
    public DataDumpParser() {

    }

    public DataDumpParser load() throws IOException, ParseException {
        downloadNationFilesByDay();
        downloadCityFilesByDay();
        return this;
    }

    public long getMinDate() {
        if (nationFilesByDay == null || nationFilesByDay.isEmpty()) throw new IllegalStateException("Please load the data");
        return TimeUtil.getTimeFromDay(nationFilesByDay.keySet().iterator().next());
    }

    public Map<Long, File> downloadCityFilesByDay() throws IOException, ParseException {
        if (cityFilesByDay != null) return cityFilesByDay;
        return cityFilesByDay = load("https://politicsandwar.com/data/cities/", new File("data/cities"));
    }

    public Map<Long, File> downloadNationFilesByDay() throws IOException, ParseException {
        if (nationFilesByDay != null) return nationFilesByDay;
        return nationFilesByDay = load("https://politicsandwar.com/data/nations/", new File("data/nations"));
    }

    public Map<Long, Map<Integer, Continent>> getContinentByNationByDay() throws IOException, NoSuchFieldException, IllegalAccessException {
        Map<Long, Map<Integer, Continent>> continentByNationByDay = new Long2ObjectOpenHashMap<>();

        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
            long day = entry.getKey();
            System.out.println("Read " + day);
            Map<Integer, Continent> continentByNation = continentByNationByDay.computeIfAbsent(day, f -> new Int2ObjectOpenHashMap<>());
            readAll(entry.getValue(), new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
                @Override
                public void acceptThrows(List<String> headerList, CloseableIterator<CsvRow> rows) throws NoSuchFieldException, IllegalAccessException, ParseException {
                    NationHeader header = loadHeader(new NationHeader(), headerList);
                    while (rows.hasNext()) {
                        CsvRow row = rows.next();
                        int nationId = Integer.parseInt(row.getField(header.nation_id));
                        Continent continent = Continent.parseV3(row.getField(header.continent));
                        continentByNation.put(nationId, continent);
                    }
                }
            });
        }
        return continentByNationByDay;
    }

    private class MetricValue {
        int alliance;
        AllianceMetric metric;
        long turn;
        double value;

        public MetricValue(int alliance, AllianceMetric metric, long turn, double value) {
            this.alliance = alliance;
            this.metric = metric;
            this.turn = turn;
            this.value = value;
        }
    }

    public void backCalculateInfra() throws IOException, ParseException, NoSuchFieldException, IllegalAccessException {
        load();
        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
            File cityFile = cityFilesByDay.get(entry.getKey());
            if (cityFile == null) continue;
            if (TimeUtil.getTimeFromDay(entry.getKey()) < 1629861913000L) continue;
            System.out.println("File " + cityFile);

            Map<Integer, Double> infraTotal = new Int2DoubleOpenHashMap();
            Map<Integer, Integer> numCities = new Int2IntOpenHashMap();

            Map<Integer, Integer> nationAlliances = new Int2IntOpenHashMap();

            // nation, non vm, position >1, infra
            readAll(entry.getValue(), new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
                @Override
                public void acceptThrows(List<String> headerList, CloseableIterator<CsvRow> rows) throws Exception {
                    NationHeader header = loadHeader(new NationHeader(), headerList);
                    while (rows.hasNext()) {
                        CsvRow row = rows.next();


                        int alliance = Integer.parseInt(row.getField(header.alliance_id));
                        if (alliance == 0) continue;
                        int vm = Integer.parseInt(row.getField(header.vm_turns));
                        if (vm > 0) continue;
                        int pos = Integer.parseInt(row.getField(header.alliance_position));
                        if (pos <= Rank.APPLICANT.id) continue;

                        int nationId = Integer.parseInt(row.getField(header.nation_id));
                        nationAlliances.put(nationId, alliance);
                    }
                }
            });

            readAll(cityFile, new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
                @Override
                public void acceptThrows(List<String> headerList, CloseableIterator<CsvRow> rows) throws Exception {
                    CityHeader header = loadHeader(new CityHeader(), headerList);
                    while (rows.hasNext()) {
                        CsvRow row = rows.next();

                        int nationId = Integer.parseInt(row.getField(header.nation_id));
                        Integer aaId = nationAlliances.get(nationId);
                        if (aaId == null) continue;
                        double infra = Double.parseDouble(row.getField(header.infrastructure));
                        numCities.put(aaId, numCities.getOrDefault(aaId, 0) + 1);
                        infraTotal.put(aaId, infraTotal.getOrDefault(aaId, 0d) + infra);
                    }
                }
            });

            long day = entry.getKey();
            long turnStart = TimeUtil.getTurn(TimeUtil.getTimeFromDay(day));
            long turnEnd = turnStart + 12;

            List<MetricValue> values = new ArrayList<>();
            for (long turn = turnStart; turn < turnEnd; turn++) {
                for (Map.Entry<Integer, Double> infraEntry : infraTotal.entrySet()) {
                    int aaId = infraEntry.getKey();
                    double total = infraEntry.getValue();
                    double average = total / numCities.get(aaId);

                    DBAlliance aa = DBAlliance.getOrCreate(aaId);
                    values.add(new MetricValue(aaId, AllianceMetric.INFRA, turn, total));
                    values.add(new MetricValue(aaId, AllianceMetric.INFRA_AVG, turn, average));
                }
            }
            Locutus.imp().getNationDB().executeBatch(values, "INSERT OR IGNORE INTO `ALLIANCE_METRICS`(`alliance_id`, `metric`, `turn`, `value`) VALUES(?, ?, ?, ?)", new ThrowingBiConsumer<MetricValue, PreparedStatement>() {
                @Override
                public void acceptThrows(MetricValue value, PreparedStatement stmt) throws Exception {
                    stmt.setInt(1, value.alliance);
                    stmt.setInt(2, value.metric.ordinal());
                    stmt.setLong(3, value.turn);
                    stmt.setDouble(4, value.value);
                }
            });
        }
    }

    public Map<Continent, Double> getRadsAt(long currentTurn, List<DBAttack> attacks, Map<Long, Map<Integer, Continent>> continentInfo) {
        double radsBase = MilitaryUnit.NUKE_RADIATION;

        double[] radsByContinent = new double[Continent.values.length];
        int unknown = 0;
        int total = 0;

        for (DBAttack attack : attacks) {
            long attTurn = TimeUtil.getTurn(attack.epoch);
            long expireTurn = attTurn + 100;

            if (attTurn <= currentTurn && expireTurn > currentTurn) {
                double rads = radsBase * (1 - ((currentTurn - attTurn) / 100d));

                long day = TimeUtil.getDay(attack.epoch);

                Continent continent = continentInfo.getOrDefault(day, Collections.emptyMap()).get(attack.defender_nation_id);
                if (continent == null) {
                    DBNation nation = DBNation.byId(attack.defender_nation_id);
                    if (nation != null) continent = nation.getContinent();
                    else {
//                        System.out.println("Could not find continent for " + attack.defender_nation_id);
                        // pick random continent
                        continent = Continent.values[ThreadLocalRandom.current().nextInt(Continent.values.length)];
                        unknown++;
                    }
                }
                total++;
                radsByContinent[continent.ordinal()] += rads;
            }
        }
        Map<Continent, Double> result = new HashMap<>();
        for (Continent continent : Continent.values) {
            result.put(continent, radsByContinent[continent.ordinal()]);
        }
        System.out.println("Incorrect: " + unknown + "/" + total);
        return result;
    }

    public void backCalculateRadiation() throws ParseException, IOException, NoSuchFieldException, IllegalAccessException {
        load();
        System.out.println("Updating attacks");
        Locutus.imp().getWarDb().updateAttacks(null);
        System.out.println("Done update attacks");

        long minDate = getMinDate();

        long start2 = System.currentTimeMillis();
        Map<Long, Map<Integer, Continent>> continentInfo = getContinentByNationByDay();
        long diff1 = System.currentTimeMillis() - start2;

        long cutoff = minDate - TimeUnit.DAYS.toMillis(20);
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(cutoff, f -> f.attack_type == AttackType.NUKE && f.success > 0);
        Collections.sort(attacks, Comparator.comparingLong(o -> o.epoch));

        long currTurn = TimeUtil.getTurn();

        long start = System.currentTimeMillis();
        for (long turn = TimeUtil.getTurn(minDate); turn <= currTurn; turn++) {
            Map<Continent, Double> radMap = getRadsAt(turn, attacks, continentInfo);
            System.out.println("Rads " + StringMan.getString(radMap));
            for (Map.Entry<Continent, Double> entry : radMap.entrySet()) {
                Locutus.imp().getNationDB().addRadiationByDay(entry.getKey(), turn, entry.getValue());
            }
        }
        long diff = System.currentTimeMillis() - start;
        System.out.println("Diff " + diff + "ms | " + diff1);
    }

    private Set<Integer> getNationsAtWar(long timestamp, Map<DBWar, Long> getWarEndDates) {
        Set<Integer> nationsAtWar = new HashSet<>();
        for (Map.Entry<DBWar, Long> entry : getWarEndDates.entrySet()) {
            DBWar war = entry.getKey();
            if (war.date <= timestamp && timestamp <= entry.getValue()) {
                nationsAtWar.add(war.attacker_id);
                nationsAtWar.add(war.defender_id);
            }
        }
        return nationsAtWar;
    }

    public Map<DBWar, Long> getWarEndDates(Map<Integer, DBWar> wars, Collection<DBAttack> attacks) {
        Map<DBWar, Long> warEndDates = new HashMap<>();
        for (DBAttack attack : attacks) {
            switch (attack.attack_type) {
                case VICTORY, A_LOOT, PEACE -> {
                    DBWar war = wars.get(attack.war_id);
                    if (war != null) {
                        warEndDates.put(war, attack.epoch);
                    }
                }
            }
        }
        for (DBWar war : wars.values()) {
            long expireDate = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(war.date) + 60);
            warEndDates.putIfAbsent(war, expireDate);
        }
        return warEndDates;
    }

    public LootEstimateTracker exportToTracker() throws IOException, ParseException, NoSuchFieldException, IllegalAccessException {
        load();

        long minDate = getMinDate();

        Map<Integer, Map.Entry<Long, double[]>> legacyLoot = Locutus.imp().getWarDb().getNationLootFromAttacksLegacy();

        Map<Integer, LootEntry> loot = Locutus.imp().getNationDB().getNationLootMap();

        Map<Integer, LootEntry> minLootDate = new Int2ObjectOpenHashMap(loot);
        for (Map.Entry<Integer, Map.Entry<Long, double[]>> entry : legacyLoot.entrySet()) {
            int nationId = entry.getKey();
            LootEntry existing = minLootDate.get(nationId);
            if (existing == null || existing.getDate() > entry.getValue().getKey()) {
                LootEntry lootInfo = new LootEntry(nationId, entry.getValue().getValue(), entry.getValue().getKey(), NationLootType.WAR_LOSS);
                minLootDate.put(entry.getKey(), lootInfo);
            }
        }

        Locutus.imp().runEventsAsync(Locutus.imp().getWarDb()::updateAllWars);
        Locutus.imp().runEventsAsync(Locutus.imp().getWarDb()::updateAttacks);

        Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWarsSince(minDate - TimeUnit.DAYS.toMillis(5));
        Collection<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(minDate - TimeUnit.DAYS.toMillis(20));

        Map<DBWar, Long> warEndDates = getWarEndDates(wars, attacks);

        long twoDays = TimeUnit.DAYS.toMillis(2);

        LootEstimateTracker tracker = new LootEstimateTracker(true, 0L, false, f -> {
        }, (nationId, taxIds, doubles) -> System.out.println("Ignore saving tax rate"),
            id -> {
            return DBNation.byId(id);
//                throw new IllegalArgumentException("Call to get nation not allowed");
        });

        for (Map.Entry<Integer, LootEntry> entry : minLootDate.entrySet()) {
            int nationId = entry.getKey();
            LootEntry minEntry = entry.getValue();
            double[] rss = minEntry.getTotal_rss();
            LootEstimateTracker.LootEstimate estimate = new LootEstimateTracker.LootEstimate(rss, minEntry.getDate());
            tracker.addLootEstimate(nationId, estimate);
        }

        // add daily logins
        {
            Map<Long, Set<Integer>> activity = Locutus.imp().getNationDB().getActivityByDay(minDate, id -> DBNation.byId(id) != null);
            Map<Integer, Integer> sequentialLoginsByNationId = new Int2IntOpenHashMap();
            // for loop each day
            for (long day = TimeUtil.getDay(minDate); day < TimeUtil.getDay(); day++) {
                long timestamp = TimeUtil.getTimeFromDay(day);
                Set<Integer> activeToday = activity.getOrDefault(day, Collections.emptySet());
                // remove all nations that are not active today from sequentialLoginsByNationId
                sequentialLoginsByNationId.entrySet().removeIf(entry -> !activeToday.contains(entry.getKey()));
                for (int nationId : activeToday) {
                    DBNation nation = DBNation.byId(nationId);
                    int total = sequentialLoginsByNationId.getOrDefault(nationId, 0) + 1;

                    int age = (int) TimeUnit.MILLISECONDS.toDays(nation.getDate() - timestamp);
                    if (age > 0) {
                        tracker.loginBonus(nationId, age, total, timestamp);
                    }
                    sequentialLoginsByNationId.put(nationId, total);
                }
            }

        }

        // add attacks
        for (DBAttack attack : attacks) {
            tracker.onAttack(attack, false);
        }

        // add bank recs
        Locutus.imp().runEventsAsync(Locutus.imp().getBankDB()::updateBankRecs);
        for (Transaction2 transaction : Locutus.imp().getBankDB().getTransactions(minDate, false)) {
            tracker.bankEvent(new TransactionEvent(transaction));
        }

        // add trades
        for (DBTrade trade : Locutus.imp().getTradeManager().getTradeDb().getTrades(minDate)) {
            tracker.onTradeCreate(new TradeCreateEvent(trade));
        }

        // add baseball
        Locutus.imp().runEventsAsync(Locutus.imp().getBaseballDB()::updateGames);
        for (BBGame game : Locutus.imp().getBaseballDB().getBaseballGames(f -> {
        })) {
            tracker.onBaseball(new BaseballGameEvent(game));
        }

        double[] EMPTY = ResourceType.getBuffer();

        Map<Long, Map<Continent, Double>> radsByDay = Locutus.imp().getNationDB().getRadiationByDay();

        long previousTurn = 0L;
        Map<Integer, DBNation> previousNationsMap = null;
        Map<Integer, Map<Integer, DBCity>> previousCitiesMap = null;

        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
            long day = entry.getKey();
            File nationFile = entry.getValue();
            File cityFile = cityFilesByDay.get(day);
            if (cityFile == null) continue;

            long currentTimestamp = TimeUtil.getTimeFromDay(day);
            long currentTurn = TimeUtil.getTurn(currentTimestamp);
            System.out.println("Loading " + nationFile);

            Map<Integer, DBNation> dayNations = parseNationFile(nationFile, id -> {
                LootEntry minEntry = minLootDate.get(id);
                return minEntry == null || minEntry.getDate() - twoDays <= currentTimestamp;
            }, true, false);
            Map<Integer, Map<Integer, DBCity>> dayCities = parseCitiesFile(cityFile, dayNations::containsKey);
            Map<Integer, Map<Integer, JavaCity>> javaCities = new Int2ObjectOpenHashMap<>();
            for (Map.Entry<Integer, Map<Integer, DBCity>> nationCityEntry : dayCities.entrySet()) {
                DBNation nation = dayNations.get(nationCityEntry.getKey());
                if (nation == null) continue;
                for (Map.Entry<Integer, DBCity> cityEntry : nationCityEntry.getValue().entrySet()) {
                    javaCities.computeIfAbsent(nation.getId(), k -> new Int2ObjectOpenHashMap<>()).put(cityEntry.getKey(), cityEntry.getValue().toJavaCity(nation));
                }
            }

            if (previousTurn > 0) {

                for (long turn = previousTurn; turn < currentTurn; turn++) {
                    long turnTimestamp = TimeUtil.getTimeFromTurn(turn);

                    Set<Integer> nationsAtWar = getNationsAtWar(turnTimestamp, warEndDates);
                    Map<Continent, Double> radsByContinent = radsByDay.get(turnTimestamp);
                    double globalRads = radsByContinent.values().stream().mapToDouble(Double::doubleValue).sum() / 5d;
                    long gameTime = TimeUtil.getOrbisDate(TimeUtil.getTimeFromTurn(turn));

                    for (Map.Entry<Integer, DBNation> nationEntry : dayNations.entrySet()) {
                        DBNation nation = nationEntry.getValue();
                        int vmTurns = nation.getVm_turns();
                        if (previousTurn + vmTurns > turn) continue;
                        Map<Integer, JavaCity> nationCities = javaCities.get(entry.getKey());
                        if (nationCities == null) continue;
                        nation.setCities(nationCities.size());


                        boolean atWar = nationsAtWar.contains(nation.getId());
                        double rads = radsByContinent.get(nation.getContinent()) + globalRads;

                        double[] revenue = PnwUtil.getRevenue(null, 1, gameTime, nation, nationCities.values(), true, true, true, true, false, rads, atWar);
                        if (nation.isTaxable()) {
                            tracker.getOrCreate(nation.getId()).addUnknownRevenue(tracker, nation.getId(), -1, turnTimestamp, revenue);
//                            tracker.add(nation.getId(), turnTimestamp, EMPTY, revenue);
                        } else {
                            tracker.addRevenue(nation.getId(), turnTimestamp, revenue, -1);
                        }
                    }
                }

                for (Map.Entry<Integer, DBNation> nationEntry : dayNations.entrySet()) {
                    int nationId = nationEntry.getKey();
                    DBNation currentNation = nationEntry.getValue();
                    DBNation previousNation = previousNationsMap == null ? null : previousNationsMap.get(nationId);

                    Map<Integer, DBCity> currentCities = dayCities.get(nationId);
                    Map<Integer, DBCity> previousCities = previousCitiesMap == null ? null : previousCitiesMap.get(nationId);

                    if (currentNation != null && previousNation != null) {

                        // projects
                        for (Project project : currentNation.getProjects()) {
                            if (!previousNation.hasProject(project)) {
                                double[] cost = currentNation.projectCost(project);
                                tracker.add(currentNation.getId(), currentTimestamp, cost);
                            }
                        }

                        // units
                        for (MilitaryUnit unit : MilitaryUnit.values) {
                            int amt = currentNation.getUnits(unit) - previousNation.getUnits(unit);
                            if (amt > 0) {
                                tracker.add(nationId, currentTimestamp, unit.getCost(amt));
                            }
                        }

                        // cities
                        if (currentCities != null && previousCities != null) {
                            for (Map.Entry<Integer, DBCity> cityEntry : currentCities.entrySet()) {
                                DBCity previousCity = previousCities.get(cityEntry.getKey());
                                DBCity city = cityEntry.getValue();
                                double infra = previousCity == null ? 10 : previousCity.infra;
                                double land = previousCity == null ? 250 : previousCity.land;
                                byte[] previousBuildings;
                                if (previousCity != null) {
                                    previousBuildings = previousCity.buildings;
                                } else {
                                    previousBuildings = new byte[city.buildings.length];
                                }
                                double[] total = ResourceType.getBuffer();
                                for (Building building : Buildings.values()) {
                                    int amt = city.buildings[building.ordinal()] - previousBuildings[building.ordinal()];
                                    if (amt > 0) total = building.cost(total, amt);
                                }
                                tracker.add(nationId, currentTimestamp, total);

                                if (city.infra > infra + 0.01) {
                                    tracker.add(nationId, currentTimestamp, currentNation.infraCost(infra, city.infra));
                                }
                                if (city.land > land + 0.01) {
                                    tracker.add(nationId, currentTimestamp, currentNation.landCost(infra, city.infra));
                                }
                            }

                            // cities
                            if (currentCities.size() > previousCities.size()) {
                                double cost = PnwUtil.cityCost(currentNation, previousCities.size(), currentCities.size());
                                tracker.add(nationId, currentTimestamp, cost);
                            }
                        }
                    }

                }
            }




            // when tax rate is resolved via estimate, don't use the estimate

            previousNationsMap = dayNations;
            previousCitiesMap = dayCities;
            previousTurn = currentTurn;
        }
        // diff by taxrate

        // loot_estimates int nation_id, double[] min, double[] max, double[] offset, long lastTurnRevenue, int tax_id
        // loot_estimate_by_tax_id, int nation_id, int tax_id, double[] resources,
            // - when absolute, delete all tax loot estimates except current running
        // tax_estimage: int tax_id, int minMoney, int maxMoney, int minRss, int maxRss


        // have a way to include guesses without throwing away the current margins

        // iterate over loot estimates and resolve tax rates
        for (Map.Entry<Integer, Map.Entry<Long, double[]>> entry : legacyLoot.entrySet()) {

        }

        // resolve the tracker queues
        // estimate tax rate from that
        return tracker;
    }

    public Map<Long, Map<Integer, Map<Integer, Double>>> cityInfraByDay(BiPredicate<Long, Integer> dateNationFilter) throws IOException, ParseException, NoSuchFieldException, IllegalAccessException {
        Map<Long, Map<Integer, Map<Integer, Double>>> result = new Long2ObjectOpenHashMap<>();

        for (Map.Entry<Long, File> entry : downloadCityFilesByDay().entrySet()) {
            System.out.println("File " + entry.getValue());
            long day = entry.getKey();
            readAll(entry.getValue(), new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
                @Override
                public void acceptThrows(List<String> elem, CloseableIterator<CsvRow> rows) throws Exception {
                    CityHeader header = loadHeader(new CityHeader(), elem);
                    while (rows.hasNext()) {
                        CsvRow row = rows.next();
                        int nationId = Integer.parseInt(row.getField(header.nation_id));
                        if (!dateNationFilter.test(day, nationId)) continue;

                        int cityId = Integer.parseInt(row.getField(header.city_id));
                        double infra = Double.parseDouble(row.getField(header.infrastructure));
                        result.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, f -> new Int2ObjectOpenHashMap<>()).put(cityId, infra);
                    }
                }
            });
            return result;
        }

        return result;
    }

    public void backCalculateBeigeDamage() throws IOException, ParseException, NoSuchFieldException, IllegalAccessException {
        load();
        long min = getMinDate();
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(min, f -> f.attack_type == AttackType.VICTORY && f.infra_destroyed_value <= 0);
        Map<Long, Set<Integer>> nationsByDay = new HashMap<>();
        for (DBAttack attack : attacks) {
            long day = TimeUtil.getDay(attack.epoch);
            nationsByDay.computeIfAbsent(day, k -> new HashSet<>()).add(attack.defender_nation_id);
        }

        System.out.println("Attacks " + attacks.size());

        Map<Long, Map<Integer, Map<Integer, Double>>> infraMap = cityInfraByDay(new BiPredicate<Long, Integer>() {
            @Override
            public boolean test(Long day, Integer nation) {
                return nationsByDay.getOrDefault(day, Collections.emptySet()).contains(nation);
            }
        });

        attacks.removeIf(new Predicate<DBAttack>() {
            @Override
            public boolean test(DBAttack dbAttack) {
                long day = TimeUtil.getDay(dbAttack.epoch);
                Map<Integer, Map<Integer, Double>> infraDay = infraMap.get(day);
                if (infraDay == null) return true;
                return infraDay.containsKey(dbAttack.defender_nation_id);
            }
        });

        Map<Integer, DBAttack> attacksById = new HashMap<>();
        for (DBAttack attack : attacks) {
            attacksById.put(attack.war_attack_id, attack);
        }

        List<Integer> attacksToFetch = new ArrayList<>(attacks.stream().map(f -> f.war_attack_id).collect(Collectors.toList()));
        int amtPer = 999;
        for (int i = 0; i < attacksToFetch.size(); i+=amtPer) {
            List<Integer> subList = attacksToFetch.subList(i, i + amtPer);
            for (WarAttack attack : Locutus.imp().getV3().fetchAttacks(f -> f.setId(subList), new Consumer<WarAttackResponseProjection>() {
                @Override
                public void accept(WarAttackResponseProjection proj) {
                    proj.id();
                    proj.loot_info();
                }
            })) {
                int id = attack.getId();
                String note = attack.getLoot_info();
                String end = "% of the infrastructure in each of their cities.";
                String[] split = note.substring(0, note.length() - end.length()).split(" ");
                try {
                    DBAttack dbAttack = attacksById.get(id);
                    double infraPercent_cached = Double.parseDouble(split[split.length - 1]) / 100d;

                    long day = TimeUtil.getDay(dbAttack.epoch);
                    Map<Integer, Double> cityInfra = infraMap.get(day).get(dbAttack.defender_nation_id);
                    double cost = 0;
                    for (Map.Entry<Integer, Double> entry : cityInfra.entrySet()) {
                        double infraAmt = entry.getValue();
                        cost += PnwUtil.calculateInfra(infraAmt * (1 - infraPercent_cached), infraAmt);
                    }
                    dbAttack.infra_destroyed_value = cost;
                    System.out.println("Save $" + dbAttack.infra_destroyed_value);
                    Locutus.imp().getWarDb().saveAttacks(List.of(dbAttack));

                } catch (Throwable e) {
                    System.out.println("Error parsing " + note);
                }
            }

        }



    }

    public static void main(String[] args) throws IOException, ParseException, NoSuchFieldException, IllegalAccessException, SQLException, LoginException, InterruptedException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = true;

        Locutus.create().start();

        DataDumpParser instance = new DataDumpParser().load();

        {
            instance.load();
            instance.exportToTracker();
//            Writer writer = new BufferedWriter(new FileWriter("C:/Users/jesse/Documents/GitHub/locutus/cities.csv"));
//            writer.write("nation\tcity\tinfra\tdate\n");
//            for (Map.Entry<Long, File> entry : instance.downloadCityFilesByDay().entrySet()) {
//                System.out.println("File " + entry.getValue());
//
//
//                instance.readAll(entry.getValue(), new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
//                    @Override
//                    public void acceptThrows(List<String> elem, CloseableIterator<CsvRow> rows) throws Exception {
//                        CityHeader header = instance.loadHeader(new CityHeader(), elem);
//                        while (rows.hasNext()) {
//                            CsvRow row = rows.next();
//                            int nationId = Integer.parseInt(row.getField(header.nation_id));
//                            int cityId = Integer.parseInt(row.getField(header.city_id));
//                            double infra = Double.parseDouble(row.getField(header.infrastructure));
//                            writer.write(nationId + "\t" + cityId + "\t" + String.format("%.2f", infra) + "\t" + entry.getKey() + "\n");
//                        }
//
//
//                    }
//                });
//            }
//            writer.close();

//            instance.backCalculateRadiation();
            System.out.println("Done!");
            System.exit(0);
        }

        // get loot info
        Map<Integer, LootEntry> loot = Locutus.imp().getNationDB().getNationLootMap();
        System.out.println("Loot entries " + loot.size());
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

//        instance.parseCitiesFile(new File("data/cities/cities-2022-04-21.csv"));
//        instance.parseNationFile(new File("data/nations/nations-2022-04-21.csv"));

        long diff = System.currentTimeMillis() - start;
        System.out.println("Diff " + diff + "ms");
    }

    public Map<Integer, Map<Integer, DBCity>> parseCitiesFile(File file, Predicate<Integer> allowedNationIds) throws IOException, NoSuchFieldException, IllegalAccessException {
        Map<Integer, Map<Integer, DBCity>> result = new Int2ObjectOpenHashMap<>();
        readAll(file, new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
            @Override
            public void acceptThrows(List<String> headerList, CloseableIterator<CsvRow> rows) throws NoSuchFieldException, IllegalAccessException, ParseException {
                CityHeader header = loadHeader(new CityHeader(), headerList);
                while (rows.hasNext()) {
                    CsvRow row = rows.next();
                    int nationId = Integer.parseInt(row.getField(header.nation_id));
                    if (allowedNationIds.test(nationId)) {
                        DBCity city = loadCity(header, row);
                        result.computeIfAbsent(nationId, k -> new Int2ObjectOpenHashMap<>()).put(city.id, city);
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

    public Map<Integer, DBNation> parseNationFile(File file, Predicate<Integer> allowedNationIds, boolean allowVm, boolean allowDeleted) throws IOException, ParseException, NoSuchFieldException, IllegalAccessException {
        Map<Integer, DBNation> result = new Int2ObjectOpenHashMap<>();
        readAll(file, new ThrowingBiConsumer<List<String>, CloseableIterator<CsvRow>>() {
            @Override
            public void acceptThrows(List<String> headerList, CloseableIterator<CsvRow> rows) throws NoSuchFieldException, IllegalAccessException, ParseException {
                NationHeader header = loadHeader(new NationHeader(), headerList);
                while (rows.hasNext()) {
                    CsvRow row = rows.next();
                    DBNation nation = loadNation(header, row, allowedNationIds, allowVm, allowDeleted);
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

    public Map<Long, File> load(String url, File savePath) throws IOException, ParseException {
        Map<Long, File> filesByDate = new LinkedHashMap<>();
        Document dom = Jsoup.parse(FileUtil.readStringFromURL(url));
        for (Element a : dom.select("a")) {
            String subUrl = a.attr("href");
            if (subUrl != null && subUrl.contains(".zip")) {
                String fileUrl = url + subUrl;
                File saveAs = new File(savePath, subUrl.replace(".zip", ""));
                filesByDate.put(TimeUtil.getDay(getDate(saveAs)), saveAs);
                if (saveAs.exists()) continue;

                download(fileUrl, saveAs);
                System.out.println(subUrl);
            }
        }
        return filesByDate;
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

    private Map<Integer, Long> nationDateCache = new Int2LongOpenHashMap();

    public DBNation loadNation(NationHeader header, CsvRow row, Predicate<Integer> allowedNationIds, boolean allowVm, boolean allowDeleted) throws ParseException {
        int vm_turns = Integer.MAX_VALUE;
        if (!allowVm) {
            vm_turns = Integer.parseInt(row.getField(header.vm_turns));
            if (vm_turns > 0) return null;
        }

        int nationId = Integer.parseInt(row.getField(header.nation_id));
        if (!allowedNationIds.test(nationId)) return null;
        DBNation existing = DBNation.byId(nationId);
        if (existing == null && !allowDeleted) {
            return null;
        }
        DBNation nation = new DBNation();
        if (existing == null) {
            Long date = nationDateCache.get(nationId);
            if (date == null) {
                date = TimeUtil.YYYY_MM_DD_HH_MM_SS.parse(row.getField(header.date_created)).toInstant().toEpochMilli();
            }
            nationDateCache.put(nationId, date);
        } else {
            nation.setDate(existing.getDate());
        }
        if (vm_turns == Integer.MAX_VALUE) {
            int vm = Integer.parseInt(row.getField(header.vm_turns));
            if (vm > 0) {
                nation.setLeaving_vm(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + vm));
            }
        }
        nation.setNation_id(nationId);



//        nation.setDate();
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
