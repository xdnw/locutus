package link.locutus.discord.web.jooby;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import gg.jte.TemplateOutput;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.db.entities.conflict.OffDefStatGroup;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class JteUtil {
    public static String render(Consumer<OwaspHtmlTemplateOutput> task) {
        TemplateOutput output = new StringOutput();
        OwaspHtmlTemplateOutput htmlOutput = new OwaspHtmlTemplateOutput(output);
        task.accept(htmlOutput);
        return output.toString();
    }

    public static JsonArray createArray(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    public static <V, T> JsonArray writeArray(JsonArray parent, Collection<Function<T, Object>> functions, List<V> values, Map<V, Map.Entry<T, T>> map) {
        return writeArray(parent, functions, values, (aaId, consumer) -> {
            Map.Entry<T, T> pair = map.get(aaId);
            consumer.accept(pair == null ? null : pair.getKey());
            consumer.accept(pair == null ? null : pair.getValue());
        });
    }

    public static <V, T> JsonArray writeArray(JsonArray parent, Collection<Function<T, Object>> functions, Collection<V> collection, BiConsumer<V, Consumer<T>> provider) {
        List<T> obj = new ObjectArrayList<>();
        for (V value : collection) {
            provider.accept(value, obj::add);
        }
        return writeArray(parent, functions, obj);
    }

    public static <T> JsonArray writeArray(JsonArray parent, Collection<Function<T, Object>> functions, Collection<T> collection) {
        for (T value : collection) {
            JsonArray array = new JsonArray();
            if (value == null) {
                for (Function<T, Object> function : functions) {
                    array.add("");
                }
            } else {
                for (Function<T, Object> function : functions) {
                    add(array, function.apply(value));
                }
            }
            parent.add(array);
        }
        return parent;
    }

    public static JsonArray createArrayCol(Collection<String> myCollection) {
        JsonArray array = new JsonArray();
        for (String value : myCollection) {
            array.add(value);
        }
        return array;
    }

    public static JsonArray createArrayColObj(Collection<Object> myCollection) {
        JsonArray array = new JsonArray();
        for (Object value : myCollection) {
            add(array, value);
        }
        return array;
    }


    public static JsonArray createArrayObj(Object... values) {
        JsonArray array = new JsonArray();
        for (Object value : values) {
            add(array, value);
        }
        return array;
    }

    public static JsonArray add(JsonArray array, Object value) {
        if (value instanceof Number num) {
            array.add(num);
        } else if (value instanceof Boolean bool) {
            array.add(bool);
        } else if (value instanceof String str) {
            array.add(str);
        } else if (value instanceof JsonElement elem) {
            array.add(elem);
        } else {
            array.add(value == null ? "" : value.toString());
        }
        return array;
    }
}
