package link.locutus.discord.web.jooby;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jpson.PSON;
import gg.jte.TemplateOutput;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class JteUtil {

    private static volatile ObjectMapper serializer;

    public static ObjectMapper getSerializer() {
        if (serializer == null) {
            synchronized (JteUtil.class) {
                if (serializer == null) {
                    serializer = new ObjectMapper(new MessagePackFactory());
                }
            }
        }
        return serializer;
    }

    // Helper: copy inner elements of a MessagePack array into the current generator
    public static void copyArrayElements(JsonFactory factory,
                                         byte[] arrayBytes,
                                         JsonGenerator gen) throws IOException {
        try (JsonParser p = factory.createParser(arrayBytes)) {
            JsonToken t = p.nextToken();
            if (t != JsonToken.START_ARRAY) {
                // Fallback: copy as single value/structure
                if (t != null) {
                    gen.copyCurrentStructure(p);
                }
                return;
            }
            while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                gen.copyCurrentStructure(p);
            }
        }
    }

    public static String toB64(JsonObject json) {
        return Base64.getEncoder().encodeToString(json.toString().getBytes());
    }

    private static class CustomGZIPOutputStream extends GZIPOutputStream {
        CustomGZIPOutputStream(OutputStream out, int bytes) throws IOException {
            super(out, bytes);
            def.setLevel(Deflater.BEST_COMPRESSION);
        }
    }

    public static byte[] toPsonBinary(Map<String, Object> map) {
        return PSON.encode(map);
    }

    public static Object fromPsonBinary(byte[] bytes) {
        return PSON.decode(bytes);
    }

    public static byte[] compress(String b64String) {
        return compress(b64String.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] compress(Object root) {
        try {
            return compress(JteUtil.getSerializer().writeValueAsBytes(root));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new CustomGZIPOutputStream(baos, 1048576)) {
                gos.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to gzip string", e);
        }
    }

    public static <T> T decompressToObject(byte[] bytes, Class<T> clazz) {
        byte[] data = decompress(bytes);
        try {
            return getSerializer().readValue(data, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }

    public static byte[] decompress(byte[] bytes) {
        try {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                try (GZIPInputStream gis = new GZIPInputStream(bais)) {
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = gis.read(buffer)) != -1) {
                            baos.write(buffer, 0, len);
                        }
                        return baos.toByteArray();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress data", e);
        }
    }

    public static String render(Consumer<OwaspHtmlTemplateOutput> task) {
        TemplateOutput output = new StringOutput();
        OwaspHtmlTemplateOutput htmlOutput = new OwaspHtmlTemplateOutput(output);
        task.accept(htmlOutput);
        return output.toString();
    }

    public static <V, T> List<List<Object>> writeArray(List<List<Object>> parent, Collection<Function<T, Object>> functions, List<V> values, Map<V, Map.Entry<T, T>> map) {
        return writeArray(parent, functions, values, (aaId, consumer) -> {
            Map.Entry<T, T> pair = map.get(aaId);
            consumer.accept(pair == null ? null : pair.getKey());
            consumer.accept(pair == null ? null : pair.getValue());
        });
    }

    public static <V, T> List<List<Object>> writeArray(List<List<Object>> parent, Collection<Function<T, Object>> functions, Collection<V> collection, BiConsumer<V, Consumer<T>> provider) {
        List<T> obj = new ObjectArrayList<>();
        for (V value : collection) {
            provider.accept(value, obj::add);
        }
        return writeArray(parent, functions, obj);
    }

    public static <T> List<List<Object>> writeArray(List<List<Object>> parent, Collection<Function<T, Object>> functions, Collection<T> collection) {
        for (T value : collection) {
            List<Object> array = new ObjectArrayList<>();
            if (value == null) {
                for (Function<T, Object> _ : functions) {
                    array.add("");
                }
            } else {
                for (Function<T, Object> function : functions) {
                    array.add(function.apply(value));
                }
            }
            parent.add(array);
        }
        return parent;
    }
//
    public static JsonArray createArrayColNum(Collection<Number> myCollection) {
        JsonArray array = new JsonArray();
        for (Number value : myCollection) {
            array.add(value);
        }
        return array;
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
