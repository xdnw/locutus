package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class IntColumn<P> extends ColumnInfo<P, Integer> {
    public IntColumn(BiConsumer<P, Integer> setter) {
        super(setter);
    }

    @Override
    public Integer read(DataInputStream dis) throws IOException {
        return IOUtil.readVarInt(dis);
    }

    @Override
    public void skip(DataInputStream dis) throws IOException {
        read(dis);
    }

    @Override
    public void write(DataOutputStream dos, Integer value) throws IOException {
        IOUtil.writeVarInt(dos, value);
    }

    @Override
    public Integer read(String string) {
        return Integer.parseInt(string);
    }
}
