package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import net.jpountz.util.SafeUtils;

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
        return Double.longBitsToDouble(
                buffer[offset] & 0xFFL |
                (buffer[offset + 1] & 0xFFL) << 8 |
                (buffer[offset + 2] & 0xFFL) << 16 |
                (buffer[offset + 3] & 0xFFL) << 24 |
                (buffer[offset + 4] & 0xFFL) << 32 |
                (buffer[offset + 5] & 0xFFL) << 40 |
                (buffer[offset + 6] & 0xFFL) << 48 |
                (buffer[offset + 7] & 0xFFL) << 56);
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
