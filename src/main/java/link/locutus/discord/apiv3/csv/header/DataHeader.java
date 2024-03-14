package link.locutus.discord.apiv3.csv.header;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.apiv3.csv.ColumnInfo;

import java.lang.reflect.Field;
import java.util.Map;

public interface DataHeader<T> {

    default Map<String, ColumnInfo<T, Object>> getHeaders() {
        Map<String, ColumnInfo<T, Object>> headers = new Object2ObjectLinkedOpenHashMap<>();
        for (Field field : getClass().getDeclaredFields()) {
            try {
                ColumnInfo<T, Object> column = (ColumnInfo<T, Object>) field.get(this);
                column.setIndex(-1);
                column.setCachedValue(null);
                headers.put(field.getName(), column);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return headers;
    }

    public long getDate();

    void clear();
}
