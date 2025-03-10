package link.locutus.discord.commands.manager.v2.command.shrink;

import link.locutus.discord.commands.manager.v2.command.IShrinkable;
import link.locutus.discord.commands.manager.v2.command.ShrinkList;
import link.locutus.discord.commands.manager.v2.command.Shrinkable;

public class PairShrink implements IShrinkable {
    private CharSequence small;
    private CharSequence large;
    private final int keepFactor;
    private boolean isSmall;

    private PairShrink(CharSequence small, CharSequence large, int keepFactor) {
        this.small = small;
        this.large = large;
        this.keepFactor = keepFactor;
        this.isSmall = false;
    }

    public static IShrinkable PairShrink(CharSequence small, CharSequence large, int priority) {
        if (small == large || small.equals(large)) {
            return IdenticalShrink.of(small);
        }
        return new PairShrink(small, large, priority);
    }

    @Override
    public IShrinkable append(String s) {
        if (small instanceof StringBuilder b) {
            b.insert(0, s);
        } else {
            this.small = new StringBuilder(s).append(small);
        }
        if (large instanceof StringBuilder b) {
            b.append(s);
        } else {
            this.large = new StringBuilder(large).append(s);
        }
        return this;
    }

    @Override
    public IShrinkable prepend(String s) {
        if (small instanceof StringBuilder b) {
            b.insert(0, s);
        } else {
            this.small = new StringBuilder(s).append(small);
        }
        if (large instanceof StringBuilder b) {
            b.insert(0, s);
        } else {
            this.large = new StringBuilder(s).append(large);
        }
        return this;
    }

    @Override
    public IShrinkable append(Shrinkable s) {
        if (s.isEmpty()) return this;
        if (s.isIdentical()) return append(s.get());
        return new ListShrink(this, s);
    }

    @Override
    public IShrinkable prepend(Shrinkable s) {
        if (s.isEmpty()) return this;
        if (s.isIdentical()) return prepend(s.get());
        return new ListShrink(s, this);
    }

    @Override
    public IShrinkable clone() {
        return new PairShrink(small.toString(), large.toString(), keepFactor);
    }

    @Override
    public int getSize() {
        return isSmall ? small.length() : large.length();
    }

    @Override
    public int shrink(int totalSize) {
        if (!isSmall && large.length() > totalSize) {
            isSmall = true;
            return large.length() - small.length();
        }
        return 0;
    }

    @Override
    public IShrinkable shrink() {
        isSmall = true;
        return this;
    }

    @Override
    public boolean isIdentical() {
        return false;
    }

    @Override
    public String get() {
        return (isSmall ? small : large).toString();
    }

    @Override
    public boolean isEmpty() {
        return (isSmall ? small : large).length() == 0;
    }
}
