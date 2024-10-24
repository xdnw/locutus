package link.locutus.discord.apiv3.csv;

import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.FastBufferedInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.VictoryCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv3.csv.file.CitiesFile;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.util.IOUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DataUtil {
    private final DataDumpParser parser;

    public DataUtil(DataDumpParser parser) {
        this.parser = parser;
    }

    /**
     * Get the VM ranges for each nation, using a cached file
     * @param updateAfterDay Update if the cache day is below this (use Long.MAX_VALUE to force update)
     * @return Map of nation id to the last day they were present
     * @throws IOException
     * @throws ParseException
     */
    public synchronized Map<Integer, List<Map.Entry<Integer, Integer>>> getCachedVmRanged(long minDay, boolean addCurrentStatus) throws IOException, ParseException {
        long start = System.currentTimeMillis();
        File file = new File(parser.getNationDir(), "vm_ranges.bin");
        long currentDay = TimeUtil.getDay();

        // Read the first long (8 bytes) to get the day created
        long fileDay = -1;
        if (file.exists()) {
            try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
                System.out.println("Load from file");
                fileDay = dis.readLong();
            } catch (FileNotFoundException e) {
                fileDay = -1; // File does not exist, force recreation
            }
        }

        System.out.println(":||Remove vm cache 1 " + ( - start + (start = System.currentTimeMillis())));

        if (fileDay == -1 || fileDay != currentDay && (fileDay < minDay || minDay < 0)) {
            System.out.println("Is not cached");
            // Recreate the ranges
            Map<Integer, List<Map.Entry<Integer, Integer>>> ranges = getVMRanges(f -> true, f -> true, true);
            List<Integer> nationIdsSorted = new ObjectArrayList<>(ranges.keySet());
            nationIdsSorted.sort(Integer::compareTo);

            System.out.println(":||Remove vm cache 2 " + ( - start + (start = System.currentTimeMillis())));

            // Write the new ranges to the file
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file), Character.MAX_VALUE)) {
                new DataOutputStream(bos).writeLong(currentDay); // Write the current day
                try (DataOutputStream dos = new DataOutputStream(new LZ4BlockOutputStream(bos))) {
                    IOUtil.writeVarInt(dos, nationIdsSorted.size());
                    for (int nationId : nationIdsSorted) {
                        List<Map.Entry<Integer, Integer>> natRanges = ranges.get(nationId);
                        IOUtil.writeVarInt(dos, nationId);
                        IOUtil.writeVarInt(dos, natRanges.size());
                        for (Map.Entry<Integer, Integer> range : natRanges) {
                            IOUtil.writeVarInt(dos, range.getKey());
                            IOUtil.writeVarInt(dos, range.getValue());
                        }
                    }
                }
            }

            System.out.println(":||Remove vm cache 3 " + ( - start + (start = System.currentTimeMillis())));

            return ranges;
        }

        System.out.println(":||Remove vm cache 4 " + ( - start + (start = System.currentTimeMillis())));
        // Read the ranges from the file
        Map<Integer, List<Map.Entry<Integer, Integer>>> ranges = new Int2ObjectOpenHashMap<>();
        try (FastBufferedInputStream fbis = new FastBufferedInputStream(new FileInputStream(file), Character.MAX_VALUE)) {
            fbis.skip(8); // Skip the first 8 bytes
            try (DataInputStream dis = new DataInputStream(new LZ4BlockInputStream(fbis))) {
                int numNations = IOUtil.readVarInt(dis);
                for (int i = 0; i < numNations; i++) {
                    int nationId = IOUtil.readVarInt(dis);
                    int size = IOUtil.readVarInt(dis);
                    List<Map.Entry<Integer, Integer>> list = new ObjectArrayList<>(size);
                    for (int j = 0; j < size; j++) {
                        int rangeKey = IOUtil.readVarInt(dis);
                        int rangeValue = IOUtil.readVarInt(dis);
                        list.add(Map.entry(rangeKey, rangeValue));
                    }
                    ranges.put(nationId, list);
                }
            }
        }

        System.out.println(":||Remove vm cache 5 " + ( - start + (start = System.currentTimeMillis())));

        if (addCurrentStatus) {
            for (DBNation nation : Locutus.imp().getNationDB().getAllNations()) {
                if (nation.getVm_turns() > 0) {
                    List<Map.Entry<Integer, Integer>> existing = ranges.computeIfAbsent(nation.getNation_id(), k -> new ObjectArrayList<>());
                    if (!existing.isEmpty() && existing.get(existing.size() - 1).getValue() == Integer.MAX_VALUE) {
                        continue;
                    }
                    existing.add(Map.entry((int) currentDay, Integer.MAX_VALUE));
                }
            }
        }

        System.out.println(":||Remove vm cache 6 " + ( - start + (start = System.currentTimeMillis())));

        return ranges;
    }

    public Set<Integer> getVMNations(Map<Integer, List<Map.Entry<Integer, Integer>>> vmRanges, int day) {
        Set<Integer> result = new IntOpenHashSet();
        for (Map.Entry<Integer, List<Map.Entry<Integer, Integer>>> entry : vmRanges.entrySet()) {
            int nationId = entry.getKey();
            for (Map.Entry<Integer, Integer> range : entry.getValue()) {
                int startDay = range.getKey();
                int endDay = range.getValue();
                if (startDay < day && endDay > day) {
                    result.add(nationId);
                    break;
                }
            }
        }
        return result;
    }

    public Map<Integer, List<Map.Entry<Integer, Integer>>> getVMRanges(Predicate<Long> allowDays, Predicate<Integer> nationIds, boolean addCurrentStatus) throws IOException, ParseException {
        List<Long> days = parser.getDays(true, false).reversed();
        Map<Integer, Long> dateCreated = new Int2LongOpenHashMap();

        Set<Integer> lastPresentIds = new IntOpenHashSet();
        Set<Integer> newPresentIds = new IntOpenHashSet();
        Map<Integer, Long> missing = new Int2LongOpenHashMap();

        if (addCurrentStatus) {
            for (DBNation nation : Locutus.imp().getNationDB().getAllNations()) {
                if (nation.getVm_turns() > 0) {
                    missing.put(nation.getNation_id(), Long.MAX_VALUE);
                    dateCreated.put(nation.getNation_id(), nation.getDate());
                }
            }
        }

        long twoDays = TimeUnit.DAYS.toMillis(2);

        Map<Integer, List<Map.Entry<Integer, Integer>>> vmRanges = new Int2ObjectOpenHashMap<>();

        long lastDay = Long.MAX_VALUE;
        for (long day : days) {
            if (!allowDays.test(day)) continue;

            Set<Integer> newFinal = newPresentIds;

            long timestamp = TimeUtil.getTimeFromDay(day);

            parser.withNationFile(day, file -> {
                NationHeader header = file.getHeader();
                try {
                    file.reader().required(header.nation_id, header.date_created).read(new Consumer<NationHeader>() {
                        @Override
                        public void accept(NationHeader header) {
                            int nationId = header.nation_id.get();
                            if (nationIds.test(nationId)) {
                                newFinal.add(nationId);
                                if (!dateCreated.containsKey(nationId)) {
                                    dateCreated.put(nationId, header.date_created.get());
                                }
                            }
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            for (int id : lastPresentIds) {
                if (!newPresentIds.contains(id)) {
                    long created = dateCreated.get(id);
                    if (created >= timestamp - twoDays) continue;
                    missing.put(id, lastDay);
                }
            }

            for (int id : newPresentIds) {
                Long missingDay = missing.remove(id);
                if (missingDay != null) {
                    Map.Entry<Integer, Integer> range = Map.entry((int) day, (int) Math.min(Integer.MAX_VALUE, missingDay));
                    vmRanges.computeIfAbsent(id, k -> new ObjectArrayList<>()).add(range);
                }
            }

            Set<Integer> tmp = lastPresentIds;
            lastPresentIds = newPresentIds;
            newPresentIds = tmp;
            newPresentIds.clear();

            lastDay = day;
        }

        return vmRanges;
    }

    public Map<Long, Map<Integer, Byte>> backCalculateCityCounts() throws IOException, ParseException {
        Map<Long, Map<Integer, Byte>> cityCountsByDay = new Long2ObjectOpenHashMap<>();
        parser.iterateAll(f -> true,
                (h, r) -> r.required(h.nation_id, h.cities),
                null,
                (day, header) -> {
                    int nationId = header.nation_id.get();
                    int cities = header.cities.get();
                    cityCountsByDay.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>()).put(nationId, (byte) cities);
                }, null, f -> System.out.println("backCalculateCityCounts @ day=" + f));
        return cityCountsByDay;
    }

    public Set<Integer> getNationsAtWar(long timestamp, Map<DBWar, Long> getWarEndDates) {
        Set<Integer> nationsAtWar = new HashSet<>();
        for (Map.Entry<DBWar, Long> entry : getWarEndDates.entrySet()) {
            DBWar war = entry.getKey();
            if (war.getDate() <= timestamp && timestamp <= entry.getValue()) {
                nationsAtWar.add(war.getAttacker_id());
                nationsAtWar.add(war.getDefender_id());
            }
        }
        return nationsAtWar;
    }

    public Map<DBWar, Long> getWarEndDates(Map<Integer, DBWar> wars, Collection<AbstractCursor> attacks) {
        Map<DBWar, Long> warEndDates = new Object2LongOpenHashMap<>();
        for (AbstractCursor attack : attacks) {
            switch (attack.getAttack_type()) {
                case VICTORY, A_LOOT, PEACE -> {
                    DBWar war = wars.get(attack.getWar_id());
                    if (war != null) {
                        warEndDates.put(war, attack.getDate());
                    }
                }
            }
        }
        for (DBWar war : wars.values()) {
            long expireDate = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(war.getDate()) + 60);
            warEndDates.putIfAbsent(war, expireDate);
        }
        return warEndDates;
    }

    public void backCalculateBeigeDamage() throws IOException, ParseException {
        parser.load();
        long min = parser.getMinDate();
        List<AbstractCursor> attacks = Locutus.imp().getWarDb().queryAttacks().withWars(f -> f.possibleEndDate() >= min && (f.getStatus() == WarStatus.ATTACKER_VICTORY || f.getStatus() == WarStatus.DEFENDER_VICTORY))
                .withTypes(AttackType.VICTORY).appendAttackFilter(f -> f.getInfra_destroyed_value() <= 0).getList();
        Map<Long, Set<Integer>> nationsByDay = new HashMap<>();
        for (AbstractCursor attack : attacks) {
            long day = TimeUtil.getDay(attack.getDate());
            nationsByDay.computeIfAbsent(day, k -> new HashSet<>()).add(attack.getDefender_id());
        }

        Map<Long, Map<Integer, Map<Integer, Double>>> infraMap = cityInfraByDay((day, nation) -> nationsByDay.getOrDefault(day, Collections.emptySet()).contains(nation));

        attacks.removeIf(AbstractCursor -> {
            long day = TimeUtil.getDay(AbstractCursor.getDate());
            Map<Integer, Map<Integer, Double>> infraDay = infraMap.get(day);
            if (infraDay == null) return true;
            return infraDay.containsKey(AbstractCursor.getDefender_id());
        });

        Map<Integer, AbstractCursor> attacksById = new HashMap<>();
        for (AbstractCursor attack : attacks) {
            attacksById.put(attack.getWar_attack_id(), attack);
        }

        List<Integer> attacksToFetch = attacks.stream().map(f -> f.getWar_attack_id()).collect(Collectors.toList());
        int amtPer = 999;
        for (int i = 0; i < attacksToFetch.size(); i += amtPer) {
            List<Integer> subList = attacksToFetch.subList(i, i + amtPer);
            for (WarAttack attack : Locutus.imp().getV3().fetchAttacks(f -> f.setId(subList), proj -> {
                proj.id();
                proj.loot_info();
            })) {
                int id = attack.getId();
                String note = attack.getLoot_info();
                String end = "% of the infrastructure in each of their cities.";
                String[] split = note.substring(0, note.length() - end.length()).split(" ");
                try {
                    AbstractCursor att = attacksById.get(id);
                    double infraPercent_cached = Double.parseDouble(split[split.length - 1]) / 100d;

                    long day = TimeUtil.getDay(att.getDate());
                    Map<Integer, Double> cityInfra = infraMap.get(day).get(att.getDefender_id());
                    double cost = 0;
                    for (Map.Entry<Integer, Double> entry : cityInfra.entrySet()) {
                        double infraAmt = entry.getValue();
                        cost += PW.City.Infra.calculateInfra(infraAmt * (1 - infraPercent_cached), infraAmt);
                    }

                    if (att instanceof VictoryCursor victory) {
//                        victory.att.setInfra_destroyed_value(cost);
//                        System.out.println("Save $" + att.getInfra_destroyed_value());
//                        Locutus.imp().getWarDb().saveAttacks(List.of(att));
                    }

                } catch (Throwable e) {
                    Logg.text("Error parsing " + id + " " + note + " " + e.getMessage());
                    e.printStackTrace();
                }
            }

        }
    }

    public Map<Long, Map<Integer, Map<Integer, Double>>> cityInfraByDay(BiPredicate<Long, Integer> dateNationFilter) throws IOException, ParseException {
        Map<Long, Map<Integer, Map<Integer, Double>>> result = new Long2ObjectOpenHashMap<>();

        for (Map.Entry<Long, CitiesFile> entry : parser.getCityFilesByDay().entrySet()) {
            long day = entry.getKey();
            CitiesFile cities = entry.getValue();
            CityHeader header = cities.getHeader();
            cities.reader().required(header.nation_id, header.city_id, header.infrastructure).read(new Consumer<CityHeader>() {
                @Override
                public void accept(CityHeader cityHeader) {
                    int nationId = cityHeader.nation_id.get();
                    if (!dateNationFilter.test(day, nationId)) return;
                    int cityId = cityHeader.city_id.get();
                    double infra = cityHeader.infrastructure.get();
                    result.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, f -> new Int2ObjectOpenHashMap<>()).put(cityId, infra);
                }
            });
        }
        return result;
    }

    ///////////////////

    //    public void backCalculateNukesAndMissiles() throws IOException, ParseException {
//        load();
//        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
//            File cityFile = cityFilesByDay.get(entry.getKey());
//            if (cityFile == null) continue;
//
//            Map<Integer, Long> nukeTotal = new Int2LongOpenHashMap();
//            Map<Integer, Long> missileTotal = new Int2LongOpenHashMap();
//            Map<Integer, Integer> numCities = new Int2IntOpenHashMap();
//
//            Map<Integer, Integer> nationAlliances = new Int2IntOpenHashMap();
//
//            // nation, non vm, position >1, infra
//            readAll(entry.getValue(), (headerList, rows) -> {
//                NationHeader header = loadHeader(new NationHeader(), headerList);
//                while (rows.hasNext()) {
//                    CsvRow row = rows.next();
//
//
//                    int alliance = Integer.parseInt(row.getField(header.alliance_id));
//                    if (alliance == 0) continue;
//                    int vm = Integer.parseInt(row.getField(header.vm_turns));
//                    if (vm > 0) continue;
//                    int pos = Integer.parseInt(row.getField(header.alliance_position));
//                    if (pos <= Rank.APPLICANT.id) continue;
//
//                    int nationId = Integer.parseInt(row.getField(header.nation_id));
//                    nationAlliances.put(nationId, alliance);
//
//                    int missile = Integer.parseInt(row.getField(header.missiles));
//                    int nuke = Integer.parseInt(row.getField(header.nukes));
//                    int num = Integer.parseInt(row.getField(header.cities));
//                    missileTotal.put(alliance, missileTotal.getOrDefault(alliance, 0L) + missile);
//                    nukeTotal.put(alliance, nukeTotal.getOrDefault(alliance, 0L) + nuke);
//                    numCities.put(alliance, numCities.getOrDefault(alliance, 0) + num);
//                }
//            });
//
//            long day = entry.getKey();
//            long turnStart = TimeUtil.getTurn(TimeUtil.getTimeFromDay(day));
//            long turnEnd = turnStart + 12;
//
//            List<AllianceMetricValue> values = new ArrayList<>();
//            for (long turn = turnStart; turn < turnEnd; turn++) {
//                for (Map.Entry<Integer, Long> entry2 : missileTotal.entrySet()) {
//                    int aaId = entry2.getKey();
//                    double total = entry2.getValue();
//                    double average = total / numCities.get(aaId);
//
//                    values.add(new AllianceMetricValue(aaId, AllianceMetric.MISSILE, turn, total));
//                    values.add(new AllianceMetricValue(aaId, AllianceMetric.MISSILE_AVG, turn, average));
//                }
//                for (Map.Entry<Integer, Long> entry2 : nukeTotal.entrySet()) {
//                    int aaId = entry2.getKey();
//                    double total = entry2.getValue();
//                    double average = total / numCities.get(aaId);
//
//                    values.add(new AllianceMetricValue(aaId, AllianceMetric.NUKE, turn, total));
//                    values.add(new AllianceMetricValue(aaId, AllianceMetric.NUKE_AVG, turn, average));
//                }
//            }
//            Locutus.imp().getNationDB().executeBatch(values, "INSERT OR IGNORE INTO `ALLIANCE_METRICS`(`alliance_id`, `metric`, `turn`, `value`) VALUES(?, ?, ?, ?)", (ThrowingBiConsumer<AllianceMetricValue, PreparedStatement>) (value, stmt) -> {
//                stmt.setInt(1, value.alliance);
//                stmt.setInt(2, value.metric.ordinal());
//                stmt.setLong(3, value.turn);
//                stmt.setDouble(4, value.value);
//            });
//        }
//    }

//    public Map<Continent, Double> getRadsAt(long currentTurn, List<AbstractCursor> attacks, Map<Long, Map<Integer, Continent>> continentInfo) {
//        double radsBase = MilitaryUnit.NUKE_RADIATION;
//
//        double[] radsByContinent = new double[Continent.values.length];
//        int unknown = 0;
//        int total = 0;
//
//        for (AbstractCursor attack : attacks) {
//            long attTurn = TimeUtil.getTurn(attack.getDate());
//            long expireTurn = attTurn + 100;
//
//            if (attTurn <= currentTurn && expireTurn > currentTurn) {
//                double rads = radsBase * (1 - ((currentTurn - attTurn) / 100d));
//
//                long day = TimeUtil.getDay(attack.getDate());
//
//                Continent continent = continentInfo.getOrDefault(day, Collections.emptyMap()).get(attack.getDefender_id());
//                if (continent == null) {
//                    DBNation nation = DBNation.getById(attack.getDefender_id());
//                    if (nation != null) continent = nation.getContinent();
//                    else {
////                        System.out.println("Could not find continent for " + attack.defender_nation_id);
//                        // pick random continent
//                        continent = Continent.values[ThreadLocalRandom.current().nextInt(Continent.values.length)];
//                        unknown++;
//                    }
//                }
//                total++;
//                radsByContinent[continent.ordinal()] += rads;
//            }
//        }
//        Map<Continent, Double> result = new HashMap<>();
//        for (Continent continent : Continent.values) {
//            result.put(continent, radsByContinent[continent.ordinal()]);
//        }
//        System.out.println("Incorrect: " + unknown + "/" + total);
//        return result;
//    }

//    public void backCalculateRadiation() throws ParseException, IOException {
//        load();
//        System.out.println("Updating attacks");
//        Locutus.imp().getWarDb().updateAttacks(null);
//        System.out.println("Done update attacks");
//
//        long minDate = getMinDate();
//
//        long start2 = System.currentTimeMillis();
//        Map<Long, Map<Integer, Continent>> continentInfo = getContinentByNationByDay();
//        long diff1 = System.currentTimeMillis() - start2;
//
//        long cutoff = minDate - TimeUnit.DAYS.toMillis(20);
//        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(cutoff, f -> true, f -> f.getAttack_type() == AttackType.NUKE && f.getSuccess() != SuccessType.UTTER_FAILURE);
//        attacks.sort(Comparator.comparingLong(AbstractCursor::getDate));
//
//        long currTurn = TimeUtil.getTurn();
//
//        long start = System.currentTimeMillis();
//        for (long turn = TimeUtil.getTurn(minDate); turn <= currTurn; turn++) {
//            Map<Continent, Double> radMap = getRadsAt(turn, attacks, continentInfo);
//            System.out.println("Rads " + StringMan.getString(radMap));
//            for (Map.Entry<Continent, Double> entry : radMap.entrySet()) {
//                Locutus.imp().getNationDB().addRadiationByTurn(entry.getKey(), turn, entry.getValue());
//            }
//        }
//        long diff = System.currentTimeMillis() - start;
//        System.out.println("Diff " + diff + "ms | " + diff1);
//    }

    //    public void printActiveCitiesByDay() throws IOException {
//        List<String> result = new ArrayList<>();
//        Map<Long, Set<Integer>> activeByDay = Locutus.imp().getNationDB().getActivityByDay(0, f -> true);
//        for (Map.Entry<Long, NationsFile> entry : nationFilesByDay.entrySet()) {
//            NationsFile file = entry
//            long day = entry.getKey();
//            Set<Integer> activeToday = new HashSet<>();
//            for (long i = day; i > day - 5; i--) {
//                activeToday.addAll(activeByDay.getOrDefault(i, Collections.emptySet()));
//            }
//            long timestamp = TimeUtil.getTimeFromDay(day);
//
//
//            AtomicLong citiesToday = new AtomicLong(0);
//            readAll(entry.getValue(), (headerList, rows) -> {
//                NationHeader header = loadHeader(new NationHeader(), headerList);
//                while (rows.hasNext()) {
//                    CsvRow row = rows.next();
//                    int nationId = Integer.parseInt(row.getField(header.nation_id));
//                    if (header.vm_turns != 0) {
//                        int vm = Integer.parseInt(row.getField(header.vm_turns));
//                        if (vm > 0) continue;
//                    } else {
//                        if (!activeToday.contains(nationId)) {
//                            continue;
//                        }
//                    }
//                    NationColor color = NationColor.valueOf(row.getField(header.color).toUpperCase(Locale.ROOT));
//                    if (color == NationColor.GRAY || color == NationColor.BEIGE) {
//                        if (!activeToday.contains(nationId)) {
//                            continue;
//                        }
//                    }
//                    int numCities = Integer.parseInt(row.getField(header.cities));
//                    citiesToday.addAndGet(numCities);
//                }
//            });
//            result.add(dateStr + "\t" + citiesToday.get());
//        }
//        System.out.println(StringMan.join(result, "\n"));
//    }

//    public Map<Long, Map<Integer, Continent>> getContinentByNationByDay() throws IOException {
//        Map<Long, Map<Integer, Continent>> continentByNationByDay = new Long2ObjectOpenHashMap<>();
//
//        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
//            long day = entry.getKey();
//            System.out.println("Read " + day);
//            Map<Integer, Continent> continentByNation = continentByNationByDay.computeIfAbsent(day, f -> new Int2ObjectOpenHashMap<>());
//            readAll(entry.getValue(), (headerList, rows) -> {
//                NationHeader header = loadHeader(new NationHeader(), headerList);
//                while (rows.hasNext()) {
//                    CsvRow row = rows.next();
//                    int nationId = Integer.parseInt(row.getField(header.nation_id));
//                    Continent continent = Continent.parseV3(row.getField(header.continent));
//                    continentByNation.put(nationId, continent);
//                }
//            });
//        }
//        return continentByNationByDay;
//    }

//    public void backCalculateInfra() throws IOException, ParseException {
//        load();
//        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
//            File cityFile = cityFilesByDay.get(entry.getKey());
//            if (cityFile == null) continue;
//            if (TimeUtil.getTimeFromDay(entry.getKey()) < 1629861913000L) continue;
//            System.out.println("File " + cityFile);
//
//            Map<Integer, Double> infraTotal = new Int2DoubleOpenHashMap();
//            Map<Integer, Integer> numCities = new Int2IntOpenHashMap();
//
//            Map<Integer, Integer> nationAlliances = new Int2IntOpenHashMap();
//
//            // nation, non vm, position >1, infra
//            readAll(entry.getValue(), (headerList, rows) -> {
//                NationHeader header = loadHeader(new NationHeader(), headerList);
//                while (rows.hasNext()) {
//                    CsvRow row = rows.next();
//
//
//                    int alliance = Integer.parseInt(row.getField(header.alliance_id));
//                    if (alliance == 0) continue;
//                    int vm = Integer.parseInt(row.getField(header.vm_turns));
//                    if (vm > 0) continue;
//                    int pos = Integer.parseInt(row.getField(header.alliance_position));
//                    if (pos <= Rank.APPLICANT.id) continue;
//
//                    int nationId = Integer.parseInt(row.getField(header.nation_id));
//                    nationAlliances.put(nationId, alliance);
//                }
//            });
//
//            readAll(cityFile, (headerList, rows) -> {
//                CityHeader header = loadHeader(new CityHeader(), headerList);
//                while (rows.hasNext()) {
//                    CsvRow row = rows.next();
//
//                    int nationId = Integer.parseInt(row.getField(header.nation_id));
//                    Integer aaId = nationAlliances.get(nationId);
//                    if (aaId == null) continue;
//                    double infra = Double.parseDouble(row.getField(header.infrastructure));
//                    numCities.put(aaId, numCities.getOrDefault(aaId, 0) + 1);
//                    infraTotal.put(aaId, infraTotal.getOrDefault(aaId, 0d) + infra);
//                }
//            });
//
//            long day = entry.getKey();
//            long turnStart = TimeUtil.getTurn(TimeUtil.getTimeFromDay(day));
//            long turnEnd = turnStart + 12;
//
//            List<AllianceMetricValue> values = new ArrayList<>();
//            for (long turn = turnStart; turn < turnEnd; turn++) {
//                for (Map.Entry<Integer, Double> infraEntry : infraTotal.entrySet()) {
//                    int aaId = infraEntry.getKey();
//                    double total = infraEntry.getValue();
//                    double average = total / numCities.get(aaId);
//
//                    DBAlliance aa = DBAlliance.getOrCreate(aaId);
//                    values.add(new AllianceMetricValue(aaId, AllianceMetric.INFRA, turn, total));
//                    values.add(new AllianceMetricValue(aaId, AllianceMetric.INFRA_AVG, turn, average));
//                }
//            }
//            AllianceMetric.saveAll(values, true);
//        }
//    }

//    public void getAverageFarms() throws IOException, ParseException {
//        load();
//        // Average farms per player
//        // Average MI
//        // Average land
//        // Average infra
//        // Average population
//
//        List<String> outHeader = new ArrayList<>(Arrays.asList(
//            "date",
//            "farm_per_nation",
//            "avg_mi",
//            "land_per_nation",
//            "infra_per_nation",
//            "avg_cities",
//            "pop_per_nation",
//                "farm_per_city",
//                "land_per_city",
//                "infra_per_city"
//        ));
//
//        Map<Long, Set<Integer>> activeNationsByDay = new LinkedHashMap<>();
//        Map<Long, Map<Integer, Double>> valuesByDay = new LinkedHashMap<>();
//
//        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
//            File cityFile = cityFilesByDay.get(entry.getKey());
//            if (cityFile == null) continue;
//
//            System.out.println("Reading nation file " + entry.getValue());
//
//            readAll(entry.getValue(), (headerList, rows) -> {
//                NationHeader header = loadHeader(new NationHeader(), headerList);
//                Set<Integer> active = new HashSet<>();
//                long totalPop = 0;
//                long totalCities = 0;
//                long numMI = 0;
//
//                Map<Integer, Double> data = valuesByDay.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
//
//                while (rows.hasNext()) {
//                    CsvRow row = rows.next();
//
//                    // is vm
//                    int vmTurns = Integer.parseInt(row.getField(header.vm_turns));
//                    if (vmTurns > 0) continue;
//                    NationColor color = NationColor.valueOf(row.getField(header.color).toUpperCase(Locale.ROOT));
//                    // gray or beige
//                    if (color == NationColor.GRAY || color == NationColor.BEIGE) continue;
//                    int nationId = Integer.parseInt(row.getField(header.nation_id));
//
//                    boolean hasMassIrrigation = Integer.parseInt(row.getField(header.mass_irrigation_np)) > 0;
//                    int numCities = Integer.parseInt(row.getField(header.cities));
//                    int pop = Integer.parseInt(row.getField(header.population));
//                    active.add(nationId);
//                    totalPop += pop;
//                    totalCities += numCities;
//                    if (hasMassIrrigation) numMI++;
//                }
//
//                int numNations = active.size();
//                // avg mi
//                data.put(2, numMI / (double) numNations);
//                // avg cities
//                data.put(5, totalCities / (double) numNations);
//                // avg population
//                data.put(6, totalPop / (double) numNations);
//
//                activeNationsByDay.put(entry.getKey(), active);
//            });
//        }
//
//        List<List<String>> outRows = new ArrayList<>();
//        outRows.add(outHeader);
//
//        for (Map.Entry<Long, File> entry : cityFilesByDay.entrySet()) {
//            System.out.println("Reading city file " + entry.getValue());
//            readAll(entry.getValue(), (headerList, rows) -> {
//                CityHeader header = loadHeader(new CityHeader(), headerList);
//
//                Set<Integer> nations = activeNationsByDay.get(entry.getKey());
//                if (nations == null) return;
//
//
//
//                Map<Integer, Double> data = valuesByDay.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
//
//                long numFarms = 0;
//                long totalLand = 0;
//                long totalInfra = 0;
//                long totalCities = 0;
//
//                while (rows.hasNext()) {
//                    CsvRow row = rows.next();
//
//                    int nationId = Integer.parseInt(row.getField(header.nation_id));
//                    if (!nations.contains(nationId)) continue;
//                    int farms = Integer.parseInt(row.getField(header.farms));
//                    double land = Double.parseDouble(row.getField(header.land));
//                    double infra = Double.parseDouble(row.getField(header.infrastructure));
//                    numFarms += farms;
//                    totalLand += land;
//                    totalInfra += infra;
//                    totalCities++;
//
//                }
//
//                int numNations = nations.size();
//                long timeStamp = TimeUtil.getTimeFromDay(entry.getKey());
//                // date string from unix
//                ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeStamp), ZoneOffset.UTC);;
//                String timeStr = TimeUtil.YYYY_MM_DD.format(time);
//                // avg farms
//                data.put(1, numFarms / (double) numNations);
//                // avg land
//                data.put(3, totalLand / (double) numNations);
//                // avg infra
//                data.put(4, totalInfra / (double) numNations);
//
//                data.put(7, numFarms / (double) totalCities);
//                data.put(8, totalLand / (double) totalCities);
//                data.put(9, totalInfra / (double) totalCities);
//
//
//                List<String> outRow = new ArrayList<>();
//                outRow.add(timeStr);
//                outRow.add(MathMan.format(data.get(1)));
//                outRow.add(MathMan.format(data.get(2)));
//                outRow.add(MathMan.format(data.get(3)));
//                outRow.add(MathMan.format(data.get(4)));
//                outRow.add(MathMan.format(data.get(5)));
//                outRow.add(MathMan.format(data.get(6)));
//                outRow.add(MathMan.format(data.get(7)));
//                outRow.add(MathMan.format(data.get(8)));
//                outRow.add(MathMan.format(data.get(9)));
//
//                outRows.add(outRow);
//            });
//        }
//
//        System.out.println("Writing farms.csv");
//        // write csv to farms.csv
//        CsvWriter writer = CsvWriter.builder().build(Path.of("farms.csv"));
//        for (List<String> outRow : outRows) {
//            writer.writeRow(outRow);
//        }
//        System.out.println("Wrote farms.csv");
//        writer.close();
//    }
//    public LootEstimateTracker exportToTracker() throws IOException, ParseException {
//        load();
//
//        long minDate = getMinDate();
//
//        Map<Integer, Map.Entry<Long, double[]>> legacyLoot = Locutus.imp().getWarDb().getNationLootFromAttacksLegacy(0);
//
//        Map<Integer, LootEntry> loot = Locutus.imp().getNationDB().getNationLootMap();
//
//        Map<Integer, LootEntry> minLootDate = new Int2ObjectOpenHashMap(loot);
//        for (Map.Entry<Integer, Map.Entry<Long, double[]>> entry : legacyLoot.entrySet()) {
//            int nationId = entry.getKey();
//            LootEntry existing = minLootDate.get(nationId);
//            if (existing == null || existing.getDate() > entry.getValue().getKey()) {
//                LootEntry lootInfo = new LootEntry(nationId, entry.getValue().getValue(), entry.getValue().getKey(), NationLootType.WAR_LOSS);
//                minLootDate.put(entry.getKey(), lootInfo);
//            }
//        }
//
//        Locutus.imp().runEventsAsync(Locutus.imp().getWarDb()::updateAllWars);
//        Locutus.imp().runEventsAsync(Locutus.imp().getWarDb()::updateAttacks);
//
//        Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWarsSince(minDate - TimeUnit.DAYS.toMillis(5));
//        Collection<AbstractCursor> attacks = Locutus.imp().getWarDb().queryAttacks().withWars(wars).withTypes(AttackType.PEACE, AttackType.VICTORY).getList();
//
//        Map<DBWar, Long> warEndDates = getWarEndDates(wars, attacks);
//
//        long twoDays = TimeUnit.DAYS.toMillis(2);
//
//        //                throw new IllegalArgumentException("Call to get nation not allowed");
//        LootEstimateTracker tracker = new LootEstimateTracker(true, 0L, false, f -> {
//        }, (nationId, taxIds, doubles) -> System.out.println("Ignore saving tax rate"),
//                DBNation::getById);
//
//        for (Map.Entry<Integer, LootEntry> entry : minLootDate.entrySet()) {
//            int nationId = entry.getKey();
//            LootEntry minEntry = entry.getValue();
//            double[] rss = minEntry.getTotal_rss();
//            LootEstimateTracker.LootEstimate estimate = new LootEstimateTracker.LootEstimate(rss, minEntry.getDate());
//            tracker.addLootEstimate(nationId, estimate);
//        }
//
//        // add daily logins
//        {
//            Map<Long, Set<Integer>> activity = Locutus.imp().getNationDB().getActivityByDay(minDate, id -> DBNation.getById(id) != null);
//            Map<Integer, Integer> sequentialLoginsByNationId = new Int2IntOpenHashMap();
//            // for loop each day
//            for (long day = TimeUtil.getDay(minDate); day < TimeUtil.getDay(); day++) {
//                long timestamp = TimeUtil.getTimeFromDay(day);
//                Set<Integer> activeToday = activity.getOrDefault(day, Collections.emptySet());
//                // remove all nations that are not active today from sequentialLoginsByNationId
//                sequentialLoginsByNationId.entrySet().removeIf(entry -> !activeToday.contains(entry.getKey()));
//                for (int nationId : activeToday) {
//                    DBNation nation = DBNation.getById(nationId);
//                    int total = sequentialLoginsByNationId.getOrDefault(nationId, 0) + 1;
//
//                    int age = (int) TimeUnit.MILLISECONDS.toDays(nation.getDate() - timestamp);
//                    if (age > 0) {
//                        tracker.loginBonus(nationId, age, total, timestamp);
//                    }
//                    sequentialLoginsByNationId.put(nationId, total);
//                }
//            }
//
//        }
//
//        // add attacks
//        for (AbstractCursor attack : attacks) {
//            tracker.onAttack(attack, false);
//        }
//
//        // add bank recs
//        Locutus.imp().runEventsAsync(f -> Locutus.imp().getBankDB().updateBankRecs(false, f));
//        for (Transaction2 transaction : Locutus.imp().getBankDB().getTransactions(minDate, false)) {
//            tracker.bankEvent(new TransactionEvent(transaction));
//        }
//
//        // add trades
//        for (DBTrade trade : Locutus.imp().getTradeManager().getTradeDb().getTrades(minDate)) {
//            tracker.onTradeCreate(new TradeCreateEvent(trade));
//        }
//
//        // add baseball
//        Locutus.imp().runEventsAsync(Locutus.imp().getBaseballDB()::updateGames);
//        for (BBGame game : Locutus.imp().getBaseballDB().getBaseballGames(f -> {
//        })) {
//            tracker.onBaseball(new BaseballGameEvent(game));
//        }
//
//        Map<Long, Map<Continent, Double>> radsByDay = Locutus.imp().getNationDB().getRadiationByTurns();
//
//        long previousTurn = 0L;
//        Map<Integer, DBNation> previousNationsMap = null;
//        Map<Integer, Map<Integer, DBCity>> previousCitiesMap = null;
//
//        for (Map.Entry<Long, File> entry : nationFilesByDay.entrySet()) {
//            long day = entry.getKey();
//            File nationFile = entry.getValue();
//            File cityFile = cityFilesByDay.get(day);
//            if (cityFile == null) continue;
//
//            long currentTimestamp = TimeUtil.getTimeFromDay(day);
//            long currentTurn = TimeUtil.getTurn(currentTimestamp);
//            System.out.println("Loading " + nationFile);
//
//            Map<Integer, DBNation> dayNations = parseNationFile(nationFile, day, id -> {
//                LootEntry minEntry = minLootDate.get(id);
//                return minEntry == null || minEntry.getDate() - twoDays <= currentTimestamp;
//            }, f -> true, true, false);
//            Map<Integer, Map<Integer, DBCity>> dayCities = parseCitiesFile(cityFile, dayNations::containsKey);
//            Map<Integer, Map<Integer, JavaCity>> javaCities = new Int2ObjectOpenHashMap<>();
//            for (Map.Entry<Integer, Map<Integer, DBCity>> nationCityEntry : dayCities.entrySet()) {
//                DBNation nation = dayNations.get(nationCityEntry.getKey());
//                if (nation == null) continue;
//                for (Map.Entry<Integer, DBCity> cityEntry : nationCityEntry.getValue().entrySet()) {
//                    javaCities.computeIfAbsent(nation.getId(), k -> new Int2ObjectOpenHashMap<>()).put(cityEntry.getKey(), cityEntry.getValue().toJavaCity(nation));
//                }
//            }
//
//            if (previousTurn > 0) {
//
//                for (long turn = previousTurn; turn < currentTurn; turn++) {
//                    long turnTimestamp = TimeUtil.getTimeFromTurn(turn);
//
//                    Set<Integer> nationsAtWar = getNationsAtWar(turnTimestamp, warEndDates);
//                    Map<Continent, Double> radsByContinent = radsByDay.get(turnTimestamp);
//                    double globalRads = radsByContinent.values().stream().mapToDouble(Double::doubleValue).sum() / 5d;
//                    long gameTime = TimeUtil.getOrbisDate(TimeUtil.getTimeFromTurn(turn));
//
//                    for (Map.Entry<Integer, DBNation> nationEntry : dayNations.entrySet()) {
//                        DBNation nation = nationEntry.getValue();
//                        int vmTurns = nation.getVm_turns();
//                        if (previousTurn + vmTurns > turn) continue;
//                        Map<Integer, JavaCity> nationCities = javaCities.get(entry.getKey());
//                        if (nationCities == null) continue;
//                        nation.setCities(nationCities.size());
//
//
//                        boolean atWar = nationsAtWar.contains(nation.getId());
//                        double rads = radsByContinent.get(nation.getContinent()) + globalRads;
//
//                        double[] revenue = PW.getRevenue(null, 1, gameTime, nation, nationCities.values(), true, true, true, false, false, rads, atWar, 0d);
//                        if (nation.isTaxable()) {
//                            tracker.getOrCreate(nation.getId()).addUnknownRevenue(tracker, nation.getId(), -1, turnTimestamp, revenue);
////                            tracker.add(nation.getId(), turnTimestamp, EMPTY, revenue);
//                        } else {
//                            tracker.addRevenue(nation.getId(), turnTimestamp, revenue, -1);
//                        }
//                    }
//                }
//
//                for (Map.Entry<Integer, DBNation> nationEntry : dayNations.entrySet()) {
//                    int nationId = nationEntry.getKey();
//                    DBNation currentNation = nationEntry.getValue();
//                    DBNation previousNation = previousNationsMap.get(nationId);
//
//                    Map<Integer, DBCity> currentCities = dayCities.get(nationId);
//                    Map<Integer, DBCity> previousCities = previousCitiesMap.get(nationId);
//
//                    if (currentNation != null && previousNation != null) {
//
//                        // projects
//                        for (Project project : currentNation.getProjects()) {
//                            if (!previousNation.hasProject(project)) {
//                                double[] cost = currentNation.projectCost(project);
//                                tracker.add(currentNation.getId(), currentTimestamp, cost);
//                            }
//                        }
//
//                        // units
//                        for (MilitaryUnit unit : MilitaryUnit.values) {
//                            int amt = currentNation.getUnits(unit) - previousNation.getUnits(unit);
//                            if (amt > 0) {
//                                tracker.add(nationId, currentTimestamp, unit.getCost(amt));
//                            }
//                        }
//
//                        // cities
//                        if (currentCities != null && previousCities != null) {
//                            for (Map.Entry<Integer, DBCity> cityEntry : currentCities.entrySet()) {
//                                DBCity previousCity = previousCities.get(cityEntry.getKey());
//                                DBCity city = cityEntry.getValue();
//                                double infra = previousCity == null ? 10 : previousCity.getInfra();
//                                double land = previousCity == null ? 250 : previousCity.getLand();
//                                double[] total = ResourceType.getBuffer();
//                                if (previousCity == null) {
//                                    for (Building building : Buildings.values()) {
//                                        int amt = city.get(building);
//                                        if (amt > 0) total = building.cost(total, amt);
//                                    }
//                                } else {
//                                    for (Building building : Buildings.values()) {
//                                        int amt = city.get(building) - previousCity.get(building);
//                                        if (amt > 0) total = building.cost(total, amt);
//                                    }
//                                }
//                                tracker.add(nationId, currentTimestamp, total);
//
//                                if (city.getInfra() > infra + 0.01) {
//                                    tracker.add(nationId, currentTimestamp, currentNation.infraCost(infra, city.getInfra()));
//                                }
//                                if (city.getLand() > land + 0.01) {
//                                    tracker.add(nationId, currentTimestamp, currentNation.landCost(infra, city.getInfra()));
//                                }
//                            }
//
//                            // cities
//                            if (currentCities.size() > previousCities.size()) {
//                                double cost = PW.cityCost(currentNation, previousCities.size(), currentCities.size());
//                                tracker.add(nationId, currentTimestamp, cost);
//                            }
//                        }
//                    }
//
//                }
//            }
//
//
//            // when tax rate is resolved via estimate, don't use the estimate
//
//            previousNationsMap = dayNations;
//            previousCitiesMap = dayCities;
//            previousTurn = currentTurn;
//        }
//        // diff by taxrate
//
//        // loot_estimates int nation_id, double[] min, double[] max, double[] offset, long lastTurnRevenue, int tax_id
//        // loot_estimate_by_tax_id, int nation_id, int tax_id, double[] resources,
//        // - when absolute, delete all tax loot estimates except current running
//        // tax_estimage: int tax_id, int minMoney, int maxMoney, int minRss, int maxRss
//
//
//        // have a way to include guesses without throwing away the current margins
//
//        // iterate over loot estimates and resolve tax rates
//
//        // resolve the tracker queues
//        // estimate tax rate from that
//        return tracker;
//    }

}
