package link.locutus.discord.gpt.imps.moderator;

import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.ITokenizer;
import link.locutus.discord.gpt.ModerationResult;

import java.util.List;

public interface IModerator extends ITokenizer {
    List<ModerationResult> moderate(List<String> inputs);

    default List<ModerationResult> moderate(String input) {
        int size = this.getSize(input);
        int sizeCap = this.getSizeCap();
        if (size > sizeCap) {
            List<String> chunks = GPTUtil.getChunks(input, sizeCap, this::getSize);
            return moderate(chunks);
        } else {
            return moderate(List.of(input));
        }
    }
}
