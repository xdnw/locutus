package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class LongColumn<P> extends NumberColumn<P, Long> {
    public LongColumn(DataHeader<P> header, BiConsumer<P, Long> setter) {
        super(header, setter);
    }

    @Override
    public Long read(byte[] buffer, int offset) throws IOException {
        return ((long)buffer[offset] << 56) |
                ((long)(buffer[offset + 1] & 255) << 48) |
                ((long)(buffer[offset + 2] & 255) << 40) |
                ((long)(buffer[offset + 3] & 255) << 32) |
                ((long)(buffer[offset + 4] & 255) << 24) |
                ((buffer[offset + 5] & 255) << 16) |
                ((buffer[offset + 6] & 255) << 8) |
                ((buffer[offset + 7] & 255));
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
