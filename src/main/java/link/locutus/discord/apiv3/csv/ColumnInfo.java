package link.locutus.discord.apiv3.csv;

import de.siegmar.fastcsv.reader.CsvRow;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public abstract class ColumnInfo<P, V> {
    private int index;
    private final BiConsumer<P, V> setter;
    private V cacheValue;

    public ColumnInfo(BiConsumer<P, V> setter) {
        this.index = -1;
        this.setter = setter;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setCachedValue(V value) {
        this.cacheValue = value;
    }

    public V get() {
        return cacheValue;
    }

    public int getIndex() {
        return index;
    }

    public abstract V read(DataInputStream dis) throws IOException;

    public abstract void skip(DataInputStream dis) throws IOException;

    public abstract void write(DataOutputStream dos, V value) throws IOException;

    public abstract V read(String string);

    public void set(P parent, V value) {
        setter.accept(parent, value);
    }

    public void set(P parent) {
        setter.accept(parent, cacheValue);
    }

    public void setIfPresent(P parent) {
        if (cacheValue != null) {
            setter.accept(parent, cacheValue);
        }
    }
}
