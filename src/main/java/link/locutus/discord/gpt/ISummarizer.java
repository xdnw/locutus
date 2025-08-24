package link.locutus.discord.gpt;

import java.util.function.IntConsumer;

public interface ISummarizer {
    public String summarize(String text, IntConsumer tokensUsed);
}
