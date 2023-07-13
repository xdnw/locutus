package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.ISummarizer;

import java.util.ArrayList;
import java.util.List;

public class GPTSummarizer implements ISummarizer {
    private final EncodingRegistry registry;
    private final OpenAiService service;
    private final String prompt;
    private final Encoding chatEncoder;
    private final int promptTokens;
    private final ModelType model;

    public GPTSummarizer(EncodingRegistry registry, OpenAiService service) {
        this.registry = registry;
        this.service = service;
        this.prompt = """
                Write a concise summary which preserves syntax, equations, arguments and constraints of the following:
                
                {query}
                
                Concise summary:""";
        this.model = ModelType.GPT_3_5_TURBO;
        this.chatEncoder = registry.getEncodingForModel(model);
        this.promptTokens = GPTUtil.getTokens(prompt.replace("{query}", ""), model);
    }

    @Override
    public String summarize(String text) {
        int cap = 4096 - 4;
        int remaining = cap - promptTokens;
        List<String> summaries = new ArrayList<>();
        for (String chunk : GPTUtil.getChunks(text, model, remaining)) {
            String result = summarizeChunk(chunk);
            summaries.add(result);
        }
        return String.join("\n", summaries);
    }

    public String summarizeChunk(String chunk) {
        String full = prompt.replace("{query}", chunk);
        CompletionRequest completionRequest = CompletionRequest.builder()
                .prompt(full)
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
