package link.locutus.discord.gpt.imps.text2text;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.web.WebUtil;
import org.jooq.JSON;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.IntConsumer;

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

    public static class RerankResult {
        public List<String> ranking;
    }

    public List<String> rerank(LinkedHashMap<String, String> items, String criterion, IntConsumer tokensUsed) {
        Objects.requireNonNull(items, "items");
        checkArgument(!items.isEmpty(), "items must not be empty");

        // Build a compact instruction + items listing
        StringBuilder user = new StringBuilder();
        user.append("Criterion: ").append(criterion == null ? "rank by overall relevance" : criterion).append("\n");
        user.append("Items:\n");
        items.forEach((name, desc) -> {
            user.append("- name: ").append(name).append("\n");
            user.append("  description: ").append(desc == null ? "" : desc.replace("\n", " ").trim()).append("\n");
        });

        // Keep prompt free of schema/format instructions; structured output is enforced by responseFormat
        String system = "You are a reranking engine. Rank the provided items by the given criterion.";

        // Strict JSON Schema: ranking is a permutation of the provided item names
        LinkedHashSet<String> itemNames = new LinkedHashSet<>(items.keySet());
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "string");
        itemSchema.put("enum", new ArrayList<>(itemNames));

        Map<String, Object> rankingSchema = new LinkedHashMap<>();
        rankingSchema.put("type", "array");
        rankingSchema.put("items", itemSchema);
        rankingSchema.put("minItems", itemNames.size());
        rankingSchema.put("maxItems", itemNames.size());
        rankingSchema.put("uniqueItems", true);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ranking", rankingSchema);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("ranking"));
        schema.put("additionalProperties", false);

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .addSystemMessage(system)
                .addUserMessage(user.toString())
                .model(this.model)
                .responseFormat(RerankResult.class);

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

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) rankingNode;
        for (Object v : raw) {
            String name = String.valueOf(v);
            if (items.containsKey(name)) ordered.add(name);
        }
        for (String name : items.keySet()) {
            if (!ordered.contains(name)) ordered.add(name);
        }
        return new ArrayList<>(ordered);
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
