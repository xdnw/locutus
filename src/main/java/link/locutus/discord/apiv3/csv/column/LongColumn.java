package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.util.IOUtil;
import net.jpountz.util.SafeUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class LongColumn<P> extends NumberColumn<P, Long> {
    public LongColumn(DataHeader<P> header, BiConsumer<P, Long> setter) {
        super(header, setter);
    }

//    @Override
//    public Long read(DataInputStream dis) throws IOException {
//        return dis.readLong();
//    }


    @Override
    public Long read(byte[] buffer, int offset) throws IOException {
        return buffer[offset] & 0xFFL |
                (buffer[offset + 1] & 0xFFL) << 8 |
                (buffer[offset + 2] & 0xFFL) << 16 |
                (buffer[offset + 3] & 0xFFL) << 24 |
                (buffer[offset + 4] & 0xFFL) << 32 |
                (buffer[offset + 5] & 0xFFL) << 40 |
                (buffer[offset + 6] & 0xFFL) << 48 |
                (buffer[offset + 7] & 0xFFL) << 56;
    }

    @Override
    public int getBytes() {
        return 8;
    }

    @Override
    public void write(DataOutputStream dos, Long value) throws IOException {
        dos.writeLong(value);
    }

    @Override
    public Long read(String string) {
        return Long.parseLong(string);
    }
}
