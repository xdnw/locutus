package link.locutus.discord.commands.manager.v2.command.shrink;

import link.locutus.discord.commands.manager.v2.command.IShrinkable;
import link.locutus.discord.commands.manager.v2.command.Shrinkable;

public class EmptyShrink implements IShrinkable {
    @Override
    public IShrinkable append(String s) {
        if (s.isEmpty()) return this;
        return IdenticalShrink.of(s);
    }

    @Override
    public IShrinkable prepend(String s) {
        if (s.isEmpty()) return this;
        return IdenticalShrink.of(s);
    }

    @Override
    public IShrinkable append(Shrinkable s) {
        return s;
    }

    @Override
    public IShrinkable prepend(Shrinkable s) {
        return s;
    }

    @Override
    public IShrinkable clone() {
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
    public IShrinkable shrink() {
        return this;
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
