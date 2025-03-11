package link.locutus.discord.commands.manager.v2.command.shrink;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.*;

public class ListShrink implements IShrink {
    public final List<IShrink> items;
    private int unshrunk = -1;

    public ListShrink(IShrink a, IShrink b) {
        items = new ObjectArrayList<>(2);
        items.add(a);
        items.add(b);
    }

    @Override
    public boolean smaller() {
        if (items.isEmpty()) return false;
        if (unshrunk == 0) {
            return items.get(0).smaller();
        }
        if (unshrunk == -1 || unshrunk >= items.size()) {
            unshrunk = items.size() - 1;
        }
        for (int i = unshrunk; i >= 0; i--) {
            IShrink item = items.get(i);
            int size = item.getSize();
            boolean smaller = item.smaller();
            if (item.getSize() < size) {
                unshrunk = smaller ? i : (i == 0 ? 0 : i - 1);
                return true;
            }
        }
        unshrunk = 0;
        return false;
    }

    public ListShrink() {
        items = new ObjectArrayList<>();
    }

    @Override
    public String toString() {
        return get();
    }

    @Override
    public IShrink append(String s) {
        if (s.isEmpty()) return this;
        if (!items.isEmpty()) {
            IShrink last = items.get(items.size() - 1).append(s);
            items.set(items.size() - 1, last);
        } else {
            items.add(IShrink.of(s));
        }
        return this;
    }

    @Override
    public IShrink prepend(String s) {
        if (s.isEmpty()) return this;
        if (!items.isEmpty()) {
            IShrink first = items.get(0).prepend(s);
            items.set(0, first);
        } else {
            items.add(IShrink.of(s));
        }
        return this;
    }

    @Override
    public IShrink append(IShrink s) {
        if (s.isEmpty()) return this;
        if (s.isIdentical() && !items.isEmpty()) return append(s.toString());
        items.add(s);
        return this;
    }

    @Override
    public IShrink prepend(IShrink s) {
        if (s.isEmpty()) return this;
        if (s.isIdentical() && !items.isEmpty()) return prepend(s.toString());
        items.add(0, s);
        return this;
    }

    @Override
    public IShrink clone() {
        ListShrink copy = new ListShrink();
        for (IShrink item : items) {
            copy.items.add(item.clone());
        }
        return copy;
    }

    @Override
    public int getSize() {
        return items.stream().mapToInt(IShrink::getSize).sum();
    }

    @Override
    public int shrink(int totalSize) {
        if (isIdentical()) return 0;
        return IShrink.shrink(items, totalSize);
    }

    @Override
    public int shrink() {
        int diff = 0;
        for (IShrink item : items) {
            diff += item.shrink();
        }
        return diff;
    }

    @Override
    public boolean isIdentical() {
        return items.stream().allMatch(IShrink::isIdentical);
    }

    @Override
    public String get() {
        StringBuilder sb = new StringBuilder();
        for (IShrink item : items) {
            sb.append(item.get());
        }
        return sb.toString();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty() || items.stream().allMatch(IShrink::isEmpty);
    }
}
