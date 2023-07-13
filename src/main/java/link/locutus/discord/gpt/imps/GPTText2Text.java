package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.IEmbeddingDatabase;

import java.util.ArrayList;
import java.util.List;

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
        this.chatEncoder = registry.getEncodingForModel(model);
        this.embeddings = embeddings;
    }

    @Override
    public String generate(String text) {
        CompletionRequest completionRequest = CompletionRequest.builder()
                .prompt(text)
                .model(this.model.getName())
                .echo(false)
                .build();
        CompletionResult completion = service.createCompletion(completionRequest);
        List<String> results = new ArrayList<>();
        for (CompletionChoice choice : completion.getChoices()) {
            results.add(choice.getText());
        }
        return String.join("\n", results);
    }
}
