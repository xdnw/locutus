package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public class FlatCodedStringMap implements ICodedStringMap {
    private final List<String> list = new ObjectArrayList<>();

    @Override
    public int insert(String value) {
        int newIndex = list.size();
        list.add(value);
        return newIndex;
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
    public int charSize() {
        int size = 0;
        for (String s : list) {
            size += s.length();
        }
        return size;
    }
}