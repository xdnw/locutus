package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.util.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class LongColumn<P> extends NumberColumn<P, Long> {
    public LongColumn(BiConsumer<P, Long> setter) {
        super(setter);
    }

    @Override
    public Long read(DataInputStream dis) throws IOException {
        return IOUtil.readVarLong(dis);
    }

    @Override
    public void skip(DataInputStream dis) throws IOException {
        read(dis);
    }

    @Override
    public void write(DataOutputStream dos, Long value) throws IOException {
        IOUtil.writeVarLong(dos, value);
    }

    @Override
    public Long read(String string) {
        return Long.parseLong(string);
    }
}
