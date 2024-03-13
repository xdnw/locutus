package link.locutus.discord.apiv3.csv;

import java.lang.reflect.Field;

public class GzipHeader<T> {
    public GzipHeader(Class parent) {
        Field[] fields = parent.getDeclaredFields();
    }


}
