package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.util.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EnumColumn<P, V extends Enum> extends ColumnInfo<P, V> {
    private final Class<V> enumClass;
    private final V[] constants;
    private final Function<String, V> parser;

    public EnumColumn(Class<V> enumClass, BiConsumer<P, V> setter) {
        this(enumClass, setter, string -> (V) Enum.valueOf(enumClass, string.toUpperCase(Locale.ROOT)));
    }

    public EnumColumn(Class<V> enumClass, BiConsumer<P, V> setter, Function<String, V> parser) {
        super(setter);
        this.enumClass = enumClass;
        this.constants = enumClass.getEnumConstants();
        this.parser = parser;
    }

    @Override
    public V read(DataInputStream dis) throws IOException {
        return constants[IOUtil.readVarInt(dis)];
    }

    @Override
    public void skip(DataInputStream dis) throws IOException {
        read(dis);
    }

    @Override
    public void write(DataOutputStream dos, V value) throws IOException {
        IOUtil.writeVarInt(dos, value.ordinal());
    }

    @Override
    public V read(String string) {
        return parser.apply(string);
    }
}
