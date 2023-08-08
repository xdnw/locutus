package link.locutus.discord.gpt.imps;

import link.locutus.discord.gpt.ISummarizer;

import javax.annotation.Nullable;
import java.util.Map;

public interface IText2Text {
    public String getId();
    default String generate(String text) {
        return generate(null, text);
    }
    String generate(Map<String, String> options, String text);
    int getSize(String text);
    int getSizeCap();
    public Map<String, String> getOptions();

    default ISummarizer getSummarizer(@Nullable String promptOrNull, double promptToOutputRatio) {
        return new SimpleSummarizer(this, promptOrNull, promptToOutputRatio);
    }
}
