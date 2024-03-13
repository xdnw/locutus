package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.util.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class StringColumn<P> extends ColumnInfo<P, String> {
    public StringColumn(BiConsumer<P, String> setter) {
        super(setter);
    }

    @Override
    public String read(DataInputStream dis) throws IOException {
        return dis.readUTF();
    }

    @Override
    public void skip(DataInputStream dis) throws IOException {
        int length = dis.readUnsignedShort();
        dis.skipBytes(length);
    }

    @Override
    public void write(DataOutputStream dos, String value) throws IOException {
        dos.writeUTF(value);
    }

    @Override
    public String read(String string) {
        return string;
    }
}
