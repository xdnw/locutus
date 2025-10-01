package link.locutus.discord.gpt.imps.text2text;

import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.OpenAiUtil;
import link.locutus.discord.web.WebUtil;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
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
                .responseFormat(OpenAiUtil.createSchema(items));

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

    public void generate(
            String text,
            List<Map<String, Object>> toolSchemas,
            Function<Map<String, Object>, String> callFunction,
            Consumer<String> onResponse,
            IntConsumer tokensUsed
    ) {
        final int MAX_TURNS = 8;

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(this.model)
                .addUserMessage(text);

        // Build function tools from raw JSON schemas
        List<ChatCompletionTool> tools = buildTools(toolSchemas);
        if (!tools.isEmpty()) {
            builder.tools(tools);
        }

        int totalTokens = 0;

        try {
            for (int turn = 0; turn < MAX_TURNS; turn++) {
                ChatCompletion completion = service.chat().completions().create(builder.build());

                int thisTurnTokens = completion.usage()
                        .map(u -> (int) u.totalTokens())
                        .orElseGet(() -> getSize(text));
                totalTokens += thisTurnTokens;

                if (completion.choices().isEmpty()) break;

                ChatCompletion.Choice choice = completion.choices().get(0);
                ChatCompletionMessage message = choice.message();

                message.content().ifPresent(c -> {
                    if (!c.isBlank() && onResponse != null) onResponse.accept(c);
                });

                builder.addMessage(message);

                List<ChatCompletionMessageToolCall> calls = message.toolCalls()
                        .orElse(Collections.emptyList());
                if (calls.isEmpty()) {
                    break;
                }

                for (ChatCompletionMessageToolCall call : calls) {
                    if (!call.isFunction()) {
                        System.err.println("[ToolCall] Skipping non-function tool call: " + call);
                        continue;
                    }
                    try {
                        ChatCompletionMessageFunctionToolCall fCall = call.asFunction();              // throws if invalid
                        ChatCompletionMessageFunctionToolCall.Function fn = fCall.function();         // non-null
                        String toolName = fn.name();                                                  // non-null
                        String argsJson = fn.arguments();                                             // non-null

                        Map<String, Object> args = parseArgs(argsJson);
                        if (args == null) args = new Object2ObjectLinkedOpenHashMap<>();
                        args.put("", toolName);

                        String result;
                        try {
                            result = callFunction != null ? callFunction.apply(args) : "";
                        } catch (Exception ex) {
                            result = "Tool execution error: " + ex.getMessage();
                        }
                        if (result == null) result = "";

                        Object jsonPayload = tryParseJson(result);
                        ChatCompletionToolMessageParam.Builder toolMsg = ChatCompletionToolMessageParam.builder()
                                .toolCallId(fCall.id());

                        if (jsonPayload != null) {
                            toolMsg.contentAsJson(jsonPayload);
                        } else {
                            toolMsg.content(result);
                        }
                        builder.addMessage(toolMsg.build());
                    } catch (RuntimeException ex) { // includes OpenAIInvalidDataException
                        System.err.println("[ToolCall] Failed to process function tool call: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (onResponse != null) onResponse.accept("Error: " + e.getMessage());
        } finally {
            if (tokensUsed != null) tokensUsed.accept(totalTokens);
        }
    }

    private List<ChatCompletionTool> buildTools(List<Map<String, Object>> toolDefs) {
        if (toolDefs == null || toolDefs.isEmpty()) return Collections.emptyList();
        List<ChatCompletionTool> tools = new ArrayList<>(toolDefs.size());

        for (Map<String, Object> raw : toolDefs) {
            if (raw == null) continue;

            String type = Optional.ofNullable(raw.get("type"))
                    .map(Object::toString)
                    .orElse("function");
            checkArgument("function".equals(type), "Unsupported tool type: %s (only 'function' allowed)", type);

            Object nameObj = raw.get("name");
            checkArgument(nameObj instanceof String && !((String) nameObj).isBlank(),
                    "Function tool missing 'name'");
            String name = (String) nameObj;
            checkArgument(name.matches("[a-zA-Z0-9_-]{1,64}"),
                    "Invalid function name: %s (must match [a-zA-Z0-9_-]{1,64})", name);

            Object paramsObj = raw.get("parameters");
            checkArgument(paramsObj != null, "Function '%s' missing 'parameters'", name);
            Object normalizedParams = paramsObj;

            if (paramsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paramMap = (Map<String, Object>) paramsObj;
                if (!paramMap.containsKey("type")) {
                    // Copy to avoid mutating caller map
                    Map<String, Object> copy = new LinkedHashMap<>(paramMap);
                    copy.put("type", "object");
                    normalizedParams = copy;
                }
            }

            JsonValue params = (normalizedParams instanceof JsonValue)
                    ? (JsonValue) normalizedParams
                    : OpenAiUtil.toJsonValue(normalizedParams);

            String description = (raw.get("description") instanceof String)
                    ? ((String) raw.get("description")).trim()
                    : null;
            if (description != null && description.isEmpty()) description = null;

            FunctionDefinition.Builder fBuilder = FunctionDefinition.builder()
                    .name(name)
                    .parameters(params);
            if (description != null) {
                fBuilder.description(description);
            }
            // strict flag present but SDK builder currently lacks an exposed setter; ignore safely.

            FunctionDefinition fn = fBuilder.build();
            ChatCompletionFunctionTool tool = ChatCompletionFunctionTool.builder()
                    .function(fn)
                    .build();
            tools.add(ChatCompletionTool.ofFunction(tool));
        }
        return tools;
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) return null;
        return WebUtil.GSON.fromJson(json, Map.class);
    }

    private Object tryParseJson(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}")) &&
                !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return null;
        }
        try {
            return WebUtil.GSON.fromJson(trimmed, Object.class);
        } catch (Exception ignore) {
            return null;
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
