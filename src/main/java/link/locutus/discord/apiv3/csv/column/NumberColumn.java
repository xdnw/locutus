package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.util.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;

public abstract class NumberColumn<P, V extends Number> extends ColumnInfo<P, V> {
    public NumberColumn(BiConsumer<P, V> setter) {
        super(setter);
    }
}
