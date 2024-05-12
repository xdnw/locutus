package link.locutus.discord.apiv3.csv;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.locutus.discord.apiv3.csv.file.CitiesFile;
import link.locutus.discord.apiv3.csv.file.DataFile;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DBNationSnapshot;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.TriConsumer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DataDumpParser {

    private Map<Long, NationsFile> nationFilesByDay;
    private Map<Long, CitiesFile> cityFilesByDay;
    private long lastUpdatedNations = 0;
    private long lastUpdatedCities = 0;
    private final File cityDir, nationDir;
    private final Dictionary nationDict, cityDict;

    public DataDumpParser() {
        this.cityDir = new File(Settings.INSTANCE.DATABASE.DATA_DUMP.CITIES);
        this.nationDir = new File(Settings.INSTANCE.DATABASE.DATA_DUMP.NATIONS);
        this.nationDict = new Dictionary(nationDir);
        this.cityDict = new Dictionary(cityDir);
    }

    public Map<Integer, DBNation> getNations(long day, boolean loadCities, boolean includeVM, Predicate<Integer> allowedNations, Predicate<Integer> allowedAlliances, Predicate<DBNation> nationFilter) throws IOException, ParseException {
        load();
        NationsFile nationsFile = getNearestNationFile(day);
        CitiesFile citiesFile = getNearestCityFile(day);

        Map<Integer, DBNationSnapshot> nationsById = nationsFile.readNations(allowedNations, allowedAlliances, includeVM, true, true);
        if (loadCities) {
            Map<Integer, Map<Integer, DBCity>> dayCities = citiesFile.readCities(nationsById::containsKey, false);
            Iterator<Map.Entry<Integer, DBNationSnapshot>> iter = nationsById.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Integer, DBNationSnapshot> entry = iter.next();
                DBNationSnapshot nation = entry.getValue();
                if (nationFilter != null && !nationFilter.test(nation)) {
                    iter.remove();
                    continue;
                }
                Map<Integer, DBCity> cityMap = dayCities.get(entry.getKey());
                if (cityMap == null) continue;
                nation.setCityMap(cityMap);
            }
        } else if (nationFilter != null) {
            nationsById.entrySet().removeIf(entry -> !nationFilter.test(entry.getValue()));
        }
        return (Map) nationsById;
    }

    public DataDumpParser load() throws IOException, ParseException {
        downloadNationFilesByDay();
        downloadCityFilesByDay();
        return this;
    }

    public List<Long> getDays(boolean includeNations, boolean includeCities) {
        Set<Long> days = new LongOpenHashSet();
        if (includeNations && nationFilesByDay != null) {
            synchronized (nationFilesByDay) {
                days.addAll(nationFilesByDay.keySet());
            }
        }
        if (includeCities && cityFilesByDay != null) {
            synchronized (cityFilesByDay) {
                days.addAll(cityFilesByDay.keySet());
            }
        }
        return days.stream().sorted().toList();
    }

    public long getMinDay() {
        long min = Long.MAX_VALUE;
        if (nationFilesByDay != null) {
            min = Math.min(min, nationFilesByDay.keySet().stream().min(Long::compareTo).orElse(Long.MAX_VALUE));
        }
        if (cityFilesByDay != null) {
            min = Math.min(min, cityFilesByDay.keySet().stream().min(Long::compareTo).orElse(Long.MAX_VALUE));
        }
        return min;
    }

    public long getMinDate() {
        return TimeUtil.getTimeFromDay(getMinDay());
    }

    public void iterateFiles(TriConsumer<Long, NationsFile, CitiesFile> onEach) {
        if (nationFilesByDay != null) {
            synchronized (nationFilesByDay) {
                for (long day : getDays(true, true)) {
                    NationsFile nationFile = nationFilesByDay.get(day);
                    CitiesFile cityFile = cityFilesByDay.get(day);
                    onEach.accept(day, nationFile, cityFile);
                }
            }
        }
    }

    public void iterateAll(Predicate<Long> acceptDay,
                            BiConsumer<NationHeader, DataFile<DBNation, NationHeader>.Builder> nationColumns,
                            BiConsumer<CityHeader, DataFile<DBCity, CityHeader>.Builder> cityColumns,
                            BiConsumer<Long, NationHeader> nationRows,
                            BiConsumer<Long, CityHeader> cityRows,
                            Consumer<Long> onEach) throws IOException, ParseException {
        load();
        iterateFiles((day, nationFile, cityFile) -> {
            try {
                if (!acceptDay.test(day)) return;
                if (cityRows != null && cityFile == null) return;
                if (nationRows != null && nationFile == null) return;
                if (nationRows != null) {
                    DataFile<DBNation, NationHeader>.Builder reader = nationFile.reader();
                    if (nationColumns == null) {
                        reader.all(false);
                    } else {
                        nationColumns.accept(nationFile.getHeader(), reader);
                    }
                    reader.read(nationHeader -> nationRows.accept(day, nationHeader));
                }
                if (cityRows != null) {
                    DataFile<DBCity, CityHeader>.Builder reader = cityFile.reader();
                    if (cityColumns == null) {
                        reader.all(false);
                    } else {
                        cityColumns.accept(cityFile.getHeader(), reader);
                    }
                    reader.read(cityHeader -> cityRows.accept(day, cityHeader));
                }
                if (onEach != null) {
                    onEach.accept(day);
                }
            } catch (IOException e) {
                System.out.println("Error reading file " + day + " | " + (cityFile == null ? "no city file" : cityFile.getFilePart()));
                throw new RuntimeException(e);
            } catch (Throwable e) {
                System.out.println("Error reading file (2) " + day + " | " + (cityFile == null ? "no city file" : cityFile.getFilePart()));
                throw e;
            }
        });
    }

    public DataUtil getUtil() {
        return new DataUtil(this);
    }

    public Map<Long, CitiesFile> getCityFilesByDay() {
        if (cityFilesByDay == null) return Collections.emptyMap();
        synchronized (cityFilesByDay) {
            return new Long2ObjectLinkedOpenHashMap<>(cityFilesByDay);
        }
    }

    public Map<Long, NationsFile> getNationFilesByDay() {
        if (nationFilesByDay == null) return Collections.emptyMap();
        synchronized (nationFilesByDay) {
            return new Long2ObjectLinkedOpenHashMap<>(nationFilesByDay);
        }
    }

    // private functions

    private Map<Long, File> load(String url, File savePath) throws IOException, ParseException {
        Map<Long, File> filesByDate = new Long2ObjectLinkedOpenHashMap<>();
        Document dom = Jsoup.parse(FileUtil.readStringFromURL(PagePriority.DATA_DUMP, url));
        for (Element a : dom.select("a")) {
            String subUrl = a.attr("href");
            if (subUrl != null && subUrl.contains(".zip")) {
                synchronized (this) {
                    String fileUrl = url + subUrl;
                    File saveAs = new File(savePath, subUrl.replace(".zip", ""));
                    filesByDate.put(DataFile.parseDateFromFile(saveAs.getName()), saveAs);
                    if (saveAs.exists()) continue;
                    download(fileUrl, saveAs);
                }
            }
        }
        return filesByDate;
    }

    private void download(String fileUrl, File savePath) throws IOException {
        File parent = savePath.getParentFile();
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Could not create directory " + parent);
            }
        }
        byte[] bytes = FileUtil.readBytesFromUrl(PagePriority.DATA_DUMP, fileUrl);
        assert bytes != null;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = in.getNextEntry();
            byte[] data = in.readNBytes((int) Objects.requireNonNull(entry).getSize());
            Files.write(savePath.toPath(), data);
        }
    }

    private <T, H extends DataHeader<T>, F extends DataFile<T, H>> F getNearest(Map<Long, F> map, long day) {
        F nearest = null;
        long nearestDiff = Long.MAX_VALUE;
        for (Map.Entry<Long, F> entry : map.entrySet()) {
            long diff = Math.abs(entry.getKey() - day);
            if (diff < nearestDiff) {
                nearestDiff = diff;
                nearest = entry.getValue();
            }
        }
        return nearest;
    }

    private NationsFile getNearestNationFile(long day) {
        return getNearest(nationFilesByDay, day);
    }

    private CitiesFile getNearestCityFile(long day) {
        return getNearest(cityFilesByDay, day);
    }

    private Map<Long, NationsFile> downloadNationFilesByDay() throws IOException, ParseException {
        if (nationFilesByDay == null) {
            if (!nationDir.exists()) {
                nationDir.mkdirs();
            }
            nationFilesByDay = new Long2ObjectLinkedOpenHashMap<>();

            String prefix = "nations";
            for (File file : nationDir.listFiles()) {
                if (!DataFile.isValidName(file, prefix)) continue;
                NationsFile natFile = new NationsFile(file, nationDict);
                long day = natFile.getDay();
                nationFilesByDay.putIfAbsent(day, natFile);
            }
            lastUpdatedNations = nationFilesByDay.keySet().stream().max(Long::compareTo).orElse(0L);
        }
        long currentDay = TimeUtil.getDay();
        if (currentDay > lastUpdatedNations) {
            Map<Long, File> downloaded = load("https://politicsandwar.com/data/nations/", new File(Settings.INSTANCE.DATABASE.DATA_DUMP.NATIONS));
            downloaded.forEach((time, file) -> {
                long day = TimeUtil.getDay(time);
                NationsFile natFile = new NationsFile(file, nationDict);
                nationFilesByDay.putIfAbsent(day, natFile);
            });
            lastUpdatedNations = currentDay;
        }
        return nationFilesByDay;
    }

    private Map<Long, CitiesFile> downloadCityFilesByDay() throws IOException, ParseException {
        if (cityFilesByDay == null) {
            if (!cityDir.exists()) {
                cityDir.mkdirs();
            }
            cityFilesByDay = new Long2ObjectLinkedOpenHashMap<>();

            String prefix = "cities";
            for (File file : cityDir.listFiles()) {
                if (!DataFile.isValidName(file, prefix)) continue;
                CitiesFile cityFile = new CitiesFile(file, cityDict);
                long day = cityFile.getDay();
                cityFilesByDay.putIfAbsent(day, cityFile);
            }
            lastUpdatedCities = cityFilesByDay.keySet().stream().max(Long::compareTo).orElse(0L);
        }
        long currentDay = TimeUtil.getDay();
        if (currentDay > lastUpdatedCities) {
            Map<Long, File> downloaded = load("https://politicsandwar.com/data/cities/", new File(Settings.INSTANCE.DATABASE.DATA_DUMP.CITIES));
            downloaded.forEach((time, file) -> {
                long day = TimeUtil.getDay(time);
                CitiesFile cityFile = new CitiesFile(file, cityDict);
                cityFilesByDay.putIfAbsent(day, cityFile);
            });
            lastUpdatedCities = currentDay;
        }

        return cityFilesByDay;
    }

    public Dictionary getDict(boolean isNations) {
        return isNations ? nationDict : cityDict;
    }
}
