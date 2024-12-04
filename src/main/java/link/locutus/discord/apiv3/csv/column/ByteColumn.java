package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class ByteColumn<P> extends NumberColumn<P, Integer> {
    public ByteColumn(DataHeader<P> header, BiConsumer<P, Integer> setter) {
        super(header, setter);
    }

    @Override
    public Integer read(byte[] buffer, int offset) throws IOException {
        return buffer[offset] & 0xFF;
    }

    @Override
    public int getBytes() {
        return 1;
    }

    @Override
    public void write(DataOutputStream dos, Integer value) throws IOException {
        dos.writeByte(value);
    }

    @Override
    public Integer read(String string) {
        return Integer.parseInt(string);
    }
}
