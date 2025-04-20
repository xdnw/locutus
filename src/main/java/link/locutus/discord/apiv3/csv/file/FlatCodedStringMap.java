package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.StringMan;

import java.util.List;
import java.util.Map;

public class FlatCodedStringMap implements ICodedStringMap {
    private final List<String> list = new ObjectArrayList<>();
    private LongOpenHashSet hashes = new LongOpenHashSet();

    @Override
    public boolean insert(String value) {
        long hash = StringMan.hash(value);
        if (hashes.add(hash)) {
            list.add(value);
        }
        return false;
    }

    @Override
    public String get(int index) {
        return list.get(index);
    }

    @Override
    public int size() {
        return list.size();
    }
}