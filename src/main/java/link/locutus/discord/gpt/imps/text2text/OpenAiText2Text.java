package link.locutus.discord.gpt.imps.text2text;

import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.web.WebUtil;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class OpenAiText2Text implements IText2Text {
    private final OpenAIClient service;
    private final ChatModel model;

    private final OpenAiOptions defaultOptions = new OpenAiOptions();

    public OpenAiText2Text(OpenAIClient service, ChatModel model) {
        this.service = service;
        this.model = model;
    }

    @Override
    public String getId() {
        return model.asString();
    }

    public ResponseFormatJsonSchema createSchema(Map<String, String> items) {
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

    private static JsonValue toJsonValue(Object o) {
        if (o == null) return JsonValue.from((Object) null);
        if (o instanceof JsonValue) return (JsonValue) o;
        if (o instanceof Map) {
            Map<?, ?> raw = (Map<?, ?>) o;
            LinkedHashMap<String, JsonValue> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                converted.put(String.valueOf(e.getKey()), toJsonValue(e.getValue()));
            }
            return JsonValue.from(converted);
        }
        if (o instanceof Collection) {
            Collection<?> coll = (Collection<?>) o;
            List<JsonValue> converted = new ArrayList<>(coll.size());
            for (Object item : coll) converted.add(toJsonValue(item));
            return JsonValue.from(converted);
        }
        return JsonValue.from(o);
    }

    @Override
    public List<String> rerank(Map<String, String> items, String criterion, IntConsumer tokensUsed) {
        Objects.requireNonNull(items, "items");
        checkArgument(!items.isEmpty(), "items must not be empty");

        // Build a compact instruction + items listing
        StringBuilder user = new StringBuilder();
        user.append("Criterion: ").append(criterion).append("\n");
        String system = "You are a reranking engine. Rank the provided items in the schema by the given criterion.";

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .addSystemMessage(system)
                .addUserMessage(user.toString())
                .model(this.model)
                .responseFormat(this.createSchema(items));

        applyOptions(builder, defaultOptions);

        ChatCompletion completion = service.chat().completions().create(builder.build());
        String content = completion.choices().stream()
                .map(c -> c.message().content().orElse(null))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No content in rerank response"));

        // Update token usage
        int tokens = completion.usage().map(u -> (int) u.totalTokens())
                .orElseGet(() -> getSize(system + "\n" + user + "\n" + content));
        if (tokensUsed != null) tokensUsed.accept(tokens);

        // Parse JSON and normalize results (using Gson)
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> obj = WebUtil.GSON.fromJson(content, mapType);
        Object rankingNode = obj == null ? null : obj.get("ranking");
        checkArgument(rankingNode instanceof List, "Missing or invalid 'ranking' array in response");

        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) rankingNode;

        List<String> result = new ArrayList<>(raw.size());
        Set<String> allowed = items.keySet();
        Set<String> seen = new HashSet<>();

        for (Object v : raw) {
            checkArgument(v instanceof String, "Ranking element must be a string: %s", v);
            String name = (String) v;
            checkArgument(allowed.contains(name), "Unknown item in ranking: %s", name);
            checkArgument(seen.add(name), "Duplicate item in ranking: %s", name);
            result.add(name);
        }

        checkArgument(result.size() == allowed.size(),
                "Ranking size %s != number of items %s", result.size(), allowed.size());

        return result;
    }

    private void applyOptions(ChatCompletionCreateParams.Builder builder, OpenAiOptions optObj) {
        if (optObj.temperature != null) {
            builder = builder.temperature(optObj.temperature);
        }
        if (optObj.stopSequences != null && optObj.stopSequences.length > 0) {
            builder = builder.stopOfStrings(Arrays.asList(optObj.stopSequences));
        }
        if (optObj.topP != null) {
            builder = builder.topP(optObj.topP);
        }
        if (optObj.presencePenalty != null) {
            builder = builder.presencePenalty(optObj.presencePenalty);
        }
        if (optObj.frequencyPenalty != null) {
            builder = builder.frequencyPenalty(optObj.frequencyPenalty);
        }
        if (optObj.maxTokens != null) {
            builder.maxTokens(optObj.maxTokens);
        }
    }

    @Override
    public String generate(String text, IntConsumer tokensUsed) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .addUserMessage(text)
                .model(this.model);

        applyOptions(builder, defaultOptions);

        ChatCompletionCreateParams completionRequest = builder.build();
        ChatCompletion completion = service.chat().completions().create(completionRequest);
        List<String> results = new ArrayList<>();
        for (ChatCompletion.Choice choice : completion.choices()) {
            String msg = choice.message().content().orElse(null);
            if (msg != null) {
                results.add(msg);
            } else {
                System.out.println("No content in message, skipping.");
            }
        }
        String fullResponse = String.join("\n", results);

        int tokens = completion.usage().map(f -> (int) f.totalTokens()).orElseGet(() -> getSize(text + "\n" + fullResponse));
        if (tokensUsed != null) tokensUsed.accept((int) tokens);


        return String.join("\n", results);
    }

    private static class OpenAiOptions {
        public Double temperature = null;
        public String[] stopSequences = null;
        public Double topP = null;
        public Double presencePenalty = null;
        public Double frequencyPenalty = null;
        public Integer maxTokens = null;

        public OpenAiOptions setOptions(OpenAiText2Text parent, Map<String, String> options) {
            // reset options
            temperature = null;
            stopSequences = null;
            topP = null;
            presencePenalty = null;
            frequencyPenalty = null;
            maxTokens = null;

            if (options != null) {
                for (Map.Entry<String, String> entry : options.entrySet()) {
                    switch (entry.getKey().toLowerCase()) {
                        case "temperature":
                            temperature = Double.parseDouble(entry.getValue());
                            checkArgument(temperature >= 0 && temperature <= 2, "Temperature must be between 0 and 2");
                            break;
                        case "stop_sequences":
                            stopSequences = entry.getValue().replace("\\n", "\n").split(",");
                            checkArgument(stopSequences.length > 0 && stopSequences.length <= 4, "stop_sequences must be between 1 and 4 sequences, separated by commas");
                            break;
                        case "top_p":
                            topP = Double.parseDouble(entry.getValue());
                            checkArgument(topP >= 0 && topP <= 1, "top_p must be between 0 and 1");
                            break;
                        case "presence_penalty":
                            presencePenalty = Double.parseDouble(entry.getValue());
                            checkArgument(presencePenalty >= -2 && presencePenalty <= 2, "presence_penalty must be between -2 and 2");
                            break;
                        case "frequency_penalty":
                            frequencyPenalty = Double.parseDouble(entry.getValue());
                            checkArgument(frequencyPenalty >= -2 && frequencyPenalty <= 2, "frequency_penalty must be between -2 and 2");
                            break;
                        case "max_tokens":
                            maxTokens = Integer.parseInt(entry.getValue());
                            checkArgument(maxTokens >= 1 && maxTokens <= parent.getSizeCap(), "max_tokens must be between 1 and " + parent.getSizeCap());
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown option: " + entry.getKey() + ". Valid options are: " +
                                    "temperature (0-2), stop_sequences (1-4 comma-separated), top_p (0-1), presence_penalty (-2 to 2), frequency_penalty (-2 to 2), max_tokens (1-" + parent.getSizeCap() + ")");
                    }
                }
            }
            return this;
        }
    }

    @Override
    public int getSize(String text) {
        return GPTUtil.getTokens(text, ModelType.fromName(model.asString()).orElseThrow(() -> new IllegalArgumentException("Unknown model: " + model.asString())));
    }

    @Override
    public int getSizeCap() {
        return ModelType.fromName(model.asString()).orElseThrow(() -> new IllegalArgumentException("Unknown model: " + model.asString())).getMaxContextLength();
    }

    public ChatModel getModel() {
        return model;
    }
}
