package link.locutus.discord.util.task.multi;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.NationHeaderReader;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class SnapshotMultiData {
    public final Map<Continent, Long> mostCommonLocation = new Object2LongOpenHashMap<>();
    public final Map<Integer, MultiData> data = new Int2ObjectOpenHashMap<>();
    public final Map<String, Integer> flagCounts = new Object2IntOpenHashMap<>();

    public record MultiData(int nationId, Continent continent, long location, String currency, String flagUrl,
                            String portraitUrl, String leaderTitle, String nationTitle) {
    }

    public boolean hasCustomFlag(int nationId) {
        MultiData natData = data.get(nationId);
        if (natData == null) return false;
        if (natData.flagUrl() == null || natData.flagUrl().isEmpty()) return false;
        return flagCounts.getOrDefault(natData.flagUrl(), 0) <= 1;
    }

    public boolean hasCustomCurrency(int nationId) {
        MultiData natData = data.get(nationId);
        if (natData == null) return false;
        return !natData.currency().equalsIgnoreCase("dollar");
    }

    public boolean hasPickedLand(int nationId) {
        MultiData natData = data.get(nationId);
        if (natData == null) return false;
        return mostCommonLocation.get(natData.continent()) != natData.location();
    }

    public boolean hasCustomPortrait(int nationId) {
        MultiData natData = data.get(nationId);
        if (natData == null) return false;
        if (natData.portraitUrl() == null || natData.portraitUrl().isEmpty()) return false;
        return true;
    }

    public SnapshotMultiData() throws IOException, ParseException {
        Map<Continent, Map<Long, AtomicInteger>> mostCommonLocationPairs = new Object2ObjectOpenHashMap<>();
        DataDumpParser snapshot = Locutus.imp().getDataDumper(true);
        snapshot.load();

        List<Long> days = snapshot.getDays(true, false);
        long lastDay = days.get(days.size() - 1);

        BiFunction<Double, Double, Long> pairLocation = (a, b) -> {
            int lat = (int) (a * 100);
            int lon = (int) (b * 100);
            return MathMan.pairInt(lat, lon);
        };

        snapshot.withNationFile(lastDay, new ThrowingConsumer<NationsFile>() {
            @Override
            public void acceptThrows(NationsFile file) throws IOException {
                System.out.println("Load snapshot: " + file.getDay());
                file.reader().required(header -> List.of(
                        header.nation_id,
                        header.continent,
                        header.latitude,
                        header.longitude,
                        header.currency,
                        header.flag_url,
                        header.portrait_url,
                        header.leader_title,
                        header.nation_title
                )).read(new ThrowingConsumer<NationHeaderReader>() {
                    @Override
                    public void acceptThrows(NationHeaderReader r) {
                        try {
                            int nationId = r.header.nation_id.get();

                            double lat = r.header.latitude.get();
                            double lon = r.header.longitude.get();
                            long pair = pairLocation.apply(lat, lon);
                            Continent continent = r.header.continent.get();
                            mostCommonLocationPairs.computeIfAbsent(continent, k -> new Object2ObjectOpenHashMap<>()).computeIfAbsent(pair, k -> new AtomicInteger()).incrementAndGet();
                            flagCounts.merge(r.header.flag_url.get(), 1, Integer::sum);
                            MultiData row = new MultiData(nationId, continent, pair, r.header.currency.get(), r.header.flag_url.get(), r.header.portrait_url.get(), r.header.leader_title.get(), r.header.nation_title.get());
                            data.put(nationId, row);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        for (Map.Entry<Continent, Map<Long, AtomicInteger>> entry : mostCommonLocationPairs.entrySet()) {
            long max = 0;
            long maxPair = 0;
            for (Map.Entry<Long, AtomicInteger> pair : entry.getValue().entrySet()) {
                if (pair.getValue().get() > max) {
                    max = pair.getValue().get();
                    maxPair = pair.getKey();
                }
            }
            mostCommonLocation.put(entry.getKey(), maxPair);
        }

    }
}
