package link.locutus.discord.apiv3.csv;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv3.csv.file.*;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import link.locutus.discord.apiv3.csv.header.*;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
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
import java.util.function.Function;
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
    private final DataUtil util;

    public DataDumpParser() {
        this.cityDir = new File(Settings.INSTANCE.DATABASE.DATA_DUMP.CITIES);
        this.nationDir = new File(Settings.INSTANCE.DATABASE.DATA_DUMP.NATIONS);
        this.nationDict = new Dictionary(nationDir);
        this.cityDict = new Dictionary(cityDir);
        this.util = new DataUtil(this);
    }

    public File getNationDir() {
        return nationDir;
    }

    public File getCityDir() {
        return cityDir;
    }

    public NationsFileSnapshot getSnapshotDelegate(long day, boolean loadCities, boolean loadVm)
            throws IOException, ParseException {
        NationsFileSnapshot snapshot = new NationsFileSnapshot(this, day, loadCities);
        if (loadVm)
            snapshot.loadVm(null);
        return snapshot;
    }

    // old method, for reference only:
    // (long day, boolean loadCities, boolean includeVM, Predicate<Integer>
    // allowedNations, Predicate<Integer> allowedAlliances, @Nullable
    // Predicate<DBNation> nationFilter) throws IOException, ParseException {

    // new method
    public Map<Integer, DBNationSnapshot> getNations(long day, boolean loadCities) throws IOException, ParseException {
        NationsFile nationsFile = getNearestNationFile(day);
        Function<Integer, Map<Integer, DBCity>> fetchCities = null;
        if (loadCities) {
            CitiesFile citiesFile = getNearestCityFile(day);
            if (citiesFile != null) {
                fetchCities = citiesFile.loadCities();
            }
        }
        Map<Integer, DBNationSnapshot> nations = nationsFile.readNations(fetchCities);
        return nations;
    }

    public DataDumpParser load() throws IOException, ParseException {
        downloadNationFilesByDay();
        downloadCityFilesByDay();
        return this;
    }

    public DataDumpParser loadDict() {
        cityDict.load();
        nationDict.load();
        return this;
    }

    public List<Long> getDays(boolean requireNations, boolean requireCities) {
        Set<Long> days = new LongOpenHashSet();
        if (requireNations) {
            if (requireCities) {
                if (nationFilesByDay != null && cityFilesByDay != null) {
                    synchronized (nationFilesByDay) {
                        days.addAll(nationFilesByDay.keySet());
                    }
                    synchronized (cityFilesByDay) {
                        days.removeIf(day -> !cityFilesByDay.containsKey(day));
                    }
                }
            } else {
                if (nationFilesByDay != null) {
                    synchronized (nationFilesByDay) {
                        days.addAll(nationFilesByDay.keySet());
                    }
                }
            }
        } else if (requireCities) {
            if (cityFilesByDay != null) {
                synchronized (cityFilesByDay) {
                    days.addAll(cityFilesByDay.keySet());
                }
            }
        }
        return days.stream().sorted().toList();
    }

    public long getMinDay() {
        long min = Long.MAX_VALUE;
        if (nationFilesByDay != null) {
            synchronized (nationFilesByDay) {
                min = Math.min(min, nationFilesByDay.keySet().stream().min(Long::compareTo).orElse(Long.MAX_VALUE));
            }
        }
        if (cityFilesByDay != null) {
            synchronized (cityFilesByDay) {
                min = Math.min(min, cityFilesByDay.keySet().stream().min(Long::compareTo).orElse(Long.MAX_VALUE));
            }
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

    public void withNationFile(long day, Consumer<NationsFile> withFile) throws IOException, ParseException {
        downloadNationFilesByDay();
        if (nationFilesByDay == null)
            return;
        synchronized (nationFilesByDay) {
            NationsFile nationFile = nationFilesByDay.get(day);
            if (nationFile != null) {
                withFile.accept(nationFile);
            }
        }
    }

    public void iterateAll(Predicate<Long> acceptDay,
            BiConsumer<NationHeader, DataFile<DBNation, NationHeader, NationHeaderReader>.Builder> nationColumns,
            BiConsumer<CityHeader, DataFile<DBCity, CityHeader, CityHeaderReader>.Builder> cityColumns,
            BiConsumer<Long, NationHeaderReader> nationRows,
            BiConsumer<Long, CityHeaderReader> cityRows,
            Consumer<Long> onEach) throws IOException, ParseException {
        load();
        iterateFiles((day, nationFile, cityFile) -> {
            try {
                if (!acceptDay.test(day))
                    return;
                if (cityRows != null && cityFile == null)
                    return;
                if (nationRows != null && nationFile == null)
                    return;
                if (nationRows != null) {
                    DataFile<DBNation, NationHeader, NationHeaderReader>.Builder reader = nationFile.reader();
                    if (nationColumns == null) {
                        reader.all(false);
                    } else {
                        nationColumns.accept(reader.getHeader(), reader);
                    }
                    reader.read(nationHeader -> nationRows.accept(day, nationHeader));
                }
                if (cityRows != null) {
                    DataFile<DBCity, CityHeader, CityHeaderReader>.Builder reader = cityFile.reader();
                    if (cityColumns == null) {
                        reader.all(false);
                    } else {
                        cityColumns.accept(reader.getHeader(), reader);
                    }
                    reader.read(cityHeader -> cityRows.accept(day, cityHeader));
                }
                if (onEach != null) {
                    onEach.accept(day);
                }
            } catch (IOException e) {
                Logg.text("Error reading file " + day + " | "
                        + (cityFile == null ? "no city file" : cityFile.getFilePart()));
                throw new RuntimeException(e);
            } catch (Throwable e) {
                Logg.text("Error reading file (2) " + day + " | "
                        + (cityFile == null ? "no city file" : cityFile.getFilePart()));
                throw e;
            }
        });
    }

    public DataUtil getUtil() {
        return util;
    }

    public Map<Long, CitiesFile> getCityFilesByDay() {
        if (cityFilesByDay == null)
            return Collections.emptyMap();
        synchronized (cityFilesByDay) {
            return new Long2ObjectLinkedOpenHashMap<>(cityFilesByDay);
        }
    }

    public Map<Long, NationsFile> getNationFilesByDay() {
        if (nationFilesByDay == null)
            return Collections.emptyMap();
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
                    if (saveAs.exists())
                        continue;
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

    private <T, H extends DataHeader<T>, U extends DataReader<H>, F extends DataFile<T, H, U>> F getNearest(
            Map<Long, F> map, long day) {
        if (map == null)
            return null;
        F exact;
        synchronized (map) {
            exact = map.get(day);
        }
        if (exact != null)
            return exact;
        F nearest = null;
        long nearestDiff = Long.MAX_VALUE;
        synchronized (map) {
            for (Map.Entry<Long, F> entry : map.entrySet()) {
                long diff = Math.abs(entry.getKey() - day);
                if (diff < nearestDiff) {
                    nearestDiff = diff;
                    nearest = entry.getValue();
                }
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
            synchronized (this) {
                if (nationFilesByDay == null) {
                    if (!nationDir.exists()) {
                        nationDir.mkdirs();
                    }
                    nationFilesByDay = new Long2ObjectLinkedOpenHashMap<>();

                    String prefix = "nations";
                    for (File file : nationDir.listFiles()) {
                        if (!DataFile.isValidName(file, prefix))
                            continue;
                        NationsFile natFile = new NationsFile(file, nationDict);
                        long day = natFile.getDay();
                        nationFilesByDay.putIfAbsent(day, natFile);
                    }
                    lastUpdatedNations = nationFilesByDay.keySet().stream().max(Long::compareTo).orElse(0L);
                }
            }
        }
        long currentDay = TimeUtil.getDay();
        if (currentDay > lastUpdatedNations) {
            synchronized (this) {
                if (currentDay > lastUpdatedNations) {
                    Map<Long, File> downloaded = load(Settings.PNW_URL() + "/data/nations/",
                            new File(Settings.INSTANCE.DATABASE.DATA_DUMP.NATIONS));
                    downloaded.forEach((time, file) -> {
                        long day = TimeUtil.getDay(time);
                        NationsFile natFile = new NationsFile(file, nationDict);
                        nationFilesByDay.putIfAbsent(day, natFile);
                    });
                    lastUpdatedNations = currentDay;
                }
            }
        }
        return nationFilesByDay;
    }

    private Map<Long, CitiesFile> downloadCityFilesByDay() throws IOException, ParseException {
        if (cityFilesByDay == null) {
            synchronized (this) {
                if (cityFilesByDay == null) {
                    if (!cityDir.exists()) {
                        cityDir.mkdirs();
                    }
                    cityFilesByDay = new Long2ObjectLinkedOpenHashMap<>();

                    String prefix = "cities";
                    for (File file : cityDir.listFiles()) {
                        if (!DataFile.isValidName(file, prefix))
                            continue;
                        CitiesFile cityFile = new CitiesFile(file, cityDict);
                        long day = cityFile.getDay();
                        cityFilesByDay.putIfAbsent(day, cityFile);
                    }
                    lastUpdatedCities = cityFilesByDay.keySet().stream().max(Long::compareTo).orElse(0L);
                }
            }
        }
        long currentDay = TimeUtil.getDay();
        if (currentDay > lastUpdatedCities) {
            synchronized (this) {
                if (currentDay > lastUpdatedCities) {
                    Map<Long, File> downloaded = load(Settings.PNW_URL() + "/data/cities/",
                            new File(Settings.INSTANCE.DATABASE.DATA_DUMP.CITIES));
                    downloaded.forEach((time, file) -> {
                        long day = TimeUtil.getDay(time);
                        CitiesFile cityFile = new CitiesFile(file, cityDict);
                        cityFilesByDay.putIfAbsent(day, cityFile);
                    });
                    lastUpdatedCities = currentDay;
                }
            }
        }

        return cityFilesByDay;
    }

    public Dictionary getDict(boolean isNations) {
        return isNations ? nationDict : cityDict;
    }
}
