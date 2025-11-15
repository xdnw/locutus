package link.locutus.discord.util.math;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class StreamMerge {

    public static void merge(JsonParser cached, JsonParser fresh, JsonGenerator out) throws IOException {
        if (cached.currentToken() == null) {
            cached.nextToken();
        }
        if (fresh.currentToken() == null) {
            fresh.nextToken();
        }
        if (cached.currentToken() != JsonToken.START_OBJECT || fresh.currentToken() != JsonToken.START_OBJECT) {
            throw new IllegalStateException("Both roots must be JSON objects.");
        }
        mergeObject(cached, fresh, out);
    }

    private static void mergeValue(JsonParser cached, JsonParser fresh, JsonGenerator out) throws IOException {
        JsonToken cachedToken = cached.currentToken();
        JsonToken freshToken = fresh.currentToken();

        if (cachedToken == JsonToken.START_OBJECT && freshToken == JsonToken.START_OBJECT) {
            mergeObject(cached, fresh, out);
        } else if (cachedToken == JsonToken.START_ARRAY && freshToken == JsonToken.START_ARRAY) {
            mergeArray(cached, fresh, out);
        } else if (isScalar(cachedToken) && isScalar(freshToken)) {
            // same index, scalar value – latest wins
            out.copyCurrentStructure(fresh);
        } else {
            // Different shapes -> favour fresh but keep parsers in sync.
            cached.skipChildren();
            out.copyCurrentStructure(fresh);
        }
    }

    /**
     * Merge two JSON objects. Each side is streamed; only per-field TokenBuffers are held.
     */
    private static void mergeObject(JsonParser cached, JsonParser fresh, JsonGenerator out) throws IOException {
        out.writeStartObject();

        Map<String, TokenBuffer> freshFields = materialiseFields(fresh);

        while (cached.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = cached.getCurrentName();
            cached.nextToken(); // advance to value

            out.writeFieldName(fieldName);
            TokenBuffer freshBuffer = freshFields.remove(fieldName);

            if (freshBuffer != null) {
                JsonParser freshValue = freshBuffer.asParser(fresh.getCodec());
                freshValue.nextToken(); // position at value
                mergeValue(cached, freshValue, out);
                freshValue.close();
            } else {
                out.copyCurrentStructure(cached);
            }
        }

        // Remaining fields only present in fresh payload.
        for (Map.Entry<String, TokenBuffer> entry : freshFields.entrySet()) {
            out.writeFieldName(entry.getKey());
            try (JsonParser freshValue = entry.getValue().asParser(fresh.getCodec())) {
                freshValue.nextToken();
                out.copyCurrentStructure(freshValue);
            }
        }

        out.writeEndObject();
    }

    /**
     * Merge arrays element-by-element while keeping just the current index in memory.
     * Extra tail elements on either side are copied verbatim.
     */
    private static void mergeArray(JsonParser cached, JsonParser fresh, JsonGenerator out) throws IOException {
        out.writeStartArray();

        JsonToken cachedToken = cached.nextToken(); // first element or END_ARRAY
        JsonToken freshToken = fresh.nextToken();
        int index = 0;

        while (cachedToken != JsonToken.END_ARRAY && freshToken != JsonToken.END_ARRAY) {
            if (cachedToken == JsonToken.START_OBJECT && freshToken == JsonToken.START_OBJECT) {
                mergeObject(cached, fresh, out);
            } else if (cachedToken == JsonToken.START_ARRAY && freshToken == JsonToken.START_ARRAY) {
                mergeArray(cached, fresh, out);
            } else if (isScalar(cachedToken) && isScalar(freshToken)) {
                out.copyCurrentStructure(fresh);
            } else {
                cached.skipChildren();
                out.copyCurrentStructure(fresh);
            }

            cachedToken = cached.nextToken();
            freshToken = fresh.nextToken();
            index++;
        }

        if (cachedToken != JsonToken.END_ARRAY) {
            do {
                out.copyCurrentStructure(cached);
            } while ((cachedToken = cached.nextToken()) != JsonToken.END_ARRAY);
        }

        if (freshToken != JsonToken.END_ARRAY) {
            do {
                out.copyCurrentStructure(fresh);
            } while ((freshToken = fresh.nextToken()) != JsonToken.END_ARRAY);
        }

        out.writeEndArray();
    }

    private static boolean isScalar(JsonToken token) {
        return token != null && token.isScalarValue();
    }

    /**
     * Materialises each field value from {@code parser} into a TokenBuffer without constructing POJOs.
     * The parser must currently be positioned on START_OBJECT.
     */
    private static Map<String, TokenBuffer> materialiseFields(JsonParser parser) throws IOException {
        Map<String, TokenBuffer> buffers = new LinkedHashMap<>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            parser.nextToken(); // move to value
            TokenBuffer buffer = new TokenBuffer(parser.getCodec(), false);
            buffer.copyCurrentStructure(parser);
            buffers.put(fieldName, buffer);
        }
        return buffers;
    }
}
