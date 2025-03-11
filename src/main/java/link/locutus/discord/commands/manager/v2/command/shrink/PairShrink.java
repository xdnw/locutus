package link.locutus.discord.commands.manager.v2.command.shrink;

public class PairShrink implements IShrink {

    private CharSequence[] options;
    private int index;
    private final int keepFactor;

    protected PairShrink(int keepFactor, CharSequence... options) {
        this.options = options;
        this.index = options.length - 1;
        this.keepFactor = keepFactor;
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
        for (int i = 0; i < options.length; i++) {
            CharSequence c = options[i];
            if (c instanceof StringBuilder b) {
                b.append(s);
            } else {
                options[i] = new StringBuilder(c).append(s);
            }
        }
        return this;
    }

    @Override
    public IShrink prepend(String s) {
        for (int i = 0; i < options.length; i++) {
            CharSequence c = options[i];
            if (c instanceof StringBuilder b) {
                b.insert(0, s);
            } else {
                options[i] = new StringBuilder(s).append(c);
            }
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
        return new PairShrink(keepFactor, options);
    }

    @Override
    public int getSize() {
        return options[index].length();
    }

    @Override
    public boolean smaller() {
        if (index > 0) {
            index--;
            return index > 0;
        }
        return false;
    }

    @Override
    public int shrink(int maxSize) {
        int originalIndex = index;
        for (int i = index; i >= 0; i--) {
            index = i;
            if (options[i].length() <= maxSize) {
                break;
            }
        }
        return options[originalIndex].length() - options[index].length();
    }

    @Override
    public int shrink() {
        int originalIndex = index;
        index = 0;
        return options[originalIndex].length() - options[index].length();
    }

    @Override
    public boolean isIdentical() {
        return false;
    }

    @Override
    public String get() {
        return options[index].toString();
    }

    @Override
    public boolean isEmpty() {
        for (CharSequence c : options) {
            if (c.length() > 0) return false;
        }
        return true;
    }
}
