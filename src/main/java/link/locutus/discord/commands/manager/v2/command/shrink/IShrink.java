package link.locutus.discord.commands.manager.v2.command.shrink;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.util.discord.DiscordUtil;

import java.util.*;

public interface IShrink {
    public IShrink append(String s);
    public IShrink prepend(String s);
    public IShrink append(IShrink s);
    public IShrink prepend(IShrink s);
    public IShrink clone();
    public int getSize();
    public int shrink(int totalSize);
    public int shrink();
    public boolean isIdentical();

    default int getKeepFactor() {
        return 0;
    }

    public String get();
    public boolean isEmpty();

    public static int shrink(List<IShrink> items, int totalSize) {
        if (items.size() == 0) return 0;
        if (items.size() == 1) {
            return items.get(0).shrink(totalSize);
        }

        int currentSize = items.stream().mapToInt(IShrink::getSize).reduce(0, Integer::sum);
        if (currentSize <= totalSize) return 0;
        int originalSize = currentSize;

        Set<Integer> sizes = new IntOpenHashSet();
        Map<Integer, LinkedList<IShrink>> messagesByKeepFactor = new Int2ObjectOpenHashMap<>();

        for (IShrink message : items) {
            int keepFactor = message.getKeepFactor();
            sizes.add(keepFactor);
            messagesByKeepFactor.computeIfAbsent(keepFactor, k -> new LinkedList<>()).add(message);
        }

        List<Integer> sizesSorted = new IntArrayList(sizes);
        sizesSorted.sort(Comparator.reverseOrder());

        for (int keepFactor : sizesSorted) {
            List<IShrink> shrinkables = messagesByKeepFactor.get(keepFactor);
            for (IShrink shrinkable : shrinkables) {
                int elemSize = shrinkable.getSize();
                shrinkable.shrink();
                int diff = elemSize - shrinkable.getSize();
                currentSize -= diff;
                if (currentSize <= totalSize) return originalSize - currentSize;
            }
        }
        return originalSize - currentSize;
    }

    default List<String> split(int maxContentLength, int minSize) {
        String message = toString();
        if (message.contains("@everyone")) {
            message = message.replace("@everyone", "");
        }
        if (message.contains("@here")) {
            message = message.replace("@here", "");
        }
        return DiscordUtil.wrap(message, maxContentLength, minSize);
    }
}
