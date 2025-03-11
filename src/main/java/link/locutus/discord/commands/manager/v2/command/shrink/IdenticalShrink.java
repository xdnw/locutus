package link.locutus.discord.commands.manager.v2.command.shrink;


public class IdenticalShrink implements IShrink {
    private CharSequence item;

    public static IShrink of(CharSequence s) {
        if (s == null || s.isEmpty()) return EmptyShrink.EMPTY;
        return new IdenticalShrink(s);
    }

    private IdenticalShrink(CharSequence s) {
        this.item = s;
    }

    @Override
    public IShrink append(String s) {
        if (s.isEmpty()) return this;
        if (item instanceof StringBuilder b) {
            b.append(s);
        } else {
            this.item = new StringBuilder(item).append(s);
        }
        return this;
    }

    @Override
    public IShrink prepend(String s) {
        if (s.isEmpty()) return this;
        if (item instanceof StringBuilder b) {
            b.insert(0, s);
        } else {
            this.item = new StringBuilder(s).append(item);
        }
        return this;
    }

    @Override
    public IShrink append(IShrink s) {
        if (s.isIdentical()) {
            return append(s.get());
        }
        return new ListShrink(this, s);
    }

    @Override
    public IShrink prepend(IShrink s) {
        if (s.isIdentical()) {
            return prepend(s.get());
        }
        return new ListShrink(s, this);
    }

    @Override
    public String toString() {
        return get();
    }

    @Override
    public IShrink clone() {
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
    public int shrink() {
        return 0;
    }

    @Override
    public boolean isIdentical() {
        return true;
    }

    @Override
    public String get() {
        return item.toString();
    }

    @Override
    public boolean isEmpty() {
        return this.item.isEmpty();
    }
}
