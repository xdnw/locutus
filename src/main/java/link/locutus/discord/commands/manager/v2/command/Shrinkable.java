package link.locutus.discord.commands.manager.v2.command;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.*;

public class Shrinkable {
    private final String small;
    private final String large;
    private final int keepFactor;
    private boolean isIdentical;
    private boolean isSmall;

    public Shrinkable(String small) {
        this(small, small, Integer.MIN_VALUE);
        this.isIdentical = true;
        this.isSmall = true;
    }

    public Shrinkable(String small, String large, int keepFactor) {
        this.small = small;
        this.large = large;
        this.keepFactor = keepFactor;
    }

    public static int calcSize(Collection<Shrinkable> shrinkables) {
        return shrinkables.stream().mapToInt(Shrinkable::getSize).sum();
    }

    public static int calcSize(Shrinkable... shrinkables) {
        return Arrays.stream(shrinkables).mapToInt(Shrinkable::getSize).sum();
    }

    public static void shrink(int totalSize, Shrinkable... messages) {
        if (messages.length == 0) return;
        if (messages.length == 1) {
            messages[0].shrink(totalSize);
            return;
        }

        int currentSize = calcSize(messages);
        if (currentSize <= totalSize) return;

        Set<Integer> sizes = new IntOpenHashSet();
        Map<Integer, LinkedList<Shrinkable>> messagesByKeepFactor = new Int2ObjectOpenHashMap<>();

        for (Shrinkable message : messages) {
            int keepFactor = message.keepFactor();
            sizes.add(keepFactor);
            messagesByKeepFactor.computeIfAbsent(keepFactor, k -> new LinkedList<>()).add(message);
        }

        List<Integer> sizesSorted = new IntArrayList(sizes);
        sizesSorted.sort(Comparator.reverseOrder());

        for (int keepFactor : sizesSorted) {
            LinkedList<Shrinkable> shrinkables = messagesByKeepFactor.get(keepFactor);
            for (Shrinkable shrinkable : shrinkables) {
                currentSize -= shrinkable.shrink(totalSize);
                if (currentSize <= totalSize) return;
            }
        }
    }

    public int getSize() {
        return isSmall ? small.length() : large.length();
    }

    /**
     * @param totalSize
     * @return the amount of characters removed
     */
    public int shrink(int totalSize) {
        if (!isSmall && large.length() > totalSize) {
            isSmall = true;
            return large.length() - small.length();
        }
        return 0;
    }

    public boolean isIdentical() {
        return isIdentical;
    }

    public String get(int minKeepFactor) {
        return keepFactor <= minKeepFactor ? small : large;
    }

    public int keepFactor() {
        return keepFactor;
    }

    public String get() {
        return isSmall ? small : large;
    }

    public String small() {
        return small;
    }

    public String large() {
        return large;
    }
}
