package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;

import java.util.function.BiConsumer;

public abstract class NumberColumn<P, V extends Number> extends ColumnInfo<P, V> {
    public NumberColumn(DataHeader<P> header, BiConsumer<P, V> setter) {
        super(header, setter);
    }
}
