package link.locutus.discord.gpt.imps;

import link.locutus.discord.gpt.Chunker;
import link.locutus.discord.gpt.ISummarizer;
import link.locutus.discord.gpt.imps.text2text.IText2Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static com.google.common.base.Preconditions.checkArgument;

public class SimpleSummarizer implements ISummarizer {
    private final String prompt;
    private final IText2Text parent;
    private final int promptTokens;
    private final int tokenCap;
    private final double promptToResponseRatio;

    public SimpleSummarizer(IText2Text parent, String prompt, double promptToResponseRatio) {
        this.parent = parent;
        checkArgument(promptToResponseRatio > 0.2 && promptToResponseRatio < 1, "promptToResponseRatio must be between 0.2 and 1. Recommended 0.75");
        this.promptToResponseRatio = promptToResponseRatio;
        if (prompt == null) {
            this.prompt = """
                Write a concise summary which preserves syntax, equations, arguments and constraints of the following:
                
                {query}
                
                Concise summary:""";
        } else {
            this.prompt = prompt;
        }
        String queryLess = prompt.replace("{query}", "");
        this.promptTokens = queryLess.isEmpty() ? 0 : parent.getSize(queryLess);
        this.tokenCap = parent.getSizeCap();
    }

    @Override
    public String summarize(String text, IntConsumer tokensUsed) {
        int remaining = (int) ((tokenCap * promptToResponseRatio) - promptTokens);
        List<String> summaries = new ArrayList<>();
        for (String chunk : Chunker.getChunks(text, remaining, parent::getSize)) {
            String result = summarizeChunk(chunk, tokensUsed);
            summaries.add(result);
        }
        return String.join("\n", summaries);
    }

    public String summarizeChunk(String chunk, IntConsumer tokensUsed) {
        String full = prompt.replace("{query}", chunk);
        return parent.generate(full, tokensUsed);
    }
}
