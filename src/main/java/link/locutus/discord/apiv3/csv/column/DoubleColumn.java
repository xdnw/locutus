package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class DoubleColumn<P> extends ColumnInfo<P, Double> {
    public DoubleColumn(BiConsumer<P, Double> setter) {
        super(setter);
    }

    @Override
    public Double read(DataInputStream dis) throws IOException {
        return dis.readDouble();
    }

    @Override
    public void skip(DataInputStream dis) throws IOException {
        dis.skipBytes(8);
    }

    @Override
    public void write(DataOutputStream dos, Double value) throws IOException {
        dos.writeDouble(value);
    }

    @Override
    public Double read(String string) {
        return Double.parseDouble(string);
    }
}
