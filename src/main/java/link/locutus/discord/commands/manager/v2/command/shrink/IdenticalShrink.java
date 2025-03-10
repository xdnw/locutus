package link.locutus.discord.commands.manager.v2.command.shrink;

import link.locutus.discord.commands.manager.v2.command.IShrinkable;
import link.locutus.discord.commands.manager.v2.command.Shrinkable;

public class IdenticalShrink implements IShrinkable {
    private CharSequence item;

    public static IShrinkable of(CharSequence s) {
        if (s == null || s.isEmpty()) return new EmptyShrink();
        return new IdenticalShrink(s);
    }

    private IdenticalShrink(CharSequence s) {
        this.item = s;
    }

    @Override
    public IShrinkable append(String s) {
        if (s.isEmpty()) return this;
        if (item instanceof StringBuilder b) {
            b.append(s);
        } else {
            this.item = new StringBuilder(item).append(s);
        }
        return this;
    }

    @Override
    public IShrinkable prepend(String s) {
        if (s.isEmpty()) return this;
        if (item instanceof StringBuilder b) {
            b.insert(0, s);
        } else {
            this.item = new StringBuilder(s).append(item);
        }
        return this;
    }

    @Override
    public IShrinkable append(Shrinkable s) {
        if (s.isIdentical()) {
            return append(s.get());
        }
        return new ListShrink(this, s);
    }

    @Override
    public IShrinkable prepend(Shrinkable s) {
        if (s.isIdentical()) {
            return prepend(s.get());
        }
        return new ListShrink(s, this);
    }

    @Override
    public IShrinkable clone() {
        return new IdenticalShrink(item);
    }

    @Override
    public int getSize() {
        return item.length();
    }

    @Override
    public int shrink(int totalSize) {
        return 0;
    }

    @Override
    public IShrinkable shrink() {
        return this;
    }

    @Override
    public boolean isIdentical() {
        return true;
    }

    @Override
    public String get() {
        return item.toString();
    }
}
