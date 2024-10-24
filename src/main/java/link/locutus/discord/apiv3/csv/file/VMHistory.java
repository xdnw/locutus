package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class VMHistory {
    private final DataDumpParser parser;

    public static void main(String[] args) throws IOException, ParseException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        DataDumpParser parser = new DataDumpParser().load();

        VMHistory history = new VMHistory(parser);
        long start = System.currentTimeMillis();
        Map<Integer, List<Map.Entry<Integer, Integer>>> vmRanges = history.getVMRanges(f -> true, f -> f == 13861, false);
        long diff = System.currentTimeMillis() - start;
        System.out.println("Took " + diff + "ms to process " + vmRanges.size() + " nations");


        int nationId = 13861;
        List<Map.Entry<Integer, Integer>> ranges = vmRanges.get(nationId);
        if (ranges != null) {
            for (Map.Entry<Integer, Integer> range : ranges) {
                System.out.println("VM range: " + range.getKey() + " - " + range.getValue());
            }
        } else {
            System.out.println("No VM range found for nation " + nationId);
        }
    }

    public VMHistory(DataDumpParser parser) {
        this.parser = parser;
    }


}
