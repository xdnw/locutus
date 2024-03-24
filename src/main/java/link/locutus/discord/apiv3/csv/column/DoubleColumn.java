package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import net.jpountz.util.SafeUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class DoubleColumn<P> extends NumberColumn<P, Double> {
    public DoubleColumn(DataHeader<P> header, BiConsumer<P, Double> setter) {
        super(header, setter);
    }

    @Override
    public int getBytes() {
        return 8;
    }


    @Override
    public Double read(byte[] buffer, int offset) throws IOException {
        long l = ((long)buffer[offset] << 56) |
                ((long)(buffer[offset + 1] & 255) << 48) |
                ((long)(buffer[offset + 2] & 255) << 40) |
                ((long)(buffer[offset + 3] & 255) << 32) |
                ((long)(buffer[offset + 4] & 255) << 24) |
                ((buffer[offset + 5] & 255) << 16) |
                ((buffer[offset + 6] & 255) << 8) |
                ((buffer[offset + 7] & 255));
        return Double.longBitsToDouble(l);
    }

    @Override
    public void write(DataOutputStream dos, Double value) throws IOException {
        long l = Double.doubleToRawLongBits(value);
        dos.writeLong(l);
    }

    @Override
    public Double read(String string) {
        return Double.parseDouble(string);
    }
}
