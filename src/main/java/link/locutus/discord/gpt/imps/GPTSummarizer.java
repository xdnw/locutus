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
                You will be provided text from a user guide for the game Politics And War. 
                Take the information and organize it into dot points of factual knowledge (start each line with `- `). Do not make anything up. 
                Preserve syntax, formulas and precision.
                
                Text:
                {query}
                
                Fact summary:""";
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

        ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                .messages(List.of(new ChatMessage("user", full)))
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
