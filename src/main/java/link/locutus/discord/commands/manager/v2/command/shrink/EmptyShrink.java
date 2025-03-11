package link.locutus.discord.commands.manager.v2.command.shrink;

public class EmptyShrink implements IShrink {
    public static final EmptyShrink EMPTY = new EmptyShrink();

    private EmptyShrink() {

    }


    @Override
    public IShrink append(String s) {
        if (s.isEmpty()) return this;
        return IdenticalShrink.of(s);
    }

    @Override
    public IShrink prepend(String s) {
        if (s.isEmpty()) return this;
        return IdenticalShrink.of(s);
    }

    @Override
    public IShrink append(IShrink s) {
        return s;
    }

    @Override
    public IShrink prepend(IShrink s) {
        return s;
    }

    @Override
    public IShrink clone() {
        return this;
    }

    @Override
    public int getSize() {
        return 0;
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
        return "";
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
