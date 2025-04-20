package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.StringMan;

import java.util.List;
import java.util.Map;

public class FlatCodedStringMap implements ICodedStringMap {
    private final List<String> list = new ObjectArrayList<>();
    private Long2IntOpenHashMap hashes = new Long2IntOpenHashMap();

    @Override
    public int insert(String value) {
        long hash = StringMan.hash(value);
        Integer index = hashes.get(hash);
        if (index != null) {
            return index;
        }
        int size = list.size();
        hashes.put(hash, size);
        list.add(value);
        return size;
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