package link.locutus.discord.commands.manager.v2.command.shrink;

public class ShrinkableField implements IShrink {
    public final IShrink name;
    public final IShrink value;
    public final boolean inline;

    public ShrinkableField(IShrink name, IShrink value, boolean inline) {
        this.name = name;
        this.value = value;
        this.inline = inline;
    }

    public ShrinkableField(String name, String value, boolean inline) {
        this(IShrink.of(name), IShrink.of(value), inline);
    }

    public ShrinkableField(ShrinkableField field) {
        this.name = field.name.clone();
        this.value = field.value.clone();
        this.inline = field.inline;
    }

    @Override
    public boolean smaller() {
        int size = name.getSize();
        name.smaller();
        if (name.getSize() < size) return true;
        return value.smaller();
    }

    @Override
    public IShrink append(String s) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public IShrink prepend(String s) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public IShrink append(IShrink s) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public IShrink prepend(IShrink s) {
        throw new UnsupportedOperationException("Not supported.");
    }

    public ShrinkableField clone() {
        return new ShrinkableField(this);
    }

    @Override
    public int getSize() {
        return name.getSize() + value.getSize();
    }

    @Override
    public int shrink(int totalSize) {
        int size = getSize();
        if (size > totalSize) {
            int originalSize = size;
            size -= name.shrink();
            if (size > totalSize) {
                size -= value.shrink();
            }
            return originalSize - size;
        }
        return 0;
    }

    @Override
    public int shrink() {
        return name.shrink() + value.shrink();
    }

    @Override
    public boolean isIdentical() {
        return true;
    }

    @Override
    public String toString() {
        return get();
    }

    @Override
    public String get() {
        return name.get() + ": " + value.get();
    }

    @Override
    public boolean isEmpty() {
        return name.isEmpty() && value.isEmpty();
    }
}
