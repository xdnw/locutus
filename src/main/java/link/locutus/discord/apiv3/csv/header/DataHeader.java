package link.locutus.discord.apiv3.csv.header;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.file.DataFile;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import net.jpountz.util.SafeUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public abstract class DataHeader<T> {

    private final Dictionary dict;
    private int offset = 0;
    private Map<String, ColumnInfo<T, Object>> headers;
    private DataFile.Header<T> headerInfo;

    public DataHeader(Dictionary dict) {
        this.dict = dict;
        this.dict.load();
    }

    public final Dictionary getDictionary() {
        return dict;
    }

    public final Map<String, ColumnInfo<T, Object>> createHeaders() {
        return createHeaders(true);
    }

    public DataFile.Header<T> readIndexes(byte[] decompressed) {
        if (headerInfo != null) return headerInfo;
        synchronized (this) {
            if (headerInfo == null) {
                headerInfo = new DataFile.Header<>();

                Map<String, ColumnInfo<T, Object>> headers = createHeaders(false);
                int index = 0;
                int numHeaders = SafeUtils.readIntBE(decompressed, index);
                index += 4;
                List<ColumnInfo<T, Object>> validColumns = new ObjectArrayList<>();
                int i = 0;

                if (numHeaders > headers.size()) {
                    throw new IllegalStateException("Number of headers in file (" + numHeaders + ") exceeds number of headers defined in class (" + headers.size() + ")");
                } else {
//                    System.out.println("Number of headers in file: " + numHeaders + ", defined in class: " + headers.size());
                }

                List<ColumnInfo<T, Object>> headersArr = new ObjectArrayList<>(headers.values());
                for (int j = 0; j < numHeaders; j++) {
                    ColumnInfo<T, Object> col = headersArr.get(j);
                    col.setCachedValue(null);
                    boolean hasIndex = decompressed[index++] != 0;
                    if (hasIndex) {
                        validColumns.add(col);
                        col.setIndex(i, headerInfo.bytesPerRow);
                        col.setCachedValue(null);
                        headerInfo.bytesPerRow += col.getBytes();
                        i++;
                    } else {
                        col.setIndex(-1, -1);
                        col.setCachedValue(null);
                    }
                }
                headerInfo.numLines = SafeUtils.readIntBE(decompressed, index);
                index += 4;
                headerInfo.headers = validColumns.toArray(new ColumnInfo[0]);
                headerInfo.initialOffset = index;
            }
        }
        return headerInfo;
    }

    public final Map<String, String> getAliases() {
        Map<String, String> result = new Object2ObjectLinkedOpenHashMap<>(1);
        for (Map.Entry<String, ColumnInfo<T, Object>> entry : createHeaders().entrySet()) {
            ColumnInfo<T, Object> col = entry.getValue();
            String[] aliases = col.getAliases();
            if (aliases != null) {
                for (String alias : aliases) {
                    result.put(alias, entry.getKey());
                }
            }
        }
        return result;
    }

    public final Map<String, ColumnInfo<T, Object>> createHeaders(boolean clear) {
        if (headers == null) {
            synchronized (this) {
                if (headers == null) {
                    headers = new Object2ObjectLinkedOpenHashMap<>();
                    for (Field field : getClass().getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || !field.canAccess(this)) continue;
                        try {
                            ColumnInfo<T, Object> column = (ColumnInfo<T, Object>) field.get(this);
                            if (clear) {
                                column.setIndex(-1, -1);
                                column.setCachedValue(null);
                            }
                            column.setName(field.getName());
                            headers.put(field.getName(), column);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    clear = false;
                }
            }
        }
        if (clear) {
            synchronized (this) {
                for (ColumnInfo<T, Object> column : headers.values()) {
                    column.setIndex(-1, -1);
                    column.setCachedValue(null);
                }
            }
        }
        return headers;
    }

    public void setOffset(int index) {
        this.offset = index;
    }

    public final int getOffset() {
        return offset;
    }
}
