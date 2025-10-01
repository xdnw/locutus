package link.locutus.discord.gpt;

import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.moderations.Moderation;

import java.util.*;
import java.util.stream.Collectors;

public class OpenAiUtil {
    public static void checkThrowModeration2(List<Moderation> moderations, String text) {
        for (Moderation result : moderations) {
            if (result.flagged()) {
                String message = "Your submission has been flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static final int TO_JSON_MAX_DEPTH = 64;

    public static JsonValue toJsonValue(Object o) {
        return toJsonValue(o, new IdentityHashMap<>(), 0);
    }

    @SuppressWarnings("unchecked")
    private static JsonValue toJsonValue(Object o,
                                         IdentityHashMap<Object, Boolean> seen,
                                         int depth) {
        if (o == null) return JsonValue.from((Object) null);
        if (o instanceof JsonValue) return (JsonValue) o;

        if (depth > TO_JSON_MAX_DEPTH) {
            throw new IllegalArgumentException("Max JSON conversion depth exceeded (" + TO_JSON_MAX_DEPTH + ")");
        }

        // Unwrap Optional
        if (o instanceof Optional) {
            Object inner = ((Optional<?>) o).orElse(null);
            return toJsonValue(inner, seen, depth + 1);
        }

        // Enums
        if (o instanceof Enum<?>) {
            return JsonValue.from(((Enum<?>) o).name());
        }

        // CharSequence / Character
        if (o instanceof CharSequence || o instanceof Character) {
            return JsonValue.from(o.toString());
        }

        // Primitive arrays
        if (o.getClass().isArray()) {
            // Handle primitive types explicitly
            if (o instanceof int[]) {
                int[] arr = (int[]) o;
                List<JsonValue> list = new ArrayList<>(arr.length);
                for (int v : arr) list.add(JsonValue.from(v));
                return JsonValue.from(list);
            } else if (o instanceof long[]) {
                long[] arr = (long[]) o;
                List<JsonValue> list = new ArrayList<>(arr.length);
                for (long v : arr) list.add(JsonValue.from(v));
                return JsonValue.from(list);
            } else if (o instanceof double[]) {
                double[] arr = (double[]) o;
                List<JsonValue> list = new ArrayList<>(arr.length);
                for (double v : arr) list.add(JsonValue.from(v));
                return JsonValue.from(list);
            } else if (o instanceof float[]) {
                float[] arr = (float[]) o;
                List<JsonValue> list = new ArrayList<>(arr.length);
                for (float v : arr) list.add(JsonValue.from(v));
                return JsonValue.from(list);
            } else if (o instanceof boolean[]) {
                boolean[] arr = (boolean[]) o;
                List<JsonValue> list = new ArrayList<>(arr.length);
                for (boolean v : arr) list.add(JsonValue.from(v));
                return JsonValue.from(list);
            } else if (o instanceof byte[]) {
                byte[] arr = (byte[]) o;
                // Encode as base64 string (alternative: array of numbers)
                return JsonValue.from(Base64.getEncoder().encodeToString(arr));
            } else if (o instanceof short[]) {
                short[] arr = (short[]) o;
                List<JsonValue> list = new ArrayList<>(arr.length);
                for (short v : arr) list.add(JsonValue.from((int) v));
                return JsonValue.from(list);
            } else if (o instanceof char[]) {
                char[] arr = (char[]) o;
                return JsonValue.from(new String(arr));
            } else {
                // Object[] fallback
                Object[] arr = (Object[]) o;
                List<JsonValue> list = new ArrayList<>(arr.length);
                for (Object v : arr) list.add(toJsonValue(v, seen, depth + 1));
                return JsonValue.from(list);
            }
        }

        // Iterable (that is not a Collection)
        if (o instanceof Iterable && !(o instanceof Collection)) {
            List<JsonValue> list = new ArrayList<>();
            for (Object v : (Iterable<?>) o) {
                list.add(toJsonValue(v, seen, depth + 1));
            }
            return JsonValue.from(list);
        }

        // Iterator
        if (o instanceof Iterator) {
            List<JsonValue> list = new ArrayList<>();
            Iterator<?> it = (Iterator<?>) o;
            while (it.hasNext()) {
                list.add(toJsonValue(it.next(), seen, depth + 1));
            }
            return JsonValue.from(list);
        }

        // Map
        if (o instanceof Map) {
            if (seen.put(o, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference detected in Map");
            }
            Map<?, ?> raw = (Map<?, ?>) o;
            LinkedHashMap<String, JsonValue> converted = new LinkedHashMap<>(raw.size());
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                Object keyObj = e.getKey();
                String key = (keyObj == null) ? "null" : String.valueOf(keyObj);
                // Collisions from String.valueOf could overwrite earlier entries
                JsonValue val = toJsonValue(e.getValue(), seen, depth + 1);
                converted.put(key, val);
            }
            seen.remove(o);
            return JsonValue.from(converted);
        }

        // Collection
        if (o instanceof Collection) {
            if (seen.put(o, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Cyclic reference detected in Collection");
            }
            Collection<?> coll = (Collection<?>) o;
            List<JsonValue> converted = new ArrayList<>(coll.size());
            for (Object item : coll) {
                converted.add(toJsonValue(item, seen, depth + 1));
            }
            seen.remove(o);
            return JsonValue.from(converted);
        }

        // Date / Temporal (serialize ISO8601)
        if (o instanceof java.time.temporal.TemporalAccessor) {
            return JsonValue.from(o.toString());
        }
        if (o instanceof Date) {
            return JsonValue.from(((Date) o).toInstant().toString());
        }

        // BigInteger / BigDecimal: pass through (ensure JsonValue can handle)
        if (o instanceof Number || o instanceof Boolean) {
            return JsonValue.from(o);
        }

        // Fallback: use toString() to avoid accidental reflection serialization
        return JsonValue.from(o.toString());
    }

    public static ResponseFormatJsonSchema createSchema(Map<String, String> items) {
        // Deterministic order
        List<String> names = new ArrayList<>(items.keySet());
        Collections.sort(names);

        // Pre-sanitize item descriptions and track which remain non-empty
        Map<String, String> sanitizedDescByName = new HashMap<>();
        for (String name : names) {
            String desc = items.get(name);
            String sanitized = desc == null ? null : GPTUtil.sanitizeForStrictSchema(desc, 256);
            if (sanitized != null && !sanitized.isBlank()) {
                sanitizedDescByName.put(name, sanitized);
            }
        }
        boolean haveAnySanitizedDescriptions = !sanitizedDescByName.isEmpty();

        // Build item schema
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "string");

        if (!names.isEmpty()) {
            if (haveAnySanitizedDescriptions) {
                // anyOf with const + optional description
                List<Map<String, Object>> alts = new ArrayList<>(names.size());
                for (String name : names) {
                    LinkedHashMap<String, Object> alt = new LinkedHashMap<>();
                    alt.put("const", name);
                    String desc = sanitizedDescByName.get(name);
                    if (desc != null && !desc.isBlank()) {
                        alt.put("description", desc);
                    }
                    alts.add(alt);
                }
                itemSchema.put("anyOf", alts);
            } else {
                // simpler, smaller schema when there are no (sanitized) descriptions
                itemSchema.put("enum", names);
            }
        }

        // ranking schema: exact size N; uniqueness cannot be reliably enforced here
        Map<String, Object> rankingSchema = new LinkedHashMap<>();
        rankingSchema.put("type", "array");
        rankingSchema.put("minItems", names.size());
        rankingSchema.put("maxItems", names.size());

        String rankingDesc = GPTUtil.sanitizeForStrictSchema(
                "Return a permutation (no repeats) of the given item names in ranking order.", 256);
        if (rankingDesc != null && !rankingDesc.isBlank()) {
            rankingSchema.put("description", rankingDesc);
        }

        if (!names.isEmpty()) {
            rankingSchema.put("items", itemSchema);
        }
        // If names is empty, omitting "items" avoids an empty enum (invalid); maxItems=0 enforces empty array.

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ranking", rankingSchema);

        Map<String, Object> rawMap = new LinkedHashMap<>();
        rawMap.put("type", "object");
        rawMap.put("properties", properties);
        rawMap.put("required", Collections.singletonList("ranking"));
        rawMap.put("additionalProperties", false);

        // Convert rawMap -> Map<String, JsonValue> preserving order
        Map<String, JsonValue> schemaMap = rawMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> toJsonValue(e.getValue()),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        ResponseFormatJsonSchema.JsonSchema.Schema schema = ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                .additionalProperties(schemaMap) // inject full schema
                .build();

        String topLevelDesc = GPTUtil.sanitizeForStrictSchema(
                "Return a permutation of the input item names in ranking order.", 256);

        ResponseFormatJsonSchema.JsonSchema.Builder jsonSchemaBuilder = ResponseFormatJsonSchema.JsonSchema.builder()
                .name("ranking_schema")
                .schema(schema)
                .strict(true);

        if (topLevelDesc != null && !topLevelDesc.isBlank()) {
            jsonSchemaBuilder.description(topLevelDesc);
        }

        ResponseFormatJsonSchema responseFormat = ResponseFormatJsonSchema.builder()
                .jsonSchema(jsonSchemaBuilder.build())
                .build();

        responseFormat.validate();
        return responseFormat;
    }
}
