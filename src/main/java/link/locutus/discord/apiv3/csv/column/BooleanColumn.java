package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class BooleanColumn<P> extends ColumnInfo<P, Boolean> {
    public BooleanColumn(DataHeader<P> header, BiConsumer<P, Boolean> setter) {
        super(header, setter);
    }

    @Override
    public Boolean read(byte[] buffer, int offset) throws IOException {
        return buffer[offset] != 0;
    }

    @Override
    public int getBytes() {
        return 1;
    }

    @Override
    public void write(DataOutputStream dos, Boolean value) throws IOException {
        dos.writeBoolean(value);
    }

    @Override
    public Boolean read(String string) {
        switch (string.toLowerCase()) {
            case "1":
            case "true":
                return true;
            case "0":
            case "false":
                return false;
            default:
                throw new IllegalArgumentException("Invalid boolean value: " + string);
        }
    }
}
