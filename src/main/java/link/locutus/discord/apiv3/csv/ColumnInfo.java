package link.locutus.discord.apiv3.csv;

import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public abstract class ColumnInfo<P, V> {
    private final boolean alwaysSkip;
    private final DataHeader<P> header;
    private int index;
    private int offset;
    private final BiConsumer<P, V> setter;
    protected V cacheValue;
    private String[] aliases;
    private String name;

    public ColumnInfo(DataHeader<P> header, BiConsumer<P, V> setter) {
        this.header = header;
        this.index = -1;
        this.setter = setter == null ? (_1, _2) -> {} : setter;
        this.alwaysSkip = setter == null;
    }

    public abstract V getDefault();

    public String getName() {
        return name;
    }

    public final DataHeader<P> getHeader() {
        return header;
    }

    public abstract int getBytes();

    public boolean isAlwaysSkip() {
        return alwaysSkip;
    }

    public void setIndex(int index, int offset) {
        this.index = index;
        this.offset = offset;
    }

    public final int getOffset() {
        return offset;
    }

    public final void setCachedValue(V value) {
        this.cacheValue = value;
    }

    public V get() {
        return cacheValue;
    }

    public int getIndex() {
        return index;
    }

    public abstract V read(byte[] buffer, int offset) throws IOException;

    public final void skip(DataInputStream dis) throws IOException {
        dis.skipBytes(getBytes());
    }

    public abstract void write(DataOutputStream dos, V value) throws IOException;

    public abstract V read(String string);

    public void set(P parent, V value) {
        setter.accept(parent, value);
    }

    public void set(P parent) {
        setter.accept(parent, get());
    }

    public void setIfPresent(P parent) {
        V tmp = get();
        if (tmp != null) {
            setter.accept(parent, tmp);
        }
    }

    public <T extends ColumnInfo<P, V>> T alias(String... s) {
        this.aliases = s;
        return (T) this;
    }

    public String[] getAliases() {
        return aliases;
    }

    public void setName(String name) {
        this.name = name;
    }
}
