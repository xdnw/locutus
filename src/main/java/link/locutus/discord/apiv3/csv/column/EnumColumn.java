package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EnumColumn<P, V extends Enum> extends ColumnInfo<P, V> {
    private final Class<V> enumClass;
    private final V[] constants;
    private final Function<String, V> parser;

    public EnumColumn(DataHeader<P> header, Class<V> enumClass, BiConsumer<P, V> setter) {
        this(header, enumClass, setter, string -> (V) Enum.valueOf(enumClass, string.toUpperCase(Locale.ROOT)));
    }

    public EnumColumn(DataHeader<P> header, Class<V> enumClass, BiConsumer<P, V> setter, Function<String, V> parser) {
        super(header, setter);
        this.enumClass = enumClass;
        this.constants = enumClass.getEnumConstants();
        this.parser = parser;
    }

    @Override
    public V read(byte[] buffer, int offset) throws IOException {
        return constants[buffer[offset] & 0xFF];
    }

    @Override
    public int getBytes() {
        return 1;
    }

    @Override
    public void write(DataOutputStream dos, V value) throws IOException {
        dos.writeByte(value.ordinal());
    }

    @Override
    public V read(String string) {
        return parser.apply(string);
    }
}
