package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.util.IOUtil;
import net.jpountz.util.SafeUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public class StringColumn<P> extends ColumnInfo<P, String> {
    private int id = -1;

    public StringColumn(DataHeader<P> header, BiConsumer<P, String> setter) {
        super(header, setter);
    }

    @Override
    public String read(byte[] buffer, int offset) throws IOException {
        this.id = SafeUtils.readIntBE(buffer, offset);
        cacheValue = null;
        return null;
    }

    @Override
    public String get() {
        if (cacheValue != null) {
            return cacheValue;
        }
        return cacheValue = getHeader().getDictionary().get(id);
    }

    @Override
    public int getBytes() {
        return 4;
    }

    @Override
    public void write(DataOutputStream dos, String value) throws IOException {
        int id = getHeader().getDictionary().put(value);
        dos.writeInt(id);
    }

    @Override
    public String read(String string) {
        return string;
    }
}
