package link.locutus.discord.gpt.imps.text2text;

import link.locutus.discord.gpt.ISummarizer;
import link.locutus.discord.gpt.ITokenizer;
import link.locutus.discord.gpt.imps.SimpleSummarizer;

import javax.annotation.Nullable;
import java.util.Map;

public interface IText2Text  extends ITokenizer {
    public String getId();

    default String generate(String text) {
        return generate(null, text);
    }

    String generate(Map<String, String> options, String text);

    public Map<String, String> getOptions();

    default ISummarizer getSummarizer(@Nullable String promptOrNull, double promptToOutputRatio) {
        return new SimpleSummarizer(this, promptOrNull, promptToOutputRatio);
    }
}
