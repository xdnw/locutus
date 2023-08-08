package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.util.StringMan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class GPTText2Text implements IText2Text{
    private final OpenAiService service;
    private final ModelType model;

    public GPTText2Text(String openAiKey, ModelType model) {
        this(new OpenAiService(openAiKey, Duration.ofSeconds(120)), model);
    }

    public GPTText2Text(OpenAiService service, ModelType model) {
        this.service = service;
        this.model = model;
    }

    @Override
    public String getId() {
        return model.name();
    }

    @Override
    public String generate(Map<String, String> options, String text) {
        setOptions(options);
        ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                .messages(List.of(new ChatMessage("user", text)))
                .model(this.model.getName());

        if (temperature != null) {
            builder = builder.temperature(temperature);
        }
        if (stopSequences != null) {
            builder = builder.stop(Arrays.asList(stopSequences));
        }
        if (topP != null) {
            builder = builder.topP(topP);
        }
        if (presencePenalty != null) {
            builder = builder.presencePenalty(presencePenalty);
        }
        if (frequencyPenalty != null) {
            builder = builder.frequencyPenalty(frequencyPenalty);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }

        builder = builder.temperature(temperature);


        ChatCompletionRequest completionRequest = builder.build();
        ChatCompletionResult completion = service.createChatCompletion(completionRequest);
        List<String> results = new ArrayList<>();
        for (ChatCompletionChoice choice : completion.getChoices()) {
            System.out.println("Reason: " + choice.getFinishReason());
            System.out.println("name: " + choice.getMessage().getName());
            System.out.println("role: " + choice.getMessage().getRole());
            System.out.println("text: " + choice.getMessage().getContent());
            results.add(choice.getMessage().getContent());
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

    private Double temperature = 0.7;
    private String[] stopSequences = null;
    private Double topP = null;
    private Double presencePenalty = null;
    private Double frequencyPenalty = null;
    private Integer maxTokens = null;

    public void setOptions(Map<String, String> options) {
        // reset options
        temperature = 0.7;
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
                        checkArgument(maxTokens >= 1 && maxTokens <= getSizeCap(), "max_tokens must be between 1 and " + getSizeCap());
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown option: " + entry.getKey() + ". Valid options are: " + StringMan.getString(getOptions()));
                }
            }
        }
    }

    @Override
    public int getSize(String text) {
        return GPTUtil.getTokens(text, model);
    }

    @Override
    public int getSizeCap() {
        return model.getMaxContextLength();
    }

    public ModelType getModel() {
        return model;
    }
}
