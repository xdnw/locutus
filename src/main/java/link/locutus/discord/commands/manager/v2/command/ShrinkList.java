package link.locutus.discord.commands.manager.v2.command;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public class ShrinkList {
    public final List<Shrinkable> items = new ObjectArrayList<>();
    public ShrinkList() {

    }

    public ShrinkList add(String s) {
        if (!s.isEmpty()) {
            items.add(Shrinkable.of(s));
        }
        return this;
    }

    public ShrinkList add(Shrinkable s) {
        if (!s.get().isEmpty()) {
            items.add(s);
        }
        return this;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Shrinkable item : items) {
            sb.append(item.get());
        }
        return sb.toString();
    }

    public int getSize() {
        int size = 0;
        for (Shrinkable item : items) {
            size += item.getSize();
        }
        return size;
    }

    public ShrinkList shrink() {
        for (Shrinkable item : items) {
            item.shrink();
        }
        return this;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
