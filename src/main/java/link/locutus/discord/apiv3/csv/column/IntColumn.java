package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class IntColumn<P> extends NumberColumn<P, Integer> {
    public IntColumn(DataHeader<P> header, BiConsumer<P, Integer> setter) {
        super(header, setter);
    }

    @Override
    public Integer read(byte[] buffer, int offset) throws IOException {
        return buffer[offset] << 24 | (buffer[offset + 1] & 0xFF) << 16 | (buffer[offset + 2] & 0xFF) << 8 | (buffer[offset + 3] & 0xFF);
    }

    @Override
    public int getBytes() {
        return 4;
    }

    @Override
    public void write(DataOutputStream dos, Integer value) throws IOException {
        dos.writeInt(value);
    }

    @Override
    public Integer read(String string) {
        return Integer.parseInt(string);
    }
}
