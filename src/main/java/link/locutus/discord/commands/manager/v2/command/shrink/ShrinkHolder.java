package link.locutus.discord.commands.manager.v2.command.shrink;

import link.locutus.discord.commands.manager.v2.command.IShrinkable;
import link.locutus.discord.commands.manager.v2.command.Shrinkable;

public class ShrinkHolder implements IShrinkable {
    private IShrinkable holder;

    public ShrinkHolder(IShrinkable clone) {
        this.holder = clone;
    }

    public ShrinkHolder() {
       holder = new EmptyShrink();
    }

    @Override
    public IShrinkable append(String s) {
        this.holder = holder.append(s);
        return this;
    }

    @Override
    public IShrinkable prepend(String s) {
        this.holder = holder.prepend(s);
        return this;
    }

    @Override
    public IShrinkable append(Shrinkable s) {
        this.holder = holder.append(s);
        return this;
    }

    @Override
    public IShrinkable prepend(Shrinkable s) {
        this.holder = holder.prepend(s);
        return this;
    }

    @Override
    public IShrinkable clone() {
        return new ShrinkHolder(holder.clone());
    }

    @Override
    public int getSize() {
        return holder.getSize();
    }

    @Override
    public int shrink(int totalSize) {
        return holder.shrink(totalSize);
    }

    @Override
    public IShrinkable shrink() {
        holder.shrink();
        return this;
    }

    @Override
    public boolean isIdentical() {
        return holder.isIdentical();
    }

    @Override
    public String get() {
        return holder.get();
    }

    @Override
    public boolean isEmpty() {
        return holder.isEmpty();
    }
}
