package link.locutus.discord.apiv3.csv.header;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.file.Dictionary;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

public abstract class DataHeader<T> {

    private final long date;
    private final Dictionary dict;
    private int offset = 0;
    public DataHeader(long date, Dictionary dict) {
        this.date = date;
        this.dict = dict;
        this.dict.load();
    }

    public final long getDate() {
        return date;
    }

    public final Dictionary getDictionary() {
        return dict;
    }

    public final Map<String, ColumnInfo<T, Object>> getHeaders() {
        Map<String, ColumnInfo<T, Object>> headers = new Object2ObjectLinkedOpenHashMap<>();
        for (Field field : getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !field.canAccess(this)) continue;
            try {
                ColumnInfo<T, Object> column = (ColumnInfo<T, Object>) field.get(this);
                column.setIndex(-1, -1);
                column.setCachedValue(null);
                headers.put(field.getName(), column);
                String[] aliases = column.getAliases();
                if (aliases != null) {
                    for (String alias : aliases) {
                        headers.put(alias, column);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return headers;
    }

    public abstract void clear();

    public void setOffset(int index) {
        this.offset = index;
    }

    public final int getOffset() {
        return offset;
    }
}
