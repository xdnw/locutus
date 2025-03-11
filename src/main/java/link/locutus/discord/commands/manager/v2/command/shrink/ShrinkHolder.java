package link.locutus.discord.commands.manager.v2.command.shrink;

import link.locutus.discord.commands.manager.v2.command.CommandCallable;

public class ShrinkHolder implements IShrink {
    private IShrink holder;

    public ShrinkHolder(IShrink clone) {
        this.holder = clone;
    }

    public ShrinkHolder() {
       holder = EmptyShrink.EMPTY;
    }

    @Override
    public boolean smaller() {
        return this.holder.smaller();
    }

    @Override
    public String toString() {
        return get();
    }

    @Override
    public IShrink append(String s) {
        this.holder = holder.append(s);
        return this;
    }

    @Override
    public IShrink prepend(String s) {
        this.holder = holder.prepend(s);
        return this;
    }

    @Override
    public IShrink append(IShrink s) {
        this.holder = holder.append(s);
        return this;
    }

    @Override
    public IShrink prepend(IShrink s) {
        this.holder = holder.prepend(s);
        return this;
    }

    @Override
    public IShrink clone() {
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
    public int shrink() {
        return holder.shrink();
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

    public IShrink getChild() {
        return holder;
    }

    public void clear() {
        this.holder = EmptyShrink.EMPTY;
    }
}
