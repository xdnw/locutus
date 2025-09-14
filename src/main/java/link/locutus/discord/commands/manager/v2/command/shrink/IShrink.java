package link.locutus.discord.commands.manager.v2.command.shrink;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.util.discord.DiscordUtil;

import java.util.*;
import java.util.stream.Collectors;

public interface IShrink {
    public static IShrink of(CharSequence s) {
        if (s == null || s.isEmpty()) return EmptyShrink.EMPTY;
        return new IdenticalShrink(s);
    }

    public static IShrink of(CharSequence small, CharSequence large, int priority) {
        if (small == large || small.equals(large)) {
            return of(small);
        }
        return new PairShrink(priority, small, large);
    }

    public static IShrink of(CharSequence... options) {
        if (options.length == 0) return EmptyShrink.EMPTY;
        if (options.length == 1) return of(options[0]);
        return new PairShrink(1, options);
    }

    public static IShrink of(int priority, CharSequence... options) {
        return new PairShrink(priority, options);
    }

    public static IShrink of(String small, String large) {
        return of(small, large, 1);
    }

    public static List<IShrink> toList(List<String> results) {
        return results.stream().map(IShrink::of).collect(Collectors.toList());
    }

    public IShrink append(String s);
    public IShrink prepend(String s);
    public IShrink append(IShrink s);
    public IShrink prepend(IShrink s);
    public IShrink clone();
    public int getSize();

    /*
    Shrinks this to the next smallest string. Returns true if there is a smaller string, false otherwise.
     */
    public boolean smaller();
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

        while (!messagesByKeepFactor.isEmpty()) {
            for (int keepFactor : sizesSorted) {
                List<IShrink> shrinkables = messagesByKeepFactor.get(keepFactor);
                if (shrinkables == null) continue;
                for (int i = shrinkables.size() - 1; i >= 0; i--) {
                    IShrink shrinkable = shrinkables.get(i);
                    int elemSize = shrinkable.getSize();
                    boolean dontRemove = shrinkable.smaller();
                    if (!dontRemove) {
                        shrinkables.remove(i);
                    }
                    int diff = elemSize - shrinkable.getSize();
                    currentSize -= diff;
                    if (currentSize <= totalSize) return originalSize - currentSize;
                }
                if (shrinkables.isEmpty()) {
                    messagesByKeepFactor.remove(keepFactor);
                }
            }
        }
        return originalSize - currentSize;
    }

    default List<String> split(int maxContentLength) {
        String message = toString();
        if (message.contains("@everyone")) {
            message = message.replace("@everyone", "");
        }
        if (message.contains("@here")) {
            message = message.replace("@here", "");
        }
        return DiscordUtil.wrap(message, maxContentLength);
    }

}
