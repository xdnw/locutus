package link.locutus.discord.commands.manager.v2.command.shrink;

public class PairShrink implements IShrink {
    public static IShrink of(String small, String large) {
        return of(small, large, 1);
    }

    public static IShrink of(CharSequence small, CharSequence large, int priority) {
        if (small == large || small.equals(large)) {
            return IdenticalShrink.of(small);
        }
        return new PairShrink(small, large, priority);
    }

    ///

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

    @Override
    public int getKeepFactor() {
        return keepFactor;
    }

    @Override
    public String toString() {
        return get();
    }

    @Override
    public IShrink append(String s) {
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
    public IShrink prepend(String s) {
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
    public IShrink append(IShrink s) {
        if (s.isEmpty()) return this;
        if (s.isIdentical()) return append(s.get());
        return new ListShrink(this, s);
    }

    @Override
    public IShrink prepend(IShrink s) {
        if (s.isEmpty()) return this;
        if (s.isIdentical()) return prepend(s.get());
        return new ListShrink(s, this);
    }

    @Override
    public IShrink clone() {
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
    public int shrink() {
        if (!isSmall) {
            isSmall = true;
            return large.length() - small.length();
        }
        return 0;
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
