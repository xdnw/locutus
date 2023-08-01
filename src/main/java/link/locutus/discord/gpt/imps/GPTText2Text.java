package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.IEmbeddingDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GPTText2Text implements IText2Text{
    private final EncodingRegistry registry;
    private final OpenAiService service;
    private final Encoding chatEncoder;
    private final ModelType model;
    private final IEmbeddingDatabase embeddings;

    public GPTText2Text(EncodingRegistry registry, OpenAiService service, IEmbeddingDatabase embeddings) {
        this.registry = registry;
        this.service = service;
        this.model = ModelType.GPT_3_5_TURBO;
        System.out.println("Name " + model.getName());
        this.chatEncoder = registry.getEncodingForModel(model);
        this.embeddings = embeddings;
    }

    @Override
    public String generate(String text) {
        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .messages(List.of(new ChatMessage("user", text)))
                .model(this.model.getName())
                .build();
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
}
