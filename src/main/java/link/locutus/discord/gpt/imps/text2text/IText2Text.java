package link.locutus.discord.gpt.imps.text2text;

import link.locutus.discord.gpt.ISummarizer;
import link.locutus.discord.gpt.ITokenizer;
import link.locutus.discord.gpt.imps.SimpleSummarizer;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

public interface IText2Text  extends ITokenizer {
    public String getId();

    String generate(String text, IntConsumer usage);

    default String generate(String text, BiConsumer<String, Integer> usage) {
        return generate(text, (t) -> usage.accept(getId(), t));
    }

    default ISummarizer getSummarizer(@Nullable String promptOrNull, double promptToOutputRatio) {
        return new SimpleSummarizer(this, promptOrNull, promptToOutputRatio);
    }
}
