package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.ModelType;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.util.StringMan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class GPTText2Text implements IText2Text{
    private final OpenAIClient service;
    private final ChatModel model;

    private OpenAiOptions defaultOptions = new OpenAiOptions();

    public GPTText2Text(String openAiKey, ChatModel model) {
        this(OpenAIOkHttpClient.builder().apiKey(openAiKey).timeout(Duration.ofSeconds(120)).build(), model);
    }

    public GPTText2Text(OpenAIClient service, ChatModel model) {
        this.service = service;
        this.model = model;
    }

    @Override
    public String getId() {
        return model.asString();
    }

    @Override
    public String generate(Map<String, String> options, String text) {
        OpenAiOptions optObj = options == null || options.isEmpty() ? defaultOptions : new OpenAiOptions().setOptions(this, options);
//        ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
//                .messages(List.of(new ChatMessage("user", text)))
//                .model(this.model.getName());

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .addUserMessage(text)
                .model(this.model);

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

        ChatCompletionCreateParams completionRequest = builder.build();
        ChatCompletion completion = service.chat().completions().create(completionRequest);
        List<String> results = new ArrayList<>();
        for (ChatCompletion.Choice choice : completion.choices()) {
            System.out.println("Reason: " + choice.finishReason());
            // name, getName() doesn't exist
            System.out.println("Message: " + choice.message().toString());
            String msg = choice.message().content().orElse(null);
            if (msg != null) {
                results.add(msg);
            } else {
                System.out.println("No content in message, skipping.");
            }
        }
        return String.join("\n", results);
    }

    @Override
    public Map<String, String> getOptions() {
        return Map.of(
                "temperature", "0.7",
                "stop_sequences", "\n\n",
                "top_p", "1",
                "presence_penalty", "0",
                "frequency_penalty", "0",
                "max_tokens", "2000"
        );
    }

    private static class OpenAiOptions {
        public Double temperature = null;
        public String[] stopSequences = null;
        public Double topP = null;
        public Double presencePenalty = null;
        public Double frequencyPenalty = null;
        public Integer maxTokens = null;

        public OpenAiOptions setOptions(GPTText2Text parent, Map<String, String> options) {
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
                            throw new IllegalArgumentException("Unknown option: " + entry.getKey() + ". Valid options are: " + StringMan.getString(parent.getOptions()));
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
