package link.locutus.discord.commands.manager.v2.command;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.*;

public class Shrinkable implements IShrinkable {
    public static int calcSize(Collection<Shrinkable> shrinkables) {
        return shrinkables.stream().mapToInt(Shrinkable::getSize).sum();
    }

    public static int calcSize(Shrinkable... shrinkables) {
        return Arrays.stream(shrinkables).mapToInt(Shrinkable::getSize).sum();
    }

    public static void shrink(int totalSize, Shrinkable... messages) {
        shrink(totalSize, Arrays.asList(messages));
    }

    public static void shrink(int totalSize, List<Shrinkable> messages) {
        if (messages.size() == 0) return;
        if (messages.size() == 1) {
            messages.get(0).shrink(totalSize);
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

    public static Shrinkable of(String s) {
        return new Shrinkable(s);
    }

    public static Shrinkable of(String small, String large) {
        return of(small, large, 1);
    }

    public static Shrinkable of(String small, String large, int factor) {
        return new Shrinkable(small, large, factor);
    }

    /// /////////////////
    private String small;
    private String large;
    private final int keepFactor;
    private boolean isIdentical;
    private boolean isSmall;

    public Shrinkable(String small) {
        this(small, small, Integer.MIN_VALUE);
        this.isIdentical = true;
        this.isSmall = true;
    }

    public Shrinkable(String small, String large, int keepFactor) {
        if (small == null) throw new IllegalArgumentException("Cannot have null string");
        this.small = small;
        this.large = large;
        this.keepFactor = keepFactor;
    }

    public Shrinkable(Shrinkable shrinkable) {
        this.small = shrinkable.small;
        this.large = shrinkable.large;
        this.keepFactor = shrinkable.keepFactor;
        this.isIdentical = shrinkable.isIdentical;
        this.isSmall = shrinkable.isSmall;
    }

    @Override
    public Shrinkable clone() {
        return new Shrinkable(this);
    }

    public <T extends Collection<Shrinkable>> T addTo(T collection) {
        collection.add(this);
        return collection;
    }

    @Override
    public int getSize() {
        return isSmall ? small.length() : large.length();
    }

    /**
     * @param totalSize
     * @return the amount of characters removed
     */
    @Override
    public int shrink(int totalSize) {
        if (!isSmall && large.length() > totalSize) {
            isSmall = true;
            return large.length() - small.length();
        }
        return 0;
    }

    @Override
    public Shrinkable shrink() {
        isSmall = true;
        return this;
    }

    @Override
    public boolean isIdentical() {
        return isIdentical;
    }

    public String get(int minKeepFactor) {
        return keepFactor <= minKeepFactor ? small : large;
    }

    public int keepFactor() {
        return keepFactor;
    }

    @Override
    public String get() {
        return isSmall ? small : large;
    }

    public String small() {
        return small;
    }

    public String large() {
        return large;
    }

    public Shrinkable append(String s) {
        small += s;
        if (isIdentical) {
            large = small;
        } else {
            large += s;
        }
        return this;
    }

    public Shrinkable prepend(String s) {
        small = s + small;
        if (isIdentical) {
            large = small;
        } else {
            large = s + large;
        }
        return this;
    }
}
