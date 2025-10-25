package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.StringMan;

import java.util.List;

public class FlatCodedStringMap implements ICodedStringMap {
    private final List<String> list = new ObjectArrayList<>();
    private final Long2IntOpenHashMap hashes = new Long2IntOpenHashMap();

    @Override
    public int insert(String value) {
        long hash = StringMan.hash(value);
        int index = hashes.getOrDefault(hash, -1);
        if (index != -1) {
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

    @Override
    public int countDuplicates() {
        return list.size() - hashes.size();
    }

    @Override
    public int stringLength() {
        int total = 0;
        for (String s : list) {
            total += s.length();
        }
        return total;
    }
}