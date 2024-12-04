package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class DoubleIntColumn<P> extends NumberColumn<P, Double> {
    public DoubleIntColumn(DataHeader<P> header, BiConsumer<P, Double> setter) {
        super(header, setter);
    }

    @Override
    public Double read(byte[] buffer, int offset) throws IOException {
        return 0.01 * (buffer[offset] << 24 | (buffer[offset + 1] & 0xFF) << 16 | (buffer[offset + 2] & 0xFF) << 8 | (buffer[offset + 3] & 0xFF));
    }

    @Override
    public int getBytes() {
        return 4;
    }

    @Override
    public void write(DataOutputStream dos, Double value) throws IOException {
        dos.writeInt((int) Math.round(value * 100));
    }

    @Override
    public Double read(String string) {
        return Double.parseDouble(string);
    }
}
